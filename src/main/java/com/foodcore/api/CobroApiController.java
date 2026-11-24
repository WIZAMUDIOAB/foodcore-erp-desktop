package com.foodcore.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.JwtService;
import com.foodcore.model.Caja;
import com.foodcore.service.api.ApiOperacionException;
import com.foodcore.service.api.ApiOperacionesPort;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador REST delgado para estado/apertura/cierre de caja y cobro de pedidos o ítems desde API.
 */
public class CobroApiController {

    private static final Logger LOGGER = Logger.getLogger(CobroApiController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final Set<String> TIPOS_COMPROBANTE_VALIDOS = Set.of("BOLETA", "FACTURA", "TICKET");
    private static final Set<String> MEDIOS_PAGO_VALIDOS =
            Set.of("EFECTIVO", "TARJETA", "YAPE", "PLIN", "YAPE_PLIN", "TRANSFERENCIA", "MIXTO");

    private final ApiState state;
    private final ApiOperacionesPort operaciones;

    public CobroApiController(ApiState state, ApiOperacionesPort operaciones) {
        this.state = state;
        this.operaciones = operaciones;
    }

    public void handleCajaEstado(Context ctx) {
        try {
            ApiOperacionesPort.EstadoCajaSnapshot snap = operaciones.consultarEstadoCaja();
            if (snap.abierta() && snap.caja() != null) {
                Caja c = snap.caja();
                ObjectNode resp = state.mapper.createObjectNode();
                resp.put("abierta", true);
                resp.put("id_caja", c.getIdCaja());
                resp.put("nombre_caja", c.getNombreCaja());
                resp.put("id_usuario", c.getIdUsuarioApertura());
                resp.put("monto_apertura", c.getMontoApertura().doubleValue());
                resp.put("total_ventas", snap.totalVentas().doubleValue());
                resp.put("total_gastos", snap.totalGastos().doubleValue());
                resp.put("saldo_esperado", snap.saldoEsperado().doubleValue());
                resp.put("estado", c.getEstado());
                ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("abierta", false);
            resp.put("mensaje", "No hay ningún turno de caja abierto actualmente.");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al consultar estado de caja", e);
            state.responderError(ctx, 500, "Error interno al consultar estado de caja.");
        }
    }

    public void handleCajaAbrir(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idUsuario = claims.idUsuario();
            String nombreCaja = body.hasNonNull("nombre_caja") && !body.get("nombre_caja").asText().isBlank()
                    ? body.get("nombre_caja").asText().trim()
                    : "Caja Principal";
            double montoRaw = body.hasNonNull("monto_inicial") ? body.get("monto_inicial").asDouble(Double.NaN) : 0.0;
            if (Double.isNaN(montoRaw) || Double.isInfinite(montoRaw) || montoRaw < 0.0) {
                state.responderError(ctx, 400,
                        "El monto inicial de caja debe ser un número válido mayor o igual a cero.");
                return;
            }
            BigDecimal montoInicial = BigDecimal.valueOf(montoRaw).setScale(2, RoundingMode.HALF_UP);

            int idCaja;
            try {
                idCaja = operaciones.abrirTurnoCaja(idUsuario, nombreCaja, montoInicial);
            } catch (ApiOperacionException exOp) {
                state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_caja", idCaja);
            resp.put("id_usuario", idUsuario);
            resp.put("nombre_caja", nombreCaja);
            resp.put("monto_inicial", montoInicial.doubleValue());
            resp.put("mensaje", "Turno de caja #" + idCaja + " abierto exitosamente con fondo S/ " + montoInicial);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud JSON inválida al abrir caja.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al abrir turno de caja", e);
            state.responderError(ctx, 500, "Error interno al abrir turno de caja.");
        }
    }

    public void handleCajaCerrar(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idUsuario = claims.idUsuario();
            int idCajaSolicitado = body.hasNonNull("id_caja") ? body.get("id_caja").asInt(0) : 0;
            if (!body.hasNonNull("efectivo_fisico_contado")) {
                state.responderError(ctx, 400, "El campo 'efectivo_fisico_contado' es obligatorio.");
                return;
            }
            double contadoRaw = body.get("efectivo_fisico_contado").asDouble(Double.NaN);
            if (Double.isNaN(contadoRaw) || Double.isInfinite(contadoRaw) || contadoRaw < 0.0) {
                state.responderError(ctx, 400, "El efectivo físico contado debe ser un número válido no negativo.");
                return;
            }
            BigDecimal contado = BigDecimal.valueOf(contadoRaw).setScale(2, RoundingMode.HALF_UP);

            ApiOperacionesPort.ResultadoCierreCaja res;
            try {
                res = operaciones.cerrarTurnoCaja(idUsuario, idCajaSolicitado, contado);
            } catch (ApiOperacionException exOp) {
                state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_caja", res.idCaja());
            resp.put("id_usuario", idUsuario);
            resp.put("monto_esperado", res.esperado().doubleValue());
            resp.put("monto_contado", res.contado().doubleValue());
            resp.put("diferencia", res.diferencia().doubleValue());
            resp.put("mensaje", "Turno de caja cerrado exitosamente (Cierre Z).");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud JSON inválida al cerrar caja.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al cerrar turno de caja", e);
            state.responderError(ctx, 500, "Error interno al cerrar turno de caja.");
        }
    }

    public void handleCajaCobrar(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idPedido = body.hasNonNull("id_pedido") ? body.get("id_pedido").asInt(0) : 0;
            String numeroMesa = body.hasNonNull("numero_mesa") ? body.get("numero_mesa").asText().trim() : "";
            String tipoComprobante = body.hasNonNull("tipo_comprobante")
                    ? body.get("tipo_comprobante").asText().trim().toUpperCase(Locale.ROOT)
                    : "BOLETA";
            String medioPago = body.hasNonNull("medio_pago")
                    ? body.get("medio_pago").asText().trim().toUpperCase(Locale.ROOT)
                    : "EFECTIVO";
            String idempotencyKey = state.extraerTexto(body, "idempotency_key", "uuid_local");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                idempotencyKey = UUID.randomUUID().toString();
            } else if (idempotencyKey.length() > 36) {
                state.responderError(ctx, 400, "El campo 'idempotency_key' no puede exceder 36 caracteres.");
                return;
            }
            if (!TIPOS_COMPROBANTE_VALIDOS.contains(tipoComprobante)) {
                state.responderError(ctx, 400, "Tipo de comprobante inválido: " + tipoComprobante);
                return;
            }
            if (!MEDIOS_PAGO_VALIDOS.contains(medioPago)) {
                state.responderError(ctx, 400, "Medio de pago inválido: " + medioPago);
                return;
            }

            List<ApiOperacionesPort.ItemSolicitudPedido> itemsDirectos = List.of();
            if (idPedido <= 0) {
                itemsDirectos = parsearItemsDirectos(ctx, body);
                if (itemsDirectos == null) {
                    return;
                }
            }

            BigDecimal montoRecibidoOpt = null;
            if (body.hasNonNull("monto_recibido")) {
                double recibidoRaw = body.get("monto_recibido").asDouble(Double.NaN);
                if (Double.isNaN(recibidoRaw) || Double.isInfinite(recibidoRaw) || recibidoRaw < 0.0) {
                    state.responderError(ctx, 400, "El monto recibido es inválido.");
                    return;
                }
                montoRecibidoOpt = BigDecimal.valueOf(recibidoRaw).setScale(2, RoundingMode.HALF_UP);
            }

            ApiOperacionesPort.SolicitudCobroCaja solicitud = new ApiOperacionesPort.SolicitudCobroCaja(
                    claims.idUsuario(), idPedido, numeroMesa, tipoComprobante, medioPago,
                    idempotencyKey, montoRecibidoOpt, itemsDirectos);

            ApiOperacionesPort.ResultadoCobroCaja res;
            try {
                res = operaciones.cobrarEnCaja(solicitud);
            } catch (ApiOperacionException exOp) {
                state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
                return;
            }

            ObjectNode resp = construirRespuestaCobro(claims.idUsuario(), res);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud JSON inválida al procesar cobro.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al procesar cobro en caja", e);
            state.responderError(ctx, 500, "Error interno al procesar cobro.");
        }
    }

    private List<ApiOperacionesPort.ItemSolicitudPedido> parsearItemsDirectos(Context ctx, JsonNode body) {
        JsonNode itemsNode = body.has("items") ? body.get("items") : body.get("detalles");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            state.responderError(ctx, 400, "Debe proporcionar un 'id_pedido' válido o una lista de 'items'.");
            return null;
        }
        List<ApiOperacionesPort.ItemSolicitudPedido> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            int idProd = ApiPedidoJsonMapper.extraerIdProducto(item);
            int cantidad = item.hasNonNull("cantidad") ? item.get("cantidad").asInt(0) : 0;
            if (idProd <= 0 || cantidad <= 0) {
                state.responderError(ctx, 400, "Cada ítem a cobrar requiere 'id_producto' y 'cantidad > 0'.");
                return null;
            }
            items.add(new ApiOperacionesPort.ItemSolicitudPedido(idProd, cantidad, ""));
        }
        return items;
    }

    private ObjectNode construirRespuestaCobro(int idUsuario, ApiOperacionesPort.ResultadoCobroCaja res) {
        ObjectNode resp = state.mapper.createObjectNode();
        resp.put("status", "OK");
        resp.put("id_venta", res.venta().getIdVenta());
        resp.put("id_pedido", res.idPedidoPersistido() != null ? res.idPedidoPersistido() : 0);
        resp.put("id_usuario", idUsuario);
        resp.put("numero_mesa", res.numeroMesa());
        resp.put("tipo_comprobante", res.tipoComprobante());
        resp.put("numero_comprobante", res.numeroComprobante());
        resp.put("medio_pago", res.medioPago());
        resp.put("subtotal", res.venta().getSubtotal().doubleValue());
        resp.put("igv", res.venta().getIgv().doubleValue());
        resp.put("total", res.venta().getTotal().doubleValue());
        resp.put("monto_recibido", res.montoRecibido().doubleValue());
        resp.put("vuelto", res.vuelto().doubleValue());
        resp.put("mesa_liberada", res.mesaLiberada() || !res.numeroMesa().isBlank());
        resp.put("fecha_emision", LocalDateTime.now(ZONA_LIMA).toString());
        return resp;
    }
}
