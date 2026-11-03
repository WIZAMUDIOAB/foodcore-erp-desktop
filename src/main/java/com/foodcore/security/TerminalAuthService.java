package com.foodcore.security;

import com.foodcore.exception.TerminalNoAutorizadaException;
import com.foodcore.util.ConexionBD;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de autenticación mutua de terminales LAN (KDS/POS) mediante handshake
 * {@code HELLO / WELCOME / RENEW} contra {@code terminal.secreto_hash} (BCrypt costo 12)
 * según SPEC-FOODCORE-v1.0 (§10.4, §11; REMEDIATION R-14, R-02.7).
 */
public class TerminalAuthService {

    public static final long TTL_SEGUNDOS = 8L * 3600L; // 8 horas
    public static final int MAX_FRAME_BYTES = 1048576; // 1 MB
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EncriptadorPassword encriptador;
    private final Map<String, SesionTerminal> sesionesActivas = new ConcurrentHashMap<>();

    public TerminalAuthService() {
        this(new EncriptadorPassword());
    }

    public TerminalAuthService(EncriptadorPassword encriptador) {
        this.encriptador = encriptador;
    }

    /**
     * Representa una sesión efímera en memoria de una terminal autenticada.
     * {@code sessionSecret} nunca se persiste en base de datos (§10.4, R-14).
     */
    public static final class SesionTerminal {
        private final String codigoTerminal;
        private final String sessionToken;
        private final String sessionSecret;
        private final long ttlSegundos;
        private final LocalDateTime expiraEn;

        public SesionTerminal(String codigoTerminal, String sessionToken, String sessionSecret, long ttlSegundos, LocalDateTime expiraEn) {
            this.codigoTerminal = codigoTerminal;
            this.sessionToken = sessionToken;
            this.sessionSecret = sessionSecret;
            this.ttlSegundos = ttlSegundos;
            this.expiraEn = expiraEn;
        }

        public String getCodigoTerminal() {
            return codigoTerminal;
        }

        public String getSessionToken() {
            return sessionToken;
        }

        public String getSessionSecret() {
            return sessionSecret;
        }

        public long getTtlSegundos() {
            return ttlSegundos;
        }

        public LocalDateTime getExpiraEn() {
            return expiraEn;
        }

        public boolean haExpirado(LocalDateTime ahora) {
            return !ahora.isBefore(expiraEn);
        }
    }

    public SesionTerminal autenticar(String codigoTerminal, String secretoEnClaro)
            throws SQLException, TerminalNoAutorizadaException {
        try (Connection conn = ConexionBD.getConnection()) {
            return autenticar(conn, codigoTerminal, secretoEnClaro);
        }
    }

    public SesionTerminal autenticar(Connection conn, String codigoTerminal, String secretoEnClaro)
            throws SQLException, TerminalNoAutorizadaException {
        if (codigoTerminal == null || codigoTerminal.isBlank() || secretoEnClaro == null || secretoEnClaro.isBlank()) {
            throw new TerminalNoAutorizadaException("Credenciales de terminal vacías.");
        }

        String sql = "SELECT secreto_hash, activo FROM terminal WHERE codigo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigoTerminal.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new TerminalNoAutorizadaException(codigoTerminal);
                }
                boolean activo = rs.getBoolean("activo");
                if (!activo) {
                    throw new TerminalNoAutorizadaException(codigoTerminal);
                }
                String secretoHash = rs.getString("secreto_hash");
                if (!encriptador.verificar(secretoEnClaro, secretoHash)) {
                    throw new TerminalNoAutorizadaException(codigoTerminal);
                }
            }
        }

        try (PreparedStatement psUpd = conn.prepareStatement("UPDATE terminal SET ultimo_heartbeat = NOW() WHERE codigo = ?")) {
            psUpd.setString(1, codigoTerminal.trim());
            psUpd.executeUpdate();
        }

        String sessionToken = UUID.randomUUID().toString();
        String sessionSecret = generarSessionSecret32Bytes();
        LocalDateTime expiraEn = LocalDateTime.now(ZONA_LIMA).plusSeconds(TTL_SEGUNDOS);

        SesionTerminal sesion = new SesionTerminal(codigoTerminal.trim(), sessionToken, sessionSecret, TTL_SEGUNDOS, expiraEn);
        sesionesActivas.put(sessionToken, sesion);
        return sesion;
    }

    public SesionTerminal renovar(String sessionToken) throws TerminalNoAutorizadaException {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new TerminalNoAutorizadaException("Token de sesión inválido.");
        }
        SesionTerminal actual = sesionesActivas.get(sessionToken);
        if (actual == null || actual.haExpirado(LocalDateTime.now(ZONA_LIMA))) {
            sesionesActivas.remove(sessionToken);
            throw new TerminalNoAutorizadaException("Sesión de terminal inexistente o expirada.");
        }
        String nuevoSecret = generarSessionSecret32Bytes();
        LocalDateTime nuevaExpiracion = LocalDateTime.now(ZONA_LIMA).plusSeconds(TTL_SEGUNDOS);
        SesionTerminal renovada = new SesionTerminal(
            actual.getCodigoTerminal(),
            sessionToken,
            nuevoSecret,
            TTL_SEGUNDOS,
            nuevaExpiracion
        );
        sesionesActivas.put(sessionToken, renovada);
        return renovada;
    }

    public SesionTerminal obtenerSesion(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return sesionesActivas.get(sessionToken);
    }

    public void limpiarSesiones() {
        sesionesActivas.clear();
    }

    private static String generarSessionSecret32Bytes() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
