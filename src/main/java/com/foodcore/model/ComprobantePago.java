package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ComprobantePago {
    private int idComprobante;
    private int idVenta;
    private String tipoComprobante; // BOLETA, FACTURA, TICKET
    private String serie;
    private String numero;
    private LocalDateTime fechaEmision;
    private BigDecimal total;

    public ComprobantePago() {}

    public ComprobantePago(int idComprobante, int idVenta, String tipoComprobante, String serie, String numero, BigDecimal total) {
        this.idComprobante = idComprobante;
        this.idVenta = idVenta;
        this.tipoComprobante = tipoComprobante;
        this.serie = serie;
        this.numero = numero;
        this.total = total;
        this.fechaEmision = LocalDateTime.now();
    }

    public int getIdComprobante() { return idComprobante; }
    public void setIdComprobante(int idComprobante) { this.idComprobante = idComprobante; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public String getTipoComprobante() { return tipoComprobante; }
    public void setTipoComprobante(String tipoComprobante) { this.tipoComprobante = tipoComprobante; }

    public String getSerie() { return serie; }
    public void setSerie(String serie) { this.serie = serie; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime fechaEmision) { this.fechaEmision = fechaEmision; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getNumeroCompleto() {
        return serie + "-" + numero;
    }

    @Override
    public String toString() { return tipoComprobante + " " + getNumeroCompleto(); }
}
