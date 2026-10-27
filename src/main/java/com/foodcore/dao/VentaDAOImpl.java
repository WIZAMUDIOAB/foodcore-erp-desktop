package com.foodcore.dao;

import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VentaDAOImpl implements VentaDAO {

    @Override
    public int insertarVenta(Connection conn, Venta venta) throws SQLException {
        String sql = "INSERT INTO venta (id_pedido, id_caja, id_usuario, id_cliente, subtotal, igv, total, metodo_pago, monto_recibido, vuelto, estado) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setBigDecimal(5, venta.getSubtotal());
            ps.setBigDecimal(6, venta.getIgv());
            ps.setBigDecimal(7, venta.getTotal());
            ps.setString(8, venta.getMetodoPago());
            ps.setBigDecimal(9, venta.getMontoRecibido());
            ps.setBigDecimal(10, venta.getVuelto());
            ps.setString(11, venta.getEstado());

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
                v.setFechaHora(rs.getTimestamp("fecha_hora").toLocalDateTime());
                v.setSubtotal(rs.getBigDecimal("subtotal"));
                v.setIgv(rs.getBigDecimal("igv"));
                v.setTotal(rs.getBigDecimal("total"));
                v.setMetodoPago(rs.getString("metodo_pago"));
                v.setMontoRecibido(rs.getBigDecimal("monto_recibido"));
                v.setVuelto(rs.getBigDecimal("vuelto"));
                v.setEstado(rs.getString("estado"));
                lista.add(v);
            }
        }
        return lista;
    }
}
