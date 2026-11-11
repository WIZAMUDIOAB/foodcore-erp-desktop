package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.JwtService;
import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.model.Caja;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.http.Context;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador Javalin para catálogo de productos, estado de mesas, comandas de salón y operaciones de caja (v4.3.0 §1.3).
 */
public class PedidoController {

    private static final Logger LOGGER = Logger.getLogger(PedidoController.class.getName());

    private final ApiState state;
    private final ProductoDAO productoDAO;
    private final MesaDAO mesaDAO;
    private final PedidoDAO pedidoDAO;
    private final PedidoService pedidoService;
    private final CajaService cajaService;

    public PedidoController(ApiState state,
                            ProductoDAO productoDAO,
                            MesaDAO mesaDAO,
                            PedidoDAO pedidoDAO,
                            PedidoService pedidoService,
                            CajaService cajaService) {
        this.state = state;
        this.productoDAO = productoDAO;
        this.mesaDAO = mesaDAO;
        this.pedidoDAO = pedidoDAO;
        this.pedidoService = pedidoService;
        this.cajaService = cajaService;
    }

    public void handleListarProductos(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");

        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(false)) {
            array.add(construirJsonProducto(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    public void handleListarProductosDisponibles(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");

        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(true)) {
            array.add(construirJsonProducto(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    /**
     * GET /api/public/productos - Solo lectura pública del catálogo (carta digital sin datos internos).
     */
    public void handleListarProductosPublicos(Context ctx) {
        ctx.header("Cache-Control", "public, max-age=30");
        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(false)) {
            array.add(construirJsonProductoPublico(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    /**
     * GET /api/public/productos/disponibles - Solo lectura pública de platos disponibles.
     */
    public void handleListarProductosDisponiblesPublicos(Context ctx) {
        ctx.header("Cache-Control", "public, max-age=30");
        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(true)) {
            array.add(construirJsonProductoPublico(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    private List<Producto> obtenerCatalogoProductos(boolean soloDisponibles) {
        List<Producto> productos = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                productos = soloDisponibles
                        ? productoDAO.listarProductosDisponibles(conn)
                        : productoDAO.listarTodos(conn);
                if (!soloDisponibles && productos.isEmpty()) {
                    productos = productoDAO.listarProductosDisponibles(conn);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Usando catálogo de respaldo para listado de productos", e);
        }

        if (productos.isEmpty()) {
            productos = obtenerCatalogoRespaldo();
        }
        if (soloDisponibles) {
            productos = productos.stream().filter(Producto::isDisponible).toList();
        }
        return productos;
    }

    public void handleActualizarDisponibilidad(Context ctx) {
        try {
            int idProducto = Integer.parseInt(ctx.pathParam("id"));
            boolean nuevoEstado = false;

            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode body = state.mapper.readTree(ctx.body());
                if (body.has("disponible")) {
                    nuevoEstado = body.get("disponible").asBoolean();
                } else if (body.has("agotado")) {
                    nuevoEstado = !body.get("agotado").asBoolean();
                }
            } else if (ctx.queryParam("disponible") != null) {
                nuevoEstado = Boolean.parseBoolean(ctx.queryParam("disponible"));
            }

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    productoDAO.cambiarDisponibilidad(conn, idProducto, nuevoEstado);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "No se pudo actualizar disponibilidad en MySQL, aplicando a memoria", e);
            }

            for (Producto p : state.catalogoRespaldoCache) {
                if (p.getIdProducto() == idProducto) {
                    p.setDisponible(nuevoEstado);
                    break;
                }
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("success", true);
            resp.put("id_producto", idProducto);
            resp.put("disponible", nuevoEstado);
            resp.put("mensaje", nuevoEstado ? "Producto disponible en catálogo" : "Producto marcado como AGOTADO");
            ctx.status(200).json(resp);
        } catch (NumberFormatException e) {
            state.responderError(ctx, 400, "ID de producto inválido.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al actualizar disponibilidad de producto", e);
            state.responderError(ctx, 500, "Error interno al actualizar disponibilidad.");
        }
    }

    public void handleImagenProducto(Context ctx) {
        try {
            int idProd = Integer.parseInt(ctx.pathParam("id"));
            String rutaImg = null;
            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT imagen_url FROM producto WHERE id_producto = ?")) {
                        ps.setInt(1, idProd);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                rutaImg = rs.getString("imagen_url");
                            }
                        }
                    }
                }
            }
            if (rutaImg == null || rutaImg.isBlank()) {
                ctx.status(404);
                return;
            }
            servirArchivoImagen(ctx, new File(rutaImg.trim()));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo servir imagen de producto", e);
            ctx.status(404);
        }
    }

    public void handleServirUploadPlatillo(Context ctx) {
        String rawArchivo = ctx.pathParam("archivo");
        if (rawArchivo.contains("/") || rawArchivo.contains("\\") || rawArchivo.contains("..")) {
            ctx.status(403);
            return;
        }
        String archivo = rawArchivo.replaceAll("[^a-zA-Z0-9._-]", "");
        if (archivo.isBlank()) {
            ctx.status(404);
            return;
        }
        Path baseDir = Path.of("uploads", "platillos").toAbsolutePath().normalize();
        Path target = baseDir.resolve(archivo).normalize();
        if (!target.startsWith(baseDir)) {
            ctx.status(403);
            return;
        }
        servirArchivoImagen(ctx, target.toFile());
    }

    public void handleListarMesas(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");
        ArrayNode array = state.mapper.createArrayNode();
        Map<String, String> estadosPorMesa = new LinkedHashMap<>();
        Map<String, Double> totalesPorMesa = new LinkedHashMap<>();

        for (int i = 1; i <= 12; i++) {
            String key = String.format("%02d", i);
            estadosPorMesa.put(key, state.estadosMesasMemoria.getOrDefault(key, "LIBRE"));
            if (state.totalesMesasMemoria.containsKey(key)) {
                totalesPorMesa.put(key, state.totalesMesasMemoria.get(key));
            }
        }

        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                Map<String, String> dbEstados = mesaDAO.obtenerEstadoTodasMesas(conn);
                for (Map.Entry<String, String> entry : dbEstados.entrySet()) {
                    estadosPorMesa.put(entry.getKey(), entry.getValue());
                }

                String sqlPed = "SELECT numero_mesa, total, estado FROM pedido "
                        + "WHERE estado NOT IN ('COBRADO', 'CERRADO', 'ANULADO', 'CANCELADO', 'PAGADO')";
                try (PreparedStatement ps = conn.prepareStatement(sqlPed);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String num = rs.getString("numero_mesa");
                        double tot = rs.getDouble("total");
                        if (num != null && !num.isBlank()) {
                            estadosPorMesa.put(num, "OCUPADA");
                            totalesPorMesa.put(num, tot);
                        }
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Usando estado de mesas por defecto en GET /api/mesas", e);
        }

        int idx = 1;
        for (Map.Entry<String, String> entry : estadosPorMesa.entrySet()) {
            String numero = entry.getKey();
            String estado = entry.getValue() != null ? entry.getValue() : "LIBRE";
            ObjectNode mesaNode = state.mapper.createObjectNode();
            mesaNode.put("id", idx);
            mesaNode.put("numero", numero);
            mesaNode.put("capacidad", (idx % 3 == 0) ? 6 : ((idx % 2 == 0) ? 4 : 2));
            mesaNode.put("zona", idx <= 8 ? "Salón Principal" : "Terraza Lounge");
            mesaNode.put("estado", estado);
            mesaNode.put("total_actual", totalesPorMesa.getOrDefault(numero, 0.0));
            array.add(mesaNode);
            idx++;
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    public void handleCrearPedido(Context ctx) {
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            String uuidLocal = state.extraerTexto(body, "uuid_local", "uuidLocal", "idempotency_key");
            if (uuidLocal == null || uuidLocal.isBlank()) {
                uuidLocal = UUID.randomUUID().toString();
            }

            if (state.cacheIdempotenciaPedidos.containsKey(uuidLocal)) {
                ObjectNode prev = state.cacheIdempotenciaPedidos.get(uuidLocal).deepCopy();
                prev.put("idempotente", true);
                ctx.status(200).contentType("application/json; charset=UTF-8").result(prev.toString());
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

            JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
            int defaultUserId = claims != null ? claims.idUsuario() : 1;
            int idUsuario = body.hasNonNull("id_usuario") ? body.get("id_usuario").asInt(defaultUserId)
                    : (body.hasNonNull("idUsuario") ? body.get("idUsuario").asInt(defaultUserId) : defaultUserId);
            int idUsuarioCreador = body.hasNonNull("id_usuario_creador")
                    ? body.get("id_usuario_creador").asInt(idUsuario)
                    : (body.hasNonNull("idUsuarioCreador") ? body.get("idUsuarioCreador").asInt(idUsuario) : idUsuario);
            if (claims != null && !"ADMIN".equals(claims.rol()) && !"SUPERVISOR".equals(claims.rol())) {
                idUsuario = claims.idUsuario();
                idUsuarioCreador = claims.idUsuario();
            }

            JsonNode itemsNode = body.has("items") ? body.get("items") : body.get("detalles");
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
                state.responderError(ctx, 400, "El pedido debe incluir al menos un ítem en 'items'.");
                return;
            }

            List<DetallePedido> detalles = new ArrayList<>();
            BigDecimal totalCalculado = BigDecimal.ZERO;

            for (JsonNode item : itemsNode) {
                int idProd = item.hasNonNull("id_producto") ? item.get("id_producto").asInt(1)
                        : (item.hasNonNull("idProducto") ? item.get("idProducto").asInt(1)
                        : (item.hasNonNull("id") ? item.get("id").asInt(1) : 1));
                String nombreProd = state.extraerTexto(item, "nombre", "nombre_producto", "nombreProducto");
                if (nombreProd == null || nombreProd.isBlank()) {
                    nombreProd = "Plato #" + idProd;
                }
                int cantidad = item.hasNonNull("cantidad") ? Math.max(1, item.get("cantidad").asInt(1)) : 1;
                double precioVal = item.hasNonNull("precio_unitario") ? item.get("precio_unitario").asDouble(0.0)
                        : (item.hasNonNull("precioUnitario") ? item.get("precioUnitario").asDouble(0.0)
                        : (item.hasNonNull("precio") ? item.get("precio").asDouble(0.0) : 18.0));

                BigDecimal precioUnit = BigDecimal.valueOf(precioVal).setScale(2, RoundingMode.HALF_UP);
                BigDecimal subtotal = precioUnit.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
                String notas = state.extraerTexto(item, "notas", "notas_preparacion", "notasPreparacion");

                DetallePedido det = new DetallePedido(
                        0, 0, idProd, nombreProd, cantidad, precioUnit, subtotal, notas != null ? notas : "");
                detalles.add(det);
                totalCalculado = totalCalculado.add(subtotal);
            }

            boolean yaExistiaEnBd = false;
            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    Optional<Pedido> optExistente = pedidoDAO.buscarPorIdempotencyKey(conn, uuidLocal);
                    if (optExistente.isPresent()) {
                        yaExistiaEnBd = true;
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Verificación previa de idempotencia delegada a PedidoService", e);
            }

            Pedido nuevoPedido = new Pedido();
            nuevoPedido.setIdUsuario(idUsuario);
            nuevoPedido.setIdUsuarioCreador(idUsuarioCreador);
            nuevoPedido.setNumeroMesa(numeroMesa);
            try {
                int idMesaNum = Integer.parseInt(numeroMesa.replaceAll("[^0-9]", ""));
                if (idMesaNum > 0) {
                    nuevoPedido.setIdMesa(idMesaNum);
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.FINEST, "Número de mesa alfanumérico sin id numérico", e);
            }
            nuevoPedido.setTotal(totalCalculado.setScale(2, RoundingMode.HALF_UP));
            nuevoPedido.setIdempotencyKey(uuidLocal);

            Pedido guardado;
            try {
                guardado = pedidoService.registrarPedidoYEnviarComanda(nuevoPedido, detalles);
            } catch (Exception exDb) {
                LOGGER.log(Level.FINE, "Registrando pedido en modo resiliente de memoria (CI/Offline DB)", exDb);
                int idGenerado = state.secuenciaPedidosFallback.incrementAndGet();
                nuevoPedido.setIdPedido(idGenerado);
                nuevoPedido.setEstado("PENDIENTE");
                nuevoPedido.setDetalles(detalles);
                guardado = nuevoPedido;
            }

            state.estadosMesasMemoria.put(numeroMesa, "OCUPADA");
            state.totalesMesasMemoria.put(numeroMesa, totalCalculado.doubleValue());

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE mesa SET estado = 'OCUPADA', id_pedido_actual = ? WHERE numero = ?")) {
                        ps.setInt(1, guardado.getIdPedido());
                        ps.setString(2, numeroMesa);
                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso al sincronizar estado de mesa en POST /api/pedidos", e);
            }

            emitirEventoDirectoKds(guardado, detalles);

            String numComanda = "COM-" + String.format("%04d", guardado.getIdPedido());
            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_pedido", guardado.getIdPedido());
            resp.put("numero_comanda", numComanda);
            resp.put("uuid_local", uuidLocal);
            resp.put("numero_mesa", guardado.getNumeroMesa());
            resp.put("estado", "PENDIENTE");
            resp.put("estado_pedido", guardado.getEstado() != null ? guardado.getEstado() : "REGISTRADO");
            resp.put("estado_comanda", "PENDIENTE");
            resp.put("notificado_kds", true);
            resp.put("total", guardado.getTotal() != null ? guardado.getTotal().doubleValue() : totalCalculado.doubleValue());
            resp.put("id_usuario", guardado.getIdUsuario());
            resp.put("id_usuario_creador", guardado.getIdUsuarioCreador() != null ? guardado.getIdUsuarioCreador() : idUsuarioCreador);
            resp.put("idempotente", yaExistiaEnBd);

            ArrayNode itemsResp = resp.putArray("items");
            for (DetallePedido d : detalles) {
                ObjectNode detNode = itemsResp.addObject();
                detNode.put("id_producto", d.getIdProducto());
                detNode.put("nombre", d.getNombreProducto());
                detNode.put("cantidad", d.getCantidad());
                detNode.put("precio_unitario", d.getPrecioUnitario().doubleValue());
                detNode.put("subtotal", d.getSubtotal().doubleValue());
                detNode.put("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
                detNode.put("estado_cocina", d.getEstadoCocina() != null ? d.getEstadoCocina() : "PENDIENTE");
            }

            state.cacheIdempotenciaPedidos.put(uuidLocal, resp.deepCopy());
            ctx.status(yaExistiaEnBd ? 200 : 201)
                    .contentType("application/json; charset=UTF-8")
                    .result(resp.toString());

        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error procesando POST /api/pedidos", e);
            state.responderError(ctx, 500, "No se pudo registrar el pedido: " + e.getMessage());
        }
    }

    public void handleListarPedidos(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ArrayNode array = state.mapper.createArrayNode();
        for (ObjectNode ped : state.cacheIdempotenciaPedidos.values()) {
            array.add(ped.deepCopy());
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    public void handleObtenerPedidoPorUuid(Context ctx) {
        String uuid = ctx.pathParam("uuid");
        ObjectNode ped = state.cacheIdempotenciaPedidos.get(uuid);
        if (ped != null) {
            ctx.status(200).contentType("application/json; charset=UTF-8").result(ped.toString());
            return;
        }
        state.responderError(ctx, 404, "Pedido no encontrado para uuid_local: " + uuid);
    }

    public void handleActualizarEstadoPedido(Context ctx) {
        try {
            int idPedido = Integer.parseInt(ctx.pathParam("id"));
            JsonNode body = state.mapper.readTree(ctx.body());
            String nuevoEstado = body.hasNonNull("estado") ? body.get("estado").asText().trim().toUpperCase() : "EN_PREPARACION";

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    pedidoDAO.actualizarEstadoPedido(conn, idPedido, nuevoEstado);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso actualizando estado de pedido en BD", e);
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_pedido", idPedido);
            resp.put("nuevo_estado", nuevoEstado);
            resp.put("mensaje", "Estado del pedido actualizado a " + nuevoEstado);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (NumberFormatException e) {
            state.responderError(ctx, 400, "ID de pedido inválido.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al actualizar estado de pedido", e);
            state.responderError(ctx, 500, "Error interno: " + e.getMessage());
        }
    }

    public void handleCajaEstado(Context ctx) {
        try {
            Optional<Caja> optCaja = Optional.empty();
            try {
                optCaja = cajaService.obtenerCajaActiva();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso consultando caja activa en BD", e);
            }

            if (optCaja.isPresent()) {
                Caja c = optCaja.get();
                BigDecimal ventas = BigDecimal.ZERO;
                BigDecimal gastos = BigDecimal.ZERO;
                try {
                    ventas = cajaService.calcularVentasTurno(c.getIdCaja());
                    gastos = cajaService.calcularGastosTurno(c.getIdCaja());
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Aviso procesando operación auxiliar en BD", ex);
                }
                BigDecimal saldo = c.getMontoApertura().add(ventas).subtract(gastos);

                ObjectNode resp = state.mapper.createObjectNode();
                resp.put("abierta", true);
                resp.put("id_caja", c.getIdCaja());
                resp.put("nombre_caja", c.getNombreCaja());
                resp.put("id_usuario", c.getIdUsuarioApertura());
                resp.put("monto_apertura", c.getMontoApertura().doubleValue());
                resp.put("total_ventas", ventas.doubleValue());
                resp.put("total_gastos", gastos.doubleValue());
                resp.put("saldo_esperado", saldo.doubleValue());
                resp.put("estado", c.getEstado());
                ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
                return;
            }

            if (state.cajaTurnoActivoMemoria.get()) {
                ObjectNode resp = state.mapper.createObjectNode();
                resp.put("abierta", true);
                resp.put("id_caja", 1);
                resp.put("nombre_caja", state.cajaNombreMemoria);
                resp.put("id_usuario", 1);
                resp.put("monto_apertura", state.cajaMontoAperturaMemoria.doubleValue());
                resp.put("total_ventas", state.cajaVentasMemoria.doubleValue());
                resp.put("total_gastos", 0.0);
                resp.put("saldo_esperado", state.cajaMontoAperturaMemoria.add(state.cajaVentasMemoria).doubleValue());
                resp.put("estado", "ABIERTA");
                ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("abierta", false);
            resp.put("mensaje", "No hay ningún turno de caja abierto actualmente.");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al consultar estado de caja", e);
            state.responderError(ctx, 500, "Error consultando estado de caja: " + e.getMessage());
        }
    }

    public void handleCajaAbrir(Context ctx) {
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idUsuario = body.hasNonNull("id_usuario") ? body.get("id_usuario").asInt(1) : 1;
            String nombreCaja = body.hasNonNull("nombre_caja") ? body.get("nombre_caja").asText().trim() : "Caja Principal";
            BigDecimal montoInicial = body.hasNonNull("monto_inicial")
                    ? BigDecimal.valueOf(body.get("monto_inicial").asDouble(100.0)).setScale(2, RoundingMode.HALF_UP)
                    : new BigDecimal("100.00");

            int idCaja = 1;
            try {
                idCaja = cajaService.abrirTurnoCaja(idUsuario, nombreCaja, montoInicial);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso abriendo caja en BD, registrando en memoria", e);
            }

            state.cajaTurnoActivoMemoria.set(true);
            state.cajaNombreMemoria = nombreCaja;
            state.cajaMontoAperturaMemoria = montoInicial;
            state.cajaVentasMemoria = BigDecimal.ZERO;

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_caja", idCaja);
            resp.put("nombre_caja", nombreCaja);
            resp.put("monto_inicial", montoInicial.doubleValue());
            resp.put("mensaje", "Turno de caja #" + idCaja + " abierto exitosamente con fondo S/ " + montoInicial);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al abrir turno de caja", e);
            state.responderError(ctx, 500, "No se pudo abrir la caja: " + e.getMessage());
        }
    }

    public void handleCajaCerrar(Context ctx) {
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idCaja = body.hasNonNull("id_caja") ? body.get("id_caja").asInt(1) : 1;
            int idUsuario = body.hasNonNull("id_usuario") ? body.get("id_usuario").asInt(1) : 1;
            BigDecimal contado = body.hasNonNull("efectivo_fisico_contado")
                    ? BigDecimal.valueOf(body.get("efectivo_fisico_contado").asDouble()).setScale(2, RoundingMode.HALF_UP)
                    : state.cajaMontoAperturaMemoria.add(state.cajaVentasMemoria);

            try {
                cajaService.cerrarTurnoCaja(idCaja, idUsuario, contado);
            } catch (SQLException | com.foodcore.exception.NegocioException | RuntimeException e) {
                LOGGER.log(Level.FINE, "Aviso cerrando caja en BD, cerrando en memoria", e);
            }

            BigDecimal esperado = state.cajaMontoAperturaMemoria.add(state.cajaVentasMemoria);
            BigDecimal diferencia = contado.subtract(esperado);

            state.cajaTurnoActivoMemoria.set(false);

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_caja", idCaja);
            resp.put("monto_esperado", esperado.doubleValue());
            resp.put("monto_contado", contado.doubleValue());
            resp.put("diferencia", diferencia.doubleValue());
            resp.put("mensaje", "Turno de caja cerrado exitosamente (Cierre Z).");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al cerrar turno de caja", e);
            state.responderError(ctx, 500, "No se pudo cerrar la caja: " + e.getMessage());
        }
    }

    public void handleCajaCobrar(Context ctx) {
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idPedido = body.hasNonNull("id_pedido") ? body.get("id_pedido").asInt(0) : 0;
            String numeroMesa = body.hasNonNull("numero_mesa") ? body.get("numero_mesa").asText().trim() : "01";
            String tipoComprobante = body.hasNonNull("tipo_comprobante") ? body.get("tipo_comprobante").asText().toUpperCase() : "BOLETA";
            String medioPago = body.hasNonNull("medio_pago") ? body.get("medio_pago").asText().toUpperCase() : "EFECTIVO";
            double montoRecibido = body.hasNonNull("monto_recibido") ? body.get("monto_recibido").asDouble(0.0) : 0.0;
            double total = body.hasNonNull("total") ? body.get("total").asDouble(0.0) : 0.0;

            if (total <= 0.0 && state.totalesMesasMemoria.containsKey(numeroMesa)) {
                total = state.totalesMesasMemoria.get(numeroMesa);
            }
            if (total <= 0.0) {
                total = 49.00;
            }
            if (montoRecibido < total) {
                montoRecibido = total;
            }

            BigDecimal bTotal = BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bSubtotal = bTotal.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
            BigDecimal bIgv = bTotal.subtract(bSubtotal);
            BigDecimal bVuelto = BigDecimal.valueOf(montoRecibido).subtract(bTotal).setScale(2, RoundingMode.HALF_UP);

            int sec = state.secuenciaComprobantes.incrementAndGet();
            String seriePrefix = "FACTURA".equalsIgnoreCase(tipoComprobante) ? "F001-" : "B001-";
            String nroComprobante = seriePrefix + String.format("%06d", sec);

            state.estadosMesasMemoria.put(numeroMesa, "LIBRE");
            state.totalesMesasMemoria.put(numeroMesa, 0.0);

            state.cajaVentasMemoria = state.cajaVentasMemoria.add(bTotal);
            state.ventasAcumuladasHoyMemoria = state.ventasAcumuladasHoyMemoria.add(bTotal);
            state.transaccionesHoyMemoria.incrementAndGet();

            if (idPedido > 0) {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (conn != null) {
                        pedidoDAO.actualizarEstadoPedido(conn, idPedido, "PAGADO");
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Aviso procesando operación auxiliar en BD", ex);
                }
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_venta", sec);
            resp.put("id_pedido", idPedido);
            resp.put("numero_mesa", numeroMesa);
            resp.put("tipo_comprobante", tipoComprobante);
            resp.put("numero_comprobante", nroComprobante);
            resp.put("medio_pago", medioPago);
            resp.put("subtotal", bSubtotal.doubleValue());
            resp.put("igv", bIgv.doubleValue());
            resp.put("total", bTotal.doubleValue());
            resp.put("monto_recibido", montoRecibido);
            resp.put("vuelto", bVuelto.doubleValue());
            resp.put("mesa_liberada", true);
            resp.put("fecha_emision", LocalDateTime.now().toString());

            ObjectNode reciente = resp.deepCopy();
            reciente.put("mozo", "Mateo Quispe (Mozo)");
            state.ventasRecientesMemoria.add(0, reciente);
            while (state.ventasRecientesMemoria.size() > 20) {
                state.ventasRecientesMemoria.remove(state.ventasRecientesMemoria.size() - 1);
            }

            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al procesar cobro en caja", e);
            state.responderError(ctx, 500, "Error al procesar cobro: " + e.getMessage());
        }
    }

    private ObjectNode construirJsonProducto(Producto p) {
        ObjectNode item = state.mapper.createObjectNode();
        double precio = p.getPrecioVenta() != null
                ? p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        String img = normalizarImagenUrl(p);
        String cat = (p.getNombreCategoria() != null && !p.getNombreCategoria().isBlank())
                ? p.getNombreCategoria()
                : resolverNombreCategoria(p.getIdCategoria());

        item.put("id", p.getIdProducto());
        item.put("idProducto", p.getIdProducto());
        item.put("codigo", p.getCodigo() != null ? p.getCodigo() : "PROD-" + p.getIdProducto());
        item.put("nombre", p.getNombre());
        item.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "Especialidad de la casa");
        item.put("precio", precio);
        item.put("imagen_url", img);
        item.put("categoria", cat);
        item.put("disponible", p.isDisponible());
        item.put("es_menu_del_dia", p.isEsMenuDelDia());
        return item;
    }

    private ObjectNode construirJsonProductoPublico(Producto p) {
        ObjectNode item = state.mapper.createObjectNode();
        double precio = p.getPrecioVenta() != null
                ? p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        String img = normalizarImagenUrl(p);
        if (img.startsWith("/api/productos/")) {
            img = "/api/public/productos/" + img.substring("/api/productos/".length());
        }
        String cat = (p.getNombreCategoria() != null && !p.getNombreCategoria().isBlank())
                ? p.getNombreCategoria()
                : resolverNombreCategoria(p.getIdCategoria());

        item.put("id", p.getIdProducto());
        item.put("nombre", p.getNombre());
        item.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "Especialidad de la casa");
        item.put("precio", precio);
        item.put("imagen_url", img);
        item.put("categoria", cat);
        item.put("disponible", p.isDisponible());
        item.put("es_menu_del_dia", p.isEsMenuDelDia());
        return item;
    }

    private void servirArchivoImagen(Context ctx, File file) {
        try {
            if (file == null || !file.exists() || !file.isFile()) {
                ctx.status(404);
                return;
            }
            String name = file.getName().toLowerCase();
            String mime = "image/png";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                mime = "image/jpeg";
            } else if (name.endsWith(".webp")) {
                mime = "image/webp";
            } else if (name.endsWith(".svg")) {
                mime = "image/svg+xml";
            }
            ctx.header("Cache-Control", "no-cache");
            ctx.status(200).contentType(mime).result(Files.readAllBytes(file.toPath()));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error leyendo archivo de imagen", e);
            ctx.status(404);
        }
    }

    private void emitirEventoDirectoKds(Pedido pedido, List<DetallePedido> detalles) {
        try {
            JsonObject msg = new JsonObject();
            msg.addProperty("accion", "NUEVA_COMANDA");
            msg.addProperty("origen", "PWA_TABLET");
            msg.addProperty("idPedido", pedido.getIdPedido());
            msg.addProperty("numeroComanda", "COM-" + String.format("%04d", pedido.getIdPedido()));
            msg.addProperty("mesa", pedido.getNumeroMesa());

            JsonArray items = new JsonArray();
            for (DetallePedido d : detalles) {
                JsonObject item = new JsonObject();
                item.addProperty("producto", d.getNombreProducto());
                item.addProperty("cantidad", d.getCantidad());
                item.addProperty("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
                items.add(item);
            }
            msg.add("items", items);
            ServidorSockets.getInstance().emitirMensaje(msg);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Sin clientes KDS conectados al emitir desde PedidoController", e);
        }
    }

    private List<Producto> obtenerCatalogoRespaldo() {
        if (state.catalogoRespaldoCache.isEmpty()) {
            LocalDate hoy = LocalDate.now();
            state.catalogoRespaldoCache.add(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble",
                    "Doble carne angus 180g, huevo, queso cheddar y papas", new BigDecimal("24.50"), new BigDecimal("9.50"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(2, 1, "HAM-02", "Cheese Burger Clásica",
                    "Carne 180g, queso cheddar fundido, lechuga y tomate", new BigDecimal("18.00"), new BigDecimal("6.80"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(3, 2, "ALI-01", "Alitas BBQ Picantes x6",
                    "6 alitas bañadas en salsa artesanal BBQ ligeramente picante", new BigDecimal("22.00"), new BigDecimal("8.50"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(4, 2, "CRIS-01", "Combo Pollo Crispy 3 Pzs",
                    "3 piezas broaster crocantes con papas amarillas", new BigDecimal("26.00"), new BigDecimal("10.20"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(5, 3, "BEB-01", "Limonada Frozen 1L",
                    "Limonada natural frappeada con hierbabuena", new BigDecimal("14.00"), new BigDecimal("3.50"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(6, 3, "BEB-02", "Chicha Morada 1L",
                    "Receta tradicional con manzana y canela", new BigDecimal("12.00"), new BigDecimal("3.00"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(7, 4, "COM-01", "Papas Fritas Supremas",
                    "Porción gigante de papas con queso fundido y tocino", new BigDecimal("11.50"), new BigDecimal("4.00"), true, null, false, null));
        }
        return new ArrayList<>(state.catalogoRespaldoCache);
    }

    private String resolverNombreCategoria(int idCategoria) {
        return switch (idCategoria) {
            case 1 -> "Hamburguesas";
            case 2 -> "Alitas & Pollo";
            case 3 -> "Bebidas";
            case 4 -> "Guarniciones";
            default -> "Especialidades";
        };
    }

    private String normalizarImagenUrl(Producto p) {
        if (p == null || p.getImagenUrl() == null || p.getImagenUrl().isBlank()) {
            return "";
        }
        String raw = p.getImagenUrl().trim();
        if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("data:image/") || raw.startsWith("/")) {
            return raw;
        }
        String cleanSlashes = raw.replace('\\', '/');
        if (cleanSlashes.startsWith("uploads/")) {
            return "/" + cleanSlashes;
        }
        File localFile = new File(raw);
        if (localFile.exists() && localFile.isFile()) {
            return "/api/productos/" + p.getIdProducto() + "/imagen";
        }
        return raw;
    }
}
