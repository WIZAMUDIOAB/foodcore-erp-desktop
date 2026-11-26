package com.foodcore.service;

import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.security.SesionUsuario;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Contrato de servicio transaccional de gestión de Pedidos, Comandas y bloqueo optimista de Mesas
 * según SPEC-FOODCORE-v1.0 (§6.2, §9.1, §9.2; REMEDIATION R-02.5).
 */
public interface PedidoService {

    Pedido crearPedido(Pedido pedido, List<DetallePedido> detalles) throws NegocioException, SQLException;

    Pedido registrarPedidoYEnviarComanda(Pedido pedido, List<DetallePedido> detalles)
            throws NegocioException, SQLException;

    void actualizarEstadoMesaOptimista(long idMesa, String nuevoEstado, int versionEsperada)
            throws ConflictoEstadoException, SQLException;

    Pedido adicionarItemsPostEntrega(int idPedido, List<DetallePedido> nuevosItems, SesionUsuario sesion)
            throws NegocioException, SQLException;

    BigDecimal calcularTotal(int idPedido) throws SQLException;

    List<DetallePedido> listarDetallesPorPedido(int idPedido) throws SQLException;

    Pedido agregarItemsAPedido(int idPedido, List<DetallePedido> nuevosItems)
            throws NegocioException, SQLException;

    Pedido agregarItemsAPedido(int idPedido, List<DetallePedido> nuevosItems, SesionUsuario sesion)
            throws NegocioException, SQLException;

    boolean transferirMesa(int idMesaOrigen, int idMesaDestino) throws SQLException;

    List<DetallePedido> listarItemsPendientesCocina() throws SQLException;

    List<Pedido> listarPedidosPendientesCocina() throws SQLException;

    List<DetallePedido> listarItemsPendientesCocinaPorPedido(int idPedido) throws SQLException;

    void actualizarEstadoKds(int idDetallePedido, int idPedido, String estado) throws SQLException;

    void marcarItemEnPreparacionKds(int idDetallePedido, int idPedido) throws SQLException;

    void marcarItemEstadoYActualizarPedidoKds(int idDetallePedido, int idPedido, String nuevoEstadoDetalle)
            throws SQLException;

    String obtenerEstadoCocinaDetalle(int idDetallePedido) throws SQLException;

    void marcarPedidoEntregadoKds(List<Integer> idsDetalle, int idPedido) throws SQLException;

    void reordenarPrioridadKds(List<Integer> idsDetalleOrdenDescendente) throws SQLException;
}
