package com.foodcore.dao;

import com.foodcore.model.HistorialPrecio;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface HistorialPrecioDAO {
    List<HistorialPrecio> listarTodos(Connection conn) throws SQLException;
    List<HistorialPrecio> listarPorEntidad(Connection conn, String tipoEntidad, int idEntidad) throws SQLException;
    List<HistorialPrecio> listarPorRangoFecha(Connection conn, LocalDate inicio, LocalDate fin) throws SQLException;
    int insertar(Connection conn, HistorialPrecio historial) throws SQLException;
    double obtenerPrecioPromedioInsumo(Connection conn, int idInsumo) throws SQLException;
}
