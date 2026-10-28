package com.foodcore.model;

import java.math.BigDecimal;

public class ResumenKpi {
    private BigDecimal totalVentas = BigDecimal.ZERO;
    private int cantidadTransacciones = 0;
    private BigDecimal ticketPromedio = BigDecimal.ZERO;
    private String platoMasVendido = "Sin ventas";
    private int cantidadPlatoMasVendido = 0;

    public ResumenKpi() {
    }

    public ResumenKpi(BigDecimal totalVentas, int cantidadTransacciones, BigDecimal ticketPromedio, String platoMasVendido, int cantidadPlatoMasVendido) {
        this.totalVentas = totalVentas != null ? totalVentas : BigDecimal.ZERO;
        this.cantidadTransacciones = cantidadTransacciones;
        this.ticketPromedio = ticketPromedio != null ? ticketPromedio : BigDecimal.ZERO;
        this.platoMasVendido = platoMasVendido != null ? platoMasVendido : "Sin ventas";
        this.cantidadPlatoMasVendido = cantidadPlatoMasVendido;
    }

    public BigDecimal getTotalVentas() {
        return totalVentas;
    }

    public void setTotalVentas(BigDecimal totalVentas) {
        this.totalVentas = totalVentas;
    }

    public int getCantidadTransacciones() {
        return cantidadTransacciones;
    }

    public void setCantidadTransacciones(int cantidadTransacciones) {
        this.cantidadTransacciones = cantidadTransacciones;
    }

    public BigDecimal getTicketPromedio() {
        return ticketPromedio;
    }

    public void setTicketPromedio(BigDecimal ticketPromedio) {
        this.ticketPromedio = ticketPromedio;
    }

    public String getPlatoMasVendido() {
        return platoMasVendido;
    }

    public void setPlatoMasVendido(String platoMasVendido) {
        this.platoMasVendido = platoMasVendido;
    }

    public int getCantidadPlatoMasVendido() {
        return cantidadPlatoMasVendido;
    }

    public void setCantidadPlatoMasVendido(int cantidadPlatoMasVendido) {
        this.cantidadPlatoMasVendido = cantidadPlatoMasVendido;
    }
}
