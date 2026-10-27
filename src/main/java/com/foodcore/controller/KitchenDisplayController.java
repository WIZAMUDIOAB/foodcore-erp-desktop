package com.foodcore.controller;

import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

import java.awt.*;
import java.io.IOException;

public class KitchenDisplayController {

    @FXML private FlowPane flowComandas;
    @FXML private Label lblSocketStatus;
    @FXML private Label lblContadorActivas;

    private int contadorComandas = 0;

    @FXML
    public void initialize() {
        iniciarServidorSockets();
        // Cargar comanda inicial de ejemplo
        crearTarjetaComanda("03", "COM-0001", "PENDIENTE", new String[][]{
            {"Hamburguesa Doble Queso", "2", "Sin cebolla"},
            {"Papas Fritas Grandes", "1", "Extra crocantes"}
        });
    }

    private void iniciarServidorSockets() {
        ServidorSockets server = ServidorSockets.getInstance();
        server.setMensajeListener(json -> Platform.runLater(() -> procesarMensajeSocket(json)));
        server.iniciar();
    }

    private void procesarMensajeSocket(JsonObject json) {
        if (json.has("accion") && "NUEVA_COMANDA".equals(json.get("accion").getAsString())) {
            String mesa = json.has("mesa") ? json.get("mesa").getAsString() : "01";
            String comanda = json.has("numeroComanda") ? json.get("numeroComanda").getAsString() : "COM-AUTO";

            try {
                // Alerta sonora del sistema
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
            try {
                Parent cardNode = loader.load();
                ComandaCardController cardCtrl = loader.getController();
                cardCtrl.initData(mesa, comanda, "PENDIENTE");

                if (json.has("items")) {
                    JsonArray items = json.getAsJsonArray("items");
                    for (JsonElement el : items) {
                        JsonObject obj = el.getAsJsonObject();
                        String prod = obj.get("producto").getAsString();
                        int cant = obj.get("cantidad").getAsInt();
                        String notas = obj.has("notas") ? obj.get("notas").getAsString() : "";
                        cardCtrl.agregarPlato(prod, cant, notas);
                    }
                }

                cardCtrl.setOnDespachadoListener(c -> {
                    flowComandas.getChildren().remove(c.getCardContainer());
                    actualizarContador(-1);
                });

                flowComandas.getChildren().add(cardNode);
                actualizarContador(1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void crearTarjetaComanda(String mesa, String comanda, String estado, String[][] platos) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
            Parent cardNode = loader.load();
            ComandaCardController cardCtrl = loader.getController();
            cardCtrl.initData(mesa, comanda, estado);

            for (String[] plato : platos) {
                cardCtrl.agregarPlato(plato[0], Integer.parseInt(plato[1]), plato[2]);
            }

            cardCtrl.setOnDespachadoListener(c -> {
                flowComandas.getChildren().remove(c.getCardContainer());
                actualizarContador(-1);
            });

            flowComandas.getChildren().add(cardNode);
            actualizarContador(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleSimularComanda(ActionEvent event) {
        JsonObject json = new JsonObject();
        json.addProperty("accion", "NUEVA_COMANDA");
        json.addProperty("mesa", "08");
        json.addProperty("numeroComanda", "COM-" + String.format("%04d", (int)(Math.random() * 1000)));

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
