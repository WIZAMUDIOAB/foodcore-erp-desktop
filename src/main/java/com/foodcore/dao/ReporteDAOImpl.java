package com.foodcore.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReporteDAOImpl implements ReporteDAO {

    @Override
    public Map<String, BigDecimal> obtenerVentasUltimosDias(Connection conn, int dias) throws SQLException {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        String sql = "SELECT DATE(fecha_hora) AS dia, COALESCE(SUM(total), 0) AS monto " +
                     "FROM venta " +
                     "WHERE estado = 'COMPLETADA' " +
                     "GROUP BY DATE(fecha_hora) " +
                     "ORDER BY dia DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dias);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString("dia"), rs.getBigDecimal("monto"));
                }
            }
        }
        return mapa;
    }

    @Override
    public Map<String, Integer> obtenerPlatosBajaRotacion(Connection conn, int limite) throws SQLException {
        Map<String, Integer> mapa = new LinkedHashMap<>();
        String sql = "SELECT p.nombre, COALESCE(SUM(dv.cantidad), 0) AS total_vendido " +
                     "FROM producto p " +
                     "LEFT JOIN detalle_venta dv ON p.id_producto = dv.id_producto " +
                     "GROUP BY p.id_producto, p.nombre " +
                     "ORDER BY total_vendido ASC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString("nombre"), rs.getInt("total_vendido"));
                }
            }
        }
        return mapa;
    }
}
