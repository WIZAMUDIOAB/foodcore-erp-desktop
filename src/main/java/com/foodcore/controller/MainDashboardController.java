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
    @FXML private Button btnNavMantenimiento;

    @FXML
    public void initialize() {
        Usuario act = AuthService.getUsuarioSesionActual();
        if (act != null) {
            lblUsuarioLogueado.setText("Usuario: " + act.getUsername() + " (" + act.getNombreRol() + ")");
            aplicarControlAcceso(act);
        } else {
            cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
        }
    }

    private void aplicarControlAcceso(Usuario user) {
        if (user == null || user.getNombreRol() == null) {
            cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
            return;
        }

        String rol = user.getNombreRol().toUpperCase();

        if (rol.contains("MOZO")) {
            // Mozo: solo acceso a salon de pedidos y marcacion de asistencia
            btnNavPOS.setVisible(false);
            btnNavPOS.setManaged(false);
            btnNavKDS.setVisible(false);
            btnNavKDS.setManaged(false);
            btnNavReportes.setVisible(false);
            btnNavReportes.setManaged(false);
            btnNavMantenimiento.setVisible(false);
            btnNavMantenimiento.setManaged(false);
            cargarVista("/com/foodcore/view/SalonMesasView.fxml", btnNavSalon);
        } else if (rol.contains("COCIN")) {
            // Cocinero: solo acceso al monitor de cocina KDS y asistencia
            btnNavPOS.setVisible(false);
            btnNavPOS.setManaged(false);
            btnNavSalon.setVisible(false);
            btnNavSalon.setManaged(false);
            btnNavReportes.setVisible(false);
            btnNavReportes.setManaged(false);
            btnNavMantenimiento.setVisible(false);
            btnNavMantenimiento.setManaged(false);
            cargarVista("/com/foodcore/view/KitchenDisplayView.fxml", btnNavKDS);
        } else if (rol.contains("CAJERO")) {
            // Cajero: POS, Salon y Asistencia. Sin acceso a reportes ni mantenimiento BD
            btnNavKDS.setVisible(false);
            btnNavKDS.setManaged(false);
            btnNavReportes.setVisible(false);
            btnNavReportes.setManaged(false);
            btnNavMantenimiento.setVisible(false);
            btnNavMantenimiento.setManaged(false);
            cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
        } else {
            // Administrador o Gerente: Acceso completo a todos los modulos
            cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
        }
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

    @FXML
    public void handleNavMantenimiento(ActionEvent event) {
        cargarVista("/com/foodcore/view/MantenimientoBDView.fxml", btnNavMantenimiento);
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
        btnNavMantenimiento.setStyle("-fx-background-color: transparent; -fx-text-fill: #a8dadc; -fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand;");
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
