package com.foodcore.model;

import java.time.LocalDateTime;

public class Usuario {
    private int idUsuario;
    private int idEmpleado;
    private int idRol;
    private String username;
    private String passwordHash;
    private boolean activo;
    private LocalDateTime fechaCreacion;

    // Relaciones descriptivas
    private String nombreRol;
    private String nombreEmpleado;

    public Usuario() {}

    public Usuario(int idUsuario, int idEmpleado, int idRol, String username, String passwordHash, boolean activo) {
        this.idUsuario = idUsuario;
        this.idEmpleado = idEmpleado;
        this.idRol = idRol;
        this.username = username;
        this.passwordHash = passwordHash;
        this.activo = activo;
    }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public int getIdEmpleado() { return idEmpleado; }
    public void setIdEmpleado(int idEmpleado) { this.idEmpleado = idEmpleado; }

    public int getIdRol() { return idRol; }
    public void setIdRol(int idRol) { this.idRol = idRol; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getNombreRol() { return nombreRol; }
    public void setNombreRol(String nombreRol) { this.nombreRol = nombreRol; }

    public String getNombreEmpleado() { return nombreEmpleado; }
    public void setNombreEmpleado(String nombreEmpleado) { this.nombreEmpleado = nombreEmpleado; }

    @Override
    public String toString() { return username; }
}
