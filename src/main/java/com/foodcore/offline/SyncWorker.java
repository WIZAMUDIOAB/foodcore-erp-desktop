package com.foodcore.offline;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.service.PedidoService;
import com.foodcore.util.ConexionBD;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker en segundo plano para el Modo Offline y Sincronización Outbox (§1.1, §1.2).
 * Gestiona el caché local de productos/mesas, encolado de pedidos sin red en SQLite,
 * reintento automático periódico al servidor y notificación de estado ONLINE/OFFLINE.
 */
public class SyncWorker {

    private static final Logger LOGGER = Logger.getLogger(SyncWorker.class.getName());
    private static final Gson GSON = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.time.LocalDateTime.class, (com.google.gson.JsonSerializer<java.time.LocalDateTime>) (src, typeOfSrc, context) ->
                    new com.google.gson.JsonPrimitive(src != null ? src.toString() : ""))
            .registerTypeAdapter(java.time.LocalDateTime.class, (com.google.gson.JsonDeserializer<java.time.LocalDateTime>) (json, typeOfT, context) ->
                    json != null && !json.getAsString().isEmpty() ? java.time.LocalDateTime.parse(json.getAsString()) : null)
            .create();

    private static final SyncWorker INSTANCIA = new SyncWorker();

    private final AtomicBoolean redDisponible = new AtomicBoolean(true);
    private final PedidoService pedidoService = new PedidoService();
    private ScheduledExecutorService scheduler;
    private final List<SyncStatusListener> listeners = new ArrayList<>();

    public interface SyncStatusListener {
        void onStatusChanged(boolean online, int pedidosPendientes, String mensajeAlerta);
    }

    public static SyncWorker getInstancia() {
        return INSTANCIA;
    }

    private SyncWorker() {
        try {
            LocalDatabaseManager.inicializarEsquemaLocal();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo auto-inicializar SQLite local", e);
        }
    }

    public void addListener(SyncStatusListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            notificarListeners(null);
        }
    }

    public void removeListener(SyncStatusListener listener) {
        listeners.remove(listener);
    }

    public boolean isOnline() {
        return redDisponible.get();
    }

    public void setSimularDesconexionRed(boolean sinRed) {
        this.redDisponible.set(!sinRed);
        notificarListeners(sinRed ? "Modo Offline activado: Sin conexión con el servidor central." : "Red reestablecida.");
    }

    /**
     * Sincroniza el catálogo de productos y mesas hacia la BD local (§1.1).
     */
    public synchronized boolean sincronizarCatalogoHaciaLocal() {
        if (!verificarConectividadServidor()) {
            redDisponible.set(false);
            notificarListeners("Sin conexión: Operando con catálogo local en caché.");
            return false;
        }

        try (Connection remoteConn = ConexionBD.getConnection();
             Connection localConn = LocalDatabaseManager.getLocalConnection()) {

            // Sincronizar item_vendible
            try (Statement stRemote = remoteConn.createStatement();
                 ResultSet rsRemote = stRemote.executeQuery(
                     "SELECT p.id_producto, p.codigo, p.nombre, p.precio_venta, p.id_categoria, c.nombre as cat_nombre, p.disponible, p.activo " +
                     "FROM producto p LEFT JOIN categoria c ON p.id_categoria = c.id_categoria WHERE p.activo = 1");
                 PreparedStatement psLocal = localConn.prepareStatement(
                     "INSERT OR REPLACE INTO item_vendible (id_producto, codigo, nombre, precio_venta, id_categoria, nombre_categoria, disponible, activo) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

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

            // Sincronizar mesa
            try (Statement stRemote = remoteConn.createStatement();
                 ResultSet rsRemote = stRemote.executeQuery("SELECT id_mesa, numero, estado, activo FROM mesa WHERE activo = 1");
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

            redDisponible.set(true);
            notificarListeners(null);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al sincronizar catálogo remoto hacia BD local", e);
            redDisponible.set(false);
            notificarListeners("Error al sincronizar catálogo con servidor central.");
            return false;
        }
    }

    /**
     * Guarda el pedido localmente en SQLite y lo encola en outbox_sync (§1.2).
     */
    public synchronized int guardarPedidoEnOutboxLocal(Pedido pedido, List<DetallePedido> detalles) throws SQLException {
        try (Connection conn = LocalDatabaseManager.getLocalConnection()) {
            conn.setAutoCommit(false);
            try {
                String sqlPed = "INSERT INTO pedido (id_usuario, numero_mesa, tipo_pedido, cliente_nombre, " +
                    "cliente_telefono, cliente_direccion, cliente_referencia, costo_envio, total, estado, idempotency_key) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                int idPedidoLocal;
                try (PreparedStatement ps = conn.prepareStatement(sqlPed, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, pedido.getIdUsuario() > 0 ? pedido.getIdUsuario() : 1);
                    ps.setString(2, pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "DELIVERY");
                    ps.setString(3, pedido.getTipoPedido() != null ? pedido.getTipoPedido() : "MESA");
                    ps.setString(4, pedido.getClienteNombre());
                    ps.setString(5, pedido.getClienteTelefono());
                    ps.setString(6, pedido.getClienteDireccion());
                    ps.setString(7, pedido.getClienteReferencia());
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

                // Guardar detalles locales
                String sqlDet = "INSERT INTO detalle_pedido (id_pedido_local, id_producto, nombre_producto, cantidad, precio_unitario, subtotal, notas_preparacion) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
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

                // Encolar en outbox_sync
                JsonObject payload = new JsonObject();
                payload.add("pedido", GSON.toJsonTree(pedido));
                payload.add("detalles", GSON.toJsonTree(detalles));

                String sqlOutbox = "INSERT INTO outbox_sync (id_pedido_local, idempotency_key, payload_json, estado) VALUES (?, ?, ?, 'PENDIENTE')";
                try (PreparedStatement ps = conn.prepareStatement(sqlOutbox)) {
                    ps.setInt(1, idPedidoLocal);
                    ps.setString(2, pedido.getIdempotencyKey());
                    ps.setString(3, payload.toString());
                    ps.executeUpdate();
                }

                conn.commit();
                notificarListeners(null);
                return idPedidoLocal;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    /**
     * Procesa los registros pendientes de outbox_sync intentando enviarlos al servidor central (§1.2).
     */
    public synchronized int procesarOutboxSync() {
        if (!verificarConectividadServidor()) {
            redDisponible.set(false);
            notificarListeners(null);
            return 0;
        }

        redDisponible.set(true);
        int exitosos = 0;

        try (Connection conn = LocalDatabaseManager.getLocalConnection()) {
            String sqlSelect = "SELECT id_outbox, id_pedido_local, idempotency_key, payload_json, intentos FROM outbox_sync " +
                "WHERE estado = 'PENDIENTE' ORDER BY id_outbox ASC LIMIT 20";

            List<OutboxItem> pendientes = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sqlSelect)) {
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
                    JsonObject obj = GSON.fromJson(item.payloadJson, JsonObject.class);
                    Pedido pedido = GSON.fromJson(obj.get("pedido"), Pedido.class);
                    Type listType = new TypeToken<List<DetallePedido>>(){}.getType();
                    List<DetallePedido> detalles = GSON.fromJson(obj.get("detalles"), listType);

                    // Enviar al servidor central de forma transaccional e idempotente
                    Pedido pRegistrado = pedidoService.registrarPedidoYEnviarComanda(pedido, detalles);

                    // Actualizar outbox a SINCRONIZADO
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE outbox_sync SET estado = 'SINCRONIZADO', updated_at = datetime('now', 'localtime') WHERE id_outbox = ?")) {
                        ps.setInt(1, item.idOutbox);
                        ps.executeUpdate();
                    }

                    // Actualizar pedido local con el ID remoto
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE pedido SET id_pedido_servidor = ?, estado = ? WHERE id_pedido_local = ?")) {
                        ps.setInt(1, pRegistrado.getIdPedido());
                        ps.setString(2, pRegistrado.getEstado());
                        ps.setInt(3, item.idPedidoLocal);
                        ps.executeUpdate();
                    }
                    exitosos++;
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error al sincronizar item outbox #" + item.idOutbox, ex);
                    String errorMsg = ex.getMessage() != null ? ex.getMessage() : "Error desconocido";
                    boolean esConflictoMesa = errorMsg.toLowerCase().contains("ya está ocupada") ||
                                              errorMsg.toLowerCase().contains("mesa ocupada");

                    String nuevoEstado = esConflictoMesa ? "RECHAZADO" : "PENDIENTE";
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE outbox_sync SET estado = ?, intentos = intentos + 1, ultimo_error = ?, updated_at = datetime('now', 'localtime') WHERE id_outbox = ?")) {
                        ps.setString(1, nuevoEstado);
                        ps.setString(2, errorMsg);
                        ps.setInt(3, item.idOutbox);
                        ps.executeUpdate();
                    }

                    if (esConflictoMesa) {
                        try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE pedido SET estado = 'RECHAZADO' WHERE id_pedido_local = ?")) {
                            ps.setInt(1, item.idPedidoLocal);
                            ps.executeUpdate();
                        }
                        notificarListeners("¡Conflicto! El pedido fue rechazado por el servidor: " + errorMsg);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo al procesar cola outbox local", e);
        }

        notificarListeners(null);
        return exitosos;
    }

    public synchronized int contarPedidosPendientes() {
        try (Connection conn = LocalDatabaseManager.getLocalConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM outbox_sync WHERE estado = 'PENDIENTE'")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
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
            }, 5, 10, TimeUnit.SECONDS); // cada 10 segundos según §1.2
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
        try (Connection conn = ConexionBD.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    private void notificarListeners(String alerta) {
        boolean online = redDisponible.get();
        int pendientes = contarPedidosPendientes();
        for (SyncStatusListener l : listeners) {
            try {
                l.onStatusChanged(online, pendientes, alerta);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Error en listener de SyncWorker", ex);
            }
        }
    }

    private record OutboxItem(int idOutbox, int idPedidoLocal, String idempotencyKey, String payloadJson, int intentos) {}
}
