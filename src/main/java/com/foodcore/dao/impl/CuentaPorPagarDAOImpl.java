package com.foodcore.dao.impl;

import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.model.PagoProveedor;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CuentaPorPagarDAOImpl implements CuentaPorPagarDAO {

    @Override
    public List<CuentaPorPagar> listarTodas(Connection conn) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT cxp.*, p.razon_social AS razon_social_proveedor, p.ruc AS ruc_proveedor "
                + "FROM cuenta_por_pagar cxp "
                + "JOIN proveedor p ON cxp.id_proveedor = p.id_proveedor "
                + "ORDER BY cxp.fecha_vencimiento ASC";

        List<CuentaPorPagar> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCxp(rs));
            }
        }
        return lista;
    }

    @Override
    public List<CuentaPorPagar> listarPorEstado(Connection conn, String estado) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT cxp.*, p.razon_social AS razon_social_proveedor, p.ruc AS ruc_proveedor "
                + "FROM cuenta_por_pagar cxp "
                + "JOIN proveedor p ON cxp.id_proveedor = p.id_proveedor "
                + "WHERE cxp.estado = ? "
                + "ORDER BY cxp.fecha_vencimiento ASC";

        List<CuentaPorPagar> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearCxp(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<CuentaPorPagar> listarPorProveedor(Connection conn, int idProveedor) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT cxp.*, p.razon_social AS razon_social_proveedor, p.ruc AS ruc_proveedor "
                + "FROM cuenta_por_pagar cxp "
                + "JOIN proveedor p ON cxp.id_proveedor = p.id_proveedor "
                + "WHERE cxp.id_proveedor = ? "
                + "ORDER BY cxp.fecha_vencimiento ASC";

        List<CuentaPorPagar> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProveedor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearCxp(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Optional<CuentaPorPagar> buscarPorId(Connection conn, int idCuentaPagar) throws SQLException {
        String sql = "SELECT cxp.*, p.razon_social AS razon_social_proveedor, p.ruc AS ruc_proveedor "
                + "FROM cuenta_por_pagar cxp "
                + "JOIN proveedor p ON cxp.id_proveedor = p.id_proveedor "
                + "WHERE cxp.id_cuenta_pagar = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCuentaPagar);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearCxp(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int insertar(Connection conn, CuentaPorPagar cxp) throws SQLException {
        String sql = "INSERT INTO cuenta_por_pagar (id_proveedor, numero_documento, fecha_emision, fecha_vencimiento, "
                + "monto_total, monto_pagado, estado, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cxp.getIdProveedor());
            ps.setString(2, cxp.getNumeroDocumento());
            ps.setDate(3, Date.valueOf(cxp.getFechaEmision()));
            ps.setDate(4, Date.valueOf(cxp.getFechaVencimiento()));
            ps.setDouble(5, cxp.getMontoTotal());
            ps.setDouble(6, cxp.getMontoPagado());
            ps.setString(7, cxp.getEstado());
            ps.setString(8, cxp.getObservaciones());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cxp.setIdCuentaPagar(rs.getInt(1));
                    return cxp.getIdCuentaPagar();
                }
            }
        }
        return 0;
    }

    @Override
    public boolean registrarPago(Connection conn, PagoProveedor pago) throws SQLException {
        // 1. Insertar detalle de pago
        String sqlPago = "INSERT INTO pago_proveedor (id_cuenta_pagar, id_caja, id_usuario, monto, metodo_pago, nota) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sqlPago, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pago.getIdCuentaPagar());
            if (pago.getIdCaja() != null) {
                ps.setInt(2, pago.getIdCaja());
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setInt(3, pago.getIdUsuario());
            ps.setDouble(4, pago.getMonto());
            ps.setString(5, pago.getMetodoPago());
            ps.setString(6, pago.getNota());
            ps.executeUpdate();
        }

        // 2. Actualizar monto pagado y estado en cuenta_por_pagar
        String sqlUpdate = "UPDATE cuenta_por_pagar SET "
                + "monto_pagado = monto_pagado + ?, "
                + "estado = CASE WHEN (monto_total - (monto_pagado + ?)) <= 0.001 THEN 'PAGADA' "
                + "              WHEN fecha_vencimiento < CURRENT_DATE THEN 'VENCIDA' "
                + "              ELSE 'PENDIENTE' END "
                + "WHERE id_cuenta_pagar = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setDouble(1, pago.getMonto());
            ps.setDouble(2, pago.getMonto());
            ps.setInt(3, pago.getIdCuentaPagar());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public int contarVencidas(Connection conn) throws SQLException {
        actualizarEstadosVencidos(conn);
        String sql = "SELECT COUNT(*) FROM cuenta_por_pagar WHERE estado = 'VENCIDA'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void actualizarEstadosVencidos(Connection conn) throws SQLException {
        String sql = "UPDATE cuenta_por_pagar SET estado = 'VENCIDA' "
                + "WHERE estado = 'PENDIENTE' AND fecha_vencimiento < CURRENT_DATE AND (monto_total - monto_pagado) > 0.001";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private CuentaPorPagar mapearCxp(ResultSet rs) throws SQLException {
        CuentaPorPagar c = new CuentaPorPagar();
        c.setIdCuentaPagar(rs.getInt("id_cuenta_pagar"));
        c.setIdProveedor(rs.getInt("id_proveedor"));
        c.setRazonSocialProveedor(rs.getString("razon_social_proveedor"));
        c.setRucProveedor(rs.getString("ruc_proveedor"));
        c.setNumeroDocumento(rs.getString("numero_documento"));
        if (rs.getDate("fecha_emision") != null) {
            c.setFechaEmision(rs.getDate("fecha_emision").toLocalDate());
        }
        if (rs.getDate("fecha_vencimiento") != null) {
            c.setFechaVencimiento(rs.getDate("fecha_vencimiento").toLocalDate());
        }
        c.setMontoTotal(rs.getDouble("monto_total"));
        c.setMontoPagado(rs.getDouble("monto_pagado"));
        c.setEstado(rs.getString("estado"));
        c.setObservaciones(rs.getString("observaciones"));
        if (rs.getTimestamp("fecha_registro") != null) {
            c.setFechaRegistro(rs.getTimestamp("fecha_registro").toLocalDateTime());
        }
        return c;
    }
}
