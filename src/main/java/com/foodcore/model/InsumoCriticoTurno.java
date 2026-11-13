package com.foodcore.model;

import java.math.BigDecimal;

/**
 * Insumo en situación crítica de stock (agotado o por debajo/igual al mínimo) al cierre de turno de caja.
 */
public record InsumoCriticoTurno(
        long idInsumo,
        String codigo,
        String nombre,
        String unidadMedida,
        BigDecimal stockActual,
        BigDecimal stockMinimo,
        String nivel
) {
    public boolean isAgotado() {
        return "AGOTADO".equalsIgnoreCase(nivel);
    }
}
