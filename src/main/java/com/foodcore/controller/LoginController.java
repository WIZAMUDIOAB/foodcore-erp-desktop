package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
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
        // Enfoque inicial
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        try {
            Usuario u = authService.login(username, password);
            lblMensaje.setVisible(false);

            // Redirigir según el rol o al Dashboard Principal
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
            stage.setTitle("Control de Asistencia - Marcación Rápida");
            stage.setScene(new Scene(root, 480, 520));
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
