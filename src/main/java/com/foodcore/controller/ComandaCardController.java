package com.foodcore.controller;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonObject;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.sql.Connection;
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

    private int idPedido = 0;
    private int segundosTranscurridos = 0;
    private Timeline timer;
    private Consumer<ComandaCardController> onDespachadoListener;
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnIniciar);
        AnimationUtils.attachTactileFeedback(btnDespachar);
        iniciarTemporizador();
    }

    public void initData(String mesa, String comanda, String estado) {
        lblNumeroMesa.setText("MESA " + mesa);
        lblNumeroComanda.setText("Comanda #" + comanda);
        lblEstado.setText("Estado: " + estado);
        if ("EN_PREPARACION".equalsIgnoreCase(estado)) {
            btnIniciar.setDisable(true);
            btnIniciar.setText("Preparando...");
        }
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public void agregarPlato(String producto, int cantidad, String notas) {
        VBox itemBox = new VBox(3);
        Label lblPlato = new Label(cantidad + "x  " + producto);
        lblPlato.setWrapText(true);
        lblPlato.setMaxWidth(265);
        lblPlato.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #f9fafb;");
        itemBox.getChildren().add(lblPlato);

        if (notas != null && !notas.trim().isEmpty()) {
            Label lblNota = new Label("↳ Nota: " + notas);
            lblNota.setWrapText(true);
            lblNota.setMaxWidth(265);
            lblNota.setStyle("-fx-background-color: rgba(245, 158, 11, 0.14); -fx-text-fill: #fcd34d; -fx-padding: 2px 6px; -fx-background-radius: 4px; -fx-font-size: 11px; -fx-font-weight: 600;");
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

            String baseCard = "-fx-background-color: #161920; -fx-background-radius: 10px; -fx-padding: 14px; -fx-border-width: 2px; -fx-border-radius: 10px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 3);";

            // Semaforo KDS de Alto Rendimiento
            if (min >= 10) {
                // Alerta critica roja (> 10 min)
                cardContainer.setStyle(baseCard + " -fx-border-color: #ef4444;");
                lblTiempo.setStyle("-fx-font-weight: 800; -fx-text-fill: #ef4444; -fx-font-size: 14px; -fx-background-color: rgba(239, 68, 68, 0.18); -fx-padding: 4px 8px; -fx-background-radius: 6px;");
            } else if (min >= 5) {
                // Alerta intermedia ambar (5 a 10 min)
                cardContainer.setStyle(baseCard + " -fx-border-color: #f59e0b;");
                lblTiempo.setStyle("-fx-font-weight: 800; -fx-text-fill: #f59e0b; -fx-font-size: 14px; -fx-background-color: rgba(245, 158, 11, 0.18); -fx-padding: 4px 8px; -fx-background-radius: 6px;");
            } else {
                // Tiempo optimo verde (< 5 min)
                cardContainer.setStyle(baseCard + " -fx-border-color: #10b981;");
                lblTiempo.setStyle("-fx-font-weight: 800; -fx-text-fill: #10b981; -fx-font-size: 14px; -fx-background-color: rgba(16, 185, 129, 0.14); -fx-padding: 4px 8px; -fx-background-radius: 6px;");
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    @FXML
    public void handleIniciar(ActionEvent event) {
        lblEstado.setText("Estado: EN PREPARACIÓN");
        lblEstado.setStyle("-fx-font-weight: 700; -fx-font-size: 11px; -fx-text-fill: #f59e0b;");
        btnIniciar.setDisable(true);
        btnIniciar.setText("En Cocina");

        if (idPedido > 0) {
            new Thread(() -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    pedidoDAO.actualizarEstadoPedido(conn, idPedido, "EN_PREPARACION");
                } catch (Exception ignored) {
                }
            }).start();
        }
    }

    @FXML
    public void handleDespachar(ActionEvent event) {
        if (timer != null) timer.stop();
        lblEstado.setText("Estado: LISTO / DESPACHADO");
        lblEstado.setStyle("-fx-font-weight: 700; -fx-font-size: 11px; -fx-text-fill: #10b981;");

        if (idPedido > 0) {
            new Thread(() -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    pedidoDAO.actualizarEstadoPedido(conn, idPedido, "LISTO");

                    // Notificar por Sockets a salon/mozos
                    JsonObject json = new JsonObject();
                    json.addProperty("accion", "PLATO_DESPACHADO");
                    json.addProperty("idPedido", idPedido);
                    ServidorSockets.getInstance().emitirMensaje(json);
                } catch (Exception ignored) {
                }
            }).start();
        }

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
