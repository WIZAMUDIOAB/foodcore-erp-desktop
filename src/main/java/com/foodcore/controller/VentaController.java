package com.foodcore.controller;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Producto;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class VentaController {

    @FXML private TilePane tileProductos;
    @FXML private TextField txtBuscar;
    @FXML private HBox boxCategorias;
    @FXML private TableView<DetalleVenta> tblDetalle;
    @FXML private TableColumn<DetalleVenta, String> colProducto;
    @FXML private TableColumn<DetalleVenta, Integer> colCantidad;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colPrecio;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colSubtotal;

    @FXML private Label lblSubtotal;
    @FXML private Label lblIgv;
    @FXML private Label lblTotal;

    private final ObservableList<DetalleVenta> itemsVenta = FXCollections.observableArrayList();
    private final List<Producto> todosLosProductos = new ArrayList<>();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private int categoriaFiltroSeleccionada = 0; // 0 = Todos

    @FXML
    public void initialize() {
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreProducto()));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
        colSubtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getSubtotal()));

        tblDetalle.setItems(itemsVenta);

        // Doble clic para sumar cantidad
        tblDetalle.setRowFactory(tv -> {
            TableRow<DetalleVenta> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    DetalleVenta dv = row.getItem();
                    dv.setCantidad(dv.getCantidad() + 1);
                    dv.setSubtotal(dv.getPrecioUnitario().multiply(BigDecimal.valueOf(dv.getCantidad())));
                    tblDetalle.refresh();
                    recalcularTotales();
                }
            });
            return row;
        });

        // Filtro en tiempo real por buscador
        txtBuscar.textProperty().addListener((obs, oldVal, newVal) -> filtrarCatalogo());

        cargarCategorias();
        cargarCatalogoProductos();

        // Configurar atajos de teclado F5, F6, F7 cuando el nodo se vincule a una Scene
        Platform.runLater(() -> {
            if (tblDetalle.getScene() != null) {
                tblDetalle.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F5) {
                        handleCobroEfectivo(null);
                        event.consume();
                    } else if (event.getCode() == KeyCode.F6) {
                        handleCobroTarjeta(null);
                        event.consume();
                    } else if (event.getCode() == KeyCode.F7) {
                        handleCobroYape(null);
                        event.consume();
                    }
                });
            }
        });
    }

    private void cargarCategorias() {
        if (boxCategorias == null) return;
        boxCategorias.getChildren().clear();

        Button btnTodos = new Button("Todos");
        btnTodos.setStyle("-fx-background-color: #1d3557; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15px; -fx-cursor: hand;");
        btnTodos.setOnAction(e -> {
            categoriaFiltroSeleccionada = 0;
            actualizarEstiloBotonesCategorias(btnTodos);
            filtrarCatalogo();
        });
        boxCategorias.getChildren().add(btnTodos);

        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                List<Categoria> cats = productoDAO.listarCategorias(conn);
                Platform.runLater(() -> {
                    for (Categoria c : cats) {
                        Button btn = new Button(c.getNombre());
                        btn.setStyle("-fx-background-color: #e9ecef; -fx-text-fill: #1d3557; -fx-background-radius: 15px; -fx-cursor: hand;");
                        btn.setOnAction(ev -> {
                            categoriaFiltroSeleccionada = c.getIdCategoria();
                            actualizarEstiloBotonesCategorias(btn);
                            filtrarCatalogo();
                        });
                        boxCategorias.getChildren().add(btn);
                    }
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void actualizarEstiloBotonesCategorias(Button activo) {
        boxCategorias.getChildren().forEach(node -> {
            if (node instanceof Button b) {
                if (b == activo) {
                    b.setStyle("-fx-background-color: #1d3557; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15px; -fx-cursor: hand;");
                } else {
                    b.setStyle("-fx-background-color: #e9ecef; -fx-text-fill: #1d3557; -fx-background-radius: 15px; -fx-cursor: hand;");
                }
            }
        });
    }

    private void cargarCatalogoProductos() {
        tileProductos.getChildren().clear();
        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                List<Producto> productos = productoDAO.listarProductosDisponibles(conn);
                todosLosProductos.clear();
                todosLosProductos.addAll(productos);
                Platform.runLater(this::filtrarCatalogo);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Producto demo = new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "", new BigDecimal("15.00"), BigDecimal.ZERO, true);
                    todosLosProductos.clear();
                    todosLosProductos.add(demo);
                    filtrarCatalogo();
                });
            }
        }).start();
    }

    private void filtrarCatalogo() {
        tileProductos.getChildren().clear();
        String filtroTexto = txtBuscar.getText() != null ? txtBuscar.getText().trim().toLowerCase() : "";

        for (Producto p : todosLosProductos) {
            boolean coincideCategoria = (categoriaFiltroSeleccionada == 0) || (p.getIdCategoria() == categoriaFiltroSeleccionada);
            boolean coincideTexto = filtroTexto.isEmpty() || p.getNombre().toLowerCase().contains(filtroTexto) || p.getCodigo().toLowerCase().contains(filtroTexto);

            if (coincideCategoria && coincideTexto) {
                Button btn = new Button(p.getNombre() + "\n\nS/ " + p.getPrecioVenta());
                btn.setPrefSize(140, 95);
                btn.setWrapText(true);
                btn.setStyle("-fx-font-weight: bold; -fx-background-color: white; -fx-border-color: #adb5bd; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 4, 0, 0, 1);");
                btn.setOnAction(e -> agregarProductoAVenta(p));
                tileProductos.getChildren().add(btn);
            }
        }
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
            if (sel.getCantidad() > 1) {
                sel.setCantidad(sel.getCantidad() - 1);
                sel.setSubtotal(sel.getPrecioUnitario().multiply(BigDecimal.valueOf(sel.getCantidad())));
                tblDetalle.refresh();
            } else {
                itemsVenta.remove(sel);
            }
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
        abrirModalCobro("EFECTIVO");
    }

    @FXML
    public void handleCobroTarjeta(ActionEvent event) {
        abrirModalCobro("TARJETA");
    }

    @FXML
    public void handleCobroYape(ActionEvent event) {
        abrirModalCobro("YAPE_PLIN");
    }

    private void abrirModalCobro(String metodo) {
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "No hay productos en la venta activa.", Alert.AlertType.WARNING);
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            total = total.add(dv.getSubtotal());
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/CobroModalView.fxml"));
            Parent root = loader.load();

            CobroModalController ctrl = loader.getController();
            ctrl.initData(total, metodo, itemsVenta, () -> {
                itemsVenta.clear();
                recalcularTotales();
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Finalizar Venta y Cobro - FoodCore POS");
            stage.setScene(new Scene(root, 480, 620));
            stage.setResizable(false);
            stage.showAndWait();

        } catch (IOException e) {
            mostrarAlerta("Error", "No se pudo abrir la ventana de cobro: " + e.getMessage(), Alert.AlertType.ERROR);
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
