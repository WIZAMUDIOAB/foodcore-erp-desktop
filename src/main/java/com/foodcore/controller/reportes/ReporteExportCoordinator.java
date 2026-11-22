package com.foodcore.controller.reportes;

import com.foodcore.controller.ReportesAvanzadosController;
import com.foodcore.util.ExportFileChooserHelper;
import java.io.File;
import java.util.List;

public interface ReporteExportCoordinator {
    record SolicitudExportacion(String tituloDialogo, String nombreSugerido,
            ExportFileChooserHelper.FormatoExportacion formato, String estadoProcesando,
            String prefijoExito, String prefijoError) { }

    SolicitudExportacion prepararSolicitud(ExportFileChooserHelper.FormatoExportacion formato, String categoria);
    List<List<String>> convertirFilas(List<ReportesAvanzadosController.FilaReporteAvanzado> filas);
    void ejecutarExportacion(ExportFileChooserHelper.FormatoExportacion formato, String categoria,
            List<String> headers, List<List<String>> filas, File destino) throws Exception;
}
