package com.foodcore.service;

import com.foodcore.dao.ReporteDAO;
import com.foodcore.dao.ReporteDAOImpl;
import com.foodcore.model.ResumenKpi;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class ReporteService {

    private final ReporteDAO reporteDAO = new ReporteDAOImpl();

    public Map<String, BigDecimal> getVentasUltimos7Dias() throws SQLException {
        return getVentasPeriodo(7);
    }

    public Map<String, BigDecimal> getVentasPeriodo(int dias) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reporteDAO.obtenerVentasUltimosDias(conn, dias);
        }
    }

    public Map<String, Integer> getRankingPlatosBajaRotacion(int limite) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reporteDAO.obtenerPlatosBajaRotacion(conn, limite);
        }
    }

    public Map<String, BigDecimal> getVentasPorMetodoPago(int dias) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reporteDAO.obtenerVentasPorMetodoPago(conn, dias);
        }
    }

    public ResumenKpi getResumenKpi(int dias) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reporteDAO.obtenerResumenKpi(conn, dias);
        }
    }
}
