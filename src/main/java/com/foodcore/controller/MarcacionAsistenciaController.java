package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.exception.NegocioException;
import com.foodcore.service.AuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class MarcacionAsistenciaController {

    @FXML private TextField txtDni;
    @FXML private Label lblEstado;

    private final AuthService authService;

    public MarcacionAsistenciaController() {
        this(AppContainer.getDefault().authService());
    }

    public MarcacionAsistenciaController(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService no puede ser nulo");
    }

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
            String resultado = authService.marcarAsistenciaInteligente(dni);
            lblEstado.getStyleClass().removeAll("fc-status-ok", "fc-status-error");
            lblEstado.getStyleClass().add("fc-status-ok");
            lblEstado.setText(resultado);
            txtDni.clear();
        } catch (NegocioException e) {
            lblEstado.getStyleClass().removeAll("fc-status-ok", "fc-status-error");
            lblEstado.getStyleClass().add("fc-status-error");
            lblEstado.setText(e.getMessage());
        } catch (Exception e) {
            lblEstado.getStyleClass().removeAll("fc-status-ok", "fc-status-error");
            lblEstado.getStyleClass().add("fc-status-error");
            lblEstado.setText("Error al registrar marcación: " + e.getMessage());
        }
    }
}
