package com.foodcore.dao;

import com.foodcore.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * DAO para consulta y actualización de expedientes personales y laborales en la tabla {@code usuario}.
 */
public interface ExpedienteUsuarioDAO {

    List<Usuario> listarTodos(Connection conn, boolean soloActivos) throws SQLException;

    Optional<Usuario> buscarPorId(Connection conn, int idUsuario) throws SQLException;

    boolean actualizarDatosPersonales(Connection conn, Usuario usuario) throws SQLException;

    boolean actualizarDatosLaborales(Connection conn, Usuario usuario) throws SQLException;
}
