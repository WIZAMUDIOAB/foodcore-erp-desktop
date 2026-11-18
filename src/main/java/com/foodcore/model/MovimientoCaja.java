package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MovimientoCaja {
    private int idMovimiento;
    private int idCaja;
    private int idUsuario;
    private String tipoMovimiento; // INGRESO, EGRESO, VENTA
    private String medioPago;      // EFECTIVO, TARJETA, YAPE, PLIN
    private BigDecimal monto;
    private String concepto;
    private Integer idCategoriaGasto;
    private String nombreCategoriaGasto;
    private LocalDateTime fechaHora;

    public MovimientoCaja() {
        this.medioPago = "EFECTIVO";
    }

    public MovimientoCaja(int idMovimiento, int idCaja, int idUsuario, String tipoMovimiento, BigDecimal monto, String concepto) {
        this(idMovimiento, idCaja, idUsuario, tipoMovimiento, "EFECTIVO", monto, concepto, null);
    }

    public MovimientoCaja(int idMovimiento, int idCaja, int idUsuario, String tipoMovimiento,
                          String medioPago, BigDecimal monto, String concepto) {
        this(idMovimiento, idCaja, idUsuario, tipoMovimiento, medioPago, monto, concepto, null);
    }

    public MovimientoCaja(int idMovimiento, int idCaja, int idUsuario, String tipoMovimiento,
                          String medioPago, BigDecimal monto, String concepto, Integer idCategoriaGasto) {
        this.idMovimiento = idMovimiento;
        this.idCaja = idCaja;
        this.idUsuario = idUsuario;
        this.tipoMovimiento = tipoMovimiento;
        this.medioPago = medioPago != null ? medioPago : "EFECTIVO";
        this.monto = monto;
        this.concepto = concepto;
        this.idCategoriaGasto = idCategoriaGasto;
        this.fechaHora = LocalDateTime.now();
    }

    public int getIdMovimiento() {
        return idMovimiento;
    }

    public void setIdMovimiento(int idMovimiento) {
        this.idMovimiento = idMovimiento;
    }

    public int getIdCaja() {
        return idCaja;
    }

    public void setIdCaja(int idCaja) {
        this.idCaja = idCaja;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(String tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
    }

    public String getMedioPago() {
        return medioPago;
    }

    public void setMedioPago(String medioPago) {
        this.medioPago = medioPago;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public Integer getIdCategoriaGasto() {
        return idCategoriaGasto;
    }

    public void setIdCategoriaGasto(Integer idCategoriaGasto) {
        this.idCategoriaGasto = idCategoriaGasto;
    }

    public String getNombreCategoriaGasto() {
        return nombreCategoriaGasto;
    }

    public void setNombreCategoriaGasto(String nombreCategoriaGasto) {
        this.nombreCategoriaGasto = nombreCategoriaGasto;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
