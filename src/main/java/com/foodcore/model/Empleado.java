package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Empleado {
    private int idEmpleado;
    private String dni;
    private String nombres;
    private String apellidos;
    private String telefono;
    private String cargo;
    private BigDecimal sueldoBase;
    private LocalDate fechaIngreso;
    private boolean estado;

    public Empleado() {}

    public Empleado(int idEmpleado, String dni, String nombres, String apellidos, String telefono, String cargo, BigDecimal sueldoBase, LocalDate fechaIngreso, boolean estado) {
        this.idEmpleado = idEmpleado;
        this.dni = dni;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.telefono = telefono;
        this.cargo = cargo;
        this.sueldoBase = sueldoBase;
        this.fechaIngreso = fechaIngreso;
        this.estado = estado;
    }

    public int getIdEmpleado() { return idEmpleado; }
    public void setIdEmpleado(int idEmpleado) { this.idEmpleado = idEmpleado; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public BigDecimal getSueldoBase() { return sueldoBase; }
    public void setSueldoBase(BigDecimal sueldoBase) { this.sueldoBase = sueldoBase; }

    public LocalDate getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(LocalDate fechaIngreso) { this.fechaIngreso = fechaIngreso; }

    public boolean isEstado() { return estado; }
    public void setEstado(boolean estado) { this.estado = estado; }

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    @Override
    public String toString() { return getNombreCompleto(); }
}
