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
 * Implementación JDBC del gestor transaccional explícito (SPEC-FOODCORE-v1.0 §7.1, §16; REMEDIATION R-10, R-11).
 * <ul>
 *   <li>Asigna {@code idOperacion} UUID correlacionado para trazabilidad de auditoría.</li>
 *   <li>Reintenta con backoff exponencial ({@code 100ms, 200ms, 400ms}, máx 3 intentos) ante deadlocks
 *       InnoDB (error MySQL {@code 1213}) o timeout de bloqueo ({@code 1205}).</li>
 *   <li>Propaga directamente excepciones de dominio sin envolverlas en {@link SQLException}.</li>
 * </ul>
 */
public class JdbcTransactionManager implements TransactionManager {

    private static final Logger LOGGER = Logger.getLogger(JdbcTransactionManager.class.getName());

    public static final int MAX_RETRIES = 3;
    public static final long BASE_BACKOFF_MS = 100L;

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

        for (int intento = 1; intento <= MAX_RETRIES; intento++) {
            try {
                return executeOnce(work, idOperacion, intento);
            } catch (SQLException se) {
                if (!esDeadlockOTimeout(se) || intento == MAX_RETRIES) {
                    throw se;
                }
                long esperaMs = BASE_BACKOFF_MS * (1L << (intento - 1));
                LOGGER.warning(String.format(
                    "[idOperacion=%s] Deadlock/LockTimeout detectado (errorCode=%d, intento=%d/%d). Reintentando en %d ms...",
                    idOperacion, se.getErrorCode(), intento, MAX_RETRIES, esperaMs
                ));
                dormirBackoff(esperaMs);
            } catch (RuntimeException re) {
                if (re.getCause() instanceof SQLException seCause && esDeadlockOTimeout(seCause) && intento < MAX_RETRIES) {
                    long esperaMs = BASE_BACKOFF_MS * (1L << (intento - 1));
                    LOGGER.warning(String.format(
                        "[idOperacion=%s] Deadlock/LockTimeout envuelto (errorCode=%d, intento=%d/%d). Reintentando en %d ms...",
                        idOperacion, seCause.getErrorCode(), intento, MAX_RETRIES, esperaMs
                    ));
                    dormirBackoff(esperaMs);
                    continue;
                }
                throw re;
            }
        }
        throw new IllegalStateException("Inalcanzable: fin de reintentos transaccionales [idOperacion=" + idOperacion + "]");
    }

    private <T> T executeOnce(CheckedWork<T> work, String idOperacion, int intento) throws NegocioException, SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T resultado = work.apply(conn);
                conn.commit();
                LOGGER.info(String.format(
                    "[idOperacion=%s] [intento=%d] Transacción ACID confirmada (COMMIT).",
                    idOperacion, intento
                ));
                return resultado;
            } catch (NegocioException ne) {
                rollbackSilencioso(conn, idOperacion, intento, ne);
                throw ne;
            } catch (SQLException se) {
                rollbackSilencioso(conn, idOperacion, intento, se);
                throw se;
            } catch (RuntimeException re) {
                rollbackSilencioso(conn, idOperacion, intento, re);
                throw re;
            } catch (Exception ex) {
                rollbackSilencioso(conn, idOperacion, intento, ex);
                throw new NegocioException(CodigoError.ERROR_TRANSACCIONAL,
                    "Fallo inesperado en transacción [idOperacion=" + idOperacion + "]: " + ex.getMessage(), ex);
            } finally {
                try {
                    conn.setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {
                    // La conexión se cierra en el bloque try-with-resources
                }
            }
        }
    }

    /**
     * Identifica errores transitorios de concurrencia en InnoDB:
     * MySQL {@code 1213} (Deadlock found when trying to get lock) y {@code 1205} (Lock wait timeout exceeded),
     * o SQLState {@code 40001} (Serialization failure).
     */
    public static boolean esDeadlockOTimeout(SQLException e) {
        if (e == null) {
            return false;
        }
        int code = e.getErrorCode();
        String state = e.getSQLState();
        return code == 1213 || code == 1205 || "40001".equals(state);
    }

    private void dormirBackoff(long esperaMs) {
        try {
            Thread.sleep(esperaMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hilo interrumpido durante backoff de deadlock", ie);
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

    private void rollbackSilencioso(Connection conn, String idOperacion, int intento, Throwable causa) {
        try {
            conn.rollback();
            LOGGER.log(Level.WARNING, String.format(
                "[idOperacion=%s] [intento=%d] Transacción abortada (ROLLBACK) por: %s",
                idOperacion, intento, causa.getMessage()
            ));
        } catch (SQLException rbEx) {
            causa.addSuppressed(rbEx);
            LOGGER.log(Level.SEVERE, String.format(
                "[idOperacion=%s] [intento=%d] Error crítico ejecutando ROLLBACK.",
                idOperacion, intento
            ), rbEx);
        }
    }
}
