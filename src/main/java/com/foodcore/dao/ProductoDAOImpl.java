package com.foodcore.dao;

import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        String sql = "SELECT p.*, COALESCE(c.nombre, 'General') AS nombre_cat " +
                     "FROM producto p " +
                     "LEFT JOIN categoria c ON p.id_categoria = c.id_categoria " +
                     "WHERE (p.activo = 1 OR p.activo IS NULL) " +
                     "AND (p.disponible = TRUE OR p.disponible IS NULL OR p.disponible = 1) " +
                     "ORDER BY p.id_producto ASC";
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
    public List<Producto> listarMenuDelDia(Connection conn) throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT p.*, COALESCE(c.nombre, 'General') AS nombre_cat " +
                     "FROM producto p " +
                     "LEFT JOIN categoria c ON p.id_categoria = c.id_categoria " +
                     "WHERE (p.activo = 1 OR p.activo IS NULL) " +
                     "AND (p.disponible = TRUE OR p.disponible IS NULL OR p.disponible = 1) " +
                     "AND p.es_menu_del_dia = 1 " +
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
    public List<Producto> listarTodos(Connection conn) throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT p.*, c.nombre AS nombre_cat " +
                     "FROM producto p " +
                     "JOIN categoria c ON p.id_categoria = c.id_categoria " +
                     "WHERE p.activo = 1 ORDER BY p.id_producto ASC";
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
    public Producto buscarPorCodigo(Connection conn, String codigo) throws SQLException {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String sql = "SELECT p.*, COALESCE(c.nombre, 'General') AS nombre_cat " +
                     "FROM producto p " +
                     "LEFT JOIN categoria c ON p.id_categoria = c.id_categoria " +
                     "WHERE UPPER(TRIM(p.codigo)) = UPPER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapearProducto(rs);
                }
            }
        }
        return null;
    }

    @Override
    public void reactivar(Connection conn, int idProducto) throws SQLException {
        String sql = "UPDATE producto SET activo = 1 WHERE id_producto = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.executeUpdate();
        }
    }

    @Override
    public int crear(Connection conn, Producto p) throws SQLException {
        String sql = "INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, " +
                     "costo_estimado, disponible, imagen_url, es_menu_del_dia, fecha_disponibilidad, es_combo) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getIdCategoria());
            ps.setString(2, p.getCodigo());
            ps.setString(3, p.getNombre());
            ps.setString(4, p.getDescripcion());
            ps.setBigDecimal(5, p.getPrecioVenta());
            ps.setBigDecimal(6, p.getCostoEstimado());
            ps.setBoolean(7, p.isDisponible());
            ps.setString(8, p.getImagenUrl());
            ps.setBoolean(9, p.isEsMenuDelDia());
            if (p.getFechaDisponibilidad() != null) {
                ps.setDate(10, Date.valueOf(p.getFechaDisponibilidad()));
            } else {
                ps.setNull(10, java.sql.Types.DATE);
            }
            ps.setBoolean(11, p.isEsCombo());
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
        String sql = "UPDATE producto SET id_categoria = ?, codigo = ?, nombre = ?, descripcion = ?, " +
                     "precio_venta = ?, costo_estimado = ?, disponible = ?, imagen_url = ?, " +
                     "es_menu_del_dia = ?, fecha_disponibilidad = ?, es_combo = ? " +
                     "WHERE id_producto = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getIdCategoria());
            ps.setString(2, p.getCodigo());
            ps.setString(3, p.getNombre());
            ps.setString(4, p.getDescripcion());
            ps.setBigDecimal(5, p.getPrecioVenta());
            ps.setBigDecimal(6, p.getCostoEstimado());
            ps.setBoolean(7, p.isDisponible());
            ps.setString(8, p.getImagenUrl());
            ps.setBoolean(9, p.isEsMenuDelDia());
            if (p.getFechaDisponibilidad() != null) {
                ps.setDate(10, Date.valueOf(p.getFechaDisponibilidad()));
            } else {
                ps.setNull(10, java.sql.Types.DATE);
            }
            ps.setBoolean(11, p.isEsCombo());
            ps.setInt(12, p.getIdProducto());
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
    public void cambiarMenuDelDia(Connection conn, int idProducto, boolean esMenuDelDia) throws SQLException {
        String sql = "UPDATE producto SET es_menu_del_dia = ? WHERE id_producto = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, esMenuDelDia);
            ps.setInt(2, idProducto);
            ps.executeUpdate();
        }
    }

    @Override
    public void eliminar(Connection conn, int idProducto) throws SQLException {
        String sql = "UPDATE producto SET activo = 0 WHERE id_producto = ?";
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
        try {
            p.setImagenUrl(rs.getString("imagen_url"));
        } catch (SQLException ignored) {
            p.setImagenUrl(null);
        }
        try {
            p.setEsMenuDelDia(rs.getBoolean("es_menu_del_dia"));
        } catch (SQLException ignored) {
            p.setEsMenuDelDia(false);
        }
        try {
            Date fDisp = rs.getDate("fecha_disponibilidad");
            if (fDisp != null) {
                p.setFechaDisponibilidad(fDisp.toLocalDate());
            }
        } catch (SQLException ignored) {
            p.setFechaDisponibilidad(null);
        }
        try {
            p.setEsCombo(rs.getBoolean("es_combo"));
        } catch (SQLException ignored) {
            p.setEsCombo(false);
        }
        p.setNombreCategoria(rs.getString("nombre_cat"));
        return p;
    }
}
