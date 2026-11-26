package com.foodcore.tx.impl;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias determinísticas para {@link JdbcTransactionManager} sin esperas reales ni BD externa.
 */
class JdbcTransactionManagerUnitTest {

    @Test
    @DisplayName("execute, executeVoid y executeVoidChecked confirman transacción, restauran autoCommit y cierran")
    void executeYExecuteVoidYExecuteVoidCheckedConfirmanYRestauranAutoCommit() throws Exception {
        ConnectionTracker conn1 = crearConexionMock(true, false, false);
        JdbcTransactionManager tx1 = new JdbcTransactionManager(() -> conn1.proxy);

        String resultado = tx1.execute(conn -> "ok-tx");
        assertEquals("ok-tx", resultado);
        assertEquals(1, conn1.commits.get());
        assertEquals(0, conn1.rollbacks.get());
        assertTrue(conn1.closed.get());
        assertEquals(List.of(false, true), conn1.autoCommitHistory);

        ConnectionTracker conn2 = crearConexionMock(false, false, false);
        JdbcTransactionManager tx2 = new JdbcTransactionManager(() -> conn2.proxy);
        AtomicInteger invocado = new AtomicInteger();
        tx2.executeVoid(conn -> invocado.incrementAndGet());
        assertEquals(1, invocado.get());
        assertEquals(1, conn2.commits.get());
        assertEquals(List.of(false, false), conn2.autoCommitHistory);

        ConnectionTracker conn3 = crearConexionMock(true, false, false);
        JdbcTransactionManager tx3 = new JdbcTransactionManager(() -> conn3.proxy);
        tx3.executeVoidChecked(conn -> invocado.incrementAndGet());
        assertEquals(2, invocado.get());
        assertEquals(1, conn3.commits.get());
        assertTrue(conn3.closed.get());
    }

    @Test
    @DisplayName("Reintenta con backoff exponencial (100ms, 200ms) en deadlock 1213, lock timeout 1205 y SQLState 40001")
    void reintentaConBackoffExponencialEnDeadlockYLockTimeout() throws Exception {
        List<ConnectionTracker> conexiones = List.of(
                crearConexionMock(true, false, false),
                crearConexionMock(true, false, false),
                crearConexionMock(true, false, false)
        );
        AtomicInteger indice = new AtomicInteger(0);
        List<Long> esperasRegistradas = new ArrayList<>();

        JdbcTransactionManager tx = new JdbcTransactionManager(
                () -> conexiones.get(indice.getAndIncrement()).proxy,
                esperasRegistradas::add
        );

        String res = tx.executeChecked(conn -> {
            int intentoActual = indice.get();
            if (intentoActual == 1) {
                throw new SQLException("Deadlock found", "40001", 1213);
            }
            if (intentoActual == 2) {
                throw new SQLException("Lock wait timeout", "HY000", 1205);
            }
            return "recuperado";
        });

        assertEquals("recuperado", res);
        assertEquals(List.of(100L, 200L), esperasRegistradas);
        assertEquals(1, conexiones.get(0).rollbacks.get());
        assertEquals(1, conexiones.get(1).rollbacks.get());
        assertEquals(0, conexiones.get(2).rollbacks.get());
        assertEquals(1, conexiones.get(2).commits.get());

        List<Long> esperas40001 = new ArrayList<>();
        AtomicInteger intento40001 = new AtomicInteger(0);
        JdbcTransactionManager txSqlState = new JdbcTransactionManager(
                () -> crearConexionMock(true, false, false).proxy,
                esperas40001::add
        );
        String res40001 = txSqlState.executeChecked(conn -> {
            if (intento40001.incrementAndGet() == 1) {
                throw new SQLException("Serialization failure", "40001", 9999);
            }
            return "ok-40001";
        });
        assertEquals("ok-40001", res40001);
        assertEquals(List.of(100L), esperas40001);
    }

    @Test
    @DisplayName("Reintenta cuando RuntimeException envuelve SQLException de deadlock y luego tiene éxito")
    void reintentaCuandoRuntimeExceptionEnvuelveDeadlock() {
        AtomicInteger intentos = new AtomicInteger(0);
        List<Long> esperas = new ArrayList<>();
        JdbcTransactionManager tx = new JdbcTransactionManager(
                () -> crearConexionMock(true, false, false).proxy,
                esperas::add
        );

        String res = tx.execute(conn -> {
            if (intentos.incrementAndGet() == 1) {
                throw new IllegalStateException("Envuelto", new SQLException("Deadlock", "40001", 1213));
            }
            return "listo";
        });

        assertEquals("listo", res);
        assertEquals(2, intentos.get());
        assertEquals(List.of(100L), esperas);
    }

    @Test
    @DisplayName("Agota MAX_RETRIES en deadlock persistente y no espera después del último intento")
    void agotaMaximoReintentosYPropagaExcepcion() {
        List<Long> esperasSql = new ArrayList<>();
        JdbcTransactionManager txSql = new JdbcTransactionManager(
                () -> crearConexionMock(true, false, false).proxy,
                esperasSql::add
        );

        SQLException exSql = assertThrows(SQLException.class, () ->
                txSql.executeChecked(conn -> {
                    throw new SQLException("Deadlock persistente", "40001", 1213);
                })
        );
        assertEquals(1213, exSql.getErrorCode());
        assertEquals(List.of(100L, 200L), esperasSql);

        List<Long> esperasRuntime = new ArrayList<>();
        JdbcTransactionManager txRt = new JdbcTransactionManager(
                () -> crearConexionMock(true, false, false).proxy,
                esperasRuntime::add
        );
        RuntimeException exRt = assertThrows(RuntimeException.class, () ->
                txRt.execute(conn -> {
                    throw new IllegalStateException("Deadlock rt", new SQLException("Timeout", "HY000", 1205));
                })
        );
        assertEquals("Deadlock rt", exRt.getMessage());
        assertEquals(List.of(100L, 200L), esperasRuntime);
    }

    @Test
    @DisplayName("Errores no transitorios hacen rollback inmediato sin reintentos y preservan jerarquía")
    void erroresNoTransitoriosHacenRollbackSinReintentos() {
        ConnectionTracker connNeg = crearConexionMock(true, false, false);
        List<Long> esperas = new ArrayList<>();
        JdbcTransactionManager tx = new JdbcTransactionManager(() -> connNeg.proxy, esperas::add);

        NegocioException ne = assertThrows(NegocioException.class, () ->
                tx.executeChecked(conn -> {
                    throw new NegocioException(CodigoError.VALIDACION_ENTRADA, "Dato inválido");
                })
        );
        assertEquals(CodigoError.VALIDACION_ENTRADA, ne.getCodigoError());
        assertEquals(1, connNeg.rollbacks.get());
        assertTrue(esperas.isEmpty());

        SQLException sqlNoTransitoria = assertThrows(SQLException.class, () ->
                tx.executeChecked(conn -> {
                    throw new SQLException("Duplicate key", "23000", 1062);
                })
        );
        assertEquals(1062, sqlNoTransitoria.getErrorCode());

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () ->
                tx.execute(conn -> {
                    throw new IllegalArgumentException("Argumento inválido");
                })
        );
        assertEquals("Argumento inválido", iae.getMessage());

        RuntimeException rtConCausaNoDeadlock = assertThrows(RuntimeException.class, () ->
                tx.execute(conn -> {
                    throw new IllegalStateException("Error BD no transitorio", new SQLException("Syntax error", "42000", 1064));
                })
        );
        assertInstanceOf(SQLException.class, rtConCausaNoDeadlock.getCause());
        assertTrue(esperas.isEmpty());

        NegocioException voidNegEx = assertThrows(NegocioException.class, () ->
                tx.executeVoidChecked(conn -> {
                    throw new NegocioException(CodigoError.VALIDACION_ENTRADA, "Error void checked");
                })
        );
        assertEquals("Error void checked", voidNegEx.getMessage());

        JdbcTransactionManager txFalloConexion = new JdbcTransactionManager(() -> {
            throw new SQLException("Sin conexión", "08001", 2002);
        }, esperas::add);
        IllegalStateException wrappedConnectionEx = assertThrows(IllegalStateException.class, () ->
                txFalloConexion.execute(conn -> "nunca")
        );
        assertInstanceOf(SQLException.class, wrappedConnectionEx.getCause());
    }

    @Test
    @DisplayName("Fallo en rollback adjunta excepción suprimida y error en setAutoCommit de finally no enmascara")
    void falloRollbackYAutoCommitEnFinallyNoEnmascaran() {
        ConnectionTracker connFallaRollback = crearConexionMock(true, true, true);
        JdbcTransactionManager txRb = new JdbcTransactionManager(() -> connFallaRollback.proxy);

        NegocioException original = new NegocioException(CodigoError.STOCK_INSUFICIENTE, "Sin stock");
        NegocioException capturada = assertThrows(NegocioException.class, () ->
                txRb.executeChecked(conn -> {
                    throw original;
                })
        );
        assertEquals(1, capturada.getSuppressed().length);
        assertEquals("Error simulado en rollback", capturada.getSuppressed()[0].getMessage());
        assertTrue(connFallaRollback.closed.get());

        ConnectionTracker connFallaAutoCommitFinally = crearConexionMock(true, false, true);
        JdbcTransactionManager txFinally = new JdbcTransactionManager(() -> connFallaAutoCommitFinally.proxy);
        assertEquals("ok", txFinally.execute(conn -> "ok"));
        assertEquals(1, connFallaAutoCommitFinally.commits.get());
    }

    @Test
    @DisplayName("esDeadlockOTimeout y validaciones de argumentos nulos cubren casos límite")
    void utilitariosYValidacionesNulas() {
        assertFalse(JdbcTransactionManager.esDeadlockOTimeout(null));
        assertFalse(JdbcTransactionManager.esDeadlockOTimeout(new SQLException("Otro", "23000", 1062)));
        assertTrue(JdbcTransactionManager.esDeadlockOTimeout(new SQLException("Deadlock", "HY000", 1213)));
        assertTrue(JdbcTransactionManager.esDeadlockOTimeout(new SQLException("Timeout", "HY000", 1205)));
        assertTrue(JdbcTransactionManager.esDeadlockOTimeout(new SQLException("Serialization", "40001", 0)));

        assertThrows(NullPointerException.class, () -> new JdbcTransactionManager(null));
        JdbcTransactionManager tx = new JdbcTransactionManager(() -> crearConexionMock(true, false, false).proxy);
        assertThrows(NullPointerException.class, () -> tx.execute(null));
        assertThrows(NullPointerException.class, () -> tx.executeVoid(null));
        assertThrows(NullPointerException.class, () -> tx.executeChecked(null));
        assertThrows(NullPointerException.class, () -> tx.executeVoidChecked(null));
    }

    private static ConnectionTracker crearConexionMock(
            boolean initialAutoCommit,
            boolean failOnRollback,
            boolean failOnRestoreAutoCommit) {
        ConnectionTracker tracker = new ConnectionTracker(initialAutoCommit);
        Connection proxy = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (p, method, args) -> {
                    String name = method.getName();
                    switch (name) {
                        case "getAutoCommit":
                            return tracker.autoCommit;
                        case "setAutoCommit":
                            boolean val = (Boolean) args[0];
                            if (failOnRestoreAutoCommit && val && !tracker.autoCommit) {
                                throw new SQLException("Error simulado restaurando autoCommit");
                            }
                            tracker.autoCommit = val;
                            tracker.autoCommitHistory.add(val);
                            return null;
                        case "commit":
                            tracker.commits.incrementAndGet();
                            return null;
                        case "rollback":
                            tracker.rollbacks.incrementAndGet();
                            if (failOnRollback) {
                                throw new SQLException("Error simulado en rollback");
                            }
                            return null;
                        case "close":
                            tracker.closed.set(true);
                            return null;
                        case "isClosed":
                            return tracker.closed.get();
                        default:
                            return valorPorDefecto(method.getReturnType());
                    }
                }
        );
        tracker.proxy = proxy;
        return tracker;
    }

    private static Object valorPorDefecto(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    private static final class ConnectionTracker {
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final List<Boolean> autoCommitHistory = new ArrayList<>();
        private boolean autoCommit;
        private Connection proxy;

        private ConnectionTracker(boolean initialAutoCommit) {
            this.autoCommit = initialAutoCommit;
        }
    }
}
