package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paquete o combo compuesto por múltiples productos con precio final propio y descuento atómico de stock (v4.8.0 Módulo 3).
 */
public class Combo {
    private int idCombo;
    private Integer idProducto;
    private int idCategoria;
    private String codigo;
    private String nombre;
    private BigDecimal precioCombo;
    private boolean activo = true;
    private LocalDateTime creadoEn;
    private List<ComboDetalle> detalles = new ArrayList<>();

    public Combo() {
    }

    public Combo(int idCombo, Integer idProducto, String nombre, BigDecimal precioCombo, boolean activo) {
        this.idCombo = idCombo;
        this.idProducto = idProducto;
        this.nombre = nombre;
        this.precioCombo = precioCombo;
        this.activo = activo;
    }

    public int getIdCombo() {
        return idCombo;
    }

    public void setIdCombo(int idCombo) {
        this.idCombo = idCombo;
    }

    public Integer getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(Integer idProducto) {
        this.idProducto = idProducto;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(int idCategoria) {
        this.idCategoria = idCategoria;
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

    public BigDecimal getPrecioCombo() {
        return precioCombo;
    }

    public void setPrecioCombo(BigDecimal precioCombo) {
        this.precioCombo = precioCombo;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public List<ComboDetalle> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    public void setDetalles(List<ComboDetalle> detalles) {
        this.detalles = detalles != null ? new ArrayList<>(detalles) : new ArrayList<>();
    }

    public BigDecimal getPrecioComboNormalizado() {
        return precioCombo != null
                ? precioCombo.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return (nombre != null ? nombre : "Combo") + " (S/ " + getPrecioComboNormalizado() + ")";
    }
}
