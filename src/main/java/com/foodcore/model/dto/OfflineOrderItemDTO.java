package com.foodcore.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.foodcore.model.DetallePedido;

import java.math.BigDecimal;

/**
 * Vista de transporte de un ítem de pedido para sincronización offline.
 * Expone únicamente los campos relevantes para reconstruir la comanda en el servidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OfflineOrderItemDTO {

    private int idProducto;
    private String nombreProducto;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    private String notasPreparacion;

    public OfflineOrderItemDTO() {
    }

    public OfflineOrderItemDTO(int idProducto, String nombreProducto, int cantidad,
                               BigDecimal precioUnitario, BigDecimal subtotal, String notasPreparacion) {
        this.idProducto = idProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
        this.notasPreparacion = notasPreparacion;
    }

    /**
     * Construye la vista a partir de un {@link DetallePedido} persistente.
     */
    public static OfflineOrderItemDTO from(DetallePedido detalle) {
        if (detalle == null) {
            return null;
        }
        return new OfflineOrderItemDTO(
                detalle.getIdProducto(),
                detalle.getNombreProducto(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal(),
                detalle.getNotasPreparacion());
    }

    /**
     * Reconstruye el ítem persistente con estado inicial de cocina y marca de envío estándar.
     */
    public DetallePedido toDetallePedido() {
        DetallePedido detalle = new DetallePedido();
        detalle.setIdProducto(this.idProducto);
        detalle.setNombreProducto(this.nombreProducto);
        detalle.setCantidad(this.cantidad);
        detalle.setPrecioUnitario(this.precioUnitario);
        detalle.setSubtotal(this.subtotal);
        detalle.setNotasPreparacion(this.notasPreparacion);
        return detalle;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getNotasPreparacion() {
        return notasPreparacion;
    }

    public void setNotasPreparacion(String notasPreparacion) {
        this.notasPreparacion = notasPreparacion;
    }
}
