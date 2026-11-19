package com.foodcore.api.security;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio central de revocación JWT e invalidación forzada de sesiones (ÍTEM 8 v5.1.0).
 * Combina caché en memoria (con negative-cache de 15s por usuario/JTI para evitar 2 SQL por petición
 * y purga de tokens expirados) con respaldo persistente en MySQL y postura fail-closed ante fallo de BD.
 */
public final class TokenBlacklistService {

    private static final Logger LOGGER = Logger.getLogger(TokenBlacklistService.class.getName());
    public static final long NEGATIVE_CACHE_TTL_MS = 15_000L;
    private static final int MAX_NEGATIVE_CACHE_ENTRIES = 10_000;
    private static final int PURGE_EVERY_N_CHECKS = 256;
    private static final TokenBlacklistService INSTANCE = new TokenBlacklistService(true);

    private final ConcurrentHashMap<String, Long> tokensRevocados = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> cortePorUsuario = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> negativeCacheUsuarioMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> negativeCacheJtiMs = new ConcurrentHashMap<>();
    private final AtomicInteger checkCounter = new AtomicInteger(0);
    private final UsuarioDAO usuarioDAO;
    private final AuditoriaDAO auditoriaDAO;
    private final boolean usarBaseDatosPorDefecto;
    private final LongSupplier clockMillis;

    public TokenBlacklistService() {
        this(true);
    }

    public TokenBlacklistService(boolean usarBaseDatosPorDefecto) {
        this(usarBaseDatosPorDefecto, new UsuarioDAOImpl(), new AuditoriaDAOImpl(), System::currentTimeMillis);
    }

    TokenBlacklistService(boolean usarBaseDatosPorDefecto, UsuarioDAO usuarioDAO, AuditoriaDAO auditoriaDAO) {
        this(usarBaseDatosPorDefecto, usuarioDAO, auditoriaDAO, System::currentTimeMillis);
    }

    public TokenBlacklistService(boolean usarBaseDatosPorDefecto,
                                 UsuarioDAO usuarioDAO,
                                 AuditoriaDAO auditoriaDAO,
                                 LongSupplier clockMillis) {
        this.usarBaseDatosPorDefecto = usarBaseDatosPorDefecto;
        this.usuarioDAO = usuarioDAO;
        this.auditoriaDAO = auditoriaDAO;
        this.clockMillis = clockMillis != null ? clockMillis : System::currentTimeMillis;
    }

    /**
     * Instancia compartida entre el servidor API REST y el backoffice desktop JavaFX.
     */
    public static TokenBlacklistService getInstance() {
        return INSTANCE;
    }

    /**
     * Crea una instancia aislada en memoria para pruebas unitarias sin persistencia MySQL.
     */
    public static TokenBlacklistService crearAisladoEnMemoria() {
        return new TokenBlacklistService(false);
    }

    /**
     * Invalida inmediatamente todos los JWT emitidos hasta este momento para un usuario
     * específico y registra el evento en {@code auditoria}.
     */
    public long forzarCierreSesionUsuario(int idUsuario, int revocadoPor, String motivo) {
        try (Connection conn = abrirConexionSiDisponible()) {
            return forzarCierreSesionUsuario(conn, idUsuario, revocadoPor, motivo);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Fallo cerrando conexión tras force-logout", ex);
            return forzarCierreSesionUsuario(null, idUsuario, revocadoPor, motivo);
        }
    }

    /**
     * Variante transaccional o reutilizando una conexión existente de {@link #forzarCierreSesionUsuario}.
     */
    public long forzarCierreSesionUsuario(Connection conn, int idUsuario, int revocadoPor, String motivo) {
        long corteMs = clockMillis.getAsLong();
        try {
            Thread.sleep(2L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        cortePorUsuario.merge(idUsuario, corteMs, Math::max);
        negativeCacheUsuarioMs.remove(idUsuario);

        if (conn != null) {
            try {
                usuarioDAO.registrarInvalidacionTokensAntes(conn, idUsuario, corteMs);
                String detalle = "Cierre de sesión remoto forzado para usuario ID " + idUsuario
                        + (motivo != null && !motivo.isBlank() ? " (" + motivo + ")" : "");
                auditoriaDAO.registrarEvento(
                        conn,
                        revocadoPor > 0 ? revocadoPor : 1,
                        "FORCE_LOGOUT",
                        "USUARIO",
                        null,
                        detalle);
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "No se pudo persistir force-logout en BD para usuario " + idUsuario, ex);
            }
        }
        return corteMs;
    }

    /**
     * Agrega un {@code jti} concreto a la lista negra en memoria y en {@code token_blacklist}.
     */
    public void revocarToken(Connection conn, String jti, int idUsuario, int revocadoPor,
            String motivo, Instant expiraEn) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long expSec = expiraEn != null ? expiraEn.getEpochSecond() : Long.MAX_VALUE;
        tokensRevocados.put(jti, expSec);
        negativeCacheJtiMs.remove(jti);
        negativeCacheUsuarioMs.remove(idUsuario);

        if (conn == null) {
            return;
        }
        String sql = "INSERT IGNORE INTO token_blacklist (token_jti, id_usuario, revocado_por, motivo, expira_en) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jti);
            ps.setInt(2, idUsuario > 0 ? idUsuario : 1);
            if (revocadoPor > 0) {
                ps.setInt(3, revocadoPor);
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, motivo);
            ps.setTimestamp(5, expiraEn != null ? Timestamp.from(expiraEn) : null);
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo persistir token revocado en token_blacklist", ex);
        }
    }

    /**
     * Evalúa si un JWT válido en firma y expiración ha sido revocado o invalidado globalmente.
     * En modo BD aplica caché negativa de 15s por usuario/JTI y postura fail-closed ante errores de BD.
     */
    public boolean estaRevocado(JwtService.JwtClaims claims) {
        if (claims == null) {
            return true;
        }
        if ((checkCounter.incrementAndGet() & (PURGE_EVERY_N_CHECKS - 1)) == 0) {
            purgarExpirados();
        }
        if (estaRevocadoEnMemoria(claims)) {
            return true;
        }
        if (!usarBaseDatosPorDefecto) {
            return false;
        }
        long ahoraMs = clockMillis.getAsLong();
        if (esCacheNegativaVigente(claims, ahoraMs)) {
            return false;
        }
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                LOGGER.warning("Conexión BD nula en TokenBlacklistService (fail-closed).");
                return true;
            }
            boolean revocado = estaRevocadoEnBd(conn, claims);
            if (!revocado) {
                registrarEnCacheNegativa(claims, ahoraMs);
            }
            return revocado;
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Fallo consultando revocación JWT en BD; aplicando rechazo fail-closed.", ex);
            return true;
        }
    }

    /**
     * Variante explícita con conexión provista (útil en pruebas con MySQL directo).
     */
    public boolean estaRevocado(Connection conn, JwtService.JwtClaims claims) {
        if (claims == null) {
            return true;
        }
        if (estaRevocadoEnMemoria(claims)) {
            return true;
        }
        if (conn == null) {
            return usarBaseDatosPorDefecto;
        }
        long ahoraMs = clockMillis.getAsLong();
        if (esCacheNegativaVigente(claims, ahoraMs)) {
            return false;
        }
        try {
            boolean revocado = estaRevocadoEnBd(conn, claims);
            if (!revocado) {
                registrarEnCacheNegativa(claims, ahoraMs);
            }
            return revocado;
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error SQL consultando token_blacklist; aplicando rechazo fail-closed.", ex);
            return true;
        }
    }

    /**
     * Depura de memoria los tokens revocados cuya fecha {@code exp} ya transcurrió
     * y las entradas vencidas de caché negativa.
     */
    public void purgarExpirados() {
        long ahoraMs = clockMillis.getAsLong();
        long ahoraSec = ahoraMs / 1000L;
        tokensRevocados.entrySet().removeIf(entry -> entry.getValue() < ahoraSec);
        negativeCacheUsuarioMs.entrySet().removeIf(entry -> (ahoraMs - entry.getValue()) >= NEGATIVE_CACHE_TTL_MS);
        negativeCacheJtiMs.entrySet().removeIf(entry -> (ahoraMs - entry.getValue()) >= NEGATIVE_CACHE_TTL_MS);
    }

    public int getCantidadTokensRevocadosEnMemoria() {
        return tokensRevocados.size();
    }

    /**
     * Limpia el estado en memoria (uso exclusivo de soporte en pruebas).
     */
    public void limpiarMemoria() {
        tokensRevocados.clear();
        cortePorUsuario.clear();
        negativeCacheUsuarioMs.clear();
        negativeCacheJtiMs.clear();
    }

    private boolean esCacheNegativaVigente(JwtService.JwtClaims claims, long ahoraMs) {
        Long tsUsuario = negativeCacheUsuarioMs.get(claims.idUsuario());
        if (tsUsuario == null || (ahoraMs - tsUsuario) >= NEGATIVE_CACHE_TTL_MS) {
            return false;
        }
        if (claims.jti() == null || claims.jti().isBlank()) {
            return true;
        }
        Long tsJti = negativeCacheJtiMs.get(claims.jti());
        return tsJti != null && (ahoraMs - tsJti) < NEGATIVE_CACHE_TTL_MS;
    }

    private void registrarEnCacheNegativa(JwtService.JwtClaims claims, long ahoraMs) {
        if (negativeCacheUsuarioMs.size() >= MAX_NEGATIVE_CACHE_ENTRIES
                || negativeCacheJtiMs.size() >= MAX_NEGATIVE_CACHE_ENTRIES) {
            purgarExpirados();
        }
        if (negativeCacheUsuarioMs.size() < MAX_NEGATIVE_CACHE_ENTRIES) {
            negativeCacheUsuarioMs.put(claims.idUsuario(), ahoraMs);
        }
        if (claims.jti() != null && !claims.jti().isBlank() && negativeCacheJtiMs.size() < MAX_NEGATIVE_CACHE_ENTRIES) {
            negativeCacheJtiMs.put(claims.jti(), ahoraMs);
        }
    }

    private boolean estaRevocadoEnMemoria(JwtService.JwtClaims claims) {
        if (claims.jti() != null && !claims.jti().isBlank() && tokensRevocados.containsKey(claims.jti())) {
            return true;
        }
        Long corteMem = cortePorUsuario.get(claims.idUsuario());
        return corteMem != null && claims.issuedAtEpochMs() <= corteMem;
    }

    private boolean estaRevocadoEnBd(Connection conn, JwtService.JwtClaims claims) throws SQLException {
        Optional<Long> corteBd = usuarioDAO.obtenerTokensInvalidadosAntes(conn, claims.idUsuario());
        if (corteBd.isPresent() && corteBd.get() > 0L) {
            cortePorUsuario.merge(claims.idUsuario(), corteBd.get(), Math::max);
            if (claims.issuedAtEpochMs() <= corteBd.get()) {
                return true;
            }
        }
        if (claims.jti() != null && !claims.jti().isBlank()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM token_blacklist WHERE token_jti = ? LIMIT 1")) {
                ps.setString(1, claims.jti());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tokensRevocados.put(claims.jti(), claims.expiresAtEpochSec());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Connection abrirConexionSiDisponible() {
        if (!usarBaseDatosPorDefecto) {
            return null;
        }
        try {
            return ConexionBD.getConnection();
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "Conexión BD no disponible para TokenBlacklistService", ex);
            return null;
        }
    }
}
