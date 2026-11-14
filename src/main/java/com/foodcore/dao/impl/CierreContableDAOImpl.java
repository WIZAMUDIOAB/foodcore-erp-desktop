package com.foodcore.dao.impl;

import com.foodcore.dao.CierreContableDAO;
import com.foodcore.model.CierreContable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO JDBC de {@code cierre_contable} para control y bloqueo de períodos mensuales cerrados (v5.2.0).
 */
public class CierreContableDAOImpl implements CierreContableDAO {

    @Override
    public boolean estaPeriodoCerrado(Connection conn, int anio, int mes) throws SQLException {
        String sql = "SELECT 1 FROM cierre_contable WHERE anio = ? AND mes = ? AND estado = 'CERRADO' LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, anio);
            ps.setInt(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<CierreContable> obtenerPorPeriodo(Connection conn, int anio, int mes) throws SQLException {
        String sql = "SELECT cc.*, COALESCE(u.username, 'sistema') AS username "
                + "FROM cierre_contable cc "
                + "LEFT JOIN usuario u ON cc.id_usuario = u.id_usuario "
                + "WHERE cc.anio = ? AND cc.mes = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, anio);
            ps.setInt(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<CierreContable> listarTodos(Connection conn) throws SQLException {
        String sql = "SELECT cc.*, COALESCE(u.username, 'sistema') AS username "
                + "FROM cierre_contable cc "
                + "LEFT JOIN usuario u ON cc.id_usuario = u.id_usuario "
                + "ORDER BY cc.anio DESC, cc.mes DESC";
        List<CierreContable> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public CierreContable registrarCierre(Connection conn, CierreContable cierre) throws SQLException {
        String sql = "INSERT INTO cierre_contable (anio, mes, id_usuario, estado, observaciones) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cierre.getAnio());
            ps.setInt(2, cierre.getMes());
            ps.setInt(3, cierre.getIdUsuario() > 0 ? cierre.getIdUsuario() : 1);
            ps.setString(4, cierre.getEstado() != null ? cierre.getEstado() : "CERRADO");
            ps.setString(5, cierre.getObservaciones());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cierre.setIdCierre(rs.getInt(1));
                }
            }
        }
        return obtenerPorPeriodo(conn, cierre.getAnio(), cierre.getMes()).orElse(cierre);
    }

    private CierreContable mapear(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha_cierre");
        return new CierreContable(
                rs.getInt("id_cierre"),
                rs.getInt("anio"),
                rs.getInt("mes"),
                ts != null ? ts.toLocalDateTime() : null,
                rs.getInt("id_usuario"),
                rs.getString("username"),
                rs.getString("estado"),
                rs.getString("observaciones")
        );
    }
}
