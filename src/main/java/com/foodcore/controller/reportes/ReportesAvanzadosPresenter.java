package com.foodcore.controller.reportes;

import com.foodcore.controller.ReportesAvanzadosController;
import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.ReporteCohortes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReportesAvanzadosPresenter {
    record PieSlice(String label, double value) { }

    record ResultadoAnalisisAvanzado(
            String[] headers,
            List<ReportesAvanzadosController.FilaReporteAvanzado> filas,
            List<PieSlice> slices,
            double montoTotal,
            int cantidadTotal,
            String topItem,
            double topValor,
            ComparativaPeriodos comparativa,
            ReporteCohortes cohortesReporte,
            String kpiMontoTotalText,
            String kpiSubTotalText,
            String kpiPromedioText,
            String kpiTopValorText) {
        public ResultadoAnalisisAvanzado {
            headers = headers != null ? headers.clone() : new String[]{"", "", "", "", ""};
            filas = filas != null ? List.copyOf(filas) : List.of();
            slices = slices != null ? List.copyOf(slices) : List.of();
        }

        @Override
        public String[] headers() {
            return headers.clone();
        }
    }

    ResultadoAnalisisAvanzado consultarAnalisis(String categoria, LocalDate ini, LocalDate fin, LocalDate fechaRef);
    String determinarModoActivo(String categoria);
    String sanitizarNombre(String nom);
    String fmt2(BigDecimal val);
    String fmt1(BigDecimal val);
    double parsePctSeguro(String textoPct);
    String resolverEstiloHeatmap(String item);
}
