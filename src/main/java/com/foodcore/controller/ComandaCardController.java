package com.foodcore.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;

public class ComandaCardController {

    @FXML private VBox cardContainer;
    @FXML private Label lblNumeroMesa;
    @FXML private Label lblTiempo;
    @FXML private Label lblNumeroComanda;
    @FXML private VBox vboxItems;
    @FXML private Label lblEstado;
    @FXML private Button btnIniciar;
    @FXML private Button btnDespachar;

    private int segundosTranscurridos = 0;
    private Timeline timer;
    private Consumer<ComandaCardController> onDespachadoListener;

    @FXML
    public void initialize() {
        iniciarTemporizador();
    }

    public void initData(String mesa, String comanda, String estado) {
        lblNumeroMesa.setText("MESA " + mesa);
        lblNumeroComanda.setText("Comanda #" + comanda);
        lblEstado.setText("Estado: " + estado);
    }

    public void agregarPlato(String producto, int cantidad, String notas) {
        VBox itemBox = new VBox(2);
        Label lblPlato = new Label(cantidad + "x " + producto);
        lblPlato.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        itemBox.getChildren().add(lblPlato);

        if (notas != null && !notas.trim().isEmpty()) {
            Label lblNota = new Label("  ↳ Nota: " + notas);
            lblNota.setStyle("-fx-text-fill: #e63946; -fx-font-style: italic; -fx-font-size: 11px;");
            itemBox.getChildren().add(lblNota);
        }
        vboxItems.getChildren().add(itemBox);
    }

    private void iniciarTemporizador() {
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            segundosTranscurridos++;
            int min = segundosTranscurridos / 60;
            int sec = segundosTranscurridos % 60;
            lblTiempo.setText(String.format("%02d:%02d min", min, sec));

            // Cambio dinámico de color según tiempo transcurrido
            if (min >= 10) {
                // Alerta crítica roja
                cardContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-padding: 12px; -fx-border-color: #e63946; -fx-border-width: 3px; -fx-border-radius: 8px;");
                lblTiempo.setStyle("-fx-font-weight: bold; -fx-text-fill: #e63946; -fx-font-size: 13px;");
            } else if (min >= 5) {
                // Alerta intermedia amarilla
                cardContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-padding: 12px; -fx-border-color: #e9c46a; -fx-border-width: 3px; -fx-border-radius: 8px;");
                lblTiempo.setStyle("-fx-font-weight: bold; -fx-text-fill: #d4a373; -fx-font-size: 13px;");
            } else {
                // Tiempo verde inicial
                cardContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-padding: 12px; -fx-border-color: #2a9d8f; -fx-border-width: 3px; -fx-border-radius: 8px;");
                lblTiempo.setStyle("-fx-font-weight: bold; -fx-text-fill: #2a9d8f; -fx-font-size: 13px;");
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    @FXML
    public void handleIniciar(ActionEvent event) {
        lblEstado.setText("Estado: EN PREPARACIÓN");
        btnIniciar.setDisable(true);
    }

    @FXML
    public void handleDespachar(ActionEvent event) {
        if (timer != null) timer.stop();
        lblEstado.setText("Estado: LISTO / DESPACHADO");
        if (onDespachadoListener != null) {
            onDespachadoListener.accept(this);
        }
    }

    public void setOnDespachadoListener(Consumer<ComandaCardController> listener) {
        this.onDespachadoListener = listener;
    }

    public VBox getCardContainer() {
        return cardContainer;
    }
}
