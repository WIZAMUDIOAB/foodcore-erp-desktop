package com.foodcore.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonObject;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador Javalin para el KDS (Kitchen Display System) de Cocina y Bar (v5.7.1 Hardening):
 * consulta batch sin N+1, validación de máquina de estados contra lista blanca
 * y datos demo estrictamente acotados a {@code -Dfoodcore.demoFallback=true}.
 */
public class KdsController {

    private static final Logger LOGGER = Logger.getLogger(KdsController.class.getName());
    private static final Set<String> ESTADOS_KDS_VALIDOS =
            Set.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO", "CANCELADO");
    private static final Map<String, Set<String>> TRANSICIONES_KDS = Map.of(
            "PENDIENTE", Set.of("PENDIENTE", "EN_PREPARACION", "LISTO", "CANCELADO"),
            "EN_PREPARACION", Set.of("EN_PREPARACION", "LISTO", "ENTREGADO", "CANCELADO"),
            "LISTO", Set.of("LISTO", "ENTREGADO", "EN_PREPARACION"),
            "ENTREGADO", Set.of("ENTREGADO"),
            "CANCELADO", Set.of("CANCELADO")
    );

    private final ApiState state;
    private final PedidoDAO pedidoDAO;

    public KdsController(ApiState state, PedidoDAO pedidoDAO) {
        this.state = state;
        this.pedidoDAO = pedidoDAO;
    }

    /**
     * GET /api/kds/comandas — Consulta comandas pendientes y todos sus ítems en bloque (sin N+1).
     */
    public void handleKdsComandas(Context ctx) {
        ArrayNode root = state.mapper.createArrayNode();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                List<Pedido> pedidos = pedidoDAO.listarPedidosPendientesCocina(conn);
                if (!pedidos.isEmpty()) {
                    List<DetallePedido> todosLosItems = pedidoDAO.listarItemsPendientesCocina(conn);
                    Map<Integer, List<DetallePedido>> itemsPorPedido = new HashMap<>();
                    for (DetallePedido dp : todosLosItems) {
                        itemsPorPedido.computeIfAbsent(dp.getIdPedido(), k -> new ArrayList<>()).add(dp);
                    }
                    for (Pedido p : pedidos) {
                        List<DetallePedido> detalles = itemsPorPedido.getOrDefault(p.getIdPedido(), List.of());
                        if (detalles.isEmpty()) {
                            continue;
                        }
                        ObjectNode pNode = root.addObject();
                        pNode.put("id_pedido", p.getIdPedido());
                        pNode.put("uuid", p.getIdempotencyKey());
                        pNode.put("numero_mesa", p.getNumeroMesa());
                        pNode.put("id_usuario", p.getIdUsuario());
                        pNode.put("id_usuario_creador", p.getIdUsuarioCreador() != null ? p.getIdUsuarioCreador() : p.getIdUsuario());
                        pNode.put("estado", p.getEstado());
                        pNode.put("total", p.getTotal() != null ? p.getTotal().doubleValue() : 0.0);
                        pNode.put("creado_en", p.getFechaHora() != null ? p.getFechaHora().toString() : Instant.now().toString());

                        ArrayNode itemsArr = pNode.putArray("items");
                        for (DetallePedido dp : detalles) {
                            ObjectNode itemNode = itemsArr.addObject();
                            itemNode.put("id_detalle_pedido", dp.getIdDetallePedido());
                            itemNode.put("id_producto", dp.getIdProducto());
                            String nombreCocina = com.foodcore.util.FormatoPersonalizacionUtil.formatearDescripcionItem(dp);
                            itemNode.put("nombre", nombreCocina);
                            if (dp.getNombreVariante() != null && !dp.getNombreVariante().isBlank()) {
                                itemNode.put("variante", dp.getNombreVariante());
                            }
                            String mods = com.foodcore.util.FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                                    dp.getModificadoresSeleccionados(), dp.getModificadoresTexto());
                            if (!mods.isEmpty()) {
                                itemNode.put("modificadores", mods);
                            }
                            itemNode.put("cantidad", dp.getCantidad());
                            itemNode.put("precio_unitario", dp.getPrecioUnitario() != null ? dp.getPrecioUnitario().doubleValue() : 0.0);
                            itemNode.put("notas", dp.getNotasPreparacion() != null ? dp.getNotasPreparacion() : "");
                            itemNode.put("estado_cocina", dp.getEstadoCocina() != null ? dp.getEstadoCocina() : "PENDIENTE");
                            itemNode.put("enviado_en", dp.getEnviadoEn() != null ? dp.getEnviadoEn().toString() : Instant.now().toString());
                        }
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Aviso consultando comandas KDS en BD", e);
        }

        if (root.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            agregarComandaDemo(root);
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(root.toString());
    }

    public void handleKdsItems(Context ctx) {
        ArrayNode root = state.mapper.createArrayNode();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                List<DetallePedido> items = pedidoDAO.listarItemsPendientesCocina(conn);
                for (DetallePedido dp : items) {
                    ObjectNode itemNode = root.addObject();
                    itemNode.put("id_detalle_pedido", dp.getIdDetallePedido());
                    itemNode.put("id_pedido", dp.getIdPedido());
                    itemNode.put("id_producto", dp.getIdProducto());
                    itemNode.put("nombre", com.foodcore.util.FormatoPersonalizacionUtil.formatearDescripcionItem(dp));
                    if (dp.getNombreVariante() != null && !dp.getNombreVariante().isBlank()) {
                        itemNode.put("variante", dp.getNombreVariante());
                    }
                    String mods = com.foodcore.util.FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                            dp.getModificadoresSeleccionados(), dp.getModificadoresTexto());
                    if (!mods.isEmpty()) {
                        itemNode.put("modificadores", mods);
                    }
                    if (dp.getIdCombo() != null && dp.getIdCombo() > 0) {
                        itemNode.put("id_combo", dp.getIdCombo());
                    }
                    if (dp.getComboComponentesTexto() != null && !dp.getComboComponentesTexto().isBlank()) {
                        itemNode.put("componentes", dp.getComboComponentesTexto());
                    }
                    itemNode.put("cantidad", dp.getCantidad());
                    itemNode.put("notas", dp.getNotasPreparacion() != null ? dp.getNotasPreparacion() : "");
                    itemNode.put("estado_cocina", dp.getEstadoCocina() != null ? dp.getEstadoCocina() : "PENDIENTE");
                    itemNode.put("enviado_en", dp.getEnviadoEn() != null ? dp.getEnviadoEn().toString() : Instant.now().toString());
                    itemNode.put("numero_mesa", dp.getNumeroMesa() != null ? dp.getNumeroMesa() : "01");
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Aviso listando ítems de cocina en BD", e);
        }

        if (root.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            agregarItemsDemo(root);
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(root.toString());
    }

    public void handleActualizarEstadoItemKds(Context ctx) {
        try {
            int idDetalle = Integer.parseInt(ctx.pathParam("id"));
            if (idDetalle <= 0) {
                state.responderError(ctx, 400, "ID de ítem de pedido inválido.");
                return;
            }
            JsonNode body = state.mapper.readTree(ctx.body());
            if (!body.hasNonNull("estado") || body.get("estado").asText().isBlank()) {
                state.responderError(ctx, 400, "El campo 'estado' es obligatorio.");
                return;
            }
            String nuevoEstado = body.get("estado").asText().trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_KDS_VALIDOS.contains(nuevoEstado)) {
                state.responderError(ctx, 400, "Estado KDS no permitido: " + nuevoEstado);
                return;
            }

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn == null) {
                    state.responderError(ctx, 503, "Base de datos no disponible.");
                    return;
                }
                String estadoActual = obtenerEstadoActualDetalle(conn, idDetalle);
                if (estadoActual == null) {
                    state.responderError(ctx, 404, "Ítem de comanda no encontrado: #" + idDetalle);
                    return;
                }
                Set<String> permitidos = TRANSICIONES_KDS.getOrDefault(estadoActual, Set.of());
                if (!permitidos.contains(nuevoEstado)) {
                    state.responderError(ctx, 409,
                            "Transición KDS no permitida: " + estadoActual + " -> " + nuevoEstado);
                    return;
                }
                pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetalle, nuevoEstado);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error SQL actualizando estado de ítem KDS #" + idDetalle, e);
                state.responderError(ctx, 503, "No se pudo actualizar el estado del ítem en base de datos.");
                return;
            }

            try {
                JsonObject msg = new JsonObject();
                msg.addProperty("type", "KDS_STATUS_CHANGE");
                msg.addProperty("idDetalle", idDetalle);
                msg.addProperty("nuevoEstado", nuevoEstado);
                ServidorSockets.getInstance().emitirMensaje(msg);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso notificando cambio de comanda vía socket", e);
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_detalle_pedido", idDetalle);
            resp.put("nuevo_estado", nuevoEstado);
            resp.put("mensaje", "Estado de cocina actualizado correctamente a " + nuevoEstado);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (NumberFormatException | JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud de actualización KDS inválida.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al actualizar estado de ítem KDS", e);
            state.responderError(ctx, 500, "Error interno al actualizar estado de cocina.");
        }
    }

    private String obtenerEstadoActualDetalle(Connection conn, int idDetalle) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT estado_cocina FROM detalle_pedido WHERE id_detalle_pedido = ?")) {
            ps.setInt(1, idDetalle);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("estado_cocina");
                    return val != null ? val.trim().toUpperCase(Locale.ROOT) : "PENDIENTE";
                }
            }
        }
        return null;
    }

    /**
     * Marca únicamente el pedido indicado como ENTREGADO sin afectar ni limpiar otros pedidos en cocina (v4.4.0 §1).
     */
    public boolean marcarEntregado(int idPedido) {
        if (idPedido <= 0) {
            return false;
        }
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                List<DetallePedido> detalles = pedidoDAO.listarDetallesPorPedido(conn, idPedido);
                for (DetallePedido dp : detalles) {
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, dp.getIdDetallePedido(), "ENTREGADO");
                }
                pedidoDAO.actualizarEstadoPedido(conn, idPedido, "ENTREGADO");
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al marcar pedido #" + idPedido + " como ENTREGADO en KDS", e);
        }
        return false;
    }

    public void handleMarcarPedidoEntregado(Context ctx) {
        try {
            int idPedido = Integer.parseInt(ctx.pathParam("id"));
            boolean ok = marcarEntregado(idPedido);
            if (!ok) {
                state.responderError(ctx, 400, "No se pudo marcar como entregado el pedido #" + idPedido);
                return;
            }
            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_pedido", idPedido);
            resp.put("estado", "ENTREGADO");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (NumberFormatException e) {
            state.responderError(ctx, 400, "ID de pedido inválido.");
        }
    }

    private static void agregarComandaDemo(ArrayNode root) {
        ObjectNode demo = root.addObject();
        demo.put("id_pedido", 101);
        demo.put("uuid", "demo-kds-01");
        demo.put("numero_mesa", "04");
        demo.put("id_usuario", 3);
        demo.put("id_usuario_creador", 3);
        demo.put("mozo", "Demo");
        demo.put("estado", "PENDIENTE");
        demo.put("total", 46.50);
        demo.put("creado_en", Instant.now().minusSeconds(180).toString());
        ArrayNode items = demo.putArray("items");
        ObjectNode it1 = items.addObject();
        it1.put("id_detalle_pedido", 501);
        it1.put("id_producto", 1);
        it1.put("nombre", "Hamburguesa Royal Doble");
        it1.put("cantidad", 2);
        it1.put("notas", "Sin cebolla, papas crocantes");
        it1.put("estado_cocina", "PENDIENTE");
        it1.put("enviado_en", Instant.now().minusSeconds(180).toString());
    }

    private static void agregarItemsDemo(ArrayNode root) {
        ObjectNode it1 = root.addObject();
        it1.put("id_detalle_pedido", 501);
        it1.put("id_pedido", 101);
        it1.put("id_producto", 1);
        it1.put("nombre", "Hamburguesa Royal Doble");
        it1.put("cantidad", 2);
        it1.put("notas", "Sin cebolla, término medio");
        it1.put("estado_cocina", "PENDIENTE");
        it1.put("numero_mesa", "04");
        it1.put("enviado_en", Instant.now().minusSeconds(180).toString());
    }
}
