package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.util.AnimationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;
    @FXML private Button btnLogin;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnLogin);
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        try {
            Usuario u = authService.login(username, password);
            lblMensaje.setVisible(false);

            // Redirigir al Dashboard Principal
            App.setRoot("MainDashboardView");

        } catch (NegocioException e) {
            mostrarError(e.getMessage());
        } catch (Exception e) {
            mostrarError("Error al conectar con la BD MySQL: " + e.getMessage());
        }
    }

    @FXML
    public void handleAbrirAsistencia(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MarcacionAsistenciaView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Control de Asistencia - Marcación por DNI");

            Scene scene = new Scene(root, 490, 560);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            AnimationUtils.animateModalEnter(root);

            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (IOException e) {
            mostrarError("No se pudo abrir la ventana de asistencia: " + e.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        lblMensaje.setText(mensaje);
        lblMensaje.setVisible(true);
    }
}
