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
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de tarjeta individual de ítem KDS con barra superior de 4px según SLA
 * (<5m verde, 5-10m ámbar, >10m rojo con pulso suave de 1.5s) y botones de 44px
 * según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§7) y SPEC-FOODCORE-v1.0 (§8.5).
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
    @FXML private Button btnEntregado;

    private int idPedido = 0;
    private int idDetallePedido = 0;
    private int segundosTranscurridos = 0;
    private String estadoActual = "PENDIENTE";
    private String colorUmbral = "GREEN";
    private Timeline timer;
    private Consumer<ComandaCardController> onDespachadoListener;
    private BiConsumer<ComandaCardController, String> onEstadoCambiadoListener;
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
        AnimationUtils.attachTactileFeedback(btnEntregado);
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
        agregarPlatoDetalle(item);
        long seg = item.calcularSegundosDesdeEnvio(LocalDateTime.now(ZONA_LIMA));
        setSegundosTranscurridos((int) seg);

        if (btnIniciar != null && onIniciar != null) {
            btnIniciar.setOnAction(ev -> {
                onIniciar.accept(item);
                aplicarEstadoVisual("EN_PREPARACION");
            });
        }
        if (btnDespachar != null && onListo != null) {
            btnDespachar.setId("btnListo");
            btnDespachar.setOnAction(ev -> {
                stopTimer();
                onListo.accept(item);
                aplicarEstadoVisual("LISTO");
            });
        }
    }

    public void initData(String mesa, String comanda, String estado) {
        if (mesa != null && (mesa.toUpperCase().contains("DELIVERY") || mesa.toUpperCase().contains("DL-"))) {
            lblNumeroMesa.setText("DELIVERY " + mesa);
            lblNumeroMesa.getStyleClass().add("badge-pill-accent");
        } else if (mesa != null && (mesa.toUpperCase().contains("LLEVAR") || mesa.toUpperCase().contains("TK-"))) {
            lblNumeroMesa.setText("PARA LLEVAR " + mesa);
            lblNumeroMesa.getStyleClass().add("badge-pill-accent");
        } else if (mesa != null && (mesa.toUpperCase().contains("T-") || mesa.toUpperCase().contains("KIOSCO"))) {
            lblNumeroMesa.setText("KIOSCO " + mesa);
            lblNumeroMesa.getStyleClass().add("badge-pill-accent");
        } else {
            lblNumeroMesa.setText("MESA " + mesa);
        }
        lblNumeroComanda.setText("Comanda #" + comanda);
        aplicarEstadoVisual(estado != null ? estado : "PENDIENTE");
    }

    public void aplicarEstadoVisual(String estado) {
        this.estadoActual = estado;
        lblEstado.setText("Estado: " + estado);
        if ("EN_PREPARACION".equalsIgnoreCase(estado)) {
            if (btnIniciar != null) {
                btnIniciar.setDisable(true);
            }
        } else if ("LISTO".equalsIgnoreCase(estado)) {
            if (btnIniciar != null) {
                btnIniciar.setDisable(true);
            }
            if (btnDespachar != null) {
                btnDespachar.setDisable(true);
            }
        } else if ("ENTREGADO".equalsIgnoreCase(estado)) {
            if (btnIniciar != null) {
                btnIniciar.setDisable(true);
            }
            if (btnDespachar != null) {
                btnDespachar.setDisable(true);
            }
            if (btnEntregado != null) {
                btnEntregado.setDisable(true);
            }
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

    public String getEstadoActual() {
        return estadoActual;
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
        AnimationUtils.setSoftPulse(cardContainer, false);
    }

    public void agregarPlatoDetalle(DetallePedido item) {
        if (item == null) {
            return;
        }
        String tituloCocina = com.foodcore.util.FormatoPersonalizacionUtil.formatearDescripcionItem(item);
        List<String> componentesCombo = com.foodcore.util.FormatoPersonalizacionUtil.extraerLineasDesgloseCombo(
                item.getComboComponentesTexto(), item.getCantidad());
        agregarPlato(tituloCocina, item.getCantidad(), item.getNotasPreparacion(), componentesCombo);
    }

    public void agregarPlato(String producto, int cantidad, String notas) {
        agregarPlato(producto, cantidad, notas, List.of());
    }

    public void agregarPlato(String producto, int cantidad, String notas, List<String> componentesCombo) {
        VBox itemBox = new VBox(5);
        boolean esCombo = componentesCombo != null && !componentesCombo.isEmpty();
        if (esCombo) {
            Label badgeCombo = new Label("COMBO");
            badgeCombo.setStyle("-fx-background-color: #4F46E5; -fx-text-fill: #FFFFFF; -fx-font-size: 10px; "
                    + "-fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 6;");
            itemBox.getChildren().add(badgeCombo);
        }

        Label lblPlato = new Label(cantidad + "x  " + producto);
        lblPlato.setWrapText(true);
        lblPlato.setMaxWidth(275);
        lblPlato.getStyleClass().add("text-body-strong-inverse");
        itemBox.getChildren().add(lblPlato);

        if (esCombo) {
            VBox boxDesglose = new VBox(3);
            boxDesglose.setMaxWidth(275);
            boxDesglose.setStyle("-fx-background-color: rgba(255, 255, 255, 0.08); "
                    + "-fx-border-color: rgba(165, 180, 252, 0.35); -fx-border-radius: 6; -fx-background-radius: 6; "
                    + "-fx-padding: 6 9;");
            Label lblCabeceraDesglose = new Label("Desglose de empaquetado:");
            lblCabeceraDesglose.setStyle("-fx-text-fill: #A5B4FC; -fx-font-size: 11px; -fx-font-weight: 700;");
            boxDesglose.getChildren().add(lblCabeceraDesglose);
            for (String comp : componentesCombo) {
                Label lblComp = new Label("•  " + comp);
                lblComp.setWrapText(true);
                lblComp.setMaxWidth(252);
                lblComp.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 12.5px; -fx-font-weight: 600;");
                boxDesglose.getChildren().add(lblComp);
            }
            itemBox.getChildren().add(boxDesglose);
        }

        if (notas != null && !notas.trim().isEmpty()) {
            Label lblNota = new Label("Nota: " + notas);
            lblNota.setWrapText(true);
            lblNota.setMaxWidth(275);
            lblNota.getStyleClass().add("badge-pill-warning");
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

        cardContainer.getStyleClass().removeAll(
            "kds-header-green", "kds-header-amber", "kds-header-red",
            "kds-card-green", "kds-card-amber", "kds-card-red"
        );
        lblTiempo.getStyleClass().removeAll("kds-timer-green", "kds-timer-amber", "kds-timer-red");

        if (min >= 10) {
            colorUmbral = "RED";
            cardContainer.getStyleClass().addAll("kds-header-red", "kds-card-red");
            lblTiempo.getStyleClass().add("kds-timer-red");
            AnimationUtils.setSoftPulse(cardContainer, true);
        } else if (min >= 5) {
            colorUmbral = "AMBER";
            cardContainer.getStyleClass().addAll("kds-header-amber", "kds-card-amber");
            lblTiempo.getStyleClass().add("kds-timer-amber");
            AnimationUtils.setSoftPulse(cardContainer, false);
        } else {
            colorUmbral = "GREEN";
            cardContainer.getStyleClass().addAll("kds-header-green", "kds-card-green");
            lblTiempo.getStyleClass().add("kds-timer-green");
            AnimationUtils.setSoftPulse(cardContainer, false);
        }
    }

    @FXML
    public void handleIniciar(ActionEvent event) {
        aplicarEstadoVisual("EN_PREPARACION");

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

        if (onEstadoCambiadoListener != null) {
            onEstadoCambiadoListener.accept(this, "EN_PREPARACION");
        }
    }

    @FXML
    public void handleDespachar(ActionEvent event) {
        aplicarEstadoVisual("LISTO");

        if (idDetallePedido > 0 || idPedido > 0) {
            new Thread(() -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (idDetallePedido > 0) {
                        pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "LISTO");
                    }
                    if (idPedido > 0 && !pedidoDAO.tieneItemsPendientesCocina(conn, idPedido)) {
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

        if (onEstadoCambiadoListener != null) {
            onEstadoCambiadoListener.accept(this, "LISTO");
        } else if (onDespachadoListener != null) {
            stopTimer();
            onDespachadoListener.accept(this);
        }
    }

    @FXML
    public void handleEntregado(ActionEvent event) {
        stopTimer();
        aplicarEstadoVisual("ENTREGADO");

        if (idDetallePedido > 0 || idPedido > 0) {
            new Thread(() -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (idDetallePedido > 0) {
                        pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "ENTREGADO");
                    }
                    if (idPedido > 0 && !pedidoDAO.tieneItemsPendientesCocina(conn, idPedido)) {
                        pedidoDAO.actualizarEstadoPedido(conn, idPedido, "ENTREGADO");
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error actualizando estado ENTREGADO en KDS", ex);
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

    public void setOnEstadoCambiadoListener(BiConsumer<ComandaCardController, String> listener) {
        this.onEstadoCambiadoListener = listener;
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

    public Button getBtnEntregado() {
        return btnEntregado;
    }
}
