package com.foodcore.dao;

import com.foodcore.model.Caja;
import com.foodcore.model.MovimientoCaja;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Optional;

public class CajaDAOImpl implements CajaDAO {

    @Override
    public Optional<Caja> obtenerCajaAbierta(Connection conn) throws SQLException {
        String sql = "SELECT * FROM caja WHERE estado = 'ABIERTA' ORDER BY id_caja DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Caja c = new Caja();
                c.setIdCaja(rs.getInt("id_caja"));
                c.setIdUsuarioApertura(rs.getInt("id_usuario_apertura"));
                int uCierre = rs.getInt("id_usuario_cierre");
                c.setIdUsuarioCierre(rs.wasNull() ? null : uCierre);
                c.setNombreCaja(rs.getString("nombre_caja"));
                c.setMontoApertura(rs.getBigDecimal("monto_apertura"));
                c.setMontoCierre(rs.getBigDecimal("monto_cierre"));
                Timestamp fa = rs.getTimestamp("fecha_apertura");
                if (fa != null) c.setFechaApertura(fa.toLocalDateTime());
                Timestamp fc = rs.getTimestamp("fecha_cierre");
                if (fc != null) c.setFechaCierre(fc.toLocalDateTime());
                c.setEstado(rs.getString("estado"));
                c.setDiferencia(rs.getBigDecimal("diferencia"));
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    @Override
    public int abrirCaja(Connection conn, Caja caja) throws SQLException {
        String sql = "INSERT INTO caja (id_usuario_apertura, nombre_caja, monto_apertura, estado) VALUES (?, ?, ?, 'ABIERTA')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, caja.getIdUsuarioApertura());
            ps.setString(2, caja.getNombreCaja());
            ps.setBigDecimal(3, caja.getMontoApertura());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    caja.setIdCaja(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo registrar la apertura de caja.");
    }

    @Override
    public void cerrarCaja(Connection conn, int idCaja, int idUsuarioCierre, BigDecimal montoCierre, BigDecimal diferencia) throws SQLException {
        String sql = "UPDATE caja SET id_usuario_cierre = ?, monto_cierre = ?, diferencia = ?, fecha_cierre = CURRENT_TIMESTAMP, estado = 'CERRADA' WHERE id_caja = ? AND estado = 'ABIERTA'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuarioCierre);
            ps.setBigDecimal(2, montoCierre);
            ps.setBigDecimal(3, diferencia);
            ps.setInt(4, idCaja);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("La caja #" + idCaja + " ya fue cerrada previamente por otro proceso.");
            }
        }
    }

    @Override
    public void registrarMovimiento(Connection conn, MovimientoCaja mov) throws SQLException {
        String sql = "INSERT INTO movimiento_caja (id_caja, id_usuario, tipo_movimiento, monto, concepto) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mov.getIdCaja());
            ps.setInt(2, mov.getIdUsuario());
            ps.setString(3, mov.getTipoMovimiento());
            ps.setBigDecimal(4, mov.getMonto());
            ps.setString(5, mov.getConcepto());
            ps.executeUpdate();
        }
    }

    @Override
    public BigDecimal calcularTotalVentasTurno(Connection conn, int idCaja) throws SQLException {
        // Arqueo físico de gaveta: contabiliza exclusivamente las ventas liquidadas en EFECTIVO
        String sql = "SELECT COALESCE(SUM(total), 0.00) FROM venta WHERE id_caja = ? AND estado = 'COMPLETADA' AND metodo_pago = 'EFECTIVO'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        }
        return BigDecimal.ZERO;
    }
}
