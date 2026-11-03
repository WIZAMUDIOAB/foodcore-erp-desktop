package com.foodcore.events;

import com.foodcore.model.OutboxEvento;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Despachador asíncrono de eventos {@code outbox_evento} con confirmación {@code ACK}
 * y backoff exponencial acotado a 60s según SPEC-FOODCORE-v1.0 (§11, §19.10; REMEDIATION R-13, R-02.6).
 */
public class OutboxDispatcher {

    private static final Logger LOGGER = Logger.getLogger(OutboxDispatcher.class.getName());
    public static final int MAX_INTENTOS = 10;
    public static final long MAX_BACKOFF_SEGUNDOS = 60L;
    public static final int ACK_TIMEOUT_MS = 5000;

    private final TransactionManager tx;
    private final OutboxRepository repository;

    public OutboxDispatcher() {
        this(new JdbcTransactionManager(), new OutboxRepository());
    }

    public OutboxDispatcher(TransactionManager tx, OutboxRepository repository) {
        this.tx = tx;
        this.repository = repository;
    }

    /**
     * Calcula el backoff exponencial en segundos:
     * {@code backoff(1)=1s, backoff(2)=2s, backoff(3)=4s, ..., backoff(n)=min(2^(n-1), 60)s}.
     */
    public static long calcularBackoffSegundos(int intento) {
        if (intento <= 1) {
            return 1L;
        }
        int shift = Math.min(intento - 1, 10);
        long calculado = 1L << shift;
        return Math.min(calculado, MAX_BACKOFF_SEGUNDOS);
    }

    /**
     * Procesa un ciclo de despacho sobre eventos {@code PENDIENTE} cuyo {@code proximo_intento <= NOW()}.
     *
     * @param canalEnvioConAck predicado que envía el evento por socket y devuelve {@code true} si recibe {@code ACK} en ≤5s
     * @return cantidad de eventos marcados como {@code ENVIADO}
     */
    public int despacharPendientes(Predicate<OutboxEvento> canalEnvioConAck)
            throws com.foodcore.exception.NegocioException, SQLException {
        return tx.executeChecked(conn -> {
            List<OutboxEvento> pendientes = repository.listarPendientesParaDespacho(conn);
            return procesarLote(conn, pendientes, canalEnvioConAck);
        });
    }

    /**
     * Reenvía todos los eventos {@code PENDIENTE} no confirmados al reconectarse una terminal KDS (§11, R-02.6).
     */
    public int reenviarPendientesEnReconexion(Predicate<OutboxEvento> canalEnvioConAck)
            throws com.foodcore.exception.NegocioException, SQLException {
        return tx.executeChecked(conn -> {
            List<OutboxEvento> pendientes = repository.listarTodosPendientes(conn);
            return procesarLote(conn, pendientes, canalEnvioConAck);
        });
    }

    private int procesarLote(java.sql.Connection conn,
                             List<OutboxEvento> eventos,
                             Predicate<OutboxEvento> canalEnvioConAck) throws SQLException {
        int enviados = 0;
        for (OutboxEvento ev : eventos) {
            boolean ackRecibido = false;
            try {
                ackRecibido = canalEnvioConAck.test(ev);
            } catch (Exception e) {
                LOGGER.warning("Fallo enviando evento outbox #" + ev.getIdEvento() + ": " + e.getMessage());
            }

            if (ackRecibido) {
                repository.marcarEnviado(conn, ev.getIdEvento());
                enviados++;
            } else {
                int nuevoIntento = ev.getIntentos() + 1;
                long backoff = calcularBackoffSegundos(nuevoIntento);
                boolean fallidoDefinitivo = nuevoIntento >= MAX_INTENTOS;
                repository.registrarIntentoFallido(conn, ev.getIdEvento(), nuevoIntento, backoff, fallidoDefinitivo);
            }
        }
        return enviados;
    }
}
