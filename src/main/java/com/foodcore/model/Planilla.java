package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Planilla {
    private int idPlanilla;
    private int idEmpleado;
    private int mes;
    private int anio;
    private int diasTrabajados;
    private BigDecimal sueldoNeto;
    private LocalDate fechaPago;
    private String estado; // PENDIENTE, PAGADO

    private String nombreEmpleado;

    public Planilla() {}

    public Planilla(int idPlanilla, int idEmpleado, int mes, int anio, int diasTrabajados, BigDecimal sueldoNeto, LocalDate fechaPago, String estado) {
        this.idPlanilla = idPlanilla;
        this.idEmpleado = idEmpleado;
        this.mes = mes;
        this.anio = anio;
        this.diasTrabajados = diasTrabajados;
        this.sueldoNeto = sueldoNeto;
        this.fechaPago = fechaPago;
        this.estado = estado;
    }

    public int getIdPlanilla() { return idPlanilla; }
    public void setIdPlanilla(int idPlanilla) { this.idPlanilla = idPlanilla; }

    public int getIdEmpleado() { return idEmpleado; }
    public void setIdEmpleado(int idEmpleado) { this.idEmpleado = idEmpleado; }

    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public int getDiasTrabajados() { return diasTrabajados; }
    public void setDiasTrabajados(int diasTrabajados) { this.diasTrabajados = diasTrabajados; }

    public BigDecimal getSueldoNeto() { return sueldoNeto; }
    public void setSueldoNeto(BigDecimal sueldoNeto) { this.sueldoNeto = sueldoNeto; }

    public LocalDate getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDate fechaPago) { this.fechaPago = fechaPago; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getNombreEmpleado() { return nombreEmpleado; }
    public void setNombreEmpleado(String nombreEmpleado) { this.nombreEmpleado = nombreEmpleado; }
}
