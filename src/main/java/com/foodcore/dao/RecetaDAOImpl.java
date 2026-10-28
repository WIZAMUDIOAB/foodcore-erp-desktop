package com.foodcore.dao;

import com.foodcore.model.Receta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RecetaDAOImpl implements RecetaDAO {

    @Override
    public List<Receta> listarPorProducto(Connection conn, int idProducto) throws SQLException {
        List<Receta> lista = new ArrayList<>();
        String sql = "SELECT r.*, p.nombre AS nombre_prod, i.nombre AS nombre_ins, i.unidad_medida " +
                     "FROM receta r " +
                     "JOIN producto p ON r.id_producto = p.id_producto " +
                     "JOIN insumo i ON r.id_insumo = i.id_insumo " +
                     "WHERE r.id_producto = ? " +
                     "ORDER BY i.nombre ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Receta r = new Receta();
                    r.setIdReceta(rs.getInt("id_receta"));
                    r.setIdProducto(rs.getInt("id_producto"));
                    r.setIdInsumo(rs.getInt("id_insumo"));
                    r.setCantidadNecesaria(rs.getBigDecimal("cantidad_necesaria"));
                    r.setNombreProducto(rs.getString("nombre_prod"));
                    r.setNombreInsumo(rs.getString("nombre_ins"));
                    r.setUnidadMedidaInsumo(rs.getString("unidad_medida"));
                    lista.add(r);
                }
            }
        }
        return lista;
    }

    @Override
    public void asociarInsumo(Connection conn, int idProducto, int idInsumo, BigDecimal cantidad) throws SQLException {
        String sql = "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE cantidad_necesaria = VALUES(cantidad_necesaria)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idInsumo);
            ps.setBigDecimal(3, cantidad);
            ps.executeUpdate();
        }
    }

    @Override
    public void eliminarComponente(Connection conn, int idReceta) throws SQLException {
        String sql = "DELETE FROM receta WHERE id_receta = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idReceta);
            ps.executeUpdate();
        }
    }
}
