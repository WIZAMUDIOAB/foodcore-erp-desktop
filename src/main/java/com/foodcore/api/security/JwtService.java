package com.foodcore.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Servicio criptográfico JWT HS256 para generación y validación real de firma y expiración.
 */
public final class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final long TOKEN_TTL_SECONDS = 28_800L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final byte[] secretBytes;

    public record JwtClaims(int idUsuario, String username, String rol, long expiresAtEpochSec) {
    }

    public JwtService() {
        this(resolverClaveSecreta());
    }

    public JwtService(byte[] secretBytes) {
        if (secretBytes == null || secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "La clave JWT debe tener al menos " + MIN_SECRET_BYTES + " bytes de entropía."
            );
        }
        this.secretBytes = secretBytes.clone();
    }

    private static byte[] resolverClaveSecreta() {
        String configured = leerSecretConfigurado();
        if (configured != null && !configured.isBlank() && !configured.startsWith("${")) {
            byte[] raw = configured.getBytes(StandardCharsets.UTF_8);
            if (raw.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                    "FOODCORE_JWT_SECRET es demasiado corto; mínimo " + MIN_SECRET_BYTES + " bytes."
                );
            }
            return raw;
        }
        // Clave efímera aleatoria por instancia cuando no se especifica FOODCORE_JWT_SECRET en entorno dev/test
        byte[] ephemeral = new byte[MIN_SECRET_BYTES];
        SECURE_RANDOM.nextBytes(ephemeral);
        return ephemeral;
    }

    private static String leerSecretConfigurado() {
        String sysProp = System.getProperty("FOODCORE_JWT_SECRET");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String envVal = System.getenv("FOODCORE_JWT_SECRET");
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        Properties props = new Properties();
        cargarSiExiste(props, "/application.properties");
        cargarSiExiste(props, "/application-test.properties");
        return props.getProperty("jwt.secret");
    }

    private static void cargarSiExiste(Properties target, String path) {
        try (InputStream is = JwtService.class.getResourceAsStream(path)) {
            if (is != null) {
                target.load(is);
            }
        } catch (java.io.IOException ignored) {
        }
    }

    public String generarToken(int idUsuario, String username, String rol) {
        return generarToken(idUsuario, username, rol, TOKEN_TTL_SECONDS);
    }

    public String generarToken(int idUsuario, String username, String rol, long ttlSeconds) {
        try {
            long iat = Instant.now().getEpochSecond();
            long exp = iat + ttlSeconds;

            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", username != null ? username : "");
            payload.put("uid", idUsuario);
            payload.put("rol", normalizarRol(rol));
            payload.put("iat", iat);
            payload.put("exp", exp);

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String headerB64 = encoder.encodeToString(MAPPER.writeValueAsBytes(header));
            String payloadB64 = encoder.encodeToString(MAPPER.writeValueAsBytes(payload));
            String signingInput = headerB64 + "." + payloadB64;

            byte[] signature = firmarHmacSha256(signingInput);
            return signingInput + "." + encoder.encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el token JWT.", e);
        }
    }

    public Optional<JwtClaims> verificarToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String[] parts = token.trim().split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] headerBytes = decoder.decode(parts[0]);
            JsonNode headerNode = MAPPER.readTree(headerBytes);
            if (!"HS256".equals(headerNode.path("alg").asText())) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSig = firmarHmacSha256(signingInput);
            byte[] providedSig = decoder.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, providedSig)) {
                return Optional.empty();
            }

            byte[] payloadBytes = decoder.decode(parts[1]);
            JsonNode payloadNode = MAPPER.readTree(payloadBytes);
            long exp = payloadNode.path("exp").asLong(0L);
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            String sub = payloadNode.path("sub").asText("");
            int uid = payloadNode.path("uid").asInt(0);
            String rol = normalizarRol(payloadNode.path("rol").asText(""));
            if (sub.isBlank() || uid <= 0 || rol.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new JwtClaims(uid, sub, rol, exp));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String normalizarRol(String rol) {
        if (rol == null || rol.isBlank()) {
            return "";
        }
        String upper = rol.trim().toUpperCase(Locale.ROOT);
        if ("ADMINISTRADOR".equals(upper)) {
            return "ADMIN";
        }
        return upper;
    }

    private byte[] firmarHmacSha256(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    }
}
