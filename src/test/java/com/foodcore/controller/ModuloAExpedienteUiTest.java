package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.impl.DocumentoEmpleadoDAOImpl;
import com.foodcore.dao.impl.ExpedienteUsuarioDAOImpl;
import com.foodcore.model.DocumentoEmpleado;
import com.foodcore.service.ExpedienteEmpleadoService;
import com.foodcore.service.impl.ExpedienteEmpleadoServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación UI y capturas del Módulo A (Expediente de Personal + Alertas 30 días en Dashboard).
 */
class ModuloAExpedienteUiTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "v470-screenshots");

    @BeforeAll
    static void setUpClass() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @AfterEach
    void limpiarFileChooser() {
        ExportFileChooserHelper.clearTestSelectorOverride();
    }

    @Test
    @DisplayName("Módulo A: ExpedienteEmpleadoView con 3 Tabs, subida PDF via FileChooser y captura")
    void testExpedienteEmpleadoTresTabsYSubidaPdf(@TempDir Path tempDir) throws Exception {
        Path pdfDemo = tempDir.resolve("contrato_temporal_ui.pdf");
        Files.write(pdfDemo, "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));

        ExpedienteEmpleadoService serviceTemp = new ExpedienteEmpleadoServiceImpl(
                new ExpedienteUsuarioDAOImpl(), new DocumentoEmpleadoDAOImpl(), tempDir.resolve("uploads"));
        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(pdfDemo.toFile()));

        AtomicReference<ExpedienteEmpleadoController> ctrlRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Long> docSubidoId = new AtomicReference<>();

        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                    App.cargarFuentesSistema();
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/com/foodcore/view/ExpedienteEmpleadoView.fxml"));
                    loader.setControllerFactory(type -> {
                        if (type == ExpedienteEmpleadoController.class) {
                            return new ExpedienteEmpleadoController(serviceTemp);
                        }
                        try {
                            return type.getDeclaredConstructor().newInstance();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                    Parent root = loader.load();
                    ExpedienteEmpleadoController ctrl = loader.getController();
                    ctrlRef.set(ctrl);
                    rootRef.set(root);

                    new Scene(root, 1280, 820);
                    root.applyCss();
                    root.layout();

                    assertEquals(3, ctrl.getTabPaneExpediente().getTabs().size());
                    ctrl.getTabPaneExpediente().getSelectionModel().select(0);
                    root.layout();
                    guardarSnapshot(root, "01_expediente_tab_personales_v470.png");

                    ctrl.getTabPaneExpediente().getSelectionModel().select(1);
                    root.layout();
                    guardarSnapshot(root, "02_expediente_tab_laborales_v470.png");

                    ctrl.getTabPaneExpediente().getSelectionModel().select(2);
                    ctrl.handleSubirPdf();
                    List<DocumentoEmpleado> docs = ctrl.getTblDocumentos().getItems();
                    docs.stream()
                            .filter(d -> "contrato_temporal_ui.pdf".equals(d.getNombreArchivo()))
                            .findFirst()
                            .ifPresent(d -> docSubidoId.set(d.getIdDocumento()));
                    root.layout();
                    guardarSnapshot(root, "03_expediente_tab_documentos_v470.png");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            ExpedienteEmpleadoController ctrl = ctrlRef.get();
            assertEquals("Datos Personales", ctrl.getTabPaneExpediente().getTabs().get(0).getText());
            assertEquals("Datos Laborales", ctrl.getTabPaneExpediente().getTabs().get(1).getText());
            assertEquals("Documentos", ctrl.getTabPaneExpediente().getTabs().get(2).getText());
            assertFalse(ctrl.getTblColaboradores().getItems().isEmpty());
            assertTrue(docSubidoId.get() != null && docSubidoId.get() > 0,
                    "El PDF seleccionado desde ExportFileChooserHelper debe quedar registrado");
            assertTrue(ctrl.getLblMensajeEstado().getText().contains("PDF adjuntado correctamente"));
        } finally {
            if (docSubidoId.get() != null) {
                serviceTemp.eliminarDocumento(docSubidoId.get());
            }
        }
    }

    @Test
    @DisplayName("Módulo A: Dashboard incluye alertas de vencimiento 30 días de documento_empleado")
    void testDashboardIncluyeAlertasDocumentosEmpleado() throws Exception {
        AtomicReference<List<DashboardController.AlertaVencimiento>> alertasRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                Parent root = loader.load();
                DashboardController ctrl = loader.getController();
                rootRef.set(root);

                try (Connection conn = ConexionBD.getConnection()) {
                    alertasRef.set(ctrl.obtenerAlertasVencimiento30Dias(conn));
                }

                new Scene(root, 1280, 820);
                root.applyCss();
                root.layout();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        for (int i = 0; i < 30; i++) {
            AtomicReference<String> badgeText = new AtomicReference<>("");
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                javafx.scene.control.Label lbl = (javafx.scene.control.Label) rootRef.get()
                        .lookup("#lblCountAlertasPersonal");
                if (lbl != null) {
                    badgeText.set(lbl.getText());
                }
            });
            if (!badgeText.get().startsWith("0 ")) {
                break;
            }
            Thread.sleep(150);
        }

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = rootRef.get();
                if (root instanceof javafx.scene.control.ScrollPane sp) {
                    sp.setVvalue(1.0);
                } else {
                    root.lookupAll(".scroll-pane").stream()
                            .filter(javafx.scene.control.ScrollPane.class::isInstance)
                            .map(javafx.scene.control.ScrollPane.class::cast)
                            .findFirst()
                            .ifPresent(sp -> sp.setVvalue(1.0));
                }
                root.applyCss();
                root.layout();
                guardarSnapshot(root, "04_dashboard_alertas_documentos_v470.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        List<DashboardController.AlertaVencimiento> alertas = alertasRef.get();
        assertFalse(alertas.isEmpty(), "El Dashboard debe reportar alertas vigentes");
        assertTrue(alertas.stream().noneMatch(a -> a.diasRestantes() > 30 || a.diasRestantes() < 0),
                "Solo deben mostrarse vencimientos dentro de los próximos 30 días");
        assertTrue(alertas.stream().anyMatch(a -> a.documento().contains("certificado_manipulacion_cajero.pdf")),
                "Debe incluir el documento PDF de documento_empleado próximo a vencer");
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
