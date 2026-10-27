package com.foodcore.controller;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Producto;
import com.foodcore.model.Venta;
import com.foodcore.service.AuthService;
import com.foodcore.service.VentaService;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.List;

public class VentaController {

    @FXML private TilePane tileProductos;
    @FXML private TextField txtBuscar;
    @FXML private TableView<DetalleVenta> tblDetalle;
    @FXML private TableColumn<DetalleVenta, String> colProducto;
    @FXML private TableColumn<DetalleVenta, Integer> colCantidad;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colPrecio;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colSubtotal;

    @FXML private Label lblSubtotal;
    @FXML private Label lblIgv;
    @FXML private Label lblTotal;

    private final ObservableList<DetalleVenta> itemsVenta = FXCollections.observableArrayList();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final VentaService ventaService = new VentaService();

    @FXML
    public void initialize() {
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreProducto()));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
        colSubtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getSubtotal()));

        tblDetalle.setItems(itemsVenta);
        cargarCatalogoProductos();
    }

    private void cargarCatalogoProductos() {
        tileProductos.getChildren().clear();
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                List<Producto> productos = productoDAO.listarProductosDisponibles(conn);
                Platform.runLater(() -> {
                    for (Producto p : productos) {
                        Button btn = new Button(p.getNombre() + "\nS/ " + p.getPrecioVenta());
                        btn.setPrefSize(140, 90);
                        btn.setWrapText(true);
                        btn.setStyle("-fx-font-weight: bold; -fx-background-color: white; -fx-border-color: #adb5bd; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand;");
                        btn.setOnAction(e -> agregarProductoAVenta(p));
                        tileProductos.getChildren().add(btn);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    // Fallback visual si la BD aún no está encendida
                    Button btn = new Button("Hamburguesa Clásica\nS/ 15.00");
                    btn.setPrefSize(140, 90);
                    btn.setOnAction(ev -> {
                        Producto demo = new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "", new BigDecimal("15.00"), BigDecimal.ZERO, true);
                        agregarProductoAVenta(demo);
                    });
                    tileProductos.getChildren().add(btn);
                });
            }
        }).start();
    }

    private void agregarProductoAVenta(Producto p) {
        for (DetalleVenta dv : itemsVenta) {
            if (dv.getIdProducto() == p.getIdProducto()) {
                dv.setCantidad(dv.getCantidad() + 1);
                dv.setSubtotal(dv.getPrecioUnitario().multiply(BigDecimal.valueOf(dv.getCantidad())));
                tblDetalle.refresh();
                recalcularTotales();
                return;
            }
        }
        DetalleVenta nuevo = new DetalleVenta(0, 0, p.getIdProducto(), 1, p.getPrecioVenta(), p.getPrecioVenta());
        nuevo.setNombreProducto(p.getNombre());
        itemsVenta.add(nuevo);
        recalcularTotales();
    }

    @FXML
    public void handleEliminarItem() {
        DetalleVenta sel = tblDetalle.getSelectionModel().getSelectedItem();
        if (sel != null) {
            itemsVenta.remove(sel);
            recalcularTotales();
        }
    }

    private void recalcularTotales() {
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            total = total.add(dv.getSubtotal());
        }
        // Subtotal = Total / 1.18, IGV = Total - Subtotal (Cálculo según SUNAT Perú IGV 18%)
        BigDecimal divisor = new BigDecimal("1.18");
        BigDecimal subtotal = total.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);

        lblSubtotal.setText("S/ " + subtotal.toString());
        lblIgv.setText("S/ " + igv.toString());
        lblTotal.setText("S/ " + total.toString());
    }

    @FXML
    public void handleCobroEfectivo(ActionEvent event) {
        procesarCobro("EFECTIVO");
    }

    @FXML
    public void handleCobroTarjeta(ActionEvent event) {
        procesarCobro("TARJETA");
    }

    @FXML
    public void handleCobroYape(ActionEvent event) {
        procesarCobro("YAPE_PLIN");
    }

    private void procesarCobro(String metodo) {
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "No hay productos en la venta activa.", Alert.AlertType.WARNING);
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            total = total.add(dv.getSubtotal());
        }
        BigDecimal divisor = new BigDecimal("1.18");
        BigDecimal subtotal = total.divide(divisor, 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);

        int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
        Venta v = new Venta(0, null, 1, idUsuario, 1, subtotal, igv, total, metodo, total, BigDecimal.ZERO);
        ComprobantePago comp = new ComprobantePago(0, 0, "BOLETA", "B001", String.valueOf(System.currentTimeMillis() % 100000), total);

        try {
            ventaService.registrarVenta(v, itemsVenta, comp);
            mostrarAlerta("Venta Exitosa", "Venta registrada con éxito.\nComprobante emitido: " + comp.getNumeroCompleto() + "\nStock descontado vía receta.", Alert.AlertType.INFORMATION);
            itemsVenta.clear();
            recalcularTotales();
        } catch (Exception e) {
            mostrarAlerta("Error al Cobrar", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleAbrirArqueo(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ArqueoCajaView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Arqueo y Cierre de Caja");
            stage.setScene(new Scene(root, 520, 580));
            stage.showAndWait();
        } catch (IOException e) {
            mostrarAlerta("Error", "No se pudo abrir el arqueo: " + e.getMessage(), Alert.AlertType.ERROR);
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
