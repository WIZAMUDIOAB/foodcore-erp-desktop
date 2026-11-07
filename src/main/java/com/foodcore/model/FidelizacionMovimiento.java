package com.foodcore.model;

import java.time.LocalDateTime;

public class FidelizacionMovimiento {
    private int idMovimiento;
    private int idCliente;
    private String nombreCliente;
    private Integer idVenta;
    private String tipo; // ACUMULACION, CANJE, AJUSTE
    private int puntos;
    private String motivo;
    private LocalDateTime fechaHora;

    public FidelizacionMovimiento() {
        this.tipo = "ACUMULACION";
        this.fechaHora = LocalDateTime.now();
    }

    public FidelizacionMovimiento(int idMovimiento, int idCliente, Integer idVenta,
                                  String tipo, int puntos, String motivo,
                                  LocalDateTime fechaHora) {
        this.idMovimiento = idMovimiento;
        this.idCliente = idCliente;
        this.idVenta = idVenta;
        this.tipo = tipo;
        this.puntos = puntos;
        this.motivo = motivo;
        this.fechaHora = fechaHora != null ? fechaHora : LocalDateTime.now();
    }

    public int getIdMovimiento() {
        return idMovimiento;
    }

    public void setIdMovimiento(int idMovimiento) {
        this.idMovimiento = idMovimiento;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(int idCliente) {
        this.idCliente = idCliente;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public Integer getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(Integer idVenta) {
        this.idVenta = idVenta;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public int getPuntos() {
        return puntos;
    }

    public void setPuntos(int puntos) {
        this.puntos = puntos;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
