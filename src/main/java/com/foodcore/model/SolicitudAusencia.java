package com.foodcore.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SolicitudAusencia {

    private int idSolicitud;
    private int idUsuario;
    private String nombreUsuario;
    private String tipo; // VACACIONES, PERMISO_MEDICO, ASUNTO_PERSONAL, LICENCIA
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String motivo;
    private String estado = "PENDIENTE"; // PENDIENTE, APROBADA, RECHAZADA
    private String observacionGerente;
    private LocalDateTime fechaSolicitud;

    public SolicitudAusencia() {
        this.fechaSolicitud = LocalDateTime.now();
    }

    public SolicitudAusencia(int idSolicitud, int idUsuario, String tipo, LocalDate fechaInicio, LocalDate fechaFin, String motivo, String estado) {
        this.idSolicitud = idSolicitud;
        this.idUsuario = idUsuario;
        this.tipo = tipo;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.motivo = motivo;
        this.estado = estado != null ? estado : "PENDIENTE";
        this.fechaSolicitud = LocalDateTime.now();
    }

    public int getIdSolicitud() {
        return idSolicitud;
    }

    public void setIdSolicitud(int idSolicitud) {
        this.idSolicitud = idSolicitud;
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

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getObservacionGerente() {
        return observacionGerente;
    }

    public void setObservacionGerente(String observacionGerente) {
        this.observacionGerente = observacionGerente;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }
}
