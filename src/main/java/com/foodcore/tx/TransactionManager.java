package com.foodcore.tx;

import com.foodcore.exception.NegocioException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Contrato de gestión transaccional explícita ACID (SPEC-FOODCORE-v1.0 §7.1).
 * Garantiza {@code autoCommit = false}, {@code commit} al éxito, {@code rollback}
 * ante cualquier fallo y cierre seguro de conexión mediante try-with-resources.
 */
public interface TransactionManager {

    @FunctionalInterface
    interface CheckedWork<T> {
        T apply(Connection conn) throws NegocioException, SQLException;
    }

    @FunctionalInterface
    interface CheckedVoidWork {
        void accept(Connection conn) throws NegocioException, SQLException;
    }

    /**
     * Contrato estándar funcional según §7.1.
     */
    <T> T execute(Function<Connection, T> work);

    /**
     * Contrato estándar void según §7.1.
     */
    void executeVoid(Consumer<Connection> work);

    /**
     * Variante verificada para servicios de dominio que declaran {@link NegocioException} y {@link SQLException}.
     */
    <T> T executeChecked(CheckedWork<T> work) throws NegocioException, SQLException;

    /**
     * Variante void verificada para servicios de dominio que declaran {@link NegocioException} y {@link SQLException}.
     */
    void executeVoidChecked(CheckedVoidWork work) throws NegocioException, SQLException;
}
