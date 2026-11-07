package com.foodcore.dao;

import com.foodcore.model.Compra;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface CompraDAO {
    List<Compra> listarTodas(Connection conn) throws SQLException;
    List<Compra> listarPorRangoFecha(Connection conn, LocalDate inicio, LocalDate fin) throws SQLException;
    int insertar(Connection conn, Compra compra) throws SQLException;
}
