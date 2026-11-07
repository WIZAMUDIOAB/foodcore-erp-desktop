package com.foodcore.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Asistencia {
    private int idAsistencia;
    private int idEmpleado;
    private int idUsuario;
    private LocalDate fecha;
    private LocalTime horaIngreso;
    private LocalTime horaSalida;
    private LocalDateTime horaEntrada;
    private LocalDateTime horaSalidaTimestamp;
    private double horasTrabajadas;
    private double horasExtra;
    private int tardanzaMinutos;
    private String estado = "PRESENTE"; // PRESENTE, TARDE, FINALIZADO, ASISTIO
    private String tipoMarcaje = "DNI"; // DNI, PIN
    private String observaciones;

    // Campos de visualización
    private String nombreEmpleado;
    private String dniEmpleado;
    private String nombreUsuario;
    private String rol;

    public Asistencia() {
    }

    public Asistencia(int idAsistencia, int idEmpleado, LocalDate fecha, LocalTime horaIngreso, LocalTime horaSalida, String estado) {
        this.idAsistencia = idAsistencia;
        this.idEmpleado = idEmpleado;
        this.idUsuario = idEmpleado;
        this.fecha = fecha;
        this.horaIngreso = horaIngreso;
        this.horaSalida = horaSalida;
        this.estado = estado;
    }

    public int getIdAsistencia() {
        return idAsistencia;
    }

    public void setIdAsistencia(int idAsistencia) {
        this.idAsistencia = idAsistencia;
    }

    public int getIdEmpleado() {
        return idEmpleado > 0 ? idEmpleado : idUsuario;
    }

    public void setIdEmpleado(int idEmpleado) {
        this.idEmpleado = idEmpleado;
        if (this.idUsuario <= 0) {
            this.idUsuario = idEmpleado;
        }
    }

    public int getIdUsuario() {
        return idUsuario > 0 ? idUsuario : idEmpleado;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
        if (this.idEmpleado <= 0) {
            this.idEmpleado = idUsuario;
        }
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHoraIngreso() {
        if (horaIngreso != null) {
            return horaIngreso;
        }
        return horaEntrada != null ? horaEntrada.toLocalTime() : null;
    }

    public void setHoraIngreso(LocalTime horaIngreso) {
        this.horaIngreso = horaIngreso;
    }

    public LocalTime getHoraSalida() {
        if (horaSalida != null) {
            return horaSalida;
        }
        return horaSalidaTimestamp != null ? horaSalidaTimestamp.toLocalTime() : null;
    }

    public void setHoraSalida(LocalTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public LocalDateTime getHoraEntrada() {
        return horaEntrada;
    }

    public void setHoraEntrada(LocalDateTime horaEntrada) {
        this.horaEntrada = horaEntrada;
        if (horaEntrada != null && this.horaIngreso == null) {
            this.horaIngreso = horaEntrada.toLocalTime();
        }
    }

    public LocalDateTime getHoraSalidaTimestamp() {
        return horaSalidaTimestamp;
    }

    public void setHoraSalidaTimestamp(LocalDateTime horaSalidaTimestamp) {
        this.horaSalidaTimestamp = horaSalidaTimestamp;
        if (horaSalidaTimestamp != null && this.horaSalida == null) {
            this.horaSalida = horaSalidaTimestamp.toLocalTime();
        }
    }

    public double getHorasTrabajadas() {
        return horasTrabajadas;
    }

    public void setHorasTrabajadas(double horasTrabajadas) {
        this.horasTrabajadas = horasTrabajadas;
    }

    public double getHorasExtra() {
        return horasExtra;
    }

    public void setHorasExtra(double horasExtra) {
        this.horasExtra = horasExtra;
    }

    public int getTardanzaMinutos() {
        return tardanzaMinutos;
    }

    public void setTardanzaMinutos(int tardanzaMinutos) {
        this.tardanzaMinutos = tardanzaMinutos;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getTipoMarcaje() {
        return tipoMarcaje;
    }

    public void setTipoMarcaje(String tipoMarcaje) {
        this.tipoMarcaje = tipoMarcaje;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getNombreEmpleado() {
        return nombreEmpleado != null ? nombreEmpleado : nombreUsuario;
    }

    public void setNombreEmpleado(String nombreEmpleado) {
        this.nombreEmpleado = nombreEmpleado;
    }

    public String getDniEmpleado() {
        return dniEmpleado;
    }

    public void setDniEmpleado(String dniEmpleado) {
        this.dniEmpleado = dniEmpleado;
    }

    public String getDni() {
        return dniEmpleado;
    }

    public void setDni(String dni) {
        this.dniEmpleado = dni;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}
