package com.foodcore.dao.impl;

import com.foodcore.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Implementación canónica de {@code UsuarioDAO} en paquete {@code com.foodcore.dao.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-18).
 */
public class UsuarioDAOImpl extends com.foodcore.dao.UsuarioDAOImpl {

    @Override
    public Optional<Usuario> autenticar(Connection conn, String username, String password) throws SQLException {
        return super.autenticar(conn, username, password);
    }

    @Override
    public Optional<Usuario> buscarPorUsername(Connection conn, String username) throws SQLException {
        return super.buscarPorUsername(conn, username);
    }

    @Override
    public void registrarIntentoFallido(Connection conn, int idUsuario, int nuevosIntentos, boolean bloquear15Minutos) throws SQLException {
        super.registrarIntentoFallido(conn, idUsuario, nuevosIntentos, bloquear15Minutos);
    }

    @Override
    public void resetearIntentosLogin(Connection conn, int idUsuario) throws SQLException {
        super.resetearIntentosLogin(conn, idUsuario);
    }

    @Override
    public void actualizarPasswordObligatorio(Connection conn, int idUsuario, String nuevoPasswordHash) throws SQLException {
        super.actualizarPasswordObligatorio(conn, idUsuario, nuevoPasswordHash);
    }
}
