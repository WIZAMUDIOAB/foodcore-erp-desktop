package com.foodcore.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.events.OutboxRepository;
import com.foodcore.model.OutboxEvento;
import com.foodcore.model.dto.EventoKdsDTO;
import com.foodcore.model.enums.TipoEventoKds;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.JsonMapperProvider;
import com.foodcore.util.ServidorSockets;
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
 * y rechazo de tramas > 1 MB según SPEC-FOODCORE-v1.0 (§10.4, §11, §15.2; REMEDIATION R-02.7, R-14, N2).
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
    @DisplayName("R-02.7 & R-14 (1/6): Handshake válido HELLO con KDS-01 -> WELCOME {sessionToken, sessionSecret, ttl} y RENEW extiende sesión")
    void debeCompletarHandshakeValidoYRenovarSesionConTerminalActiva() throws Exception {
        ObjectMapper mapper = JsonMapperProvider.getMapper();

        ObjectNode hello = mapper.createObjectNode();
        hello.put("type", "HELLO");
        hello.put("codigoTerminal", "KDS-01");
        hello.put("secreto", "Kds01#Secret2026!");

        ObjectNode welcome = servidor.procesarTramaProtocolo(hello.toString(), null);
        assertNotNull(welcome);
        assertEquals("WELCOME", welcome.get("type").asText());
        String sessionToken = welcome.get("sessionToken").asText();
        String sessionSecret = welcome.get("sessionSecret").asText();
        assertTrue(welcome.get("ttl").asLong() == TerminalAuthService.TTL_SEGUNDOS);

        // Probar RENEW
        ObjectNode renew = mapper.createObjectNode();
        renew.put("type", "RENEW");
        renew.put("sessionToken", sessionToken);

        ObjectNode renewedWelcome = servidor.procesarTramaProtocolo(renew.toString(), null);
        assertEquals("WELCOME", renewedWelcome.get("type").asText());
        assertEquals(sessionToken, renewedWelcome.get("sessionToken").asText());
        assertNotEquals(sessionSecret, renewedWelcome.get("sessionSecret").asText(),
            "RENEW debe rotar el sessionSecret efímero en memoria");
    }

    @Test
    @DisplayName("R-02.7 & R-14 (2/6): Handshake con terminal inactiva (KDS-INACTIVA, activo=0) -> ERROR TerminalNoAutorizada")
    void debeRechazarHandshakeDeTerminalInactivaConErrorTerminalNoAutorizada() throws Exception {
        ObjectMapper mapper = JsonMapperProvider.getMapper();

        ObjectNode hello = mapper.createObjectNode();
        hello.put("type", "HELLO");
        hello.put("codigoTerminal", "KDS-INACTIVA");
        hello.put("secreto", "Kds01#Secret2026!");

        ObjectNode respuesta = servidor.procesarTramaProtocolo(hello.toString(), null);
        assertNotNull(respuesta);
        assertEquals("ERROR", respuesta.get("type").asText());
        assertEquals("TerminalNoAutorizada", respuesta.get("code").asText());
    }

    @Test
    @DisplayName("R-02.7 (3/6): Trama EVENT con firma HMAC-SHA256 adulterada -> rechazada con ERROR HMAC_INVALIDO")
    void debeRechazarTramaConHmacAdulterado() throws Exception {
            ObjectMapper mapper = JsonMapperProvider.getMapper();

            ObjectNode hello = mapper.createObjectNode();
            hello.put("type", "HELLO");
            hello.put("codigoTerminal", "KDS-01");
            hello.put("secreto", "Kds01#Secret2026!");
            ObjectNode welcome = servidor.procesarTramaProtocolo(hello.toString(), null);

            String sessionToken = welcome.get("sessionToken").asText();
            String sessionSecret = welcome.get("sessionSecret").asText();

            ObjectNode data = mapper.createObjectNode();
            data.put("idPedido", 100);
            data.put("mesa", "M01");

        EventoKdsDTO eventoLegitimo = EventoKdsDTO.crearFirmado(
            TipoEventoKds.COMANDA_NUEVA.name(),
            sessionToken,
            sessionSecret,
            data
        );

        // Adulterar el número de mesa después de firmado manteniendo la firma original
        eventoLegitimo.getPayload().put("mesa", "M99_ADULTERADA");

                ObjectNode frameEvento = mapper.createObjectNode();
                        frameEvento.put("type", "EVENT");
                        frameEvento.put("sessionToken", sessionToken);
                        frameEvento.set("payload", mapper.readTree(eventoLegitimo.toJson()));

                        ObjectNode respuesta = servidor.procesarTramaProtocolo(frameEvento.toString(), null);
                        assertEquals("ERROR", respuesta.get("type").asText());
                        assertEquals("HMAC_INVALIDO", respuesta.get("code").asText());
    }

    @Test
    @DisplayName("R-02.7 (4/6): Recepción de trama ACK autenticada actualiza outbox_evento; ACK sin sesión se rechaza")
    void recepcionDeAckDebeCambiarEstadoOutboxDePendienteAEnviado() throws Exception {
        long idEvento;
        try (Connection conn = ConexionBD.getConnection()) {
            idEvento = outboxRepository.insertar(conn, "NUEVO_PEDIDO", "{\"idPedido\":77}");
        }

        ObjectMapper mapper = JsonMapperProvider.getMapper();

                // 1. ACK sin sessionToken debe ser rechazado con TerminalNoAutorizada
                        ObjectNode ackSinAuth = mapper.createObjectNode();
                        ackSinAuth.put("type", "ACK");
                        ackSinAuth.put("idEvento", idEvento);
                        ObjectNode respNoAuth = servidor.procesarTramaProtocolo(ackSinAuth.toString(), null);
                        assertEquals("ERROR", respNoAuth.get("type").asText());
                        assertEquals("TerminalNoAutorizada", respNoAuth.get("code").asText());

                        // 2. Autenticar terminal KDS-01 con HELLO y enviar ACK con sessionToken válido
                        ObjectNode hello = mapper.createObjectNode();
                        hello.put("type", "HELLO");
                        hello.put("codigoTerminal", "KDS-01");
                        hello.put("secreto", "Kds01#Secret2026!");
                        ObjectNode welcome = servidor.procesarTramaProtocolo(hello.toString(), null);
                        String sessionToken = welcome.get("sessionToken").asText();

                        ObjectNode ackFrame = mapper.createObjectNode();
                        ackFrame.put("type", "ACK");
                        ackFrame.put("sessionToken", sessionToken);
                        ackFrame.put("idEvento", idEvento);

                        ObjectNode resp = servidor.procesarTramaProtocolo(ackFrame.toString(), null);
                        assertEquals("ACK_CONFIRMED", resp.get("type").asText());

        try (Connection conn = ConexionBD.getConnection()) {
            OutboxEvento actualizado = outboxRepository.obtenerPorId(conn, idEvento).orElseThrow();
            assertEquals("ENVIADO", actualizado.getEstado());
        }
    }

    @Test
    @DisplayName("N2 (5/6): RENEW con token inexistente o expirado es rechazado con TerminalNoAutorizada")
    void debeRechazarRenewConTokenInexistenteOExpirado() throws Exception {
            ObjectMapper mapper = JsonMapperProvider.getMapper();

            ObjectNode renew = mapper.createObjectNode();
            renew.put("type", "RENEW");
            renew.put("sessionToken", "TOKEN_INVENTADO_O_EXPIRADO");

            ObjectNode resp = servidor.procesarTramaProtocolo(renew.toString(), null);
            assertNotNull(resp);
            assertEquals("ERROR", resp.get("type").asText());
            assertEquals("TerminalNoAutorizada", resp.get("code").asText());
        }

    @Test
    @DisplayName("R-02.7 (6/6): Framing -> mensaje mayor a 1 MB (1048576 bytes) es rechazado inmediatamente")
    void debeRechazarMensajeMayorA1Megabyte() {
        String payloadGigante = "A".repeat(TerminalAuthService.MAX_FRAME_BYTES + 512);
                JsonNode resp = servidor.procesarTramaProtocolo(payloadGigante, null);

                        assertNotNull(resp);
                        assertEquals("ERROR", resp.get("type").asText());
                        assertEquals("FRAME_TOO_LARGE", resp.get("code").asText());
    }
}
