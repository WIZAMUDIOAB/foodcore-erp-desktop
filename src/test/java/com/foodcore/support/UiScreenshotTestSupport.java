package com.foodcore.support;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utilidad compartida de pruebas UI JavaFX para cargar vistas con estilos FoodCore
 * y capturar evidencias PNG en {@code target/v510_evidence}.
 */
public final class UiScreenshotTestSupport {

    public static final Path EVIDENCE_DIR = Path.of("target", "v510_evidence");

    private UiScreenshotTestSupport() {
    }

    /**
     * Resultado de cargar una vista FXML junto con su escena estilizada y controlador.
     *
     * @param <T> Tipo del controlador JavaFX.
     */
    public record VistaCargada<T>(Parent root, Scene scene, T controller) {
    }

    /**
     * Carga un FXML aplicando el tema PrimerLight, fuentes y hojas de estilo FoodCore.
     * Debe invocarse dentro de {@link JavaFxTestSupport#runOnFxThreadAndWait(Runnable)}.
     *
     * @param fxmlPath Ruta del recurso FXML en el classpath.
     * @param width    Ancho inicial de escena.
     * @param height   Alto inicial de escena.
     * @param <T>      Tipo del controlador.
     * @return Instancia cargada lista para interacción y captura.
     * @throws IOException si falla la carga del recurso FXML.
     */
    public static <T> VistaCargada<T> cargarVista(String fxmlPath, double width, double height) throws IOException {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        App.cargarFuentesSistema();
        FXMLLoader loader = new FXMLLoader(UiScreenshotTestSupport.class.getResource(fxmlPath));
        Parent root = loader.load();
        Scene scene = new Scene(root, width, height);
        App.applyStylesheets(scene);
        root.applyCss();
        root.layout();
        return new VistaCargada<>(root, scene, loader.getController());
    }

    /**
     * Renderiza el nodo raíz como imagen PNG en {@code target/v510_evidence/<filename>}.
     *
     * @param root     Nodo raíz de la escena a capturar.
     * @param filename Nombre de archivo PNG.
     * @throws IOException si falla la escritura en disco.
     */
    public static void capturarEvidencia(Parent root, String filename) throws IOException {
        capturarEvidencia(EVIDENCE_DIR, root, filename);
    }

    /** Captura una vista JavaFX en PNG dentro de un directorio de evidencia específico. */
    public static void capturarEvidencia(Path evidenceDir, Parent root, String filename) throws IOException {
        Files.createDirectories(evidenceDir);
        root.applyCss();
        root.layout();
        normalizarAnimaciones(root);

        WritableImage fxImage = root.snapshot(new SnapshotParameters(), null);
        int width = Math.max(1, (int) fxImage.getWidth());
        int height = Math.max(1, (int) fxImage.getHeight());
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        Path outFile = evidenceDir.resolve(filename);
        ImageIO.write(buffered, "png", outFile.toFile());
        assertTrue(Files.exists(outFile) && Files.size(outFile) > 2000,
                "La captura PNG debe existir con contenido válido: " + outFile.toAbsolutePath());
    }

    private static void normalizarAnimaciones(Node node) {
        if (node == null) {
            return;
        }
        if (!node.isDisabled() && !node.opacityProperty().isBound()) {
            node.setOpacity(1.0);
        }
        if (!node.scaleXProperty().isBound()) {
            node.setScaleX(1.0);
        }
        if (!node.scaleYProperty().isBound()) {
            node.setScaleY(1.0);
        }
        if (node instanceof ScrollPane sp) {
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                normalizarAnimaciones(child);
            }
        }
    }
}
