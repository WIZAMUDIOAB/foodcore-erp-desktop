package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CuponCumpleanos {
    private int idCupon;
    private int idCliente;
    private String nombreCliente;
    private String codigoCupon;
    private BigDecimal porcentajeDescuento;
    private int mesValidez;
    private boolean usado;
    private LocalDateTime fechaGeneracion;

    public CuponCumpleanos() {
        this.porcentajeDescuento = new BigDecimal("15.00");
        this.usado = false;
        this.fechaGeneracion = LocalDateTime.now();
    }

    public CuponCumpleanos(int idCupon, int idCliente, String codigoCupon,
                           BigDecimal porcentajeDescuento, int mesValidez,
                           boolean usado, LocalDateTime fechaGeneracion) {
        this.idCupon = idCupon;
        this.idCliente = idCliente;
        this.codigoCupon = codigoCupon;
        this.porcentajeDescuento = porcentajeDescuento != null ? porcentajeDescuento : new BigDecimal("15.00");
        this.mesValidez = mesValidez;
        this.usado = usado;
        this.fechaGeneracion = fechaGeneracion != null ? fechaGeneracion : LocalDateTime.now();
    }

    public int getIdCupon() {
        return idCupon;
    }

    public void setIdCupon(int idCupon) {
        this.idCupon = idCupon;
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

    public String getCodigoCupon() {
        return codigoCupon;
    }

    public void setCodigoCupon(String codigoCupon) {
        this.codigoCupon = codigoCupon;
    }

    public BigDecimal getPorcentajeDescuento() {
        return porcentajeDescuento;
    }

    public void setPorcentajeDescuento(BigDecimal porcentajeDescuento) {
        this.porcentajeDescuento = porcentajeDescuento;
    }

    public int getMesValidez() {
        return mesValidez;
    }

    public void setMesValidez(int mesValidez) {
        this.mesValidez = mesValidez;
    }

    public boolean isUsado() {
        return usado;
    }

    public void setUsado(boolean usado) {
        this.usado = usado;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(LocalDateTime fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }
}
