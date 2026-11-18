package com.foodcore.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Cliente {
    private int idCliente;
    private String tipoDocumento; // RUC, DNI
    private String tipoCliente; // CORPORATIVO, MINORISTA
    private String numeroDocumento;
    private String razonSocial;
    private String contacto;
    private String telefono;
    private String email;
    private double limiteCredito;
    private int diasCredito;
    private int puntosFidelidad;
    private int puntosAcumulados;
    private LocalDate fechaNacimiento;
    private String estado; // ACTIVO, INACTIVO
    private boolean activo;
    private LocalDateTime fechaRegistro;

    // Métricas auxiliares para UI
    private double saldoDeudor;
    private int comprobantesPendientes;

    public Cliente() {
        this.tipoDocumento = "RUC";
        this.tipoCliente = "CORPORATIVO";
        this.estado = "ACTIVO";
        this.activo = true;
        this.diasCredito = 30;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(int idCliente) {
        this.idCliente = idCliente;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getContacto() {
        return contacto;
    }

    public void setContacto(String contacto) {
        this.contacto = contacto;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public double getLimiteCredito() {
        return limiteCredito;
    }

    public void setLimiteCredito(double limiteCredito) {
        this.limiteCredito = limiteCredito;
    }

    public int getDiasCredito() {
        return diasCredito;
    }

    public void setDiasCredito(int diasCredito) {
        this.diasCredito = diasCredito;
    }

    public int getPuntosFidelidad() {
        return puntosFidelidad;
    }

    public void setPuntosFidelidad(int puntosFidelidad) {
        this.puntosFidelidad = puntosFidelidad;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public double getSaldoDeudor() {
        return saldoDeudor;
    }

    public void setSaldoDeudor(double saldoDeudor) {
        this.saldoDeudor = saldoDeudor;
    }

    public int getComprobantesPendientes() {
        return comprobantesPendientes;
    }

    public void setComprobantesPendientes(int comprobantesPendientes) {
        this.comprobantesPendientes = comprobantesPendientes;
    }

    @Override
    public String toString() {
        return razonSocial + " (" + numeroDocumento + ")";
    }

    public int getPuntosAcumulados() {
        return puntosAcumulados > 0 ? puntosAcumulados : puntosFidelidad;
    }

    public void setPuntosAcumulados(int puntosAcumulados) {
        this.puntosAcumulados = puntosAcumulados;
        this.puntosFidelidad = puntosAcumulados;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public boolean esMesDeCumpleanos() {
        if (fechaNacimiento == null) {
            return false;
        }
        return fechaNacimiento.getMonthValue() == LocalDate.now().getMonthValue();
    }

    public String getTipoCliente() {
        if (tipoCliente == null || tipoCliente.isBlank()) {
            return "DNI".equalsIgnoreCase(tipoDocumento) ? "MINORISTA" : "CORPORATIVO";
        }
        return tipoCliente;
    }

    public void setTipoCliente(String tipoCliente) {
        this.tipoCliente = tipoCliente;
    }

    public boolean isMinorista() {
        return "MINORISTA".equalsIgnoreCase(getTipoCliente());
    }

    public String getNombreCompleto() {
        return razonSocial;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.razonSocial = nombreCompleto;
    }

}
