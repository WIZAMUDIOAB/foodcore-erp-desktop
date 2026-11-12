package com.foodcore;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.api.ApiServer;
import com.foodcore.config.FlywayMigrator;
import com.foodcore.config.LoggingBootstrap;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.foodcore.util.UiAsyncExecutor;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
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
        LoggingBootstrap.installBridge();
        setPrimaryStage(stage);
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        cargarFuentesSistema();

        BootstrapSplash splash = crearPantallaSplashBootstrap();
        Scene splashScene = new Scene(splash.root(), 1080, 700);
        applyStylesheets(splashScene);

        stage.setTitle("FoodCore ERP v3.0 - Sistema de Gestión Gastronómica");
        stage.setMinWidth(1280);
        stage.setMinHeight(720);
        stage.setScene(splashScene);
        stage.setMaximized(true);
        stage.show();

        Task<Void> bootstrapTask = crearBootstrapTask(
            () -> {
                ConexionBD.validarVariablesEntornoObligatorias();
                FlywayMigrator.migrateOnBootstrap();
            },
            () -> ServidorSockets.getInstance().iniciar(),
            () -> {
                ApiServer.getInstance().iniciar();
                com.foodcore.service.TunnelService.iniciarEnSegundoPlano();
            }
        );
        splash.statusLabel().textProperty().bind(bootstrapTask.messageProperty());

        bootstrapTask.setOnSucceeded(ev -> {
            splash.statusLabel().textProperty().unbind();
            cargarVistaLoginInicial(stage, splash);
        });
        bootstrapTask.setOnFailed(ev -> {
            splash.statusLabel().textProperty().unbind();
            splash.spinner().setVisible(false);
            splash.spinner().setManaged(false);
            Throwable cause = bootstrapTask.getException();
            LOGGER.log(Level.SEVERE, "Error crítico durante el arranque de FoodCore ERP", cause);
            String detail = (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank())
                ? cause.getMessage()
                : "Error inesperado al inicializar servicios.";
            splash.statusLabel().setText("No se pudo iniciar FoodCore ERP: " + detail);
        });

        UiAsyncExecutor.ejecutar(bootstrapTask);
    }

    /**
     * Construye la tarea en segundo plano que ejecuta validación, migración Flyway y servidores locales
     * sin bloquear el JavaFX Application Thread.
     */
    public static Task<Void> crearBootstrapTask(
            UiAsyncExecutor.ThrowingRunnable migratorTask,
            UiAsyncExecutor.ThrowingRunnable socketStarter,
            UiAsyncExecutor.ThrowingRunnable apiStarter) {
        Objects.requireNonNull(migratorTask, "La tarea de migración no puede ser nula.");
        Objects.requireNonNull(socketStarter, "La tarea de sockets no puede ser nula.");
        Objects.requireNonNull(apiStarter, "La tarea de API REST no puede ser nula.");
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Migrando base de datos…");
                migratorTask.run();

                updateMessage("Iniciando servicios locales…");
                try {
                    socketStarter.run();
                } catch (IOException | RuntimeException e) {
                    LOGGER.log(Level.WARNING, "No se pudo iniciar ServidorSockets en el arranque", e);
                }

                try {
                    apiStarter.run();
                } catch (IOException | RuntimeException e) {
                    LOGGER.log(Level.WARNING, "No se pudo iniciar ApiServer en el arranque", e);
                }

                updateMessage("Cargando interfaz de acceso…");
                return null;
            }
        };
    }

    private BootstrapSplash crearPantallaSplashBootstrap() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setId("spinnerBootstrap");
        spinner.setMaxSize(48.0, 48.0);
        spinner.setPrefSize(48.0, 48.0);

        Label title = new Label("FoodCore ERP");
        title.getStyleClass().add("text-display");

        Label statusLabel = new Label("Migrando base de datos…");
        statusLabel.setId("lblEstadoBootstrap");
        statusLabel.getStyleClass().add("text-body-secondary");
        statusLabel.setWrapText(true);

        VBox card = new VBox(16.0, title, spinner, statusLabel);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(440.0);
        card.setMaxHeight(260.0);
        card.getStyleClass().add("login-shell-card");
        card.setStyle("-fx-padding: 36 32; -fx-background-color: white; -fx-background-radius: 16;");

        StackPane root = new StackPane(card);
        root.setId("bootstrapSplashRoot");
        root.getStyleClass().add("login-canvas");
        return new BootstrapSplash(root, spinner, statusLabel);
    }

    private void cargarVistaLoginInicial(Stage stage, BootstrapSplash splash) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
            Parent root = loader.load();
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 1080, 700);
                applyStylesheets(scene);
                stage.setScene(scene);
            } else {
                applyStylesheets(scene);
                scene.setRoot(root);
            }
            stage.setMaximized(true);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error cargando vista inicial LoginView.fxml", e);
            splash.spinner().setVisible(false);
            splash.spinner().setManaged(false);
            splash.statusLabel().setText("Error cargando vista inicial: " + e.getMessage());
        }
    }

    private record BootstrapSplash(StackPane root, ProgressIndicator spinner, Label statusLabel) {
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

    private static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override
    public void stop() throws Exception {
        com.foodcore.service.TunnelService.detener();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
