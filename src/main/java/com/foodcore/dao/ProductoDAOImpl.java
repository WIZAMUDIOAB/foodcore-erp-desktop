package com.foodcore.dao;

import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductoDAOImpl implements ProductoDAO {

    @Override
    public List<Categoria> listarCategorias(Connection conn) throws SQLException {
        List<Categoria> lista = new ArrayList<>();
        String sql = "SELECT * FROM categoria WHERE activo = TRUE ORDER BY nombre ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Categoria c = new Categoria(
                    rs.getInt("id_categoria"),
                    rs.getString("nombre"),
                    rs.getString("descripcion"),
                    rs.getBoolean("activo")
                );
                lista.add(c);
            }
        }
        return lista;
    }

    @Override
    public List<Producto> listarProductosDisponibles(Connection conn) throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT p.*, c.nombre AS nombre_cat " +
                     "FROM producto p " +
                     "JOIN categoria c ON p.id_categoria = c.id_categoria " +
                     "WHERE p.disponible = TRUE " +
                     "ORDER BY c.nombre, p.nombre";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Producto p = new Producto(
                    rs.getInt("id_producto"),
                    rs.getInt("id_categoria"),
                    rs.getString("codigo"),
                    rs.getString("nombre"),
                    rs.getString("descripcion"),
                    rs.getBigDecimal("precio_venta"),
                    rs.getBigDecimal("costo_estimado"),
                    rs.getBoolean("disponible")
                );
                p.setNombreCategoria(rs.getString("nombre_cat"));
                lista.add(p);
            }
        }
        return lista;
    }

    @Override
    public List<Producto> listarPorCategoria(Connection conn, int idCategoria) throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT p.*, c.nombre AS nombre_cat FROM producto p " +
                     "JOIN categoria c ON p.id_categoria = c.id_categoria " +
                     "WHERE p.disponible = TRUE AND p.id_categoria = ? ORDER BY p.nombre";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCategoria);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Producto p = new Producto(
                        rs.getInt("id_producto"),
                        rs.getInt("id_categoria"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getString("descripcion"),
                        rs.getBigDecimal("precio_venta"),
                        rs.getBigDecimal("costo_estimado"),
                        rs.getBoolean("disponible")
                    );
                    p.setNombreCategoria(rs.getString("nombre_cat"));
                    lista.add(p);
                }
            }
        }
        return lista;
    }
}
