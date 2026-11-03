package com.foodcore.controller;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Producto;
import com.foodcore.model.Venta;
import com.foodcore.service.VentaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Punto de Venta (POS) con atajos F5/F6/F7/Esc y protección anti-doble cobro
 * según SPEC-FOODCORE-v1.0 (§15.3, §19.5, §19.12; REMEDIATION R-03).
 */
public class VentaController {

    private static final Logger LOGGER = Logger.getLogger(VentaController.class.getName());

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

    @FXML private Button btnEfectivo;
    @FXML private Button btnTarjeta;
    @FXML private Button btnYape;

    private final ObservableList<DetalleVenta> itemsVenta = FXCollections.observableArrayList();
    private final List<Producto> todosLosProductos = new ArrayList<>();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final VentaService ventaService = new VentaService();
    private final AtomicBoolean cobroEnProgreso = new AtomicBoolean(false);
    private boolean modoCobroDirectoTest = false;
    private boolean confirmacionEscapeAutomatica = false;
    private int categoriaFiltroSeleccionada = 0;

    @FXML
    public void initialize() {
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreProducto()));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
        colSubtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getSubtotal()));

        tblDetalle.setItems(itemsVenta);

        AnimationUtils.attachTactileFeedback(btnEfectivo);
        AnimationUtils.attachTactileFeedback(btnTarjeta);
        AnimationUtils.attachTactileFeedback(btnYape);

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

        txtBuscar.textProperty().addListener((obs, oldVal, newVal) -> filtrarCatalogo());

        cargarCategorias();
        cargarCatalogoProductos();

        Platform.runLater(this::registrarAtajosTeclado);
    }

    public void registrarAtajosTeclado() {
        if (tblDetalle != null && tblDetalle.getScene() != null) {
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
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    handleLimpiarConEscape();
                    event.consume();
                }
            });
        }
    }

    private void cargarCategorias() {
        if (boxCategorias == null) {
            return;
        }
        boxCategorias.getChildren().clear();

        Button btnTodos = new Button("Todos");
        btnTodos.getStyleClass().addAll("category-chip", "category-chip-active");
        AnimationUtils.attachTactileFeedback(btnTodos);
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
                        btn.getStyleClass().add("category-chip");
                        AnimationUtils.attachTactileFeedback(btn);
                        btn.setOnAction(ev -> {
                            categoriaFiltroSeleccionada = c.getIdCategoria();
                            actualizarEstiloBotonesCategorias(btn);
                            filtrarCatalogo();
                        });
                        boxCategorias.getChildren().add(btn);
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Categorías no disponibles en arranque", e);
            }
        }).start();
    }

    private void actualizarEstiloBotonesCategorias(Button activo) {
        boxCategorias.getChildren().forEach(node -> {
            if (node instanceof Button b) {
                b.getStyleClass().remove("category-chip-active");
                if (b == activo) {
                    b.getStyleClass().add("category-chip-active");
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
            boolean coincideTexto = filtroTexto.isEmpty()
                || p.getNombre().toLowerCase().contains(filtroTexto)
                || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(filtroTexto));

            if (coincideCategoria && coincideTexto) {
                Button btn = new Button();
                btn.setId("btnProd_" + p.getIdProducto());
                btn.setPrefSize(160, 105);
                btn.getStyleClass().add("surface-card-interactive");

                VBox content = new VBox(3.0);
                content.setAlignment(Pos.TOP_LEFT);

                Label lblNombre = new Label(p.getNombre());
                lblNombre.setWrapText(true);
                lblNombre.setStyle("-fx-font-weight: 700; -fx-text-fill: #11141a; -fx-font-size: 13px;");

                Label lblCodigo = new Label(p.getCodigo() != null ? p.getCodigo() : "PLATO");
                lblCodigo.setStyle("-fx-text-fill: #8c96a5; -fx-font-size: 10px; -fx-font-weight: 600;");

                Region spacer = new Region();
                VBox.setVgrow(spacer, Priority.ALWAYS);

                Label lblPrecio = new Label("S/ " + p.getPrecioVenta());
                lblPrecio.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #d94e34;");

                content.getChildren().addAll(lblNombre, lblCodigo, spacer, lblPrecio);
                btn.setGraphic(content);
                btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                AnimationUtils.attachTactileFeedback(btn);
                btn.setOnAction(e -> agregarProductoAVenta(p));
                tileProductos.getChildren().add(btn);
            }
        }
    }

    public void agregarProductoAVenta(Producto p) {
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

    public void handleLimpiarConEscape() {
        if (itemsVenta.isEmpty()) {
            return;
        }
        if (confirmacionEscapeAutomatica) {
            itemsVenta.clear();
            recalcularTotales();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "¿Desea limpiar los ítems de la venta actual?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar limpieza (Esc)");
        confirm.setHeaderText(null);
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            itemsVenta.clear();
            recalcularTotales();
        }
    }

    private void recalcularTotales() {
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            total = total.add(dv.getSubtotal());
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            lblSubtotal.setText("S/ 0.00");
            lblIgv.setText("S/ 0.00");
            lblTotal.setText("S/ 0.00");
            return;
        }
        try {
            DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(total);
            lblSubtotal.setText("S/ " + desglose.getSubtotalGravado());
            lblIgv.setText("S/ " + desglose.getIgv());
            lblTotal.setText("S/ " + desglose.getTotal());
        } catch (com.foodcore.exception.NegocioException e) {
            LOGGER.log(Level.WARNING, "Error calculando desglose fiscal", e);
        }
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

    private void setBotonesCobroDeshabilitados(boolean deshabilitado) {
        if (btnEfectivo != null) {
            btnEfectivo.setDisable(deshabilitado);
        }
        if (btnTarjeta != null) {
            btnTarjeta.setDisable(deshabilitado);
        }
        if (btnYape != null) {
            btnYape.setDisable(deshabilitado);
        }
    }

    private void abrirModalCobro(String metodo) {
        // Protección anti-doble clic / doble F5 (§19.5, R-03)
        if (!cobroEnProgreso.compareAndSet(false, true)) {
            return;
        }
        setBotonesCobroDeshabilitados(true);

        if (itemsVenta.isEmpty()) {
            cobroEnProgreso.set(false);
            setBotonesCobroDeshabilitados(false);
            if (!modoCobroDirectoTest) {
                mostrarAlerta("Atención", "No hay productos en la venta activa.", Alert.AlertType.WARNING);
            }
            return;
        }

        if (modoCobroDirectoTest) {
            try {
                BigDecimal total = BigDecimal.ZERO;
                for (DetalleVenta dv : itemsVenta) {
                    total = total.add(dv.getSubtotal());
                }
                DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(total);
                Venta v = new Venta(0, null, 1, 1, 1,
                    desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
                    metodo, desglose.getTotal(), BigDecimal.ZERO);
                v.setIdempotencyKey(UUID.randomUUID().toString());
                ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "TCK", "000001", desglose.getTotal());
                ventaService.procesarCobroVenta(v, new ArrayList<>(itemsVenta), comp);
                itemsVenta.clear();
                recalcularTotales();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error en cobro directo POS", e);
            } finally {
                cobroEnProgreso.set(false);
                setBotonesCobroDeshabilitados(false);
            }
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
            stage.setTitle("Finalizar Venta y Cobro - FoodCore");

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            AnimationUtils.animateModalEnter(root);

            stage.setScene(scene);
            stage.sizeToScene();
            stage.setResizable(false);
            stage.showAndWait();

        } catch (IOException e) {
            mostrarAlerta("Error", "No se pudo abrir la ventana de cobro: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
            cobroEnProgreso.set(false);
            setBotonesCobroDeshabilitados(false);
        }
    }

    @FXML
    public void handleAbrirArqueo(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ArqueoCajaView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Control de Caja y Turnos");

            Scene scene = new Scene(root, 520, 580);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            AnimationUtils.animateModalEnter(root);

            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            mostrarAlerta("Error", "No se pudo abrir el arqueo: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public ObservableList<DetalleVenta> getItemsVenta() {
        return itemsVenta;
    }

    public Button getBtnEfectivo() {
        return btnEfectivo;
    }

    public void setModoCobroDirectoTest(boolean modoCobroDirectoTest) {
        this.modoCobroDirectoTest = modoCobroDirectoTest;
    }

    public void setConfirmacionEscapeAutomatica(boolean confirmacionEscapeAutomatica) {
        this.confirmacionEscapeAutomatica = confirmacionEscapeAutomatica;
    }

    public void simularEstadoTransaccionEnCurso(boolean enCurso) {
        cobroEnProgreso.set(enCurso);
        setBotonesCobroDeshabilitados(enCurso);
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
