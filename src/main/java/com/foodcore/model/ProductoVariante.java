package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Variante o tamaño configurable de un platillo del menú (ej. Mediana, Familiar) (v4.8.0 Módulo 1).
 */
public class ProductoVariante {

    private int idVariante;
    private int idProducto;
    private String nombreVariante;
    private BigDecimal precioFijo;
    private BigDecimal precioAdicional;
    private int orden;
    private boolean activo = true;

    public ProductoVariante() {
        this.precioAdicional = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public ProductoVariante(int idVariante,
                            int idProducto,
                            String nombreVariante,
                            BigDecimal precioFijo,
                            BigDecimal precioAdicional,
                            int orden,
                            boolean activo) {
        this.idVariante = idVariante;
        this.idProducto = idProducto;
        this.nombreVariante = nombreVariante;
        this.precioFijo = precioFijo != null ? precioFijo.setScale(2, RoundingMode.HALF_UP) : null;
        this.precioAdicional = precioAdicional != null
                ? precioAdicional.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.orden = orden;
        this.activo = activo;
    }

    public static ProductoVariante conPrecioFijo(int idProducto, String nombreVariante, BigDecimal precioFijo, int orden) {
        return new ProductoVariante(0, idProducto, nombreVariante, precioFijo, BigDecimal.ZERO, orden, true);
    }

    public static ProductoVariante conPrecioAdicional(int idProducto, String nombreVariante, BigDecimal precioAdicional, int orden) {
        return new ProductoVariante(0, idProducto, nombreVariante, null, precioAdicional, orden, true);
    }

    public boolean esPrecioFijo() {
        return precioFijo != null;
    }

    /**
     * Devuelve el precio final del ítem aplicando precio fijo o suma al precio base del producto.
     */
    public BigDecimal calcularPrecioEfectivo(BigDecimal precioBase) {
        if (precioFijo != null) {
            return precioFijo.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal base = precioBase != null ? precioBase : BigDecimal.ZERO;
        BigDecimal adicional = precioAdicional != null ? precioAdicional : BigDecimal.ZERO;
        return base.add(adicional).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public String formatearEtiquetaPrecio(BigDecimal precioBase) {
        BigDecimal efectivo = calcularPrecioEfectivo(precioBase);
        if (esPrecioFijo()) {
            return "S/ " + efectivo.toPlainString();
        }
        if (precioAdicional != null && precioAdicional.compareTo(BigDecimal.ZERO) > 0) {
            return "+S/ " + precioAdicional.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + " (S/ " + efectivo.toPlainString() + ")";
        }
        return "S/ " + efectivo.toPlainString();
    }

    public int getIdVariante() {
        return idVariante;
    }

    public void setIdVariante(int idVariante) {
        this.idVariante = idVariante;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public String getNombreVariante() {
        return nombreVariante;
    }

    public void setNombreVariante(String nombreVariante) {
        this.nombreVariante = nombreVariante;
    }

    public BigDecimal getPrecioFijo() {
        return precioFijo;
    }

    public void setPrecioFijo(BigDecimal precioFijo) {
        this.precioFijo = precioFijo != null ? precioFijo.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getPrecioAdicional() {
        return precioAdicional;
    }

    public void setPrecioAdicional(BigDecimal precioAdicional) {
        this.precioAdicional = precioAdicional != null
                ? precioAdicional.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return nombreVariante != null ? nombreVariante : "";
    }
}
