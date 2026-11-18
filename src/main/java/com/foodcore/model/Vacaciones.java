package com.foodcore.model;

import java.time.LocalDate;

/**
 * Saldo administrativo de vacaciones por colaborador y año (sin cálculos legales).
 */
public class Vacaciones {

    private long idVacacion;
    private int idUsuario;
    private int periodoAnio;
    private int diasAsignados;
    private int diasGozados;
    private int diasPendientesAprobacion;
    private LocalDate fechaCorte;
    private String observaciones;

    public long getIdVacacion() {
        return idVacacion;
    }

    public void setIdVacacion(long idVacacion) {
        this.idVacacion = idVacacion;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public int getPeriodoAnio() {
        return periodoAnio;
    }

    public void setPeriodoAnio(int periodoAnio) {
        this.periodoAnio = periodoAnio;
    }

    public int getDiasAsignados() {
        return diasAsignados;
    }

    public void setDiasAsignados(int diasAsignados) {
        this.diasAsignados = diasAsignados;
    }

    public int getDiasGozados() {
        return diasGozados;
    }

    public void setDiasGozados(int diasGozados) {
        this.diasGozados = diasGozados;
    }

    public int getDiasPendientesAprobacion() {
        return diasPendientesAprobacion;
    }

    public void setDiasPendientesAprobacion(int diasPendientesAprobacion) {
        this.diasPendientesAprobacion = diasPendientesAprobacion;
    }

    public int getDiasDisponibles() {
        return Math.max(0, diasAsignados - diasGozados);
    }

    public int getDiasDisponiblesProyectados() {
        return Math.max(0, getDiasDisponibles() - diasPendientesAprobacion);
    }

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }

    public void setFechaCorte(LocalDate fechaCorte) {
        this.fechaCorte = fechaCorte;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
