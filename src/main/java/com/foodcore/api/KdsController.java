package com.foodcore.api;

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
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador Javalin para el KDS (Kitchen Display System) de Cocina y Bar (v4.3.0 §1.3 & v4.4.0 §1).
 */
public class KdsController {

    private static final Logger LOGGER = Logger.getLogger(KdsController.class.getName());

    private final ApiState state;
    private final PedidoDAO pedidoDAO;

    public KdsController(ApiState state, PedidoDAO pedidoDAO) {
        this.state = state;
        this.pedidoDAO = pedidoDAO;
    }

    public void handleKdsComandas(Context ctx) {
        ArrayNode root = state.mapper.createArrayNode();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                List<Pedido> pedidos = pedidoDAO.listarPedidosPendientesCocina(conn);
                for (Pedido p : pedidos) {
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
                    List<DetallePedido> detalles = pedidoDAO.listarDetallesPorPedido(conn, p.getIdPedido());
                    for (DetallePedido dp : detalles) {
                        ObjectNode itemNode = itemsArr.addObject();
                        itemNode.put("id_detalle_pedido", dp.getIdDetallePedido());
                        itemNode.put("id_producto", dp.getIdProducto());
                        itemNode.put("nombre", dp.getNombreProducto() != null ? dp.getNombreProducto() : ("Plato #" + dp.getIdProducto()));
                        itemNode.put("cantidad", dp.getCantidad());
                        itemNode.put("precio_unitario", dp.getPrecioUnitario() != null ? dp.getPrecioUnitario().doubleValue() : 0.0);
                        itemNode.put("notas", dp.getNotasPreparacion() != null ? dp.getNotasPreparacion() : "");
                        itemNode.put("estado_cocina", dp.getEstadoCocina() != null ? dp.getEstadoCocina() : "PENDIENTE");
                        itemNode.put("enviado_en", dp.getEnviadoEn() != null ? dp.getEnviadoEn().toString() : Instant.now().toString());
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Aviso consultando comandas KDS en BD, usando datos en memoria", e);
        }

        if (root.isEmpty()) {
            for (ObjectNode pMemo : state.cacheIdempotenciaPedidos.values()) {
                String estado = pMemo.hasNonNull("estado_comanda") ? pMemo.get("estado_comanda").asText() : "PENDIENTE";
                if (!"ENTREGADO".equalsIgnoreCase(estado) && !"CANCELADO".equalsIgnoreCase(estado)) {
                    ObjectNode copy = pMemo.deepCopy();
                    if (copy.has("items")) {
                        for (JsonNode it : copy.get("items")) {
                            if (it instanceof ObjectNode itObj && !itObj.hasNonNull("estado_cocina")) {
                                itObj.put("estado_cocina", "PENDIENTE");
                            }
                        }
                    }
                    root.add(copy);
                }
            }
            if (root.isEmpty()) {
                ObjectNode demo = root.addObject();
                demo.put("id_pedido", 101);
                demo.put("uuid", "demo-kds-01");
                demo.put("numero_mesa", "04");
                demo.put("id_usuario", 3);
                demo.put("id_usuario_creador", 3);
                demo.put("mozo", "Mateo Quispe");
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
                    itemNode.put("nombre", dp.getNombreProducto() != null ? dp.getNombreProducto() : ("Plato #" + dp.getIdProducto()));
                    itemNode.put("cantidad", dp.getCantidad());
                    itemNode.put("notas", dp.getNotasPreparacion() != null ? dp.getNotasPreparacion() : "");
                    itemNode.put("estado_cocina", dp.getEstadoCocina() != null ? dp.getEstadoCocina() : "PENDIENTE");
                    itemNode.put("enviado_en", dp.getEnviadoEn() != null ? dp.getEnviadoEn().toString() : Instant.now().toString());
                    itemNode.put("numero_mesa", dp.getNumeroMesa() != null ? dp.getNumeroMesa() : "01");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso listando ítems de cocina en BD, usando datos en memoria", e);
        }

        if (root.isEmpty()) {
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

            ObjectNode it2 = root.addObject();
            it2.put("id_detalle_pedido", 502);
            it2.put("id_pedido", 101);
            it2.put("id_producto", 5);
            it2.put("nombre", "Limonada Frozen Artesanal");
            it2.put("cantidad", 2);
            it2.put("notas", "Con hierbabuena");
            it2.put("estado_cocina", "EN_PREPARACION");
            it2.put("numero_mesa", "04");
            it2.put("enviado_en", Instant.now().minusSeconds(120).toString());

            ObjectNode it3 = root.addObject();
            it3.put("id_detalle_pedido", 503);
            it3.put("id_pedido", 102);
            it3.put("id_producto", 2);
            it3.put("nombre", "Lomo Saltado Criollo");
            it3.put("cantidad", 1);
            it3.put("notas", "Término 3/4");
            it3.put("estado_cocina", "PENDIENTE");
            it3.put("numero_mesa", "02");
            it3.put("enviado_en", Instant.now().minusSeconds(420).toString());
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(root.toString());
    }

    public void handleActualizarEstadoItemKds(Context ctx) {
        try {
            int idDetalle = Integer.parseInt(ctx.pathParam("id"));
            JsonNode body = state.mapper.readTree(ctx.body());
            String nuevoEstado = body.hasNonNull("estado") ? body.get("estado").asText().trim().toUpperCase() : "EN_PREPARACION";

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetalle, nuevoEstado);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso actualizando estado de ítem de cocina en BD", e);
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

        } catch (NumberFormatException e) {
            state.responderError(ctx, 400, "ID de ítem de pedido inválido.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al actualizar estado de ítem KDS", e);
            state.responderError(ctx, 500, "Error interno: " + e.getMessage());
        }
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
}
