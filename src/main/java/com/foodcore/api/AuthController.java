package com.foodcore.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.JwtService;
import com.foodcore.api.security.TokenBlacklistService;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.TotpService;
import com.foodcore.service.AuthService;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.util.ConexionBD;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador Javalin para autenticación JWT stateless, 2FA TOTP, revocación de sesiones
 * y gestión de usuarios activos (v4.3.0 §1.3 / v5.1.0 Sub-fase A / v5.7.1 Hardening).
 */
public class AuthController {

    private static final Logger LOGGER = Logger.getLogger(AuthController.class.getName());

    private final ApiState state;
    private final AuthService authService;
    private final UsuarioDAO usuarioDAO;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final TotpService totpService;
    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();

    public AuthController(ApiState state, AuthService authService, UsuarioDAO usuarioDAO) {
        this(state, authService, usuarioDAO, new JwtService(), TokenBlacklistService.getInstance(), new TotpService());
    }

    public AuthController(ApiState state, AuthService authService, UsuarioDAO usuarioDAO, JwtService jwtService) {
        this(state, authService, usuarioDAO, jwtService, TokenBlacklistService.getInstance(), new TotpService());
    }

    public AuthController(ApiState state,
            AuthService authService,
            UsuarioDAO usuarioDAO,
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService,
            TotpService totpService) {
        this.state = state;
        this.authService = authService;
        this.usuarioDAO = usuarioDAO;
        this.jwtService = jwtService != null ? jwtService : new JwtService();
        this.tokenBlacklistService = tokenBlacklistService != null
                ? tokenBlacklistService
                : TokenBlacklistService.getInstance();
        this.totpService = totpService != null ? totpService : new TotpService();
    }

    /**
     * POST /api/auth/login (100% stateless: nunca muta la sesión Desktop en SesionManager).
     */
    public void handleLogin(Context ctx) {
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            String username = body.hasNonNull("username") ? body.get("username").asText().trim() : "";
            String password = body.hasNonNull("password") ? body.get("password").asText().trim() : "";

            if (username.isEmpty() || password.isEmpty()) {
                state.responderError(ctx, 400, "Debe proporcionar usuario y contraseña.");
                return;
            }

            Usuario usuarioAutenticado;
            try {
                usuarioAutenticado = authService.autenticarSoloCredenciales(username, password);
            } catch (NegocioException exAuth) {
                String msgAuth = exAuth.getMessage() != null
                        ? exAuth.getMessage()
                        : "Credenciales inválidas. Verifique su usuario y contraseña.";
                state.responderError(ctx, 401, msgAuth);
                return;
            } catch (Exception exAuth) {
                LOGGER.log(Level.WARNING, "Fallo técnico en autenticación POST /api/auth/login", exAuth);
                state.responderError(ctx, 401, "Credenciales inválidas. Verifique su usuario y contraseña.");
                return;
            }
            if (usuarioAutenticado == null) {
                state.responderError(ctx, 401, "Credenciales inválidas. Verifique su usuario y contraseña.");
                return;
            }

            if (usuarioAutenticado.isTotpHabilitado()) {
                String codigoTotp = extraerCodigoTotp(body);
                if (codigoTotp.isEmpty()) {
                    auditoriaDAO.registrarEventoBestEffort(
                            usuarioAutenticado.getIdUsuario(), "2FA_FALLIDO", "USUARIO", null,
                            "Login API sin código 2FA para " + usuarioAutenticado.getUsername());
                    responderError2Fa(ctx, "Código 2FA TOTP de 6 dígitos requerido.");
                    return;
                }
                String claveUsuario = String.valueOf(usuarioAutenticado.getIdUsuario());
                if (!totpService.verificarCodigo(claveUsuario, usuarioAutenticado.getTotpSecret(), codigoTotp)) {
                    auditoriaDAO.registrarEventoBestEffort(
                            usuarioAutenticado.getIdUsuario(), "2FA_FALLIDO", "USUARIO", null,
                            "Código 2FA inválido o reutilizado vía API para " + usuarioAutenticado.getUsername());
                    responderError2Fa(ctx, "Código 2FA inválido.");
                    return;
                }
            }

            String rol = usuarioAutenticado.getNombreRol() != null ? usuarioAutenticado.getNombreRol() : "MOZO";
            String nombre = resolverNombreMostrado(usuarioAutenticado);
            String token = generarTokenJwt(usuarioAutenticado.getIdUsuario(), usuarioAutenticado.getUsername(), rol);

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("token", token);
            ObjectNode userNode = resp.putObject("usuario");
            userNode.put("id", usuarioAutenticado.getIdUsuario());
            userNode.put("idUsuario", usuarioAutenticado.getIdUsuario());
            userNode.put("username", usuarioAutenticado.getUsername());
            userNode.put("nombre", nombre);
            userNode.put("rol", rol);
            userNode.put("totpHabilitado", usuarioAutenticado.isTotpHabilitado());

            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error en POST /api/auth/login", e);
            state.responderError(ctx, 400, "Solicitud de login inválida.");
        }
    }

    /**
     * POST /api/auth/pin-login (stateless; bloquea usuarios con 2FA o roles ADMIN/SUPERVISOR).
     */
    public void handlePinLogin(Context ctx) {
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            String pin = body.hasNonNull("pin") ? body.get("pin").asText().trim() : "";
            int idUsuario = body.hasNonNull("id_usuario") ? body.get("id_usuario").asInt(0)
                    : (body.hasNonNull("idUsuario") ? body.get("idUsuario").asInt(0) : 0);
            String username = body.hasNonNull("username") ? body.get("username").asText().trim() : "";

            if (pin.isEmpty()) {
                state.responderError(ctx, 400, "Debe proporcionar el PIN de acceso.");
                return;
            }

            if (esObjetivoRestringidoParaPin(idUsuario, username)) {
                state.responderError(ctx, 403, "Acceso por PIN no permitido para usuarios con 2FA o rol administrativo.");
                return;
            }

            Usuario usuario = autenticarUsuarioPorPinStateless(idUsuario, username, pin);
            if (usuario == null) {
                state.responderError(ctx, 401, "PIN inválido. Verifique e intente nuevamente.");
                return;
            }
            if (!AuthServiceImpl.esPinPermitidoParaUsuario(usuario)) {
                state.responderError(ctx, 403, "Acceso por PIN no permitido para usuarios con 2FA o rol administrativo.");
                return;
            }

            String rol = usuario.getNombreRol() != null ? usuario.getNombreRol() : "MOZO";
            String nombre = resolverNombreMostrado(usuario);
            String token = generarTokenJwt(usuario.getIdUsuario(), usuario.getUsername(), rol);

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("token", token);
            ObjectNode userNode = resp.putObject("usuario");
            userNode.put("id", usuario.getIdUsuario());
            userNode.put("idUsuario", usuario.getIdUsuario());
            userNode.put("username", usuario.getUsername());
            userNode.put("nombre", nombre);
            userNode.put("rol", rol);
            userNode.put("avatar", obtenerAvatarUsuario(usuario.getUsername()));

            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error en POST /api/auth/pin-login", e);
            state.responderError(ctx, 400, "Solicitud de PIN login inválida.");
        }
    }

    /**
     * POST /api/auth/pin-switch (stateless; bloquea escalada a ADMIN/SUPERVISOR/2FA y revoca el JWT anterior).
     */
    public void handlePinSwitch(Context ctx) {
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            String pin = body.hasNonNull("pin") ? body.get("pin").asText().trim() : "";
            int idUsuario = body.hasNonNull("id_usuario") ? body.get("id_usuario").asInt(0)
                    : (body.hasNonNull("idUsuario") ? body.get("idUsuario").asInt(0) : 0);
            String username = body.hasNonNull("username") ? body.get("username").asText().trim() : "";

            if (pin.isEmpty()) {
                state.responderError(ctx, 400, "Debe proporcionar el PIN del nuevo mozo.");
                return;
            }

            if (esObjetivoRestringidoParaPin(idUsuario, username)) {
                state.responderError(ctx, 403, "Cambio rápido por PIN no permitido para usuarios con 2FA o rol administrativo.");
                return;
            }

            Usuario usuario = autenticarUsuarioPorPinStateless(idUsuario, username, pin);
            if (usuario == null) {
                state.responderError(ctx, 401, "PIN de operador inválido.");
                return;
            }
            if (!AuthServiceImpl.esPinPermitidoParaUsuario(usuario)) {
                state.responderError(ctx, 403, "Cambio rápido por PIN no permitido para usuarios con 2FA o rol administrativo.");
                return;
            }

            // Revocar el JWT anterior inmediatamente para impedir sesiones huérfanas tras el cambio de operador
            JwtService.JwtClaims caller = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
            if (caller != null && caller.jti() != null && !caller.jti().isBlank()) {
                try (Connection conn = abrirConexionOpcional()) {
                    tokenBlacklistService.revocarToken(
                            conn,
                            caller.jti(),
                            caller.idUsuario(),
                            caller.idUsuario(),
                            "Cambio de operador vía pin-switch",
                            Instant.ofEpochSecond(caller.expiresAtEpochSec()));
                }
            }

            String rol = usuario.getNombreRol() != null ? usuario.getNombreRol() : "MOZO";
            String nombre = resolverNombreMostrado(usuario);
            String token = generarTokenJwt(usuario.getIdUsuario(), usuario.getUsername(), rol);

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("token", token);
            resp.put("mensaje", "Operador cambiado exitosamente.");
            ObjectNode userNode = resp.putObject("usuario");
            userNode.put("id", usuario.getIdUsuario());
            userNode.put("idUsuario", usuario.getIdUsuario());
            userNode.put("username", usuario.getUsername());
            userNode.put("nombre", nombre);
            userNode.put("rol", rol);
            userNode.put("avatar", obtenerAvatarUsuario(usuario.getUsername()));

            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error en POST /api/auth/pin-switch", e);
            state.responderError(ctx, 400, "Solicitud de cambio de operador inválida.");
        }
    }

    /**
     * GET /api/usuarios/activos
     */
    public void handleListarUsuariosActivos(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ArrayNode array = state.mapper.createArrayNode();

        List<Usuario> usuarios = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                usuarios = usuarioDAO.listarTodosActivos(conn);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error consultando usuarios activos de BD", e);
        }

        for (Usuario u : usuarios) {
            ObjectNode node = array.addObject();
            node.put("id", u.getIdUsuario());
            node.put("id_usuario", u.getIdUsuario());
            node.put("username", u.getUsername());
            node.put("nombre", resolverNombreMostrado(u));
            String rol = u.getNombreRol() != null ? u.getNombreRol() : "MOZO";
            node.put("rol", rol);
            node.put("avatar", obtenerAvatarUsuario(u.getUsername()));
            node.put("tiene_pin", u.isPinConfigurado());
            node.put("pin_permitido", AuthServiceImpl.esPinPermitidoParaUsuario(u));
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    /**
     * PATCH /api/usuarios/{id}/pin
     */
    public void handleActualizarPinUsuario(Context ctx) {
        try {
            int idUsuario = Integer.parseInt(ctx.pathParam("id"));
            JsonNode body = state.mapper.readTree(ctx.body());
            String nuevoPin = body.hasNonNull("pin") ? body.get("pin").asText().trim() : "";

            if (!nuevoPin.matches("^\\d{4,6}$")) {
                state.responderError(ctx, 400, "El PIN debe tener entre 4 y 6 dígitos numéricos.");
                return;
            }

            String pinHash = EncriptadorPassword.generarHash(nuevoPin);
            boolean actualizado = false;

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    actualizado = usuarioDAO.actualizarPin(conn, idUsuario, null, pinHash);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al actualizar PIN en base de datos para usuario " + idUsuario, e);
            }

            if (!actualizado) {
                state.responderError(ctx, 503, "No se pudo actualizar el PIN en base de datos.");
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_usuario", idUsuario);
            resp.put("mensaje", "PIN actualizado correctamente.");
            resp.put("tiene_pin", true);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (NumberFormatException e) {
            state.responderError(ctx, 400, "ID de usuario inválido.");
        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Cuerpo JSON inválido.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error procesando PATCH /api/usuarios/{id}/pin", e);
            state.responderError(ctx, 500, "No se pudo actualizar el PIN.");
        }
    }

    /**
     * POST /api/auth/force-logout
     * Invalida sesiones JWT remotas (global o por usuario específico). Solo rol ADMIN.
     */
    public void handleForceLogout(Context ctx) {
        JwtService.JwtClaims caller = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (caller == null || !"ADMIN".equalsIgnoreCase(caller.rol())) {
            state.responderError(ctx, 403, "Solo un administrador puede ejecutar cierre de sesión remoto.");
            return;
        }
        try {
            String rawBody = ctx.body().trim();
            JsonNode body = rawBody.isEmpty() ? state.mapper.createObjectNode() : state.mapper.readTree(rawBody);

            int targetId = body.hasNonNull("id_usuario")
                    ? body.get("id_usuario").asInt(0)
                    : (body.hasNonNull("idUsuario") ? body.get("idUsuario").asInt(0) : 0);
            String targetUsername = body.hasNonNull("username") ? body.get("username").asText("").trim() : "";
            String motivo = body.hasNonNull("motivo")
                    ? body.get("motivo").asText("").trim()
                    : "Cierre de sesión remoto ejecutado por " + caller.username();

            int invalidados = 0;
            long corteFinal = System.currentTimeMillis();

            try (Connection conn = abrirConexionOpcional()) {
                if (targetId > 0) {
                    corteFinal = tokenBlacklistService.forzarCierreSesionUsuario(conn, targetId, caller.idUsuario(), motivo);
                    invalidados = 1;
                } else if (!targetUsername.isBlank() && conn != null) {
                    Optional<Usuario> objetivo = usuarioDAO.buscarPorUsername(conn, targetUsername);
                    if (objetivo.isPresent()) {
                        corteFinal = tokenBlacklistService.forzarCierreSesionUsuario(
                                conn, objetivo.get().getIdUsuario(), caller.idUsuario(), motivo);
                        invalidados = 1;
                    }
                } else {
                    List<Integer> idsObjetivo = new ArrayList<>();
                    if (conn != null) {
                        for (Usuario u : usuarioDAO.listarTodosActivos(conn)) {
                            if (u.getIdUsuario() > 0 && !idsObjetivo.contains(u.getIdUsuario())) {
                                idsObjetivo.add(u.getIdUsuario());
                            }
                        }
                    }
                    if (!idsObjetivo.contains(caller.idUsuario())) {
                        idsObjetivo.add(caller.idUsuario());
                    }
                    for (int uid : idsObjetivo) {
                        corteFinal = tokenBlacklistService.forzarCierreSesionUsuario(conn, uid, caller.idUsuario(), motivo);
                        invalidados++;
                    }
                    if (caller.jti() != null && !caller.jti().isBlank()) {
                        tokenBlacklistService.revocarToken(
                                conn,
                                caller.jti(),
                                caller.idUsuario(),
                                caller.idUsuario(),
                                motivo,
                                Instant.ofEpochSecond(caller.expiresAtEpochSec()));
                    }
                }
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("usuarios_invalidados", invalidados);
            resp.put("corte_epoch_ms", corteFinal);
            resp.put("mensaje", "Cierre de sesión remoto ejecutado correctamente.");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (JsonProcessingException | SQLException e) {
            LOGGER.log(Level.WARNING, "Error procesando POST /api/auth/force-logout", e);
            state.responderError(ctx, 400, "Solicitud de force-logout inválida.");
        }
    }

    public String generarTokenJwt(int idUsuario, String username, String rol) {
        return jwtService.generarToken(idUsuario, username, rol);
    }

    private Connection abrirConexionOpcional() {
        try {
            return ConexionBD.getConnection();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Sin conexión BD durante operación de revocación; se aplica en memoria", e);
            return null;
        }
    }

    private boolean esObjetivoRestringidoParaPin(int idUsuario, String username) {
        if (idUsuario <= 0 && (username == null || username.isBlank())) {
            return false;
        }
        try (Connection conn = abrirConexionOpcional()) {
            if (conn == null) {
                return false;
            }
            Optional<Usuario> objetivo = idUsuario > 0
                    ? usuarioDAO.buscarPorId(conn, idUsuario)
                    : usuarioDAO.buscarPorUsername(conn, username.trim());
            if (objetivo.isPresent()) {
                Usuario u = objetivo.get();
                if (u.isTotpHabilitado()) {
                    return true;
                }
                String rolNorm = JwtService.normalizarRol(u.getNombreRol());
                return "ADMIN".equals(rolNorm) || "SUPERVISOR".equals(rolNorm);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo verificar elegibilidad PIN en BD", e);
        }
        return false;
    }

    private static String resolverNombreMostrado(Usuario usuario) {
        if (usuario.getNombreEmpleado() != null && !usuario.getNombreEmpleado().isBlank()) {
            return usuario.getNombreEmpleado();
        }
        return usuario.getUsername() != null ? usuario.getUsername() : "Usuario";
    }

    private static String extraerCodigoTotp(JsonNode body) {
        if (body == null) {
            return "";
        }
        for (String campo : List.of("totpCode", "codigoTotp", "totp_code", "otp")) {
            if (body.hasNonNull(campo)) {
                return body.get(campo).asText("").trim();
            }
        }
        return "";
    }

    private void responderError2Fa(Context ctx, String mensaje) {
        ObjectNode err = state.mapper.createObjectNode();
        err.put("error", mensaje);
        err.put("message", mensaje);
        err.put("requires2fa", true);
        ctx.status(401).contentType("application/json; charset=UTF-8").result(err.toString());
    }

    public Optional<JwtService.JwtClaims> verificarTokenJwt(String token) {
        return jwtService.verificarToken(token);
    }

    private Usuario autenticarUsuarioPorPinStateless(int idUsuario, String username, String pin) {
        try {
            Optional<Usuario> opt = authService.autenticarPorPinStateless(idUsuario, username, pin);
            if (opt.isPresent()) {
                return opt.get();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error verificando PIN en BD", e);
        }
        return null;
    }

    private String obtenerAvatarUsuario(String username) {
        if (username == null) {
            return "/img/avatars/default.png";
        }
        String u = username.toLowerCase(Locale.ROOT);
        if (u.contains("admin")) {
            return "/img/avatars/admin.png";
        }
        if (u.contains("mozo")) {
            return "/img/avatars/mozo.png";
        }
        if (u.contains("cajer")) {
            return "/img/avatars/cajero.png";
        }
        if (u.contains("cocin")) {
            return "/img/avatars/cocina.png";
        }
        return "/img/avatars/default.png";
    }
}
