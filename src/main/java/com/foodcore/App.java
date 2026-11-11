package com.foodcore;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.api.ApiServer;
import com.foodcore.config.FlywayMigrator;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de entrada principal para el ERP de Escritorio FoodCore v1.1.
 */
public class App extends Application {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        ConexionBD.validarVariablesEntornoObligatorias();
        primaryStage = stage;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        cargarFuentesSistema();

        // Paso 2 del bootstrap (§12, R-05): ejecutar migraciones Flyway versionadas
        FlywayMigrator.migrateOnBootstrap();

        // Iniciar Servidor TCP de Sockets en segundo plano para sincronización en tiempo real con Cocina
        try {
            ServidorSockets.getInstance().iniciar();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo iniciar ServidorSockets en el arranque", e);
        }

        // Iniciar Servidor HTTP API REST (Javalin) en puerto 8080 para Tablets PWA (SPEC v4.0 §2)
        try {
            ApiServer.getInstance().iniciar();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo iniciar ApiServer en el arranque", e);
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1080, 700);
            applyStylesheets(scene);

            stage.setTitle("FoodCore ERP v3.0 - Sistema de Gestión Gastronómica");
            stage.setMinWidth(1280);
            stage.setMinHeight(720);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error cargando vista inicial LoginView.fxml", e);
        }
    }

    /**
     * Carga la fuente Inter desde /fonts/ con fallback automático a Segoe UI (§12.3).
     */
    public static void cargarFuentesSistema() {
        String[] fontPaths = {
            "/fonts/Inter-Regular.ttf",
            "/fonts/Inter-SemiBold.ttf",
            "/fonts/Inter-Bold.ttf",
            "/fonts/JetBrainsMono-Regular.ttf"
        };
        for (String path : fontPaths) {
            try (InputStream is = App.class.getResourceAsStream(path)) {
                if (is != null) {
                    Font.loadFont(is, 15);
                }
            } catch (IOException ignored) {
                // Fallback a Segoe UI / Consolas configurado en foodcore.css
            }
        }
    }

    /**
     * Extiende PrimerLight cargando foodcore.css después del tema base (§12.1).
     */
    public static void applyStylesheets(Scene scene) {
        if (scene == null) {
            return;
        }
        URL foodcoreCss = App.class.getResource("/css/foodcore.css");
        if (foodcoreCss != null) {
            String url = foodcoreCss.toExternalForm();
            if (!scene.getStylesheets().contains(url)) {
                scene.getStylesheets().add(url);
            }
        }
    }

    public static void setRoot(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/foodcore/view/" + fxml + ".fxml"));
        Parent root = loader.load();
        if (primaryStage != null && primaryStage.getScene() != null) {
            applyStylesheets(primaryStage.getScene());
            primaryStage.getScene().setRoot(root);
            primaryStage.setMinWidth(1280);
            primaryStage.setMinHeight(720);
            primaryStage.setMaximized(true);
        }
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
