package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FilaPlanilla {
    private static final int ESCALA_MONETARIA = 2;
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

    private int idUsuario;
    private String nombreEmpleado;
    private String dni;
    private String rol;
    private int diasTrabajados;
    private BigDecimal horasTotales = CERO;
    private BigDecimal horasExtra = CERO;
    private int tardanzaMinutos;
    private BigDecimal sueldoBase = CERO;
    private BigDecimal descuentoTardanza = CERO;
    private BigDecimal bonoHorasExtra = CERO;
    private BigDecimal totalNetoPagar = CERO;

    public FilaPlanilla() {
    }

    private static BigDecimal normalizarDecimal(BigDecimal valor) {
        return valor != null ? valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP) : CERO;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreEmpleado() {
        return nombreEmpleado;
    }

    public void setNombreEmpleado(String nombreEmpleado) {
        this.nombreEmpleado = nombreEmpleado;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public int getDiasTrabajados() {
        return diasTrabajados;
    }

    public void setDiasTrabajados(int diasTrabajados) {
        this.diasTrabajados = diasTrabajados;
    }

    public BigDecimal getHorasTotales() {
        return horasTotales;
    }

    public void setHorasTotales(BigDecimal horasTotales) {
        this.horasTotales = normalizarDecimal(horasTotales);
    }

    public BigDecimal getHorasExtra() {
        return horasExtra;
    }

    public void setHorasExtra(BigDecimal horasExtra) {
        this.horasExtra = normalizarDecimal(horasExtra);
    }

    public int getTardanzaMinutos() {
        return tardanzaMinutos;
    }

    public void setTardanzaMinutos(int tardanzaMinutos) {
        this.tardanzaMinutos = tardanzaMinutos;
    }

    public BigDecimal getSueldoBase() {
        return sueldoBase;
    }

    public void setSueldoBase(BigDecimal sueldoBase) {
        this.sueldoBase = normalizarDecimal(sueldoBase);
    }

    public BigDecimal getDescuentoTardanza() {
        return descuentoTardanza;
    }

    public void setDescuentoTardanza(BigDecimal descuentoTardanza) {
        this.descuentoTardanza = normalizarDecimal(descuentoTardanza);
    }

    public BigDecimal getBonoHorasExtra() {
        return bonoHorasExtra;
    }

    public void setBonoHorasExtra(BigDecimal bonoHorasExtra) {
        this.bonoHorasExtra = normalizarDecimal(bonoHorasExtra);
    }

    public BigDecimal getTotalNetoPagar() {
        return totalNetoPagar;
    }

    public void setTotalNetoPagar(BigDecimal totalNetoPagar) {
        this.totalNetoPagar = normalizarDecimal(totalNetoPagar);
    }
}
