package com.foodcore.dao;

import com.foodcore.model.Categoria;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface CategoriaDAO {
    List<Categoria> listarTodas(Connection conn) throws SQLException;
    int crear(Connection conn, Categoria c) throws SQLException;
    void actualizar(Connection conn, Categoria c) throws SQLException;
    void eliminar(Connection conn, int idCategoria) throws SQLException;
}
