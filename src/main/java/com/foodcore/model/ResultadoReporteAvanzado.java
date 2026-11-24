package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Resultado estructurado de un reporte avanzado (ReportesAvanzadosView):
 * encabezados, filas tabulares de 5 columnas, rodajas de donut y KPIs derivados.
 *
 * @param headers          Encabezados de la tabla (5 columnas).
 * @param filas            Filas de datos, cada una con exactamente 5 cadenas.
 * @param slices           Rodajas del gráfico donut.
 * @param montoTotal       Métrica agregada principal (total o margen bruto).
 * @param cantidadTotal    Métrica de conteo (registros, categorías o clientes).
 * @param topItem          Etiqueta del ítem destacado.
 * @param topValor         Valor del ítem destacado.
 * @param comparativa      Comparativas Hoy vs. Ayer y Mes Actual vs. Anterior.
 * @param cohortesReporte  Reporte de cohortes cuando aplica, {@code null} si no.
 * @param kpiMontoTotalText Texto preformateado del KPI de monto total.
 * @param kpiSubTotalText  Texto preformateado del KPI de subtotal.
 * @param kpiPromedioText  Texto preformateado del KPI de promedio.
 * @param kpiTopValorText  Texto preformateado del KPI de ítem destacado.
 */
public record ResultadoReporteAvanzado(
        String[] headers,
        List<String[]> filas,
        List<RebanadaPie> slices,
        BigDecimal montoTotal,
        int cantidadTotal,
        String topItem,
        BigDecimal topValor,
        ComparativaPeriodos comparativa,
        ReporteCohortes cohortesReporte,
        String kpiMontoTotalText,
        String kpiSubTotalText,
        String kpiPromedioText,
        String kpiTopValorText) {

    /**
     * Normaliza arreglos, listas y montos monetarios a copias no nulas e inmutables.
     */
    public ResultadoReporteAvanzado {
        headers = headers != null ? headers.clone() : new String[]{"", "", "", "", ""};
        filas = filas != null ? List.copyOf(filas) : List.of();
        slices = slices != null ? List.copyOf(slices) : List.of();
        montoTotal = normalizarMoneda(montoTotal);
        topValor = normalizarMoneda(topValor);
    }

    private static BigDecimal normalizarMoneda(BigDecimal valor) {
        return valor != null
                ? valor.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String[] headers() {
        return headers.clone();
    }

    /**
     * Retorna una copia con la comparativa y el reporte de cohortes provistos.
     *
     * @param comparativa      Comparativas de períodos.
     * @param cohortesReporte  Reporte de cohortes (opcional).
     * @return Copia enriquecida del resultado.
     */
    public ResultadoReporteAvanzado conContexto(
            ComparativaPeriodos comparativa,
            ReporteCohortes cohortesReporte) {
        return new ResultadoReporteAvanzado(
                headers,
                filas,
                slices,
                montoTotal,
                cantidadTotal,
                topItem,
                topValor,
                comparativa,
                cohortesReporte,
                kpiMontoTotalText,
                kpiSubTotalText,
                kpiPromedioText,
                kpiTopValorText);
    }

    /**
     * Rodaja del gráfico donut.
     *
     * @param etiqueta Texto visible de la leyenda.
     * @param valor    Magnitud proporcional de la rodaja.
     */
    public record RebanadaPie(String etiqueta, BigDecimal valor) {
        public RebanadaPie {
            valor = normalizarMoneda(valor);
        }
    }
}
