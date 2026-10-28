package com.foodcore.dao;

import com.foodcore.model.Receta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface RecetaDAO {
    List<Receta> listarPorProducto(Connection conn, int idProducto) throws SQLException;
    void asociarInsumo(Connection conn, int idProducto, int idInsumo, BigDecimal cantidad) throws SQLException;
    void eliminarComponente(Connection conn, int idReceta) throws SQLException;
}
