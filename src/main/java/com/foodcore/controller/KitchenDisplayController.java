package com.foodcore.controller;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KitchenDisplayController {

    private static final Logger LOGGER = Logger.getLogger(KitchenDisplayController.class.getName());

    @FXML private FlowPane flowComandas;
    @FXML private Label lblSocketStatus;
    @FXML private Label lblContadorActivas;

    private int contadorComandas = 0;
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();
    private final Map<Integer, ComandaCardController> tarjetasActivas = new ConcurrentHashMap<>();
    private final Set<Integer> comandasDespachadasLocal = Collections.synchronizedSet(new HashSet<>());
    private Timeline autoSyncTimer;

    private static class ComandaDTO {
        final int idPedido;
        final String mesa;
        final String comandaCode;
        final String estado;
        final int segundos;
        final List<DetallePedido> detalles;

        ComandaDTO(int idPedido, String mesa, String comandaCode, String estado, int segundos, List<DetallePedido> detalles) {
            this.idPedido = idPedido;
            this.mesa = mesa;
            this.comandaCode = comandaCode;
            this.estado = estado;
            this.segundos = segundos;
            this.detalles = detalles;
        }
    }

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
        // Auto-sincronización periódica cada 4 segundos con la BD
        autoSyncTimer = new Timeline(new KeyFrame(Duration.seconds(4), event -> sincronizarNuevasComandas()));
        autoSyncTimer.setCycleCount(Timeline.INDEFINITE);
        autoSyncTimer.play();
    }

    @FXML
    public void handleRefrescar(ActionEvent event) {
        comandasDespachadasLocal.clear();
        cargarComandasDesdeBD();
    }

    private void cargarComandasDesdeBD() {
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                // En KDS solo se consultan pedidos pendientes de preparacion o en coccion (REGISTRADO o EN_PREPARACION)
                List<Pedido> pedidos = pedidoDAO.listarPedidosPendientesCocina(conn);
                List<ComandaDTO> listaComandas = new ArrayList<>();

                for (Pedido p : pedidos) {
                    if (comandasDespachadasLocal.contains(p.getIdPedido())) {
                        continue;
                    }
                    List<DetallePedido> detalles = pedidoDAO.listarDetallesPorPedido(conn, p.getIdPedido());
                    String comandaCode = "COM-" + String.format("%04d", p.getIdPedido());
                    int seg = 0;
                    if (p.getFechaHora() != null) {
                        seg = (int) Math.max(0, java.time.Duration.between(p.getFechaHora(), LocalDateTime.now()).getSeconds());
                    }
                    listaComandas.add(new ComandaDTO(p.getIdPedido(), p.getNumeroMesa(), comandaCode, p.getEstado(), seg, detalles));
                }

                Platform.runLater(() -> {
                    // Detener temporizadores de tarjetas previas
                    for (ComandaCardController c : tarjetasActivas.values()) {
                        c.stopTimer();
                    }
                    tarjetasActivas.clear();
                    flowComandas.getChildren().clear();
                    contadorComandas = 0;

                    for (ComandaDTO c : listaComandas) {
                        crearTarjetaComanda(c.idPedido, c.mesa, c.comandaCode, c.estado, c.segundos, c.detalles);
                    }

                    actualizarContador(0);
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error al cargar comandas desde BD", e);
                Platform.runLater(() -> {
                    lblContadorActivas.setText("Comandas en Cocina: " + contadorComandas);
                });
            }
        }).start();
    }

    private void sincronizarNuevasComandas() {
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                // Solo pedidos que sigan en preparacion en cocina
                List<Pedido> pedidos = pedidoDAO.listarPedidosPendientesCocina(conn);
                Set<Integer> idsPendientesEnBD = new HashSet<>();
                List<ComandaDTO> nuevasComandas = new ArrayList<>();

                for (Pedido p : pedidos) {
                    if (comandasDespachadasLocal.contains(p.getIdPedido())) {
                        continue;
                    }
                    idsPendientesEnBD.add(p.getIdPedido());
                    if (!tarjetasActivas.containsKey(p.getIdPedido())) {
                        List<DetallePedido> detalles = pedidoDAO.listarDetallesPorPedido(conn, p.getIdPedido());
                        String comandaCode = "COM-" + String.format("%04d", p.getIdPedido());
                        int seg = 0;
                        if (p.getFechaHora() != null) {
                            seg = (int) Math.max(0, java.time.Duration.between(p.getFechaHora(), LocalDateTime.now()).getSeconds());
                        }
                        nuevasComandas.add(new ComandaDTO(p.getIdPedido(), p.getNumeroMesa(), comandaCode, p.getEstado(), seg, detalles));
                    }
                }

                // Identificar comandas que ya fueron despachadas, marcadas listas o cobradas
                List<Integer> idsAEliminar = new ArrayList<>();
                for (Integer id : tarjetasActivas.keySet()) {
                    if (id > 0 && !idsPendientesEnBD.contains(id)) {
                        idsAEliminar.add(id);
                    }
                }

                if (!nuevasComandas.isEmpty() || !idsAEliminar.isEmpty()) {
                    Platform.runLater(() -> {
                        // Eliminar comandas que salieron del flujo de cocina
                        for (Integer id : idsAEliminar) {
                            ComandaCardController card = tarjetasActivas.remove(id);
                            if (card != null) {
                                card.stopTimer();
                                flowComandas.getChildren().remove(card.getCardContainer());
                                actualizarContador(-1);
                            }
                        }

                        // Agregar nuevas comandas
                        for (ComandaDTO c : nuevasComandas) {
                            if (!tarjetasActivas.containsKey(c.idPedido) && !comandasDespachadasLocal.contains(c.idPedido)) {
                                crearTarjetaComanda(c.idPedido, c.mesa, c.comandaCode, c.estado, c.segundos, c.detalles);
                                try {
                                    Toolkit.getDefaultToolkit().beep();
                                } catch (Exception ignored) {}
                            }
                        }
                    });
                }
            } catch (Exception e) {
                // Silencioso en auto-sync periódico
            }
        }).start();
    }

    private void procesarMensajeSocket(JsonObject json) {
        if (json.has("accion") && "NUEVA_COMANDA".equals(json.get("accion").getAsString())) {
            int idPed = json.has("idPedido") ? json.get("idPedido").getAsInt() : 0;
            if (idPed > 0 && (tarjetasActivas.containsKey(idPed) || comandasDespachadasLocal.contains(idPed))) {
                return; // Ya está mostrada en pantalla o fue despachada
            }

            String mesa = json.has("mesa") ? json.get("mesa").getAsString() : "01";
            String comanda = json.has("numeroComanda") ? json.get("numeroComanda").getAsString() : "COM-AUTO";

            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {}

            List<DetallePedido> detalles = new ArrayList<>();
            if (json.has("items")) {
                JsonArray items = json.getAsJsonArray("items");
                for (JsonElement el : items) {
                    JsonObject obj = el.getAsJsonObject();
                    String prod = obj.get("producto").getAsString();
                    int cant = obj.get("cantidad").getAsInt();
                    String notas = obj.has("notas") ? obj.get("notas").getAsString() : "";

                    DetallePedido dp = new DetallePedido();
                    dp.setNombreProducto(prod);
                    dp.setCantidad(cant);
                    dp.setNotasPreparacion(notas);
                    detalles.add(dp);
                }
            }

            crearTarjetaComanda(idPed, mesa, comanda, "PENDIENTE", 0, detalles);
        }
    }

    private void crearTarjetaComanda(int idPedido, String mesa, String comanda, String estado, int segundos, List<DetallePedido> detalles) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
            Parent cardNode = loader.load();
            ComandaCardController cardCtrl = loader.getController();
            cardCtrl.initData(mesa, comanda, estado);
            cardCtrl.setIdPedido(idPedido);
            cardCtrl.setSegundosTranscurridos(segundos);

            for (DetallePedido d : detalles) {
                cardCtrl.agregarPlato(d.getNombreProducto(), d.getCantidad(), d.getNotasPreparacion());
            }

            cardCtrl.setOnDespachadoListener(c -> {
                if (idPedido > 0) {
                    tarjetasActivas.remove(idPedido);
                    comandasDespachadasLocal.add(idPedido);
                }
                flowComandas.getChildren().remove(c.getCardContainer());
                actualizarContador(-1);
            });

            if (idPedido > 0) {
                tarjetasActivas.put(idPedido, cardCtrl);
            }

            AnimationUtils.animateModalEnter(cardNode);
            flowComandas.getChildren().add(cardNode);
            actualizarContador(1);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al instanciar tarjeta de comanda", e);
        }
    }

    @FXML
    public void handleSimularComanda(ActionEvent event) {
        JsonObject json = new JsonObject();
        json.addProperty("accion", "NUEVA_COMANDA");
        json.addProperty("mesa", "08");
        json.addProperty("numeroComanda", "COM-" + String.format("%04d", (int) (Math.random() * 1000)));

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

    private void actualizarContador(int delta) {
        contadorComandas += delta;
        if (contadorComandas < 0) contadorComandas = 0;
        lblContadorActivas.setText("Comandas en Cocina: " + contadorComandas);
    }
}
