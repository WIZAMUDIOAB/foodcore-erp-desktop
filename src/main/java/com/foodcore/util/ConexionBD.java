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
 */
public class ConexionBD {

    private static final Logger LOGGER = Logger.getLogger(ConexionBD.class.getName());
    private static HikariDataSource dataSource;

    private ConexionBD() {
    }

    static {
        try {
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

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
            config.setUsername(user);
            config.setPassword(pass);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Optimización de pool para LAN
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
            LOGGER.info("Pool de conexiones HikariCP inicializado correctamente.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al inicializar el DataSource HikariCP", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("El DataSource HikariCP no ha sido inicializado.");
        }
        return dataSource.getConnection();
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Pool HikariCP cerrado.");
        }
    }
}
