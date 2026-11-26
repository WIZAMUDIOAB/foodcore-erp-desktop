package com.foodcore.controller.reportes;

import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.ReporteCohortes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public interface ReportesAvanzadosPresenter {
    record PieSlice(String label, BigDecimal value) {
        public PieSlice {
            value = value != null
                    ? value.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    record ResultadoAnalisisAvanzado(
            String[] headers,
            List<FilaReporteAvanzado> filas,
            List<PieSlice> slices,
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
        public ResultadoAnalisisAvanzado {
            headers = headers != null ? headers.clone() : new String[]{"", "", "", "", ""};
            filas = filas != null ? List.copyOf(filas) : List.of();
            slices = slices != null ? List.copyOf(slices) : List.of();
            montoTotal = montoTotal != null
                    ? montoTotal.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            topValor = topValor != null
                    ? topValor.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
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
