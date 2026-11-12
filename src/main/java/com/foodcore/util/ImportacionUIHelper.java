package com.foodcore.util;

import com.foodcore.service.ImportacionMasivaService.ResultadoImportacion;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper compartido para descarga de plantillas Excel e importación masiva asíncrona
 * con indicador de progreso {@link ProgressIndicator} y {@link UiAsyncExecutor} (v4.6.0 Fase 5).
 */
public final class ImportacionUIHelper {

    private static final Logger LOGGER = Logger.getLogger(ImportacionUIHelper.class.getName());

    private ImportacionUIHelper() {
    }

    @FunctionalInterface
    public interface PlantillaWriter {
        void write(File destino) throws Exception;
    }

    @FunctionalInterface
    public interface ImportadorAction {
        ResultadoImportacion importar(File origen) throws Exception;
    }

    public static void descargarPlantilla(
            Node ownerNode,
            StackPane toastContainer,
            Label statusLabel,
            String tituloDialogo,
            String nombreSugerido,
            PlantillaWriter writer) {
        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                ownerNode, tituloDialogo, nombreSugerido, FormatoExportacion.EXCEL);
        if (elegidoOpt.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Descarga de plantilla cancelada.");
            }
            return;
        }
        File destino = elegidoOpt.get();
        if (statusLabel != null) {
            statusLabel.setText("Generando plantilla...");
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                if (destino.getParentFile() != null) {
                    Files.createDirectories(destino.getParentFile().toPath());
                }
                writer.write(destino);
                return destino.getName();
            },
            nombre -> {
                String msg = "✓ Plantilla guardada: " + nombre;
                if (statusLabel != null) {
                    statusLabel.setText(msg);
                }
                if (toastContainer != null && toastContainer.getScene() != null) {
                    AnimationUtils.mostrarToast(toastContainer, msg, false);
                }
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error generando plantilla Excel", ex);
                String msg = "Error al guardar plantilla: " + (ex != null ? ex.getMessage() : "desconocido");
                if (statusLabel != null) {
                    statusLabel.setText(msg);
                }
                if (toastContainer != null && toastContainer.getScene() != null) {
                    AnimationUtils.mostrarToast(toastContainer, msg, true);
                }
            }
        );
    }

    public static void ejecutarImportacion(
            Node ownerNode,
            StackPane toastContainer,
            Button triggerBtn,
            ProgressIndicator progress,
            Label statusLabel,
            String tituloDialogo,
            ImportadorAction action,
            Runnable onSuccessReload) {
        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirOrigenImportacion(ownerNode, tituloDialogo);
        if (elegidoOpt.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Importación cancelada.");
            }
            return;
        }

        File archivoOrigen = elegidoOpt.get();
        mostrarProgreso(triggerBtn, progress, true);
        if (statusLabel != null) {
            statusLabel.setText("Importando " + archivoOrigen.getName() + "...");
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> action.importar(archivoOrigen),
            resultado -> {
                mostrarProgreso(triggerBtn, progress, false);
                String resumen = resultado.construirMensajeResumen();
                if (statusLabel != null) {
                    statusLabel.setText(resumen);
                }
                if (onSuccessReload != null) {
                    onSuccessReload.run();
                }
                if (toastContainer != null && toastContainer.getScene() != null) {
                    AnimationUtils.mostrarToast(toastContainer, resumen, resultado.errores() > 0 && resultado.creados() == 0 && resultado.actualizados() == 0);
                }
            },
            ex -> {
                mostrarProgreso(triggerBtn, progress, false);
                LOGGER.log(Level.WARNING, "Error en importación masiva", ex);
                String msg = "Error en importación: " + (ex != null ? ex.getMessage() : "desconocido");
                if (statusLabel != null) {
                    statusLabel.setText(msg);
                }
                if (toastContainer != null && toastContainer.getScene() != null) {
                    AnimationUtils.mostrarToast(toastContainer, msg, true);
                }
            }
        );
    }

    private static void mostrarProgreso(Button triggerBtn, ProgressIndicator progress, boolean activo) {
        if (triggerBtn != null) {
            triggerBtn.setDisable(activo);
        }
        if (progress != null) {
            progress.setVisible(activo);
            progress.setManaged(activo);
            progress.setProgress(activo ? ProgressIndicator.INDETERMINATE_PROGRESS : 0.0);
        }
    }
}
