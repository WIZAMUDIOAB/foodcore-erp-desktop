package com.foodcore.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor Singleton del Pool de Conexiones HikariCP optimizado para red local (LAN)
 * según SPEC-FOODCORE-v1.0 (§10.4, §17.2; REMEDIATION R-15).
 */
public final class ConexionBD {

    private static final Logger LOGGER = Logger.getLogger(ConexionBD.class.getName());
    private static HikariDataSource dataSource;
    private static String jdbcUrlInfo = "";

    private ConexionBD() {
    }

    public static synchronized void inicializarPool() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            Properties props = cargarPropiedades();

            String host = resolverValor(props.getProperty("db.host", "127.0.0.1"), "FOODCORE_DB_HOST", "127.0.0.1");
            String port = resolverValor(props.getProperty("db.port", "3306"), "FOODCORE_DB_PORT", "3306");
            String db = resolverValor(props.getProperty("db.name", "foodcore"), "FOODCORE_DB_NAME", "foodcore");
            String user = resolverValor(props.getProperty("db.user", "foodcore_app"), "FOODCORE_DB_USER", "foodcore_app");
            String pass = resolverValor(props.getProperty("db.password", "${FOODCORE_DB_PASS}"), "FOODCORE_DB_PASS", "FoodCoreApp#2026!");

            jdbcUrlInfo = "jdbc:mysql://" + host + ":" + port + "/" + db;
            String fullUrl = jdbcUrlInfo + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";

            configurarHikari(fullUrl, user, pass, props);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al inicializar el DataSource HikariCP", e);
        }
    }

    public static synchronized void reconfigurarPool(String fullJdbcUrl, String user, String pass) {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        jdbcUrlInfo = fullJdbcUrl;
        Properties props = cargarPropiedades();
        configurarHikari(fullJdbcUrl, user, pass, props);
    }

    private static void configurarHikari(String fullJdbcUrl, String user, String pass, Properties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(fullJdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setPoolName("FoodCoreHikariPool");

        config.setMaximumPoolSize(Integer.parseInt(props.getProperty("hikari.maximumPoolSize", "10")));
        config.setMinimumIdle(Integer.parseInt(props.getProperty("hikari.minimumIdle", "2")));
        config.setIdleTimeout(Long.parseLong(props.getProperty("hikari.idleTimeout", "30000")));
        boolean esCi = "true".equalsIgnoreCase(System.getenv("CI")) || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
        config.setConnectionTimeout(esCi ? 1200L : Long.parseLong(props.getProperty("hikari.connectionTimeout", "10000")));
        config.setInitializationFailTimeout(-1L);
        config.setMaxLifetime(Long.parseLong(props.getProperty("hikari.maxLifetime", "600000")));

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);
        LOGGER.info("Pool de conexiones HikariCP inicializado correctamente: " + jdbcUrlInfo);
    }

    private static Properties cargarPropiedades() {
        Properties props = new Properties();
        try (InputStream inTest = ConexionBD.class.getResourceAsStream("/application-test.properties")) {
            if (inTest != null) {
                props.load(inTest);
                return props;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo leer /application-test.properties", e);
        }
        try (InputStream inApp = ConexionBD.class.getResourceAsStream("/application.properties")) {
            if (inApp != null) {
                props.load(inApp);
                return props;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo leer /application.properties", e);
        }
        try (InputStream inDb = ConexionBD.class.getResourceAsStream("/database.properties")) {
            if (inDb != null) {
                props.load(inDb);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo leer /database.properties", e);
        }
        return props;
    }

    public static String resolverValor(String rawValue, String envVarName, String fallback) {
        String sysProp = System.getProperty(envVarName);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String envVal = System.getenv(envVarName);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        if (rawValue != null && !rawValue.isBlank() && !rawValue.startsWith("${")) {
            return rawValue;
        }
        return fallback;
    }

    public static synchronized DataSource getDataSource() {
        if (dataSource == null || dataSource.isClosed()) {
            inicializarPool();
        }
        return dataSource;
    }

    public static Connection getConnection() throws SQLException {
        DataSource ds = getDataSource();
        if (ds == null) {
            throw new SQLException("El DataSource HikariCP no ha podido inicializarse.");
        }
        return ds.getConnection();
    }

    public static boolean testConexion() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(2);
        } catch (Exception e) {
            LOGGER.warning("Fallo en verificación de conexión a la BD: " + e.getMessage());
            return false;
        }
    }

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

    public static synchronized void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Pool HikariCP cerrado.");
        }
    }
}
