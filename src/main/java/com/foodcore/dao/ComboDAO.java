package com.foodcore.dao;

import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO JDBC para combos y sus componentes incluidos (v4.8.0 Módulo 3).
 */
public interface ComboDAO {

    List<Combo> listarCombosActivos(Connection conn) throws SQLException;

    Optional<Combo> obtenerPorId(Connection conn, int idCombo) throws SQLException;

    Optional<Combo> obtenerPorIdProducto(Connection conn, int idProducto) throws SQLException;

    List<ComboDetalle> listarDetallesPorCombo(Connection conn, int idCombo) throws SQLException;

    Map<Integer, Combo> listarMapaCombosActivosPorProducto(Connection conn) throws SQLException;

    List<ComboDetalle> obtenerComponentesParaVenta(Connection conn, Integer idCombo, int idProducto) throws SQLException;

    int crearCombo(Connection conn, Combo combo) throws SQLException;

    void actualizarCombo(Connection conn, Combo combo) throws SQLException;

    void reemplazarDetalles(Connection conn, int idCombo, List<ComboDetalle> detalles) throws SQLException;

    void desactivarCombo(Connection conn, int idCombo) throws SQLException;
}
