package com.foodcore.service;

import com.foodcore.model.Pedido;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de persistencia JDBC para el Tablero de Entregas y Delivery
 * (SPEC-FOODCORE-v1.3-ENTERPRISE §3.3). Centraliza las consultas de pedidos
 * DELIVERY, el registro rápido de pedidos y la actualización de estado/repartidor,
 * reutilizando el pool HikariCP de {@link ConexionBD} con sentencias parametrizadas.
 */
public class DeliveryGestionService {

    private static final String TIPO_DELIVERY = "DELIVERY";
    private static final String PREFIJO_MESA_DELIVERY = "DL-";
    private static final int MODULO_NUMERO_MESA = 10000;

    private static final String SQL_LISTAR_DELIVERY_RECENTES =
            "SELECT id_pedido, numero_mesa, cliente_nombre, cliente_telefono, cliente_direccion, "
            + "cliente_referencia, costo_envio, total, estado, repartidor "
            + "FROM pedido WHERE tipo_pedido = 'DELIVERY' ORDER BY id_pedido DESC LIMIT ?";

    private static final String SQL_INSERTAR_DELIVERY_RAPIDO =
            "INSERT INTO pedido (id_usuario, numero_mesa, tipo_pedido, cliente_nombre, cliente_telefono, "
            + "cliente_direccion, cliente_referencia, costo_envio, total, estado, idempotency_key) "
            + "VALUES (1, ?, 'DELIVERY', ?, ?, ?, ?, ?, ?, 'EN_COCINA', ?)";

    private static final String SQL_ACTUALIZAR_ESTADO_REPARTIDOR =
            "UPDATE pedido SET estado = ?, repartidor = ? WHERE id_pedido = ?";

    /**
     * Lista los pedidos DELIVERY más recientes (máximo {@code limite} filas).
     *
     * @param limite número máximo de pedidos a recuperar.
     * @return pedidos DELIVERY ordenados por identificador descendente; lista vacía si no hay filas.
     * @throws SQLException si falla la consulta en base de datos.
     */
    public List<Pedido> listarPedidosDeliveryRecientes(int limite) throws SQLException {
        List<Pedido> pedidos = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return pedidos;
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_DELIVERY_RECENTES)) {
                ps.setInt(1, limite);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pedidos.add(mapearPedido(rs));
                    }
                }
            }
        }
        return pedidos;
    }

    private static Pedido mapearPedido(ResultSet rs) throws SQLException {
        Pedido p = new Pedido();
        p.setIdPedido(rs.getInt("id_pedido"));
        p.setNumeroMesa(rs.getString("numero_mesa"));
        p.setTipoPedido(TIPO_DELIVERY);
        p.setClienteNombre(rs.getString("cliente_nombre"));
        p.setClienteTelefono(rs.getString("cliente_telefono"));
        p.setClienteDireccion(rs.getString("cliente_direccion"));
        p.setClienteReferencia(rs.getString("cliente_referencia"));
        p.setCostoEnvio(rs.getBigDecimal("costo_envio"));
        p.setTotal(rs.getBigDecimal("total"));
        p.setEstado(rs.getString("estado"));
        p.setRepartidor(rs.getString("repartidor"));
        return p;
    }

    /**
     * Registra un pedido delivery rápido y lo envía directamente a cocina.
     *
     * @param nombre      nombre del cliente.
     * @param telefono    teléfono / WhatsApp del cliente.
     * @param direccion   dirección de entrega.
     * @param referencia  referencia de la dirección.
     * @param costoEnvio  costo de envío (sin IGV).
     * @param total       total del pedido.
     * @throws SQLException si falla la inserción en base de datos.
     */
    public void registrarDeliveryRapido(
            String nombre,
            String telefono,
            String direccion,
            String referencia,
            BigDecimal costoEnvio,
            BigDecimal total) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("El DataSource HikariCP no ha podido inicializarse.");
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_DELIVERY_RAPIDO)) {
                ps.setString(1, PREFIJO_MESA_DELIVERY + (System.currentTimeMillis() % MODULO_NUMERO_MESA));
                ps.setString(2, nombre);
                ps.setString(3, telefono);
                ps.setString(4, direccion);
                ps.setString(5, referencia);
                ps.setBigDecimal(6, costoEnvio);
                ps.setBigDecimal(7, total);
                ps.setString(8, UUID.randomUUID().toString());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Actualiza el estado y el repartidor asignado de un pedido.
     *
     * @param idPedido    identificador del pedido.
     * @param nuevoEstado nuevo estado del pedido.
     * @param repartidor  nombre del repartidor asignado.
     * @throws SQLException si falla la actualización en base de datos.
     */
    public void actualizarEstadoYRepartidor(int idPedido, String nuevoEstado, String repartidor) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("El DataSource HikariCP no ha podido inicializarse.");
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_ACTUALIZAR_ESTADO_REPARTIDOR)) {
                ps.setString(1, nuevoEstado);
                ps.setString(2, repartidor != null ? repartidor : "");
                ps.setInt(3, idPedido);
                ps.executeUpdate();
            }
        }
    }
}
