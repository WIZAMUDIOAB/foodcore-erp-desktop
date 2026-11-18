package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resumen de una compra completada de cliente minorista para la ficha Backoffice (SPEC v5.0.0 §3.1).
 */
public class CompraClienteResumen {

    private int idVenta;
    private LocalDateTime fechaHora;
    private BigDecimal montoTotal = BigDecimal.ZERO;
    private String metodoPago;
    private String resumenProductos;
    private int puntosGanados;

    public CompraClienteResumen() {
    }

    public CompraClienteResumen(int idVenta,
                                LocalDateTime fechaHora,
                                BigDecimal montoTotal,
                                String metodoPago,
                                String resumenProductos,
                                int puntosGanados) {
        this.idVenta = idVenta;
        this.fechaHora = fechaHora;
        this.montoTotal = montoTotal != null ? montoTotal : BigDecimal.ZERO;
        this.metodoPago = metodoPago;
        this.resumenProductos = resumenProductos;
        this.puntosGanados = puntosGanados;
    }

    public int getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(int idVenta) {
        this.idVenta = idVenta;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public void setMontoTotal(BigDecimal montoTotal) {
        this.montoTotal = montoTotal != null ? montoTotal : BigDecimal.ZERO;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getResumenProductos() {
        return resumenProductos;
    }

    public void setResumenProductos(String resumenProductos) {
        this.resumenProductos = resumenProductos;
    }

    public int getPuntosGanados() {
        return puntosGanados;
    }

    public void setPuntosGanados(int puntosGanados) {
        this.puntosGanados = puntosGanados;
    }
}
