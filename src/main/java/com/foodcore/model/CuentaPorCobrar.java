package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CuentaPorCobrar {
    private static final int ESCALA_MONETARIA = 2;
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

    private int idCuentaCobrar;
    private int idCliente;
    private String razonSocialCliente;
    private String numeroDocumentoCliente;
    private Integer idVenta;
    private String numeroComprobante;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private BigDecimal montoTotal = CERO;
    private BigDecimal montoCobrado = CERO;
    private String estado; // PENDIENTE, COBRADA, VENCIDA
    private String observaciones;
    private LocalDateTime fechaRegistro;

    public CuentaPorCobrar() {
        this.estado = "PENDIENTE";
    }

    public BigDecimal getSaldo() {
        BigDecimal saldo = getMontoTotal().subtract(getMontoCobrado())
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return saldo.compareTo(BigDecimal.ZERO) < 0 ? CERO : saldo;
    }

    private static BigDecimal normalizarMoneda(BigDecimal valor) {
        return valor != null ? valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP) : CERO;
    }

    public int getIdCuentaCobrar() {
        return idCuentaCobrar;
    }

    public void setIdCuentaCobrar(int idCuentaCobrar) {
        this.idCuentaCobrar = idCuentaCobrar;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(int idCliente) {
        this.idCliente = idCliente;
    }

    public String getRazonSocialCliente() {
        return razonSocialCliente;
    }

    public void setRazonSocialCliente(String razonSocialCliente) {
        this.razonSocialCliente = razonSocialCliente;
    }

    public String getNumeroDocumentoCliente() {
        return numeroDocumentoCliente;
    }

    public void setNumeroDocumentoCliente(String numeroDocumentoCliente) {
        this.numeroDocumentoCliente = numeroDocumentoCliente;
    }

    public Integer getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(Integer idVenta) {
        this.idVenta = idVenta;
    }

    public String getNumeroComprobante() {
        return numeroComprobante;
    }

    public void setNumeroComprobante(String numeroComprobante) {
        this.numeroComprobante = numeroComprobante;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public void setMontoTotal(BigDecimal montoTotal) {
        this.montoTotal = normalizarMoneda(montoTotal);
    }

    public BigDecimal getMontoCobrado() {
        return montoCobrado;
    }

    public void setMontoCobrado(BigDecimal montoCobrado) {
        this.montoCobrado = normalizarMoneda(montoCobrado);
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
