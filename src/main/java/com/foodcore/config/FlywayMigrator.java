package com.foodcore.config;

import com.foodcore.util.ConexionBD;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.util.logging.Logger;

/**
 * Ejecutor de migraciones versionadas Flyway según SPEC-FOODCORE-v1.0 (§12, §14; REMEDIATION R-05).
 * Configurado con {@code validateOnMigrate = true} y {@code baselineOnMigrate = false}.
 */
public final class FlywayMigrator {

    private static final Logger LOGGER = Logger.getLogger(FlywayMigrator.class.getName());
    public static final String MIGRATIONS_LOCATION = "classpath:db/migration";

    public FlywayMigrator() {
    }

    public static void main(String[] args) {
        MigrateResult res = migrateOnBootstrap();
        if (res != null) {
            LOGGER.info(() -> "Flyway migración completada vía CLI/CI: " + res.migrationsExecuted + " scripts aplicados.");
        } else {
            LOGGER.warning("Flyway migrateOnBootstrap finalizó sin ejecutar migraciones.");
        }
    }

    public static MigrateResult migrateOnBootstrap() {
        ConexionBD.DatabaseConfig cfg = ConexionBD.validarVariablesEntornoObligatorias();
        return migrate(cfg.jdbcUrl(), cfg.migratorUser(), cfg.migratorPassword());
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
        ConexionBD.validarJdbcUrlSegura(jdbcUrl);
        if (user == null || user.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Credenciales del migrador Flyway obligatorias (FOODCORE_MIGRATOR_PASS).");
        }
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
