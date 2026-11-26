package com.foodcore.dao;

import java.sql.Connection;

/**
 * DAO de verificación de estado y disponibilidad de base de datos y migraciones Flyway.
 */
public interface HealthCheckDAO {

    /** Resultado de verificación de base de datos e historial Flyway. */
    record ReadinessStatus(boolean dbUp, int migracionesAplicadas) {
        public boolean isReady() {
            return dbUp && migracionesAplicadas > 0;
        }
    }

    ReadinessStatus evaluarReadiness();

    ReadinessStatus evaluarReadiness(Connection conn);
}
