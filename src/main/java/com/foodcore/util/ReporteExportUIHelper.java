package com.foodcore.util;

import com.foodcore.service.ExportacionContableService;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper compartido para disparar exportaciones tabulares (PDF/Excel/CSV) con {@link FileChooser}
 * y ejecución asíncrona sin duplicación entre controladores.
 */
public final class ReporteExportUIHelper {

    private static final Logger LOGGER = Logger.getLogger(ReporteExportUIHelper.class.getName());

    private ReporteExportUIHelper() {
    }

    public static void exportarTablaConSelector(
            Node ownerNode,
            Label statusLabel,
            String tituloDialogo,
            String nombreSugerido,
            String tituloReporte,
            String nombreHojaExcel,
            FormatoExportacion formato,
            List<String> encabezados,
            List<List<String>> filas,
            ExportacionContableService exportacionService) {
        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                ownerNode, tituloDialogo, nombreSugerido, formato);
        if (elegidoOpt.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Exportación cancelada.");
            }
            return;
        }

        File destino = elegidoOpt.get();
        List<String> headersCopy = List.copyOf(encabezados);
        List<List<String>> filasCopy = filas.stream().map(List::copyOf).toList();
        ExportacionContableService svc = exportacionService != null
                ? exportacionService
                : new ExportacionContableService();

        if (statusLabel != null) {
            statusLabel.setText("Exportando " + formato.name() + "...");
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                if (destino.getParentFile() != null) {
                    Files.createDirectories(destino.getParentFile().toPath());
                }
                String lower = destino.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".pdf")) {
                    svc.exportarReporteGenericoPdf(tituloReporte, headersCopy, filasCopy, destino);
                } else if (lower.endsWith(".csv")) {
                    svc.exportarReporteGenericoCsv(headersCopy, filasCopy, destino);
                } else {
                    svc.exportarReporteGenericoExcel(nombreHojaExcel, headersCopy, filasCopy, destino);
                }
                return destino.getName();
            },
            nombre -> {
                if (statusLabel != null) {
                    statusLabel.setText("✓ Exportado: " + nombre);
                }
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error en exportación tabular", ex);
                if (statusLabel != null) {
                    statusLabel.setText("Error al exportar: " + (ex != null ? ex.getMessage() : "desconocido"));
                }
            }
        );
    }
}
