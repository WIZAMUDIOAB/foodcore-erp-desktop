package com.foodcore.service;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Servicio transaccional de gestión de Pedidos, Comandas y bloqueo optimista de Mesas
 * según SPEC-FOODCORE-v1.0 (§6.2, §9.1, §9.2; REMEDIATION R-02.5).
 */
public class PedidoService {

    private static final Logger LOGGER = Logger.getLogger(PedidoService.class.getName());
    private final TransactionManager tx;
    private final PedidoDAO pedidoDAO;
    private final AutorizacionService autorizacionService;

    public PedidoService() {
        this(new JdbcTransactionManager(), new PedidoDAOImpl(), new AutorizacionService());
    }

    public PedidoService(TransactionManager tx, PedidoDAO pedidoDAO, AutorizacionService autorizacionService) {
        this.tx = tx;
        this.pedidoDAO = pedidoDAO;
        this.autorizacionService = autorizacionService;
    }

    public Pedido crearPedido(Pedido pedido, List<DetallePedido> detalles) throws NegocioException, SQLException {
        return registrarPedidoYEnviarComanda(pedido, detalles);
    }

    public Pedido registrarPedidoYEnviarComanda(Pedido pedido, List<DetallePedido> detalles) throws NegocioException, SQLException {
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("Debe agregar al menos un plato al pedido.");
        }
        if (pedido.getNumeroMesa() == null || pedido.getNumeroMesa().trim().isEmpty()) {
            throw new NegocioException("Debe asignar una mesa al pedido.");
        }

        Pedido resultado = tx.executeChecked(conn -> {
            // 1. Verificar idempotencia por idempotency_key (R-02.5)
            if (pedido.getIdempotencyKey() != null && !pedido.getIdempotencyKey().isBlank()) {
                Optional<Pedido> existente = pedidoDAO.buscarPorIdempotencyKey(conn, pedido.getIdempotencyKey());
                if (existente.isPresent()) {
                    Pedido prev = existente.get();
                    prev.setDetalles(pedidoDAO.listarDetallesPorPedido(conn, prev.getIdPedido()));
                    return prev;
                }
            }

            // 2. Guardar Pedido
            pedido.setEstado("REGISTRADO");
            int idPedido = pedidoDAO.crearPedido(conn, pedido);
            pedido.setIdPedido(idPedido);

            // 3. Guardar Detalles con enviado_en por ítem (R-08)
            for (DetallePedido det : detalles) {
                det.setIdPedido(idPedido);
                int idDet = pedidoDAO.insertarDetallePedido(conn, det);
                det.setIdDetallePedido(idDet);
            }
            pedido.setDetalles(detalles);

            // 4. Crear Comanda
            Comanda c = new Comanda();
            c.setIdPedido(idPedido);
            c.setNumeroComanda("COM-" + String.format("%04d", idPedido));
            c.setEstado("PENDIENTE");
            c.setTiempoEstimadoMin(15);
            pedidoDAO.crearComanda(conn, c);

            return pedido;
        });

        enviarNotificacionSocketCocina(resultado, detalles, "COM-" + String.format("%04d", resultado.getIdPedido()));
        return resultado;
    }

    /**
     * Actualiza el estado de una mesa usando bloqueo optimista {@code WHERE id_mesa = ? AND version = ?}.
     * Lanza {@link ConflictoEstadoException} si otra terminal modificó la mesa concurrentemente (R-02.5).
     */
    public void actualizarEstadoMesaOptimista(long idMesa, String nuevoEstado, int versionEsperada)
            throws ConflictoEstadoException, SQLException {
        try {
            tx.executeVoidChecked(conn ->
                pedidoDAO.actualizarEstadoMesaConVersion(conn, idMesa, nuevoEstado, versionEsperada)
            );
        } catch (NegocioException e) {
            if (e instanceof ConflictoEstadoException ce) {
                throw ce;
            }
            throw new SQLException(e.getMessage(), e);
        } catch (SQLException e) {
            if (e.getCause() instanceof ConflictoEstadoException ce) {
                throw ce;
            }
            throw e;
        }
    }

    /**
     * Transición {@code ENTREGADO -> ITEMS_ADICIONALES}: exige el permiso explícito
     * {@link Permiso#PEDIDO_ADICIONAR_POST_ENTREGA} (SPEC-FOODCORE-v1.0 §6.2, §10.3; R-02.5).
     */
    public Pedido adicionarItemsPostEntrega(int idPedido, List<DetallePedido> nuevosItems, SesionUsuario sesion)
            throws NegocioException, SQLException {
        autorizacionService.exigirPermiso(sesion, Permiso.PEDIDO_ADICIONAR_POST_ENTREGA);
        if (nuevosItems == null || nuevosItems.isEmpty()) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA, "Debe proporcionar al menos un ítem adicional.");
        }

        return tx.executeChecked(conn -> {
            Optional<Pedido> opt = pedidoDAO.obtenerPorId(conn, idPedido);
            if (opt.isEmpty()) {
                throw new NegocioException(CodigoError.RECURSO_NO_ENCONTRADO, "El pedido #" + idPedido + " no existe.");
            }
            Pedido pedido = opt.get();
            if (!"ENTREGADO".equalsIgnoreCase(pedido.getEstado())) {
                throw new ConflictoEstadoException(
                    "Solo se pueden adicionar ítems post-entrega a pedidos en estado ENTREGADO (actual: " + pedido.getEstado() + ")."
                );
            }
            for (DetallePedido item : nuevosItems) {
                item.setIdPedido(idPedido);
                int idDet = pedidoDAO.insertarDetallePedido(conn, item);
                item.setIdDetallePedido(idDet);
            }
            pedidoDAO.actualizarEstadoPedido(conn, idPedido, "ITEMS_ADICIONALES");
            pedido.setEstado("ITEMS_ADICIONALES");
            pedido.setDetalles(pedidoDAO.listarDetallesPorPedido(conn, idPedido));
            return pedido;
        });
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

                try (Socket client = new Socket("localhost", ServidorSockets.PUERTO_TCP);
                     PrintWriter out = new PrintWriter(
                         new java.io.OutputStreamWriter(client.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                    out.println(json.toString());
                }
            } catch (Exception e) {
                LOGGER.fine("Servidor KDS no activo al emitir notificación socket: " + e.getMessage());
            }
        }).start();
    }
}
