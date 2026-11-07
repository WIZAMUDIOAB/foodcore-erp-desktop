package com.foodcore.dao.impl;

import com.foodcore.dao.MermaDAO;
import com.foodcore.model.Merma;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MermaDAOImpl implements MermaDAO {

    @Override
    public int registrarMerma(Connection conn, Merma m) throws SQLException {
        String sql = "INSERT INTO merma (id_insumo, id_usuario, cantidad, motivo, costo_unitario, costo_total, observacion, fecha_hora) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, m.getIdInsumo());
            ps.setInt(2, m.getIdUsuario() > 0 ? m.getIdUsuario() : 1);
            ps.setBigDecimal(3, m.getCantidad());
            ps.setString(4, m.getMotivo() != null ? m.getMotivo() : "Quemado");
            ps.setBigDecimal(5, m.getCostoUnitario());
            BigDecimal total = m.getCostoTotal() != null && m.getCostoTotal().compareTo(BigDecimal.ZERO) > 0
                    ? m.getCostoTotal() : m.getCantidad().multiply(m.getCostoUnitario());
            ps.setBigDecimal(6, total);
            ps.setString(7, m.getObservacion());
            ps.setTimestamp(8, m.getFechaHora() != null ? Timestamp.valueOf(m.getFechaHora()) : Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    m.setIdMerma(id);
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public List<Merma> listarTodas(Connection conn) throws SQLException {
        String sql = "SELECT m.*, i.nombre AS insumo_nombre, i.unidad_medida, u.username "
                + "FROM merma m "
                + "JOIN insumo i ON m.id_insumo = i.id_insumo "
                + "JOIN usuario u ON m.id_usuario = u.id_usuario "
                + "ORDER BY m.fecha_hora DESC LIMIT 100";
        List<Merma> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearMerma(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Merma> listarPorRango(Connection conn, LocalDateTime inicio, LocalDateTime fin) throws SQLException {
        String sql = "SELECT m.*, i.nombre AS insumo_nombre, i.unidad_medida, u.username "
                + "FROM merma m "
                + "JOIN insumo i ON m.id_insumo = i.id_insumo "
                + "JOIN usuario u ON m.id_usuario = u.id_usuario "
                + "WHERE m.fecha_hora BETWEEN ? AND ? "
                + "ORDER BY m.fecha_hora DESC";
        List<Merma> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(inicio));
            ps.setTimestamp(2, Timestamp.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearMerma(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Map<String, BigDecimal> obtenerMermasPorMotivo(Connection conn, int anio, int mes) throws SQLException {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        String sql = "SELECT motivo, COALESCE(SUM(costo_total), 0) AS total_perdida "
                + "FROM merma "
                + "WHERE YEAR(fecha_hora) = ? " + (mes > 0 ? "AND MONTH(fecha_hora) = ? " : "")
                + "GROUP BY motivo ORDER BY total_perdida DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, anio);
            if (mes > 0) ps.setInt(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString("motivo"), rs.getBigDecimal("total_perdida"));
                }
            }
        }
        return mapa;
    }

    @Override
    public Map<String, BigDecimal> obtenerMermasPorMes(Connection conn, int anio) throws SQLException {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        String[] meses = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"};
        for (String m : meses) mapa.put(m, BigDecimal.ZERO);

        String sql = "SELECT MONTH(fecha_hora) AS mes, COALESCE(SUM(costo_total), 0) AS total "
                + "FROM merma WHERE YEAR(fecha_hora) = ? GROUP BY MONTH(fecha_hora) ORDER BY mes ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, anio);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int m = rs.getInt("mes");
                    if (m >= 1 && m <= 12) {
                        mapa.put(meses[m - 1], rs.getBigDecimal("total"));
                    }
                }
            }
        }
        return mapa;
    }

    @Override
    public List<Merma> obtenerTopInsumosPerdida(Connection conn, int anio, int mes, int limite) throws SQLException {
        String sql = "SELECT m.id_insumo, i.nombre AS insumo_nombre, i.unidad_medida, "
                + "SUM(m.cantidad) AS total_cant, "
                + "AVG(m.costo_unitario) AS avg_costo, "
                + "SUM(m.costo_total) AS suma_perdida "
                + "FROM merma m "
                + "JOIN insumo i ON m.id_insumo = i.id_insumo "
                + "WHERE YEAR(m.fecha_hora) = ? " + (mes > 0 ? "AND MONTH(m.fecha_hora) = ? " : "")
                + "GROUP BY m.id_insumo, i.nombre, i.unidad_medida "
                + "ORDER BY suma_perdida DESC LIMIT ?";
        List<Merma> res = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setInt(idx++, anio);
            if (mes > 0) ps.setInt(idx++, mes);
            ps.setInt(idx, limite > 0 ? limite : 5);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Merma m = new Merma();
                    m.setIdInsumo(rs.getLong("id_insumo"));
                    m.setNombreInsumo(rs.getString("insumo_nombre"));
                    m.setUnidadMedida(rs.getString("unidad_medida"));
                    m.setCantidad(rs.getBigDecimal("total_cant"));
                    m.setCostoUnitario(rs.getBigDecimal("avg_costo"));
                    m.setCostoTotal(rs.getBigDecimal("suma_perdida"));
                    res.add(m);
                }
            }
        }
        return res;
    }

    @Override
    public int contarMermasPorInsumoEnMes(Connection conn, long idInsumo, int anio, int mes) throws SQLException {
        String sql = "SELECT COUNT(*) FROM merma WHERE id_insumo = ? AND YEAR(fecha_hora) = ? AND MONTH(fecha_hora) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idInsumo);
            ps.setInt(2, anio);
            ps.setInt(3, mes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private Merma mapearMerma(ResultSet rs) throws SQLException {
        Merma m = new Merma();
        m.setIdMerma(rs.getInt("id_merma"));
        m.setIdInsumo(rs.getLong("id_insumo"));
        m.setNombreInsumo(rs.getString("insumo_nombre"));
        m.setUnidadMedida(rs.getString("unidad_medida"));
        m.setIdUsuario(rs.getInt("id_usuario"));
        m.setNombreUsuario(rs.getString("username"));
        m.setCantidad(rs.getBigDecimal("cantidad"));
        m.setMotivo(rs.getString("motivo"));
        m.setCostoUnitario(rs.getBigDecimal("costo_unitario"));
        m.setCostoTotal(rs.getBigDecimal("costo_total"));
        m.setObservacion(rs.getString("observacion"));
        Timestamp ts = rs.getTimestamp("fecha_hora");
        if (ts != null) m.setFechaHora(ts.toLocalDateTime());
        return m;
    }
}
