package com.foodcore.support;

import com.foodcore.config.FlywayMigrator;
import com.foodcore.util.ConexionBD;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Infraestructura compartida de pruebas de integración contra MySQL 8.0 real (InnoDB)
 * según SPEC-FOODCORE-v1.0 (§15.2; REMEDIATION R-02, R-05).
 * Soporta contenedor efímero Testcontainers MySQL 8 o instancia local MySQL 8.0 en 127.0.0.1:3306.
 */
public final class MySqlIntegrationTestSupport {

    private static MySQLContainer<?> container;
    private static String activeJdbcUrl;
    private static String activeUser;
    private static String activePass;
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
                container = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("foodcore_test")
                    .withUsername("foodcore_migrator")
                    .withPassword("FoodCoreMigrator#2026!");
                container.start();
                activeJdbcUrl = container.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";
                activeUser = container.getUsername();
                activePass = container.getPassword();
            } else {
                activeJdbcUrl = "jdbc:mysql://127.0.0.1:3306/foodcore_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";
                activeUser = "foodcore_migrator";
                activePass = "FoodCoreMigrator#2026!";

                // Asegurar también que la BD principal foodcore tenga aplicadas las migraciones V1..V4 (R-05)
                String prodUrl = "jdbc:mysql://127.0.0.1:3306/foodcore?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";
                Flyway prodFlyway = Flyway.configure()
                    .dataSource(prodUrl, activeUser, activePass)
                    .locations(FlywayMigrator.MIGRATIONS_LOCATION)
                    .cleanDisabled(false)
                    .validateOnMigrate(true)
                    .baselineOnMigrate(false)
                    .load();
                prodFlyway.clean();
                prodFlyway.migrate();
            }
            initialized = true;
        }

        reiniciarEsquemaConFlyway();
        ConexionBD.reconfigurarPool(activeJdbcUrl, activeUser, activePass);
    }

    public static synchronized MigrateResult reiniciarEsquemaConFlyway() {
        Flyway flyway = Flyway.configure()
            .dataSource(activeJdbcUrl, activeUser, activePass)
            .locations(FlywayMigrator.MIGRATIONS_LOCATION)
            .cleanDisabled(false)
            .validateOnMigrate(true)
            .baselineOnMigrate(false)
            .load();
        flyway.clean();
        return flyway.migrate();
    }

    public static MigrateResult ejecutarMigrateIncremental() {
        return FlywayMigrator.migrate(activeJdbcUrl, activeUser, activePass);
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
        return activeUser;
    }

    public static String getActivePass() {
        return activePass;
    }
}
