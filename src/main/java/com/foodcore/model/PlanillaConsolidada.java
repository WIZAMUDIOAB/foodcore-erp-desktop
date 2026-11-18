package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Consolidado mensual administrativo por colaborador:
 * {@code Sueldo Base + Bonos + Comisiones - Adelantos - Descuentos = Neto a Pagar}.
 * No incluye cálculos legales (AFP/ONP/Renta 5ta).
 */
public record PlanillaConsolidada(
        int idUsuario,
        String nombreEmpleado,
        String cargo,
        String periodoMes,
        BigDecimal sueldoBase,
        BigDecimal bonos,
        BigDecimal comisiones,
        BigDecimal adelantos,
        BigDecimal descuentos
) {
    public BigDecimal totalIngresosAdicionales() {
        return nvl(bonos).add(nvl(comisiones)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal totalEgresos() {
        return nvl(adelantos).add(nvl(descuentos)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal netoPagar() {
        return nvl(sueldoBase)
                .add(nvl(bonos))
                .add(nvl(comisiones))
                .subtract(nvl(adelantos))
                .subtract(nvl(descuentos))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
