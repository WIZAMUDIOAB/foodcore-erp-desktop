package com.foodcore.dao;

import com.foodcore.model.Insumo;
import com.foodcore.model.Receta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface InsumoDAO {
    List<Insumo> listarTodos(Connection conn) throws SQLException;
    List<Receta> obtenerRecetaPorProducto(Connection conn, int idProducto) throws SQLException;
    void descontarStock(Connection conn, int idInsumo, BigDecimal cantidadADescontar) throws SQLException;
    void registrarMovimientoInventario(Connection conn, int idInsumo, String tipo, BigDecimal cantidad, String motivo, Integer referenciaId) throws SQLException;
}
