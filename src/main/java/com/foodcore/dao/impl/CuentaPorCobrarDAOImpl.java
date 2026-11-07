package com.foodcore.dao.impl;

import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.model.CobroCliente;
import com.foodcore.model.CuentaPorCobrar;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CuentaPorCobrarDAOImpl implements CuentaPorCobrarDAO {

    @Override
    public List<CuentaPorCobrar> listarTodas(Connection conn) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT cxc.*, c.razon_social AS razon_social_cliente, c.numero_documento AS numero_documento_cliente "
                + "FROM cuenta_por_cobrar cxc "
                + "JOIN cliente c ON cxc.id_cliente = c.id_cliente "
                + "ORDER BY cxc.fecha_vencimiento ASC";

        List<CuentaPorCobrar> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCxc(rs));
            }
        }
        return lista;
    }

    @Override
    public List<CuentaPorCobrar> listarPorEstado(Connection conn, String estado) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT cxc.*, c.razon_social AS razon_social_cliente, c.numero_documento AS numero_documento_cliente "
                + "FROM cuenta_por_cobrar cxc "
                + "JOIN cliente c ON cxc.id_cliente = c.id_cliente "
                + "WHERE cxc.estado = ? "
                + "ORDER BY cxc.fecha_vencimiento ASC";

        List<CuentaPorCobrar> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearCxc(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<CuentaPorCobrar> listarPorCliente(Connection conn, int idCliente) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT cxc.*, c.razon_social AS razon_social_cliente, c.numero_documento AS numero_documento_cliente "
                + "FROM cuenta_por_cobrar cxc "
                + "JOIN cliente c ON cxc.id_cliente = c.id_cliente "
                + "WHERE cxc.id_cliente = ? "
                + "ORDER BY cxc.fecha_vencimiento ASC";

        List<CuentaPorCobrar> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearCxc(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Optional<CuentaPorCobrar> buscarPorId(Connection conn, int idCuentaCobrar) throws SQLException {
        String sql = "SELECT cxc.*, c.razon_social AS razon_social_cliente, c.numero_documento AS numero_documento_cliente "
                + "FROM cuenta_por_cobrar cxc "
                + "JOIN cliente c ON cxc.id_cliente = c.id_cliente "
                + "WHERE cxc.id_cuenta_cobrar = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCuentaCobrar);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearCxc(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int insertar(Connection conn, CuentaPorCobrar cxc) throws SQLException {
        String sql = "INSERT INTO cuenta_por_cobrar (id_cliente, id_venta, numero_comprobante, fecha_emision, fecha_vencimiento, "
                + "monto_total, monto_cobrado, estado, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cxc.getIdCliente());
            if (cxc.getIdVenta() != null) {
                ps.setInt(2, cxc.getIdVenta());
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, cxc.getNumeroComprobante());
            ps.setDate(4, Date.valueOf(cxc.getFechaEmision()));
            ps.setDate(5, Date.valueOf(cxc.getFechaVencimiento()));
            ps.setDouble(6, cxc.getMontoTotal());
            ps.setDouble(7, cxc.getMontoCobrado());
            ps.setString(8, cxc.getEstado());
            ps.setString(9, cxc.getObservaciones());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cxc.setIdCuentaCobrar(rs.getInt(1));
                    return cxc.getIdCuentaCobrar();
                }
            }
        }
        return 0;
    }

    @Override
    public boolean registrarCobro(Connection conn, CobroCliente cobro) throws SQLException {
        // 1. Insertar detalle de cobro
        String sqlCobro = "INSERT INTO cobro_cliente (id_cuenta_cobrar, id_caja, id_usuario, monto, metodo_pago, nota) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sqlCobro, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cobro.getIdCuentaCobrar());
            if (cobro.getIdCaja() != null) {
                ps.setInt(2, cobro.getIdCaja());
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setInt(3, cobro.getIdUsuario());
            ps.setDouble(4, cobro.getMonto());
            ps.setString(5, cobro.getMetodoPago());
            ps.setString(6, cobro.getNota());
            ps.executeUpdate();
        }

        // 2. Actualizar monto cobrado y estado en cuenta_por_cobrar
        String sqlUpdate = "UPDATE cuenta_por_cobrar SET "
                + "monto_cobrado = monto_cobrado + ?, "
                + "estado = CASE WHEN (monto_total - (monto_cobrado + ?)) <= 0.001 THEN 'COBRADA' "
                + "              WHEN fecha_vencimiento < CURRENT_DATE THEN 'VENCIDA' "
                + "              ELSE 'PENDIENTE' END "
                + "WHERE id_cuenta_cobrar = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setDouble(1, cobro.getMonto());
            ps.setDouble(2, cobro.getMonto());
            ps.setInt(3, cobro.getIdCuentaCobrar());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public int contarVencidas(Connection conn) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT COUNT(*) FROM cuenta_por_cobrar WHERE estado = 'VENCIDA'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void actualizarEstadosVencidos(Connection conn) throws SQLException {
        String sql = "UPDATE cuenta_por_cobrar SET estado = 'VENCIDA' "
                + "WHERE estado = 'PENDIENTE' AND fecha_vencimiento < CURRENT_DATE AND (monto_total - monto_cobrado) > 0.001";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private CuentaPorCobrar mapearCxc(ResultSet rs) throws SQLException {
        CuentaPorCobrar c = new CuentaPorCobrar();
        c.setIdCuentaCobrar(rs.getInt("id_cuenta_cobrar"));
        c.setIdCliente(rs.getInt("id_cliente"));
        c.setRazonSocialCliente(rs.getString("razon_social_cliente"));
        c.setNumeroDocumentoCliente(rs.getString("numero_documento_cliente"));
        int idV = rs.getInt("id_venta");
        if (!rs.wasNull()) {
            c.setIdVenta(idV);
        }
        c.setNumeroComprobante(rs.getString("numero_comprobante"));
        if (rs.getDate("fecha_emision") != null) {
            c.setFechaEmision(rs.getDate("fecha_emision").toLocalDate());
        }
        if (rs.getDate("fecha_vencimiento") != null) {
            c.setFechaVencimiento(rs.getDate("fecha_vencimiento").toLocalDate());
        }
        c.setMontoTotal(rs.getDouble("monto_total"));
        c.setMontoCobrado(rs.getDouble("monto_cobrado"));
        c.setEstado(rs.getString("estado"));
        c.setObservaciones(rs.getString("observaciones"));
        if (rs.getTimestamp("fecha_registro") != null) {
            c.setFechaRegistro(rs.getTimestamp("fecha_registro").toLocalDateTime());
        }
        return c;
    }
}
