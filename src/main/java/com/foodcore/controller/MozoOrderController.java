package com.foodcore.controller;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.service.AuthService;
import com.foodcore.service.PedidoService;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

public class MozoOrderController {

    @FXML private Label lblTituloMesa;
    @FXML private TilePane tilePlatos;
    @FXML private TableView<DetallePedido> tblItems;
    @FXML private TableColumn<DetallePedido, String> colPlato;
    @FXML private TableColumn<DetallePedido, Integer> colCantidad;
    @FXML private TableColumn<DetallePedido, BigDecimal> colPrecio;
    @FXML private TableColumn<DetallePedido, String> colNotas;

    @FXML private TextField txtNotaEspecial;
    @FXML private Label lblTotal;

    private String mesa = "01";
    private final ObservableList<DetallePedido> items = FXCollections.observableArrayList();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final PedidoService pedidoService = new PedidoService();

    public void setNumeroMesa(String mesa) {
        this.mesa = mesa;
        lblTituloMesa.setText("COMANDA RÁPIDA (IU07) - MESA " + mesa);
    }

    @FXML
    public void initialize() {
        colPlato.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreProducto()));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
        colNotas.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNotasPreparacion()));

        tblItems.setItems(items);
        cargarPlatosDisponibles();
    }

    private void cargarPlatosDisponibles() {
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                List<Producto> productos = productoDAO.listarProductosDisponibles(conn);
                Platform.runLater(() -> {
                    for (Producto p : productos) {
                        Button btn = new Button(p.getNombre() + "\nS/ " + p.getPrecioVenta());
                        btn.setPrefSize(130, 75);
                        btn.setWrapText(true);
                        btn.setStyle("-fx-background-color: white; -fx-border-color: #adb5bd; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-cursor: hand; -fx-font-weight: bold;");
                        btn.setOnAction(e -> agregarPlato(p));
                        tilePlatos.getChildren().add(btn);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    // Botón de fallback para demo
                    Button btn = new Button("Hamburguesa Clásica\nS/ 15.00");
                    btn.setPrefSize(130, 75);
                    btn.setOnAction(ev -> {
                        Producto demo = new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "", new BigDecimal("15.00"), BigDecimal.ZERO, true);
                        agregarPlato(demo);
                    });
                    tilePlatos.getChildren().add(btn);
                });
            }
        }).start();
    }

    private void agregarPlato(Producto p) {
        for (DetallePedido dp : items) {
            if (dp.getIdProducto() == p.getIdProducto()) {
                dp.setCantidad(dp.getCantidad() + 1);
                dp.setSubtotal(dp.getPrecioUnitario().multiply(BigDecimal.valueOf(dp.getCantidad())));
                tblItems.refresh();
                calcularTotal();
                return;
            }
        }
        DetallePedido nuevo = new DetallePedido(0, 0, p.getIdProducto(), 1, p.getPrecioVenta(), p.getPrecioVenta(), "");
        nuevo.setNombreProducto(p.getNombre());
        items.add(nuevo);
        calcularTotal();
    }

    @FXML
    public void handleAplicarNota(ActionEvent event) {
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null && !txtNotaEspecial.getText().trim().isEmpty()) {
            sel.setNotasPreparacion(txtNotaEspecial.getText().trim());
            tblItems.refresh();
            txtNotaEspecial.clear();
        }
    }

    private void calcularTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido dp : items) {
            total = total.add(dp.getSubtotal());
        }
        lblTotal.setText("S/ " + total);
    }

    @FXML
    public void handleEnviarComanda(ActionEvent event) {
        if (items.isEmpty()) {
            mostrarAlerta("Atención", "Agregue al menos un plato a la comanda.", Alert.AlertType.WARNING);
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido dp : items) {
            total = total.add(dp.getSubtotal());
        }

        int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 3;
        Pedido pedido = new Pedido(0, idUsuario, null, mesa, "REGISTRADO", total);

        try {
            pedidoService.registrarPedidoYEnviarComanda(pedido, items);
            mostrarAlerta("Comanda Despachada", "La comanda de la Mesa " + mesa + " fue registrada y transmitida vía Socket TCP (puerto 9090) a Cocina.", Alert.AlertType.INFORMATION);
            Stage s = (Stage) lblTotal.getScene().getWindow();
            s.close();
        } catch (Exception e) {
            mostrarAlerta("Error al Enviar", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }
}
