package com.foodcore.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formateador de unidades de medida para la capa de presentación según SPEC-FOODCORE-v1.2 (§1.1):
 * <ul>
 *   <li>{@code UNIDAD}: sin decimales (ej. {@code 120 Unds}).</li>
 *   <li>{@code KG}: hasta 3 decimales, eliminando ceros a la derecha (ej. {@code 38.2 kg}, {@code 1.5 kg}).</li>
 *   <li>{@code GR} o {@code ML}: enteros sin decimales (ej. {@code 500 gr}, {@code 250 ml}).</li>
 *   <li>{@code LT}: hasta 2 decimales, eliminando ceros a la derecha (ej. {@code 1.25 L}).</li>
 * </ul>
 */
public final class UnidadMedidaFormatter {

    private UnidadMedidaFormatter() {
    }

    /**
     * Normaliza el código de unidad de medida hacia los valores canónicos del ENUM de BD
     * ({@code UNIDAD}, {@code KG}, {@code GR}, {@code ML}, {@code LT}).
     */
    public static String normalizarCodigo(String unidadMedida) {
        if (unidadMedida == null || unidadMedida.isBlank()) {
            return "UNIDAD";
        }
        String u = unidadMedida.trim().toUpperCase();
        return switch (u) {
            case "UND", "UNID", "UNIDADES", "UNIDAD", "U" -> "UNIDAD";
            case "KG", "KILO", "KILOS", "KILOGRAMO", "KILOGRAMOS" -> "KG";
            case "GR", "G", "GRAMO", "GRAMOS" -> "GR";
            case "ML", "MILILITRO", "MILILITROS" -> "ML";
            case "LT", "L", "LITRO", "LITROS" -> "LT";
            default -> "UNIDAD";
        };
    }

    /**
     * Devuelve el sufijo legible de la unidad de medida para tablas y etiquetas.
     */
    public static String formatearSufijo(String unidadMedida) {
        String codigo = normalizarCodigo(unidadMedida);
        return switch (codigo) {
            case "KG" -> "kg";
            case "GR" -> "gr";
            case "ML" -> "ml";
            case "LT" -> "L";
            default -> "Unds";
        };
    }

    /**
     * Formatea una cantidad numérica junto con su unidad de medida según las reglas de §1.1.
     */
    public static String formatear(BigDecimal cantidad, String unidadMedida) {
        return formatear(cantidad, unidadMedida, false);
    }

    /**
     * Formatea una cantidad numérica con su unidad, permitiendo incluir el signo {@code +} explícito
     * para movimientos positivos de Kardex.
     */
    public static String formatear(BigDecimal cantidad, String unidadMedida, boolean incluirSignoPositivo) {
        BigDecimal valor = cantidad != null ? cantidad : BigDecimal.ZERO;
        String codigo = normalizarCodigo(unidadMedida);
        String sufijo = formatearSufijo(codigo);

        String numeroFormateado = switch (codigo) {
            case "KG" -> formatearHastaDecimales(valor, 3);
            case "LT" -> formatearHastaDecimales(valor, 2);
            case "GR", "ML", "UNIDAD" -> valor.setScale(0, RoundingMode.HALF_UP).toPlainString();
            default -> valor.setScale(0, RoundingMode.HALF_UP).toPlainString();
        };

        if (incluirSignoPositivo && valor.compareTo(BigDecimal.ZERO) > 0 && !numeroFormateado.startsWith("+")) {
            return "+" + numeroFormateado + " " + sufijo;
        }
        return numeroFormateado + " " + sufijo;
    }

    private static String formatearHastaDecimales(BigDecimal valor, int maxDecimales) {
        BigDecimal redondeado = valor.setScale(maxDecimales, RoundingMode.HALF_UP).stripTrailingZeros();
        if (redondeado.scale() < 0) {
            redondeado = redondeado.setScale(0, RoundingMode.UNNECESSARY);
        }
        return redondeado.toPlainString();
    }
}
