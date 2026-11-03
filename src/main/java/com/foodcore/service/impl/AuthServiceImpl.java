package com.foodcore.service.impl;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.service.AuthService;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Implementación canónica de {@link AuthService} en paquete {@code com.foodcore.service.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-18).
 */
public class AuthServiceImpl extends AuthService {

    public AuthServiceImpl() {
        super();
    }

    public AuthServiceImpl(UsuarioDAO usuarioDAO, EncriptadorPassword encriptador) {
        super(usuarioDAO, encriptador);
    }

    @Override
    public Usuario login(String username, String password) throws NegocioException, SQLException {
        return super.login(username, password);
    }

    @Override
    public Usuario login(Connection conn, String username, String password) throws NegocioException, SQLException {
        return super.login(conn, username, password);
    }

    @Override
    public void cambiarPasswordObligatorio(int idUsuario, String nuevoPassword) throws NegocioException, SQLException {
        super.cambiarPasswordObligatorio(idUsuario, nuevoPassword);
    }
}
