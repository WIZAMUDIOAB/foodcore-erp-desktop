package com.foodcore.events;

import com.foodcore.model.OutboxEvento;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Suite de integración del patrón Outbox transaccional, reintento exponencial y confirmación ACK
 * según SPEC-FOODCORE-v1.0 (§11, §15.2, §19.10; REMEDIATION R-02.6, R-13).
 */
class OutboxDispatcherTest {

    private OutboxRepository repository;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        repository = new OutboxRepository();
        dispatcher = new OutboxDispatcher(new JdbcTransactionManager(), repository);
    }

    @Test
    @DisplayName("R-02.6 & R-13: Cálculo de backoff exponencial 1s, 2s, 4s, 8s, 16s, 32s, 60s (acotado a 60s)")
    void debeCalcularBackoffExponencialAcotadoA60Segundos() {
        assertEquals(1L, OutboxDispatcher.calcularBackoffSegundos(1));
        assertEquals(2L, OutboxDispatcher.calcularBackoffSegundos(2));
        assertEquals(4L, OutboxDispatcher.calcularBackoffSegundos(3));
        assertEquals(8L, OutboxDispatcher.calcularBackoffSegundos(4));
        assertEquals(16L, OutboxDispatcher.calcularBackoffSegundos(5));
        assertEquals(32L, OutboxDispatcher.calcularBackoffSegundos(6));
        assertEquals(60L, OutboxDispatcher.calcularBackoffSegundos(7));
        assertEquals(60L, OutboxDispatcher.calcularBackoffSegundos(10));
    }

    @Test
    @DisplayName("R-02.6 & R-13: Evento persiste PENDIENTE -> Sin ACK incrementa intentos con backoff -> En reconexión con ACK pasa a ENVIADO")
    void debePersistirPendienteReintentarSinAckYMarcarEnviadoAlRecibirAckEnReconexion() throws Exception {
        long idEvento;
        try (Connection conn = ConexionBD.getConnection()) {
            idEvento = repository.insertar(conn, "NUEVO_PEDIDO", "{\"idPedido\":101,\"mesa\":\"M01\"}");
            OutboxEvento inicial = repository.obtenerPorId(conn, idEvento).orElseThrow();
            assertEquals("PENDIENTE", inicial.getEstado());
            assertEquals(0, inicial.getIntentos());
            assertNull(inicial.getEnviadoEn());
        }

        // 1. Primer intento sin ACK (simulando corte de red LAN hacia KDS)
        int enviadosIntento1 = dispatcher.despacharPendientes(ev -> false);
        assertEquals(0, enviadosIntento1);

        try (Connection conn = ConexionBD.getConnection()) {
            OutboxEvento trasFallo = repository.obtenerPorId(conn, idEvento).orElseThrow();
            assertEquals("PENDIENTE", trasFallo.getEstado());
            assertEquals(1, trasFallo.getIntentos());
        }

        // 2. Reconexión de terminal KDS: reenvía eventos pendientes no ACK'd y recibe ACK
        int enviadosReconexion = dispatcher.reenviarPendientesEnReconexion(ev -> true);
        assertEquals(1, enviadosReconexion);

        try (Connection conn = ConexionBD.getConnection()) {
            OutboxEvento enviado = repository.obtenerPorId(conn, idEvento).orElseThrow();
            assertEquals("ENVIADO", enviado.getEstado());
            assertNotNull(enviado.getEnviadoEn(), "enviado_en debe poblarse al confirmar ACK");
        }
    }
}
