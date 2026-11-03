package com.foodcore.controller;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.model.DetallePedido;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de tarjeta individual de ítem KDS con semáforo de umbral (<5m verde, 5-10m ámbar, >10m rojo)
 * según SPEC-FOODCORE-v1.0 (§8.5; REMEDIATION R-08).
 */
public class ComandaCardController {

    private static final Logger LOGGER = Logger.getLogger(ComandaCardController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @FXML private VBox cardContainer;
    @FXML private Label lblNumeroMesa;
    @FXML private Label lblTiempo;
    @FXML private Label lblNumeroComanda;
    @FXML private VBox vboxItems;
    @FXML private Label lblEstado;
    @FXML private Button btnIniciar;
    @FXML private Button btnDespachar;

    private int idPedido = 0;
    private int idDetallePedido = 0;
    private int segundosTranscurridos = 0;
    private String colorUmbral = "GREEN";
    private Timeline timer;
    private Consumer<ComandaCardController> onDespachadoListener;
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    public static String determinarColorPorSegundos(long segundos) {
        long min = Math.max(0L, segundos) / 60L;
        if (min >= 10L) {
            return "RED";
        }
        if (min >= 5L) {
            return "AMBER";
        }
        return "GREEN";
    }

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnIniciar);
        AnimationUtils.attachTactileFeedback(btnDespachar);
        if (btnDespachar != null && (btnDespachar.getId() == null || btnDespachar.getId().isBlank())) {
            btnDespachar.setId("btnListo");
        }
        iniciarTemporizador();
    }

    public void initDataItem(DetallePedido item, Consumer<DetallePedido> onIniciar, Consumer<DetallePedido> onListo) {
        this.idPedido = item.getIdPedido();
        this.idDetallePedido = item.getIdDetallePedido();
        String mesa = item.getNumeroMesa() != null ? item.getNumeroMesa() : "01";
        String estado = item.getEstadoCocina() != null ? item.getEstadoCocina() : "PENDIENTE";
        initData(mesa, "PED-" + item.getIdPedido() + " · ITEM-" + item.getIdDetallePedido(), estado);
        agregarPlato(
            item.getNombreProducto() != null ? item.getNombreProducto() : "Plato #" + item.getIdProducto(),
            item.getCantidad(),
            item.getNotasPreparacion()
        );
        long seg = item.calcularSegundosDesdeEnvio(LocalDateTime.now(ZONA_LIMA));
        setSegundosTranscurridos((int) seg);

        if (btnIniciar != null && onIniciar != null) {
            btnIniciar.setOnAction(ev -> {
                onIniciar.accept(item);
                lblEstado.setText("Estado: EN_PREPARACION");
            });
        }
        if (btnDespachar != null && onListo != null) {
            btnDespachar.setId("btnListo");
            btnDespachar.setOnAction(ev -> {
                stopTimer();
                onListo.accept(item);
                lblEstado.setText("Estado: LISTO");
            });
        }
    }

    public void initData(String mesa, String comanda, String estado) {
        lblNumeroMesa.setText("MESA " + mesa);
        lblNumeroComanda.setText("Comanda #" + comanda);
        lblEstado.setText("Estado: " + estado);
        if ("EN_PREPARACION".equalsIgnoreCase(estado)) {
            btnIniciar.setDisable(true);
            btnIniciar.setText("En Cocina");
            lblEstado.setStyle("-fx-font-weight: 700; -fx-font-size: 11px; -fx-text-fill: #f59e0b;");
        } else if ("LISTO".equalsIgnoreCase(estado) || "ENTREGADO".equalsIgnoreCase(estado)) {
            btnIniciar.setDisable(true);
            btnIniciar.setText("Listo");
            lblEstado.setStyle("-fx-font-weight: 700; -fx-font-size: 11px; -fx-text-fill: #10b981;");
        }
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public int getIdPedido() {
        return idPedido;
    }

    public void setIdDetallePedido(int idDetallePedido) {
        this.idDetallePedido = idDetallePedido;
    }

    public int getIdDetallePedido() {
        return idDetallePedido;
    }

    public void setSegundosTranscurridos(int segundos) {
        this.segundosTranscurridos = Math.max(0, segundos);
        actualizarEstiloTiempo();
    }

    public int getSegundosTranscurridos() {
        return segundosTranscurridos;
    }

    public String getColorUmbral() {
        return colorUmbral;
    }

    public void stopTimer() {
        if (timer != null) {
            timer.stop();
        }
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
        actualizarEstiloTiempo();
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            segundosTranscurridos++;
            actualizarEstiloTiempo();
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void actualizarEstiloTiempo() {
        int min = segundosTranscurridos / 60;
        int sec = segundosTranscurridos % 60;
        lblTiempo.setText(String.format("%02d:%02d min", min, sec));

        String baseCard = "-fx-background-color: #161920; -fx-background-radius: 10px; -fx-padding: 14px; -fx-border-width: 2px; -fx-border-radius: 10px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 3);";
        cardContainer.getStyleClass().removeAll(
            "kds-header-green", "kds-header-amber", "kds-header-red",
            "kds-card-green", "kds-card-amber", "kds-card-red"
        );

        if (min >= 10) {
            colorUmbral = "RED";
            cardContainer.getStyleClass().addAll("kds-header-red", "kds-card-red");
            cardContainer.setStyle(baseCard + " -fx-border-color: #ef4444;");
            lblTiempo.setStyle("-fx-font-weight: 800; -fx-text-fill: #ef4444; -fx-font-size: 14px; -fx-background-color: rgba(239, 68, 68, 0.18); -fx-padding: 4px 8px; -fx-background-radius: 6px;");
        } else if (min >= 5) {
            colorUmbral = "AMBER";
            cardContainer.getStyleClass().addAll("kds-header-amber", "kds-card-amber");
            cardContainer.setStyle(baseCard + " -fx-border-color: #f59e0b;");
            lblTiempo.setStyle("-fx-font-weight: 800; -fx-text-fill: #f59e0b; -fx-font-size: 14px; -fx-background-color: rgba(245, 158, 11, 0.18); -fx-padding: 4px 8px; -fx-background-radius: 6px;");
        } else {
            colorUmbral = "GREEN";
            cardContainer.getStyleClass().addAll("kds-header-green", "kds-card-green");
            cardContainer.setStyle(baseCard + " -fx-border-color: #10b981;");
            lblTiempo.setStyle("-fx-font-weight: 800; -fx-text-fill: #10b981; -fx-font-size: 14px; -fx-background-color: rgba(16, 185, 129, 0.14); -fx-padding: 4px 8px; -fx-background-radius: 6px;");
        }
    }

    @FXML
    public void handleIniciar(ActionEvent event) {
        lblEstado.setText("Estado: EN PREPARACIÓN");
        lblEstado.setStyle("-fx-font-weight: 700; -fx-font-size: 11px; -fx-text-fill: #f59e0b;");
        btnIniciar.setDisable(true);
        btnIniciar.setText("En Cocina");

        if (idDetallePedido > 0 || idPedido > 0) {
            new Thread(() -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (idDetallePedido > 0) {
                        pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "EN_PREPARACION");
                    }
                    if (idPedido > 0) {
                        pedidoDAO.actualizarEstadoPedido(conn, idPedido, "EN_PREPARACION");
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error actualizando estado EN_PREPARACION en KDS", ex);
                }
            }).start();
        }
    }

    @FXML
    public void handleDespachar(ActionEvent event) {
        stopTimer();
        lblEstado.setText("Estado: LISTO / DESPACHADO");
        lblEstado.setStyle("-fx-font-weight: 700; -fx-font-size: 11px; -fx-text-fill: #10b981;");

        if (idDetallePedido > 0 || idPedido > 0) {
            new Thread(() -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (idDetallePedido > 0) {
                        pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "LISTO");
                    }
                    if (idPedido > 0) {
                        pedidoDAO.actualizarEstadoPedido(conn, idPedido, "LISTO");
                    }

                    JsonObject json = new JsonObject();
                    json.addProperty("accion", "PLATO_DESPACHADO");
                    json.addProperty("idPedido", idPedido);
                    json.addProperty("idDetallePedido", idDetallePedido);
                    ServidorSockets.getInstance().emitirMensaje(json);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error actualizando estado LISTO en KDS", ex);
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

    public Label getLblEstado() {
        return lblEstado;
    }

    public Button getBtnIniciar() {
        return btnIniciar;
    }

    public Button getBtnDespachar() {
        return btnDespachar;
    }
}
