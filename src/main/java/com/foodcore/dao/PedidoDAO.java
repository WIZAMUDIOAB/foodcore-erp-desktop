package com.foodcore.dao;

import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface PedidoDAO {
    int crearPedido(Connection conn, Pedido pedido) throws SQLException;
    void insertarDetallePedido(Connection conn, DetallePedido detalle) throws SQLException;
    int crearComanda(Connection conn, Comanda comanda) throws SQLException;
    List<Pedido> listarPedidosActivos(Connection conn) throws SQLException;
    List<Pedido> listarPedidosPendientesCocina(Connection conn) throws SQLException;
    List<DetallePedido> listarDetallesPorPedido(Connection conn, int idPedido) throws SQLException;
    void actualizarEstadoPedido(Connection conn, int idPedido, String nuevoEstado) throws SQLException;
    void actualizarEstadoComanda(Connection conn, int idComanda, String nuevoEstado) throws SQLException;
}
