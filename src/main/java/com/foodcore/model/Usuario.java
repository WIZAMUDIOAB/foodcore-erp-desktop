package com.foodcore.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Usuario {
    private int idUsuario;
    private int idEmpleado;
    private int idRol;
    private String username;
    private String passwordHash;
    private boolean activo;
    private int intentosFallidos;
    private LocalDateTime bloqueadoHasta;
    private boolean cambioPasswordRequerido;
    private LocalDateTime ultimoLogin;
    private LocalDateTime fechaCreacion;

    // Relaciones descriptivas
    private String nombreRol;
    private String nombreEmpleado;

    // Campos RRHH (SPEC Sub-Fase 4B & v4.4.0)
    private String dni;
    private String pin;
    private String pinHash;
    private double sueldoBase = 1200.00;
    private String horarioLaboral = "08:00-16:00";
    private LocalTime horaEntradaProgramada = LocalTime.of(8, 0);
    private LocalTime horaSalidaProgramada = LocalTime.of(16, 0);
    private LocalDate fechaVencimientoContrato;
    private LocalDate fechaVencimientoCarnetSanidad;

    // Campos de Seguridad v5.1.0 (2FA TOTP e invalidación de sesiones JWT)
    private String totpSecret;
    private boolean totpHabilitado;
    private long tokensInvalidadosAntes;

    public Usuario() {
    }

    public Usuario(int idUsuario, int idEmpleado, int idRol, String username, String passwordHash, boolean activo) {
        this.idUsuario = idUsuario;
        this.idEmpleado = idEmpleado;
        this.idRol = idRol;
        this.username = username;
        this.passwordHash = passwordHash;
        this.activo = activo;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public int getIdEmpleado() {
        return idEmpleado;
    }

    public void setIdEmpleado(int idEmpleado) {
        this.idEmpleado = idEmpleado;
    }

    public int getIdRol() {
        return idRol;
    }

    public void setIdRol(int idRol) {
        this.idRol = idRol;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public void setIntentosFallidos(int intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public boolean isCambioPasswordRequerido() {
        return cambioPasswordRequerido;
    }

    public void setCambioPasswordRequerido(boolean cambioPasswordRequerido) {
        this.cambioPasswordRequerido = cambioPasswordRequerido;
    }

    public LocalDateTime getUltimoLogin() {
        return ultimoLogin;
    }

    public void setUltimoLogin(LocalDateTime ultimoLogin) {
        this.ultimoLogin = ultimoLogin;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getNombreRol() {
        return nombreRol;
    }

    public void setNombreRol(String nombreRol) {
        this.nombreRol = nombreRol;
    }

    public String getNombreEmpleado() {
        return nombreEmpleado;
    }

    public void setNombreEmpleado(String nombreEmpleado) {
        this.nombreEmpleado = nombreEmpleado;
    }

    public String getDni() { return dni; }

    public void setDni(String dni) { this.dni = dni; }

    public String getPin() { return pin; }

    public void setPin(String pin) { this.pin = pin; }

    public String getPinHash() { return pinHash; }

    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public boolean isPinConfigurado() {
        return (pinHash != null && !pinHash.isBlank()) || (pin != null && !pin.isBlank());
    }

    public double getSueldoBase() { return sueldoBase; }

    public void setSueldoBase(double sueldoBase) { this.sueldoBase = sueldoBase; }

    public String getHorarioLaboral() { return horarioLaboral; }

    public void setHorarioLaboral(String horarioLaboral) { this.horarioLaboral = horarioLaboral; }

    public LocalTime getHoraEntradaProgramada() { return horaEntradaProgramada; }

    public void setHoraEntradaProgramada(LocalTime horaEntradaProgramada) { this.horaEntradaProgramada = horaEntradaProgramada; }

    public LocalTime getHoraSalidaProgramada() { return horaSalidaProgramada; }

    public void setHoraSalidaProgramada(LocalTime horaSalidaProgramada) { this.horaSalidaProgramada = horaSalidaProgramada; }

    public LocalDate getFechaVencimientoContrato() { return fechaVencimientoContrato; }

    public void setFechaVencimientoContrato(LocalDate fechaVencimientoContrato) { this.fechaVencimientoContrato = fechaVencimientoContrato; }

    public LocalDate getFechaVencimientoCarnetSanidad() { return fechaVencimientoCarnetSanidad; }

    public void setFechaVencimientoCarnetSanidad(LocalDate fechaVencimientoCarnetSanidad) { this.fechaVencimientoCarnetSanidad = fechaVencimientoCarnetSanidad; }

    private String tipoDocumento;
    private String direccion;
    private String telefono;
    private String telefonoEmergencia;
    private String contactoEmergencia;
    private LocalDate fechaNacimiento;
    private String tipoContrato;
    private LocalDate fechaIngreso;
    private String cargo;

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getTelefonoEmergencia() {
        return telefonoEmergencia;
    }

    public void setTelefonoEmergencia(String telefonoEmergencia) {
        this.telefonoEmergencia = telefonoEmergencia;
    }

    public String getContactoEmergencia() {
        return contactoEmergencia;
    }

    public void setContactoEmergencia(String contactoEmergencia) {
        this.contactoEmergencia = contactoEmergencia;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getTipoContrato() {
        return tipoContrato;
    }

    public void setTipoContrato(String tipoContrato) {
        this.tipoContrato = tipoContrato;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public boolean isTotpHabilitado() {
        return totpHabilitado;
    }

    public void setTotpHabilitado(boolean totpHabilitado) {
        this.totpHabilitado = totpHabilitado;
    }

    public long getTokensInvalidadosAntes() {
        return tokensInvalidadosAntes;
    }

    public void setTokensInvalidadosAntes(long tokensInvalidadosAntes) {
        this.tokensInvalidadosAntes = tokensInvalidadosAntes;
    }

    @Override
    public String toString() {
        return username;
    }
}
