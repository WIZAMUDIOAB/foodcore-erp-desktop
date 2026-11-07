package com.foodcore.dao;

import com.foodcore.model.Caja;
import com.foodcore.model.MovimientoCaja;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class CajaDAOImpl implements CajaDAO {

    @Override
    public Optional<Caja> obtenerCajaAbierta(Connection conn) throws SQLException {
        String sql = "SELECT * FROM caja WHERE estado IN ('ABIERTA', 'ABIERTO') ORDER BY id_caja DESC LIMIT 1";
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
                if (fa != null) {
                    c.setFechaApertura(fa.toLocalDateTime());
                }
                Timestamp fc = rs.getTimestamp("fecha_cierre");
                if (fc != null) {
                    c.setFechaCierre(fc.toLocalDateTime());
                }
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
        int idGenerado;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, caja.getIdUsuarioApertura());
            ps.setString(2, caja.getNombreCaja());
            ps.setBigDecimal(3, caja.getMontoApertura());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    idGenerado = rs.getInt(1);
                    caja.setIdCaja(idGenerado);
                } else {
                    throw new SQLException("No se pudo registrar la apertura de caja.");
                }
            }
        }

        // Sincronizar con tabla canónica turno_caja (SPEC-FOODCORE-v1.0 §5, §8.3, R-02.4)
        String sqlTurno = "INSERT INTO turno_caja (id_turno, id_usuario_apertura, saldo_inicial, estado, observacion) VALUES (?, ?, ?, 'ABIERTO', ?)";
        try (PreparedStatement psTurno = conn.prepareStatement(sqlTurno)) {
            psTurno.setLong(1, idGenerado);
            psTurno.setLong(2, caja.getIdUsuarioApertura());
            psTurno.setBigDecimal(3, caja.getMontoApertura());
            psTurno.setString(4, caja.getNombreCaja());
            psTurno.executeUpdate();
        }
        return idGenerado;
    }

    @Override
    public void cerrarCaja(Connection conn, int idCaja, int idUsuarioCierre, BigDecimal montoCierre, BigDecimal diferencia) throws SQLException {
        BigDecimal saldoTeorico = montoCierre != null && diferencia != null
            ? montoCierre.subtract(diferencia)
            : montoCierre;
        cerrarCaja(conn, idCaja, idUsuarioCierre, saldoTeorico, montoCierre, diferencia);
    }

    @Override
    public void cerrarCaja(Connection conn, int idCaja, int idUsuarioCierre, BigDecimal saldoFinalTeorico, BigDecimal saldoFinalContado, BigDecimal diferencia) throws SQLException {
        String sql = "UPDATE caja SET id_usuario_cierre = ?, monto_cierre = ?, diferencia = ?, fecha_cierre = CURRENT_TIMESTAMP, estado = 'CERRADA' "
            + "WHERE id_caja = ? AND estado IN ('ABIERTA', 'ABIERTO')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuarioCierre);
            ps.setBigDecimal(2, saldoFinalContado);
            ps.setBigDecimal(3, diferencia);
            ps.setInt(4, idCaja);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("La caja #" + idCaja + " ya fue cerrada previamente por otro proceso.");
            }
        }

        String sqlTurno = "UPDATE turno_caja SET id_usuario_cierre = ?, saldo_final_teorico = ?, saldo_final_contado = ?, diferencia = ?, "
            + "estado = 'CERRADO', cerrado_en = CURRENT_TIMESTAMP WHERE id_turno = ?";
        try (PreparedStatement psTurno = conn.prepareStatement(sqlTurno)) {
            psTurno.setLong(1, idUsuarioCierre);
            psTurno.setBigDecimal(2, saldoFinalTeorico);
            psTurno.setBigDecimal(3, saldoFinalContado);
            psTurno.setBigDecimal(4, diferencia);
            psTurno.setLong(5, idCaja);
            psTurno.executeUpdate();
        }
    }

    @Override
    public void registrarMovimiento(Connection conn, MovimientoCaja mov) throws SQLException {
        String sql = "INSERT INTO movimiento_caja (id_caja, id_usuario, tipo_movimiento, medio_pago, monto, concepto) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mov.getIdCaja());
            ps.setInt(2, mov.getIdUsuario());
            ps.setString(3, mov.getTipoMovimiento());
            ps.setString(4, mov.getMedioPago() != null ? mov.getMedioPago() : "EFECTIVO");
            ps.setBigDecimal(5, mov.getMonto());
            ps.setString(6, mov.getConcepto());
            ps.executeUpdate();
        }
    }

    @Override
    public BigDecimal calcularTotalVentasTurno(Connection conn, int idCaja) throws SQLException {
        String sql = "SELECT COALESCE(SUM(monto), 0.00) FROM movimiento_caja WHERE id_caja = ? AND tipo_movimiento = 'VENTA' AND COALESCE(medio_pago, 'EFECTIVO') = 'EFECTIVO'";
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

    @Override
    public BigDecimal calcularTotalGastosTurno(Connection conn, int idCaja) throws SQLException {
        String sql = "SELECT COALESCE(SUM(ABS(monto)), 0.00) FROM movimiento_caja "
            + "WHERE id_caja = ? AND tipo_movimiento IN ('EGRESO', 'ANULACION') "
            + "AND COALESCE(medio_pago, 'EFECTIVO') = 'EFECTIVO' "
            + "AND concepto NOT LIKE 'Cierre Z de turno%'";
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

    @Override
    public Map<String, BigDecimal> obtenerTotalesPorMedioPago(Connection conn, int idCaja) throws SQLException {
        Map<String, BigDecimal> totales = new LinkedHashMap<>();
        String sql = "SELECT COALESCE(medio_pago, 'EFECTIVO') AS medio, COALESCE(SUM(monto), 0.00) AS total "
            + "FROM movimiento_caja WHERE id_caja = ? AND tipo_movimiento = 'VENTA' GROUP BY COALESCE(medio_pago, 'EFECTIVO')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    totales.put(rs.getString("medio"), rs.getBigDecimal("total"));
                }
            }
        }
        return totales;
    }
}
