package com.foodcore.service;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.SesionManager;
import com.foodcore.service.impl.AuthServiceImpl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Contrato base y gestor de sesión activa de Autenticación, Bloqueo por Fuerza Bruta (5 intentos / 15 min)
 * y Seguridad PIN según SPEC-FOODCORE-v1.0 (§10.1, §10.2) y SPEC-FOODCORE-v4.3.0 (§1.2).
 * La lógica de negocio reside íntegramente en {@link AuthServiceImpl}.
 */
public class AuthService {

    public static final int MAX_INTENTOS_FALLIDOS = 5;

    protected final UsuarioDAO usuarioDAO;
    protected final EncriptadorPassword encriptador;
    private static Usuario usuarioSesionActual;

    public AuthService() {
        this(new UsuarioDAOImpl(), new EncriptadorPassword());
    }

    public AuthService(UsuarioDAO usuarioDAO, EncriptadorPassword encriptador) {
        this.usuarioDAO = usuarioDAO;
        this.encriptador = encriptador;
    }

    public Usuario login(String username, String password) throws NegocioException, SQLException {
        return new AuthServiceImpl(usuarioDAO, encriptador).login(username, password);
    }

    public Usuario login(Connection conn, String username, String password) throws NegocioException, SQLException {
        return new AuthServiceImpl(usuarioDAO, encriptador).login(conn, username, password);
    }

    public Optional<Usuario> autenticarPorPin(int idUsuario, String username, String pin) throws SQLException {
        return new AuthServiceImpl(usuarioDAO, encriptador).autenticarPorPin(idUsuario, username, pin);
    }

    public boolean actualizarPinUsuario(int idUsuario, String nuevoPin) throws NegocioException, SQLException {
        return new AuthServiceImpl(usuarioDAO, encriptador).actualizarPinUsuario(idUsuario, nuevoPin);
    }

    public void cambiarPasswordObligatorio(int idUsuario, String nuevoPassword) throws NegocioException, SQLException {
        new AuthServiceImpl(usuarioDAO, encriptador).cambiarPasswordObligatorio(idUsuario, nuevoPassword);
    }

    public String marcarAsistenciaInteligente(String dni) throws NegocioException, SQLException {
        return new AuthServiceImpl(usuarioDAO, encriptador).marcarAsistenciaInteligente(dni);
    }

    public static void setUsuarioSesionActual(Usuario usuario) {
        usuarioSesionActual = usuario;
    }

    public static Usuario getUsuarioSesionActual() {
        return usuarioSesionActual;
    }

    public static void logout() {
        usuarioSesionActual = null;
        SesionManager.cerrarSesion();
    }
}
