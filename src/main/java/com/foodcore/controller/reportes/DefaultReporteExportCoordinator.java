package com.foodcore.controller.reportes;

import com.foodcore.controller.ReportesAvanzadosController;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.util.ExportFileChooserHelper;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DefaultReporteExportCoordinator implements ReporteExportCoordinator {
    @FunctionalInterface
    public interface ExportadorArchivoFn {
        void exportar(String titulo, List<String> headers, List<List<String>> filas, File destino) throws Exception;
    }

    private final ExportadorArchivoFn pdfFn;
    private final ExportadorArchivoFn excelFn;
    private final ExportadorArchivoFn csvFn;

    public DefaultReporteExportCoordinator() {
        this(new ExportacionContableService());
    }

    public DefaultReporteExportCoordinator(ExportacionContableService exportacionService) {
        this((titulo, headers, filas, destino) -> exportacionService.exportarReporteGenericoPdf(titulo, headers, filas, destino),
                (titulo, headers, filas, destino) -> exportacionService.exportarReporteGenericoExcel("ANALISIS", headers, filas, destino),
                (titulo, headers, filas, destino) -> exportacionService.exportarReporteGenericoCsv(headers, filas, destino));
    }

    public DefaultReporteExportCoordinator(ExportadorArchivoFn pdfFn, ExportadorArchivoFn excelFn,
            ExportadorArchivoFn csvFn) {
        this.pdfFn = pdfFn;
        this.excelFn = excelFn;
        this.csvFn = csvFn;
    }

    @Override
    public SolicitudExportacion prepararSolicitud(ExportFileChooserHelper.FormatoExportacion formato, String categoria) {
        String tipo = formato.name().toLowerCase(Locale.ROOT);
        String capitalizado = tipo.substring(0, 1).toUpperCase(Locale.ROOT) + tipo.substring(1);
        return new SolicitudExportacion("Guardar Reporte en " + capitalizado,
                "REPORTE_" + (categoria == null ? "REPORTE" : categoria.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase(Locale.ROOT))
                        + formato.getExtensionPrincipal(),
                formato, "Exportando " + capitalizado + "...", "✓ " + capitalizado + " exportado: ",
                "Error al exportar " + capitalizado + ": ");
    }

    @Override
    public List<List<String>> convertirFilas(List<ReportesAvanzadosController.FilaReporteAvanzado> filas) {
        List<List<String>> resultado = new ArrayList<>();
        for (ReportesAvanzadosController.FilaReporteAvanzado fila : filas) {
            resultado.add(List.of(fila.c1, fila.c2, fila.c3, fila.c4, fila.c5));
        }
        return List.copyOf(resultado);
    }

    @Override
    public void ejecutarExportacion(ExportFileChooserHelper.FormatoExportacion formato, String categoria,
            List<String> headers, List<List<String>> filas, File destino) throws Exception {
        if (destino.getParentFile() != null) Files.createDirectories(destino.getParentFile().toPath());
        ExportadorArchivoFn exportador = switch (formato) {
            case PDF -> pdfFn;
            case EXCEL -> excelFn;
            case CSV -> csvFn;
            default -> throw new IllegalArgumentException("Formato de exportación no soportado: " + formato);
        };
        exportador.exportar(categoria != null ? categoria : "REPORTE", headers, filas, destino);
    }
}
