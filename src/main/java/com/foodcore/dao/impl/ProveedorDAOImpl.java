package com.foodcore.dao.impl;

import com.foodcore.dao.ProveedorDAO;
import com.foodcore.model.Proveedor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProveedorDAOImpl implements ProveedorDAO {

    @Override
    public List<Proveedor> listarTodos(Connection conn) throws SQLException {
        String sql = "SELECT p.*, "
                + "COALESCE(SUM(CASE WHEN cxp.estado <> 'PAGADA' THEN (cxp.monto_total - cxp.monto_pagado) ELSE 0 END), 0) AS deuda_actual, "
                + "COUNT(CASE WHEN cxp.estado <> 'PAGADA' THEN 1 END) AS facturas_pendientes "
                + "FROM proveedor p "
                + "LEFT JOIN cuenta_por_pagar cxp ON p.id_proveedor = cxp.id_proveedor "
                + "GROUP BY p.id_proveedor "
                + "ORDER BY p.razon_social ASC";

        List<Proveedor> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearProveedor(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Proveedor> listarActivos(Connection conn) throws SQLException {
        String sql = "SELECT p.*, "
                + "COALESCE(SUM(CASE WHEN cxp.estado <> 'PAGADA' THEN (cxp.monto_total - cxp.monto_pagado) ELSE 0 END), 0) AS deuda_actual, "
                + "COUNT(CASE WHEN cxp.estado <> 'PAGADA' THEN 1 END) AS facturas_pendientes "
                + "FROM proveedor p "
                + "LEFT JOIN cuenta_por_pagar cxp ON p.id_proveedor = cxp.id_proveedor "
                + "WHERE p.activo = 1 "
                + "GROUP BY p.id_proveedor "
                + "ORDER BY p.razon_social ASC";

        List<Proveedor> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearProveedor(rs));
            }
        }
        return lista;
    }

    @Override
    public Optional<Proveedor> buscarPorId(Connection conn, int idProveedor) throws SQLException {
        String sql = "SELECT p.*, "
                + "COALESCE(SUM(CASE WHEN cxp.estado <> 'PAGADA' THEN (cxp.monto_total - cxp.monto_pagado) ELSE 0 END), 0) AS deuda_actual, "
                + "COUNT(CASE WHEN cxp.estado <> 'PAGADA' THEN 1 END) AS facturas_pendientes "
                + "FROM proveedor p "
                + "LEFT JOIN cuenta_por_pagar cxp ON p.id_proveedor = cxp.id_proveedor "
                + "WHERE p.id_proveedor = ? "
                + "GROUP BY p.id_proveedor";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProveedor);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearProveedor(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Proveedor> buscarPorRuc(Connection conn, String ruc) throws SQLException {
        String sql = "SELECT p.*, 0 AS deuda_actual, 0 AS facturas_pendientes FROM proveedor p WHERE p.ruc = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ruc);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearProveedor(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int insertar(Connection conn, Proveedor proveedor) throws SQLException {
        String sql = "INSERT INTO proveedor (ruc, razon_social, contacto, telefono, email, dias_credito, estado, activo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, proveedor.getRuc());
            ps.setString(2, proveedor.getRazonSocial());
            ps.setString(3, proveedor.getContacto());
            ps.setString(4, proveedor.getTelefono());
            ps.setString(5, proveedor.getEmail());
            ps.setInt(6, proveedor.getDiasCredito());
            ps.setString(7, proveedor.getEstado());
            ps.setBoolean(8, proveedor.isActivo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    proveedor.setIdProveedor(rs.getInt(1));
                    return proveedor.getIdProveedor();
                }
            }
        }
        return 0;
    }

    @Override
    public boolean actualizar(Connection conn, Proveedor proveedor) throws SQLException {
        String sql = "UPDATE proveedor SET ruc = ?, razon_social = ?, contacto = ?, telefono = ?, email = ?, "
                + "dias_credito = ?, estado = ?, activo = ? WHERE id_proveedor = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, proveedor.getRuc());
            ps.setString(2, proveedor.getRazonSocial());
            ps.setString(3, proveedor.getContacto());
            ps.setString(4, proveedor.getTelefono());
            ps.setString(5, proveedor.getEmail());
            ps.setInt(6, proveedor.getDiasCredito());
            ps.setString(7, proveedor.getEstado());
            ps.setBoolean(8, proveedor.isActivo());
            ps.setInt(9, proveedor.getIdProveedor());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean cambiarEstado(Connection conn, int idProveedor, boolean activo) throws SQLException {
        String sql = "UPDATE proveedor SET activo = ?, estado = ? WHERE id_proveedor = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, activo);
            ps.setString(2, activo ? "ACTIVO" : "INACTIVO");
            ps.setInt(3, idProveedor);
            return ps.executeUpdate() > 0;
        }
    }

    private Proveedor mapearProveedor(ResultSet rs) throws SQLException {
        Proveedor p = new Proveedor();
        p.setIdProveedor(rs.getInt("id_proveedor"));
        p.setRuc(rs.getString("ruc"));
        p.setRazonSocial(rs.getString("razon_social"));
        p.setContacto(rs.getString("contacto"));
        p.setTelefono(rs.getString("telefono"));
        p.setEmail(rs.getString("email"));
        p.setDiasCredito(rs.getInt("dias_credito"));
        p.setEstado(rs.getString("estado"));
        p.setActivo(rs.getBoolean("activo"));
        if (rs.getTimestamp("fecha_registro") != null) {
            p.setFechaRegistro(rs.getTimestamp("fecha_registro").toLocalDateTime());
        }
        p.setDeudaActual(rs.getDouble("deuda_actual"));
        p.setFacturasPendientes(rs.getInt("facturas_pendientes"));
        return p;
    }
}
