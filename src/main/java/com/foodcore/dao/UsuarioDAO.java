package com.foodcore.dao;

import com.foodcore.model.Asistencia;
import com.foodcore.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface UsuarioDAO {
    Optional<Usuario> autenticar(Connection conn, String username, String password) throws SQLException;
    void registrarMarcacionAsistencia(Connection conn, Asistencia asistencia) throws SQLException;
    Optional<Integer> buscarEmpleadoPorDni(Connection conn, String dni) throws SQLException;
}
