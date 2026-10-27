package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Caja {
    private int idCaja;
    private int idUsuarioApertura;
    private Integer idUsuarioCierre;
    private String nombreCaja;
    private BigDecimal montoApertura;
    private BigDecimal montoCierre;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private String estado; // ABIERTA, CERRADA
    private BigDecimal diferencia;

    public Caja() {}

    public Caja(int idCaja, int idUsuarioApertura, String nombreCaja, BigDecimal montoApertura, String estado) {
        this.idCaja = idCaja;
        this.idUsuarioApertura = idUsuarioApertura;
        this.nombreCaja = nombreCaja;
        this.montoApertura = montoApertura;
        this.estado = estado;
        this.fechaApertura = LocalDateTime.now();
    }

    public int getIdCaja() { return idCaja; }
    public void setIdCaja(int idCaja) { this.idCaja = idCaja; }

    public int getIdUsuarioApertura() { return idUsuarioApertura; }
    public void setIdUsuarioApertura(int idUsuarioApertura) { this.idUsuarioApertura = idUsuarioApertura; }

    public Integer getIdUsuarioCierre() { return idUsuarioCierre; }
    public void setIdUsuarioCierre(Integer idUsuarioCierre) { this.idUsuarioCierre = idUsuarioCierre; }

    public String getNombreCaja() { return nombreCaja; }
    public void setNombreCaja(String nombreCaja) { this.nombreCaja = nombreCaja; }

    public BigDecimal getMontoApertura() { return montoApertura; }
    public void setMontoApertura(BigDecimal montoApertura) { this.montoApertura = montoApertura; }

    public BigDecimal getMontoCierre() { return montoCierre; }
    public void setMontoCierre(BigDecimal montoCierre) { this.montoCierre = montoCierre; }

    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }

    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getDiferencia() { return diferencia; }
    public void setDiferencia(BigDecimal diferencia) { this.diferencia = diferencia; }

    @Override
    public String toString() { return nombreCaja + " [" + estado + "]"; }
}
