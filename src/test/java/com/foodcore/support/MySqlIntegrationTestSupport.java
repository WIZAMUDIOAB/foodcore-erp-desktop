package com.foodcore.support;

import com.foodcore.config.FlywayMigrator;
import com.foodcore.util.ConexionBD;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

/**
 * Infraestructura compartida de pruebas de integración contra MySQL 8.0 real (InnoDB)
 * con TLS obligatorio (sslMode=REQUIRED) y cuentas diferenciadas migrador (DDL) / app (DML).
 */
public final class MySqlIntegrationTestSupport {

    private static MySQLContainer<?> container;
    private static String activeJdbcUrl;
    private static String activeMigratorUser;
    private static String activeMigratorPass;
    private static String activeAppUser;
    private static String activeAppPass;
    private static boolean initialized = false;

    private MySqlIntegrationTestSupport() {
    }

    public static synchronized void inicializarEntornoMySql() {
        if (!initialized) {
            boolean dockerDisponible = false;
            try {
                dockerDisponible = DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable ignored) {
                dockerDisponible = false;
            }

            if (dockerDisponible) {
                String rootPass = "R_" + UUID.randomUUID().toString().replace("-", "");
                String migratorPass = "M_" + UUID.randomUUID().toString().replace("-", "");
                String appPass = "A_" + UUID.randomUUID().toString().replace("-", "");
                container = new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("foodcore_test")
                    .withUsername("foodcore_migrator")
                    .withPassword(migratorPass)
                    .withEnv("MYSQL_ROOT_PASSWORD", rootPass);
                container.start();

                activeJdbcUrl = container.getJdbcUrl()
                    + "?sslMode=REQUIRED&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";
                activeMigratorUser = container.getUsername();
                activeMigratorPass = migratorPass;
                activeAppUser = "foodcore_app";
                activeAppPass = appPass;

                configurarUsuarioAplicacionEnContenedor(activeJdbcUrl, rootPass, activeAppUser, activeAppPass);
            } else {
                String host = ConexionBD.resolverValor("127.0.0.1", "FOODCORE_DB_HOST", "127.0.0.1");
                String port = ConexionBD.resolverValor("3306", "FOODCORE_DB_PORT", "3306");
                String db = ConexionBD.resolverValor("foodcore_test", "FOODCORE_DB_NAME", "foodcore_test");
                String sslMode = ConexionBD.resolverValor("REQUIRED", "FOODCORE_DB_SSL_MODE", "REQUIRED");

                activeJdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?sslMode=" + sslMode + "&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";
                ConexionBD.validarJdbcUrlSegura(activeJdbcUrl);

                activeMigratorUser = ConexionBD.resolverValor(
                    "foodcore_migrator",
                    "FOODCORE_MIGRATOR_USER",
                    "foodcore_migrator"
                );
                activeMigratorPass = ConexionBD.resolverValorObligatorio(
                    "${FOODCORE_MIGRATOR_PASS}",
                    "FOODCORE_MIGRATOR_PASS",
                    "FOODCORE_MIGRATOR_PASS"
                );
                activeAppUser = ConexionBD.resolverValor(
                    "foodcore_app",
                    "FOODCORE_DB_USER",
                    "foodcore_app"
                );
                activeAppPass = ConexionBD.resolverValorObligatorio(
                    "${FOODCORE_DB_PASS}",
                    "FOODCORE_DB_PASS",
                    "FOODCORE_DB_PASS"
                );

                boolean esCi = "true".equalsIgnoreCase(System.getenv("CI"))
                    || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
                if (!esCi) {
                    String localMainUrl = "jdbc:mysql://" + host + ":" + port + "/foodcore"
                        + "?sslMode=" + sslMode
                        + "&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";
                    Flyway prodFlyway = Flyway.configure()
                        .dataSource(localMainUrl, activeMigratorUser, activeMigratorPass)
                        .locations(FlywayMigrator.MIGRATIONS_LOCATION)
                        .cleanDisabled(false)
                        .validateOnMigrate(true)
                        .baselineOnMigrate(false)
                        .load();
                    prodFlyway.clean();
                    prodFlyway.migrate();
                }
            }
            initialized = true;
        }

        reiniciarEsquemaConFlyway();
        ConexionBD.reconfigurarPool(activeJdbcUrl, activeAppUser, activeAppPass);
    }

    private static void configurarUsuarioAplicacionEnContenedor(
            String jdbcUrl,
            String rootPass,
            String appUser,
            String appPass) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", rootPass);
             Statement st = conn.createStatement()) {
            String escapedPass = appPass.replace("'", "''");
            st.execute("CREATE USER IF NOT EXISTS '" + appUser + "'@'%' IDENTIFIED BY '" + escapedPass + "'");
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON foodcore_test.* TO '" + appUser + "'@'%'");
            st.execute("FLUSH PRIVILEGES");
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo aprovisionar usuario DML-only en contenedor de pruebas", e);
        }
    }

    public static synchronized MigrateResult reiniciarEsquemaConFlyway() {
        Flyway flyway = Flyway.configure()
            .dataSource(activeJdbcUrl, activeMigratorUser, activeMigratorPass)
            .locations(FlywayMigrator.MIGRATIONS_LOCATION)
            .cleanDisabled(false)
            .validateOnMigrate(true)
            .baselineOnMigrate(false)
            .load();
        flyway.clean();
        return flyway.migrate();
    }

    public static MigrateResult ejecutarMigrateIncremental() {
        return FlywayMigrator.migrate(activeJdbcUrl, activeMigratorUser, activeMigratorPass);
    }

    public static void ejecutarSql(String sql) throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    public static String getActiveJdbcUrl() {
        return activeJdbcUrl;
    }

    public static String getActiveUser() {
        return activeAppUser;
    }

    public static String getActivePass() {
        return activeAppPass;
    }
}
