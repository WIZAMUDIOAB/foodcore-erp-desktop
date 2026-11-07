package com.foodcore.model;

import java.time.LocalDateTime;

public class Proveedor {
    private int idProveedor;
    private String ruc;
    private String razonSocial;
    private String contacto;
    private String telefono;
    private String email;
    private int diasCredito;
    private String estado; // ACTIVO, INACTIVO
    private boolean activo;
    private LocalDateTime fechaRegistro;

    // Métricas auxiliares para UI
    private double deudaActual;
    private int facturasPendientes;

    public Proveedor() {
        this.estado = "ACTIVO";
        this.activo = true;
    }

    public int getIdProveedor() {
        return idProveedor;
    }

    public void setIdProveedor(int idProveedor) {
        this.idProveedor = idProveedor;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
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

    public int getDiasCredito() {
        return diasCredito;
    }

    public void setDiasCredito(int diasCredito) {
        this.diasCredito = diasCredito;
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

    public double getDeudaActual() {
        return deudaActual;
    }

    public void setDeudaActual(double deudaActual) {
        this.deudaActual = deudaActual;
    }

    public int getFacturasPendientes() {
        return facturasPendientes;
    }

    public void setFacturasPendientes(int facturasPendientes) {
        this.facturasPendientes = facturasPendientes;
    }

    @Override
    public String toString() {
        return razonSocial + " (" + ruc + ")";
    }
}
