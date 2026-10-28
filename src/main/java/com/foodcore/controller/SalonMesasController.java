package com.foodcore.controller;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.model.Pedido;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SalonMesasController {

    @FXML private FlowPane flowMesas;

    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();
    private final Map<String, Pedido> pedidosActivosPorMesa = new HashMap<>();

    @FXML
    public void initialize() {
        cargarMesas();
    }

    @FXML
    public void cargarMesas() {
        flowMesas.getChildren().clear();

        new Thread(() -> {
            pedidosActivosPorMesa.clear();
            try (Connection conn = ConexionBD.getConnection()) {
                List<Pedido> activos = pedidoDAO.listarPedidosActivos(conn);
                for (Pedido p : activos) {
                    pedidosActivosPorMesa.put(p.getNumeroMesa(), p);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Platform.runLater(() -> {
                for (int i = 1; i <= 12; i++) {
                    final String numMesa = String.format("%02d", i);
                    boolean ocupada = pedidosActivosPorMesa.containsKey(numMesa);
                    Pedido pedidoActivo = pedidosActivosPorMesa.get(numMesa);

                    Button btnMesa = new Button();
                    btnMesa.setPrefSize(140, 115);

                    if (ocupada) {
                        btnMesa.setText("MESA " + numMesa + "\n\nOCUPADA\nPedido #" + pedidoActivo.getIdPedido() + "\nS/ " + pedidoActivo.getTotal());
                        btnMesa.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #e63946; -fx-border-width: 2px; -fx-font-weight: bold; -fx-text-fill: #e63946; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(230,57,70,0.2), 6, 0, 0, 2);");
                        btnMesa.setOnAction(e -> gestionarMesaOcupada(numMesa, pedidoActivo));
                    } else {
                        btnMesa.setText("MESA " + numMesa + "\n\nLIBRE");
                        btnMesa.setStyle("-fx-background-color: #e6f4ea; -fx-border-color: #2a9d8f; -fx-border-width: 2px; -fx-font-weight: bold; -fx-text-fill: #2a9d8f; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(42,157,143,0.15), 6, 0, 0, 2);");
                        btnMesa.setOnAction(e -> abrirTomaPedido(numMesa));
                    }

                    flowMesas.getChildren().add(btnMesa);
                }
            });
        }).start();
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
            stage.setScene(new Scene(root, 850, 680));
            stage.showAndWait();

            cargarMesas();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void gestionarMesaOcupada(String mesa, Pedido pedido) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Gestión de Mesa " + mesa);
        alert.setHeaderText("Mesa " + mesa + " - Pedido Activo #" + pedido.getIdPedido() + " (Total: S/ " + pedido.getTotal() + ")");
        alert.setContentText("Estado actual: " + pedido.getEstado() + "\n¿Qué acción desea realizar?");

        ButtonType btnAgregarMas = new ButtonType("Agregar Más Platos");
        ButtonType btnLiberar = new ButtonType("Liberar / Cobrado");
        ButtonType btnCancelar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnAgregarMas, btnLiberar, btnCancelar);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == btnAgregarMas) {
                abrirTomaPedido(mesa);
            } else if (result.get() == btnLiberar) {
                liberarMesa(pedido.getIdPedido());
            }
        }
    }

    private void liberarMesa(int idPedido) {
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                pedidoDAO.actualizarEstadoPedido(conn, idPedido, "COBRADO");
                Platform.runLater(this::cargarMesas);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setTitle("Error");
                    a.setContentText("No se pudo liberar la mesa: " + e.getMessage());
                    a.showAndWait();
                });
            }
        }).start();
    }
}
