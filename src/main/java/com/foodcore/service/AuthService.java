package com.foodcore.service;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.SesionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Contrato de Autenticación, Bloqueo por Fuerza Bruta (5 intentos / 15 min)
 * y Seguridad PIN según SPEC-FOODCORE-v1.0 (§10.1, §10.2) y SPEC-FOODCORE-v4.3.0 (§1.2).
 * La gestión thread-safe de la sesión activa reside exclusivamente en {@link SesionManager}.
 */
public interface AuthService {

    int MAX_INTENTOS_FALLIDOS = 5;

    Usuario login(String username, String password) throws NegocioException, SQLException;

    Usuario login(Connection conn, String username, String password) throws NegocioException, SQLException;

    /**
     * Valida credenciales, estado activo y bloqueo SIN mutar {@link SesionManager}.
     * Requerido por API REST stateless y flujo Desktop 2FA previo a verificación TOTP.
     */
    Usuario autenticarSoloCredenciales(String username, String password) throws NegocioException, SQLException;

    Usuario autenticarSoloCredenciales(Connection conn, String username, String password)
            throws NegocioException, SQLException;

    Optional<Usuario> autenticarPorPin(int idUsuario, String username, String pin) throws SQLException;

    Optional<Usuario> autenticarPorPin(Connection conn, int idUsuario, String username, String pin)
            throws SQLException;

    /**
     * Valida PIN por BCrypt SIN mutar {@link SesionManager}.
     * Bloquea usuarios con 2FA habilitado, roles ADMIN/SUPERVISOR, cuentas inactivas o bloqueadas.
     */
    Optional<Usuario> autenticarPorPinStateless(int idUsuario, String username, String pin) throws SQLException;

    Optional<Usuario> autenticarPorPinStateless(Connection conn, int idUsuario, String username, String pin)
            throws SQLException;

    /**
     * Establece la sesión global Desktop en {@link SesionManager}
     * tras completar todos los factores de autenticación (contraseña + 2FA TOTP si aplica).
     */
    void establecerSesionActiva(Usuario usuario);

    boolean actualizarPinUsuario(int idUsuario, String nuevoPin) throws NegocioException, SQLException;

    void cambiarPasswordObligatorio(int idUsuario, String nuevoPassword) throws NegocioException, SQLException;

    String marcarAsistenciaInteligente(String dni) throws NegocioException, SQLException;

    static void setUsuarioSesionActual(Usuario usuario) {
        SesionManager.setUsuarioActual(usuario);
    }

    static Usuario getUsuarioSesionActual() {
        return SesionManager.getUsuarioActual();
    }

    static void logout() {
        SesionManager.cerrarSesion();
    }
}
