package com.foodcore.controller;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.Toolkit;
import java.io.IOException;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador KDS con tablero Kanban de 4 columnas (Pendiente -> En preparación -> Listo -> Historial),
 * 1 tarjeta por ítem con cronómetro y color SLA independiente (detalle_pedido.enviado_en)
 * en zona horaria America/Lima según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§7), SPEC-FOODCORE-v1.0 (§8.5) y v4.4.0.
 */
public class KitchenDisplayController {

    private static final Logger LOGGER = Logger.getLogger(KitchenDisplayController.class.getName());
    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @FXML private VBox vboxColPendiente;
    @FXML private VBox vboxColEnPreparacion;
    @FXML private VBox vboxColListo;
    @FXML private VBox vboxColHistorial;
    @FXML private Label lblCountPendiente;
    @FXML private Label lblCountEnPreparacion;
    @FXML private Label lblCountListo;
    @FXML private Label lblCountHistorial;
    @FXML private FlowPane flowComandas;
    @FXML private Label lblSocketStatus;
    @FXML private Label lblContadorActivas;

    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();
    private final Map<Integer, ComandaCardController> tarjetasActivas = new ConcurrentHashMap<>();
    private final Set<Integer> itemsDespachadosLocal = Collections.synchronizedSet(new HashSet<>());
    private final Set<Integer> itemsSimuladosLocal = Collections.synchronizedSet(new HashSet<>());
    private Timeline autoSyncTimer;

    @FXML
    public void initialize() {
        iniciarServidorSockets();
        cargarComandasDesdeBD();
        iniciarAutoSincronizacion();
    }

    private void iniciarServidorSockets() {
        ServidorSockets server = ServidorSockets.getInstance();
        server.setMensajeListener(json -> Platform.runLater(() -> procesarMensajeSocket(json)));
        server.iniciar();
    }

    private void iniciarAutoSincronizacion() {
        autoSyncTimer = new Timeline(new KeyFrame(Duration.seconds(4), event -> sincronizarNuevasComandas()));
        autoSyncTimer.setCycleCount(Timeline.INDEFINITE);
        autoSyncTimer.play();
    }

    @FXML
    public void handleRefrescar(ActionEvent event) {
        itemsDespachadosLocal.clear();
        itemsSimuladosLocal.clear();
        cargarComandasDesdeBD();
    }

    private void cargarComandasDesdeBD() {
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                List<DetallePedido> itemsPendientes = pedidoDAO.listarItemsPendientesCocina(conn);
                LocalDateTime ahoraLima = LocalDateTime.now(ZONA_LIMA);

                Platform.runLater(() -> renderizarItemsKds(itemsPendientes, ahoraLima));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error al cargar ítems de cocina desde BD", e);
                Platform.runLater(this::recalcularContadoresKanban);
            }
        }).start();
    }

    /**
     * Renderiza las tarjetas individuales por cada {@link DetallePedido} en la columna Kanban correspondiente
     * calculando su tiempo desde {@code enviado_en} sin reiniciar cronómetros de otros ítems (§7).
     */
    public void renderizarItemsKds(List<DetallePedido> items, LocalDateTime referenciaLima) {
        renderizarItemsKds(items, referenciaLima, false);
    }

    private void renderizarItemsKds(List<DetallePedido> items, LocalDateTime referenciaLima, boolean sonSimulados) {
        for (ComandaCardController c : tarjetasActivas.values()) {
            c.stopTimer();
        }
        tarjetasActivas.clear();
        if (!sonSimulados) {
            itemsSimuladosLocal.clear();
        }
        limpiarContenedores();

        LocalDateTime ref = referenciaLima != null ? referenciaLima : LocalDateTime.now(ZONA_LIMA);
        for (DetallePedido item : items) {
            int key = obtenerClaveItem(item);
            if (key > 0 && itemsDespachadosLocal.contains(key)) {
                continue;
            }
            if (sonSimulados && key != 0) {
                itemsSimuladosLocal.add(key);
            }
            int segundos = item.calcularSegundosDesdeEnvio(ref);
            String comandaCode = String.format("COM-%04d-IT%02d", item.getIdPedido(), item.getIdDetallePedido());
            String mesa = item.getNumeroMesa() != null ? item.getNumeroMesa() : "01";
            String estado = item.getEstadoCocina() != null ? item.getEstadoCocina() : "PENDIENTE";
            crearTarjetaPorItem(key, item.getIdPedido(), item.getIdDetallePedido(), mesa, comandaCode, estado, segundos, item);
        }
        recalcularContadoresKanban();
    }

    private void limpiarContenedores() {
        if (vboxColPendiente != null) {
            vboxColPendiente.getChildren().clear();
        }
        if (vboxColEnPreparacion != null) {
            vboxColEnPreparacion.getChildren().clear();
        }
        if (vboxColListo != null) {
            vboxColListo.getChildren().clear();
        }
        if (vboxColHistorial != null) {
            vboxColHistorial.getChildren().clear();
        }
        if (flowComandas != null) {
            flowComandas.getChildren().clear();
        }
    }

    private int obtenerClaveItem(DetallePedido item) {
        if (item.getIdDetallePedido() > 0) {
            return item.getIdDetallePedido();
        }
        return item.getIdPedido();
    }

    private void sincronizarNuevasComandas() {
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                List<DetallePedido> itemsBD = pedidoDAO.listarItemsPendientesCocina(conn);
                LocalDateTime ahoraLima = LocalDateTime.now(ZONA_LIMA);
                Set<Integer> idsPendientesEnBD = new HashSet<>();
                List<DetallePedido> nuevosItems = new ArrayList<>();

                for (DetallePedido item : itemsBD) {
                    int key = obtenerClaveItem(item);
                    if (itemsDespachadosLocal.contains(key)) {
                        continue;
                    }
                    idsPendientesEnBD.add(key);
                    if (!tarjetasActivas.containsKey(key)) {
                        nuevosItems.add(item);
                    }
                }

                List<Integer> idsAEliminar = new ArrayList<>();
                for (Map.Entry<Integer, ComandaCardController> entry : tarjetasActivas.entrySet()) {
                    Integer id = entry.getKey();
                    ComandaCardController ctrl = entry.getValue();
                    if (id > 0
                            && !idsPendientesEnBD.contains(id)
                            && !itemsSimuladosLocal.contains(id)
                            && !"ENTREGADO".equalsIgnoreCase(ctrl.getEstadoActual())) {
                        idsAEliminar.add(id);
                    }
                }

                if (!nuevosItems.isEmpty() || !idsAEliminar.isEmpty()) {
                    Platform.runLater(() -> {
                        for (Integer id : idsAEliminar) {
                            ComandaCardController card = tarjetasActivas.remove(id);
                            if (card != null) {
                                card.stopTimer();
                                removerDeColumnas(card.getCardContainer());
                            }
                        }

                        for (DetallePedido item : nuevosItems) {
                            int key = obtenerClaveItem(item);
                            if (!tarjetasActivas.containsKey(key) && !itemsDespachadosLocal.contains(key)) {
                                int seg = item.calcularSegundosDesdeEnvio(ahoraLima);
                                String comandaCode = String.format("COM-%04d-IT%02d", item.getIdPedido(), item.getIdDetallePedido());
                                String mesa = item.getNumeroMesa() != null ? item.getNumeroMesa() : "01";
                                String estado = item.getEstadoCocina() != null ? item.getEstadoCocina() : "PENDIENTE";
                                crearTarjetaPorItem(key, item.getIdPedido(), item.getIdDetallePedido(), mesa, comandaCode, estado, seg, item);
                                emitirBeepSonoro();
                            }
                        }
                        recalcularContadoresKanban();
                    });
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Sincronización periódica KDS omitida temporalmente", e);
            }
        }).start();
    }

    private void procesarMensajeSocket(JsonObject json) {
        String accion = json.has("accion") ? json.get("accion").getAsString()
            : (json.has("type") ? json.get("type").getAsString() : "");
        if ("NUEVA_COMANDA".equals(accion) || "COMANDA_NUEVA".equals(accion) || "ITEMS_ADICIONALES".equals(accion)) {
            int idPed = json.has("idPedido") ? json.get("idPedido").getAsInt() : 0;
            String mesa = json.has("mesa") ? json.get("mesa").getAsString() : "01";
            String comandaBase = json.has("numeroComanda") ? json.get("numeroComanda").getAsString() : "COM-AUTO";

            emitirBeepSonoro();

            if (json.has("items")) {
                JsonArray items = json.getAsJsonArray("items");
                int idx = 1;
                for (JsonElement el : items) {
                    JsonObject obj = el.getAsJsonObject();
                    int idDet = obj.has("idDetallePedido") ? obj.get("idDetallePedido").getAsInt() : -(idPed * 100 + idx);
                    if (idDet > 0 && (tarjetasActivas.containsKey(idDet) || itemsDespachadosLocal.contains(idDet))) {
                        idx++;
                        continue;
                    }
                    DetallePedido dp = new DetallePedido();
                    dp.setIdPedido(idPed);
                    dp.setIdDetallePedido(Math.max(0, idDet));
                    dp.setNumeroMesa(mesa);
                    dp.setNombreProducto(obj.get("producto").getAsString());
                    dp.setCantidad(obj.get("cantidad").getAsInt());
                    dp.setNotasPreparacion(obj.has("notas") ? obj.get("notas").getAsString() : "");
                    dp.setEnviadoEn(LocalDateTime.now(ZONA_LIMA));

                    int segundos = dp.calcularSegundosDesdeEnvio(LocalDateTime.now(ZONA_LIMA));
                    crearTarjetaPorItem(idDet, idPed, dp.getIdDetallePedido(), mesa, comandaBase + "-IT" + idx, "PENDIENTE", segundos, dp);
                    idx++;
                }
            }
        }
    }

    private void crearTarjetaPorItem(int keyMapa,
                                     int idPedido,
                                     int idDetallePedido,
                                     String mesa,
                                     String comanda,
                                     String estado,
                                     int segundos,
                                     DetallePedido item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
            Parent cardNode = loader.load();
            ComandaCardController cardCtrl = loader.getController();
            cardCtrl.initData(mesa, comanda, estado);
            cardCtrl.setIdPedido(idPedido);
            cardCtrl.setIdDetallePedido(idDetallePedido);
            cardCtrl.setSegundosTranscurridos(segundos);
            cardCtrl.agregarPlato(item.getNombreProducto(), item.getCantidad(), item.getNotasPreparacion());

            cardCtrl.setOnEstadoCambiadoListener((ctrl, nuevoEstado) -> {
                ubicarEnColumnaKanban(ctrl.getCardContainer(), nuevoEstado);
                recalcularContadoresKanban();
            });

            cardCtrl.setOnDespachadoListener(c -> {
                moverTarjetaAHistorialSinRecargar(keyMapa, c);
            });

            if (keyMapa != 0) {
                tarjetasActivas.put(keyMapa, cardCtrl);
            }

            AnimationUtils.animateModalEnter(cardNode);
            ubicarEnColumnaKanban(cardNode, estado);
            recalcularContadoresKanban();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al instanciar tarjeta KDS por ítem", e);
        }
    }

    /**
     * Marca un pedido completo como ENTREGADO de forma NO destructiva (v4.4.0 §1):
     * NO llama a {@link #cargarComandasDesdeBD()}, sino que busca la(s) tarjeta(s) del pedido,
     * las elimina únicamente de la columna LISTO, las mueve a la 4ta columna HISTORIAL
     * y actualiza los contadores numéricos de todas las columnas.
     */
    public void marcarEntregado(int idPedido) {
        List<Map.Entry<Integer, ComandaCardController>> coincidentes = new ArrayList<>();
        for (Map.Entry<Integer, ComandaCardController> entry : tarjetasActivas.entrySet()) {
            if (entry.getValue().getIdPedido() == idPedido) {
                coincidentes.add(entry);
            }
        }

        if (idPedido > 0) {
            new Thread(() -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (conn != null) {
                        for (Map.Entry<Integer, ComandaCardController> e : coincidentes) {
                            int idDet = e.getValue().getIdDetallePedido();
                            if (idDet > 0) {
                                pedidoDAO.actualizarEstadoDetalleCocina(conn, idDet, "ENTREGADO");
                            }
                        }
                        pedidoDAO.actualizarEstadoPedido(conn, idPedido, "ENTREGADO");
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Aviso marcando pedido entregado en BD", ex);
                }
            }).start();
        }

        Runnable uiTask = () -> {
            for (Map.Entry<Integer, ComandaCardController> entry : coincidentes) {
                ComandaCardController ctrl = entry.getValue();
                ctrl.stopTimer();
                ctrl.aplicarEstadoVisual("ENTREGADO");
                moverTarjetaAHistorialSinRecargar(entry.getKey(), ctrl);
            }
            recalcularContadoresKanban();
        };

        if (Platform.isFxApplicationThread()) {
            uiTask.run();
        } else {
            Platform.runLater(uiTask);
        }
    }

    /**
     * Marca un ítem individual como ENTREGADO moviéndolo únicamente de LISTO a HISTORIAL sin recargar el tablero.
     */
    public void marcarEntregadoItem(int idDetallePedido) {
        ComandaCardController ctrl = tarjetasActivas.get(idDetallePedido);
        if (ctrl == null) {
            for (ComandaCardController c : tarjetasActivas.values()) {
                if (c.getIdDetallePedido() == idDetallePedido) {
                    ctrl = c;
                    break;
                }
            }
        }
        if (ctrl != null) {
            ctrl.handleEntregado(null);
        }
    }

    private void moverTarjetaAHistorialSinRecargar(int keyMapa, ComandaCardController c) {
        if (keyMapa > 0) {
            itemsDespachadosLocal.add(keyMapa);
        }
        if (c == null || c.getCardContainer() == null) {
            return;
        }
        removerDeColumnas(c.getCardContainer());
        if (vboxColHistorial != null) {
            if (!vboxColHistorial.getChildren().contains(c.getCardContainer())) {
                vboxColHistorial.getChildren().add(c.getCardContainer());
            }
        } else if (keyMapa != 0) {
            tarjetasActivas.remove(keyMapa);
        }
        recalcularContadoresKanban();
    }

    private void ubicarEnColumnaKanban(Parent cardNode, String estado) {
        removerDeColumnas(cardNode);
        String est = estado != null ? estado.toUpperCase() : "PENDIENTE";
        if ("ENTREGADO".equals(est) && vboxColHistorial != null) {
            vboxColHistorial.getChildren().add(cardNode);
        } else if ("EN_PREPARACION".equals(est) && vboxColEnPreparacion != null) {
            vboxColEnPreparacion.getChildren().add(cardNode);
        } else if ("LISTO".equals(est) && vboxColListo != null) {
            vboxColListo.getChildren().add(cardNode);
        } else if (vboxColPendiente != null) {
            vboxColPendiente.getChildren().add(cardNode);
        } else if (flowComandas != null) {
            flowComandas.getChildren().add(cardNode);
        }
    }

    private void removerDeColumnas(Parent cardNode) {
        if (vboxColPendiente != null) {
            vboxColPendiente.getChildren().remove(cardNode);
        }
        if (vboxColEnPreparacion != null) {
            vboxColEnPreparacion.getChildren().remove(cardNode);
        }
        if (vboxColListo != null) {
            vboxColListo.getChildren().remove(cardNode);
        }
        if (vboxColHistorial != null) {
            vboxColHistorial.getChildren().remove(cardNode);
        }
        if (flowComandas != null) {
            flowComandas.getChildren().remove(cardNode);
        }
    }

    public void recalcularContadoresKanban() {
        int cPend = vboxColPendiente != null ? vboxColPendiente.getChildren().size() : 0;
        int cPrep = vboxColEnPreparacion != null ? vboxColEnPreparacion.getChildren().size() : 0;
        int cListo = vboxColListo != null ? vboxColListo.getChildren().size() : 0;
        int cHist = vboxColHistorial != null ? vboxColHistorial.getChildren().size() : 0;
        int totalActivas = cPend + cPrep + cListo;

        if (lblCountPendiente != null) {
            lblCountPendiente.setText(String.valueOf(cPend));
        }
        if (lblCountEnPreparacion != null) {
            lblCountEnPreparacion.setText(String.valueOf(cPrep));
        }
        if (lblCountListo != null) {
            lblCountListo.setText(String.valueOf(cListo));
        }
        if (lblCountHistorial != null) {
            lblCountHistorial.setText(String.valueOf(cHist));
        }
        if (lblContadorActivas != null) {
            lblContadorActivas.setText("Ítems en Cocina: " + totalActivas);
        }
    }

    @FXML
    public void handleSimularComanda(ActionEvent event) {
        LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);
        List<DetallePedido> demoItems = new ArrayList<>();

        DetallePedido itVerde = new DetallePedido(101, 1, 1, "Combo FastBurger Doble", 2, null, null, "Sin pepinillos · Gaseosa helada");
        itVerde.setNumeroMesa("02");
        itVerde.setEstadoCocina("PENDIENTE");
        itVerde.setEnviadoEn(ahora.minusMinutes(2).minusSeconds(15));
        demoItems.add(itVerde);

        DetallePedido itAmbar = new DetallePedido(102, 2, 2, "Alitas BBQ Picantes x12", 1, null, null, "Extra salsa ranch aparte");
        itAmbar.setNumeroMesa("05");
        itAmbar.setEstadoCocina("EN_PREPARACION");
        itAmbar.setEnviadoEn(ahora.minusMinutes(7).minusSeconds(40));
        demoItems.add(itAmbar);

        DetallePedido itRojo = new DetallePedido(103, 3, 3, "Chaufa Aeropuerto Familiar", 1, null, null, "Urgente · Mesa VIP");
        itRojo.setNumeroMesa("08");
        itRojo.setEstadoCocina("PENDIENTE");
        itRojo.setEnviadoEn(ahora.minusMinutes(12).minusSeconds(30));
        demoItems.add(itRojo);

        DetallePedido itListo = new DetallePedido(104, 4, 4, "Papas Fritas Supremas", 2, null, null, "Para despachar");
        itListo.setNumeroMesa("01");
        itListo.setEstadoCocina("LISTO");
        itListo.setEnviadoEn(ahora.minusMinutes(4).minusSeconds(10));
        demoItems.add(itListo);

        renderizarItemsKds(demoItems, ahora, true);
    }

    private void emitirBeepSonoro() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Beep sonoro no disponible en entorno headless", e);
        }
    }

    public Map<Integer, ComandaCardController> getTarjetasActivas() {
        return Collections.unmodifiableMap(tarjetasActivas);
    }

    public FlowPane getFlowComandas() {
        return flowComandas;
    }

    public VBox getVboxColPendiente() {
        return vboxColPendiente;
    }

    public VBox getVboxColEnPreparacion() {
        return vboxColEnPreparacion;
    }

    public VBox getVboxColListo() {
        return vboxColListo;
    }

    public VBox getVboxColHistorial() {
        return vboxColHistorial;
    }

    public Label getLblCountPendiente() {
        return lblCountPendiente;
    }

    public Label getLblCountEnPreparacion() {
        return lblCountEnPreparacion;
    }

    public Label getLblCountListo() {
        return lblCountListo;
    }

    public void stopTimers() {
        if (autoSyncTimer != null) {
            autoSyncTimer.stop();
        }
        for (ComandaCardController c : tarjetasActivas.values()) {
            c.stopTimer();
        }
    }

    public Label getLblCountHistorial() {
        return lblCountHistorial;
    }
}
