package com.foodcore.dao;

import java.math.BigDecimal;

import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PedidoDAOImpl implements PedidoDAO {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @Override
    public int crearPedido(Connection conn, Pedido pedido) throws SQLException {
        String sql = "INSERT INTO pedido (id_usuario, id_cliente, numero_mesa, total, estado, idempotency_key, tipo_pedido, cliente_nombre, cliente_telefono, cliente_direccion, cliente_referencia, costo_envio, repartidor, id_usuario_creador) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setString(6, pedido.getIdempotencyKey());
            ps.setString(7, pedido.getTipoPedido() != null ? pedido.getTipoPedido() : "MESA");
            ps.setString(8, pedido.getClienteNombre());
            ps.setString(9, pedido.getClienteTelefono());
            ps.setString(10, pedido.getClienteDireccion());
            ps.setString(11, pedido.getClienteReferencia());
            ps.setBigDecimal(12, pedido.getCostoEnvio() != null ? pedido.getCostoEnvio() : BigDecimal.ZERO);
            ps.setString(13, pedido.getRepartidor());
            if (pedido.getIdUsuarioCreador() != null) {
                ps.setInt(14, pedido.getIdUsuarioCreador());
            } else {
                ps.setInt(14, pedido.getIdUsuario());
            }

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
    public Optional<Pedido> buscarPorIdempotencyKey(Connection conn, String idempotencyKey) throws SQLException {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM pedido WHERE idempotency_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idempotencyKey.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Pedido> obtenerPorId(Connection conn, int idPedido) throws SQLException {
        String sql = "SELECT * FROM pedido WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }
        return Optional.empty();
    }

    private Pedido mapearPedido(ResultSet rs) throws SQLException {
        Pedido p = new Pedido();
        p.setIdPedido(rs.getInt("id_pedido"));
        p.setIdUsuario(rs.getInt("id_usuario"));
        int c = rs.getInt("id_cliente");
        p.setIdCliente(rs.wasNull() ? null : c);
        p.setNumeroMesa(rs.getString("numero_mesa"));
        Timestamp fa = rs.getTimestamp("fecha_hora");
        if (fa != null) {
            p.setFechaHora(fa.toLocalDateTime());
        }
        p.setEstado(rs.getString("estado"));
        p.setTotal(rs.getBigDecimal("total"));
        try {
            p.setIdempotencyKey(rs.getString("idempotency_key"));
        } catch (SQLException ignored) {
            // Compatibilidad
        }
        try {
            p.setTipoPedido(rs.getString("tipo_pedido"));
            p.setClienteNombre(rs.getString("cliente_nombre"));
            p.setClienteTelefono(rs.getString("cliente_telefono"));
            p.setClienteDireccion(rs.getString("cliente_direccion"));
            p.setClienteReferencia(rs.getString("cliente_referencia"));
            p.setCostoEnvio(rs.getBigDecimal("costo_envio"));
            p.setRepartidor(rs.getString("repartidor"));
            int idCreador = rs.getInt("id_usuario_creador");
            p.setIdUsuarioCreador(rs.wasNull() ? p.getIdUsuario() : idCreador);
        } catch (SQLException ignored) {}
        return p;
    }

    @Override
    public int insertarDetallePedido(Connection conn, DetallePedido detalle) throws SQLException {
        LocalDateTime enviadoEn = detalle.getEnviadoEn() != null ? detalle.getEnviadoEn() : LocalDateTime.now(ZONA_LIMA);
        String estadoCocina = detalle.getEstadoCocina() != null ? detalle.getEstadoCocina() : "PENDIENTE";
        String sql = "INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario, subtotal, " +
                     "notas_preparacion, estado_cocina, enviado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, detalle.getIdPedido());
            ps.setInt(2, detalle.getIdProducto());
            ps.setInt(3, detalle.getCantidad());
            ps.setBigDecimal(4, detalle.getPrecioUnitario());
            ps.setBigDecimal(5, detalle.getSubtotal());
            ps.setString(6, detalle.getNotasPreparacion() != null ? detalle.getNotasPreparacion() : "");
            ps.setString(7, estadoCocina);
            ps.setTimestamp(8, Timestamp.valueOf(enviadoEn));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    detalle.setIdDetallePedido(id);
                    return id;
                }
            }
        }
        return 0;
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
        String sql = "SELECT * FROM pedido WHERE estado IN ('ABIERTO', 'REGISTRADO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'POR_COBRAR') ORDER BY id_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearPedido(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Pedido> listarPedidosPendientesCocina(Connection conn) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String sql = "SELECT * FROM pedido WHERE estado IN ('ABIERTO', 'REGISTRADO', 'EN_PREPARACION') ORDER BY id_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearPedido(rs));
            }
        }
        return lista;
    }

    @Override
    public List<DetallePedido> listarDetallesPorPedido(Connection conn, int idPedido) throws SQLException {
        List<DetallePedido> lista = new ArrayList<>();
        String sql = "SELECT dp.*, p.nombre AS nombre_prod, ped.numero_mesa FROM detalle_pedido dp " +
                     "JOIN producto p ON dp.id_producto = p.id_producto " +
                     "JOIN pedido ped ON dp.id_pedido = ped.id_pedido " +
                     "WHERE dp.id_pedido = ? ORDER BY dp.id_detalle_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearDetallePedido(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Lista los ítems pendientes de cocina ({@code PENDIENTE} o {@code EN_PREPARACION})
     * con su marca temporal individual {@code enviado_en} (SPEC-FOODCORE-v1.0 §8.5; REMEDIATION R-08).
     */
    @Override
    public List<DetallePedido> listarItemsPendientesCocina(Connection conn) throws SQLException {
        List<DetallePedido> lista = new ArrayList<>();
        String sql = "SELECT dp.*, p.nombre AS nombre_prod, ped.numero_mesa " +
                     "FROM detalle_pedido dp " +
                     "JOIN producto p ON dp.id_producto = p.id_producto " +
                     "JOIN pedido ped ON dp.id_pedido = ped.id_pedido " +
                     "WHERE dp.estado_cocina IN ('PENDIENTE', 'EN_PREPARACION') " +
                     "AND ped.estado IN ('ABIERTO', 'REGISTRADO', 'EN_PREPARACION') " +
                     "ORDER BY dp.id_detalle_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearDetallePedido(rs));
            }
        }
        return lista;
    }

    private DetallePedido mapearDetallePedido(ResultSet rs) throws SQLException {
        DetallePedido dp = new DetallePedido();
        dp.setIdDetallePedido(rs.getInt("id_detalle_pedido"));
        dp.setIdPedido(rs.getInt("id_pedido"));
        dp.setIdProducto(rs.getInt("id_producto"));
        dp.setCantidad(rs.getInt("cantidad"));
        dp.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
        dp.setSubtotal(rs.getBigDecimal("subtotal"));
        dp.setNotasPreparacion(rs.getString("notas_preparacion"));
        dp.setNombreProducto(rs.getString("nombre_prod"));
        try {
            dp.setNumeroMesa(rs.getString("numero_mesa"));
            String estCocina = rs.getString("estado_cocina");
            if (estCocina != null) dp.setEstadoCocina(estCocina);
            Timestamp env = rs.getTimestamp("enviado_en");
            if (env != null) dp.setEnviadoEn(env.toLocalDateTime());
            Timestamp ini = rs.getTimestamp("iniciado_en");
            if (ini != null) dp.setIniciadoEn(ini.toLocalDateTime());
            Timestamp lst = rs.getTimestamp("listo_en");
            if (lst != null) dp.setListoEn(lst.toLocalDateTime());
        } catch (SQLException ignored) {
            // Columnas opcionales en esquemas previos
        }
        return dp;
    }

    @Override
    public void actualizarEstadoPedido(Connection conn, int idPedido, String nuevoEstado) throws SQLException {
        String sql = "UPDATE pedido SET estado = ?, version = version + 1 WHERE id_pedido = ?";
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
        } else if ("COBRADO".equalsIgnoreCase(nuevoEstado) || "CERRADO".equalsIgnoreCase(nuevoEstado) || "ENTREGADO".equalsIgnoreCase(nuevoEstado)) {
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
    public void actualizarEstadoDetalleCocina(Connection conn, int idDetallePedido, String nuevoEstado) throws SQLException {
        String sql;
        if ("EN_PREPARACION".equalsIgnoreCase(nuevoEstado)) {
            sql = "UPDATE detalle_pedido SET estado_cocina = ?, iniciado_en = ? WHERE id_detalle_pedido = ?";
        } else if ("LISTO".equalsIgnoreCase(nuevoEstado) || "ENTREGADO".equalsIgnoreCase(nuevoEstado)) {
            sql = "UPDATE detalle_pedido SET estado_cocina = ?, listo_en = ? WHERE id_detalle_pedido = ?";
        } else {
            sql = "UPDATE detalle_pedido SET estado_cocina = ?, enviado_en = ? WHERE id_detalle_pedido = ?";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado.toUpperCase());
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now(ZONA_LIMA)));
            ps.setInt(3, idDetallePedido);
            ps.executeUpdate();
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

    /**
     * Bloqueo optimista sobre {@code mesa} según SPEC-FOODCORE-v1.0 (§8.4; REMEDIATION R-02.5):
     * {@code UPDATE mesa SET estado=?, version=version+1 WHERE id_mesa=? AND version=?}.
     */
    @Override
    public void actualizarEstadoMesaConVersion(Connection conn, long idMesa, String nuevoEstado, int versionEsperada) throws SQLException {
        String sql = "UPDATE mesa SET estado = ?, version = version + 1 WHERE id_mesa = ? AND version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setLong(2, idMesa);
            ps.setInt(3, versionEsperada);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException(new ConflictoEstadoException(
                    "Conflicto de concurrencia optimista en mesa #" + idMesa + " (versión esperada: " + versionEsperada + ")."
                ));
            }
        }
    }
}
