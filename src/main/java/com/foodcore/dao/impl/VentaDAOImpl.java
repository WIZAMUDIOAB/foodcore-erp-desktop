package com.foodcore.dao.impl;

import com.foodcore.dao.VentaDAO;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación canónica de {@link VentaDAO} en paquete {@code com.foodcore.dao.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01) y SPEC-FOODCORE-v4.3.0 (§1.1).
 */
public class VentaDAOImpl implements VentaDAO {

    @Override
    public int insertarVenta(Connection conn, Venta venta) throws SQLException {
        if (venta == null || venta.getTotal() == null || venta.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SQLException("El total de la venta debe ser estrictamente mayor a cero.");
        }

        String idemKey = venta.getIdempotencyKey();
        if (idemKey != null && !idemKey.isBlank()) {
            String checkSql = "SELECT id_venta FROM venta WHERE idempotency_key = ?";
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, idemKey.trim());
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        int idExistente = rs.getInt("id_venta");
                        venta.setIdVenta(idExistente);
                        return idExistente;
                    }
                }
            }
        }

        String sql = "INSERT INTO venta (id_pedido, id_caja, id_usuario, id_cliente, subtotal, igv, descuento, descuento_puntos, total, "
                + "metodo_pago, monto_recibido, vuelto, estado, idempotency_key) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (venta.getIdPedido() != null) {
                ps.setInt(1, venta.getIdPedido());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setInt(2, venta.getIdCaja());
            ps.setInt(3, venta.getIdUsuario());
            if (venta.getIdCliente() != null) {
                ps.setInt(4, venta.getIdCliente());
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setBigDecimal(5, venta.getSubtotal() != null ? venta.getSubtotal() : venta.getTotal());
            ps.setBigDecimal(6, venta.getIgv() != null ? venta.getIgv() : BigDecimal.ZERO);
            ps.setBigDecimal(7, venta.getDescuento() != null ? venta.getDescuento() : BigDecimal.ZERO);
            ps.setBigDecimal(8, venta.getDescuentoPuntos() != null ? venta.getDescuentoPuntos() : BigDecimal.ZERO);
            ps.setBigDecimal(9, venta.getTotal());
            ps.setString(10, venta.getMetodoPago() != null ? venta.getMetodoPago() : "EFECTIVO");
            ps.setBigDecimal(11, venta.getMontoRecibido() != null ? venta.getMontoRecibido() : venta.getTotal());
            ps.setBigDecimal(12, venta.getVuelto() != null ? venta.getVuelto() : BigDecimal.ZERO);
            ps.setString(13, venta.getEstado() != null ? venta.getEstado() : "COMPLETADA");
            ps.setString(14, idemKey != null ? idemKey.trim() : null);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    venta.setIdVenta(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID generado para la venta.");
    }

    @Override
    public void insertarDetalleVenta(Connection conn, DetalleVenta detalle) throws SQLException {
        if (detalle == null || detalle.getCantidad() <= 0 || detalle.getIdProducto() <= 0
                || detalle.getPrecioUnitario() == null || detalle.getSubtotal() == null) {
            throw new SQLException("Detalle de venta inválido: cantidad, producto y montos deben ser válidos.");
        }
        String sql = "INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal, "
                + "id_variante, nombre_variante, modificadores_texto, id_combo, combo_componentes_texto) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, detalle.getIdVenta());
            ps.setInt(2, detalle.getIdProducto());
            ps.setInt(3, detalle.getCantidad());
            ps.setBigDecimal(4, detalle.getPrecioUnitario());
            ps.setBigDecimal(5, detalle.getSubtotal());
            if (detalle.getIdVariante() != null && detalle.getIdVariante() > 0) {
                ps.setInt(6, detalle.getIdVariante());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            if (detalle.getNombreVariante() != null && !detalle.getNombreVariante().isBlank()) {
                ps.setString(7, detalle.getNombreVariante().trim());
            } else {
                ps.setNull(7, java.sql.Types.VARCHAR);
            }
            String mods = com.foodcore.util.FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                    detalle.getModificadoresSeleccionados(), detalle.getModificadoresTexto());
            if (!mods.isEmpty()) {
                ps.setString(8, mods);
            } else {
                ps.setNull(8, java.sql.Types.VARCHAR);
            }
            if (detalle.getIdCombo() != null && detalle.getIdCombo() > 0) {
                ps.setInt(9, detalle.getIdCombo());
            } else {
                ps.setNull(9, java.sql.Types.INTEGER);
            }
            if (detalle.getComboComponentesTexto() != null && !detalle.getComboComponentesTexto().isBlank()) {
                ps.setString(10, detalle.getComboComponentesTexto().trim());
            } else {
                ps.setNull(10, java.sql.Types.VARCHAR);
            }
            ps.executeUpdate();
        }
    }

    @Override
    public void insertarComprobante(Connection conn, ComprobantePago comprobante) throws SQLException {
        String sql = "INSERT INTO comprobante_pago (id_venta, tipo_comprobante, serie, numero, total) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, comprobante.getIdVenta());
            ps.setString(2, comprobante.getTipoComprobante());
            ps.setString(3, comprobante.getSerie());
            ps.setString(4, comprobante.getNumero());
            ps.setBigDecimal(5, comprobante.getTotal());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    comprobante.setIdComprobante(rs.getInt(1));
                }
            }
        }
    }

    @Override
    public List<Venta> listarVentasConFiltro(Connection conn, LocalDate desde, LocalDate hasta, String textoBusqueda)
            throws SQLException {
        List<Venta> lista = new ArrayList<>();
        String filtro = textoBusqueda != null && !textoBusqueda.isBlank()
                ? "%" + textoBusqueda.trim().toLowerCase() + "%" : null;
        String sql = "SELECT * FROM venta "
                + "WHERE (? IS NULL OR DATE(fecha_hora) >= ?) "
                + "  AND (? IS NULL OR DATE(fecha_hora) <= ?) "
                + "  AND (? IS NULL OR CAST(id_venta AS CHAR) LIKE ? OR LOWER(metodo_pago) LIKE ? OR LOWER(estado) LIKE ?) "
                + "ORDER BY fecha_hora DESC, id_venta DESC LIMIT 200";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (desde != null) {
                Date d = Date.valueOf(desde);
                ps.setDate(1, d);
                ps.setDate(2, d);
            } else {
                ps.setNull(1, Types.DATE);
                ps.setNull(2, Types.DATE);
            }
            if (hasta != null) {
                Date h = Date.valueOf(hasta);
                ps.setDate(3, h);
                ps.setDate(4, h);
            } else {
                ps.setNull(3, Types.DATE);
                ps.setNull(4, Types.DATE);
            }
            if (filtro != null) {
                ps.setString(5, filtro);
                ps.setString(6, filtro);
                ps.setString(7, filtro);
                ps.setString(8, filtro);
            } else {
                ps.setNull(5, Types.VARCHAR);
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.VARCHAR);
                ps.setNull(8, Types.VARCHAR);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearVentaCompleta(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Optional<Venta> buscarVentaPorId(Connection conn, int idVenta) throws SQLException {
        String sql = "SELECT * FROM venta WHERE id_venta = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearVentaCompleta(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DetalleVenta> listarDetallesPorVenta(Connection conn, int idVenta) throws SQLException {
        List<DetalleVenta> lista = new ArrayList<>();
        String sql = "SELECT dv.*, COALESCE(p.nombre, CONCAT('Producto #', dv.id_producto)) AS nombre_producto "
                + "FROM detalle_venta dv "
                + "LEFT JOIN producto p ON dv.id_producto = p.id_producto "
                + "WHERE dv.id_venta = ? ORDER BY dv.id_detalle_venta ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DetalleVenta dv = new DetalleVenta();
                    dv.setIdDetalleVenta(rs.getInt("id_detalle_venta"));
                    dv.setIdVenta(rs.getInt("id_venta"));
                    dv.setIdProducto(rs.getInt("id_producto"));
                    dv.setNombreProducto(rs.getString("nombre_producto"));
                    dv.setCantidad(rs.getInt("cantidad"));
                    dv.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
                    dv.setSubtotal(rs.getBigDecimal("subtotal"));
                    int idVar = rs.getInt("id_variante");
                    dv.setIdVariante(rs.wasNull() ? null : idVar);
                    dv.setNombreVariante(rs.getString("nombre_variante"));
                    dv.setModificadoresTexto(rs.getString("modificadores_texto"));
                    int idCombo = rs.getInt("id_combo");
                    dv.setIdCombo(rs.wasNull() ? null : idCombo);
                    dv.setComboComponentesTexto(rs.getString("combo_componentes_texto"));
                    try {
                        dv.setEstado(rs.getString("estado"));
                        Timestamp fd = rs.getTimestamp("fecha_devolucion");
                        if (fd != null) {
                            dv.setFechaDevolucion(fd.toLocalDateTime());
                        }
                        dv.setMotivoDevolucion(rs.getString("motivo_devolucion"));
                    } catch (SQLException ignored) {
                        dv.setEstado("COMPLETADO");
                    }
                    lista.add(dv);
                }
            }
        }
        return lista;
    }

    @Override
    public int marcarDetalleDevuelto(Connection conn, int idDetalleVenta, String motivo) throws SQLException {
        String sql = "UPDATE detalle_venta SET estado = 'DEVUELTO', fecha_devolucion = NOW(), "
                + "motivo_devolucion = ? WHERE id_detalle_venta = ? AND estado <> 'DEVUELTO'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, motivo);
            ps.setInt(2, idDetalleVenta);
            return ps.executeUpdate();
        }
    }

    @Override
    public void actualizarTotalesVentaPorDevolucion(Connection conn, int idVenta, BigDecimal nuevoSubtotal,
                                                    BigDecimal nuevoIgv, BigDecimal nuevoTotal,
                                                    String nuevoEstado) throws SQLException {
        String sql = "UPDATE venta SET subtotal = ?, igv = ?, total = ?, estado = ? WHERE id_venta = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, nuevoSubtotal);
            ps.setBigDecimal(2, nuevoIgv);
            ps.setBigDecimal(3, nuevoTotal);
            ps.setString(4, nuevoEstado);
            ps.setInt(5, idVenta);
            ps.executeUpdate();
        }
    }

    private Venta mapearVentaCompleta(ResultSet rs) throws SQLException {
        Venta v = new Venta();
        v.setIdVenta(rs.getInt("id_venta"));
        int idPed = rs.getInt("id_pedido");
        v.setIdPedido(rs.wasNull() ? null : idPed);
        v.setIdCaja(rs.getInt("id_caja"));
        v.setIdUsuario(rs.getInt("id_usuario"));
        int idCli = rs.getInt("id_cliente");
        v.setIdCliente(rs.wasNull() ? null : idCli);
        Timestamp fh = rs.getTimestamp("fecha_hora");
        if (fh != null) {
            v.setFechaHora(fh.toLocalDateTime());
        }
        v.setSubtotal(rs.getBigDecimal("subtotal"));
        v.setIgv(rs.getBigDecimal("igv"));
        try {
            v.setDescuento(rs.getBigDecimal("descuento"));
            v.setDescuentoPuntos(rs.getBigDecimal("descuento_puntos"));
        } catch (SQLException ignored) {
            v.setDescuento(BigDecimal.ZERO);
        }
        v.setTotal(rs.getBigDecimal("total"));
        v.setMetodoPago(rs.getString("metodo_pago"));
        v.setMontoRecibido(rs.getBigDecimal("monto_recibido"));
        v.setVuelto(rs.getBigDecimal("vuelto"));
        v.setEstado(rs.getString("estado"));
        return v;
    }

    @Override
    public List<Venta> listarVentasDelDia(Connection conn) throws SQLException {
        List<Venta> lista = new ArrayList<>();
        String sql = "SELECT * FROM venta WHERE DATE(fecha_hora) = CURRENT_DATE() ORDER BY fecha_hora DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Venta v = new Venta();
                v.setIdVenta(rs.getInt("id_venta"));
                int idPed = rs.getInt("id_pedido");
                v.setIdPedido(rs.wasNull() ? null : idPed);
                v.setIdCaja(rs.getInt("id_caja"));
                v.setIdUsuario(rs.getInt("id_usuario"));
                int idCli = rs.getInt("id_cliente");
                v.setIdCliente(rs.wasNull() ? null : idCli);
                Timestamp ts = rs.getTimestamp("fecha_hora");
                if (ts != null) {
                    v.setFechaHora(ts.toLocalDateTime());
                }
                v.setSubtotal(rs.getBigDecimal("subtotal"));
                v.setIgv(rs.getBigDecimal("igv"));
                v.setDescuento(rs.getBigDecimal("descuento"));
                try {
                    BigDecimal dp = rs.getBigDecimal("descuento_puntos");
                    v.setDescuentoPuntos(dp != null ? dp : BigDecimal.ZERO);
                } catch (SQLException ignored) {
                    v.setDescuentoPuntos(BigDecimal.ZERO);
                }
                v.setTotal(rs.getBigDecimal("total"));
                v.setMetodoPago(rs.getString("metodo_pago"));
                v.setMontoRecibido(rs.getBigDecimal("monto_recibido"));
                v.setVuelto(rs.getBigDecimal("vuelto"));
                v.setEstado(rs.getString("estado"));
                v.setIdempotencyKey(rs.getString("idempotency_key"));
                lista.add(v);
            }
        }
        return lista;
    }
}
