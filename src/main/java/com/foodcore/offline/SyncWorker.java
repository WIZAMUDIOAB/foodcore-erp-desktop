package com.foodcore.offline;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.dto.OfflineOrderPayloadDTO;
import com.foodcore.service.PedidoService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.JsonMapperProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker en segundo plano para el Modo Offline y Sincronización Outbox (§1.1, §1.2).
 * Gestiona el caché local transaccional de productos/mesas, encolado cifrado en SQLite,
 * límite Dead Letter ({@link #MAX_INTENTOS_OUTBOX}) y notificación segura de estado ONLINE/OFFLINE.
 */
public class SyncWorker {

    public static final int MAX_INTENTOS_OUTBOX = 5;

    private static final Logger LOGGER = Logger.getLogger(SyncWorker.class.getName());

    private static final SyncWorker INSTANCIA = new SyncWorker();

    private final AtomicBoolean redDisponible = new AtomicBoolean(true);
    private final AtomicBoolean simularDesconexion = new AtomicBoolean(false);
    private final PedidoService pedidoService = new PedidoService();
    private ScheduledExecutorService scheduler;
    private String esquemaInicializadoRuta;
    private final CopyOnWriteArrayList<SyncStatusListener> listeners = new CopyOnWriteArrayList<>();

    public interface SyncStatusListener {
        void onStatusChanged(boolean online, int pedidosPendientes, String mensajeAlerta);
    }

    public static SyncWorker getInstancia() {
        return INSTANCIA;
    }

    private SyncWorker() {
        // Inicialización diferida: la base de datos SQLite cifrada se crea únicamente cuando se activa o usa modo offline.
    }

    private synchronized void asegurarEsquemaInicializado() throws SQLException {
        OfflineCryptoService.requireKey();
        String rutaActual = LocalDatabaseManager.getDbPath();
        Path dbFile = Path.of(rutaActual).toAbsolutePath().normalize();
        boolean archivoInexistenteOVacio;
        try {
            archivoInexistenteOVacio = Files.notExists(dbFile) || Files.size(dbFile) == 0;
        } catch (IOException ex) {
            throw new SQLException("No se pudo verificar estado de la base de datos SQLite local.", ex);
        }
        if (!rutaActual.equals(esquemaInicializadoRuta) || archivoInexistenteOVacio) {
            LocalDatabaseManager.inicializarEsquemaLocal();
            esquemaInicializadoRuta = rutaActual;
        }
    }

    public void addListener(SyncStatusListener listener) {
        if (listener != null && listeners.addIfAbsent(listener)) {
            notificarListeners(null);
        }
    }

    public void removeListener(SyncStatusListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public int getCantidadListeners() {
        return listeners.size();
    }

    public boolean isOnline() {
        return redDisponible.get() && !simularDesconexion.get();
    }

    public boolean isSimularDesconexionRed() {
        return simularDesconexion.get();
    }

    public void setSimularDesconexionRed(boolean sinRed) {
        if (sinRed) {
            OfflineCryptoService.requireKey();
            try {
                asegurarEsquemaInicializado();
            } catch (SQLException ex) {
                throw new IllegalStateException("No se pudo inicializar almacenamiento SQLite local cifrado.", ex);
            }
        }
        this.simularDesconexion.set(sinRed);
        this.redDisponible.set(!sinRed);
        notificarListeners(sinRed ? "Modo Offline activado: Sin conexión con el servidor central." : "Red reestablecida.");
    }

    /**
     * Sincroniza el catálogo de productos y mesas hacia la BD local en una única transacción SQLite,
     * desactivando previamente los ítems o mesas que fueron retirados en el servidor central (§1.1).
     */
    public synchronized boolean sincronizarCatalogoHaciaLocal() {
        OfflineCryptoService.requireKey();
        if (!verificarConectividadServidor()) {
            redDisponible.set(false);
            notificarListeners("Sin conexión: Operando con catálogo local en caché.");
            return false;
        }

        try {
            asegurarEsquemaInicializado();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al preparar esquema SQLite local cifrado", e);
            return false;
        }

        try (Connection remoteConn = ConexionBD.getConnection();
             Connection localConn = LocalDatabaseManager.getLocalConnection()) {

            boolean prevAutoCommit = localConn.getAutoCommit();
            localConn.setAutoCommit(false);
            try {
                // 1. Marcar catálogo previo como inactivo antes de aplicar la instantánea vigente
                try (PreparedStatement psDeactProd = localConn.prepareStatement(
                        "UPDATE item_vendible SET activo = 0, disponible = 0")) {
                    psDeactProd.executeUpdate();
                }

                String sqlProd = "SELECT p.id_producto, p.codigo, p.nombre, p.precio_venta, p.id_categoria, c.nombre as cat_nombre, p.disponible, p.activo "
                        + "FROM producto p LEFT JOIN categoria c ON p.id_categoria = c.id_categoria WHERE p.activo = 1";
                try (PreparedStatement stRemote = remoteConn.prepareStatement(sqlProd);
                     ResultSet rsRemote = stRemote.executeQuery();
                     PreparedStatement psLocal = localConn.prepareStatement(
                         "INSERT OR REPLACE INTO item_vendible (id_producto, codigo, nombre, precio_venta, id_categoria, nombre_categoria, disponible, activo) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                    while (rsRemote.next()) {
                        psLocal.setInt(1, rsRemote.getInt("id_producto"));
                        psLocal.setString(2, rsRemote.getString("codigo"));
                        psLocal.setString(3, rsRemote.getString("nombre"));
                        psLocal.setDouble(4, rsRemote.getDouble("precio_venta"));
                        psLocal.setInt(5, rsRemote.getInt("id_categoria"));
                        psLocal.setString(6, rsRemote.getString("cat_nombre"));
                        psLocal.setInt(7, rsRemote.getInt("disponible"));
                        psLocal.setInt(8, rsRemote.getInt("activo"));
                        psLocal.addBatch();
                    }
                    psLocal.executeBatch();
                }

                // 2. Marcar mesas previas como inactivas y sincronizar vigentes
                try (PreparedStatement psDeactMesa = localConn.prepareStatement(
                        "UPDATE mesa SET activo = 0")) {
                    psDeactMesa.executeUpdate();
                }

                try (PreparedStatement stRemote = remoteConn.prepareStatement("SELECT id_mesa, numero, estado, activo FROM mesa WHERE activo = 1");
                     ResultSet rsRemote = stRemote.executeQuery();
                     PreparedStatement psLocal = localConn.prepareStatement(
                         "INSERT OR REPLACE INTO mesa (id_mesa, numero, estado, activo) VALUES (?, ?, ?, ?)")) {

                    while (rsRemote.next()) {
                        psLocal.setInt(1, rsRemote.getInt("id_mesa"));
                        psLocal.setString(2, rsRemote.getString("numero"));
                        psLocal.setString(3, rsRemote.getString("estado"));
                        psLocal.setInt(4, rsRemote.getInt("activo"));
                        psLocal.addBatch();
                    }
                    psLocal.executeBatch();
                }

                localConn.commit();
            } catch (SQLException | RuntimeException txEx) {
                rollbackSinOcultar(localConn, txEx);
                throw txEx;
            } finally {
                restaurarAutoCommitSeguro(localConn, prevAutoCommit);
            }

            redDisponible.set(true);
            notificarListeners(null);
            return true;
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al sincronizar catálogo remoto hacia BD local", e);
            redDisponible.set(false);
            notificarListeners("Error al sincronizar catálogo con servidor central.");
            return false;
        }
    }

    /**
     * Guarda el pedido localmente en SQLite con campos sensibles cifrados y lo encola en outbox_sync (§1.2).
     */
    public synchronized int guardarPedidoEnOutboxLocal(Pedido pedido, List<DetallePedido> detalles) throws SQLException {
        String cryptoKey = OfflineCryptoService.requireKey();
        asegurarEsquemaInicializado();

        try (Connection conn = LocalDatabaseManager.getLocalConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sqlPed = "INSERT INTO pedido (id_usuario, numero_mesa, tipo_pedido, cliente_nombre, "
                    + "cliente_telefono, cliente_direccion, cliente_referencia, costo_envio, total, estado, idempotency_key) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                int idPedidoLocal;
                try (PreparedStatement ps = conn.prepareStatement(sqlPed, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, pedido.getIdUsuario() > 0 ? pedido.getIdUsuario() : 1);
                    ps.setString(2, pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "DELIVERY");
                    ps.setString(3, pedido.getTipoPedido() != null ? pedido.getTipoPedido() : "MESA");
                    ps.setString(4, OfflineCryptoService.encrypt(cryptoKey, pedido.getClienteNombre()));
                    ps.setString(5, OfflineCryptoService.encrypt(cryptoKey, pedido.getClienteTelefono()));
                    ps.setString(6, OfflineCryptoService.encrypt(cryptoKey, pedido.getClienteDireccion()));
                    ps.setString(7, OfflineCryptoService.encrypt(cryptoKey, pedido.getClienteReferencia()));
                    ps.setDouble(8, pedido.getCostoEnvio() != null ? pedido.getCostoEnvio().doubleValue() : 0.0);
                    ps.setDouble(9, pedido.getTotal() != null ? pedido.getTotal().doubleValue() : 0.0);
                    ps.setString(10, pedido.getEstado() != null ? pedido.getEstado() : "REGISTRADO");
                    ps.setString(11, pedido.getIdempotencyKey());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            idPedidoLocal = rs.getInt(1);
                        } else {
                            throw new SQLException("No se pudo obtener el ID local del pedido generado.");
                        }
                    }
                }

                String sqlDet = "INSERT INTO detalle_pedido (id_pedido_local, id_producto, nombre_producto, cantidad, precio_unitario, subtotal, notas_preparacion) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlDet)) {
                    for (DetallePedido dp : detalles) {
                        ps.setInt(1, idPedidoLocal);
                        ps.setInt(2, dp.getIdProducto());
                        ps.setString(3, dp.getNombreProducto());
                        ps.setInt(4, dp.getCantidad());
                        ps.setDouble(5, dp.getPrecioUnitario().doubleValue());
                        ps.setDouble(6, dp.getSubtotal().doubleValue());
                        ps.setString(7, dp.getNotasPreparacion());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                OfflineOrderPayloadDTO payload = new OfflineOrderPayloadDTO(pedido, detalles);

                String payloadCifrado = OfflineCryptoService.encrypt(cryptoKey, JsonMapperProvider.getMapper().writeValueAsString(payload));
                String sqlOutbox = "INSERT INTO outbox_sync (id_pedido_local, idempotency_key, payload_json, estado) VALUES (?, ?, ?, 'PENDIENTE')";
                try (PreparedStatement ps = conn.prepareStatement(sqlOutbox)) {
                    ps.setInt(1, idPedidoLocal);
                    ps.setString(2, pedido.getIdempotencyKey());
                    ps.setString(3, payloadCifrado);
                    ps.executeUpdate();
                }

                conn.commit();
                notificarListeners(null);
                return idPedidoLocal;
            } catch (SQLException | RuntimeException ex) {
                rollbackSinOcultar(conn, ex);
                throw ex;
            } finally {
                restaurarAutoCommitSeguro(conn, prevAutoCommit);
            }
        }
    }

    /**
     * Procesa los registros pendientes de outbox_sync descifrando sus payloads e intentando enviarlos al servidor (§1.2).
     * Los pedidos que exceden {@link #MAX_INTENTOS_OUTBOX} pasan a estado Dead Letter {@code RECHAZADO}
     * para evitar bloqueo head-of-line.
     */
    public synchronized int procesarOutboxSync() {
        String cryptoKey = OfflineCryptoService.requireKey();
        if (!verificarConectividadServidor()) {
            redDisponible.set(false);
            notificarListeners(null);
            return 0;
        }

        redDisponible.set(true);
        int exitosos = 0;

        try {
            asegurarEsquemaInicializado();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Fallo al preparar esquema SQLite local cifrado", e);
            return 0;
        }

        try (Connection conn = LocalDatabaseManager.getLocalConnection()) {
            purgarRegistrosSincronizados(conn);
            String sqlSelect = "SELECT id_outbox, id_pedido_local, idempotency_key, payload_json, intentos FROM outbox_sync "
                + "WHERE estado = 'PENDIENTE' ORDER BY id_outbox ASC LIMIT 20";

            List<OutboxItem> pendientes = new ArrayList<>();
            try (PreparedStatement st = conn.prepareStatement(sqlSelect);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    pendientes.add(new OutboxItem(
                        rs.getInt("id_outbox"),
                        rs.getInt("id_pedido_local"),
                        rs.getString("idempotency_key"),
                        rs.getString("payload_json"),
                        rs.getInt("intentos")
                    ));
                }
            }

            for (OutboxItem item : pendientes) {
                try {
                    String jsonPlano = OfflineCryptoService.decrypt(cryptoKey, item.payloadJson);
                    OfflineOrderPayloadDTO obj = JsonMapperProvider.getMapper().readValue(jsonPlano, OfflineOrderPayloadDTO.class);
                    Pedido pedido = obj.getPedido();
                    List<DetallePedido> detalles = obj.getDetalles();

                    pedidoService.registrarPedidoYEnviarComanda(pedido, detalles);

                    eliminarRegistroSincronizado(conn, item.idOutbox, item.idPedidoLocal);
                    exitosos++;
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error al sincronizar item outbox #" + item.idOutbox, ex);
                    String errorMsg = ex.getMessage() != null ? ex.getMessage() : "Error desconocido";
                    String lowerErr = errorMsg.toLowerCase(Locale.ROOT);
                    boolean esConflictoMesa = lowerErr.contains("ya está ocupada") || lowerErr.contains("mesa ocupada");
                    int nuevosIntentos = item.intentos + 1;
                    boolean esDeadLetter = nuevosIntentos >= MAX_INTENTOS_OUTBOX;
                    boolean rechazarDefinitivo = esConflictoMesa || esDeadLetter;

                    String nuevoEstado = rechazarDefinitivo ? "RECHAZADO" : "PENDIENTE";
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE outbox_sync SET estado = ?, intentos = ?, ultimo_error = ?, updated_at = datetime('now', 'localtime') WHERE id_outbox = ?")) {
                        ps.setString(1, nuevoEstado);
                        ps.setInt(2, nuevosIntentos);
                        ps.setString(3, errorMsg);
                        ps.setInt(4, item.idOutbox);
                        ps.executeUpdate();
                    }

                    if (rechazarDefinitivo) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE pedido SET estado = 'RECHAZADO' WHERE id_pedido_local = ?")) {
                            ps.setInt(1, item.idPedidoLocal);
                            ps.executeUpdate();
                        }
                        String alertaMsg = esConflictoMesa
                            ? "¡Conflicto! El pedido fue rechazado por el servidor: " + errorMsg
                            : "Pedido offline #" + item.idOutbox + " marcado como RECHAZADO tras " + nuevosIntentos + " intentos: " + errorMsg;
                        notificarListeners(alertaMsg);
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Fallo al procesar cola outbox local", e);
        }

        notificarListeners(null);
        return exitosos;
    }

    public synchronized int contarPedidosPendientes() {
        if (!OfflineCryptoService.isKeyConfigured() && redDisponible.get()) {
            return 0;
        }
        try {
            asegurarEsquemaInicializado();
        } catch (SQLException e) {
            return 0;
        }
        try (Connection conn = LocalDatabaseManager.getLocalConnection();
             PreparedStatement st = conn.prepareStatement("SELECT COUNT(*) FROM outbox_sync WHERE estado = 'PENDIENTE'");
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Purga filas marcadas como {@code SINCRONIZADO} y pedidos huérfanos ya sincronizados.
     */
    public synchronized int purgarRegistrosSincronizados() throws SQLException {
        OfflineCryptoService.requireKey();
        asegurarEsquemaInicializado();
        try (Connection conn = LocalDatabaseManager.getLocalConnection()) {
            return purgarRegistrosSincronizados(conn);
        }
    }

    private void eliminarRegistroSincronizado(Connection conn, int idOutbox, int idPedidoLocal) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM outbox_sync WHERE id_outbox = ? OR estado = 'SINCRONIZADO'")) {
                ps.setInt(1, idOutbox);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM detalle_pedido WHERE id_pedido_local = ?")) {
                ps.setInt(1, idPedidoLocal);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM pedido WHERE id_pedido_local = ? AND NOT EXISTS (SELECT 1 FROM outbox_sync o WHERE o.id_pedido_local = pedido.id_pedido_local)")) {
                ps.setInt(1, idPedidoLocal);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException | RuntimeException ex) {
            rollbackSinOcultar(conn, ex);
            throw ex;
        } finally {
            restaurarAutoCommitSeguro(conn, prevAutoCommit);
        }
    }

    private int purgarRegistrosSincronizados(Connection conn) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int purgados;
            try (PreparedStatement psOut = conn.prepareStatement("DELETE FROM outbox_sync WHERE estado = 'SINCRONIZADO'")) {
                purgados = psOut.executeUpdate();
            }
            try (PreparedStatement psDet = conn.prepareStatement(
                    "DELETE FROM detalle_pedido WHERE id_pedido_local NOT IN (SELECT id_pedido_local FROM outbox_sync)")) {
                psDet.executeUpdate();
            }
            try (PreparedStatement psPed = conn.prepareStatement(
                    "DELETE FROM pedido WHERE id_pedido_local NOT IN (SELECT id_pedido_local FROM outbox_sync)")) {
                psPed.executeUpdate();
            }
            conn.commit();
            return purgados;
        } catch (SQLException | RuntimeException ex) {
            rollbackSinOcultar(conn, ex);
            throw ex;
        } finally {
            restaurarAutoCommitSeguro(conn, prevAutoCommit);
        }
    }

    public static void rollbackSinOcultar(Connection conn, Throwable causaOriginal) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException rollbackEx) {
            if (causaOriginal != null) {
                causaOriginal.addSuppressed(rollbackEx);
            }
            LOGGER.log(Level.WARNING, "Fallo secundario al ejecutar rollback en SQLite local", rollbackEx);
        }
    }

    private static void restaurarAutoCommitSeguro(Connection conn, boolean prevAutoCommit) {
        if (conn == null) {
            return;
        }
        try {
            conn.setAutoCommit(prevAutoCommit);
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "No se pudo restaurar autoCommit en SQLite local", ex);
        }
    }

    public synchronized void iniciarServicioSyncPeriodico() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SyncWorker-Daemon");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    procesarOutboxSync();
                } catch (Throwable t) {
                    LOGGER.log(Level.FINE, "Error en ciclo periódico de sincronización", t);
                }
            }, 5, 10, TimeUnit.SECONDS);
            LOGGER.info("SyncWorker iniciado cada 10s.");
        }
    }

    public synchronized void detenerServicioSyncPeriodico() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public boolean verificarConectividadServidor() {
        if (simularDesconexion.get()) {
            return false;
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    private void notificarListeners(String alerta) {
        boolean online = isOnline();
        int pendientes = contarPedidosPendientes();
        for (SyncStatusListener l : listeners) {
            try {
                l.onStatusChanged(online, pendientes, alerta);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Error en listener de SyncWorker", ex);
            }
        }
    }

    private record OutboxItem(int idOutbox, int idPedidoLocal, String idempotencyKey, String payloadJson, int intentos) { }
}
