package com.foodcore.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.JwtService;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.service.api.ApiOperacionException;
import com.foodcore.service.api.ApiOperacionesPort;
import com.foodcore.util.JsonMapperProvider;
import com.foodcore.util.ServidorSockets;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador REST delgado para recepción/consulta de pedidos de salón y sincronización de estados con KDS.
 */
public class KdsSyncApiController {

    private static final Logger LOGGER = Logger.getLogger(KdsSyncApiController.class.getName());
    private static final Set<String> ESTADOS_PEDIDO_VALIDOS = Set.of(
            "PENDIENTE", "REGISTRADO", "ABIERTO", "EN_PREPARACION", "LISTO",
            "ENTREGADO", "ITEMS_ADICIONALES", "POR_COBRAR", "COBRADO", "PAGADO", "CANCELADO", "ANULADO"
    );

    private final ApiState state;
    private final ApiOperacionesPort operaciones;

    public KdsSyncApiController(ApiState state, ApiOperacionesPort operaciones) {
        this.state = state;
        this.operaciones = operaciones;
    }

    public void handleCrearPedido(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            String uuidLocal = state.extraerTexto(body, "uuid_local", "uuidLocal", "idempotency_key");
            if (uuidLocal == null || uuidLocal.isBlank()) {
                uuidLocal = UUID.randomUUID().toString();
            } else if (uuidLocal.length() > 36) {
                state.responderError(ctx, 400, "El campo 'uuid_local' no puede exceder 36 caracteres.");
                return;
            }

            String numeroMesa = state.extraerTexto(body, "numero_mesa", "numeroMesa", "mesa");
            if (numeroMesa == null || numeroMesa.isBlank()) {
                state.responderError(ctx, 400, "El campo 'numero_mesa' es obligatorio.");
                return;
            }
            if (numeroMesa.length() == 1 && Character.isDigit(numeroMesa.charAt(0))) {
                numeroMesa = "0" + numeroMesa;
            }

            JsonNode itemsNode = body.has("items") ? body.get("items") : body.get("detalles");
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
                state.responderError(ctx, 400, "El pedido debe incluir al menos un ítem en 'items'.");
                return;
            }

            List<ApiOperacionesPort.ItemSolicitudPedido> items = parsearItemsPedido(ctx, itemsNode);
            if (items == null) {
                return;
            }

            ApiOperacionesPort.ResultadoCreacionPedido res;
            try {
                res = operaciones.crearPedidoOIdempotente(claims.idUsuario(), numeroMesa, uuidLocal, items);
            } catch (ApiOperacionException exOp) {
                state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
                return;
            }

            if (res.idempotente()) {
                ObjectNode respIdem = ApiPedidoJsonMapper.construirRespuestaPedido(
                        state.mapper, res.pedido(), res.detalles(), true);
                ctx.status(200).contentType("application/json; charset=UTF-8").result(respIdem.toString());
                return;
            }

            emitirEventoDirectoKds(res.pedido(), res.detalles());
            ObjectNode resp = ApiPedidoJsonMapper.construirRespuestaPedido(
                    state.mapper, res.pedido(), res.detalles(), false);
            ctx.status(201).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Cuerpo JSON inválido en solicitud de pedido.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error procesando POST /api/pedidos", e);
            state.responderError(ctx, 500, "No se pudo registrar el pedido.");
        }
    }

    public void handleListarPedidos(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ArrayNode array = state.mapper.createArrayNode();
        try {
            ApiOperacionesPort.PedidosActivosSnapshot snap = operaciones.listarPedidosActivosConDetalles();
            for (Pedido p : snap.pedidos()) {
                List<DetallePedido> dets = snap.detallesPorPedido().getOrDefault(p.getIdPedido(), List.of());
                array.add(ApiPedidoJsonMapper.construirRespuestaPedido(state.mapper, p, dets, false));
            }
            ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
        } catch (ApiOperacionException exOp) {
            state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error consultando GET /api/pedidos en BD", e);
            state.responderError(ctx, 503, "No se pudieron consultar los pedidos en base de datos.");
        }
    }

    public void handleObtenerPedidoPorUuid(Context ctx) {
        String uuid = ctx.pathParam("uuid");
        if (uuid == null || uuid.isBlank()) {
            state.responderError(ctx, 400, "UUID de pedido inválido.");
            return;
        }
        try {
            Optional<ApiOperacionesPort.PedidoConDetalles> opt = operaciones.buscarPedidoPorUuid(uuid.trim());
            if (opt.isEmpty()) {
                state.responderError(ctx, 404, "Pedido no encontrado.");
                return;
            }
            ApiOperacionesPort.PedidoConDetalles pcd = opt.get();
            ObjectNode resp = ApiPedidoJsonMapper.construirRespuestaPedido(
                    state.mapper, pcd.pedido(), pcd.detalles(), false);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (ApiOperacionException exOp) {
            state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error consultando GET /api/pedidos/{uuid} en BD", e);
            state.responderError(ctx, 503, "No se pudo consultar el pedido en base de datos.");
        }
    }

    public void handleActualizarEstadoPedido(Context ctx) {
        try {
            int idPedido = Integer.parseInt(ctx.pathParam("id"));
            if (idPedido <= 0) {
                state.responderError(ctx, 400, "ID de pedido inválido.");
                return;
            }
            JsonNode body = state.mapper.readTree(ctx.body());
            if (!body.hasNonNull("estado") || body.get("estado").asText().isBlank()) {
                state.responderError(ctx, 400, "El campo 'estado' es obligatorio.");
                return;
            }
            String nuevoEstado = body.get("estado").asText().trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_PEDIDO_VALIDOS.contains(nuevoEstado)) {
                state.responderError(ctx, 400, "Estado de pedido no permitido: " + nuevoEstado);
                return;
            }

            try {
                operaciones.actualizarEstadoPedido(idPedido, nuevoEstado);
            } catch (ApiOperacionException exOp) {
                state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_pedido", idPedido);
            resp.put("nuevo_estado", nuevoEstado);
            resp.put("mensaje", "Estado del pedido actualizado a " + nuevoEstado);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (NumberFormatException | JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud de actualización de estado inválida.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al actualizar estado de pedido", e);
            state.responderError(ctx, 500, "Error interno al actualizar estado del pedido.");
        }
    }

    private List<ApiOperacionesPort.ItemSolicitudPedido> parsearItemsPedido(Context ctx, JsonNode itemsNode) {
        List<ApiOperacionesPort.ItemSolicitudPedido> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            int idProd = ApiPedidoJsonMapper.extraerIdProducto(item);
            if (idProd <= 0) {
                state.responderError(ctx, 400, "Cada ítem debe especificar un 'id_producto' válido.");
                return null;
            }
            if (!item.hasNonNull("cantidad") || !item.get("cantidad").isInt()) {
                state.responderError(ctx, 400, "Cada ítem debe indicar una 'cantidad' entera positiva.");
                return null;
            }
            int cantidad = item.get("cantidad").asInt(0);
            if (cantidad <= 0) {
                state.responderError(ctx, 400, "La cantidad de cada ítem debe ser mayor a cero.");
                return null;
            }
            String notas = state.extraerTexto(item, "notas", "notas_preparacion", "notasPreparacion");
            items.add(new ApiOperacionesPort.ItemSolicitudPedido(idProd, cantidad, notas != null ? notas : ""));
        }
        return items;
    }

    private void emitirEventoDirectoKds(Pedido pedido, List<DetallePedido> detalles) {
        try {
            ObjectNode msg = JsonMapperProvider.getMapper().createObjectNode();
            msg.put("accion", "NUEVA_COMANDA");
            msg.put("origen", "PWA_TABLET");
            msg.put("idPedido", pedido.getIdPedido());
            msg.put("numeroComanda", "COM-" + String.format("%04d", pedido.getIdPedido()));
            msg.put("mesa", pedido.getNumeroMesa());

            ArrayNode items = msg.putArray("items");
            for (DetallePedido d : detalles) {
                ObjectNode item = items.addObject();
                item.put("producto", d.getNombreProducto());
                item.put("cantidad", d.getCantidad());
                item.put("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
            }
            ServidorSockets.getInstance().emitirMensaje(msg);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Sin clientes KDS conectados al emitir desde KdsSyncApiController", e);
        }
    }
}
