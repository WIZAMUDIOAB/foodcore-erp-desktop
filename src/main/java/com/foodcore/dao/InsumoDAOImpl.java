package com.foodcore.dao;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.model.Receta;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DAO JDBC para gestión de insumos, bloqueo pesimista {@code FOR UPDATE} (R-09),
 * descuento atómico sin envolver excepciones de dominio (R-11) y registro inmutable de Kárdex (R-06).
 */
public class InsumoDAOImpl implements InsumoDAO {

    @Override
    public List<Insumo> listarTodos(Connection conn) throws SQLException {
        List<Insumo> lista = new ArrayList<>();
        String sql = "SELECT * FROM insumo ORDER BY nombre ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearInsumo(rs));
            }
        }
        return lista;
    }

    @Override
    public Insumo obtenerPorId(Connection conn, long idInsumo) throws SQLException {
        String sql = "SELECT * FROM insumo WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idInsumo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapearInsumo(rs);
                }
            }
        }
        return null;
    }

    private Insumo mapearInsumo(ResultSet rs) throws SQLException {
        Insumo i = new Insumo();
        i.setIdInsumo(rs.getLong("id_insumo"));
        try {
            i.setCodigo(rs.getString("codigo"));
        } catch (SQLException ignored) {
            i.setCodigo("INS-" + i.getIdInsumo());
        }
        i.setNombre(rs.getString("nombre"));
        i.setUnidadMedida(rs.getString("unidad_medida"));
        i.setStockActualDisponible(rs.getBigDecimal("stock_actual_disponible"));
        i.setStockMinimo(rs.getBigDecimal("stock_minimo"));
        i.setCostoUnitario(rs.getBigDecimal("costo_unitario"));
        return i;
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
                    r.setIdInsumo(rs.getLong("id_insumo"));
                    r.setCantidadNecesaria(rs.getBigDecimal("cantidad_necesaria"));
                    r.setNombreInsumo(rs.getString("nombre_insumo"));
                    r.setUnidadMedidaInsumo(rs.getString("unidad_medida"));
                    lista.add(r);
                }
            }
        }
        return lista;
    }

    /**
     * Adquiere bloqueo pesimista {@code SELECT ... FOR UPDATE} en orden ascendente de {@code id_insumo}
     * para prevenir race conditions y deadlocks (SPEC-FOODCORE-v1.0 §9.1; REMEDIATION R-09).
     */
    @Override
    public Map<Long, BigDecimal> lockearPorIds(Connection conn, List<Long> idsInsumos) throws SQLException {
        Map<Long, BigDecimal> saldosBloqueados = new LinkedHashMap<>();
        if (idsInsumos == null || idsInsumos.isEmpty()) {
            return saldosBloqueados;
        }
        List<Long> ordenados = idsInsumos.stream().distinct().sorted().collect(Collectors.toList());
        String placeholders = ordenados.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id_insumo, stock_actual_disponible FROM insumo " +
                     "WHERE id_insumo IN (" + placeholders + ") ORDER BY id_insumo ASC FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int idx = 0; idx < ordenados.size(); idx++) {
                ps.setLong(idx + 1, ordenados.get(idx));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    saldosBloqueados.put(
                        rs.getLong("id_insumo"),
                        rs.getBigDecimal("stock_actual_disponible").setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO)
                    );
                }
            }
        }
        return saldosBloqueados;
    }

    @Override
    public long crear(Connection conn, Insumo i) throws SQLException {
        String sql = "INSERT INTO insumo (codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, i.getCodigo() != null ? i.getCodigo() : ("INS-" + System.nanoTime()));
            ps.setString(2, i.getNombre());
            ps.setString(3, i.getUnidadMedida());
            ps.setBigDecimal(4, i.getStockActualDisponible().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(5, i.getStockMinimo().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(6, i.getCostoUnitario());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    i.setIdInsumo(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID del insumo registrado.");
    }

    @Override
    public void actualizar(Connection conn, Insumo i) throws SQLException {
        String sql = "UPDATE insumo SET codigo = ?, nombre = ?, unidad_medida = ?, stock_actual_disponible = ?, stock_minimo = ?, costo_unitario = ? " +
                     "WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, i.getCodigo());
            ps.setString(2, i.getNombre());
            ps.setString(3, i.getUnidadMedida());
            ps.setBigDecimal(4, i.getStockActualDisponible().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(5, i.getStockMinimo().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(6, i.getCostoUnitario());
            ps.setLong(7, i.getIdInsumo());
            ps.executeUpdate();
        }
    }

    @Override
    public void actualizarStock(Connection conn, long idInsumo, BigDecimal nuevoStock) throws SQLException {
        String sql = "UPDATE insumo SET stock_actual_disponible = ? WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, nuevoStock.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setLong(2, idInsumo);
            ps.executeUpdate();
        }
    }

    /**
     * Descuenta stock atómicamente aplicando validación en base de datos ({@code stock_actual_disponible >= ?})
     * y lanzando directamente {@link StockInsuficienteException} o {@link InsumoNoEncontradoException}
     * sin envolverlas en {@link SQLException} (SPEC-FOODCORE-v1.0 §9.1; REMEDIATION R-06, R-11).
     */
    @Override
    public void descontarStock(Connection conn, long idInsumo, BigDecimal cantidadADescontar) {
        BigDecimal cantNorm = cantidadADescontar.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
        String sql = "UPDATE insumo SET stock_actual_disponible = stock_actual_disponible - ? " +
                     "WHERE id_insumo = ? AND stock_actual_disponible >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, cantNorm);
            ps.setLong(2, idInsumo);
            ps.setBigDecimal(3, cantNorm);
            int filasAfectadas = ps.executeUpdate();
            if (filasAfectadas == 0) {
                if (!existeInsumo(conn, idInsumo)) {
                    throw new InsumoNoEncontradoException(idInsumo);
                }
                throw new StockInsuficienteException(idInsumo, cantNorm);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(CodigoError.ERROR_PERSISTENCIA.getCodigo() + ": Fallo al descontar stock", e);
        }
    }

    private boolean existeInsumo(Connection conn, long idInsumo) throws SQLException {
        String sql = "SELECT 1 FROM insumo WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idInsumo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void registrarMovimientoInventario(Connection conn, long idInsumo, String tipo, BigDecimal cantidad, String motivo, Integer referenciaId) throws SQLException {
        BigDecimal cantNorm = cantidad.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
        String sql = "INSERT INTO movimiento_inventario (id_insumo, tipo, cantidad, motivo, referencia_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idInsumo);
            ps.setString(2, tipo);
            ps.setBigDecimal(3, cantNorm);
            ps.setString(4, motivo);
            if (referenciaId != null) {
                ps.setInt(5, referenciaId);
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public KardexMovimiento registrarMovimientoKardexAtomico(Connection conn,
                                                             long idInsumo,
                                                             String tipoMovimiento,
                                                             BigDecimal magnitudPositiva,
                                                             long idUsuario,
                                                             String observacion) throws SQLException {
        BigDecimal mag = magnitudPositiva.abs().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
        Map<Long, BigDecimal> locked = lockearPorIds(conn, List.of(idInsumo));
        if (!locked.containsKey(idInsumo)) {
            throw new InsumoNoEncontradoException(idInsumo);
        }
        BigDecimal saldoAnterior = locked.get(idInsumo);
        boolean esIncremento = "ENTRADA".equalsIgnoreCase(tipoMovimiento)
            || "AJUSTE_POS".equalsIgnoreCase(tipoMovimiento);
        BigDecimal cantidadFirmada = esIncremento ? mag : mag.negate();
        BigDecimal saldoPosterior = saldoAnterior.add(cantidadFirmada)
            .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);

        if (esIncremento) {
            actualizarStock(conn, idInsumo, saldoPosterior);
        } else {
            descontarStock(conn, idInsumo, mag);
        }

        Insumo insumo = obtenerPorId(conn, idInsumo);
        BigDecimal costoUnitario = (insumo != null && insumo.getCostoUnitario() != null)
            ? insumo.getCostoUnitario()
            : BigDecimal.ZERO;

        KardexMovimiento km = new KardexMovimiento(
            0L,
            idInsumo,
            tipoMovimiento.toUpperCase(),
            cantidadFirmada,
            saldoAnterior,
            saldoPosterior,
            costoUnitario,
            tipoMovimiento.toUpperCase(),
            null,
            idUsuario,
            observacion,
            LocalDateTime.now()
        );
        insertarKardex(conn, km);
        return km;
    }

    @Override
    public long insertarKardex(Connection conn, KardexMovimiento mov) throws SQLException {
        String sql = "INSERT INTO kardex_movimiento (id_insumo, tipo_movimiento, cantidad, saldo_anterior, saldo_posterior, " +
                     "costo_unitario, referencia_tipo, referencia_id, id_usuario, observacion, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, mov.getIdInsumo());
            ps.setString(2, mov.getTipoMovimiento());
            ps.setBigDecimal(3, mov.getCantidad().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(4, mov.getSaldoAnterior().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(5, mov.getSaldoPosterior().setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setBigDecimal(6, (mov.getCostoUnitario() != null ? mov.getCostoUnitario() : BigDecimal.ZERO)
                .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO));
            ps.setString(7, mov.getReferenciaTipo());
            if (mov.getReferenciaId() != null) {
                ps.setLong(8, mov.getReferenciaId());
            } else {
                ps.setNull(8, Types.BIGINT);
            }
            ps.setLong(9, mov.getIdUsuario() > 0 ? mov.getIdUsuario() : 1L);
            ps.setString(10, mov.getObservacion());
            ps.setTimestamp(11, Timestamp.valueOf(mov.getCreatedAt() != null ? mov.getCreatedAt() : LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    mov.setIdKardex(id);
                    return id;
                }
            }
        }
        return 0L;
    }

    @Override
    public List<KardexMovimiento> listarKardexPorInsumo(Connection conn, long idInsumo) throws SQLException {
        List<KardexMovimiento> lista = new ArrayList<>();
        String sql = "SELECT * FROM kardex_movimiento WHERE id_insumo = ? ORDER BY id_kardex ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idInsumo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    KardexMovimiento km = new KardexMovimiento();
                    km.setIdKardex(rs.getLong("id_kardex"));
                    km.setIdInsumo(rs.getLong("id_insumo"));
                    km.setTipoMovimiento(rs.getString("tipo_movimiento"));
                    km.setCantidad(rs.getBigDecimal("cantidad"));
                    km.setSaldoAnterior(rs.getBigDecimal("saldo_anterior"));
                    km.setSaldoPosterior(rs.getBigDecimal("saldo_posterior"));
                    km.setCostoUnitario(rs.getBigDecimal("costo_unitario"));
                    km.setReferenciaTipo(rs.getString("referencia_tipo"));
                    long refId = rs.getLong("referencia_id");
                    km.setReferenciaId(rs.wasNull() ? null : refId);
                    km.setIdUsuario(rs.getLong("id_usuario"));
                    km.setObservacion(rs.getString("observacion"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) km.setCreatedAt(ts.toLocalDateTime());
                    lista.add(km);
                }
            }
        }
        return lista;
    }

    @Override
    public void eliminar(Connection conn, long idInsumo) throws SQLException {
        String sql = "DELETE FROM insumo WHERE id_insumo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idInsumo);
            ps.executeUpdate();
        }
    }
}
