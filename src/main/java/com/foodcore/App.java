package com.foodcore;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Punto de entrada principal para el ERP de Escritorio FoodCore.
 */
public class App extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        // Aplicar tema moderno AtlantaFX (PrimerLight)
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

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
            e.printStackTrace();
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
