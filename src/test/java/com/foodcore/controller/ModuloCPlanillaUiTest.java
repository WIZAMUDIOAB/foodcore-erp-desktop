package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.model.MovimientoPago;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.PlanillaService;
import com.foodcore.service.impl.PlanillaServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ExportFileChooserHelper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación UI y capturas del Módulo C (Pagos, Movimientos y Planilla Administrativa).
 */
class ModuloCPlanillaUiTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "v470-screenshots");

    @BeforeAll
    static void setUpClass() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @AfterEach
    void limpiar() {
        ExportFileChooserHelper.clearTestSelectorOverride();
    }

    @Test
    @DisplayName("Módulo C: PlanillaView registra movimiento, consolida neto, exporta PDF/Excel y genera captura")
    void testPlanillaViewMovimientosYExportacion(@TempDir Path tempDir) throws Exception {
        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setUsername("admin");
        admin.setNombreEmpleado("Administrador General");
        admin.setNombreRol("ADMIN");
        AuthService.setUsuarioSesionActual(admin);

        PlanillaService service = new PlanillaServiceImpl();
        AtomicReference<PlanillaController> ctrlRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Long> idMovimientoUi = new AtomicReference<>();

        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                    App.cargarFuentesSistema();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/PlanillaView.fxml"));
                    Parent root = loader.load();
                    PlanillaController ctrl = loader.getController();
                    new Scene(root, 1280, 820);
                    ctrlRef.set(ctrl);
                    rootRef.set(root);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            PlanillaController ctrl = ctrlRef.get();
            JavaFxTestSupport.awaitCondition(
                    () -> ctrl.getCbColaborador().getValue() != null && !ctrl.getListaPlanilla().isEmpty(),
                    10L,
                    "La carga inicial asíncrona de PlanillaView debe poblar colaboradores y planilla");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getCbTipoMovimiento().setValue("BONO");
                ctrl.getTxtConcepto().setText("Bono cierre inventario UI");
                ctrl.getTxtMonto().setText("125.00");
                ctrl.handleRegistrarMovimiento();
            });

            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnRegistrarMovimiento().isDisabled()
                            && ctrl.getListaMovimientos().stream()
                                    .anyMatch(m -> "Bono cierre inventario UI".equals(m.getConcepto())),
                    10L,
                    "El movimiento registrado de forma asíncrona debe reflejarse en listaMovimientos");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getListaMovimientos().stream()
                        .filter(m -> "Bono cierre inventario UI".equals(m.getConcepto()))
                        .findFirst()
                        .map(MovimientoPago::getIdMovimiento)
                        .ifPresent(idMovimientoUi::set);

                ExportFileChooserHelper.setTestSelectorOverride(
                        req -> Optional.of(tempDir.resolve(req.nombreSugerido()).toFile()));
                ctrl.handleExportarExcel();
                ctrl.handleExportarPdf();
            });

            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnExportarExcel().isDisabled()
                            && !ctrl.getBtnExportarPdf().isDisabled()
                            && existeArchivoConExtension(tempDir, ".xlsx")
                            && existeArchivoConExtension(tempDir, ".pdf"),
                    10L,
                    "Las exportaciones asíncronas de Excel y PDF deben completarse en tempDir");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Parent root = rootRef.get();
                    root.applyCss();
                    root.layout();
                    guardarSnapshot(root, "07_planilla_pagos_movimientos_v470.png");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertFalse(ctrl.getListaPlanilla().isEmpty());
            assertFalse(ctrl.getListaMovimientos().isEmpty());
            assertTrue(idMovimientoUi.get() != null && idMovimientoUi.get() > 0);
            assertTrue(ctrl.getLblKpiNetoPagar().getText().startsWith("S/ "));
            assertTrue(existeArchivoConExtension(tempDir, ".xlsx"));
            assertTrue(existeArchivoConExtension(tempDir, ".pdf"));
        } finally {
            if (ctrlRef.get() != null) {
                ctrlRef.get().detenerRecursos();
            }
            if (idMovimientoUi.get() != null) {
                service.eliminarMovimiento(idMovimientoUi.get());
            }
        }
    }

    private static boolean existeArchivoConExtension(Path dir, String ext) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(ext));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
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
