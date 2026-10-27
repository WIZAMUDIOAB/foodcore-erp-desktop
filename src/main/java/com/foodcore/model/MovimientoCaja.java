package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MovimientoCaja {
    private int idMovimiento;
    private int idCaja;
    private int idUsuario;
    private String tipoMovimiento; // INGRESO, EGRESO, VENTA
    private BigDecimal monto;
    private String concepto;
    private LocalDateTime fechaHora;

    public MovimientoCaja() {}

    public MovimientoCaja(int idMovimiento, int idCaja, int idUsuario, String tipoMovimiento, BigDecimal monto, String concepto) {
        this.idMovimiento = idMovimiento;
        this.idCaja = idCaja;
        this.idUsuario = idUsuario;
        this.tipoMovimiento = tipoMovimiento;
        this.monto = monto;
        this.concepto = concepto;
        this.fechaHora = LocalDateTime.now();
    }

    public int getIdMovimiento() { return idMovimiento; }
    public void setIdMovimiento(int idMovimiento) { this.idMovimiento = idMovimiento; }

    public int getIdCaja() { return idCaja; }
    public void setIdCaja(int idCaja) { this.idCaja = idCaja; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
}
