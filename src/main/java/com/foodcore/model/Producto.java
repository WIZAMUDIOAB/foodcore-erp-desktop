package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private boolean esMenuDelDia;
    private LocalDate fechaDisponibilidad;

    // Relación descriptiva y opciones configurables (v4.8.0)
    private String nombreCategoria;
    private boolean esCombo;
    private Integer idCombo;
    private List<ProductoVariante> variantes = new ArrayList<>();
    private List<ProductoModificador> modificadores = new ArrayList<>();
    private List<ComboDetalle> comboComponentes = new ArrayList<>();

    public Producto() {}

    public Producto(int idProducto, int idCategoria, String codigo, String nombre, String descripcion, BigDecimal precioVenta, BigDecimal costoEstimado, boolean disponible) {
        this(idProducto, idCategoria, codigo, nombre, descripcion, precioVenta, costoEstimado, disponible, null, false, null);
    }

    public Producto(int idProducto, int idCategoria, String codigo, String nombre, String descripcion, BigDecimal precioVenta, BigDecimal costoEstimado, boolean disponible, String imagenUrl) {
        this(idProducto, idCategoria, codigo, nombre, descripcion, precioVenta, costoEstimado, disponible, imagenUrl, false, null);
    }

    public Producto(int idProducto, int idCategoria, String codigo, String nombre, String descripcion, BigDecimal precioVenta, BigDecimal costoEstimado, boolean disponible, String imagenUrl, boolean esMenuDelDia, LocalDate fechaDisponibilidad) {
        this.idProducto = idProducto;
        this.idCategoria = idCategoria;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioVenta = precioVenta;
        this.costoEstimado = costoEstimado;
        this.disponible = disponible;
        this.imagenUrl = imagenUrl;
        this.esMenuDelDia = esMenuDelDia;
        this.fechaDisponibilidad = fechaDisponibilidad;
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

    public boolean isEsMenuDelDia() { return esMenuDelDia; }
    public void setEsMenuDelDia(boolean esMenuDelDia) { this.esMenuDelDia = esMenuDelDia; }

    public LocalDate getFechaDisponibilidad() { return fechaDisponibilidad; }
    public void setFechaDisponibilidad(LocalDate fechaDisponibilidad) { this.fechaDisponibilidad = fechaDisponibilidad; }

    public List<ProductoVariante> getVariantes() {
        return Collections.unmodifiableList(variantes);
    }

    public void setVariantes(List<ProductoVariante> variantes) {
        this.variantes = variantes != null ? new ArrayList<>(variantes) : new ArrayList<>();
    }

    public List<ProductoModificador> getModificadores() {
        return Collections.unmodifiableList(modificadores);
    }

    public void setModificadores(List<ProductoModificador> modificadores) {
        this.modificadores = modificadores != null ? new ArrayList<>(modificadores) : new ArrayList<>();
    }

    public boolean isEsCombo() { return esCombo; }
    public void setEsCombo(boolean esCombo) { this.esCombo = esCombo; }

    public Integer getIdCombo() { return idCombo; }
    public void setIdCombo(Integer idCombo) {
        this.idCombo = idCombo;
        if (idCombo != null && idCombo > 0) {
            this.esCombo = true;
        }
    }

    public List<ComboDetalle> getComboComponentes() {
        return Collections.unmodifiableList(comboComponentes);
    }

    public void setComboComponentes(List<ComboDetalle> comboComponentes) {
        this.comboComponentes = comboComponentes != null ? new ArrayList<>(comboComponentes) : new ArrayList<>();
        if (!this.comboComponentes.isEmpty()) {
            this.esCombo = true;
        }
    }

    public boolean tieneOpcionesPersonalizacion() {
        return !esCombo && (!variantes.isEmpty() || !modificadores.isEmpty());
    }

    @Override
    public String toString() { return nombre + " (S/ " + precioVenta + ")"; }
}
