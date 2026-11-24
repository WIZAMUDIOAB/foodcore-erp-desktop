package com.foodcore.controller.venta;

import com.foodcore.model.Cliente;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

import java.math.BigDecimal;
import java.util.List;

/**
 * Puerto desacoplado de estado de la orden activa en POS hacia el coordinador de operaciones.
 */
public interface VentaPosOrdenHostPort {

    ObservableList<DetalleVenta> getItemsVenta();

    Integer getPedidoActivoId();

    void setPedidoActivoId(Integer pedidoActivoId);

    String getMesaSeleccionadaActual();

    String getCanalSeleccionadoActual();

    BigDecimal getDescuentoActivo();

    void aplicarDescuentoManual(BigDecimal descuento);

    BigDecimal getBasePreCanjeActual();

    BigDecimal getTotalNetoActual();

    Cliente getClienteSeleccionadoPos();

    BigDecimal getDescuentoPuntosActivo();

    int getPuntosARedimirPos();

    void aplicarDetallesPedidoEnPOS(List<DetallePedido> detalles);

    void actualizarCachePedidoActivo(int idPedido, BigDecimal totalPedido, List<DetallePedido> detalles);

    void refrescarMesasYCanalesAsync();

    void finalizarOrdenTrasCobro();

    void recalcularTotales();

    void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo);
}
