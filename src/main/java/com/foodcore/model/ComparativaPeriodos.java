package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Indicadores comparativos de ventas entre períodos consecutivos (Hoy vs. Ayer y Mes Actual vs. Anterior).
 */
public record ComparativaPeriodos(
        LocalDate fechaReferencia,
        BigDecimal ventasHoy,
        BigDecimal ventasAyer,
        BigDecimal variacionDiariaPct,
        BigDecimal ventasMesActual,
        BigDecimal ventasMesAnterior,
        BigDecimal variacionMensualPct) {
}
