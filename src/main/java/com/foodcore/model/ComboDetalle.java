package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Componente individual incluido dentro de un {@link Combo} (v4.8.0 Módulo 3).
 */
public class ComboDetalle {
    private int idComboDetalle;
    private int idCombo;
    private int idProducto;
    private int cantidad = 1;

    // Campos descriptivos para UI Backoffice / KDS / Tickets
    private String codigoProducto;
    private String nombreProducto;
    private BigDecimal precioUnitarioReferencia;

    public ComboDetalle() {
    }

    public ComboDetalle(int idComboDetalle, int idCombo, int idProducto, int cantidad) {
        this.idComboDetalle = idComboDetalle;
        this.idCombo = idCombo;
        this.idProducto = idProducto;
        this.cantidad = Math.max(1, cantidad);
    }

    public ComboDetalle(int idProducto, String nombreProducto, int cantidad) {
        this(0, 0, idProducto, cantidad);
        this.nombreProducto = nombreProducto;
    }

    public int getIdComboDetalle() {
        return idComboDetalle;
    }

    public void setIdComboDetalle(int idComboDetalle) {
        this.idComboDetalle = idComboDetalle;
    }

    public int getIdCombo() {
        return idCombo;
    }

    public void setIdCombo(int idCombo) {
        this.idCombo = idCombo;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = Math.max(1, cantidad);
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }

    public void setCodigoProducto(String codigoProducto) {
        this.codigoProducto = codigoProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public BigDecimal getPrecioUnitarioReferencia() {
        return precioUnitarioReferencia;
    }

    public void setPrecioUnitarioReferencia(BigDecimal precioUnitarioReferencia) {
        this.precioUnitarioReferencia = precioUnitarioReferencia;
    }

    /**
     * Formatea la línea de componente para cocina/backoffice (ej. {@code "2x Hamburguesa Clasica"}).
     */
    public String formatearLinea() {
        String nom = nombreProducto != null && !nombreProducto.isBlank()
                ? nombreProducto.trim()
                : ("Producto #" + idProducto);
        return Math.max(1, cantidad) + "x " + nom;
    }

    public BigDecimal calcularSubtotalReferencia() {
        if (precioUnitarioReferencia == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return precioUnitarioReferencia.multiply(BigDecimal.valueOf(Math.max(1, cantidad)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return formatearLinea();
    }
}
