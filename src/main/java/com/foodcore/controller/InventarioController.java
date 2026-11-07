package com.foodcore.controller;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.service.AuthService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UnidadMedidaFormatter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Inventario de Insumos y Compras según SPEC-FOODCORE-v1.2 (§1.1, §2.1):
 * Semáforo de alerta ({@code stock_actual <= stock_minimo} -> {@code REABASTECER}),
 * formateo con {@link UnidadMedidaFormatter}, creación de insumos y registro de compras
 * que impactan el Kardex con movimiento {@code ENTRADA_COMPRA}.
 */
public class InventarioController {

    private static final Logger LOGGER = Logger.getLogger(InventarioController.class.getName());

    @FXML private StackPane rootInventarioStack;
    @FXML private Label lblAlertasBadge;
    @FXML private TextField txtBuscarInsumo;
    @FXML private CheckBox chkSoloStockBajo;
    @FXML private Label lblResumenInventario;
    @FXML private Button btnRegistrarCompra;
    @FXML private Button btnNuevoInsumo;
    @FXML private Button btnEliminarInsumo;

    @FXML private TableView<Insumo> tblInventario;
    @FXML private TableColumn<Insumo, String> colCodigo;
    @FXML private TableColumn<Insumo, String> colNombre;
    @FXML private TableColumn<Insumo, String> colUnidad;
    @FXML private TableColumn<Insumo, String> colStockActual;
    @FXML private TableColumn<Insumo, String> colStockMinimo;
    @FXML private TableColumn<Insumo, String> colCostoUnitario;
    @FXML private TableColumn<Insumo, String> colEstado;

    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final TransactionManager tx = new JdbcTransactionManager();
    private final ObservableList<Insumo> todosLosInsumos = FXCollections.observableArrayList();
    private final ObservableList<Insumo> insumosFiltrados = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnRegistrarCompra);
        AnimationUtils.attachTactileFeedback(btnNuevoInsumo);

        configurarColumnas();
        if (txtBuscarInsumo != null) {
            txtBuscarInsumo.textProperty().addListener((obs, oldV, newV) -> aplicarFiltro());
        }
        cargarInventarioDesdeBD();
    }

    private void configurarColumnas() {
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCodigo() != null ? c.getValue().getCodigo() : ("INS-0" + c.getValue().getIdInsumo())
        ));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colUnidad.setCellValueFactory(c -> new SimpleStringProperty(
            UnidadMedidaFormatter.formatearSufijo(c.getValue().getUnidadMedida())
        ));
        colStockActual.setCellValueFactory(c -> new SimpleStringProperty(
            UnidadMedidaFormatter.formatear(c.getValue().getStockActualDisponible(), c.getValue().getUnidadMedida())
        ));
        colStockMinimo.setCellValueFactory(c -> new SimpleStringProperty(
            UnidadMedidaFormatter.formatear(c.getValue().getStockMinimo(), c.getValue().getUnidadMedida())
        ));
        colCostoUnitario.setCellValueFactory(c -> {
            BigDecimal costo = c.getValue().getCostoUnitario() != null
                ? c.getValue().getCostoUnitario().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new SimpleStringProperty("S/ " + costo);
        });
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().requiereReabastecimiento() ? "REABASTECER" : "ÓPTIMO"
        ));

        colCodigo.setCellFactory(col -> crearCeldaTexto("text-mono-sm"));
        colNombre.setCellFactory(col -> crearCeldaTexto("text-body-strong"));
        colUnidad.setCellFactory(col -> crearCeldaTexto("text-caption"));
        colStockActual.setCellFactory(col -> crearCeldaTexto("text-mono"));
        colStockMinimo.setCellFactory(col -> crearCeldaTexto("text-mono-sm"));
        colCostoUnitario.setCellFactory(col -> crearCeldaTexto("text-mono"));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    if ("REABASTECER".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-pill-danger");
                    } else {
                        badge.getStyleClass().add("badge-pill-success");
                    }
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        tblInventario.setItems(insumosFiltrados);
    }

    private TableCell<Insumo, String> crearCeldaTexto(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", styleClass);
                }
            }
        };
    }

    public void cargarInventarioDesdeBD() {
        new Thread(() -> {
            List<Insumo> lista = new ArrayList<>();
            try (Connection conn = ConexionBD.getConnection()) {
                lista = insumoDAO.listarTodos(conn);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Cargando insumos de respaldo", e);
            }
            final List<Insumo> resultado = lista;
            Platform.runLater(() -> {
                todosLosInsumos.setAll(resultado);
                aplicarFiltro();
            });
        }).start();
    }

    @FXML
    public void handleFiltrarInventario(ActionEvent event) {
        aplicarFiltro();
    }

    public void aplicarFiltro() {
        String q = (txtBuscarInsumo != null && txtBuscarInsumo.getText() != null)
            ? txtBuscarInsumo.getText().trim().toLowerCase() : "";
        boolean soloBajo = chkSoloStockBajo != null && chkSoloStockBajo.isSelected();

        insumosFiltrados.clear();
        int alertas = 0;
        for (Insumo ins : todosLosInsumos) {
            if (ins.requiereReabastecimiento()) {
                alertas++;
            }
            boolean matchTexto = q.isEmpty()
                || (ins.getNombre() != null && ins.getNombre().toLowerCase().contains(q))
                || (ins.getCodigo() != null && ins.getCodigo().toLowerCase().contains(q));
            boolean matchStock = !soloBajo || ins.requiereReabastecimiento();
            if (matchTexto && matchStock) {
                insumosFiltrados.add(ins);
            }
        }
        if (lblAlertasBadge != null) {
            lblAlertasBadge.setText(alertas + " POR REABASTECER");
            lblAlertasBadge.getStyleClass().setAll(alertas > 0 ? "badge-pill-danger" : "badge-pill-success");
        }
        if (lblResumenInventario != null) {
            lblResumenInventario.setText("Mostrando " + insumosFiltrados.size() + " insumos");
        }
    }

    /**
     * Crea un nuevo insumo de forma transaccional y, si tiene stock inicial > 0, registra su entrada en el Kardex (§2.1).
     */
    public Insumo crearNuevoInsumoTransaccional(String codigo,
                                                String nombre,
                                                String unidadMedida,
                                                BigDecimal stockInicial,
                                                BigDecimal stockMinimo,
                                                BigDecimal costoUnitario) throws Exception {
        String umCanon = UnidadMedidaFormatter.normalizarCodigo(unidadMedida);
        long idUsuario = AuthService.getUsuarioSesionActual() != null
            ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1L;

        return tx.executeChecked(conn -> {
            Insumo nuevo = new Insumo(
                0L,
                (codigo != null && !codigo.isBlank()) ? codigo.trim() : ("INS-" + (System.currentTimeMillis() % 100000)),
                nombre.trim(),
                umCanon,
                BigDecimal.ZERO,
                stockMinimo != null ? stockMinimo : BigDecimal.ZERO,
                costoUnitario != null ? costoUnitario : BigDecimal.ZERO
            );
            long id = insumoDAO.crear(conn, nuevo);
            if (stockInicial != null && stockInicial.compareTo(BigDecimal.ZERO) > 0) {
                insumoDAO.registrarCompraInsumo(conn, id, stockInicial, costoUnitario, idUsuario, "Inventario Inicial de Insumo");
            }
            return insumoDAO.obtenerPorId(conn, id);
        });
    }

    /**
     * Registra una compra de insumo actualizando el stock y generando automáticamente {@code ENTRADA_COMPRA} en el Kardex (§2.1).
     */
    public KardexMovimiento registrarCompraTransaccional(long idInsumo,
                                                         BigDecimal cantidadComprada,
                                                         BigDecimal costoUnitarioCompra,
                                                         String observacion) throws Exception {
        long idUsuario = AuthService.getUsuarioSesionActual() != null
            ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1L;
        return tx.executeChecked(conn ->
            insumoDAO.registrarCompraInsumo(conn, idInsumo, cantidadComprada, costoUnitarioCompra, idUsuario, observacion)
        );
    }

    @FXML
    public void handleAbrirModalNuevoInsumo(ActionEvent event) {
        if (rootInventarioStack == null) {
            return;
        }
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        VBox card = new VBox(12);
        card.setMaxWidth(440);
        card.setMaxHeight(460);
        card.getStyleClass().add("modal-dialog-surface");

        Label lblTitle = new Label("Nuevo Insumo de Almacén");
        lblTitle.getStyleClass().add("text-h1");

        TextField txtCodigo = new TextField();
        txtCodigo.setPromptText("Código SKU (ej. INS-010)");
        txtCodigo.getStyleClass().add("input-field");

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre del insumo (ej. Papa Amarilla Bastón)");
        txtNombre.getStyleClass().add("input-field");

        ComboBox<String> cmbUnidad = new ComboBox<>(FXCollections.observableArrayList("UNIDAD", "KG", "GR", "ML", "LT"));
        cmbUnidad.getSelectionModel().selectFirst();
        cmbUnidad.setMaxWidth(Double.MAX_VALUE);

        TextField txtStockIni = new TextField("0");
        txtStockIni.setPromptText("Stock inicial");
        txtStockIni.getStyleClass().add("input-field");

        TextField txtStockMin = new TextField("10");
        txtStockMin.setPromptText("Stock mínimo de alerta");
        txtStockMin.getStyleClass().add("input-field");

        TextField txtCosto = new TextField("1.00");
        txtCosto.setPromptText("Costo unitario (S/)");
        txtCosto.getStyleClass().add("input-field");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootInventarioStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Guardar Insumo");
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setOnAction(ev -> {
            try {
                crearNuevoInsumoTransaccional(
                    txtCodigo.getText(),
                    txtNombre.getText(),
                    cmbUnidad.getValue(),
                    new BigDecimal(txtStockIni.getText().trim()),
                    new BigDecimal(txtStockMin.getText().trim()),
                    new BigDecimal(txtCosto.getText().trim())
                );
                rootInventarioStack.getChildren().remove(backdrop);
                AnimationUtils.mostrarToast(rootInventarioStack, "Insumo creado correctamente.", false);
                cargarInventarioDesdeBD();
            } catch (Exception ex) {
                AnimationUtils.mostrarToast(rootInventarioStack, "Error: " + ex.getMessage(), true);
            }
        });

        HBox actions = new HBox(12, btnCancelar, btnGuardar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, txtCodigo, txtNombre, cmbUnidad, txtStockIni, txtStockMin, txtCosto, actions);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootInventarioStack.getChildren().add(backdrop);
    }

    @FXML
    public void handleAbrirModalCompra(ActionEvent event) {
        if (rootInventarioStack == null) {
            return;
        }
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        VBox card = new VBox(12);
        card.setMaxWidth(440);
        card.setMaxHeight(380);
        card.getStyleClass().add("modal-dialog-surface");

        Label lblTitle = new Label("Registrar Compra de Insumo");
        lblTitle.getStyleClass().add("text-h1");

        ComboBox<Insumo> cmbInsumo = new ComboBox<>(todosLosInsumos);
        cmbInsumo.setMaxWidth(Double.MAX_VALUE);
        Insumo sel = tblInventario.getSelectionModel().getSelectedItem();
        if (sel != null) {
            cmbInsumo.getSelectionModel().select(sel);
        } else if (!todosLosInsumos.isEmpty()) {
            cmbInsumo.getSelectionModel().selectFirst();
        }

        TextField txtCantidad = new TextField();
        txtCantidad.setPromptText("Cantidad comprada (ej. 25)");
        txtCantidad.getStyleClass().add("input-field");

        TextField txtCostoCompra = new TextField();
        txtCostoCompra.setPromptText("Costo unitario de compra S/ (ej. 4.20)");
        txtCostoCompra.getStyleClass().add("input-field");

        TextField txtFactura = new TextField("Compra Factura F001");
        txtFactura.setPromptText("Referencia / Factura");
        txtFactura.getStyleClass().add("input-field");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootInventarioStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Confirmar Compra");
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setOnAction(ev -> {
            try {
                Insumo ins = cmbInsumo.getValue();
                if (ins == null) {
                    return;
                }
                registrarCompraTransaccional(
                    ins.getIdInsumo(),
                    new BigDecimal(txtCantidad.getText().trim()),
                    new BigDecimal(txtCostoCompra.getText().trim()),
                    txtFactura.getText()
                );
                rootInventarioStack.getChildren().remove(backdrop);
                AnimationUtils.mostrarToast(rootInventarioStack, "Compra registrada en Kardex (ENTRADA_COMPRA).", false);
                cargarInventarioDesdeBD();
            } catch (Exception ex) {
                AnimationUtils.mostrarToast(rootInventarioStack, "Error: " + ex.getMessage(), true);
            }
        });

        HBox actions = new HBox(12, btnCancelar, btnGuardar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, cmbInsumo, txtCantidad, txtCostoCompra, txtFactura, actions);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootInventarioStack.getChildren().add(backdrop);
    }

    @FXML
    public void handleEliminarInsumo(ActionEvent event) {
        Insumo sel = tblInventario.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootInventarioStack, "Seleccione un insumo de la tabla para dar de baja.", true);
            return;
        }
        try (Connection conn = ConexionBD.getConnection()) {
            insumoDAO.eliminar(conn, sel.getIdInsumo());
            AnimationUtils.mostrarToast(rootInventarioStack, "Insumo '" + sel.getNombre() + "' dado de baja (Soft Delete activo=0).", false);
            cargarInventarioDesdeBD();
        } catch (Exception ex) {
            AnimationUtils.mostrarToast(rootInventarioStack, "Error al dar de baja insumo: " + ex.getMessage(), true);
        }
    }

    public TableView<Insumo> getTblInventario() { return tblInventario; }
    public Button getBtnEliminarInsumo() { return btnEliminarInsumo; }

        public ObservableList<Insumo> getInsumosFiltrados() {
        return insumosFiltrados;
    }

    public ObservableList<Insumo> getTodosLosInsumos() {
        return todosLosInsumos;
    }
}
