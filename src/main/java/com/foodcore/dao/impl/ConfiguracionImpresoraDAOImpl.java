package com.foodcore.dao.impl;

import com.foodcore.dao.ConfiguracionImpresoraDAO;
import com.foodcore.model.ConfiguracionImpresora;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO JDBC para la asignación de impresoras Windows por categoría de producto ({@code configuracion_impresora}).
 */
public class ConfiguracionImpresoraDAOImpl implements ConfiguracionImpresoraDAO {

    @Override
    public List<ConfiguracionImpresora> listarTodas(Connection conn) throws SQLException {
        String sql = "SELECT ci.id_impresora, ci.id_categoria, c.nombre AS nombre_categoria, "
                + "ci.nombre_impresora_windows, ci.activa "
                + "FROM configuracion_impresora ci "
                + "JOIN categoria c ON ci.id_categoria = c.id_categoria "
                + "ORDER BY c.nombre ASC";
        List<ConfiguracionImpresora> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ConfiguracionImpresora(
                        rs.getInt("id_impresora"),
                        rs.getInt("id_categoria"),
                        rs.getString("nombre_categoria"),
                        rs.getString("nombre_impresora_windows"),
                        rs.getBoolean("activa")
                ));
            }
        }
        return lista;
    }

    @Override
    public void guardarOActualizar(Connection conn, int idCategoriaProducto, String nombreImpresoraWindows)
            throws SQLException {
        String sql = "INSERT INTO configuracion_impresora (id_categoria, nombre_impresora_windows, activa) "
                + "VALUES (?, ?, 1) AS nueva "
                + "ON DUPLICATE KEY UPDATE nombre_impresora_windows = nueva.nombre_impresora_windows, activa = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCategoriaProducto);
            ps.setString(2, nombreImpresoraWindows.trim());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean eliminarPorCategoria(Connection conn, int idCategoriaProducto) throws SQLException {
        String sql = "DELETE FROM configuracion_impresora WHERE id_categoria = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCategoriaProducto);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Map<Integer, String> obtenerMapaCategoriaAImpresora(Connection conn) throws SQLException {
        String sql = "SELECT id_categoria, nombre_impresora_windows FROM configuracion_impresora WHERE activa = 1";
        Map<Integer, String> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getInt("id_categoria"), rs.getString("nombre_impresora_windows"));
            }
        }
        return mapa;
    }

    @Override
    public Map<Integer, Integer> obtenerMapaProductoACategoria(Connection conn) throws SQLException {
        String sql = "SELECT id_producto, id_categoria FROM producto";
        Map<Integer, Integer> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getInt("id_producto"), rs.getInt("id_categoria"));
            }
        }
        return mapa;
    }

    @Override
    public Map<Integer, String> obtenerMapaNombreCategorias(Connection conn) throws SQLException {
        String sql = "SELECT id_categoria, nombre FROM categoria";
        Map<Integer, String> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getInt("id_categoria"), rs.getString("nombre"));
            }
        }
        return mapa;
    }
}
