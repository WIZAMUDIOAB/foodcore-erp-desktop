package com.foodcore.model;

import java.time.LocalDateTime;

/**
 * Entidad representativa de un período mensual contable cerrado en {@code cierre_contable}.
 */
public class CierreContable {

    private int idCierre;
    private int anio;
    private int mes;
    private LocalDateTime fechaCierre;
    private int idUsuario;
    private String nombreUsuario;
    private String estado = "CERRADO";
    private String observaciones;

    public CierreContable() {
    }

    public CierreContable(int idCierre, int anio, int mes, LocalDateTime fechaCierre,
                          int idUsuario, String nombreUsuario, String estado, String observaciones) {
        this.idCierre = idCierre;
        this.anio = anio;
        this.mes = mes;
        this.fechaCierre = fechaCierre;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.estado = estado != null ? estado : "CERRADO";
        this.observaciones = observaciones;
    }

    public int getIdCierre() {
        return idCierre;
    }

    public void setIdCierre(int idCierre) {
        this.idCierre = idCierre;
    }

    public int getAnio() {
        return anio;
    }

    public void setAnio(int anio) {
        this.anio = anio;
    }

    public int getMes() {
        return mes;
    }

    public void setMes(int mes) {
        this.mes = mes;
    }

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
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

    public boolean estaCerrado() {
        return "CERRADO".equalsIgnoreCase(estado);
    }
}
