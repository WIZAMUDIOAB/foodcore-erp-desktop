package com.foodcore.controller.reportes;

import com.foodcore.model.ReporteCohortes.CohorteMensualFila;
import com.foodcore.model.ReporteCohortes.SegmentoRetencionFila;
import java.util.List;

/**
 * Puerto de lectura desacoplado expuesto por la vista de reportes avanzados.
 */
public interface ReportesAvanzadosViewPort {
    List<FilaReporteAvanzado> getFilasActuales();

    List<SegmentoRetencionFila> getResumenCohortesActual();

    List<CohorteMensualFila> getMatrizCohortesActual();
}
