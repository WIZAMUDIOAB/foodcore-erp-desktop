package com.foodcore.dao;

import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.Insumo;
import com.foodcore.model.Receta;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InsumoDAOImpl implements InsumoDAO {

    @Override
    public List<Insumo> listarTodos(Connection conn) throws SQLException {
        List<Insumo> lista = new ArrayList<>();
        String sql = "SELECT * FROM insumo ORDER BY nombre ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Insumo i = new Insumo();
                i.setIdInsumo(rs.getInt("id_insumo"));
                i.setCodigo(rs.getString("codigo"));
                i.setNombre(rs.getString("nombre"));
                i.setUnidadMedida(rs.getString("unidad_medida"));
                i.setStockActual(rs.getBigDecimal("stock_actual"));
                i.setStockMinimo(rs.getBigDecimal("stock_minimo"));
                i.setCostoUnitario(rs.getBigDecimal("costo_unitario"));
                lista.add(i);
            }
        }
        return lista;
    }

    @Override
    public List<Receta> obtenerRecetaPorProducto(Connection conn, int idProducto) throws SQLException {
        List<Receta> lista = new ArrayList<>();
        String sql = "SELECT r.*, i.nombre AS nombre_insumo, i.unidad_medida " +
                     "FROM receta r " +
                     "JOIN insumo i ON r.id_insumo = i.id_insumo " +
                     "WHERE r.id_producto = ? " +
                     "ORDER BY r.id_insumo ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Receta r = new Receta();
                    r.setIdReceta(rs.getInt("id_receta"));
                    r.setIdProducto(rs.getInt("id_producto"));
                    r.setIdInsumo(rs.getInt("id_insumo"));
                    r.setCantidadNecesaria(rs.getBigDecimal("cantidad_necesaria"));
                    r.setNombreInsumo(rs.getString("nombre_insumo"));
                    r.setUnidadMedidaInsumo(rs.getString("unidad_medida"));
                    lista.add(r);
                }
            }
        }
        return lista;
    }

    @Override
    public int crear(Connection conn, Insumo i) throws SQLException {
        String sql = "INSERT INTO insumo (codigo, nombre, unidad_medida, stock_actual, stock_minimo, costo_unitario) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, i.getCodigo());
            ps.setString(2, i.getNombre());
            ps.setString(3, i.getUnidadMedida());
            ps.setBigDecimal(4, i.getStockActual().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(5, i.getStockMinimo().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(6, i.getCostoUnitario());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    i.setIdInsumo(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID del insumo registrado.");
    }

    @Override
    public void actualizar(Connection conn, Insumo i) throws SQLException {
        String sql = "UPDATE insumo SET codigo = ?, nombre = ?, unidad_medida = ?, stock_actual = ?, stock_minimo = ?, costo_unitario = ? " +
                     "WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, i.getCodigo());
            ps.setString(2, i.getNombre());
            ps.setString(3, i.getUnidadMedida());
            ps.setBigDecimal(4, i.getStockActual().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(5, i.getStockMinimo().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(6, i.getCostoUnitario());
            ps.setInt(7, i.getIdInsumo());
            ps.executeUpdate();
        }
    }

    @Override
    public void actualizarStock(Connection conn, int idInsumo, BigDecimal nuevoStock) throws SQLException {
        String sql = "UPDATE insumo SET stock_actual = ? WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, nuevoStock.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setInt(2, idInsumo);
            ps.executeUpdate();
        }
    }

    /**
     * Descuenta stock atómicamente aplicando validación en base de datos ({@code stock_actual >= ?})
     * para prevenir sobregiros bajo concurrencia (SPEC-FOODCORE-v1.0 §9.1).
     */
    @Override
    public void descontarStock(Connection conn, int idInsumo, BigDecimal cantidadADescontar) throws SQLException {
        BigDecimal cantNorm = cantidadADescontar.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
        String sql = "UPDATE insumo SET stock_actual = stock_actual - ? WHERE id_insumo = ? AND stock_actual >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, cantNorm);
            ps.setInt(2, idInsumo);
            ps.setBigDecimal(3, cantNorm);
            int filasAfectadas = ps.executeUpdate();
            if (filasAfectadas == 0) {
                if (!existeInsumo(conn, idInsumo)) {
                    throw new SQLException(new InsumoNoEncontradoException(idInsumo));
                }
                throw new SQLException(new StockInsuficienteException(idInsumo, cantNorm));
            }
        }
    }

    private boolean existeInsumo(Connection conn, int idInsumo) throws SQLException {
        String sql = "SELECT 1 FROM insumo WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idInsumo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void registrarMovimientoInventario(Connection conn, int idInsumo, String tipo, BigDecimal cantidad, String motivo, Integer referenciaId) throws SQLException {
        BigDecimal cantNorm = cantidad.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
        String sql = "INSERT INTO movimiento_inventario (id_insumo, tipo, cantidad, motivo, referencia_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idInsumo);
            ps.setString(2, tipo);
            ps.setBigDecimal(3, cantNorm);
            ps.setString(4, motivo);
            if (referenciaId != null) {
                ps.setInt(5, referenciaId);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void eliminar(Connection conn, int idInsumo) throws SQLException {
        String sql = "DELETE FROM insumo WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idInsumo);
            ps.executeUpdate();
        }
    }
}
