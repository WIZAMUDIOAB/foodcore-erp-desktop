package com.foodcore.dao.impl;

import com.foodcore.dao.VentaDAO;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

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

        String sql = "INSERT INTO venta (id_pedido, id_caja, id_usuario, id_cliente, subtotal, igv, descuento, total, "
                + "metodo_pago, monto_recibido, vuelto, estado, idempotency_key) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setBigDecimal(8, venta.getTotal());
            ps.setString(9, venta.getMetodoPago() != null ? venta.getMetodoPago() : "EFECTIVO");
            ps.setBigDecimal(10, venta.getMontoRecibido() != null ? venta.getMontoRecibido() : venta.getTotal());
            ps.setBigDecimal(11, venta.getVuelto() != null ? venta.getVuelto() : BigDecimal.ZERO);
            ps.setString(12, venta.getEstado() != null ? venta.getEstado() : "COMPLETADA");
            ps.setString(13, idemKey != null ? idemKey.trim() : null);

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
        String sql = "INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, detalle.getIdVenta());
            ps.setInt(2, detalle.getIdProducto());
            ps.setInt(3, detalle.getCantidad());
            ps.setBigDecimal(4, detalle.getPrecioUnitario());
            ps.setBigDecimal(5, detalle.getSubtotal());
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
