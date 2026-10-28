package com.foodcore.dao;

import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface ProductoDAO {
    List<Categoria> listarCategorias(Connection conn) throws SQLException;
    List<Producto> listarProductosDisponibles(Connection conn) throws SQLException;
    List<Producto> listarTodos(Connection conn) throws SQLException;
    List<Producto> listarPorCategoria(Connection conn, int idCategoria) throws SQLException;
    int crear(Connection conn, Producto p) throws SQLException;
    void actualizar(Connection conn, Producto p) throws SQLException;
    void cambiarDisponibilidad(Connection conn, int idProducto, boolean disponible) throws SQLException;
    void eliminar(Connection conn, int idProducto) throws SQLException;
}
