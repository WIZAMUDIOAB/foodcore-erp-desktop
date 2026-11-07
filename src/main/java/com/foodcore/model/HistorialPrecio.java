package com.foodcore.model;

import java.time.LocalDateTime;

public class HistorialPrecio {
    private int idHistorial;
    private String tipoEntidad; // PRODUCTO o INSUMO
    private int idEntidad;
    private String nombreEntidad;
    private double precioAnterior;
    private double precioNuevo;
    private int idUsuario;
    private String nombreUsuario;
    private String motivo;
    private LocalDateTime fechaCambio;

    public HistorialPrecio() {
    }

    public double getVariacionPorcentual() {
        if (precioAnterior <= 0) {
            return 0.0;
        }
        return ((precioNuevo - precioAnterior) / precioAnterior) * 100.0;
    }

    public int getIdHistorial() {
        return idHistorial;
    }

    public void setIdHistorial(int idHistorial) {
        this.idHistorial = idHistorial;
    }

    public String getTipoEntidad() {
        return tipoEntidad;
    }

    public void setTipoEntidad(String tipoEntidad) {
        this.tipoEntidad = tipoEntidad;
    }

    public int getIdEntidad() {
        return idEntidad;
    }

    public void setIdEntidad(int idEntidad) {
        this.idEntidad = idEntidad;
    }

    public String getNombreEntidad() {
        return nombreEntidad;
    }

    public void setNombreEntidad(String nombreEntidad) {
        this.nombreEntidad = nombreEntidad;
    }

    public double getPrecioAnterior() {
        return precioAnterior;
    }

    public void setPrecioAnterior(double precioAnterior) {
        this.precioAnterior = precioAnterior;
    }

    public double getPrecioNuevo() {
        return precioNuevo;
    }

    public void setPrecioNuevo(double precioNuevo) {
        this.precioNuevo = precioNuevo;
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

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaCambio() {
        return fechaCambio;
    }

    public void setFechaCambio(LocalDateTime fechaCambio) {
        this.fechaCambio = fechaCambio;
    }
}
