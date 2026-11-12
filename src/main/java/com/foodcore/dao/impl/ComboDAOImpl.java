package com.foodcore.dao.impl;

import com.foodcore.dao.ComboDAO;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación JDBC de {@link ComboDAO} para gestión de combos y explosión de componentes (v4.8.0 Módulo 3).
 */
public class ComboDAOImpl implements ComboDAO {

    @Override
    public List<Combo> listarCombosActivos(Connection conn) throws SQLException {
        String sql = "SELECT c.*, p.codigo AS codigo_prod, p.id_categoria "
                + "FROM combo c "
                + "LEFT JOIN producto p ON c.id_producto = p.id_producto "
                + "WHERE c.activo = 1 "
                + "ORDER BY c.id_combo ASC";
        List<Combo> combos = new ArrayList<>();
        Map<Integer, Combo> porId = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Combo c = mapearCombo(rs);
                combos.add(c);
                porId.put(c.getIdCombo(), c);
            }
        }
        if (porId.isEmpty()) {
            return combos;
        }
        Map<Integer, List<ComboDetalle>> detallesPorCombo = listarTodosDetallesAgrupados(conn);
        for (Combo c : combos) {
            c.setDetalles(detallesPorCombo.getOrDefault(c.getIdCombo(), List.of()));
        }
        return combos;
    }

    @Override
    public Optional<Combo> obtenerPorId(Connection conn, int idCombo) throws SQLException {
        String sql = "SELECT c.*, p.codigo AS codigo_prod, p.id_categoria "
                + "FROM combo c "
                + "LEFT JOIN producto p ON c.id_producto = p.id_producto "
                + "WHERE c.id_combo = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCombo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Combo combo = mapearCombo(rs);
                    combo.setDetalles(listarDetallesPorCombo(conn, combo.getIdCombo()));
                    return Optional.of(combo);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Combo> obtenerPorIdProducto(Connection conn, int idProducto) throws SQLException {
        String sql = "SELECT c.*, p.codigo AS codigo_prod, p.id_categoria "
                + "FROM combo c "
                + "LEFT JOIN producto p ON c.id_producto = p.id_producto "
                + "WHERE c.id_producto = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Combo combo = mapearCombo(rs);
                    combo.setDetalles(listarDetallesPorCombo(conn, combo.getIdCombo()));
                    return Optional.of(combo);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ComboDetalle> listarDetallesPorCombo(Connection conn, int idCombo) throws SQLException {
        String sql = "SELECT cd.*, p.codigo AS codigo_prod, p.nombre AS nombre_prod, p.precio_venta "
                + "FROM combo_detalle cd "
                + "JOIN producto p ON cd.id_producto = p.id_producto "
                + "WHERE cd.id_combo = ? "
                + "ORDER BY cd.id_combo_detalle ASC";
        List<ComboDetalle> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCombo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearDetalle(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Map<Integer, Combo> listarMapaCombosActivosPorProducto(Connection conn) throws SQLException {
        List<Combo> activos = listarCombosActivos(conn);
        Map<Integer, Combo> mapa = new LinkedHashMap<>();
        for (Combo c : activos) {
            if (c.getIdProducto() != null && c.getIdProducto() > 0) {
                mapa.put(c.getIdProducto(), c);
            }
        }
        return mapa;
    }

    @Override
    public List<ComboDetalle> obtenerComponentesParaVenta(Connection conn, Integer idCombo, int idProducto) throws SQLException {
        if (idCombo != null && idCombo > 0) {
            List<ComboDetalle> directos = listarDetallesPorCombo(conn, idCombo);
            if (!directos.isEmpty()) {
                return directos;
            }
        }
        if (idProducto > 0) {
            Optional<Combo> porProd = obtenerPorIdProducto(conn, idProducto);
            if (porProd.isPresent()) {
                return porProd.get().getDetalles();
            }
        }
        return List.of();
    }

    @Override
    public int crearCombo(Connection conn, Combo combo) throws SQLException {
        String sql = "INSERT INTO combo (id_producto, nombre, precio_combo, activo) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (combo.getIdProducto() != null && combo.getIdProducto() > 0) {
                ps.setInt(1, combo.getIdProducto());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, combo.getNombre());
            ps.setBigDecimal(3, combo.getPrecioComboNormalizado());
            ps.setBoolean(4, combo.isActivo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    combo.setIdCombo(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID del combo creado.");
    }

    @Override
    public void actualizarCombo(Connection conn, Combo combo) throws SQLException {
        String sql = "UPDATE combo SET id_producto = ?, nombre = ?, precio_combo = ?, activo = ? WHERE id_combo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (combo.getIdProducto() != null && combo.getIdProducto() > 0) {
                ps.setInt(1, combo.getIdProducto());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, combo.getNombre());
            ps.setBigDecimal(3, combo.getPrecioComboNormalizado());
            ps.setBoolean(4, combo.isActivo());
            ps.setInt(5, combo.getIdCombo());
            ps.executeUpdate();
        }
    }

    @Override
    public void reemplazarDetalles(Connection conn, int idCombo, List<ComboDetalle> detalles) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM combo_detalle WHERE id_combo = ?")) {
            del.setInt(1, idCombo);
            del.executeUpdate();
        }
        if (detalles == null || detalles.isEmpty()) {
            return;
        }
        String insSql = "INSERT INTO combo_detalle (id_combo, id_producto, cantidad) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insSql)) {
            for (ComboDetalle d : detalles) {
                if (d == null || d.getIdProducto() <= 0 || d.getCantidad() <= 0) {
                    continue;
                }
                ps.setInt(1, idCombo);
                ps.setInt(2, d.getIdProducto());
                ps.setInt(3, d.getCantidad());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void desactivarCombo(Connection conn, int idCombo) throws SQLException {
        String sql = "UPDATE combo SET activo = 0 WHERE id_combo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCombo);
            ps.executeUpdate();
        }
    }

    private Map<Integer, List<ComboDetalle>> listarTodosDetallesAgrupados(Connection conn) throws SQLException {
        String sql = "SELECT cd.*, p.codigo AS codigo_prod, p.nombre AS nombre_prod, p.precio_venta "
                + "FROM combo_detalle cd "
                + "JOIN producto p ON cd.id_producto = p.id_producto "
                + "ORDER BY cd.id_combo ASC, cd.id_combo_detalle ASC";
        Map<Integer, List<ComboDetalle>> mapa = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ComboDetalle det = mapearDetalle(rs);
                mapa.computeIfAbsent(det.getIdCombo(), k -> new ArrayList<>()).add(det);
            }
        }
        return mapa;
    }

    private Combo mapearCombo(ResultSet rs) throws SQLException {
        Combo c = new Combo();
        c.setIdCombo(rs.getInt("id_combo"));
        int idProd = rs.getInt("id_producto");
        c.setIdProducto(rs.wasNull() ? null : idProd);
        c.setNombre(rs.getString("nombre"));
        c.setPrecioCombo(rs.getBigDecimal("precio_combo"));
        c.setActivo(rs.getBoolean("activo"));
        Timestamp ts = rs.getTimestamp("creado_en");
        if (ts != null) {
            c.setCreadoEn(ts.toLocalDateTime());
        }
        try {
            c.setCodigo(rs.getString("codigo_prod"));
            c.setIdCategoria(rs.getInt("id_categoria"));
        } catch (SQLException ignored) {
            // Columnas opcionales si no hay JOIN
        }
        return c;
    }

    private ComboDetalle mapearDetalle(ResultSet rs) throws SQLException {
        ComboDetalle d = new ComboDetalle();
        d.setIdComboDetalle(rs.getInt("id_combo_detalle"));
        d.setIdCombo(rs.getInt("id_combo"));
        d.setIdProducto(rs.getInt("id_producto"));
        d.setCantidad(rs.getInt("cantidad"));
        d.setCodigoProducto(rs.getString("codigo_prod"));
        d.setNombreProducto(rs.getString("nombre_prod"));
        d.setPrecioUnitarioReferencia(rs.getBigDecimal("precio_venta"));
        return d;
    }
}
