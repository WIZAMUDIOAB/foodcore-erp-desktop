package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de rentabilidad por receta para el Dashboard gerencial (v3.0.3 §4).
 *
 * @param platillo        Nombre del producto de carta.
 * @param costoReceta     Costo de receta formateado (S/ 0.00).
 * @param precioVenta     Precio de venta formateado (S/ 0.00).
 * @param margenPorcentaje Margen porcentual formateado (0.0%).
 * @param margenNum       Margen porcentual numérico para clasificación visual.
 */
public record FilaRentabilidad(
        String platillo,
        String costoReceta,
        String precioVenta,
        String margenPorcentaje,
        BigDecimal margenNum) {
}
