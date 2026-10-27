package com.foodcore.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class SalonMesasController {

    @FXML private FlowPane flowMesas;

    @FXML
    public void initialize() {
        cargarMesas();
    }

    @FXML
    public void cargarMesas() {
        flowMesas.getChildren().clear();
        // Generar 12 mesas de salón
        for (int i = 1; i <= 12; i++) {
            final String numMesa = String.format("%02d", i);
            boolean ocupada = (i == 2 || i == 5); // Simuladas como ocupadas para demo

            Button btnMesa = new Button("MESA " + numMesa + "\n\n" + (ocupada ? "OCUPADA" : "LIBRE"));
            btnMesa.setPrefSize(130, 110);
            btnMesa.setStyle(ocupada
                ? "-fx-background-color: #fee2e2; -fx-border-color: #e63946; -fx-border-width: 2px; -fx-font-weight: bold; -fx-text-fill: #e63946; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand;"
                : "-fx-background-color: #e6f4ea; -fx-border-color: #2a9d8f; -fx-border-width: 2px; -fx-font-weight: bold; -fx-text-fill: #2a9d8f; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand;"
            );

            btnMesa.setOnAction(e -> abrirTomaPedido(numMesa));
            flowMesas.getChildren().add(btnMesa);
        }
    }

    private void abrirTomaPedido(String mesa) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MozoOrderView.fxml"));
            Parent root = loader.load();

            MozoOrderController controller = loader.getController();
            controller.setNumeroMesa(mesa);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Comanda de Salón - Mesa " + mesa);
            stage.setScene(new Scene(root, 750, 650));
            stage.showAndWait();

            cargarMesas(); // Refrescar tras cerrar
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
