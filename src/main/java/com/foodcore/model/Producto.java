package com.foodcore.model;

import java.math.BigDecimal;

public class Producto {
    private int idProducto;
    private int idCategoria;
    private String codigo;
    private String nombre;
    private String descripcion;
    private BigDecimal precioVenta;
    private BigDecimal costoEstimado;
    private boolean disponible;
    private String imagenUrl;

    // Relación descriptiva
    private String nombreCategoria;

    public Producto() {}

    public Producto(int idProducto, int idCategoria, String codigo, String nombre, String descripcion, BigDecimal precioVenta, BigDecimal costoEstimado, boolean disponible) {
        this(idProducto, idCategoria, codigo, nombre, descripcion, precioVenta, costoEstimado, disponible, null);
    }

    public Producto(int idProducto, int idCategoria, String codigo, String nombre, String descripcion, BigDecimal precioVenta, BigDecimal costoEstimado, boolean disponible, String imagenUrl) {
        this.idProducto = idProducto;
        this.idCategoria = idCategoria;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioVenta = precioVenta;
        this.costoEstimado = costoEstimado;
        this.disponible = disponible;
        this.imagenUrl = imagenUrl;
    }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public int getIdCategoria() { return idCategoria; }
    public void setIdCategoria(int idCategoria) { this.idCategoria = idCategoria; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public BigDecimal getCostoEstimado() { return costoEstimado; }
    public void setCostoEstimado(BigDecimal costoEstimado) { this.costoEstimado = costoEstimado; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public String getNombreCategoria() { return nombreCategoria; }
    public void setNombreCategoria(String nombreCategoria) { this.nombreCategoria = nombreCategoria; }

    @Override
    public String toString() { return nombre + " (S/ " + precioVenta + ")"; }
}
