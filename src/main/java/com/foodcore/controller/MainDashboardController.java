package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainDashboardController {

    @FXML private StackPane contentArea;
    @FXML private Label lblUsuarioLogueado;
    @FXML private Button btnNavPOS;
    @FXML private Button btnNavSalon;
    @FXML private Button btnNavKDS;
    @FXML private Button btnNavReportes;
    @FXML private Button btnNavAsistencia;

    @FXML
    public void initialize() {
        Usuario act = AuthService.getUsuarioSesionActual();
        if (act != null) {
            lblUsuarioLogueado.setText("Usuario: " + act.getUsername() + " (" + act.getNombreRol() + ")");
        }
        // Cargar vista inicial según convenga
        cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
    }

    @FXML
    public void handleNavPOS(ActionEvent event) {
        cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
    }

    @FXML
    public void handleNavSalon(ActionEvent event) {
        cargarVista("/com/foodcore/view/SalonMesasView.fxml", btnNavSalon);
    }

    @FXML
    public void handleNavKDS(ActionEvent event) {
        cargarVista("/com/foodcore/view/KitchenDisplayView.fxml", btnNavKDS);
    }

    @FXML
    public void handleNavReportes(ActionEvent event) {
        cargarVista("/com/foodcore/view/ReportesView.fxml", btnNavReportes);
    }

    @FXML
    public void handleNavAsistencia(ActionEvent event) {
        cargarVista("/com/foodcore/view/MarcacionAsistenciaView.fxml", btnNavAsistencia);
    }

    private void cargarVista(String fxmlPath, Button navBtnActivo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentArea.getChildren().setAll(view);
            resetEstilosNav();
            if (navBtnActivo != null) {
                navBtnActivo.setStyle("-fx-background-color: #457b9d; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6px;");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void resetEstilosNav() {
        String base = "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 13px; -fx-cursor: hand;";
        btnNavPOS.setStyle(base);
        btnNavSalon.setStyle(base);
        btnNavKDS.setStyle(base);
        btnNavReportes.setStyle(base);
        btnNavAsistencia.setStyle(base);
    }

    @FXML
    public void handleCerrarSesion(ActionEvent event) {
        AuthService.logout();
        try {
            App.setRoot("LoginView");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
