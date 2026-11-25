package com.foodcore.controller;


import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.service.ImportacionMasivaService;
import com.foodcore.service.InventarioGestionService;

import com.foodcore.util.AnimationUtils;

import com.foodcore.util.DialogService;
import com.foodcore.util.ImportacionUIHelper;
import com.foodcore.util.UiAsyncExecutor;
import com.foodcore.util.UnidadMedidaFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
    @FXML private Button btnDescargarPlantillaInsumos;
    @FXML private Button btnImportarInsumos;
    @FXML private ProgressIndicator pbImportacionInsumos;
    @FXML private Label lblResumenImportacionInsumos;

    @FXML private TableView<Insumo> tblInventario;
    @FXML private TableColumn<Insumo, String> colCodigo;
    @FXML private TableColumn<Insumo, String> colNombre;
    @FXML private TableColumn<Insumo, String> colUnidad;
    @FXML private TableColumn<Insumo, String> colStockActual;
    @FXML private TableColumn<Insumo, String> colStockMinimo;
    @FXML private TableColumn<Insumo, String> colCostoUnitario;
    @FXML private TableColumn<Insumo, String> colEstado;

    private final InventarioGestionService inventarioGestionService = new InventarioGestionService();
    private final ImportacionMasivaService importacionMasivaService = new ImportacionMasivaService();

    private final ObservableList<Insumo> todosLosInsumos = FXCollections.observableArrayList();
    private final ObservableList<Insumo> insumosFiltrados = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnRegistrarCompra);
        AnimationUtils.attachTactileFeedback(btnNuevoInsumo);
        AnimationUtils.attachTactileFeedback(btnDescargarPlantillaInsumos);
        AnimationUtils.attachTactileFeedback(btnImportarInsumos);

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
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try {
                    return inventarioGestionService.listarTodosInsumos();
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Cargando insumos de respaldo", e);
                }
                return new ArrayList<Insumo>();
            },
            resultado -> {
                todosLosInsumos.setAll(resultado);
                aplicarFiltro();
            },
            e -> LOGGER.log(Level.FINE, "Cargando insumos de respaldo", e)
        );
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
        return inventarioGestionService.crearNuevoInsumoTransaccional(
                codigo, nombre, unidadMedida, stockInicial, stockMinimo, costoUnitario);
    }

    /**
     * Registra una compra de insumo actualizando el stock y generando automáticamente {@code ENTRADA_COMPRA} en el Kardex (§2.1).
     */
    public KardexMovimiento registrarCompraTransaccional(long idInsumo,
                                                         BigDecimal cantidadComprada,
                                                         BigDecimal costoUnitarioCompra,
                                                         String observacion) throws Exception {
        return inventarioGestionService.registrarCompraTransaccional(
                idInsumo, cantidadComprada, costoUnitarioCompra, observacion);
    }

    @FXML
    public void handleAbrirModalNuevoInsumo(ActionEvent event) {
        if (rootInventarioStack == null) {
            return;
        }
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        TextField txtCodigo = new TextField();
        txtCodigo.setPromptText("Ej. INS-010");

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Ej. Papa Amarilla Bastón");

        ComboBox<String> cmbUnidad = new ComboBox<>(FXCollections.observableArrayList("UNIDAD", "KG", "GR", "ML", "LT"));
        cmbUnidad.getSelectionModel().selectFirst();

        TextField txtStockIni = new TextField("0");
        txtStockIni.setPromptText("Stock inicial");

        TextField txtStockMin = new TextField("10");
        txtStockMin.setPromptText("Stock mínimo de alerta");

        TextField txtCosto = new TextField("1.00");
        txtCosto.setPromptText("Costo unitario (S/)");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setOnAction(ev -> rootInventarioStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Guardar Insumo");
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

        HBox footer = DialogService.construirFooterModal(btnCancelar, btnGuardar);
        VBox card = DialogService.construirModalFormulario(
                500.0,
                "Nuevo Insumo de Almacén",
                "Registre el insumo base con unidad de medida y alertas de stock mínimo.",
                List.of(
                        DialogService.construirFilaDobleModal(
                                DialogService.construirCampoModal("Código SKU", txtCodigo),
                                DialogService.construirCampoModal("Unidad de Medida", cmbUnidad)),
                        DialogService.construirCampoModal("Nombre del Insumo", txtNombre),
                        DialogService.construirFilaDobleModal(
                                DialogService.construirCampoModal("Stock Inicial", txtStockIni),
                                DialogService.construirCampoModal("Stock Mínimo", txtStockMin)),
                        DialogService.construirCampoModal("Costo Unitario (S/)", txtCosto)),
                footer);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootInventarioStack.getChildren().add(backdrop);
        rootInventarioStack.applyCss();
        rootInventarioStack.layout();
    }

    @FXML
    public void handleAbrirModalCompra(ActionEvent event) {
        if (rootInventarioStack == null) {
            return;
        }
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        ComboBox<Insumo> cmbInsumo = new ComboBox<>(todosLosInsumos);
        Insumo sel = tblInventario.getSelectionModel().getSelectedItem();
        if (sel != null) {
            cmbInsumo.getSelectionModel().select(sel);
        } else if (!todosLosInsumos.isEmpty()) {
            cmbInsumo.getSelectionModel().selectFirst();
        }

        TextField txtCantidad = new TextField();
        txtCantidad.setPromptText("Ej. 25");

        TextField txtCostoCompra = new TextField();
        txtCostoCompra.setPromptText("Ej. 4.20");

        TextField txtFactura = new TextField("Compra Factura F001");
        txtFactura.setPromptText("Referencia / Factura");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setOnAction(ev -> rootInventarioStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Confirmar Compra");
        btnGuardar.setOnAction(ev -> {
            Insumo ins = cmbInsumo.getValue();
            if (ins == null) {
                return;
            }
            final BigDecimal cantidad;
            final BigDecimal costo;
            try {
                cantidad = new BigDecimal(txtCantidad.getText().trim());
                costo = new BigDecimal(txtCostoCompra.getText().trim());
            } catch (RuntimeException ex) {
                AnimationUtils.mostrarToast(rootInventarioStack, "Error: Ingrese valores numéricos válidos.", true);
                return;
            }
            String factura = txtFactura.getText();
            long idInsumo = ins.getIdInsumo();
            btnGuardar.setDisable(true);
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> registrarCompraTransaccional(idInsumo, cantidad, costo, factura),
                () -> {
                    btnGuardar.setDisable(false);
                    rootInventarioStack.getChildren().remove(backdrop);
                    AnimationUtils.mostrarToast(rootInventarioStack, "Compra registrada en Kardex (ENTRADA_COMPRA).", false);
                    cargarInventarioDesdeBD();
                },
                ex -> {
                    btnGuardar.setDisable(false);
                    AnimationUtils.mostrarToast(
                            rootInventarioStack,
                            "Error: " + (ex != null ? ex.getMessage() : "desconocido"),
                            true);
                }
            );
        });

        HBox footer = DialogService.construirFooterModal(btnCancelar, btnGuardar);
        VBox card = DialogService.construirModalFormulario(
                500.0,
                "Registrar Compra de Insumo",
                "Ingreso de stock al almacén con actualización en Kardex.",
                List.of(
                        DialogService.construirCampoModal("Insumo", cmbInsumo),
                        DialogService.construirFilaDobleModal(
                                DialogService.construirCampoModal("Cantidad Comprada", txtCantidad),
                                DialogService.construirCampoModal("Costo Unitario (S/)", txtCostoCompra)),
                        DialogService.construirCampoModal("Referencia / Factura", txtFactura)),
                footer);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootInventarioStack.getChildren().add(backdrop);
        rootInventarioStack.applyCss();
        rootInventarioStack.layout();
    }

    @FXML
    public void handleEliminarInsumo(ActionEvent event) {
        Insumo sel = tblInventario.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootInventarioStack, "Seleccione un insumo de la tabla para dar de baja.", true);
            return;
        }
        long idInsumo = sel.getIdInsumo();
        String nombreInsumo = sel.getNombre();
        if (btnEliminarInsumo != null) {
            btnEliminarInsumo.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                inventarioGestionService.eliminarInsumo(idInsumo);
            },
            () -> {
                if (btnEliminarInsumo != null) {
                    btnEliminarInsumo.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootInventarioStack, "Insumo '" + nombreInsumo + "' dado de baja (Soft Delete activo=0).", false);
                cargarInventarioDesdeBD();
            },
            ex -> {
                if (btnEliminarInsumo != null) {
                    btnEliminarInsumo.setDisable(false);
                }
                AnimationUtils.mostrarToast(
                        rootInventarioStack,
                        "Error al dar de baja insumo: " + (ex != null ? ex.getMessage() : "desconocido"),
                        true);
            }
        );
    }

    public TableView<Insumo> getTblInventario() { return tblInventario; }
    public Button getBtnEliminarInsumo() { return btnEliminarInsumo; }

        public ObservableList<Insumo> getInsumosFiltrados() {
        return insumosFiltrados;
    }

    public ObservableList<Insumo> getTodosLosInsumos() {
        return todosLosInsumos;
    }

    @FXML
    public void handleDescargarPlantillaExcel(ActionEvent event) {
        ImportacionUIHelper.descargarPlantilla(
                btnDescargarPlantillaInsumos != null ? btnDescargarPlantillaInsumos : rootInventarioStack,
                rootInventarioStack,
                lblResumenImportacionInsumos,
                "Guardar Plantilla Excel de Insumos",
                "plantilla_insumos.xlsx",
                importacionMasivaService::exportarPlantillaInsumos);
    }

    @FXML
    public void handleImportarDesdeExcel(ActionEvent event) {
        ImportacionUIHelper.ejecutarImportacion(
                btnImportarInsumos != null ? btnImportarInsumos : rootInventarioStack,
                rootInventarioStack,
                btnImportarInsumos,
                pbImportacionInsumos,
                lblResumenImportacionInsumos,
                "Importar Catálogo de Insumos (Excel / CSV)",
                importacionMasivaService::importarInsumos,
                this::cargarInventarioDesdeBD);
    }
}
