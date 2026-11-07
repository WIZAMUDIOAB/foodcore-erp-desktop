package com.foodcore.model;

public class FilaPlanilla {
    private int idUsuario;
    private String nombreEmpleado;
    private String dni;
    private String rol;
    private int diasTrabajados;
    private double horasTotales;
    private double horasExtra;
    private int tardanzaMinutos;
    private double sueldoBase;
    private double descuentoTardanza;
    private double bonoHorasExtra;
    private double totalNetoPagar;

    public FilaPlanilla() {
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

    public double getHorasTotales() {
        return horasTotales;
    }

    public void setHorasTotales(double horasTotales) {
        this.horasTotales = horasTotales;
    }

    public double getHorasExtra() {
        return horasExtra;
    }

    public void setHorasExtra(double horasExtra) {
        this.horasExtra = horasExtra;
    }

    public int getTardanzaMinutos() {
        return tardanzaMinutos;
    }

    public void setTardanzaMinutos(int tardanzaMinutos) {
        this.tardanzaMinutos = tardanzaMinutos;
    }

    public double getSueldoBase() {
        return sueldoBase;
    }

    public void setSueldoBase(double sueldoBase) {
        this.sueldoBase = sueldoBase;
    }

    public double getDescuentoTardanza() {
        return descuentoTardanza;
    }

    public void setDescuentoTardanza(double descuentoTardanza) {
        this.descuentoTardanza = descuentoTardanza;
    }

    public double getBonoHorasExtra() {
        return bonoHorasExtra;
    }

    public void setBonoHorasExtra(double bonoHorasExtra) {
        this.bonoHorasExtra = bonoHorasExtra;
    }

    public double getTotalNetoPagar() {
        return totalNetoPagar;
    }

    public void setTotalNetoPagar(double totalNetoPagar) {
        this.totalNetoPagar = totalNetoPagar;
    }
}
