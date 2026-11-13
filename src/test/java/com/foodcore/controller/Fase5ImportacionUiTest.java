package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FileChooserRequest;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación visual y de controles UI de Fase 5 (SPEC-FOODCORE-v4.6.0):
 * botones Descargar Plantilla Excel, Importar desde Excel, ProgressIndicator y FileChooser .xlsx/.csv.
 */
class Fase5ImportacionUiTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "fase5-screenshots");

    private String prevExportDir;
    private String prevImportDir;

    @BeforeAll
    static void setUpClass() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @BeforeEach
    void respaldoPrefs() {
        Preferences prefs = Preferences.userNodeForPackage(ExportFileChooserHelper.class);
        prevExportDir = prefs.get(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_EXPORT, null);
        prevImportDir = prefs.get(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_IMPORT, null);
        ExportFileChooserHelper.clearTestSelectorOverride();
    }

    @AfterEach
    void restaurarPrefs() {
        ExportFileChooserHelper.clearTestSelectorOverride();
        Preferences prefs = Preferences.userNodeForPackage(ExportFileChooserHelper.class);
        restaurarClave(prefs, ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_EXPORT, prevExportDir);
        restaurarClave(prefs, ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_IMPORT, prevImportDir);
    }

    private static void restaurarClave(Preferences prefs, String key, String val) {
        if (val == null) {
            prefs.remove(key);
        } else {
            prefs.put(key, val);
        }
    }

    @Test
    @DisplayName("5.2/5.3 MenuView e InventarioView exponen botones de plantilla, importación .xlsx/.csv y ProgressIndicator")
    void debeRenderizarBotonesPlantillaEImportacionYDescargar(@TempDir Path tempDir) throws Exception {
        LoadedView<MenuController> menuView = cargarVista(
                "/com/foodcore/view/MenuView.fxml", "01_menu_importacion_v460.png");
        Button btnPlantillaMenu = assertInstanceOf(Button.class, menuView.root().lookup("#btnDescargarPlantillaMenu"));
        Button btnImportarMenu = assertInstanceOf(Button.class, menuView.root().lookup("#btnImportarMenu"));
        ProgressIndicator pbMenu = assertInstanceOf(
                ProgressIndicator.class, menuView.root().lookup("#pbImportacionMenu"));
        Label lblMenu = assertInstanceOf(Label.class, menuView.root().lookup("#lblResumenImportacionMenu"));

        assertEquals("Descargar Plantilla Excel", btnPlantillaMenu.getText());
        assertEquals("Importar desde Excel", btnImportarMenu.getText());
        assertFalse(pbMenu.isVisible(), "ProgressIndicator inicia oculto");

        // Descarga plantilla Menu vía FileChooser
        Path plantillaMenu = tempDir.resolve("plantilla_menu.xlsx");
        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(plantillaMenu.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(() -> menuView.controller().handleDescargarPlantillaExcel(null));
        esperarLabelContiene(lblMenu, "Plantilla guardada", Duration.ofSeconds(10));
        assertTrue(Files.exists(plantillaMenu) && Files.size(plantillaMenu) > 0);

        // Simula cancelar importación Menu y verifica filtros .xlsx/.csv
        AtomicReference<FileChooserRequest> importReqMenu = new AtomicReference<>();
        ExportFileChooserHelper.setTestSelectorOverride(req -> {
            importReqMenu.set(req);
            return Optional.empty();
        });
        JavaFxTestSupport.runOnFxThreadAndWait(() -> menuView.controller().handleImportarDesdeExcel(null));
        List<String> extsMenu = importReqMenu.get().filtros().stream()
                .flatMap(f -> f.getExtensions().stream())
                .toList();
        assertTrue(extsMenu.containsAll(List.of("*.xlsx", "*.csv")));
        assertTrue(lblMenu.getText().contains("cancelada"));

        // InventarioView
        LoadedView<InventarioController> invView = cargarVista(
                "/com/foodcore/view/InventarioView.fxml", "02_inventario_importacion_v460.png");
        Button btnPlantillaIns = assertInstanceOf(
                Button.class, invView.root().lookup("#btnDescargarPlantillaInsumos"));
        Button btnImportarIns = assertInstanceOf(Button.class, invView.root().lookup("#btnImportarInsumos"));
        ProgressIndicator pbIns = assertInstanceOf(
                ProgressIndicator.class, invView.root().lookup("#pbImportacionInsumos"));
        Label lblIns = assertInstanceOf(Label.class, invView.root().lookup("#lblResumenImportacionInsumos"));

        assertEquals("Descargar Plantilla Excel", btnPlantillaIns.getText());
        assertEquals("Importar desde Excel", btnImportarIns.getText());
        assertFalse(pbIns.isVisible());

        Path plantillaIns = tempDir.resolve("plantilla_insumos.xlsx");
        ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(plantillaIns.toFile()));
        JavaFxTestSupport.runOnFxThreadAndWait(() -> invView.controller().handleDescargarPlantillaExcel(null));
        esperarLabelContiene(lblIns, "Plantilla guardada", Duration.ofSeconds(10));
        assertTrue(Files.exists(plantillaIns) && Files.size(plantillaIns) > 0);
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
                guardarSnapshot(root, screenshotName);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando " + fxmlPath + ": " + e.getMessage(), e);
            }
        });
        return new LoadedView<>(rootRef.get(), ctrlRef.get());
    }

    private static void esperarLabelContiene(Label label, String expected, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        String[] text = new String[1];
        while (System.nanoTime() < deadline) {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> text[0] = label.getText());
            if (text[0] != null && text[0].contains(expected)) {
                return;
            }
            Thread.sleep(50L);
        }
        assertTrue(text[0] != null && text[0].contains(expected),
                "Se esperaba texto '" + expected + "' en label, actual: " + text[0]);
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
