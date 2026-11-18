package com.foodcore.dao;

import com.foodcore.model.Cliente;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ClienteDAO {
    List<Cliente> listarTodos(Connection conn) throws SQLException;
    List<Cliente> listarActivos(Connection conn) throws SQLException;
    List<Cliente> listarPorTipo(Connection conn, String tipoCliente) throws SQLException;
    Optional<Cliente> buscarPorId(Connection conn, int idCliente) throws SQLException;
    Optional<Cliente> buscarPorDocumento(Connection conn, String numeroDocumento) throws SQLException;
    Optional<Cliente> buscarPorDniOTelefono(Connection conn, String criterio) throws SQLException;
    int insertar(Connection conn, Cliente cliente) throws SQLException;
    boolean actualizar(Connection conn, Cliente cliente) throws SQLException;
    boolean cambiarEstado(Connection conn, int idCliente, boolean activo) throws SQLException;
}
