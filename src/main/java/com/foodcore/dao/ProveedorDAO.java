package com.foodcore.dao;

import com.foodcore.model.Proveedor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ProveedorDAO {
    List<Proveedor> listarTodos(Connection conn) throws SQLException;
    List<Proveedor> listarActivos(Connection conn) throws SQLException;
    Optional<Proveedor> buscarPorId(Connection conn, int idProveedor) throws SQLException;
    Optional<Proveedor> buscarPorRuc(Connection conn, String ruc) throws SQLException;
    int insertar(Connection conn, Proveedor proveedor) throws SQLException;
    boolean actualizar(Connection conn, Proveedor proveedor) throws SQLException;
    boolean cambiarEstado(Connection conn, int idProveedor, boolean activo) throws SQLException;
}
