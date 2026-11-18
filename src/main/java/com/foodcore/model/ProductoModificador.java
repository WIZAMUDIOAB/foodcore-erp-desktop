package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Modificador o adicional global de platillos (ej. Extra Queso, Tocino, Sin Cebolla) (v4.8.0 Módulo 2).
 */
public class ProductoModificador {

    private int idModificador;
    private String nombre;
    private BigDecimal precio;
    private boolean activo = true;

    public ProductoModificador() {
        this.precio = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public ProductoModificador(int idModificador, String nombre, BigDecimal precio, boolean activo) {
        this.idModificador = idModificador;
        this.nombre = nombre;
        this.precio = precio != null
                ? precio.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.activo = activo;
    }

    public BigDecimal getPrecioNormalizado() {
        return precio != null
                ? precio.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public String formatearParaSelector() {
        BigDecimal p = getPrecioNormalizado();
        if (p.compareTo(BigDecimal.ZERO) > 0) {
            return nombre + " (+S/ " + p.toPlainString() + ")";
        }
        return nombre + " (Sin costo)";
    }

    public int getIdModificador() {
        return idModificador;
    }

    public void setIdModificador(int idModificador) {
        this.idModificador = idModificador;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio != null
                ? precio.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return formatearParaSelector();
    }
}
