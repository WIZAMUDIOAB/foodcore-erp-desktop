package com.foodcore.model;

import java.time.LocalDateTime;

public class CobroCliente {
    private int idCobroCliente;
    private int idCuentaCobrar;
    private Integer idCaja;
    private int idUsuario;
    private String nombreUsuario;
    private double monto;
    private String metodoPago;
    private LocalDateTime fechaCobro;
    private String nota;

    public CobroCliente() {
        this.metodoPago = "EFECTIVO";
    }

    public int getIdCobroCliente() {
        return idCobroCliente;
    }

    public void setIdCobroCliente(int idCobroCliente) {
        this.idCobroCliente = idCobroCliente;
    }

    public int getIdCuentaCobrar() {
        return idCuentaCobrar;
    }

    public void setIdCuentaCobrar(int idCuentaCobrar) {
        this.idCuentaCobrar = idCuentaCobrar;
    }

    public Integer getIdCaja() {
        return idCaja;
    }

    public void setIdCaja(Integer idCaja) {
        this.idCaja = idCaja;
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

    public double getMonto() {
        return monto;
    }

    public void setMonto(double monto) {
        this.monto = monto;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public LocalDateTime getFechaCobro() {
        return fechaCobro;
    }

    public void setFechaCobro(LocalDateTime fechaCobro) {
        this.fechaCobro = fechaCobro;
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }
}
