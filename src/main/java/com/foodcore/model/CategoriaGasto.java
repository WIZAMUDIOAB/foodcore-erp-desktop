package com.foodcore.model;

/**
 * Categoría contable/operativa para egresos de caja y Estado de Resultados (P&amp;L) (v4.9.0).
 */
public class CategoriaGasto {

    private int idCategoriaGasto;
    private String codigo;
    private String nombre;
    private String descripcion;
    private boolean activo;

    public CategoriaGasto() {
        this.activo = true;
    }

    public CategoriaGasto(int idCategoriaGasto, String codigo, String nombre, String descripcion, boolean activo) {
        this.idCategoriaGasto = idCategoriaGasto;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activo = activo;
    }

    public int getIdCategoriaGasto() {
        return idCategoriaGasto;
    }

    public void setIdCategoriaGasto(int idCategoriaGasto) {
        this.idCategoriaGasto = idCategoriaGasto;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return nombre != null ? nombre : "";
    }
}
