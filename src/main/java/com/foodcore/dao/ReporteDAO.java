package com.foodcore.dao;

import com.foodcore.model.ResumenKpi;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public interface ReporteDAO {
    Map<String, BigDecimal> obtenerVentasUltimosDias(Connection conn, int dias) throws SQLException;
    Map<String, Integer> obtenerPlatosBajaRotacion(Connection conn, int limite) throws SQLException;
    Map<String, BigDecimal> obtenerVentasPorMetodoPago(Connection conn, int dias) throws SQLException;
    ResumenKpi obtenerResumenKpi(Connection conn, int dias) throws SQLException;
}
