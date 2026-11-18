package com.foodcore;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.api.ApiServer;
import com.foodcore.config.FlywayMigrator;
import com.foodcore.config.LoggingBootstrap;
import com.foodcore.controller.SplashController;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.foodcore.util.UiAsyncExecutor;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Punto de entrada principal para el ERP de Escritorio FoodCore v1.1.
 */
public class App extends Application {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static final String PREF_KEY_DARK_MODE = "foodcore.ui.darkMode";
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTRADO = new AtomicBoolean(false);
    private static Stage primaryStage;
    private static volatile boolean darkModeActivo = leerPreferenciaModoOscuro();

    @Override
    public void start(Stage stage) {
        LoggingBootstrap.installBridge();
        setPrimaryStage(stage);
        registrarShutdownHookUnaVez();
        stage.setOnCloseRequest(event -> detenerServiciosAplicacion());
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
        if (splash.progressBar() != null) {
            splash.progressBar().progressProperty().bind(bootstrapTask.progressProperty());
        }

        bootstrapTask.setOnSucceeded(ev -> {
            splash.statusLabel().textProperty().unbind();
            if (splash.progressBar() != null) {
                splash.progressBar().progressProperty().unbind();
            }
            cargarVistaLoginInicial(stage, splash);
        });
        bootstrapTask.setOnFailed(ev -> {
            splash.statusLabel().textProperty().unbind();
            if (splash.progressBar() != null) {
                splash.progressBar().progressProperty().unbind();
            }
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
                updateProgress(0.25, 1.0);
                migratorTask.run();

                updateMessage("Iniciando servicios locales…");
                updateProgress(0.65, 1.0);
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
                updateProgress(1.0, 1.0);
                return null;
            }
        };
    }

    private BootstrapSplash crearPantallaSplashBootstrap() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/SplashView.fxml"));
            Parent loaded = loader.load();
            SplashController ctrl = loader.getController();
            if (loaded instanceof StackPane root && ctrl != null) {
                return new BootstrapSplash(
                        root,
                        ctrl.getSpinnerBootstrap(),
                        ctrl.getLblEstadoBootstrap(),
                        ctrl.getProgressBarSplash());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo cargar SplashView.fxml; usando respaldo programático", e);
        }

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setId("spinnerBootstrap");
        spinner.setMaxSize(48.0, 48.0);
        spinner.setPrefSize(48.0, 48.0);

        ProgressBar progressBar = new ProgressBar(0.15);
        progressBar.setId("progressBarSplash");
        progressBar.setPrefWidth(340.0);
        progressBar.getStyleClass().add("splash-progress-bar");

        Label title = new Label("FoodCore ERP");
        title.getStyleClass().add("text-display");

        Label statusLabel = new Label("Migrando base de datos…");
        statusLabel.setId("lblEstadoBootstrap");
        statusLabel.getStyleClass().add("text-body-secondary");
        statusLabel.setWrapText(true);

        VBox card = new VBox(16.0, title, progressBar, spinner, statusLabel);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(440.0);
        card.setMaxHeight(280.0);
        card.getStyleClass().addAll("card", "splash-card");
        card.setStyle("-fx-padding: 36 32;");

        StackPane root = new StackPane(card);
        root.setId("bootstrapSplashRoot");
        root.getStyleClass().add("login-canvas");
        return new BootstrapSplash(root, spinner, statusLabel, progressBar);
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
                stage.setMaximized(true);
            } else {
                applyStylesheets(scene);
                transicionarSplashALogin(scene, splash != null ? splash.root() : null, root,
                        () -> stage.setMaximized(true));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error cargando vista inicial LoginView.fxml", e);
            splash.spinner().setVisible(false);
            splash.spinner().setManaged(false);
            splash.statusLabel().setText("Error cargando vista inicial: " + e.getMessage());
        }
    }

    /**
     * Ejecuta fade-out sobre el Splash y fade-in sobre la vista Login dentro de la escena activa (F2).
     */
    public static void transicionarSplashALogin(
            Scene scene, Parent splashRoot, Parent loginRoot, Runnable onComplete) {
        if (scene == null || loginRoot == null) {
            return;
        }
        if (splashRoot == null) {
            loginRoot.setOpacity(1.0);
            scene.setRoot(loginRoot);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), splashRoot);
        fadeOut.setFromValue(splashRoot.getOpacity());
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);
        fadeOut.setOnFinished(ev -> {
            loginRoot.setOpacity(0.0);
            scene.setRoot(loginRoot);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), loginRoot);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setInterpolator(Interpolator.EASE_BOTH);
            if (onComplete != null) {
                fadeIn.setOnFinished(e2 -> onComplete.run());
            }
            fadeIn.play();
        });
        fadeOut.play();
    }

    private record BootstrapSplash(
            StackPane root, ProgressIndicator spinner, Label statusLabel, ProgressBar progressBar) {
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
     * Extiende PrimerLight cargando foodcore.css después del tema base (§12.1)
     * y opcionalmente dark-theme.css cuando el Modo Oscuro está habilitado (Sub-Fase D - D4).
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
        aplicarModoOscuro(scene, darkModeActivo);
    }

    /**
     * Activa o desactiva {@code /css/dark-theme.css} sobre la escena indicada (D4).
     */
    public static void aplicarModoOscuro(Scene scene, boolean activo) {
        if (scene == null) {
            return;
        }
        URL darkCss = App.class.getResource("/css/dark-theme.css");
        if (darkCss == null) {
            return;
        }
        String darkUrl = darkCss.toExternalForm();
        actualizarStylesheetEnLista(scene.getStylesheets(), darkUrl, activo);
        if (scene.getRoot() != null) {
            actualizarDarkModeEnNodos(scene.getRoot(), darkUrl, activo, true);
        }
    }

    private static void actualizarStylesheetEnLista(
            javafx.collections.ObservableList<String> stylesheets, String darkUrl, boolean activo) {
        if (activo) {
            if (!stylesheets.contains(darkUrl)) {
                stylesheets.add(darkUrl);
            }
        } else {
            stylesheets.remove(darkUrl);
        }
    }

    private static void actualizarDarkModeEnNodos(
            Parent parent, String darkUrl, boolean activo, boolean esRaizEscena) {
        if (!parent.getStylesheets().isEmpty() || esRaizEscena) {
            actualizarStylesheetEnLista(parent.getStylesheets(), darkUrl, activo);
        }
        if (parent instanceof javafx.scene.control.ScrollPane sp && sp.getContent() instanceof Parent content) {
            actualizarDarkModeEnNodos(content, darkUrl, activo, false);
        }
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                actualizarDarkModeEnNodos(childParent, darkUrl, activo, false);
            }
        }
    }

    public static boolean isDarkModeActivo() {
        return darkModeActivo;
    }

    public static void setDarkModeActivo(boolean activo) {
        darkModeActivo = activo;
        guardarPreferenciaModoOscuro(activo);
    }

    private static boolean leerPreferenciaModoOscuro() {
        try {
            return Preferences.userNodeForPackage(App.class).getBoolean(PREF_KEY_DARK_MODE, false);
        } catch (SecurityException ex) {
            LOGGER.log(Level.FINE, "No se pudo leer preferencia de modo oscuro", ex);
            return false;
        }
    }

    private static void guardarPreferenciaModoOscuro(boolean activo) {
        try {
            Preferences.userNodeForPackage(App.class).putBoolean(PREF_KEY_DARK_MODE, activo);
        } catch (SecurityException ex) {
            LOGGER.log(Level.FINE, "No se pudo persistir preferencia de modo oscuro", ex);
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

    public static void registrarShutdownHookUnaVez() {
        if (SHUTDOWN_HOOK_REGISTRADO.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(
                new Thread(App::detenerServiciosAplicacion, "foodcore-app-shutdown")
            );
        }
    }

    /**
     * Detiene de forma ordenada e idempotente el túnel externo, la API HTTP/HTTPS (8080/8443)
     * y el servidor TCP KDS (9090) para liberar todos los puertos al cerrar la aplicación.
     */
    public static void detenerServiciosAplicacion() {
        try {
            com.foodcore.service.TunnelService.detener();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Aviso al detener TunnelService durante el cierre", e);
        }
        try {
            ApiServer.getInstance().detener();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Aviso al detener ApiServer durante el cierre", e);
        }
        try {
            ServidorSockets.getInstance().detener();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Aviso al detener ServidorSockets durante el cierre", e);
        }
    }

    @Override
    public void stop() throws Exception {
        detenerServiciosAplicacion();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
