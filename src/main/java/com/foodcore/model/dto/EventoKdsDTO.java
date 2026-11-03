package com.foodcore.model.dto;

import com.foodcore.exception.TerminalNoAutorizadaException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Envelope de red para el protocolo autenticado KDS v1 con firma criptográfica HMAC-SHA256
 * y framing big-endian de 4 bytes (SPEC-FOODCORE-v1.0 §11, §15.1).
 */
public class EventoKdsDTO {

    public static final int VERSION_PROTOCOLO = 1;
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024; // 1 MB máximo (§11)
    private static final String ALGORITMO_HMAC = "HmacSHA256";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private int v;
    private String eventId;
    private String ts;
    private String type;
    private String token;
    private String hmac;
    private JsonObject payload;

    public EventoKdsDTO() {
    }

    public EventoKdsDTO(int v, String eventId, String ts, String type, String token, String hmac, JsonObject payload) {
        this.v = v;
        this.eventId = eventId;
        this.ts = ts;
        this.type = type;
        this.token = token;
        this.hmac = hmac;
        this.payload = payload != null ? payload : new JsonObject();
    }

    /**
     * Crea y firma criptográficamente un nuevo envelope de evento KDS con {@code HMAC-SHA256} (§11).
     */
    public static EventoKdsDTO crearFirmado(String type, String sessionToken, String sessionSecret, JsonObject payload) {
        Objects.requireNonNull(type, "El tipo de evento KDS es obligatorio.");
        Objects.requireNonNull(sessionToken, "El token de sesión es obligatorio.");
        Objects.requireNonNull(sessionSecret, "El secreto de sesión para HMAC es obligatorio.");

        String id = UUID.randomUUID().toString();
        String timestamp = OffsetDateTime.now(ZoneOffset.ofHours(-5))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        JsonObject cuerpo = payload != null ? payload : new JsonObject();

        String firma = calcularHmacBase64(sessionSecret, VERSION_PROTOCOLO, id, timestamp, type, cuerpo);
        return new EventoKdsDTO(VERSION_PROTOCOLO, id, timestamp, type, sessionToken, firma, cuerpo);
    }

    /**
     * Calcula {@code Base64(HMAC_SHA256(sessionSecret, v|eventId|ts|type|payloadCanonical))} (§11).
     */
    public static String calcularHmacBase64(String sessionSecret,
                                            int version,
                                            String eventId,
                                            String ts,
                                            String type,
                                            JsonElement payload) {
        if (sessionSecret == null || sessionSecret.isEmpty()) {
            throw new IllegalArgumentException("El secreto HMAC no puede ser nulo ni vacío.");
        }
        try {
            String payloadCanonical = (payload == null || payload.isJsonNull()) ? "{}" : GSON.toJson(payload);
            String mensaje = version + "|" +
                (eventId != null ? eventId : "") + "|" +
                (ts != null ? ts : "") + "|" +
                (type != null ? type : "") + "|" +
                payloadCanonical;

            Mac mac = Mac.getInstance(ALGORITMO_HMAC);
            SecretKeySpec keySpec = new SecretKeySpec(sessionSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO_HMAC);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Error calculando firma HMAC-SHA256 del envelope KDS.", ex);
        }
    }

    /**
     * Verifica la autenticidad e integridad del envelope en tiempo constante (previene ataques de timing).
     */
    public boolean verificarHmac(String sessionSecret) {
        if (sessionSecret == null || sessionSecret.isEmpty() || this.hmac == null || this.hmac.isEmpty()) {
            return false;
        }
        String esperado = calcularHmacBase64(sessionSecret, this.v, this.eventId, this.ts, this.type, this.payload);
        return MessageDigest.isEqual(
            esperado.getBytes(StandardCharsets.UTF_8),
            this.hmac.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Exige que la firma HMAC sea válida; de lo contrario lanza {@link TerminalNoAutorizadaException}.
     */
    public void exigirHmacValido(String sessionSecret) throws TerminalNoAutorizadaException {
        if (!verificarHmac(sessionSecret)) {
            throw new TerminalNoAutorizadaException(
                "Firma HMAC-SHA256 inválida o payload adulterado en evento KDS [" + eventId + "]."
            );
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static EventoKdsDTO fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("El JSON del evento KDS no puede ser nulo ni vacío.");
        }
        return GSON.fromJson(json, EventoKdsDTO.class);
    }

    /**
     * Empaqueta el mensaje según el framing TCP de §11: {@code [4 bytes length big-endian][payload UTF-8]}.
     */
    public byte[] empaquetarFrameTcp() {
        byte[] utf8 = toJson().getBytes(StandardCharsets.UTF_8);
        if (utf8.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("El evento KDS excede el límite de 1 MB (" + utf8.length + " bytes).");
        }
        ByteBuffer buffer = ByteBuffer.allocate(4 + utf8.length);
        buffer.putInt(utf8.length);
        buffer.put(utf8);
        return buffer.array();
    }

    /**
     * Desempaqueta un frame TCP validando longitud big-endian de 4 bytes y límite de 1 MB (§11).
     */
    public static EventoKdsDTO desempaquetarFrameTcp(byte[] frame) {
        if (frame == null || frame.length < 5) {
            throw new IllegalArgumentException("Frame TCP KDS incompleto o nulo.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        int length = buffer.getInt();
        if (length <= 0 || length > MAX_PAYLOAD_BYTES || length != frame.length - 4) {
            throw new IllegalArgumentException("Longitud de frame KDS inválida: " + length);
        }
        byte[] body = new byte[length];
        buffer.get(body);
        return fromJson(new String(body, StandardCharsets.UTF_8));
    }

    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getHmac() {
        return hmac;
    }

    public void setHmac(String hmac) {
        this.hmac = hmac;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }
}
