package com.foodcore.dao.impl;

import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Implementación canónica de {@code PedidoDAO} en paquete {@code com.foodcore.dao.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-02.5, R-07, R-08).
 */
public class PedidoDAOImpl extends com.foodcore.dao.PedidoDAOImpl {

    @Override
    public int crearPedido(Connection conn, Pedido pedido) throws SQLException {
        return super.crearPedido(conn, pedido);
    }

    @Override
    public Optional<Pedido> buscarPorIdempotencyKey(Connection conn, String idempotencyKey) throws SQLException {
        return super.buscarPorIdempotencyKey(conn, idempotencyKey);
    }

    @Override
    public Optional<Pedido> obtenerPorId(Connection conn, int idPedido) throws SQLException {
        return super.obtenerPorId(conn, idPedido);
    }

    @Override
    public int insertarDetallePedido(Connection conn, DetallePedido detalle) throws SQLException {
        return super.insertarDetallePedido(conn, detalle);
    }

    @Override
    public int crearComanda(Connection conn, Comanda comanda) throws SQLException {
        return super.crearComanda(conn, comanda);
    }

    @Override
    public List<DetallePedido> listarItemsPendientesCocina(Connection conn) throws SQLException {
        return super.listarItemsPendientesCocina(conn);
    }

    @Override
    public void actualizarEstadoMesaConVersion(Connection conn, long idMesa, String nuevoEstado, int versionEsperada) throws SQLException {
        super.actualizarEstadoMesaConVersion(conn, idMesa, nuevoEstado, versionEsperada);
    }
}
