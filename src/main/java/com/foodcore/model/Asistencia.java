package com.foodcore.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Asistencia {
    private int idAsistencia;
    private int idEmpleado;
    private LocalDate fecha;
    private LocalTime horaIngreso;
    private LocalTime horaSalida;
    private String estado; // ASISTIO, TARDANZA, FALTA

    private String nombreEmpleado;
    private String dniEmpleado;

    public Asistencia() {}

    public Asistencia(int idAsistencia, int idEmpleado, LocalDate fecha, LocalTime horaIngreso, LocalTime horaSalida, String estado) {
        this.idAsistencia = idAsistencia;
        this.idEmpleado = idEmpleado;
        this.fecha = fecha;
        this.horaIngreso = horaIngreso;
        this.horaSalida = horaSalida;
        this.estado = estado;
    }

    public int getIdAsistencia() { return idAsistencia; }
    public void setIdAsistencia(int idAsistencia) { this.idAsistencia = idAsistencia; }

    public int getIdEmpleado() { return idEmpleado; }
    public void setIdEmpleado(int idEmpleado) { this.idEmpleado = idEmpleado; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHoraIngreso() { return horaIngreso; }
    public void setHoraIngreso(LocalTime horaIngreso) { this.horaIngreso = horaIngreso; }

    public LocalTime getHoraSalida() { return horaSalida; }
    public void setHoraSalida(LocalTime horaSalida) { this.horaSalida = horaSalida; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getNombreEmpleado() { return nombreEmpleado; }
    public void setNombreEmpleado(String nombreEmpleado) { this.nombreEmpleado = nombreEmpleado; }

    public String getDniEmpleado() { return dniEmpleado; }
    public void setDniEmpleado(String dniEmpleado) { this.dniEmpleado = dniEmpleado; }
}
