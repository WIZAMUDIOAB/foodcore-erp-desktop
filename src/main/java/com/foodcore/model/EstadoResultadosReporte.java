package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado consolidado del Estado de Resultados (P&amp;L) para un rango de fechas (v4.9.0).
 */
public record EstadoResultadosReporte(
        LocalDate desde,
        LocalDate hasta,
        int cantidadVentas,
        BigDecimal ingresosBrutos,
        BigDecimal subtotalGravado,
        BigDecimal igvVentas,
        BigDecimal costoVentas,
        BigDecimal utilidadBruta,
        BigDecimal margenBrutoPct,
        BigDecimal gastosOperativos,
        BigDecimal utilidadNeta,
        BigDecimal margenNetoPct,
        List<LineaPnl> lineasContables,
        List<GastoPorCategoria> gastosPorCategoria) {

    public EstadoResultadosReporte {
        lineasContables = lineasContables != null ? List.copyOf(lineasContables) : List.of();
        gastosPorCategoria = gastosPorCategoria != null ? List.copyOf(gastosPorCategoria) : List.of();
    }

    /**
     * Línea estructurada de cascada contable en el reporte P&amp;L.
     */
    public record LineaPnl(
            String codigo,
            String concepto,
            BigDecimal monto,
            BigDecimal porcentajeSobreIngresos,
            String tipoLinea) {
    }

    /**
     * Agrupación de egresos operativos de caja por categoría contable.
     */
    public record GastoPorCategoria(
            String categoria,
            int operaciones,
            BigDecimal monto,
            BigDecimal participacionGastoPct,
            BigDecimal porcentajeSobreIngresos) {
    }
}
