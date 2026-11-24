package com.foodcore.dao.impl;

import com.foodcore.dao.HealthCheckDAO;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación JDBC para evaluación de readiness (/health) de base de datos y Flyway.
 */
public class HealthCheckDAOImpl implements HealthCheckDAO {

    private static final Logger LOGGER = Logger.getLogger(HealthCheckDAOImpl.class.getName());

    @Override
    public ReadinessStatus evaluarReadiness() {
        try (Connection conn = ConexionBD.getConnection()) {
            return evaluarReadiness(conn);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Readiness /health en espera de BD/Flyway", e);
            return new ReadinessStatus(false, 0);
        }
    }

    @Override
    public ReadinessStatus evaluarReadiness(Connection conn) {
        boolean dbOk = false;
        int migracionesAplicadas = 0;
        if (conn != null) {
            try {
                if (conn.isValid(2)) {
                    dbOk = true;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            migracionesAplicadas = rs.getInt(1);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "BD o historial Flyway aún no disponible para /health", e);
            }
        }
        return new ReadinessStatus(dbOk, migracionesAplicadas);
    }
}
