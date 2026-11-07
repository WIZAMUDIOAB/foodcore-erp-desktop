package com.foodcore.dao.impl;

import com.foodcore.dao.CompraDAO;
import com.foodcore.model.Compra;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CompraDAOImpl implements CompraDAO {

    @Override
    public List<Compra> listarTodas(Connection conn) throws SQLException {
        String sql = "SELECT c.*, p.ruc AS ruc_proveedor, p.razon_social AS razon_social_proveedor "
                + "FROM compra c "
                + "JOIN proveedor p ON c.id_proveedor = p.id_proveedor "
                + "ORDER BY c.fecha_emision DESC";

        List<Compra> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCompra(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Compra> listarPorRangoFecha(Connection conn, LocalDate inicio, LocalDate fin) throws SQLException {
        String sql = "SELECT c.*, p.ruc AS ruc_proveedor, p.razon_social AS razon_social_proveedor "
                + "FROM compra c "
                + "JOIN proveedor p ON c.id_proveedor = p.id_proveedor "
                + "WHERE c.fecha_emision BETWEEN ? AND ? "
                + "ORDER BY c.fecha_emision DESC";

        List<Compra> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(inicio));
            ps.setDate(2, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearCompra(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public int insertar(Connection conn, Compra compra) throws SQLException {
        String sql = "INSERT INTO compra (id_proveedor, serie, numero, fecha_emision, base_imponible, igv, total, estado, id_usuario) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, compra.getIdProveedor());
            ps.setString(2, compra.getSerie());
            ps.setString(3, compra.getNumero());
            ps.setDate(4, Date.valueOf(compra.getFechaEmision()));
            ps.setDouble(5, compra.getBaseImponible());
            ps.setDouble(6, compra.getIgv());
            ps.setDouble(7, compra.getTotal());
            ps.setString(8, compra.getEstado());
            ps.setInt(9, compra.getIdUsuario());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    compra.setIdCompra(rs.getInt(1));
                    return compra.getIdCompra();
                }
            }
        }
        return 0;
    }

    private Compra mapearCompra(ResultSet rs) throws SQLException {
        Compra c = new Compra();
        c.setIdCompra(rs.getInt("id_compra"));
        c.setIdProveedor(rs.getInt("id_proveedor"));
        c.setRucProveedor(rs.getString("ruc_proveedor"));
        c.setRazonSocialProveedor(rs.getString("razon_social_proveedor"));
        c.setSerie(rs.getString("serie"));
        c.setNumero(rs.getString("numero"));
        if (rs.getDate("fecha_emision") != null) {
            c.setFechaEmision(rs.getDate("fecha_emision").toLocalDate());
        }
        c.setBaseImponible(rs.getDouble("base_imponible"));
        c.setIgv(rs.getDouble("igv"));
        c.setTotal(rs.getDouble("total"));
        c.setEstado(rs.getString("estado"));
        c.setIdUsuario(rs.getInt("id_usuario"));
        if (rs.getTimestamp("fecha_registro") != null) {
            c.setFechaRegistro(rs.getTimestamp("fecha_registro").toLocalDateTime());
        }
        return c;
    }
}
