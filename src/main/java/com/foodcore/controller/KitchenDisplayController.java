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
import javafx.util.Duration;

import java.awt.Toolkit;
import java.io.IOException;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador KDS con cronómetro y color independiente por ítem ({@code detalle_pedido.enviado_en})
 * en zona horaria {@code America/Lima} según SPEC-FOODCORE-v1.0 (§8.5; REMEDIATION R-07, R-08).
 */
public class KitchenDisplayController {

    private static final Logger LOGGER = Logger.getLogger(KitchenDisplayController.class.getName());
    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @FXML private FlowPane flowComandas;
    @FXML private Label lblSocketStatus;
    @FXML private Label lblContadorActivas;

    private int contadorComandas = 0;
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();
    private final Map<Integer, ComandaCardController> tarjetasActivas = new ConcurrentHashMap<>();
    private final Set<Integer> itemsDespachadosLocal = Collections.synchronizedSet(new HashSet<>());
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
                Platform.runLater(() -> lblContadorActivas.setText("Comandas en Cocina: " + contadorComandas));
            }
        }).start();
    }

    /**
     * Renderiza las tarjetas individuales por cada {@link DetallePedido} calculando su tiempo desde {@code enviado_en}
     * sin reiniciar cronómetros de otros ítems (SPEC-FOODCORE-v1.0 §8.5, §19.9; REMEDIATION R-07, R-08).
     */
    public void renderizarItemsKds(List<DetallePedido> items, LocalDateTime referenciaLima) {
        for (ComandaCardController c : tarjetasActivas.values()) {
            c.stopTimer();
        }
        tarjetasActivas.clear();
        flowComandas.getChildren().clear();
        contadorComandas = 0;

        LocalDateTime ref = referenciaLima != null ? referenciaLima : LocalDateTime.now(ZONA_LIMA);
        for (DetallePedido item : items) {
            int key = obtenerClaveItem(item);
            if (key > 0 && itemsDespachadosLocal.contains(key)) {
                continue;
            }
            int segundos = item.calcularSegundosDesdeEnvio(ref);
            String comandaCode = String.format("COM-%04d-IT%02d", item.getIdPedido(), item.getIdDetallePedido());
            String mesa = item.getNumeroMesa() != null ? item.getNumeroMesa() : "01";
            String estado = item.getEstadoCocina() != null ? item.getEstadoCocina() : "PENDIENTE";
            crearTarjetaPorItem(key, item.getIdPedido(), item.getIdDetallePedido(), mesa, comandaCode, estado, segundos, item);
        }
        actualizarContador(0);
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
                for (Integer id : tarjetasActivas.keySet()) {
                    if (id > 0 && !idsPendientesEnBD.contains(id)) {
                        idsAEliminar.add(id);
                    }
                }

                if (!nuevosItems.isEmpty() || !idsAEliminar.isEmpty()) {
                    Platform.runLater(() -> {
                        for (Integer id : idsAEliminar) {
                            ComandaCardController card = tarjetasActivas.remove(id);
                            if (card != null) {
                                card.stopTimer();
                                flowComandas.getChildren().remove(card.getCardContainer());
                                actualizarContador(-1);
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

            cardCtrl.setOnDespachadoListener(c -> {
                if (keyMapa != 0) {
                    tarjetasActivas.remove(keyMapa);
                    if (keyMapa > 0) {
                        itemsDespachadosLocal.add(keyMapa);
                    }
                }
                flowComandas.getChildren().remove(c.getCardContainer());
                actualizarContador(-1);
            });

            if (keyMapa != 0) {
                tarjetasActivas.put(keyMapa, cardCtrl);
            }

            AnimationUtils.animateModalEnter(cardNode);
            flowComandas.getChildren().add(cardNode);
            actualizarContador(1);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al instanciar tarjeta KDS por ítem", e);
        }
    }

    @FXML
    public void handleSimularComanda(ActionEvent event) {
        JsonObject json = new JsonObject();
        json.addProperty("accion", "NUEVA_COMANDA");
        json.addProperty("mesa", "08");
        json.addProperty("numeroComanda", "COM-" + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000)));

        JsonArray items = new JsonArray();
        JsonObject item1 = new JsonObject();
        item1.addProperty("producto", "Combo FastBurger");
        item1.addProperty("cantidad", 2);
        item1.addProperty("notas", "Gaseosa helada sin hielo");
        items.add(item1);

        JsonObject item2 = new JsonObject();
        item2.addProperty("producto", "Alitas BBQ x6");
        item2.addProperty("cantidad", 1);
        item2.addProperty("notas", "Extra salsa BBQ aparte");
        items.add(item2);

        json.add("items", items);
        procesarMensajeSocket(json);
    }

    private void emitirBeepSonoro() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Beep sonoro no disponible en entorno headless", e);
        }
    }

    private void actualizarContador(int delta) {
        contadorComandas += delta;
        if (contadorComandas < 0) contadorComandas = 0;
        lblContadorActivas.setText("Comandas en Cocina: " + contadorComandas);
    }

    public Map<Integer, ComandaCardController> getTarjetasActivas() {
        return Collections.unmodifiableMap(tarjetasActivas);
    }

    public FlowPane getFlowComandas() {
        return flowComandas;
    }
}
