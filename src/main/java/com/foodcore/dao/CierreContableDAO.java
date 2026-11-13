package com.foodcore.dao;

import com.foodcore.model.CierreContable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface CierreContableDAO {
    boolean estaPeriodoCerrado(Connection conn, int anio, int mes) throws SQLException;
    Optional<CierreContable> obtenerPorPeriodo(Connection conn, int anio, int mes) throws SQLException;
    List<CierreContable> listarTodos(Connection conn) throws SQLException;
    CierreContable registrarCierre(Connection conn, CierreContable cierre) throws SQLException;
}
