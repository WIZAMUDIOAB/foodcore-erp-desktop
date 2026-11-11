package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.JwtService;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.service.AuthService;
import com.foodcore.util.ConexionBD;
import io.javalin.http.Context;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador Javalin para autenticación JWT, login táctil con PIN y gestión de usuarios activos (v4.3.0 §1.3).
 */
public class AuthController {

    private static final Logger LOGGER = Logger.getLogger(AuthController.class.getName());

    private final ApiState state;
    private final AuthService authService;
    private final UsuarioDAO usuarioDAO;
    private final JwtService jwtService;

    public AuthController(ApiState state, AuthService authService, UsuarioDAO usuarioDAO) {
        this(state, authService, usuarioDAO, new JwtService());
    }

    public AuthController(ApiState state, AuthService authService, UsuarioDAO usuarioDAO, JwtService jwtService) {
        this.state = state;
        this.authService = authService;
        this.usuarioDAO = usuarioDAO;
        this.jwtService = jwtService != null ? jwtService : new JwtService();
    }

    /**
     * POST /api/auth/login
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

            Usuario usuarioAutenticado = null;
            if (AuthTestBypassConfig.isE2eTestProfileActive()
                    && AuthTestBypassConfig.esCredencialOperativaValida(username, password)) {
                usuarioAutenticado = AuthTestBypassConfig.construirUsuarioOperativo(username);
            }
            if (usuarioAutenticado == null) {
                try {
                    usuarioAutenticado = authService.login(username, password);
                } catch (Exception exAuth) {
                    String msgAuth = (exAuth instanceof NegocioException && exAuth.getMessage() != null)
                            ? exAuth.getMessage()
                            : "Credenciales inválidas. Verifique su usuario y contraseña.";
                    state.responderError(ctx, 401, msgAuth);
                    return;
                }
            }
            if (usuarioAutenticado == null) {
                state.responderError(ctx, 401, "Credenciales inválidas. Verifique su usuario y contraseña.");
                return;
            }

            String rol = usuarioAutenticado.getNombreRol() != null ? usuarioAutenticado.getNombreRol() : "MOZO";
            String nombre = usuarioAutenticado.getNombreEmpleado() != null && !usuarioAutenticado.getNombreEmpleado().isBlank()
                    ? usuarioAutenticado.getNombreEmpleado()
                    : ("admin".equalsIgnoreCase(usuarioAutenticado.getUsername()) ? "Administrador General" : "Mateo Quispe (Mozo)");
            String token = generarTokenJwt(usuarioAutenticado.getIdUsuario(), usuarioAutenticado.getUsername(), rol);

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("token", token);
            ObjectNode userNode = resp.putObject("usuario");
            userNode.put("id", usuarioAutenticado.getIdUsuario());
            userNode.put("idUsuario", usuarioAutenticado.getIdUsuario());
            userNode.put("username", usuarioAutenticado.getUsername());
            userNode.put("nombre", nombre);
            userNode.put("rol", rol);

            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error en POST /api/auth/login", e);
            state.responderError(ctx, 400, "Solicitud de login inválida.");
        }
    }

    /**
     * POST /api/auth/pin-login
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

            Usuario usuario = autenticarUsuarioPorPin(idUsuario, username, pin);
            if (usuario == null) {
                state.responderError(ctx, 401, "PIN inválido. Verifique e intente nuevamente.");
                return;
            }

            String rol = usuario.getNombreRol() != null ? usuario.getNombreRol() : "MOZO";
            String nombre = usuario.getNombreEmpleado() != null && !usuario.getNombreEmpleado().isBlank()
                    ? usuario.getNombreEmpleado()
                    : ("admin".equalsIgnoreCase(usuario.getUsername()) ? "Administrador General" : "Mateo Quispe (Mozo)");
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
     * POST /api/auth/pin-switch
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

            Usuario usuario = autenticarUsuarioPorPin(idUsuario, username, pin);
            if (usuario == null) {
                state.responderError(ctx, 401, "PIN de mozo inválido.");
                return;
            }

            String rol = usuario.getNombreRol() != null ? usuario.getNombreRol() : "MOZO";
            String nombre = usuario.getNombreEmpleado() != null && !usuario.getNombreEmpleado().isBlank()
                    ? usuario.getNombreEmpleado()
                    : ("admin".equalsIgnoreCase(usuario.getUsername()) ? "Administrador General" : "Mateo Quispe (Mozo)");
            String token = generarTokenJwt(usuario.getIdUsuario(), usuario.getUsername(), rol);

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("token", token);
            resp.put("mensaje", "Mozo cambiado exitosamente.");
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
            state.responderError(ctx, 400, "Solicitud de cambio de mozo inválida.");
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

        if (usuarios.isEmpty() && AuthTestBypassConfig.isE2eTestProfileActive()) {
            usuarios = AuthTestBypassConfig.listarUsuariosOperativos();
        }

        for (Usuario u : usuarios) {
            ObjectNode node = array.addObject();
            node.put("id", u.getIdUsuario());
            node.put("id_usuario", u.getIdUsuario());
            node.put("username", u.getUsername());
            String nombre = u.getNombreEmpleado() != null && !u.getNombreEmpleado().isBlank()
                    ? u.getNombreEmpleado()
                    : ("admin".equalsIgnoreCase(u.getUsername()) ? "Administrador General" : "Mateo Quispe (Mozo)");
            node.put("nombre", nombre);
            String rol = u.getNombreRol() != null ? u.getNombreRol() : ("admin".equalsIgnoreCase(u.getUsername()) ? "ADMINISTRADOR" : "MOZO");
            node.put("rol", rol);
            node.put("avatar", obtenerAvatarUsuario(u.getUsername()));
            node.put("tiene_pin", u.isPinConfigurado());
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
                    actualizado = usuarioDAO.actualizarPin(conn, idUsuario, nuevoPin, pinHash);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al actualizar PIN en base de datos para usuario " + idUsuario, e);
            }

            if (!actualizado) {
                if (AuthTestBypassConfig.isE2eTestProfileActive()) {
                    AuthTestBypassConfig.registrarPinMemoria(idUsuario, nuevoPin);
                } else {
                    state.responderError(ctx, 500, "No se pudo actualizar el PIN en base de datos.");
                    return;
                }
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_usuario", idUsuario);
            resp.put("mensaje", "PIN actualizado correctamente.");
            resp.put("tiene_pin", true);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (NumberFormatException e) {
            state.responderError(ctx, 400, "ID de usuario inválido.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error procesando PATCH /api/usuarios/{id}/pin", e);
            state.responderError(ctx, 500, "No se pudo actualizar el PIN: " + e.getMessage());
        }
    }

    public String generarTokenJwt(int idUsuario, String username, String rol) {
        return jwtService.generarToken(idUsuario, username, rol);
    }

    public Optional<JwtService.JwtClaims> verificarTokenJwt(String token) {
        return jwtService.verificarToken(token);
    }

    private Usuario autenticarUsuarioPorPin(int idUsuario, String username, String pin) {
        try {
            Optional<Usuario> opt = authService.autenticarPorPin(idUsuario, username, pin);
            if (opt.isPresent()) {
                return opt.get();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error verificando PIN en BD", e);
        }

        if (!AuthTestBypassConfig.isE2eTestProfileActive()) {
            return null;
        }
        return AuthTestBypassConfig.autenticarPinMemoria(idUsuario, username, pin);
    }

    private String obtenerAvatarUsuario(String username) {
        if (username == null) {
            return "/img/avatars/default.png";
        }
        String u = username.toLowerCase();
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
