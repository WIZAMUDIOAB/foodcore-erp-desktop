package com.foodcore;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.config.FlywayMigrator;
import com.foodcore.util.ServidorSockets;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de entrada principal para el ERP de Escritorio FoodCore.
 */
public class App extends Application {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Paso 2 del bootstrap (§12, R-05): ejecutar migraciones Flyway versionadas
        FlywayMigrator.migrateOnBootstrap();

        // Iniciar Servidor TCP de Sockets en segundo plano para sincronización en tiempo real con Cocina
        try {
            ServidorSockets.getInstance().iniciar();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo iniciar ServidorSockets en el arranque", e);
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1024, 680);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            stage.setTitle("FoodCore ERP - Sistema de Gestión Gastronómica");
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error cargando vista inicial LoginView.fxml", e);
        }
    }

    public static void setRoot(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/foodcore/view/" + fxml + ".fxml"));
        Parent root = loader.load();
        primaryStage.getScene().setRoot(root);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
