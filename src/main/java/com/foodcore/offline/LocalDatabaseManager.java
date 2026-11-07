package com.foodcore.offline;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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

    public static synchronized Connection getLocalConnection() throws SQLException {
        File dbFile = new File(dbPath);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            LOGGER.log(Level.WARNING, "No se pudo crear el directorio de base de datos local: " + parent.getAbsolutePath());
        }
        String url = "jdbc:sqlite:" + dbPath;
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    public static synchronized void inicializarEsquemaLocal() throws SQLException {
        try (Connection conn = getLocalConnection();
             Statement st = conn.createStatement()) {

            // 1. Tabla local item_vendible / productos
            st.execute("""
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
            st.execute("""
                CREATE TABLE IF NOT EXISTS mesa (
                    id_mesa INTEGER PRIMARY KEY,
                    numero TEXT NOT NULL UNIQUE,
                    estado TEXT NOT NULL DEFAULT 'LIBRE',
                    activo INTEGER NOT NULL DEFAULT 1
                );
            """);

            // 3. Tabla local pedido
            st.execute("""
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
            st.execute("""
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
            st.execute("""
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
