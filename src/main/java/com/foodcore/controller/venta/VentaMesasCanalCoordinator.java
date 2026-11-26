package com.foodcore.controller.venta;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface VentaMesasCanalCoordinator {
    record ResumenCanalesPos(Map<String, Pedido> pedidosPorMesa, List<Pedido> ordenesExternas) { }
    record CabeceraCanalPos(String headerTexto, String badgeTexto, String canal, String mesaNormalizada) { }

    List<Pedido> listarPedidosActivosConDetallesSafe();
    List<DetallePedido> listarDetallesPorPedido(int idPedido);
    String determinarCanalPedido(Pedido p);
    String construirTextoBadgeCanal(Pedido p);
    String normalizarNumeroMesa(String raw);
    ResumenCanalesPos particionarPedidosActivos(List<Pedido> activos);
    CabeceraCanalPos resolverCabeceraPedido(Pedido pedido);
    CabeceraCanalPos resolverCabeceraMesaLibre(String numeroMesa);
    List<DetalleVenta> convertirDetallesPedidoAVenta(List<DetallePedido> detalles);
    DetallePedido convertirDetalleVentaAPedido(DetalleVenta dv, int idPedido);
    void actualizarCachePedidoActivo(List<Pedido> pedidosActivosMulticanal, Map<String, Pedido> pedidosPorMesa,
                                    int idPedido, BigDecimal totalPedido, List<DetallePedido> detalles);
}
