package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FileChooserRequest;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación funcional y visual de Fase 4 (SPEC-FOODCORE-v4.6.0):
 * selector {@link FileChooser} con filtros .pdf/.xlsx/.csv, cancelación limpia,
 * persistencia de última carpeta en {@link Preferences} y botones de exportación
 * en los 5 controladores requeridos.
 */
class Fase4ExportFileChooserTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "fase4-screenshots");

    private String previoUltimaCarpetaExport;

    @BeforeAll
    static void setUpClass() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @BeforeEach
    void respaldoPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(ExportFileChooserHelper.class);
        previoUltimaCarpetaExport = prefs.get(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_EXPORT, null);
        ExportFileChooserHelper.clearTestSelectorOverride();
    }

    @AfterEach
    void restaurarPreferencesYOverrides() {
        ExportFileChooserHelper.clearTestSelectorOverride();
        Preferences prefs = Preferences.userNodeForPackage(ExportFileChooserHelper.class);
        if (previoUltimaCarpetaExport == null) {
            prefs.remove(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_EXPORT);
        } else {
            prefs.put(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_EXPORT, previoUltimaCarpetaExport);
        }
    }

    @Test
    @DisplayName("4.1 ExportFileChooserHelper recuerda carpeta en Preferences, valida filtros y cancela limpio")
    void testHelperPreferenciasFiltrosYCancelacion(@TempDir Path tempDir) throws Exception {
        String testNodeName = "foodcore-test-" + UUID.randomUUID();
        Preferences isolatedPrefs = Preferences.userRoot().node(testNodeName);
        try {
            ExportFileChooserHelper helper = ExportFileChooserHelper.conPreferencias(isolatedPrefs);
            Path subDir = Files.createDirectories(tempDir.resolve("reportes_enero"));

            AtomicReference<FileChooserRequest> primeraReq = new AtomicReference<>();
            ExportFileChooserHelper.setTestSelectorOverride(req -> {
                primeraReq.set(req);
                return Optional.of(subDir.resolve("Reporte_Ventas_Mes.xlsx").toFile());
            });

            Optional<File> primeraSel = helper.seleccionarArchivoGuardar(
                    null, "Guardar Ventas", "Reporte_Ventas_Mes.xlsx", FormatoExportacion.EXCEL);
            assertTrue(primeraSel.isPresent());
            assertEquals("Reporte_Ventas_Mes.xlsx", primeraSel.get().getName());
            assertNotNull(primeraReq.get());
            assertEquals("Reporte_Ventas_Mes.xlsx", primeraReq.get().nombreSugerido());

            List<String> extensiones = primeraReq.get().filtros().stream()
                    .flatMap(f -> f.getExtensions().stream())
                    .toList();
            assertTrue(extensiones.containsAll(List.of("*.pdf", "*.xlsx", "*.csv")),
                    "El selector debe incluir extensiones .pdf, .xlsx y .csv");

            // Segunda invocación debe recordar subDir en Preferences
            AtomicReference<FileChooserRequest> segundaReq = new AtomicReference<>();
            ExportFileChooserHelper.setTestSelectorOverride(req -> {
                segundaReq.set(req);
                return Optional.empty(); // Simula Cancelar
            });

            Optional<File> cancelada = helper.seleccionarArchivoGuardar(
                    null, "Guardar Resumen", "Resumen_Caja", FormatoExportacion.PDF);
            assertTrue(cancelada.isEmpty(), "Cancelar debe retornar Optional.empty() sin errores");
            assertEquals(subDir.toAbsolutePath(), segundaReq.get().directorioInicial().toAbsolutePath(),
                    "La segunda apertura debe iniciar en la carpeta recordada por Preferences");
            assertEquals("Resumen_Caja.pdf", segundaReq.get().nombreSugerido());

            // Si la carpeta guardada deja de existir, no debe romperse
            Files.deleteIfExists(subDir);
            assertTrue(helper.obtenerUltimoDirectorio(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_EXPORT).isEmpty());
            assertNull(helper.seleccionarArchivoGuardar(null, "Test", "x.csv", FormatoExportacion.CSV)
                    .orElse(null));
        } finally {
            isolatedPrefs.removeNode();
        }
    }

    @Test
    @DisplayName("4.2 Controladores ReportesContables y ReportesAvanzados usan FileChooser y abortan limpio al cancelar")
    void testReportesContablesYAvanzadosConFileChooser(@TempDir Path tempDir) throws Exception {
        LoadedView<ReportesContablesController> contables = cargarVista(
                "/com/foodcore/view/ReportesContablesView.fxml", "01_reportes_contables_export.png");
        ReportesContablesController rcCtrl = contables.controller();

        // 1) Cancelación limpia en ReportesContablesController
        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.empty());
        JavaFxTestSupport.runOnFxThreadAndWait(rcCtrl::handleExportarVentasSunat);
        Label lblRcEstado = assertInstanceOf(Label.class, contables.root().lookup("#lblEstadoExportacion"));
        assertTrue(lblRcEstado.getText().contains("cancelada"),
                "Cancelar exportación no debe mostrar error sino estado limpio");
        assertFalse(lblRcEstado.getText().contains("Error"));

        // 2) Exportación real al destino elegido por FileChooser
        Path pleVentas = tempDir.resolve("PLE_VENTAS_ELEGIDO.xlsx");
        Path resumenPdf = tempDir.resolve("CAJA_Z_ELEGIDO.pdf");
        AtomicReference<FileChooserRequest> reqVentas = new AtomicReference<>();
        ExportFileChooserHelper.setTestSelectorOverride(req -> {
            reqVentas.set(req);
            return Optional.of(pleVentas.toFile());
        });
        JavaFxTestSupport.runOnFxThreadAndWait(rcCtrl::handleExportarVentasSunat);
        esperarArchivoConDatos(pleVentas, Duration.ofSeconds(10));
        assertTrue(reqVentas.get().nombreSugerido().endsWith(".xlsx"));

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(resumenPdf.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(rcCtrl::handleExportarResumenCaja);
        esperarArchivoConDatos(resumenPdf, Duration.ofSeconds(10));

        // 3) ReportesAvanzadosController (PDF, Excel, CSV + cancelación)
        LoadedView<ReportesAvanzadosController> avanzados = cargarVista(
                "/com/foodcore/view/ReportesAvanzadosView.fxml", null);
        ReportesAvanzadosController raCtrl = avanzados.controller();

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.empty());
        JavaFxTestSupport.runOnFxThreadAndWait(raCtrl::handleExportarExcel);
        Label lblRaEstado = assertInstanceOf(Label.class, avanzados.root().lookup("#lblEstadoReporte"));
        assertTrue(lblRaEstado.getText().contains("cancelada"));

        Path advPdf = tempDir.resolve("AVANZADO_TEST.pdf");
        Path advXlsx = tempDir.resolve("AVANZADO_TEST.xlsx");
        Path advCsv = tempDir.resolve("AVANZADO_TEST.csv");

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(advPdf.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(raCtrl::handleExportarPdf);
        esperarArchivoConDatos(advPdf, Duration.ofSeconds(10));

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(advXlsx.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(raCtrl::handleExportarExcel);
        esperarArchivoConDatos(advXlsx, Duration.ofSeconds(10));

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(advCsv.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(raCtrl::handleExportarCsv);
        esperarArchivoConDatos(advCsv, Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("4.3 Kardex, CuentasPorCobrar y CuentasPorPagar exportan vía FileChooser en ubicación elegida")
    void testKardexCxcYCxpExportacionConFileChooser(@TempDir Path tempDir) throws Exception {
        // 1) Kardex
        LoadedView<KardexController> kardex = cargarVista(
                "/com/foodcore/view/KardexView.fxml", "02_kardex_export_buttons.png");
        assertInstanceOf(Button.class, kardex.root().lookup("#btnExportarPdf"));
        assertInstanceOf(Button.class, kardex.root().lookup("#btnExportarExcel"));
        assertInstanceOf(Button.class, kardex.root().lookup("#btnExportarCsv"));

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.empty());
        JavaFxTestSupport.runOnFxThreadAndWait(kardex.controller()::handleExportarExcel);
        Label lblKardex = assertInstanceOf(Label.class, kardex.root().lookup("#lblResumenKardex"));
        assertTrue(lblKardex.getText().contains("cancelada"));

        Path kardexPdf = tempDir.resolve("KARDEX_ELEGIDO.pdf");
        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(kardexPdf.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(kardex.controller()::handleExportarPdf);
        esperarArchivoConDatos(kardexPdf, Duration.ofSeconds(10));

        // 2) Cuentas por Cobrar
        LoadedView<CuentasPorCobrarController> cxc = cargarVista(
                "/com/foodcore/view/CuentasPorCobrarView.fxml", "03_cxc_export_buttons.png");
        assertInstanceOf(Button.class, cxc.root().lookup("#btnExportarPdf"));
        assertInstanceOf(Button.class, cxc.root().lookup("#btnExportarExcel"));
        assertInstanceOf(Button.class, cxc.root().lookup("#btnExportarCsv"));

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.empty());
        JavaFxTestSupport.runOnFxThreadAndWait(cxc.controller()::handleExportarCsv);
        Label lblCxc = assertInstanceOf(Label.class, cxc.root().lookup("#lblContadorCxc"));
        assertTrue(lblCxc.getText().contains("cancelada"));

        Path cxcXlsx = tempDir.resolve("CXC_ELEGIDO.xlsx");
        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(cxcXlsx.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(cxc.controller()::handleExportarExcel);
        esperarArchivoConDatos(cxcXlsx, Duration.ofSeconds(10));

        // 3) Cuentas por Pagar
        LoadedView<CuentasPorPagarController> cxp = cargarVista(
                "/com/foodcore/view/CuentasPorPagarView.fxml", "04_cxp_export_buttons.png");
        assertInstanceOf(Button.class, cxp.root().lookup("#btnExportarPdf"));
        assertInstanceOf(Button.class, cxp.root().lookup("#btnExportarExcel"));
        assertInstanceOf(Button.class, cxp.root().lookup("#btnExportarCsv"));

        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.empty());
        JavaFxTestSupport.runOnFxThreadAndWait(cxp.controller()::handleExportarPdf);
        Label lblCxp = assertInstanceOf(Label.class, cxp.root().lookup("#lblContadorCxp"));
        assertTrue(lblCxp.getText().contains("cancelada"));

        Path cxpCsv = tempDir.resolve("CXP_ELEGIDO.csv");
        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(cxpCsv.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(cxp.controller()::handleExportarCsv);
        esperarArchivoConDatos(cxpCsv, Duration.ofSeconds(10));
    }

    private record LoadedView<T>(Parent root, T controller) {
    }

    private <T> LoadedView<T> cargarVista(String fxmlPath, String screenshotName) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<T> ctrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                Parent root = loader.load();
                new Scene(root, 1240, 780);
                root.applyCss();
                root.layout();
                rootRef.set(root);
                ctrlRef.set(loader.getController());
                if (screenshotName != null) {
                    guardarSnapshot(root, screenshotName);
                }
            } catch (Exception e) {
                throw new RuntimeException("Fallo cargando " + fxmlPath + ": " + e.getMessage(), e);
            }
        });
        return new LoadedView<>(rootRef.get(), ctrlRef.get());
    }

    private static void esperarArchivoConDatos(Path path, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(path) && Files.size(path) > 0) {
                return;
            }
            Thread.sleep(60L);
        }
        assertTrue(Files.exists(path) && Files.size(path) > 0,
                "Se esperaba archivo exportado con contenido en: " + path);
    }

    private static void guardarSnapshot(Parent root, String filename) throws Exception {
        WritableImage fxImage = root.snapshot(new SnapshotParameters(), null);
        int width = Math.max(1, (int) fxImage.getWidth());
        int height = Math.max(1, (int) fxImage.getHeight());
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(bufferedImage, "png", SCREENSHOT_DIR.resolve(filename).toFile());
    }
}
