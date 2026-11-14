package com.foodcore.dao;

import com.foodcore.model.ConfiguracionImpresora;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface ConfiguracionImpresoraDAO {
    List<ConfiguracionImpresora> listarTodas(Connection conn) throws SQLException;
    void guardarOActualizar(Connection conn, int idCategoriaProducto, String nombreImpresoraWindows) throws SQLException;
    boolean eliminarPorCategoria(Connection conn, int idCategoriaProducto) throws SQLException;
    Map<Integer, String> obtenerMapaCategoriaAImpresora(Connection conn) throws SQLException;
    Map<Integer, Integer> obtenerMapaProductoACategoria(Connection conn) throws SQLException;
    Map<Integer, String> obtenerMapaNombreCategorias(Connection conn) throws SQLException;
}
