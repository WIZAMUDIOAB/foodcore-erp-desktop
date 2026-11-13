package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación visual y estructural de Fase 3 (SPEC-FOODCORE-v4.6.0):
 * tarjetas KPI blancas elevadas en vistas secundarias y ficha 360° ordenada en ClientesView.
 */
class Fase3UiPolishVisualTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "fase3-screenshots");

    @BeforeAll
    static void setUpClass() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @Test
    @DisplayName("3.1 Tarjetas KPI en MiHorario, Asistencia, Reservas y ReportesAvanzados cumplen diseño elevado")
    void testTarjetasKpiEnCuatroVistasSecundarias() throws Exception {
        String cssContent = Files.readString(Path.of("src", "main", "resources", "css", "foodcore.css"));
        assertTrue(cssContent.contains(".kpi-card-secondary"), "CSS debe definir .kpi-card-secondary");
        assertTrue(cssContent.contains("#FFFFFF"), "CSS debe usar fondo blanco #FFFFFF");
        assertTrue(cssContent.contains("dropshadow("), "CSS debe definir sombra dropshadow");
        assertTrue(cssContent.contains("-fx-font-size: 24px;"), "CSS debe configurar JetBrains Mono a 24px");

        verificarVistaKpi("/com/foodcore/view/MiHorarioView.fxml", 4,
                List.of("lblMisHorasMes", "lblMisDiasTrabajados", "lblMisTardanzasMin", "lblMiSueldoBase"),
                "01_mi_horario_kpi_v460.png");

        verificarVistaKpi("/com/foodcore/view/AsistenciaView.fxml", 4,
                List.of("lblKpiActivos", "lblKpiPuntuales", "lblKpiTardanzas", "lblKpiHorasTotales"),
                "02_asistencia_kpi_v460.png");

        verificarVistaKpi("/com/foodcore/view/ReservasView.fxml", 4,
                List.of("lblKpiTotalReservas", "lblKpiPersonas", "lblKpiMesasComprometidas", "lblKpiConfirmadas"),
                "03_reservas_kpi_v460.png");

        verificarVistaKpi("/com/foodcore/view/ReportesAvanzadosView.fxml", 3,
                List.of("lblKpiMontoTotal", "lblKpiPromedio", "lblKpiTopItem"),
                "04_reportes_avanzados_kpi_v460.png");
    }

    @Test
    @DisplayName("3.2 Ficha del Cliente en .surface-card con secciones Overline y botones inferiores fillWidth=true")
    void testFichaClienteOrdenadaConOverlineYBotonesAlFondo() throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ClientesView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1240, 820);
                root.applyCss();
                root.layout();
                rootRef.set(root);
                guardarSnapshot(root, "05_clientes_ficha_v460.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Parent root = rootRef.get();
        VBox panelFicha = assertInstanceOf(VBox.class, root.lookup("#panelFichaCliente"),
                "Debe existir #panelFichaCliente");
        assertTrue(panelFicha.getStyleClass().contains("surface-card"),
                "El panel derecho de Clientes debe usar la clase .surface-card");
        assertTrue(panelFicha.isFillWidth(), "El panel derecho debe mantener fillWidth=true");

        Set<Node> overlines = panelFicha.lookupAll(".text-overline");
        List<String> titulos = overlines.stream()
                .filter(Label.class::isInstance)
                .map(n -> ((Label) n).getText())
                .toList();
        assertTrue(titulos.contains("DATOS GENERALES"), "Debe incluir sección Overline DATOS GENERALES");
        assertTrue(titulos.contains("FINANZAS"), "Debe incluir sección Overline FINANZAS");
        assertTrue(titulos.contains("FIDELIZACIÓN"), "Debe incluir sección Overline FIDELIZACIÓN");

        VBox boxAcciones = assertInstanceOf(VBox.class, root.lookup("#boxAccionesCliente"),
                "Debe existir #boxAccionesCliente");
        assertTrue(boxAcciones.isFillWidth(), "Los botones inferiores deben usar fillWidth=true");

        Button btnEstadoCuenta = assertInstanceOf(Button.class, root.lookup("#btnEstadoCuentaPdf"));
        Button btnEditar = assertInstanceOf(Button.class, root.lookup("#btnEditarCliente"));
        assertNotNull(btnEstadoCuenta);
        assertNotNull(btnEditar);
        assertTrue(panelFicha.lookupAll(".button").containsAll(List.of(btnEstadoCuenta, btnEditar)),
                "Los botones Estado de Cuenta y Editar Datos deben vivir dentro de #panelFichaCliente");
        assertTrue(panelFicha.getBoundsInParent().getHeight() <= 700,
                "La ficha de cliente debe caber holgadamente sin recortar botones: altura="
                        + panelFicha.getBoundsInParent().getHeight());
    }

    private void verificarVistaKpi(String fxmlPath, int tarjetasEsperadas, List<String> valueIds, String screenshotName)
            throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();

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
                guardarSnapshot(root, screenshotName);
            } catch (Exception e) {
                throw new RuntimeException("Fallo cargando " + fxmlPath + ": " + e.getMessage(), e);
            }
        });

        Parent root = rootRef.get();
        Set<Node> kpiCards = root.lookupAll(".kpi-card-secondary");
        assertEquals(tarjetasEsperadas, kpiCards.size(),
                fxmlPath + " debe renderizar exactamente " + tarjetasEsperadas + " tarjetas KPI");

        for (Node card : kpiCards) {
            assertInstanceOf(DropShadow.class, card.getEffect(),
                    fxmlPath + " debe aplicar efecto DropShadow en cada tarjeta KPI");
            Set<Node> iconos = card.lookupAll(".ikonli-font-icon");
            assertFalse(iconos.isEmpty(),
                    fxmlPath + " debe incluir FontIcon dentro del círculo superior derecho de la tarjeta");
            assertTrue(iconos.stream().allMatch(FontIcon.class::isInstance));
        }

        for (String id : valueIds) {
            Label valLabel = assertInstanceOf(Label.class, root.lookup("#" + id),
                    "Debe existir #" + id + " en " + fxmlPath);
            boolean tieneClaseMono24 = valLabel.getStyleClass().stream()
                    .anyMatch(c -> c.startsWith("kpi-value-24"));
            assertTrue(tieneClaseMono24,
                    "#" + id + " en " + fxmlPath + " debe aplicar estilo kpi-value-24 (JetBrains Mono 24px)");
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
