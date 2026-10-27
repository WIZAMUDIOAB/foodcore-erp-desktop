package com.foodcore.model;

import java.math.BigDecimal;

public class DetallePedido {
    private int idDetallePedido;
    private int idPedido;
    private int idProducto;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    private String notasPreparacion;

    // Campo descriptivo
    private String nombreProducto;

    public DetallePedido() {}

    public DetallePedido(int idDetallePedido, int idPedido, int idProducto, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal, String notasPreparacion) {
        this.idDetallePedido = idDetallePedido;
        this.idPedido = idPedido;
        this.idProducto = idProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
        this.notasPreparacion = notasPreparacion;
    }

    public int getIdDetallePedido() { return idDetallePedido; }
    public void setIdDetallePedido(int idDetallePedido) { this.idDetallePedido = idDetallePedido; }

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public String getNotasPreparacion() { return notasPreparacion; }
    public void setNotasPreparacion(String notasPreparacion) { this.notasPreparacion = notasPreparacion; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    @Override
    public String toString() {
        return cantidad + "x " + nombreProducto + " [S/ " + subtotal + "]";
    }
}
