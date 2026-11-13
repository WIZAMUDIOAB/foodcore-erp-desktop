package com.foodcore.util;

import javafx.scene.control.Label;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilidades de formateo visual para indicadores comparativos y porcentajes BI en JavaFX (v4.9.0).
 */
public final class FormatoBiUiHelper {

    private FormatoBiUiHelper() {
    }

    /**
     * Aplica texto con flecha direccional y estilo verde/rojo/neutro a una píldora de variación porcentual.
     */
    public static void aplicarIndicadorComparativa(Label badge, BigDecimal variacionPct) {
        if (badge == null) {
            return;
        }
        BigDecimal norm = variacionPct != null
                ? variacionPct.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        int cmp = norm.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            badge.setText("↑ +" + norm + "%");
            badge.getStyleClass().setAll("badge-pill-success");
        } else if (cmp < 0) {
            badge.setText("↓ " + norm + "%");
            badge.getStyleClass().setAll("badge-pill-danger");
        } else {
            badge.setText("→ 0.0%");
            badge.getStyleClass().setAll("badge-pill-neutral");
        }
    }

    /**
     * Formatea una celda porcentual de la matriz de cohortes (o guion si el mes aún es futuro).
     */
    public static String formatearPorcentajeCohorte(BigDecimal pct) {
        if (pct == null) {
            return "-";
        }
        return pct.setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
