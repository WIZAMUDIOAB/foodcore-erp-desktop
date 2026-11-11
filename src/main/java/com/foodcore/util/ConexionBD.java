package com.foodcore.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor Singleton del Pool de Conexiones HikariCP optimizado para red local (LAN)
 * con validación fail-fast de credenciales obligatorias, TLS obligatorio y separación
 * estricta de cuentas entre aplicación (DML) y migraciones (DDL).
 */
public final class ConexionBD {

    private static final Logger LOGGER = Logger.getLogger(ConexionBD.class.getName());
    private static final Set<String> SSL_MODES_PERMITIDOS = Set.of("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");
    private static HikariDataSource dataSource;
    private static String jdbcUrlInfo = "";

    public record DatabaseConfig(
        String jdbcUrl,
        String sslMode,
        String appUser,
        String appPassword,
        String migratorUser,
        String migratorPassword
    ) {
    }

    private ConexionBD() {
    }

    public static synchronized void inicializarPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        Properties props = cargarPropiedades();
        DatabaseConfig cfg = validarConfiguracion(props);
        jdbcUrlInfo = cfg.jdbcUrl();

        configurarHikari(cfg.jdbcUrl(), cfg.appUser(), cfg.appPassword(), props);
    }

    public static DatabaseConfig validarVariablesEntornoObligatorias() {
        Properties props = cargarPropiedades();
        return validarConfiguracion(props);
    }

    public static DatabaseConfig validarConfiguracion(Properties props) {
        return validarConfiguracion(props, System::getenv);
    }

    public static DatabaseConfig validarConfiguracion(Properties props, UnaryOperator<String> envLookup) {
        if (props == null) {
            throw new IllegalStateException("No se pudieron cargar propiedades de configuración de base de datos.");
        }
        UnaryOperator<String> lookup = envLookup == null ? System::getenv : envLookup;
        String host = resolverValor(props.getProperty("db.host", "127.0.0.1"), "FOODCORE_DB_HOST", "127.0.0.1", lookup);
        String port = resolverValor(props.getProperty("db.port", "3306"), "FOODCORE_DB_PORT", "3306", lookup);
        String db = resolverValor(props.getProperty("db.name", "foodcore"), "FOODCORE_DB_NAME", "foodcore", lookup);
        String serverTimezone = resolverValor(
            props.getProperty("db.serverTimezone", "America/Lima"),
            "FOODCORE_DB_TIMEZONE",
            "America/Lima",
            lookup
        );
        String sslMode = resolverSslModeObligatorio(
            resolverValor(props.getProperty("db.sslMode", "REQUIRED"), "FOODCORE_DB_SSL_MODE", "REQUIRED", lookup)
        );

        String appUser = resolverValorObligatorio(
            props.getProperty("db.user"),
            "FOODCORE_DB_USER",
            "db.user / FOODCORE_DB_USER",
            lookup
        );
        String appPass = resolverValorObligatorio(
            props.getProperty("db.password"),
            "FOODCORE_DB_PASS",
            "db.password / FOODCORE_DB_PASS",
            lookup
        );
        String migratorUser = resolverValorObligatorio(
            props.getProperty("db.migrator.user"),
            "FOODCORE_MIGRATOR_USER",
            "db.migrator.user / FOODCORE_MIGRATOR_USER",
            lookup
        );
        String migratorPass = resolverValorObligatorio(
            props.getProperty("db.migrator.password"),
            "FOODCORE_MIGRATOR_PASS",
            "db.migrator.password / FOODCORE_MIGRATOR_PASS",
            lookup
        );

        if (appUser.equalsIgnoreCase(migratorUser)) {
            throw new IllegalStateException(
                "Configuración insegura: db.user (" + appUser + ") y db.migrator.user (" + migratorUser
                + ") deben ser cuentas distintas (DML app vs DDL migrator)."
            );
        }

        String jdbcUrl = construirJdbcUrlSegura(props, host, port, db, sslMode, serverTimezone, lookup);
        return new DatabaseConfig(jdbcUrl, sslMode, appUser, appPass, migratorUser, migratorPass);
    }

    private static String resolverSslModeObligatorio(String rawSslMode) {
        String normalized = rawSslMode == null ? "" : rawSslMode.trim().toUpperCase(Locale.ROOT);
        if (!SSL_MODES_PERMITIDOS.contains(normalized)) {
            throw new IllegalStateException(
                "Configuración insegura de TLS MySQL (db.sslMode='" + rawSslMode
                + "'): sólo se permiten " + SSL_MODES_PERMITIDOS + "."
            );
        }
        return normalized;
    }

    private static String construirJdbcUrlSegura(
            Properties props,
            String host,
            String port,
            String db,
            String sslMode,
            String serverTimezone,
            UnaryOperator<String> envLookup) {
        String envUrlOverride = resolverSobrescrituraExplicita("FOODCORE_DB_URL", envLookup);
        boolean tieneOverridePartes = resolverSobrescrituraExplicita("FOODCORE_DB_HOST", envLookup) != null
            || resolverSobrescrituraExplicita("FOODCORE_DB_PORT", envLookup) != null
            || resolverSobrescrituraExplicita("FOODCORE_DB_NAME", envLookup) != null
            || resolverSobrescrituraExplicita("FOODCORE_DB_SSL_MODE", envLookup) != null
            || resolverSobrescrituraExplicita("FOODCORE_DB_TIMEZONE", envLookup) != null;

        String propUrl = props.getProperty("db.url");
        String candidata;
        if (envUrlOverride != null) {
            candidata = envUrlOverride;
        } else if (!tieneOverridePartes && propUrl != null && !propUrl.isBlank() && !propUrl.contains("${")) {
            candidata = propUrl.trim();
        } else {
            candidata = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?sslMode=" + sslMode
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=" + serverTimezone
                + "&characterEncoding=UTF-8";
        }
        validarJdbcUrlSegura(candidata);
        return candidata;
    }

    public static void validarJdbcUrlSegura(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("La URL JDBC de MySQL no puede estar vacía.");
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("usessl=false")
                || lower.contains("sslmode=disabled")
                || lower.contains("sslmode=preferred")
                || lower.contains("sslmode=none")) {
            throw new IllegalStateException(
                "URL JDBC insegura rechazada (TLS deshabilitado o degradable): " + jdbcUrl
            );
        }
        if (!lower.contains("sslmode=required")
                && !lower.contains("sslmode=verify_ca")
                && !lower.contains("sslmode=verify_identity")) {
            throw new IllegalStateException(
                "URL JDBC rechazada por omitir sslMode=REQUIRED/VERIFY_CA/VERIFY_IDENTITY: " + jdbcUrl
            );
        }
    }

    public static synchronized void reconfigurarPool(String fullJdbcUrl, String user, String pass) {
        validarJdbcUrlSegura(fullJdbcUrl);
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            throw new IllegalStateException("Las credenciales para reconfigurar el pool HikariCP son obligatorias.");
        }
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
        cargarSiExiste(props, "/database.properties");
        cargarSiExiste(props, "/application.properties");
        cargarSiExiste(props, "/application-test.properties");
        return props;
    }

    private static void cargarSiExiste(Properties target, String resourcePath) {
        try (InputStream in = ConexionBD.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                target.load(in);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo leer " + resourcePath, e);
        }
    }

    public static String resolverValor(String rawValue, String envVarName, String fallback) {
        return resolverValor(rawValue, envVarName, fallback, System::getenv);
    }

    public static String resolverValor(
            String rawValue,
            String envVarName,
            String fallback,
            UnaryOperator<String> envLookup) {
        String override = resolverSobrescrituraExplicita(envVarName, envLookup);
        if (override != null) {
            return override;
        }
        if (rawValue != null && !rawValue.isBlank() && !rawValue.trim().startsWith("${")) {
            return rawValue.trim();
        }
        return fallback;
    }

    public static String resolverValorObligatorio(String rawValue, String envVarName, String nombreCampo) {
        return resolverValorObligatorio(rawValue, envVarName, nombreCampo, System::getenv);
    }

    public static String resolverValorObligatorio(
            String rawValue,
            String envVarName,
            String nombreCampo,
            UnaryOperator<String> envLookup) {
        String override = resolverSobrescrituraExplicita(envVarName, envLookup);
        if (override != null) {
            return override;
        }
        if (rawValue != null && !rawValue.isBlank() && !rawValue.trim().startsWith("${")) {
            return rawValue.trim();
        }
        throw new IllegalStateException(
            "Variable de entorno obligatoria no configurada o vacía: " + envVarName
            + " (propiedad requerida: " + nombreCampo + "). Configúrela antes de iniciar FoodCore ERP."
        );
    }

    private static String resolverSobrescrituraExplicita(String envVarName, UnaryOperator<String> envLookup) {
        String sysProp = System.getProperty(envVarName);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String envVal = envLookup.apply(envVarName);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        return null;
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
