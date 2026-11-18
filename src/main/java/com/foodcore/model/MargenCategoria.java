package com.foodcore.model;

import java.math.BigDecimal;

/**
 * Rentabilidad y margen bruto agregado por categoría de carta en un período (v4.9.0).
 */
public record MargenCategoria(
        int idCategoria,
        String nombreCategoria,
        int unidadesVendidas,
        BigDecimal ingresosVenta,
        BigDecimal costoReceta,
        BigDecimal margenBruto,
        BigDecimal margenPorcentaje) {
}
