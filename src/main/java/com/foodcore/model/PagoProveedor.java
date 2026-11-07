package com.foodcore.model;

import java.time.LocalDateTime;

public class PagoProveedor {
    private int idPagoProveedor;
    private int idCuentaPagar;
    private Integer idCaja;
    private int idUsuario;
    private String nombreUsuario;
    private double monto;
    private String metodoPago;
    private LocalDateTime fechaPago;
    private String nota;

    public PagoProveedor() {
        this.metodoPago = "EFECTIVO";
    }

    public int getIdPagoProveedor() {
        return idPagoProveedor;
    }

    public void setIdPagoProveedor(int idPagoProveedor) {
        this.idPagoProveedor = idPagoProveedor;
    }

    public int getIdCuentaPagar() {
        return idCuentaPagar;
    }

    public void setIdCuentaPagar(int idCuentaPagar) {
        this.idCuentaPagar = idCuentaPagar;
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

    public LocalDateTime getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDateTime fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }
}
