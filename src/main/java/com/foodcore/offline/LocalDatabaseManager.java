package com.foodcore.offline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Conexión y esquema a Base de Datos Local Embebida SQLite (Local-First)
 * conforme a SPEC-FOODCORE-v1.3-ENTERPRISE (§1.1, §1.2).
 * Almacena en la terminal las tablas mínimas: {@code item_vendible}, {@code mesa},
 * {@code pedido}, {@code detalle_pedido} y {@code outbox_sync}.
 */
public final class LocalDatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(LocalDatabaseManager.class.getName());
    private static final String DEFAULT_DB_PATH = "data/offline/terminal_local.db";
    private static String dbPath = DEFAULT_DB_PATH;

    private LocalDatabaseManager() {}

    public static synchronized void setDbPath(String customPath) {
        dbPath = customPath != null ? customPath : DEFAULT_DB_PATH;
    }

    public static synchronized String getDbPath() {
        return dbPath;
    }

    public static synchronized Connection getLocalConnection() throws SQLException {
        OfflineCryptoService.requireKey();

        Path dbFilePath = Path.of(dbPath).toAbsolutePath().normalize();
        Path parent = dbFilePath.getParent();
        try {
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
                aplicarPermisosRestrictivos(parent);
            }
            if (Files.notExists(dbFilePath)) {
                Files.createFile(dbFilePath);
            }
            aplicarPermisosRestrictivos(dbFilePath);
        } catch (IOException ex) {
            throw new SQLException(
                    "No se pudo preparar permisos seguros para la base de datos local: " + dbFilePath, ex);
        }

        String url = "jdbc:sqlite:" + dbPath;
        Connection conn = DriverManager.getConnection(url);
        try {
            ejecutarDdlSeguro(conn, "PRAGMA foreign_keys = ON;");
            aplicarPermisosRestrictivos(dbFilePath);
            return conn;
        } catch (SQLException | IOException | RuntimeException ex) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // Conservar la excepción original al fallar inicialización segura
            }
            if (ex instanceof SQLException sqlEx) {
                throw sqlEx;
            }
            throw new SQLException("Fallo al asegurar la conexión SQLite local.", ex);
        }
    }

    static void aplicarPermisosRestrictivos(Path targetPath) throws IOException {
        if (targetPath == null || Files.notExists(targetPath)) {
            return;
        }
        PosixFileAttributeView posixView = Files.getFileAttributeView(targetPath, PosixFileAttributeView.class);
        if (posixView != null) {
            posixView.setPermissions(PosixFilePermissions.fromString(
                    Files.isDirectory(targetPath) ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView aclView = Files.getFileAttributeView(targetPath, AclFileAttributeView.class);
        if (aclView != null) {
            UserPrincipal owner = aclView.getOwner();
            AclEntry.Builder builder = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (Files.isDirectory(targetPath)) {
                builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
            }
            aclView.setAcl(List.of(builder.build()));
        }
    }

    private static void ejecutarDdlSeguro(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    public static synchronized void inicializarEsquemaLocal() throws SQLException {
        try (Connection conn = getLocalConnection()) {

            // 1. Tabla local item_vendible / productos
            ejecutarDdlSeguro(conn, """
                CREATE TABLE IF NOT EXISTS item_vendible (
                    id_producto INTEGER PRIMARY KEY,
                    codigo TEXT,
                    nombre TEXT NOT NULL,
                    precio_venta REAL NOT NULL,
                    id_categoria INTEGER NOT NULL DEFAULT 1,
                    nombre_categoria TEXT,
                    disponible INTEGER NOT NULL DEFAULT 1,
                    activo INTEGER NOT NULL DEFAULT 1
                );
            """);

            // 2. Tabla local mesa
            ejecutarDdlSeguro(conn, """
                CREATE TABLE IF NOT EXISTS mesa (
                    id_mesa INTEGER PRIMARY KEY,
                    numero TEXT NOT NULL UNIQUE,
                    estado TEXT NOT NULL DEFAULT 'LIBRE',
                    activo INTEGER NOT NULL DEFAULT 1
                );
            """);

            // 3. Tabla local pedido
            ejecutarDdlSeguro(conn, """
                CREATE TABLE IF NOT EXISTS pedido (
                    id_pedido_local INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_pedido_servidor INTEGER,
                    id_usuario INTEGER NOT NULL DEFAULT 1,
                    numero_mesa TEXT,
                    tipo_pedido TEXT NOT NULL DEFAULT 'MESA',
                    cliente_nombre TEXT,
                    cliente_telefono TEXT,
                    cliente_direccion TEXT,
                    cliente_referencia TEXT,
                    costo_envio REAL NOT NULL DEFAULT 0.00,
                    total REAL NOT NULL DEFAULT 0.00,
                    estado TEXT NOT NULL DEFAULT 'REGISTRADO',
                    idempotency_key TEXT NOT NULL UNIQUE,
                    fecha_creacion TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
                );
            """);

            // 4. Tabla local detalle_pedido
            ejecutarDdlSeguro(conn, """
                CREATE TABLE IF NOT EXISTS detalle_pedido (
                    id_detalle_local INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_pedido_local INTEGER NOT NULL,
                    id_producto INTEGER NOT NULL,
                    nombre_producto TEXT,
                    cantidad INTEGER NOT NULL DEFAULT 1,
                    precio_unitario REAL NOT NULL,
                    subtotal REAL NOT NULL,
                    notas_preparacion TEXT,
                    FOREIGN KEY (id_pedido_local) REFERENCES pedido(id_pedido_local) ON DELETE CASCADE
                );
            """);

            // 5. Tabla local outbox_sync (§1.2 Patrón Outbox Local)
            ejecutarDdlSeguro(conn, """
                CREATE TABLE IF NOT EXISTS outbox_sync (
                    id_outbox INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_pedido_local INTEGER NOT NULL,
                    idempotency_key TEXT NOT NULL UNIQUE,
                    payload_json TEXT NOT NULL,
                    estado TEXT NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE, EN_PROCESO, SINCRONIZADO, RECHAZADO
                    intentos INTEGER NOT NULL DEFAULT 0,
                    ultimo_error TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    FOREIGN KEY (id_pedido_local) REFERENCES pedido(id_pedido_local) ON DELETE CASCADE
                );
            """);

            LOGGER.info("Esquema SQLite local para Modo Offline inicializado exitosamente.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Fallo al inicializar la base de datos SQLite local", e);
            throw e;
        }
    }
}
