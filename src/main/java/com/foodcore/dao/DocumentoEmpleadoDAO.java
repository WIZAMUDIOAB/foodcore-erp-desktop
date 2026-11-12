package com.foodcore.dao;

import com.foodcore.model.DocumentoEmpleado;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * DAO para gestión de documentos PDF adjuntos al expediente del empleado.
 */
public interface DocumentoEmpleadoDAO {

    long insertar(Connection conn, DocumentoEmpleado doc) throws SQLException;

    List<DocumentoEmpleado> listarPorUsuario(Connection conn, int idUsuario) throws SQLException;

    Optional<DocumentoEmpleado> buscarPorId(Connection conn, long idDocumento) throws SQLException;

    boolean eliminar(Connection conn, long idDocumento) throws SQLException;

    List<DocumentoEmpleado> listarProximosAVencer(Connection conn, int dias) throws SQLException;
}
