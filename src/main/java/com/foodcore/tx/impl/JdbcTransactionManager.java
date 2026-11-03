package com.foodcore.tx.impl;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.tx.TransactionManager;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación JDBC del gestor transaccional explícito (SPEC-FOODCORE-v1.0 §7.1, §16).
 * Asigna un {@code idOperacion} UUID correlacionado para trazabilidad de auditoría.
 */
public class JdbcTransactionManager implements TransactionManager {

    private static final Logger LOGGER = Logger.getLogger(JdbcTransactionManager.class.getName());

    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionSupplier connectionSupplier;

    public JdbcTransactionManager() {
        this(ConexionBD::getConnection);
    }

    public JdbcTransactionManager(ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "El proveedor de conexiones no puede ser nulo.");
    }

    @Override
    public <T> T execute(Function<Connection, T> work) {
        Objects.requireNonNull(work, "La unidad de trabajo transaccional no puede ser nula.");
        try {
            return executeChecked(work::apply);
        } catch (NegocioException | SQLException e) {
            throw new IllegalStateException("Fallo en unidad de trabajo transaccional: " + e.getMessage(), e);
        }
    }

    @Override
    public void executeVoid(Consumer<Connection> work) {
        Objects.requireNonNull(work, "La unidad de trabajo transaccional no puede ser nula.");
        execute(conn -> {
            work.accept(conn);
            return null;
        });
    }

    @Override
    public <T> T executeChecked(CheckedWork<T> work) throws NegocioException, SQLException {
        Objects.requireNonNull(work, "La unidad de trabajo transaccional no puede ser nula.");
        String idOperacion = UUID.randomUUID().toString();

        try (Connection conn = connectionSupplier.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T resultado = work.apply(conn);
                conn.commit();
                LOGGER.fine(() -> String.format("[idOperacion=%s] Transacción ACID confirmada (COMMIT).", idOperacion));
                return resultado;
            } catch (NegocioException ne) {
                rollbackSilencioso(conn, idOperacion, ne);
                throw ne;
            } catch (SQLException se) {
                rollbackSilencioso(conn, idOperacion, se);
                throw se;
            } catch (RuntimeException re) {
                rollbackSilencioso(conn, idOperacion, re);
                if (re.getCause() instanceof NegocioException neCause) {
                    throw neCause;
                }
                if (re.getCause() instanceof SQLException seCause) {
                    throw seCause;
                }
                throw re;
            } catch (Exception ex) {
                rollbackSilencioso(conn, idOperacion, ex);
                throw new NegocioException(CodigoError.ERROR_TRANSACCIONAL,
                    "Fallo inesperado en transacción [idOperacion=" + idOperacion + "]: " + ex.getMessage(), ex);
            } finally {
                try {
                    conn.setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {
                    // La conexión se devolverá al pool HikariCP en el cierre del try-with-resources
                }
            }
        }
    }

    @Override
    public void executeVoidChecked(CheckedVoidWork work) throws NegocioException, SQLException {
        Objects.requireNonNull(work, "La unidad de trabajo transaccional no puede ser nula.");
        executeChecked(conn -> {
            work.accept(conn);
            return null;
        });
    }

    private void rollbackSilencioso(Connection conn, String idOperacion, Throwable causa) {
        try {
            conn.rollback();
            LOGGER.log(Level.WARNING,
                String.format("[idOperacion=%s] Transacción abortada (ROLLBACK) por: %s", idOperacion, causa.getMessage()));
        } catch (SQLException rbEx) {
            causa.addSuppressed(rbEx);
            LOGGER.log(Level.SEVERE,
                String.format("[idOperacion=%s] Error crítico ejecutando ROLLBACK.", idOperacion), rbEx);
        }
    }
}
