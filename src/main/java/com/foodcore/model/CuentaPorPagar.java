package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CuentaPorPagar {
    private static final int ESCALA_MONETARIA = 2;
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

    private int idCuentaPagar;
    private int idProveedor;
    private String razonSocialProveedor;
    private String rucProveedor;
    private String numeroDocumento;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private BigDecimal montoTotal = CERO;
    private BigDecimal montoPagado = CERO;
    private String estado; // PENDIENTE, PAGADA, VENCIDA
    private String observaciones;
    private LocalDateTime fechaRegistro;

    public CuentaPorPagar() {
        this.estado = "PENDIENTE";
    }

    public BigDecimal getSaldo() {
        BigDecimal saldo = getMontoTotal().subtract(getMontoPagado())
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
        return saldo.compareTo(BigDecimal.ZERO) < 0 ? CERO : saldo;
    }

    private static BigDecimal normalizarMoneda(BigDecimal valor) {
        return valor != null ? valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP) : CERO;
    }

    public int getIdCuentaPagar() {
        return idCuentaPagar;
    }

    public void setIdCuentaPagar(int idCuentaPagar) {
        this.idCuentaPagar = idCuentaPagar;
    }

    public int getIdProveedor() {
        return idProveedor;
    }

    public void setIdProveedor(int idProveedor) {
        this.idProveedor = idProveedor;
    }

    public String getRazonSocialProveedor() {
        return razonSocialProveedor;
    }

    public void setRazonSocialProveedor(String razonSocialProveedor) {
        this.razonSocialProveedor = razonSocialProveedor;
    }

    public String getRucProveedor() {
        return rucProveedor;
    }

    public void setRucProveedor(String rucProveedor) {
        this.rucProveedor = rucProveedor;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
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

    public BigDecimal getMontoPagado() {
        return montoPagado;
    }

    public void setMontoPagado(BigDecimal montoPagado) {
        this.montoPagado = normalizarMoneda(montoPagado);
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
