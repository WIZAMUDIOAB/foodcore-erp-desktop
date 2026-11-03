package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MovimientoInventario {
    private int idMovimientoInv;
    private long idInsumo;
    private String tipo; // ENTRADA, SALIDA, AJUSTE, MERMA, DEVOLUCION
    private BigDecimal cantidad;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoPosterior;
    private LocalDateTime fechaHora;
    private String motivo;
    private Integer referenciaId;

    public MovimientoInventario() {}

    public MovimientoInventario(int idMovimientoInv, long idInsumo, String tipo, BigDecimal cantidad, String motivo, Integer referenciaId) {
        this.idMovimientoInv = idMovimientoInv;
        this.idInsumo = idInsumo;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.motivo = motivo;
        this.referenciaId = referenciaId;
        this.fechaHora = LocalDateTime.now();
    }

    public int getIdMovimientoInv() { return idMovimientoInv; }
    public void setIdMovimientoInv(int idMovimientoInv) { this.idMovimientoInv = idMovimientoInv; }

    public long getIdInsumo() { return idInsumo; }
    public void setIdInsumo(long idInsumo) { this.idInsumo = idInsumo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }

    public BigDecimal getSaldoAnterior() { return saldoAnterior; }
    public void setSaldoAnterior(BigDecimal saldoAnterior) { this.saldoAnterior = saldoAnterior; }

    public BigDecimal getSaldoPosterior() { return saldoPosterior; }
    public void setSaldoPosterior(BigDecimal saldoPosterior) { this.saldoPosterior = saldoPosterior; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public Integer getReferenciaId() { return referenciaId; }
    public void setReferenciaId(Integer referenciaId) { this.referenciaId = referenciaId; }
}
