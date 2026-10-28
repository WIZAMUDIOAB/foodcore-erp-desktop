package com.foodcore.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor Singleton del Pool de Conexiones HikariCP optimizado para red local (LAN).
 * Responsabilidad de Integrante 1 (Líder Técnico de Persistencia).
 */
public class ConexionBD {

    private static final Logger LOGGER = Logger.getLogger(ConexionBD.class.getName());
    private static HikariDataSource dataSource;
    private static String jdbcUrlInfo = "";

    private ConexionBD() {
    }

    static {
        inicializarPool();
    }

    public static synchronized void inicializarPool() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            Properties props = new Properties();
            InputStream in = ConexionBD.class.getResourceAsStream("/database.properties");
            if (in != null) {
                props.load(in);
            }

            String host = props.getProperty("db.host", "localhost");
            String port = props.getProperty("db.port", "3306");
            String db = props.getProperty("db.name", "foodcore_db");
            String user = props.getProperty("db.user", "root");
            String pass = props.getProperty("db.password", "root");

            jdbcUrlInfo = "jdbc:mysql://" + host + ":" + port + "/" + db;

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrlInfo + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8");
            config.setUsername(user);
            config.setPassword(pass);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setPoolName("FoodCoreHikariPool");

            // Optimización de pool para LAN (Baja latencia y control de conexiones)
            config.setMaximumPoolSize(Integer.parseInt(props.getProperty("hikari.maximumPoolSize", "10")));
            config.setMinimumIdle(Integer.parseInt(props.getProperty("hikari.minimumIdle", "2")));
            config.setIdleTimeout(Long.parseLong(props.getProperty("hikari.idleTimeout", "30000")));
            config.setConnectionTimeout(Long.parseLong(props.getProperty("hikari.connectionTimeout", "10000")));
            config.setMaxLifetime(Long.parseLong(props.getProperty("hikari.maxLifetime", "600000")));

            // Cache de Prepared Statements para alto rendimiento
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);
            LOGGER.info("Pool de conexiones HikariCP inicializado correctamente: " + jdbcUrlInfo);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al inicializar el DataSource HikariCP", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            inicializarPool();
        }
        if (dataSource == null) {
            throw new SQLException("El DataSource HikariCP no ha podido inicializarse.");
        }
        return dataSource.getConnection();
    }

    /**
     * Prueba el estado de la conexión en vivo contra MySQL.
     * @return true si la conexión responde en menos de 2 segundos.
     */
    public static boolean testConexion() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(2);
        } catch (Exception e) {
            LOGGER.warning("Fallo en verificación de conexión a la BD: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retorna métricas en tiempo real del pool de conexiones HikariCP.
     */
    public static String getEstadoPool() {
        if (dataSource == null || dataSource.isClosed()) {
            return "Desconectado";
        }
        try {
            int activas = dataSource.getHikariPoolMXBean().getActiveConnections();
            int inactivas = dataSource.getHikariPoolMXBean().getIdleConnections();
            int total = dataSource.getHikariPoolMXBean().getTotalConnections();
            return String.format("Activas: %d | Disponibles: %d | Total en Pool: %d", activas, inactivas, total);
        } catch (Exception e) {
            return "Activo (" + jdbcUrlInfo + ")";
        }
    }

    public static String getJdbcUrlInfo() {
        return jdbcUrlInfo;
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Pool HikariCP cerrado.");
        }
    }
}
