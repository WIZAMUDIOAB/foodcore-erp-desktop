package com.foodcore.controller;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.model.Pedido;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
                    btnMesa.setPrefSize(175, 125);
                    btnMesa.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                    VBox box = new VBox(6.0);
                    box.setAlignment(Pos.CENTER_LEFT);

                    HBox topRow = new HBox(8.0);
                    topRow.setAlignment(Pos.CENTER_LEFT);
                    Label lblMesaTitle = new Label("Mesa " + numMesa);
                    lblMesaTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 14px; -fx-text-fill: #11141a;");
                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    Label lblBadge = new Label();
                    lblBadge.setStyle("-fx-font-size: 9px; -fx-font-weight: 700; -fx-padding: 2px 7px; -fx-background-radius: 999px;");

                    if (ocupada) {
                        btnMesa.getStyleClass().add("mesa-card-ocupada");
                        lblBadge.setText("EN SERVICIO");
                        lblBadge.setStyle(lblBadge.getStyle() + "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626;");

                        topRow.getChildren().addAll(lblMesaTitle, sp, lblBadge);

                        Label lblPedido = new Label("Comanda #" + pedidoActivo.getIdPedido());
                        lblPedido.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

                        Label lblTotalMesa = new Label("S/ " + pedidoActivo.getTotal());
                        lblTotalMesa.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #d94e34;");

                        box.getChildren().addAll(topRow, lblPedido, lblTotalMesa);
                        btnMesa.setOnAction(e -> gestionarMesaOcupada(numMesa, pedidoActivo));
                    } else {
                        btnMesa.getStyleClass().add("mesa-card-libre");
                        lblBadge.setText("LIBRE");
                        lblBadge.setStyle(lblBadge.getStyle() + "-fx-background-color: #ecfdf5; -fx-text-fill: #059669;");

                        topRow.getChildren().addAll(lblMesaTitle, sp, lblBadge);

                        Label lblCapacidad = new Label("4 comensales");
                        lblCapacidad.setStyle("-fx-font-size: 11px; -fx-text-fill: #9ca3af;");

                        Label lblAction = new Label("Tocar para comanda →");
                        lblAction.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #059669;");

                        box.getChildren().addAll(topRow, lblCapacidad, lblAction);
                        btnMesa.setOnAction(e -> abrirTomaPedido(numMesa));
                    }

                    btnMesa.setGraphic(box);
                    AnimationUtils.attachTactileFeedback(btnMesa);
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

            Scene scene = new Scene(root, 920, 710);
            stage.setMinWidth(900);
            stage.setMinHeight(680);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            AnimationUtils.animateModalEnter(root);

            stage.setScene(scene);
            stage.showAndWait();

            cargarMesas();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void gestionarMesaOcupada(String mesa, Pedido pedido) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Gestión de Mesa " + mesa);
        alert.setHeaderText("Mesa " + mesa + " · Comanda Activa #" + pedido.getIdPedido() + " (Total: S/ " + pedido.getTotal() + ")");
        alert.setContentText("Estado actual: " + pedido.getEstado() + "\nSeleccione la acción a realizar:");

        ButtonType btnAgregarMas = new ButtonType("Agregar Más Platos");
        ButtonType btnLiberar = new ButtonType("Liberar / Mesa Cobrada");
        ButtonType btnCancelar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnAgregarMas, btnLiberar, btnCancelar);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == btnAgregarMas) {
                abrirTomaPedido(mesa);
            } else if (result.get() == btnLiberar) {
                try (Connection conn = ConexionBD.getConnection()) {
                    pedidoDAO.actualizarEstadoPedido(conn, pedido.getIdPedido(), "COBRADO");
                    cargarMesas();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
