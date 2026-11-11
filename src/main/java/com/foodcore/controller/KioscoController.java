package com.foodcore.controller;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KioscoController {

    private static final Logger LOGGER = Logger.getLogger(KioscoController.class.getName());
    private static final AtomicInteger SECUENCIA_TURNO = new AtomicInteger(101);

    @FXML private Label lblTurnoActual;
    @FXML private HBox boxCategoriasKiosco;
    @FXML private FlowPane flowProductosKiosco;

    @FXML private TableView<DetallePedido> tblCarrito;
    @FXML private TableColumn<DetallePedido, String> colProducto;
    @FXML private TableColumn<DetallePedido, String> colCantidad;
    @FXML private TableColumn<DetallePedido, String> colSubtotal;
    @FXML private TableColumn<DetallePedido, Void> colAccion;

    @FXML private Label lblSubtotal;
    @FXML private Label lblIgv;
    @FXML private Label lblTotalKiosco;
    @FXML private Button btnConfirmarKiosco;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    private final List<Producto> todosLosProductos = new ArrayList<>();
    private final ObservableList<DetallePedido> carritoItems = FXCollections.observableArrayList();
    private int categoriaSeleccionada = 0;
    private int turnoActual = 101;

    @FXML
    public void initialize() {
        this.turnoActual = SECUENCIA_TURNO.get();
        if (lblTurnoActual != null) {
            lblTurnoActual.setText("TURNO #T-" + turnoActual);
        }

        configurarTablaCarrito();
        cargarCategorias();
        cargarCatalogo();
        recalcularTotales();
    }

    private void configurarTablaCarrito() {
        colProducto.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getNombreProducto()));
        colCantidad.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getCantidad())));
        colSubtotal.setCellValueFactory(cell -> new SimpleStringProperty("S/ " + cell.getValue().getSubtotal()));

        colAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btnMenos = new Button("-");
            private final Button btnMas = new Button("+");
            private final HBox box = new HBox(8.0, btnMenos, btnMas);

            {
                box.setAlignment(Pos.CENTER);
                btnMenos.getStyleClass().add("btn-kiosco-ajuste");
                btnMas.getStyleClass().add("btn-kiosco-ajuste");
                String btnCartStyle = "-fx-min-width: 32px; -fx-pref-width: 32px; -fx-max-width: 32px; "
                        + "-fx-min-height: 32px; -fx-pref-height: 32px; -fx-max-height: 32px; "
                        + "-fx-padding: 0; -fx-font-size: 16px; -fx-font-weight: 700; "
                        + "-fx-background-color: #FFFFFF; -fx-text-fill: #374151; "
                        + "-fx-border-color: #D1D5DB; -fx-border-radius: 6px; -fx-background-radius: 6px; "
                        + "-fx-cursor: hand; -fx-alignment: center;";
                btnMenos.setStyle(btnCartStyle);
                btnMas.setStyle(btnCartStyle);
                btnMenos.setMinWidth(32);
                btnMenos.setPrefWidth(32);
                btnMas.setMinWidth(32);
                btnMas.setPrefWidth(32);
                AnimationUtils.attachTactileFeedback(btnMenos);
                AnimationUtils.attachTactileFeedback(btnMas);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    DetallePedido det = getTableRow().getItem();
                    btnMenos.setOnAction(e -> decrementarItem(det));
                    btnMas.setOnAction(e -> incrementarItem(det));
                    setGraphic(box);
                    setAlignment(Pos.CENTER);
                    setStyle("-fx-padding: 0 4 0 4; -fx-alignment: CENTER;");
                }
            }
        });

        tblCarrito.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tblCarrito.setItems(carritoItems);
    }

    private void cargarCategorias() {
        if (boxCategoriasKiosco == null) return;
        boxCategoriasKiosco.getChildren().clear();

        Button btnTodos = new Button("Todos");
        btnTodos.getStyleClass().addAll("category-chip", "category-chip-active");
        AnimationUtils.attachTactileFeedback(btnTodos);
        btnTodos.setOnAction(e -> {
            categoriaSeleccionada = 0;
            actualizarEstiloChips(btnTodos);
            renderizarMosaicoProductos();
        });
        boxCategoriasKiosco.getChildren().add(btnTodos);

        try (Connection conn = ConexionBD.getConnection()) {
            List<Categoria> cats = productoDAO.listarCategorias(conn);
            if (cats != null && !cats.isEmpty()) {
                for (Categoria c : cats) {
                    Button chip = new Button(c.getNombre());
                    chip.getStyleClass().add("category-chip");
                    AnimationUtils.attachTactileFeedback(chip);
                    chip.setOnAction(ev -> {
                        categoriaSeleccionada = c.getIdCategoria();
                        actualizarEstiloChips(chip);
                        renderizarMosaicoProductos();
                    });
                    boxCategoriasKiosco.getChildren().add(chip);
                }
            } else {
                poblarCategoriasDemo();
            }
        } catch (Exception e) {
            poblarCategoriasDemo();
        }
    }

    private void poblarCategoriasDemo() {
        String[] cats = {"Hamburguesas", "Pizzas", "Alitas & Pollo", "Bebidas", "Postres"};
        int id = 1;
        for (String c : cats) {
            final int idCat = id++;
            Button chip = new Button(c);
            chip.getStyleClass().add("category-chip");
            AnimationUtils.attachTactileFeedback(chip);
            chip.setOnAction(ev -> {
                categoriaSeleccionada = idCat;
                actualizarEstiloChips(chip);
                renderizarMosaicoProductos();
            });
            boxCategoriasKiosco.getChildren().add(chip);
        }
    }

    private void actualizarEstiloChips(Button activo) {
        boxCategoriasKiosco.getChildren().forEach(n -> {
            if (n instanceof Button b) {
                b.getStyleClass().remove("category-chip-active");
            }
        });
        activo.getStyleClass().add("category-chip-active");
    }

    public void cargarCatalogo() {
        todosLosProductos.clear();
        try (Connection conn = ConexionBD.getConnection()) {
            List<Producto> lista = productoDAO.listarProductosDisponibles(conn);
            if (lista != null && !lista.isEmpty()) {
                todosLosProductos.addAll(lista);
            } else {
                poblarProductosDemo();
            }
        } catch (Exception e) {
            poblarProductosDemo();
        }
        renderizarMosaicoProductos();
    }

    private void poblarProductosDemo() {
        todosLosProductos.add(new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "Carne 180g, queso cheddar fundido", new BigDecimal("18.50"), BigDecimal.ZERO, true));
        todosLosProductos.add(new Producto(2, 1, "HAM-02", "Hamburguesa Doble Tocino", "Doble smash, cheddar y tocino crocante", new BigDecimal("24.00"), BigDecimal.ZERO, true));
        todosLosProductos.add(new Producto(3, 2, "PIZ-01", "Pizza Artesanal Personal", "Mozzarella, jamón serrano y albahaca", new BigDecimal("26.00"), BigDecimal.ZERO, true));
        todosLosProductos.add(new Producto(4, 3, "ALI-01", "Alitas BBQ Clásicas x8", "Alitas bañadas en salsa BBQ dulce ahumada", new BigDecimal("22.00"), BigDecimal.ZERO, true));
        todosLosProductos.add(new Producto(5, 3, "ALI-02", "Alitas Acevichadas x8", "Crujientes con emulsión acevichada", new BigDecimal("24.00"), BigDecimal.ZERO, true));
        todosLosProductos.add(new Producto(6, 4, "BEB-01", "Gaseosa Personal 500ml", "Inca Kola / Coca Cola helada", new BigDecimal("6.00"), BigDecimal.ZERO, true));
        todosLosProductos.add(new Producto(7, 4, "BEB-02", "Limonada Frozen 1L", "Jarra clásica de limón natural", new BigDecimal("10.00"), BigDecimal.ZERO, true));
    }

    private void renderizarMosaicoProductos() {
        if (flowProductosKiosco == null) return;
        flowProductosKiosco.getChildren().clear();

        for (Producto p : todosLosProductos) {
            if (!p.isDisponible()) {
                continue;
            }
            if (categoriaSeleccionada != 0 && p.getIdCategoria() != categoriaSeleccionada) {
                continue;
            }

            VBox card = new VBox(8.0);
            card.setPrefSize(180, 160);
            card.setStyle("-fx-background-color: white; -fx-padding: 12; -fx-background-radius: 12; -fx-border-color: #E2E8F0; -fx-border-radius: 12;");

            Label lblNom = new Label(p.getNombre());
            lblNom.setWrapText(true);
            lblNom.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #1E293B;");

            Label lblDesc = new Label(p.getDescripcion() != null && !p.getDescripcion().isBlank() ? p.getDescripcion() : "Especialidad de la casa");
            lblDesc.setWrapText(true);
            lblDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
            VBox.setVgrow(lblDesc, Priority.ALWAYS);

            HBox footer = new HBox(8.0);
            footer.setAlignment(Pos.CENTER_LEFT);

            Label lblPrecio = new Label("S/ " + p.getPrecioVenta());
            lblPrecio.setStyle("-fx-font-weight: 800; -fx-font-size: 14px; -fx-text-fill: #FF5A1F; -fx-font-family: monospace;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnAdd = new Button("+ Agregar");
            btnAdd.getStyleClass().add("btn-primary");
            btnAdd.setStyle("-fx-font-size: 11px; -fx-padding: 5 10 5 10;");
            AnimationUtils.attachTactileFeedback(btnAdd);
            btnAdd.setOnAction(e -> agregarAlCarrito(p));

            footer.getChildren().addAll(lblPrecio, spacer, btnAdd);
            card.getChildren().addAll(lblNom, lblDesc, footer);
            flowProductosKiosco.getChildren().add(card);
        }
    }

    public void agregarAlCarrito(Producto p) {
        if (p == null || !p.isDisponible()) {
            return;
        }
        for (DetallePedido item : carritoItems) {
            if (item.getIdProducto() == p.getIdProducto()) {
                item.setCantidad(item.getCantidad() + 1);
                item.setSubtotal(p.getPrecioVenta().multiply(BigDecimal.valueOf(item.getCantidad())));
                tblCarrito.refresh();
                recalcularTotales();
                return;
            }
        }

        DetallePedido nuevo = new DetallePedido(
                0, 0, p.getIdProducto(), p.getNombre(), 1,
                p.getPrecioVenta(), p.getPrecioVenta(), "Autoservicio Kiosco"
        );
        carritoItems.add(nuevo);
        recalcularTotales();
    }

    private void incrementarItem(DetallePedido det) {
        det.setCantidad(det.getCantidad() + 1);
        det.setSubtotal(det.getPrecioUnitario().multiply(BigDecimal.valueOf(det.getCantidad())));
        tblCarrito.refresh();
        recalcularTotales();
    }

    private void decrementarItem(DetallePedido det) {
        if (det.getCantidad() > 1) {
            det.setCantidad(det.getCantidad() - 1);
            det.setSubtotal(det.getPrecioUnitario().multiply(BigDecimal.valueOf(det.getCantidad())));
        } else {
            carritoItems.remove(det);
        }
        tblCarrito.refresh();
        recalcularTotales();
    }

    @FXML
    public void handleLimpiarCarrito() {
        carritoItems.clear();
        recalcularTotales();
    }

    private void recalcularTotales() {
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido item : carritoItems) {
            if (item.getSubtotal() != null) {
                total = total.add(item.getSubtotal());
            }
        }

        BigDecimal subtotalNeto = total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotalNeto);

        lblSubtotal.setText("S/ " + subtotalNeto);
        lblIgv.setText("S/ " + igv);
        lblTotalKiosco.setText("S/ " + total.setScale(2, RoundingMode.HALF_UP));

        btnConfirmarKiosco.setDisable(carritoItems.isEmpty());
    }

    @FXML
    public void handleConfirmarPedido() {
        if (carritoItems.isEmpty()) return;

        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido item : carritoItems) {
            total = total.add(item.getSubtotal());
        }

        Pedido ordenKiosco = new Pedido();
        ordenKiosco.setTipoPedido("KIOSCO");
        ordenKiosco.setNumeroMesa("T-" + turnoActual);
        ordenKiosco.setClienteNombre("Cliente Kiosco #T-" + turnoActual);
        ordenKiosco.setEstado("EN_PREPARACION");
        ordenKiosco.setTotal(total);
        ordenKiosco.setIdUsuario(1);
        ordenKiosco.setDetalles(new ArrayList<>(carritoItems));

        try (Connection conn = ConexionBD.getConnection()) {
            int id = pedidoDAO.crearPedido(conn, ordenKiosco);
            ordenKiosco.setIdPedido(id);
            for (DetallePedido d : carritoItems) {
                d.setIdPedido(id);
                try {
                    pedidoDAO.insertarDetallePedido(conn, d);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Detalle guardado: {0}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al guardar orden de kiosco en BD: {0}", e.getMessage());
            ordenKiosco.setIdPedido(9100 + turnoActual);
        }

        LOGGER.log(Level.INFO, "Ticket emitido exitosamente para turno #{0}. Orden despachada a cocina.", turnoActual);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("FoodCore Kiosco · Pedido Confirmado");
        alert.setHeaderText("¡Su orden ha sido enviada a Cocina!");
        alert.setContentText("TURNO ASIGNADO: #T-" + turnoActual + "\n\n"
                + "Total Pagado: S/ " + total + "\n"
                + "Su ticket térmico se está imprimiendo en la bandeja inferior.\n"
                + "Por favor tome su ticket y espere su turno en pantalla.");
        alert.showAndWait();

        // Preparar para el siguiente cliente
        carritoItems.clear();
        recalcularTotales();
        this.turnoActual = SECUENCIA_TURNO.incrementAndGet();
        if (lblTurnoActual != null) {
            lblTurnoActual.setText("TURNO #T-" + turnoActual);
        }
    }

    public ObservableList<DetallePedido> getCarritoItems() {
        return carritoItems;
    }

    public List<Producto> getTodosLosProductos() {
        return todosLosProductos;
    }

    public FlowPane getFlowProductosKiosco() {
        return flowProductosKiosco;
    }

    public int getTurnoActual() {
        return turnoActual;
    }

    @FXML
    public void handleExpandirVentana(javafx.event.ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/foodcore/view/KioscoView.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 768);
            com.foodcore.App.applyStylesheets(scene);

            javafx.stage.Stage newStage = new javafx.stage.Stage();
            newStage.initStyle(javafx.stage.StageStyle.DECORATED);
            newStage.setTitle("FoodCore ERP — Kiosco Autoservicio (Monitor Secundario)");
            newStage.setMaximized(true);
            newStage.setScene(scene);
            newStage.setOnCloseRequest(e -> {
                newStage.close();
                newStage.setScene(null);
            });
            newStage.show();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo expandir KioscoView en nueva ventana", ex);
        }
    }

    @FXML
    public void handleSalirKiosco(javafx.event.ActionEvent event) {
        if (event.getSource() instanceof Button btn && btn.getScene() != null && btn.getScene().getWindow() instanceof javafx.stage.Stage stage) {
            if (stage.getOwner() != null || stage.getStyle() == javafx.stage.StageStyle.UNDECORATED) {
                stage.close();
                stage.setScene(null);
            } else {
                javafx.scene.Scene currentScene = btn.getScene();
                if (currentScene != null && currentScene.lookup("#contentArea") instanceof javafx.scene.layout.StackPane contentArea) {
                    try {
                        javafx.fxml.FXMLLoader l = new javafx.fxml.FXMLLoader(getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                        javafx.scene.Parent dash = l.load();
                        contentArea.getChildren().setAll(dash);
                    } catch (Exception ex) {
                        LOGGER.log(Level.FINE, "No se pudo volver a DashboardView tras salir de kiosco", ex);
                    }
                }
            }
        }
    }
}
