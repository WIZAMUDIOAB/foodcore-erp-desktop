package com.foodcore.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class TurnoProgramado {
    private int idTurnoProg;
    private int idUsuario;
    private String nombreUsuario;
    private String rol;
    private String diaSemana;
    private LocalDate fechaEspecifica;
    private String turno; // MANANA, TARDE, NOCHE, PERSONALIZADO
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private boolean publicado = true;

    public TurnoProgramado() {
    }

    public TurnoProgramado(int idTurnoProg, int idUsuario, String diaSemana, String turno, LocalTime horaInicio, LocalTime horaFin) {
        this.idTurnoProg = idTurnoProg;
        this.idUsuario = idUsuario;
        this.diaSemana = diaSemana;
        this.turno = turno;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    public int getIdTurnoProg() {
        return idTurnoProg;
    }

    public void setIdTurnoProg(int idTurnoProg) {
        this.idTurnoProg = idTurnoProg;
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

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(String diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalDate getFechaEspecifica() {
        return fechaEspecifica;
    }

    public void setFechaEspecifica(LocalDate fechaEspecifica) {
        this.fechaEspecifica = fechaEspecifica;
    }

    public String getTurno() {
        return turno;
    }

    public void setTurno(String turno) {
        this.turno = turno;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public boolean isPublicado() {
        return publicado;
    }

    public void setPublicado(boolean publicado) {
        this.publicado = publicado;
    }

    public String getHorarioTexto() {
        return (horaInicio != null ? horaInicio.toString().substring(0, 5) : "--:--") + " - "
                + (horaFin != null ? horaFin.toString().substring(0, 5) : "--:--");
    }
}
