package com.foodcore.model.dto;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.TerminalNoAutorizadaException;
import com.foodcore.model.enums.TipoEventoKds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del protocolo de red KDS v1 y autenticación criptográfica HMAC-SHA256
 * según SPEC-FOODCORE-v1.0 (§11, §15.1).
 */
class PayloadKdsTest {

    private static final String SESSION_TOKEN = "sess_kds_cocina_01";
    private static final String SESSION_SECRET = "SecretoCompartidoTerminalKds2026#";

    @Test
    @DisplayName("Contrato §11 / §15.1: Debe serializar/deserializar el envelope JSON y verificar HMAC-SHA256 válido")
    void debeSerializarDeserializarYValidarHmacCorrecto() {
        JsonObject payload = new JsonObject();
        payload.addProperty("idPedido", 42);
        payload.addProperty("mesa", "MESA 04");
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("producto", "Alitas BBQ x6");
        item.addProperty("cantidad", 2);
        items.add(item);
        payload.add("items", items);

        EventoKdsDTO original = EventoKdsDTO.crearFirmado(
            TipoEventoKds.ITEMS_ADICIONALES.name(),
            SESSION_TOKEN,
            SESSION_SECRET,
            payload
        );

        String json = original.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"v\":1"));
        assertTrue(json.contains("\"ITEMS_ADICIONALES\""));

        EventoKdsDTO deserializado = EventoKdsDTO.fromJson(json);
        assertEquals(1, deserializado.getV());
        assertEquals(original.getEventId(), deserializado.getEventId());
        assertEquals("ITEMS_ADICIONALES", deserializado.getType());
        assertEquals(SESSION_TOKEN, deserializado.getToken());
        assertEquals("MESA 04", deserializado.getPayload().get("mesa").getAsString());

        assertTrue(deserializado.verificarHmac(SESSION_SECRET));
        assertDoesNotThrow(() -> deserializado.exigirHmacValido(SESSION_SECRET));
    }

    @Test
    @DisplayName("Contrato §11 / §15.1: Debe rechazar firma HMAC inválida o payload adulterado en tránsito")
    void debeRechazarHmacInvalidoOPayloadAdulterado() {
        JsonObject payload = new JsonObject();
        payload.addProperty("idPedido", 100);
        payload.addProperty("mesa", "MESA 01");

        EventoKdsDTO evento = EventoKdsDTO.crearFirmado(
            TipoEventoKds.COMANDA_NUEVA.name(),
            SESSION_TOKEN,
            SESSION_SECRET,
            payload
        );

        // 1. Verificar con secreto incorrecto debe fallar
        assertFalse(evento.verificarHmac("SecretoFalsoDeAtacante"));

        // 2. Adulterar el payload después de firmado debe invalidar el HMAC
        evento.getPayload().addProperty("mesa", "MESA 99 ADULTERADA");
        assertFalse(evento.verificarHmac(SESSION_SECRET));

        TerminalNoAutorizadaException ex = assertThrows(TerminalNoAutorizadaException.class,
            () -> evento.exigirHmacValido(SESSION_SECRET));
        assertEquals(CodigoError.TERMINAL_NO_AUTORIZADA, ex.getCodigoError());
    }

    @Test
    @DisplayName("Contrato §11: Debe empaquetar y desempaquetar frame TCP con longitud big-endian de 4 bytes")
    void debeEmpaquetarYDesempaquetarFrameTcpBigEndian() {
        JsonObject payload = new JsonObject();
        payload.addProperty("idDetallePedido", 501);

        EventoKdsDTO original = EventoKdsDTO.crearFirmado(
            TipoEventoKds.ITEM_LISTO.name(),
            SESSION_TOKEN,
            SESSION_SECRET,
            payload
        );

        byte[] frame = original.empaquetarFrameTcp();
        EventoKdsDTO reconstruido = EventoKdsDTO.desempaquetarFrameTcp(frame);

        assertEquals(original.getEventId(), reconstruido.getEventId());
        assertTrue(reconstruido.verificarHmac(SESSION_SECRET));
    }
}
