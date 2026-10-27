package com.foodcore.model;

import java.math.BigDecimal;

public class DetalleCompra {
    private int idDetalleCompra;
    private int idCompra;
    private int idInsumo;
    private BigDecimal cantidad;
    private BigDecimal costoUnitario;
    private BigDecimal subtotal;

    private String nombreInsumo;

    public DetalleCompra() {}

    public DetalleCompra(int idDetalleCompra, int idCompra, int idInsumo, BigDecimal cantidad, BigDecimal costoUnitario, BigDecimal subtotal) {
        this.idDetalleCompra = idDetalleCompra;
        this.idCompra = idCompra;
        this.idInsumo = idInsumo;
        this.cantidad = cantidad;
        this.costoUnitario = costoUnitario;
        this.subtotal = subtotal;
    }

    public int getIdDetalleCompra() { return idDetalleCompra; }
    public void setIdDetalleCompra(int idDetalleCompra) { this.idDetalleCompra = idDetalleCompra; }

    public int getIdCompra() { return idCompra; }
    public void setIdCompra(int idCompra) { this.idCompra = idCompra; }

    public int getIdInsumo() { return idInsumo; }
    public void setIdInsumo(int idInsumo) { this.idInsumo = idInsumo; }

    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }

    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public String getNombreInsumo() { return nombreInsumo; }
    public void setNombreInsumo(String nombreInsumo) { this.nombreInsumo = nombreInsumo; }
}
