package com.foodcore.service;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Usuario;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.JsonMapperProvider;
import com.foodcore.util.ServidorSockets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
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
    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();

    public PedidoService() {
        this(new JdbcTransactionManager(), new PedidoDAOImpl(), new AutorizacionService());
    }

    public PedidoService(PedidoDAO pedidoDAO) {
        this(new JdbcTransactionManager(), pedidoDAO != null ? pedidoDAO : new PedidoDAOImpl(), new AutorizacionService());
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

    /**
     * Recalcula el total acumulado del pedido sumando todos sus ítems en BD
     * (y costo de envío si aplica), persiste el monto en {@code pedido.total} y lo retorna.
     */
    public BigDecimal calcularTotal(int idPedido) throws SQLException {
        if (idPedido <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return tx.executeChecked(conn -> pedidoDAO.calcularTotalPedido(conn, idPedido));
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Lista todos los ítems persistidos de un pedido dado.
     */
    public List<DetallePedido> listarDetallesPorPedido(int idPedido) throws SQLException {
        if (idPedido <= 0) {
            return List.of();
        }
        try {
            return tx.executeChecked(conn -> pedidoDAO.listarDetallesPorPedido(conn, idPedido));
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Agrega nuevos ítems a un pedido activo existente sin crear un segundo pedido,
     * recalcula el total acumulado vía {@link PedidoDAO#calcularTotalPedido} y notifica al KDS.
     * Si el pedido se encuentra en estado {@code ENTREGADO}, exige explícitamente
     * {@link Permiso#PEDIDO_ADICIONAR_POST_ENTREGA}.
     */
    public Pedido agregarItemsAPedido(int idPedido, List<DetallePedido> nuevosItems)
            throws NegocioException, SQLException {
        return agregarItemsAPedido(idPedido, nuevosItems, resolverSesionActualParaPedido());
    }

    /**
     * Variante con sesión explícita para validar {@link Permiso#PEDIDO_ADICIONAR_POST_ENTREGA}
     * cuando el pedido ya se encuentra {@code ENTREGADO}.
     */
    public Pedido agregarItemsAPedido(int idPedido, List<DetallePedido> nuevosItems, SesionUsuario sesion)
            throws NegocioException, SQLException {
        if (idPedido <= 0) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA, "Identificador de pedido inválido.");
        }
        if (nuevosItems == null || nuevosItems.isEmpty()) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA, "Debe proporcionar al menos un ítem nuevo.");
        }

        Pedido actualizado = tx.executeChecked(conn -> {
            Optional<Pedido> opt = pedidoDAO.obtenerPorId(conn, idPedido);
            if (opt.isEmpty()) {
                throw new NegocioException(CodigoError.RECURSO_NO_ENCONTRADO, "El pedido #" + idPedido + " no existe.");
            }
            Pedido pedido = opt.get();
            String est = pedido.getEstado() != null ? pedido.getEstado().toUpperCase(Locale.ROOT) : "";
            if ("COBRADO".equals(est) || "CERRADO".equals(est) || "PAGADO".equals(est)
                    || "CANCELADO".equals(est) || "ANULADO".equals(est)) {
                throw new ConflictoEstadoException(
                    "No se pueden agregar ítems a un pedido en estado terminal (" + pedido.getEstado() + ")."
                );
            }
            if ("ENTREGADO".equals(est)) {
                autorizacionService.exigirPermiso(sesion, Permiso.PEDIDO_ADICIONAR_POST_ENTREGA);
            }

            for (DetallePedido item : nuevosItems) {
                item.setIdPedido(idPedido);
                int idDet = pedidoDAO.insertarDetallePedido(conn, item);
                item.setIdDetallePedido(idDet);
            }

            if ("ENTREGADO".equals(est) || "POR_COBRAR".equals(est)) {
                pedidoDAO.actualizarEstadoPedido(conn, idPedido, "ITEMS_ADICIONALES");
                pedido.setEstado("ITEMS_ADICIONALES");
            }

            BigDecimal totalAcumulado = pedidoDAO.calcularTotalPedido(conn, idPedido);
            pedido.setTotal(totalAcumulado);
            pedido.setDetalles(pedidoDAO.listarDetallesPorPedido(conn, idPedido));
            return pedido;
        });

        enviarNotificacionSocketCocina(actualizado, nuevosItems, "COM-" + String.format("%04d", idPedido));
        return actualizado;
    }

    private SesionUsuario resolverSesionActualParaPedido() {
        SesionUsuario activa = SesionManager.getSesionActiva();
        if (activa != null) {
            return activa;
        }
        Usuario usr = AuthService.getUsuarioSesionActual();
        if (usr != null && usr.getIdUsuario() > 0 && usr.getNombreRol() != null && !usr.getNombreRol().isBlank()) {
            return autorizacionService.crearSesion(usr.getIdUsuario(), usr.getUsername(), usr.getNombreRol(), "POS-PEDIDO");
        }
        return null;
    }

    /**
     * Transfiere un pedido activo completo de una mesa origen a una mesa destino (SPEC-FOODCORE-v4.6.0 §1.2):
     * adquiere bloqueo pesimista ordenado sobre ambas mesas, exige que la mesa destino esté {@code LIBRE},
     * incrementa {@code version} y registra trazabilidad en {@code auditoria}.
     */
    public boolean transferirMesa(int idMesaOrigen, int idMesaDestino) throws SQLException {
        if (idMesaOrigen <= 0 || idMesaDestino <= 0 || idMesaOrigen == idMesaDestino) {
            throw new IllegalArgumentException("Las mesas de origen y destino deben ser distintas y válidas.");
        }
        try {
            return tx.executeChecked(conn -> {
                String numOrigen = null;
                String numDestino = null;
                String estadoDestino = null;
                boolean existeOrigen = false;
                boolean existeDestino = false;

                String sqlLockMesas = "SELECT id_mesa, numero, estado FROM mesa WHERE id_mesa IN (?, ?) ORDER BY id_mesa ASC FOR UPDATE";
                try (PreparedStatement psLock = conn.prepareStatement(sqlLockMesas)) {
                    psLock.setInt(1, Math.min(idMesaOrigen, idMesaDestino));
                    psLock.setInt(2, Math.max(idMesaOrigen, idMesaDestino));
                    try (ResultSet rs = psLock.executeQuery()) {
                        while (rs.next()) {
                            int idM = rs.getInt("id_mesa");
                            if (idM == idMesaOrigen) {
                                existeOrigen = true;
                                numOrigen = rs.getString("numero");
                            } else if (idM == idMesaDestino) {
                                existeDestino = true;
                                numDestino = rs.getString("numero");
                                estadoDestino = rs.getString("estado");
                            }
                        }
                    }
                }

                if (!existeOrigen) {
                    throw new IllegalStateException("La mesa origen #" + idMesaOrigen + " no existe.");
                }
                if (!existeDestino) {
                    throw new IllegalStateException("La mesa destino #" + idMesaDestino + " no existe.");
                }
                if (!"LIBRE".equalsIgnoreCase(estadoDestino)) {
                    throw new IllegalStateException(
                            "La mesa destino #" + idMesaDestino + " (" + numDestino
                                    + ") no está LIBRE (estado actual: " + estadoDestino + ").");
                }

                Optional<Pedido> optPedido = pedidoDAO.buscarPedidoActivoPorMesa(conn, idMesaOrigen);
                if (optPedido.isEmpty()) {
                    throw new IllegalStateException("No existe pedido activo en la mesa origen #" + idMesaOrigen);
                }
                Pedido pedido = optPedido.get();
                String etiquetaOrigen = numOrigen != null ? numOrigen
                        : (pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : String.format("%02d", idMesaOrigen));
                String etiquetaDestino = numDestino != null ? numDestino : String.format("%02d", idMesaDestino);

                pedidoDAO.moverPedidoAMesa(conn, pedido.getIdPedido(), idMesaDestino, etiquetaDestino);
                pedidoDAO.actualizarEstadoMesaDirecto(conn, idMesaOrigen, "LIBRE", null);
                pedidoDAO.actualizarEstadoMesaDirecto(conn, idMesaDestino, "OCUPADA", pedido.getIdPedido());

                int idUsuario = AuthService.getUsuarioSesionActual() != null
                        ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
                auditoriaDAO.registrarEventoSeguro(
                        conn,
                        idUsuario,
                        "TRANSFERIR_MESA",
                        "pedido#" + pedido.getIdPedido(),
                        "Mesa " + etiquetaOrigen,
                        "Mesa " + etiquetaDestino
                );
                return true;
            });
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    private void enviarNotificacionSocketCocina(Pedido pedido, List<DetallePedido> detalles, String numeroComanda) {
        try {
            ObjectNode json = JsonMapperProvider.getMapper().createObjectNode();
            json.put("accion", "NUEVA_COMANDA");
            json.put("idPedido", pedido.getIdPedido());
            json.put("numeroComanda", numeroComanda);
            json.put("mesa", pedido.getNumeroMesa());

            ArrayNode items = json.putArray("items");
            for (DetallePedido d : detalles) {
                ObjectNode item = items.addObject();
                item.put("idDetallePedido", d.getIdDetallePedido());
                item.put("producto", d.getNombreProducto());
                if (d.getNombreVariante() != null && !d.getNombreVariante().isBlank()) {
                    item.put("variante", d.getNombreVariante());
                }
                String mods = com.foodcore.util.FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                        d.getModificadoresSeleccionados(), d.getModificadoresTexto());
                if (!mods.isEmpty()) {
                    item.put("modificadores", mods);
                }
                if (d.getIdCombo() != null && d.getIdCombo() > 0) {
                    item.put("idCombo", d.getIdCombo());
                }
                if (d.getComboComponentesTexto() != null && !d.getComboComponentesTexto().isBlank()) {
                    item.put("componentes", d.getComboComponentesTexto());
                }
                item.put("cantidad", d.getCantidad());
                item.put("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
            }

            // Despacho interno in-process sin abrir sockets TCP raw ni hilos no gestionados
            ServidorSockets.getInstance().emitirMensaje(json);
        } catch (RuntimeException e) {
            LOGGER.fine("Servidor KDS no activo al emitir notificación interna: " + e.getMessage());
        }
    }

    /**
     * Lista los ítems activos de cocina directamente desde BD gestionando su propia conexión HikariCP.
     */
    public List<DetallePedido> listarItemsPendientesCocina() throws SQLException {
        try {
            return tx.executeChecked(conn -> pedidoDAO.listarItemsPendientesCocina(conn));
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Lista los pedidos activos de cocina directamente desde BD gestionando su propia conexión HikariCP.
     */
    public List<Pedido> listarPedidosPendientesCocina() throws SQLException {
        try {
            return tx.executeChecked(conn -> pedidoDAO.listarPedidosPendientesCocina(conn));
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Lista los ítems de cocina pendientes de un pedido dado directamente desde BD.
     */
    public List<DetallePedido> listarItemsPendientesCocinaPorPedido(int idPedido) throws SQLException {
        if (idPedido <= 0) {
            return List.of();
        }
        try {
            return tx.executeChecked(conn -> pedidoDAO.listarItemsPendientesCocinaPorPedido(conn, idPedido));
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Actualiza el estado de cocina de un ítem y, de forma coherente, el estado del pedido asociado.
     */
    public void actualizarEstadoKds(int idDetallePedido, int idPedido, String estado) throws SQLException {
        marcarItemEstadoYActualizarPedidoKds(idDetallePedido, idPedido, estado);
    }

    /**
     * Marca un ítem de cocina con el estado indicado y actualiza el pedido asociado siempre a {@code EN_PREPARACION},
     * propagando otros estados al pedido solo cuando no quedan ítems pendientes (v5.8.0).
     */
    public void marcarItemEnPreparacionKds(int idDetallePedido, int idPedido) throws SQLException {
        try {
            tx.executeVoidChecked(conn -> {
                if (idDetallePedido > 0) {
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "EN_PREPARACION");
                }
                if (idPedido > 0) {
                    pedidoDAO.actualizarEstadoPedido(conn, idPedido, "EN_PREPARACION");
                }
            });
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Marca un ítem de cocina con el estado indicado y, solo cuando el pedido asociado ya no tiene
     * ítems pendientes de cocina, propaga el mismo estado al pedido (v5.8.0).
     * Encapsula {@link ConexionBD#getConnection()} para que los controladores no gestionen conexiones JDBC.
     */
    public void marcarItemEstadoYActualizarPedidoKds(int idDetallePedido, int idPedido, String nuevoEstadoDetalle)
            throws SQLException {
        try {
            tx.executeVoidChecked(conn -> {
                if (idDetallePedido > 0) {
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, nuevoEstadoDetalle);
                }
                if (idPedido > 0 && !pedidoDAO.tieneItemsPendientesCocina(conn, idPedido)) {
                    pedidoDAO.actualizarEstadoPedido(conn, idPedido, nuevoEstadoDetalle);
                }
            });
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Consulta el estado de cocina actual de un ítem directamente desde BD.
     *
     * @return el estado normalizado, o {@code null} si el ítem no existe
     */
    public String obtenerEstadoCocinaDetalle(int idDetallePedido) throws SQLException {
        try {
            return tx.executeChecked(conn -> pedidoDAO.obtenerEstadoCocinaDetalle(conn, idDetallePedido));
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Marca como ENTREGADO únicamente los ítems indicados y el pedido asociado sin afectar otros pedidos en cocina.
     */
    public void marcarPedidoEntregadoKds(List<Integer> idsDetalle, int idPedido) throws SQLException {
        try {
            tx.executeVoidChecked(conn -> {
                if (idsDetalle != null) {
                    for (int idDet : idsDetalle) {
                        if (idDet > 0) {
                            pedidoDAO.actualizarEstadoDetalleCocina(conn, idDet, "ENTREGADO");
                        }
                    }
                }
                if (idPedido > 0) {
                    pedidoDAO.actualizarEstadoPedido(conn, idPedido, "ENTREGADO");
                }
            });
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Persiste el orden manual del KDS asignando prioridades descendentes en {@code detalle_pedido} (v5.2.0).
     */
    public void reordenarPrioridadKds(List<Integer> idsDetalleOrdenDescendente) throws SQLException {
        if (idsDetalleOrdenDescendente == null || idsDetalleOrdenDescendente.isEmpty()) {
            return;
        }
        try {
            tx.executeVoidChecked(conn ->
                pedidoDAO.actualizarPrioridadesCocina(conn, idsDetalleOrdenDescendente)
            );
        } catch (NegocioException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }
}
