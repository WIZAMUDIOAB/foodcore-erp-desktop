package com.foodcore.dao;

import com.foodcore.model.ResumenKpi;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        String sql = "SELECT DATE_FORMAT(fecha_hora, '%Y-%m-%d') AS dia, COALESCE(SUM(total), 0) AS monto " +
                     "FROM venta " +
                     "WHERE estado = 'COMPLETADA' " +
                     "  AND (? <= 0 OR fecha_hora >= DATE_SUB(NOW(), INTERVAL ? DAY)) " +
                     "GROUP BY DATE(fecha_hora), DATE_FORMAT(fecha_hora, '%Y-%m-%d') " +
                     "ORDER BY dia ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dias);
            ps.setInt(2, dias);
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
                     "LEFT JOIN venta v ON dv.id_venta = v.id_venta AND v.estado = 'COMPLETADA' " +
                     "WHERE p.activo = 1 " +
                     "GROUP BY p.id_producto, p.nombre " +
                     "ORDER BY total_vendido ASC, p.nombre ASC LIMIT ?";
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

    @Override
    public Map<String, BigDecimal> obtenerVentasPorMetodoPago(Connection conn, int dias) throws SQLException {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        String sql = "SELECT metodo_pago, COALESCE(SUM(total), 0) AS total_monto " +
                     "FROM venta " +
                     "WHERE estado = 'COMPLETADA' " +
                     "  AND (? <= 0 OR fecha_hora >= DATE_SUB(NOW(), INTERVAL ? DAY)) " +
                     "GROUP BY metodo_pago " +
                     "ORDER BY total_monto DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dias);
            ps.setInt(2, dias);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString("metodo_pago"), rs.getBigDecimal("total_monto"));
                }
            }
        }
        return mapa;
    }

    @Override
    public ResumenKpi obtenerResumenKpi(Connection conn, int dias) throws SQLException {
        ResumenKpi kpi = new ResumenKpi();

        // 1. Total ventas, cantidad transacciones y ticket promedio
        String sqlVentas = "SELECT COALESCE(SUM(total), 0) AS total_vendido, " +
                           "       COUNT(*) AS cantidad_ventas, " +
                           "       COALESCE(AVG(total), 0) AS ticket_promedio " +
                           "FROM venta " +
                           "WHERE estado = 'COMPLETADA' " +
                           "  AND (? <= 0 OR fecha_hora >= DATE_SUB(NOW(), INTERVAL ? DAY))";
        try (PreparedStatement ps = conn.prepareStatement(sqlVentas)) {
            ps.setInt(1, dias);
            ps.setInt(2, dias);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal total = rs.getBigDecimal("total_vendido");
                    int cant = rs.getInt("cantidad_ventas");
                    BigDecimal prom = rs.getBigDecimal("ticket_promedio").setScale(2, RoundingMode.HALF_UP);
                    kpi.setTotalVentas(total != null ? total : BigDecimal.ZERO);
                    kpi.setCantidadTransacciones(cant);
                    kpi.setTicketPromedio(prom);
                }
            }
        }

        // 2. Plato estrella
        String sqlTop = "SELECT p.nombre, COALESCE(SUM(dv.cantidad), 0) AS total_qty " +
                        "FROM detalle_venta dv " +
                        "JOIN producto p ON dv.id_producto = p.id_producto " +
                        "JOIN venta v ON dv.id_venta = v.id_venta " +
                        "WHERE v.estado = 'COMPLETADA' " +
                        "  AND (? <= 0 OR v.fecha_hora >= DATE_SUB(NOW(), INTERVAL ? DAY)) " +
                        "GROUP BY p.id_producto, p.nombre " +
                        "ORDER BY total_qty DESC " +
                        "LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlTop)) {
            ps.setInt(1, dias);
            ps.setInt(2, dias);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setPlatoMasVendido(rs.getString("nombre"));
                    kpi.setCantidadPlatoMasVendido(rs.getInt("total_qty"));
                } else {
                    kpi.setPlatoMasVendido("Sin ventas");
                    kpi.setCantidadPlatoMasVendido(0);
                }
            }
        }

        return kpi;
    }
}
