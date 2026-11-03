package com.foodcore.security;

import com.foodcore.events.OutboxRepository;
import com.foodcore.model.OutboxEvento;
import com.foodcore.model.dto.EventoKdsDTO;
import com.foodcore.model.enums.TipoEventoKds;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración del protocolo de sockets KDS LAN: Handshake {@code HELLO/WELCOME/RENEW},
 * rechazo de terminal inactiva, validación HMAC-SHA256, confirmación ACK sobre {@code outbox_evento}
 * y rechazo de tramas > 1 MB según SPEC-FOODCORE-v1.0 (§10.4, §11, §15.2; REMEDIATION R-02.7, R-14).
 */
class SocketsIntegracionTest {

    private ServidorSockets servidor;
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        servidor = ServidorSockets.getInstance();
        servidor.cerrarSesionesActivas();
        outboxRepository = new OutboxRepository();
    }

    @Test
    @DisplayName("R-02.7 & R-14 (1/5): Handshake válido HELLO con KDS-01 -> WELCOME {sessionToken, sessionSecret, ttl} y RENEW extiende sesión")
    void debeCompletarHandshakeValidoYRenovarSesionConTerminalActiva() {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "HELLO");
        hello.addProperty("codigoTerminal", "KDS-01");
        hello.addProperty("secreto", "Kds01#Secret2026!");

        JsonObject welcome = servidor.procesarTramaProtocolo(hello.toString(), null);
        assertNotNull(welcome);
        assertEquals("WELCOME", welcome.get("type").getAsString());
        String sessionToken = welcome.get("sessionToken").getAsString();
        String sessionSecret = welcome.get("sessionSecret").getAsString();
        assertTrue(welcome.get("ttl").getAsLong() == TerminalAuthService.TTL_SEGUNDOS);

        // Probar RENEW
        JsonObject renew = new JsonObject();
        renew.addProperty("type", "RENEW");
        renew.addProperty("sessionToken", sessionToken);

        JsonObject renewedWelcome = servidor.procesarTramaProtocolo(renew.toString(), null);
        assertEquals("WELCOME", renewedWelcome.get("type").getAsString());
        assertEquals(sessionToken, renewedWelcome.get("sessionToken").getAsString());
        assertNotEquals(sessionSecret, renewedWelcome.get("sessionSecret").getAsString(),
            "RENEW debe rotar el sessionSecret efímero en memoria");
    }

    @Test
    @DisplayName("R-02.7 & R-14 (2/5): Handshake con terminal inactiva (KDS-INACTIVA, activo=0) -> ERROR TerminalNoAutorizada")
    void debeRechazarHandshakeDeTerminalInactivaConErrorTerminalNoAutorizada() {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "HELLO");
        hello.addProperty("codigoTerminal", "KDS-INACTIVA");
        hello.addProperty("secreto", "Kds01#Secret2026!");

        JsonObject respuesta = servidor.procesarTramaProtocolo(hello.toString(), null);
        assertNotNull(respuesta);
        assertEquals("ERROR", respuesta.get("type").getAsString());
        assertEquals("TerminalNoAutorizada", respuesta.get("code").getAsString());
    }

    @Test
    @DisplayName("R-02.7 (3/5): Trama EVENT con firma HMAC-SHA256 adulterada -> rechazada con ERROR HMAC_INVALIDO")
    void debeRechazarTramaConHmacAdulterado() {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "HELLO");
        hello.addProperty("codigoTerminal", "KDS-01");
        hello.addProperty("secreto", "Kds01#Secret2026!");
        JsonObject welcome = servidor.procesarTramaProtocolo(hello.toString(), null);

        String sessionToken = welcome.get("sessionToken").getAsString();
        String sessionSecret = welcome.get("sessionSecret").getAsString();

        JsonObject data = new JsonObject();
        data.addProperty("idPedido", 100);
        data.addProperty("mesa", "M01");

        EventoKdsDTO eventoLegitimo = EventoKdsDTO.crearFirmado(
            TipoEventoKds.COMANDA_NUEVA.name(),
            sessionToken,
            sessionSecret,
            data
        );

        // Adulterar el número de mesa después de firmado manteniendo la firma original
        eventoLegitimo.getPayload().addProperty("mesa", "M99_ADULTERADA");

        JsonObject frameEvento = new JsonObject();
        frameEvento.addProperty("type", "EVENT");
        frameEvento.addProperty("sessionToken", sessionToken);
        frameEvento.add("payload", JsonParser.parseString(eventoLegitimo.toJson()));

        JsonObject respuesta = servidor.procesarTramaProtocolo(frameEvento.toString(), null);
        assertEquals("ERROR", respuesta.get("type").getAsString());
        assertEquals("HMAC_INVALIDO", respuesta.get("code").getAsString());
    }

    @Test
    @DisplayName("R-02.7 (4/5): Recepción de trama ACK actualiza outbox_evento de PENDIENTE a ENVIADO")
    void recepcionDeAckDebeCambiarEstadoOutboxDePendienteAEnviado() throws Exception {
        long idEvento;
        try (Connection conn = ConexionBD.getConnection()) {
            idEvento = outboxRepository.insertar(conn, "NUEVO_PEDIDO", "{\"idPedido\":77}");
        }

        JsonObject ackFrame = new JsonObject();
        ackFrame.addProperty("type", "ACK");
        ackFrame.addProperty("idEvento", idEvento);

        JsonObject resp = servidor.procesarTramaProtocolo(ackFrame.toString(), null);
        assertEquals("ACK_CONFIRMED", resp.get("type").getAsString());

        try (Connection conn = ConexionBD.getConnection()) {
            OutboxEvento actualizado = outboxRepository.obtenerPorId(conn, idEvento).orElseThrow();
            assertEquals("ENVIADO", actualizado.getEstado());
        }
    }

    @Test
    @DisplayName("R-02.7 (5/5): Framing -> mensaje mayor a 1 MB (1048576 bytes) es rechazado inmediatamente")
    void debeRechazarMensajeMayorA1Megabyte() {
        String payloadGigante = "A".repeat(TerminalAuthService.MAX_FRAME_BYTES + 512);
        JsonObject resp = servidor.procesarTramaProtocolo(payloadGigante, null);

        assertNotNull(resp);
        assertEquals("ERROR", resp.get("type").getAsString());
        assertEquals("FRAME_TOO_LARGE", resp.get("code").getAsString());
    }
}
