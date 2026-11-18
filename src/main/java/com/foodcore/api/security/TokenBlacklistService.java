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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio central de revocación JWT e invalidación forzada de sesiones (ÍTEM 8 v5.1.0).
 * Combina caché en memoria de baja latencia con respaldo persistente en MySQL
 * ({@code usuario.tokens_invalidados_antes} y tabla {@code token_blacklist}).
 */
public final class TokenBlacklistService {

    private static final Logger LOGGER = Logger.getLogger(TokenBlacklistService.class.getName());
    private static final TokenBlacklistService INSTANCE = new TokenBlacklistService(true);

    private final ConcurrentHashMap<String, Long> tokensRevocados = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> cortePorUsuario = new ConcurrentHashMap<>();
    private final UsuarioDAO usuarioDAO;
    private final AuditoriaDAO auditoriaDAO;
    private final boolean usarBaseDatosPorDefecto;

    public TokenBlacklistService() {
        this(true);
    }

    public TokenBlacklistService(boolean usarBaseDatosPorDefecto) {
        this(usarBaseDatosPorDefecto, new UsuarioDAOImpl(), new AuditoriaDAOImpl());
    }

    TokenBlacklistService(boolean usarBaseDatosPorDefecto, UsuarioDAO usuarioDAO, AuditoriaDAO auditoriaDAO) {
        this.usarBaseDatosPorDefecto = usarBaseDatosPorDefecto;
        this.usuarioDAO = usuarioDAO;
        this.auditoriaDAO = auditoriaDAO;
    }

    /**
     * Instancia compartida entre el servidor API REST y el backoffice desktop JavaFX.
     *
     * @return Instancia global de lista negra y cortes por usuario.
     */
    public static TokenBlacklistService getInstance() {
        return INSTANCE;
    }

    /**
     * Crea una instancia aislada en memoria para pruebas unitarias sin persistencia MySQL.
     *
     * @return Instancia aislada para tests.
     */
    public static TokenBlacklistService crearAisladoEnMemoria() {
        return new TokenBlacklistService(false);
    }

    /**
     * Invalida inmediatamente todos los JWT emitidos hasta este momento para un usuario
     * específico y registra el evento en {@code auditoria}.
     *
     * @param idUsuario   ID del usuario objetivo cuyo acceso remoto debe revocarse.
     * @param revocadoPor ID del administrador que ejecuta el cierre remoto.
     * @param motivo      Descripción legible del motivo para bitácora.
     * @return Marca temporal epoch ms aplicada como umbral de invalidación.
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
     *
     * @param conn        Conexión MySQL (puede ser {@code null} para modo solo memoria).
     * @param idUsuario   ID del usuario objetivo.
     * @param revocadoPor ID del administrador que ordena la revocación.
     * @param motivo      Motivo registrado en auditoría.
     * @return Marca temporal epoch ms aplicada como umbral de invalidación.
     */
    public long forzarCierreSesionUsuario(Connection conn, int idUsuario, int revocadoPor, String motivo) {
        long corteMs = System.currentTimeMillis();
        // Garantizar separación de al menos 2 ms frente a emisiones posteriores al force-logout
        try {
            Thread.sleep(2L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        cortePorUsuario.merge(idUsuario, corteMs, Math::max);

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
     *
     * @param conn        Conexión MySQL (puede ser {@code null}).
     * @param jti         Identificador único del token.
     * @param idUsuario   ID del propietario del token.
     * @param revocadoPor ID del administrador solicitante.
     * @param motivo      Motivo de revocación.
     * @param expiraEn    Fecha de expiración original del token.
     */
    public void revocarToken(Connection conn, String jti, int idUsuario, int revocadoPor,
            String motivo, Instant expiraEn) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long expSec = expiraEn != null ? expiraEn.getEpochSecond() : Long.MAX_VALUE;
        tokensRevocados.put(jti, expSec);

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
     *
     * @param claims Claims extraídos por {@link JwtService#verificarToken(String)}.
     * @return {@code true} si el token debe rechazarse con HTTP 401.
     */
    public boolean estaRevocado(JwtService.JwtClaims claims) {
        if (claims == null) {
            return true;
        }
        if (estaRevocadoEnMemoria(claims)) {
            return true;
        }
        if (!usarBaseDatosPorDefecto) {
            return false;
        }
        try (Connection conn = abrirConexionSiDisponible()) {
            return estaRevocadoEnBd(conn, claims);
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "No se pudo consultar token_blacklist en BD; se usa caché en memoria", ex);
            return false;
        }
    }

    /**
     * Variante explícita con conexión provista (útil en pruebas con MySQL directo).
     *
     * @param conn   Conexión MySQL (puede ser {@code null}).
     * @param claims Claims del JWT validado.
     * @return {@code true} si el token está revocado en memoria o en base de datos.
     */
    public boolean estaRevocado(Connection conn, JwtService.JwtClaims claims) {
        if (claims == null) {
            return true;
        }
        if (estaRevocadoEnMemoria(claims)) {
            return true;
        }
        return estaRevocadoEnBd(conn, claims);
    }

    /**
     * Limpia el estado en memoria (uso exclusivo de soporte en pruebas).
     */
    public void limpiarMemoria() {
        tokensRevocados.clear();
        cortePorUsuario.clear();
    }

    private boolean estaRevocadoEnMemoria(JwtService.JwtClaims claims) {
        if (claims.jti() != null && !claims.jti().isBlank() && tokensRevocados.containsKey(claims.jti())) {
            return true;
        }
        Long corteMem = cortePorUsuario.get(claims.idUsuario());
        return corteMem != null && claims.issuedAtEpochMs() <= corteMem;
    }

    private boolean estaRevocadoEnBd(Connection conn, JwtService.JwtClaims claims) {
        if (conn == null) {
            return false;
        }
        try {
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
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Columna o tabla de revocación aún no disponible en BD", ex);
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
