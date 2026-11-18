package com.foodcore.dao.impl;

import com.foodcore.dao.MovimientoPagoDAO;
import com.foodcore.model.MovimientoPago;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC de {@link MovimientoPagoDAO}.
 */
public class MovimientoPagoDAOImpl implements MovimientoPagoDAO {

    private static final String BASE_SELECT =
            "SELECT m.id_movimiento, m.id_usuario, m.periodo_mes, m.tipo_movimiento, m.concepto, "
                    + "m.monto, m.fecha_movimiento, m.registrado_por, m.observaciones, m.creado_en, "
                    + "COALESCE(NULLIF(TRIM(u.nombre_completo), ''), "
                    + "NULLIF(TRIM(CONCAT(COALESCE(e.nombres, ''), ' ', COALESCE(e.apellidos, ''))), ''), "
                    + "u.username) AS nombre_empleado "
                    + "FROM movimiento_pago m "
                    + "JOIN usuario u ON m.id_usuario = u.id_usuario "
                    + "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado ";

    @Override
    public long insertar(Connection conn, MovimientoPago mov) throws SQLException {
        String sql = "INSERT INTO movimiento_pago "
                + "(id_usuario, periodo_mes, tipo_movimiento, concepto, monto, fecha_movimiento, registrado_por, observaciones) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, mov.getIdUsuario());
            ps.setString(2, mov.getPeriodoMes());
            ps.setString(3, mov.getTipoMovimiento());
            ps.setString(4, mov.getConcepto());
            ps.setBigDecimal(5, mov.getMonto());
            ps.setDate(6, Date.valueOf(mov.getFechaMovimiento()));
            if (mov.getRegistradoPor() != null && mov.getRegistradoPor() > 0) {
                ps.setInt(7, mov.getRegistradoPor());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setString(8, mov.getObservaciones());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    mov.setIdMovimiento(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se generó ID para movimiento_pago.");
    }

    @Override
    public List<MovimientoPago> listarPorPeriodo(Connection conn, String periodoMes) throws SQLException {
        String sql = BASE_SELECT + "WHERE m.periodo_mes = ? ORDER BY m.fecha_movimiento DESC, m.id_movimiento DESC";
        List<MovimientoPago> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, periodoMes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<MovimientoPago> listarPorUsuarioYPeriodo(Connection conn, int idUsuario, String periodoMes)
            throws SQLException {
        String sql = BASE_SELECT
                + "WHERE m.id_usuario = ? AND m.periodo_mes = ? "
                + "ORDER BY m.fecha_movimiento DESC, m.id_movimiento DESC";
        List<MovimientoPago> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setString(2, periodoMes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public boolean eliminar(Connection conn, long idMovimiento) throws SQLException {
        String sql = "DELETE FROM movimiento_pago WHERE id_movimiento = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMovimiento);
            return ps.executeUpdate() > 0;
        }
    }

    private MovimientoPago mapear(ResultSet rs) throws SQLException {
        MovimientoPago m = new MovimientoPago();
        m.setIdMovimiento(rs.getLong("id_movimiento"));
        m.setIdUsuario(rs.getInt("id_usuario"));
        m.setPeriodoMes(rs.getString("periodo_mes"));
        m.setTipoMovimiento(rs.getString("tipo_movimiento"));
        m.setConcepto(rs.getString("concepto"));
        m.setMonto(rs.getBigDecimal("monto"));
        Date fecha = rs.getDate("fecha_movimiento");
        if (fecha != null) {
            m.setFechaMovimiento(fecha.toLocalDate());
        }
        int reg = rs.getInt("registrado_por");
        if (!rs.wasNull()) {
            m.setRegistradoPor(reg);
        }
        m.setObservaciones(rs.getString("observaciones"));
        Timestamp creado = rs.getTimestamp("creado_en");
        if (creado != null) {
            m.setCreadoEn(creado.toLocalDateTime());
        }
        m.setNombreEmpleado(rs.getString("nombre_empleado"));
        return m;
    }
}
