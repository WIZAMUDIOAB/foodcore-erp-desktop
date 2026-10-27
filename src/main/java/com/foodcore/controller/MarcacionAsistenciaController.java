package com.foodcore.controller;

import com.foodcore.exception.NegocioException;
import com.foodcore.service.AuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class MarcacionAsistenciaController {

    @FXML private TextField txtDni;
    @FXML private Label lblEstado;

    private final AuthService authService = new AuthService();

    @FXML
    public void handleNumClick(ActionEvent event) {
        Button b = (Button) event.getSource();
        if (txtDni.getText().length() < 8) {
            txtDni.setText(txtDni.getText() + b.getText());
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        txtDni.clear();
        lblEstado.setText("");
    }

    @FXML
    public void handleBackspace(ActionEvent event) {
        String act = txtDni.getText();
        if (!act.isEmpty()) {
            txtDni.setText(act.substring(0, act.length() - 1));
        }
    }

    @FXML
    public void handleRegistrar(ActionEvent event) {
        String dni = txtDni.getText();
        try {
            authService.marcarAsistenciaRapida(dni);
            String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            lblEstado.setStyle("-fx-text-fill: #2a9d8f;");
            lblEstado.setText("¡Marcación Exitosa! Registrado a las " + hora);
            txtDni.clear();
        } catch (NegocioException e) {
            lblEstado.setStyle("-fx-text-fill: #e63946;");
            lblEstado.setText(e.getMessage());
        } catch (Exception e) {
            lblEstado.setStyle("-fx-text-fill: #e63946;");
            lblEstado.setText("Error al registrar marcación: " + e.getMessage());
        }
    }
}
