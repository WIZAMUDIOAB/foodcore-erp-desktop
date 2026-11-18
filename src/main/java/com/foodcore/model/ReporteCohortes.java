package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Resultado del análisis de cohortes y retención de clientes (v4.9.0 Fase 3).
 */
public record ReporteCohortes(
        LocalDate fechaCorte,
        YearMonth mesAnalizado,
        int totalClientesIdentificados,
        int clientesNuevosMes,
        int clientesRecurrentesMes,
        int clientesPerdidosMas60Dias,
        boolean datosSuficientesCohortes,
        List<CohorteMensualFila> cohortes,
        List<SegmentoRetencionFila> resumenRetencion) {

    public ReporteCohortes {
        cohortes = cohortes != null ? List.copyOf(cohortes) : List.of();
        resumenRetencion = resumenRetencion != null ? List.copyOf(resumenRetencion) : List.of();
    }

    /**
     * Fila de la matriz de cohortes mensuales mostrando retención porcentual mes a mes.
     */
    public record CohorteMensualFila(
            YearMonth mesCohorte,
            String etiquetaCohorte,
            int tamanoCohorte,
            BigDecimal mes1Pct,
            BigDecimal mes2Pct,
            BigDecimal mes3Pct,
            BigDecimal mes4Pct) {
    }

    /**
     * Fila resumen de segmentación de fidelización del mes (Nuevos, Recurrentes y Perdidos &gt; 60 días).
     */
    public record SegmentoRetencionFila(
            String segmento,
            int cantidad,
            BigDecimal porcentaje,
            String criterio) {
    }
}
