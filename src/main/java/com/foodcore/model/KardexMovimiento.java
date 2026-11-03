package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad inmutable de movimiento de Kárdex según SPEC-FOODCORE-v1.0 (§5 y §8.2).
 */
public class KardexMovimiento {
    private long idKardex;
    private long idInsumo;
    private String tipoMovimiento; // ENTRADA|SALIDA|AJUSTE|MERMA|DEVOLUCION
    private BigDecimal cantidad;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoPosterior;
    private BigDecimal costoUnitario;
    private String referenciaTipo;
    private Long referenciaId;
    private long idUsuario;
    private String observacion;
    private LocalDateTime createdAt;

    public KardexMovimiento() {}

    public KardexMovimiento(long idKardex, long idInsumo, String tipoMovimiento,
                            BigDecimal cantidad, BigDecimal saldoAnterior, BigDecimal saldoPosterior,
                            BigDecimal costoUnitario, String referenciaTipo, Long referenciaId,
                            long idUsuario, String observacion, LocalDateTime createdAt) {
        this.idKardex = idKardex;
        this.idInsumo = idInsumo;
        this.tipoMovimiento = tipoMovimiento;
        this.cantidad = cantidad;
        this.saldoAnterior = saldoAnterior;
        this.saldoPosterior = saldoPosterior;
        this.costoUnitario = costoUnitario;
        this.referenciaTipo = referenciaTipo;
        this.referenciaId = referenciaId;
        this.idUsuario = idUsuario;
        this.observacion = observacion;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public long getIdKardex() { return idKardex; }
    public void setIdKardex(long idKardex) { this.idKardex = idKardex; }

    public long getIdInsumo() { return idInsumo; }
    public void setIdInsumo(long idInsumo) { this.idInsumo = idInsumo; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }

    public BigDecimal getSaldoAnterior() { return saldoAnterior; }
    public void setSaldoAnterior(BigDecimal saldoAnterior) { this.saldoAnterior = saldoAnterior; }

    public BigDecimal getSaldoPosterior() { return saldoPosterior; }
    public void setSaldoPosterior(BigDecimal saldoPosterior) { this.saldoPosterior = saldoPosterior; }

    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }

    public String getReferenciaTipo() { return referenciaTipo; }
    public void setReferenciaTipo(String referenciaTipo) { this.referenciaTipo = referenciaTipo; }

    public Long getReferenciaId() { return referenciaId; }
    public void setReferenciaId(Long referenciaId) { this.referenciaId = referenciaId; }

    public long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(long idUsuario) { this.idUsuario = idUsuario; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
