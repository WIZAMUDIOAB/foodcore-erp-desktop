package com.foodcore.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.config.AppContainer;
import com.foodcore.model.DetallePedido;
import com.foodcore.service.PedidoService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.JsonMapperProvider;
import com.foodcore.util.ServidorSockets;
import com.foodcore.util.UiAsyncExecutor;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

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
    @FXML private Label lblPrioridadBadge;
    @FXML private Label lblTiempo;
    @FXML private Label lblNumeroComanda;
    @FXML private VBox vboxItems;
    @FXML private Label lblEstado;
    @FXML private Button btnIniciar;
    @FXML private Button btnDespachar;
    @FXML private Button btnEntregado;

    private int idPedido = 0;
    private int idDetallePedido = 0;
    private int prioridad = 0;
    private int segundosTranscurridos = 0;
    private String estadoActual = "PENDIENTE";
    private String colorUmbral = "GREEN";
    private Timeline timer;
    private Consumer<ComandaCardController> onDespachadoListener;
    private BiConsumer<ComandaCardController, String> onEstadoCambiadoListener;
    private BiConsumer<Integer, Integer> onDragReordenarListener;
    private final PedidoService pedidoService;

    public ComandaCardController() {
        this(AppContainer.getDefault().pedidoService());
    }

    public ComandaCardController(PedidoService pedidoService) {
        this.pedidoService = java.util.Objects.requireNonNull(pedidoService, "pedidoService no puede ser nulo");
    }

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
        aplicarEstiloCronometro(KitchenDisplayController.TIMER_FONT_FAMILY, KitchenDisplayController.TIMER_FONT_SIZE);
        configurarDragAndDrop();
        iniciarTemporizador();
    }

    /**
     * Aplica la tipografía de alta legibilidad al cronómetro de comanda KDS (Sub-Fase D - D6).
     */
    public void aplicarEstiloCronometro(String fontFamily, double fontSize) {
        if (lblTiempo == null) {
            return;
        }
        String fam = (fontFamily == null || fontFamily.isBlank()) ? "JetBrains Mono" : fontFamily.trim();
        double size = fontSize > 0 ? fontSize : 28.0;
        lblTiempo.setFont(Font.font(fam, FontWeight.BOLD, size));
        lblTiempo.setStyle("-fx-font-family: '" + fam + "', 'Consolas', monospace; "
                + "-fx-font-size: " + Math.round(size) + "px; -fx-font-weight: bold;");
    }

    private void configurarDragAndDrop() {
        if (cardContainer == null) {
            return;
        }
        cardContainer.setOnDragDetected(ev -> {
            if (idDetallePedido <= 0 || "ENTREGADO".equalsIgnoreCase(estadoActual)) {
                return;
            }
            Dragboard db = cardContainer.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("KDS_DET:" + idDetallePedido);
            db.setContent(content);
            cardContainer.setOpacity(0.65);
            ev.consume();
        });
        cardContainer.setOnDragDone(ev -> {
            cardContainer.setOpacity(1.0);
            ev.consume();
        });
        cardContainer.setOnDragOver(ev -> {
            Dragboard db = ev.getDragboard();
            if (db != null && db.hasString() && db.getString().startsWith("KDS_DET:")) {
                ev.acceptTransferModes(TransferMode.MOVE);
            }
            ev.consume();
        });
        cardContainer.setOnDragDropped(ev -> {
            Dragboard db = ev.getDragboard();
            boolean ok = false;
            if (db != null && db.hasString() && db.getString().startsWith("KDS_DET:") && idDetallePedido > 0) {
                try {
                    int idOrigen = Integer.parseInt(db.getString().substring("KDS_DET:".length()));
                    if (idOrigen > 0 && idOrigen != idDetallePedido && onDragReordenarListener != null) {
                        onDragReordenarListener.accept(idOrigen, idDetallePedido);
                        ok = true;
                    }
                } catch (NumberFormatException ignored) {
                    ok = false;
                }
            }
            ev.setDropCompleted(ok);
            ev.consume();
        });
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
        if (lblPrioridadBadge != null) {
            if (prioridad > 0) {
                lblPrioridadBadge.setText("P" + prioridad);
                lblPrioridadBadge.setVisible(true);
                lblPrioridadBadge.setManaged(true);
            } else {
                lblPrioridadBadge.setVisible(false);
                lblPrioridadBadge.setManaged(false);
            }
        }
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setOnDragReordenarListener(BiConsumer<Integer, Integer> listener) {
        this.onDragReordenarListener = listener;
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
        if (lblTiempo != null) {
            lblTiempo.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        if (lblPrioridadBadge != null) {
            lblPrioridadBadge.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
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
            badgeCombo.getStyleClass().add("fc-kds-combo-badge");
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
            boxDesglose.getStyleClass().add("fc-kds-combo-box");
            Label lblCabeceraDesglose = new Label("Desglose de empaquetado:");
            lblCabeceraDesglose.getStyleClass().add("fc-kds-combo-header");
            boxDesglose.getChildren().add(lblCabeceraDesglose);
            for (String comp : componentesCombo) {
                Label lblComp = new Label("•  " + comp);
                lblComp.setWrapText(true);
                lblComp.setMaxWidth(252);
                lblComp.getStyleClass().add("fc-kds-combo-item");
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
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> pedidoService.marcarItemEnPreparacionKds(idDetallePedido, idPedido),
                null,
                ex -> LOGGER.log(Level.WARNING, "Error actualizando estado EN_PREPARACION en KDS", ex)
            );
        }

        if (onEstadoCambiadoListener != null) {
            onEstadoCambiadoListener.accept(this, "EN_PREPARACION");
        }
    }

    @FXML
    public void handleDespachar(ActionEvent event) {
        aplicarEstadoVisual("LISTO");

        if (idDetallePedido > 0 || idPedido > 0) {
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> {
                    pedidoService.marcarItemEstadoYActualizarPedidoKds(idDetallePedido, idPedido, "LISTO");
                    ObjectNode json = JsonMapperProvider.getMapper().createObjectNode();
                    json.put("accion", "PLATO_DESPACHADO");
                    json.put("idPedido", idPedido);
                    json.put("idDetallePedido", idDetallePedido);
                    ServidorSockets.getInstance().emitirMensaje(json);
                },
                null,
                ex -> LOGGER.log(Level.WARNING, "Error actualizando estado LISTO en KDS", ex)
            );
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
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> pedidoService.marcarItemEstadoYActualizarPedidoKds(idDetallePedido, idPedido, "ENTREGADO"),
                null,
                ex -> LOGGER.log(Level.WARNING, "Error actualizando estado ENTREGADO en KDS", ex)
            );
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

    public Label getLblTiempo() {
        return lblTiempo;
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
