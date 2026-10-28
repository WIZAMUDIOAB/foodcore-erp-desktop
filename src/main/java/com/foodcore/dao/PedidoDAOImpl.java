package com.foodcore.dao;

import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PedidoDAOImpl implements PedidoDAO {

    @Override
    public int crearPedido(Connection conn, Pedido pedido) throws SQLException {
        String sql = "INSERT INTO pedido (id_usuario, id_cliente, numero_mesa, total, estado) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pedido.getIdUsuario());
            if (pedido.getIdCliente() != null) {
                ps.setInt(2, pedido.getIdCliente());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, pedido.getNumeroMesa());
            ps.setBigDecimal(4, pedido.getTotal());
            ps.setString(5, pedido.getEstado());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    pedido.setIdPedido(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID del pedido registrado.");
    }

    @Override
    public void insertarDetallePedido(Connection conn, DetallePedido detalle) throws SQLException {
        String sql = "INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario, subtotal, notas_preparacion) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, detalle.getIdPedido());
            ps.setInt(2, detalle.getIdProducto());
            ps.setInt(3, detalle.getCantidad());
            ps.setBigDecimal(4, detalle.getPrecioUnitario());
            ps.setBigDecimal(5, detalle.getSubtotal());
            ps.setString(6, detalle.getNotasPreparacion() != null ? detalle.getNotasPreparacion() : "");
            ps.executeUpdate();
        }
    }

    @Override
    public int crearComanda(Connection conn, Comanda comanda) throws SQLException {
        String sql = "INSERT INTO comanda (id_pedido, numero_comanda, estado, tiempo_estimado_min) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, comanda.getIdPedido());
            ps.setString(2, comanda.getNumeroComanda());
            ps.setString(3, comanda.getEstado());
            ps.setInt(4, comanda.getTiempoEstimadoMin());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    comanda.setIdComanda(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo registrar la comanda para cocina.");
    }

    @Override
    public List<Pedido> listarPedidosActivos(Connection conn) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String sql = "SELECT * FROM pedido WHERE estado IN ('REGISTRADO', 'EN_PREPARACION', 'LISTO') ORDER BY id_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Pedido p = new Pedido();
                p.setIdPedido(rs.getInt("id_pedido"));
                p.setIdUsuario(rs.getInt("id_usuario"));
                int c = rs.getInt("id_cliente");
                p.setIdCliente(rs.wasNull() ? null : c);
                p.setNumeroMesa(rs.getString("numero_mesa"));
                p.setFechaHora(rs.getTimestamp("fecha_hora").toLocalDateTime());
                p.setEstado(rs.getString("estado"));
                p.setTotal(rs.getBigDecimal("total"));
                lista.add(p);
            }
        }
        return lista;
    }

    @Override
    public List<DetallePedido> listarDetallesPorPedido(Connection conn, int idPedido) throws SQLException {
        List<DetallePedido> lista = new ArrayList<>();
        String sql = "SELECT dp.*, p.nombre AS nombre_prod FROM detalle_pedido dp " +
                     "JOIN producto p ON dp.id_producto = p.id_producto " +
                     "WHERE dp.id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DetallePedido dp = new DetallePedido();
                    dp.setIdDetallePedido(rs.getInt("id_detalle_pedido"));
                    dp.setIdPedido(rs.getInt("id_pedido"));
                    dp.setIdProducto(rs.getInt("id_producto"));
                    dp.setCantidad(rs.getInt("cantidad"));
                    dp.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
                    dp.setSubtotal(rs.getBigDecimal("subtotal"));
                    dp.setNotasPreparacion(rs.getString("notas_preparacion"));
                    dp.setNombreProducto(rs.getString("nombre_prod"));
                    lista.add(dp);
                }
            }
        }
        return lista;
    }

    @Override
    public void actualizarEstadoPedido(Connection conn, int idPedido, String nuevoEstado) throws SQLException {
        String sql = "UPDATE pedido SET estado = ? WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idPedido);
            ps.executeUpdate();
        }

        String estadoComanda = "PENDIENTE";
        if ("EN_PREPARACION".equalsIgnoreCase(nuevoEstado)) {
            estadoComanda = "EN_PREPARACION";
        } else if ("LISTO".equalsIgnoreCase(nuevoEstado)) {
            estadoComanda = "LISTA";
        } else if ("COBRADO".equalsIgnoreCase(nuevoEstado) || "ENTREGADO".equalsIgnoreCase(nuevoEstado)) {
            estadoComanda = "DESPACHADA";
        }
        String sqlComanda = "UPDATE comanda SET estado = ? WHERE id_pedido = ?";
        try (PreparedStatement psCom = conn.prepareStatement(sqlComanda)) {
            psCom.setString(1, estadoComanda);
            psCom.setInt(2, idPedido);
            psCom.executeUpdate();
        }
    }

    @Override
    public void actualizarEstadoComanda(Connection conn, int idComanda, String nuevoEstado) throws SQLException {
        String sql = "UPDATE comanda SET estado = ? WHERE id_comanda = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idComanda);
            ps.executeUpdate();
        }
    }
}
