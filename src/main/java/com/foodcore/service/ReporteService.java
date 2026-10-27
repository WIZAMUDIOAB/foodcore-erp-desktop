package com.foodcore.service;

import com.foodcore.dao.ReporteDAO;
import com.foodcore.dao.ReporteDAOImpl;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class ReporteService {

    private final ReporteDAO reporteDAO = new ReporteDAOImpl();

    public Map<String, BigDecimal> getVentasUltimos7Dias() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reporteDAO.obtenerVentasUltimosDias(conn, 7);
        }
    }

    public Map<String, Integer> getRankingPlatosBajaRotacion(int limite) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reporteDAO.obtenerPlatosBajaRotacion(conn, limite);
        }
    }
}
