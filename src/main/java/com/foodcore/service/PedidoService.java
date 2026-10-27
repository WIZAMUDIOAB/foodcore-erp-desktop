package com.foodcore.service;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PedidoService {

    private static final Logger LOGGER = Logger.getLogger(PedidoService.class.getName());
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    public Pedido registrarPedidoYEnviarComanda(Pedido pedido, List<DetallePedido> detalles) throws NegocioException, SQLException {
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("Debe agregar al menos un plato al pedido.");
        }
        if (pedido.getNumeroMesa() == null || pedido.getNumeroMesa().trim().isEmpty()) {
            throw new NegocioException("Debe asignar una mesa al pedido.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Guardar Pedido
                pedido.setEstado("REGISTRADO");
                int idPedido = pedidoDAO.crearPedido(conn, pedido);
                pedido.setIdPedido(idPedido);

                // 2. Guardar Detalles
                for (DetallePedido det : detalles) {
                    det.setIdPedido(idPedido);
                    pedidoDAO.insertarDetallePedido(conn, det);
                }

                // 3. Crear Comanda
                Comanda c = new Comanda();
                c.setIdPedido(idPedido);
                c.setNumeroComanda("COM-" + String.format("%04d", idPedido));
                c.setEstado("PENDIENTE");
                c.setTiempoEstimadoMin(15);
                pedidoDAO.crearComanda(conn, c);

                conn.commit();

                // 4. Notificar a Cocina vía TCP Socket (puerto 9090)
                enviarNotificacionSocketCocina(pedido, detalles, c.getNumeroComanda());

                return pedido;
            } catch (Exception e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Error al registrar pedido. Rollback ejecutado.", e);
                throw new SQLException("No se pudo registrar el pedido: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void enviarNotificacionSocketCocina(Pedido pedido, List<DetallePedido> detalles, String numeroComanda) {
        new Thread(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("accion", "NUEVA_COMANDA");
                json.addProperty("idPedido", pedido.getIdPedido());
                json.addProperty("numeroComanda", numeroComanda);
                json.addProperty("mesa", pedido.getNumeroMesa());

                JsonArray items = new JsonArray();
                for (DetallePedido d : detalles) {
                    JsonObject item = new JsonObject();
                    item.addProperty("producto", d.getNombreProducto());
                    item.addProperty("cantidad", d.getCantidad());
                    item.addProperty("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
                    items.add(item);
                }
                json.add("items", items);

                // Enviar directo al servidor TCP local
                try (Socket client = new Socket("localhost", ServidorSockets.PUERTO_TCP);
                     PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
                    out.println(json.toString());
                }
            } catch (Exception e) {
                LOGGER.warning("No se pudo enviar comanda por socket (Servidor KDS no activo o error de conexión): " + e.getMessage());
            }
        }).start();
    }
}
