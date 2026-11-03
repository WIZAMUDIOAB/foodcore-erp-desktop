package com.foodcore.config;

import com.foodcore.util.ConexionBD;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ejecutor de migraciones versionadas Flyway según SPEC-FOODCORE-v1.0 (§12, §14; REMEDIATION R-05).
 * Configurado con {@code validateOnMigrate = true} y {@code baselineOnMigrate = false}.
 */
public final class FlywayMigrator {

    private static final Logger LOGGER = Logger.getLogger(FlywayMigrator.class.getName());
    public static final String MIGRATIONS_LOCATION = "classpath:db/migration";

    private FlywayMigrator() {
    }

    public static MigrateResult migrateOnBootstrap() {
        try {
            Properties props = new Properties();
            try (InputStream in = FlywayMigrator.class.getResourceAsStream("/application.properties")) {
                if (in != null) {
                    props.load(in);
                }
            }
            String host = ConexionBD.resolverValor(props.getProperty("db.host", "127.0.0.1"), "FOODCORE_DB_HOST", "127.0.0.1");
            String port = ConexionBD.resolverValor(props.getProperty("db.port", "3306"), "FOODCORE_DB_PORT", "3306");
            String db = ConexionBD.resolverValor(props.getProperty("db.name", "foodcore"), "FOODCORE_DB_NAME", "foodcore");
            String migratorUser = ConexionBD.resolverValor(props.getProperty("db.migrator.user", "foodcore_migrator"), "FOODCORE_MIGRATOR_USER", "foodcore_migrator");
            String migratorPass = ConexionBD.resolverValor(props.getProperty("db.migrator.password", "${FOODCORE_MIGRATOR_PASS}"), "FOODCORE_MIGRATOR_PASS", "FoodCoreMigrator#2026!");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=UTF-8";
            return migrate(url, migratorUser, migratorPass);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error durante migrateOnBootstrap Flyway", e);
            return null;
        }
    }

    public static MigrateResult migrate(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(MIGRATIONS_LOCATION)
            .validateOnMigrate(true)
            .baselineOnMigrate(false)
            .load();
        MigrateResult result = flyway.migrate();
        LOGGER.info(() -> String.format(
            "Flyway migración completada: %d scripts aplicados (target version: %s).",
            result.migrationsExecuted, result.targetSchemaVersion
        ));
        return result;
    }

    public static MigrateResult migrate(String jdbcUrl, String user, String password) {
        Flyway flyway = Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations(MIGRATIONS_LOCATION)
            .validateOnMigrate(true)
            .baselineOnMigrate(false)
            .load();
        MigrateResult result = flyway.migrate();
        LOGGER.info(() -> String.format(
            "Flyway migración completada: %d scripts aplicados (target version: %s).",
            result.migrationsExecuted, result.targetSchemaVersion
        ));
        return result;
    }
}
