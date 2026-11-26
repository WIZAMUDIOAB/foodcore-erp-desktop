package com.foodcore.service.api;

import com.foodcore.model.Caja;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Venta;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de operaciones de dominio y persistencia para los controladores REST de salón, KDS y caja.
 */
public interface ApiOperacionesPort {

    record EstadoMesasSnapshot(Map<String, String> estadosPorMesa, Map<String, BigDecimal> totalesPorMesa) {
        public EstadoMesasSnapshot {
            estadosPorMesa = estadosPorMesa != null ? Map.copyOf(estadosPorMesa) : Map.of();
            totalesPorMesa = totalesPorMesa != null ? Map.copyOf(totalesPorMesa) : Map.of();
        }
    }

    record ItemSolicitudPedido(int idProducto, int cantidad, String notas) {
    }

    record ResultadoCreacionPedido(Pedido pedido, List<DetallePedido> detalles, boolean idempotente) {
        public ResultadoCreacionPedido {
            detalles = detalles != null ? List.copyOf(detalles) : List.of();
        }
    }

    record PedidosActivosSnapshot(List<Pedido> pedidos, Map<Integer, List<DetallePedido>> detallesPorPedido) {
        public PedidosActivosSnapshot {
            pedidos = pedidos != null ? List.copyOf(pedidos) : List.of();
            if (detallesPorPedido == null || detallesPorPedido.isEmpty()) {
                detallesPorPedido = Map.of();
            } else {
                Map<Integer, List<DetallePedido>> copia = new LinkedHashMap<>();
                for (Map.Entry<Integer, List<DetallePedido>> entry : detallesPorPedido.entrySet()) {
                    if (entry.getKey() != null) {
                        copia.put(entry.getKey(),
                                entry.getValue() != null ? List.copyOf(entry.getValue()) : List.of());
                    }
                }
                detallesPorPedido = Map.copyOf(copia);
            }
        }
    }

    record PedidoConDetalles(Pedido pedido, List<DetallePedido> detalles) {
        public PedidoConDetalles {
            detalles = detalles != null ? List.copyOf(detalles) : List.of();
        }
    }

    record EstadoCajaSnapshot(boolean abierta, Caja caja, BigDecimal totalVentas,
                              BigDecimal totalGastos, BigDecimal saldoEsperado) {
    }

    record ResultadoCierreCaja(int idCaja, BigDecimal esperado, BigDecimal contado, BigDecimal diferencia) {
    }

    record SolicitudCobroCaja(int idUsuario, int idPedido, String numeroMesa, String tipoComprobante,
                                    String medioPago, String idempotencyKey, BigDecimal montoRecibidoOpt,
                                    List<ItemSolicitudPedido> itemsDirectos) {
        public SolicitudCobroCaja {
            itemsDirectos = itemsDirectos != null ? List.copyOf(itemsDirectos) : List.of();
        }
    }

    record ResultadoCobroCaja(Venta venta, Integer idPedidoPersistido, String numeroMesa, String tipoComprobante,
                                    String numeroComprobante, String medioPago, BigDecimal montoRecibido,
                                    BigDecimal vuelto, boolean mesaLiberada) {
    }

    List<Producto> listarCatalogoProductos(boolean soloDisponibles);

    void actualizarDisponibilidadProducto(int idProducto, boolean disponible) throws ApiOperacionException;

    Optional<String> obtenerImagenUrlProducto(int idProducto);

    EstadoMesasSnapshot obtenerEstadoMesas();

    ResultadoCreacionPedido crearPedidoOIdempotente(int idUsuario, String numeroMesa, String uuidLocal,
            List<ItemSolicitudPedido> items) throws ApiOperacionException;

    PedidosActivosSnapshot listarPedidosActivosConDetalles() throws ApiOperacionException;

    Optional<PedidoConDetalles> buscarPedidoPorUuid(String uuid) throws ApiOperacionException;

    void actualizarEstadoPedido(int idPedido, String nuevoEstado) throws ApiOperacionException;

    EstadoCajaSnapshot consultarEstadoCaja();

    int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial) throws ApiOperacionException;

    ResultadoCierreCaja cerrarTurnoCaja(int idUsuario, int idCajaSolicitado, BigDecimal contado)
            throws ApiOperacionException;

    ResultadoCobroCaja cobrarEnCaja(SolicitudCobroCaja solicitud) throws ApiOperacionException;
}
