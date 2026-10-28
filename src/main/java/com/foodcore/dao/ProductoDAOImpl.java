package com.foodcore.dao;

import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;

import java.sql.*;
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
                Producto p = mapearProducto(rs);
                lista.add(p);
            }
        }
        return lista;
    }

    @Override
    public List<Producto> listarTodos(Connection conn) throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT p.*, c.nombre AS nombre_cat " +
                     "FROM producto p " +
                     "JOIN categoria c ON p.id_categoria = c.id_categoria " +
                     "ORDER BY p.id_producto ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearProducto(rs));
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
                    lista.add(mapearProducto(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public int crear(Connection conn, Producto p) throws SQLException {
        String sql = "INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getIdCategoria());
            ps.setString(2, p.getCodigo());
            ps.setString(3, p.getNombre());
            ps.setString(4, p.getDescripcion());
            ps.setBigDecimal(5, p.getPrecioVenta());
            ps.setBigDecimal(6, p.getCostoEstimado());
            ps.setBoolean(7, p.isDisponible());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    p.setIdProducto(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID del producto generado.");
    }

    @Override
    public void actualizar(Connection conn, Producto p) throws SQLException {
        String sql = "UPDATE producto SET id_categoria = ?, codigo = ?, nombre = ?, descripcion = ?, precio_venta = ?, costo_estimado = ?, disponible = ? " +
                     "WHERE id_producto = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getIdCategoria());
            ps.setString(2, p.getCodigo());
            ps.setString(3, p.getNombre());
            ps.setString(4, p.getDescripcion());
            ps.setBigDecimal(5, p.getPrecioVenta());
            ps.setBigDecimal(6, p.getCostoEstimado());
            ps.setBoolean(7, p.isDisponible());
            ps.setInt(8, p.getIdProducto());
            ps.executeUpdate();
        }
    }

    @Override
    public void cambiarDisponibilidad(Connection conn, int idProducto, boolean disponible) throws SQLException {
        String sql = "UPDATE producto SET disponible = ? WHERE id_producto = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, disponible);
            ps.setInt(2, idProducto);
            ps.executeUpdate();
        }
    }

    @Override
    public void eliminar(Connection conn, int idProducto) throws SQLException {
        String sql = "DELETE FROM producto WHERE id_producto = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.executeUpdate();
        }
    }

    private Producto mapearProducto(ResultSet rs) throws SQLException {
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
        return p;
    }
}
