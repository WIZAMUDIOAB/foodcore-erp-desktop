package com.foodcore.controller;

import com.foodcore.model.Caja;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.Optional;

public class ArqueoCajaController {

    @FXML private Label lblNombreCaja;
    @FXML private Label lblFondoApertura;
    @FXML private Label lblTotalVentas;
    @FXML private Label lblTotalEsperado;
    @FXML private TextField txtEfectivoFisico;
    @FXML private Label lblDiferencia;

    private final CajaService cajaService = new CajaService();
    private Caja cajaActual;
    private BigDecimal totalEsperado = BigDecimal.ZERO;

    @FXML
    public void initialize() {
        cargarDatosCaja();
    }

    private void cargarDatosCaja() {
        new Thread(() -> {
            try {
                Optional<Caja> opt = cajaService.obtenerCajaActiva();
                if (opt.isPresent()) {
                    cajaActual = opt.get();
                    BigDecimal ventas = cajaService.calcularVentasTurno(cajaActual.getIdCaja());
                    totalEsperado = cajaActual.getMontoApertura().add(ventas);

                    Platform.runLater(() -> {
                        lblNombreCaja.setText(cajaActual.getNombreCaja());
                        lblFondoApertura.setText("S/ " + cajaActual.getMontoApertura());
                        lblTotalVentas.setText("S/ " + ventas);
                        lblTotalEsperado.setText("S/ " + totalEsperado);
                    });
                } else {
                    Platform.runLater(() -> lblNombreCaja.setText("No hay turno de caja abierto actualmente."));
                }
            } catch (Exception e) {
                Platform.runLater(() -> lblNombreCaja.setText("Error al consultar caja: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void calcularDiferencia() {
        try {
            String texto = txtEfectivoFisico.getText().trim();
            if (texto.isEmpty()) {
                lblDiferencia.setText("S/ 0.00");
                return;
            }
            BigDecimal fisico = new BigDecimal(texto);
            BigDecimal dif = fisico.subtract(totalEsperado);

            lblDiferencia.setText("S/ " + dif);
            if (dif.compareTo(BigDecimal.ZERO) < 0) {
                lblDiferencia.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e63946;");
            } else {
                lblDiferencia.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2a9d8f;");
            }
        } catch (NumberFormatException e) {
            lblDiferencia.setText("Monto inválido");
        }
    }

    @FXML
    public void handleCerrarCaja() {
        if (cajaActual == null) {
            mostrarAlerta("Aviso", "No hay caja abierta para cerrar.", Alert.AlertType.WARNING);
            return;
        }

        try {
            BigDecimal fisico = new BigDecimal(txtEfectivoFisico.getText().trim());
            int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
            cajaService.cerrarTurnoCaja(cajaActual.getIdCaja(), idUsuario, fisico);

            mostrarAlerta("Caja Cerrada", "El turno de caja se cerró exitosamente.", Alert.AlertType.INFORMATION);
            Stage stage = (Stage) txtEfectivoFisico.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            mostrarAlerta("Error al Cerrar Caja", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }
}
