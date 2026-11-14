package com.foodcore.controller;

import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.ItemInventarioFisico;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.InventarioFisicoService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de la vista Toma de Inventario Físico (v5.2.0):
 * muestra el stock del sistema, permite editar {@code Stock Contado} por insumo,
 * calcula la diferencia en vivo y ejecuta el ajuste atómico {@code AJUSTE_INV}.
 */
public class InventarioFisicoController {

    private static final Logger LOGGER = Logger.getLogger(InventarioFisicoController.class.getName());

    @FXML private StackPane rootInventarioFisicoStack;
    @FXML private TextField txtBuscarInsumo;
    @FXML private TextField txtMotivoAjuste;
    @FXML private CheckBox chkSoloDiferencias;
    @FXML private Label lblResumenConteo;
    @FXML private Button btnRecargarConteo;
    @FXML private Button btnConfirmarAjuste;

    @FXML private TableView<ItemInventarioFisico> tblConteoFisico;
    @FXML private TableColumn<ItemInventarioFisico, String> colCodigo;
    @FXML private TableColumn<ItemInventarioFisico, String> colNombre;
    @FXML private TableColumn<ItemInventarioFisico, String> colUnidad;
    @FXML private TableColumn<ItemInventarioFisico, String> colStockSistema;
    @FXML private TableColumn<ItemInventarioFisico, String> colStockContado;
    @FXML private TableColumn<ItemInventarioFisico, String> colDiferencia;
    @FXML private TableColumn<ItemInventarioFisico, String> colValorDiferencia;

    private final InventarioFisicoService inventarioFisicoService = new InventarioFisicoService();
    private final List<ItemInventarioFisico> todosLosItems = new ArrayList<>();
    private final ObservableList<ItemInventarioFisico> itemsVisibles = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnRecargarConteo);
        AnimationUtils.attachTactileFeedback(btnConfirmarAjuste);
        configurarColumnas();
        if (txtBuscarInsumo != null) {
            txtBuscarInsumo.textProperty().addListener((obs, oldVal, newVal) -> aplicarFiltro());
        }
        cargarDatosAsync();
    }

    private void configurarColumnas() {
        colCodigo.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCodigo() != null ? d.getValue().getCodigo() : ""));
        colNombre.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getNombre() != null ? d.getValue().getNombre() : ""));
        colUnidad.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getUnidadMedida() != null ? d.getValue().getUnidadMedida() : ""));
        colStockSistema.setCellValueFactory(d -> new SimpleStringProperty(
                formatearStock(d.getValue().getStockSistema())));

        colStockContado.setCellValueFactory(d -> new SimpleStringProperty(
                formatearStock(d.getValue().getStockContado())));
        colStockContado.setCellFactory(col -> new TableCell<>() {
            private final TextField editor = new TextField();
            private boolean actualizando = false;

            {
                editor.getStyleClass().add("input-field");
                editor.setPrefWidth(130.0);
                editor.setOnAction(e -> confirmarValor());
                editor.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused) {
                        confirmarValor();
                    }
                });
            }

            private void confirmarValor() {
                if (actualizando || getTableRow() == null || getTableRow().getItem() == null) {
                    return;
                }
                ItemInventarioFisico rowItem = getTableRow().getItem();
                String raw = editor.getText() != null ? editor.getText().trim().replace(",", ".") : "";
                if (raw.isEmpty()) {
                    actualizando = true;
                    editor.setText(formatearStock(rowItem.getStockContado()));
                    actualizando = false;
                    return;
                }
                try {
                    BigDecimal val = new BigDecimal(raw);
                    if (val.compareTo(BigDecimal.ZERO) < 0) {
                        actualizando = true;
                        editor.setText(formatearStock(rowItem.getStockContado()));
                        actualizando = false;
                        return;
                    }
                    rowItem.setStockContado(val);
                    tblConteoFisico.refresh();
                    actualizarResumen();
                } catch (NumberFormatException ignored) {
                    actualizando = true;
                    editor.setText(formatearStock(rowItem.getStockContado()));
                    actualizando = false;
                }
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    actualizando = true;
                    editor.setText(formatearStock(getTableRow().getItem().getStockContado()));
                    actualizando = false;
                    setGraphic(editor);
                    setText(null);
                }
            }
        });

        colDiferencia.setCellValueFactory(d -> {
            BigDecimal dif = d.getValue().getDiferencia();
            String prefijo = dif.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            return new SimpleStringProperty(prefijo + formatearStock(dif));
        });
        colDiferencia.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                BigDecimal dif = getTableRow().getItem().getDiferencia();
                int cmp = dif.compareTo(BigDecimal.ZERO);
                if (cmp > 0) {
                    setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                } else if (cmp < 0) {
                    setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #6B7280;");
                }
            }
        });

        colValorDiferencia.setCellValueFactory(d -> {
            BigDecimal dif = d.getValue().getDiferencia();
            BigDecimal costo = d.getValue().getCostoUnitario() != null
                    ? d.getValue().getCostoUnitario() : BigDecimal.ZERO;
            BigDecimal impacto = dif.multiply(costo).setScale(2, RoundingMode.HALF_UP);
            String prefijo = impacto.compareTo(BigDecimal.ZERO) > 0 ? "+S/ " : "S/ ";
            return new SimpleStringProperty(prefijo + impacto.toPlainString());
        });

        tblConteoFisico.setItems(itemsVisibles);
    }

    public void cargarDatosAsync() {
        UiAsyncExecutor.ejecutarAsync(
            inventarioFisicoService::listarInsumosParaConteo,
            lista -> aplicarListaCargada(lista),
            ex -> {
                LOGGER.log(Level.WARNING, "Error cargando insumos para inventario físico", ex);
                AnimationUtils.mostrarToast(rootInventarioFisicoStack,
                        "No se pudo cargar inventario físico: " + ex.getMessage(), true);
            }
        );
    }

    public void aplicarListaCargada(List<ItemInventarioFisico> lista) {
        todosLosItems.clear();
        if (lista != null) {
            todosLosItems.addAll(lista);
        }
        aplicarFiltro();
    }

    /** Actualiza el stock contado de un insumo específico y recalcula la vista (también usado por pruebas FX). */
    public void actualizarConteoManual(long idInsumo, BigDecimal nuevoStockContado) {
        for (ItemInventarioFisico item : todosLosItems) {
            if (item.getIdInsumo() == idInsumo) {
                item.setStockContado(nuevoStockContado);
                break;
            }
        }
        aplicarFiltro();
        if (tblConteoFisico != null) {
            tblConteoFisico.refresh();
        }
    }

    @FXML
    public void handleRecargar(ActionEvent event) {
        cargarDatosAsync();
    }

    @FXML
    public void handleFiltrar(ActionEvent event) {
        aplicarFiltro();
    }

    private void aplicarFiltro() {
        String filtro = txtBuscarInsumo != null && txtBuscarInsumo.getText() != null
                ? txtBuscarInsumo.getText().trim().toLowerCase(Locale.ROOT) : "";
        boolean soloDif = chkSoloDiferencias != null && chkSoloDiferencias.isSelected();

        itemsVisibles.clear();
        for (ItemInventarioFisico item : todosLosItems) {
            if (soloDif && !item.tieneDiferencia()) {
                continue;
            }
            if (!filtro.isEmpty()) {
                String nom = item.getNombre() != null ? item.getNombre().toLowerCase(Locale.ROOT) : "";
                String cod = item.getCodigo() != null ? item.getCodigo().toLowerCase(Locale.ROOT) : "";
                if (!nom.contains(filtro) && !cod.contains(filtro)) {
                    continue;
                }
            }
            itemsVisibles.add(item);
        }
        actualizarResumen();
    }

    private void actualizarResumen() {
        long conDif = todosLosItems.stream().filter(ItemInventarioFisico::tieneDiferencia).count();
        if (lblResumenConteo != null) {
            lblResumenConteo.setText(todosLosItems.size() + " insumos · " + conDif + " con diferencia");
        }
    }

    @FXML
    public void handleConfirmarAjustes(ActionEvent event) {
        Usuario usr = AuthService.getUsuarioSesionActual();
        int idUsr = usr != null ? usr.getIdUsuario() : 1;
        String motivo = txtMotivoAjuste != null ? txtMotivoAjuste.getText() : "Toma de Inventario Físico";
        List<ItemInventarioFisico> snapshot = new ArrayList<>(todosLosItems);

        if (btnConfirmarAjuste != null) {
            btnConfirmarAjuste.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> inventarioFisicoService.aplicarAjustesInventarioFisico(snapshot, idUsr, motivo),
            res -> {
                if (btnConfirmarAjuste != null) {
                    btnConfirmarAjuste.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootInventarioFisicoStack,
                        "Ajuste aplicado (" + res.insumosAjustados() + " insumo(s) actualizado(s) en Kárdex).",
                        false);
                cargarDatosAsync();
            },
            ex -> {
                if (btnConfirmarAjuste != null) {
                    btnConfirmarAjuste.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootInventarioFisicoStack,
                        ex != null ? ex.getMessage() : "Error al aplicar ajuste de inventario.",
                        true);
            }
        );
    }

    public ObservableList<ItemInventarioFisico> getItemsVisibles() {
        return itemsVisibles;
    }

    public List<ItemInventarioFisico> getTodosLosItems() {
        return List.copyOf(todosLosItems);
    }

    private String formatearStock(BigDecimal val) {
        BigDecimal safe = val != null ? val : BigDecimal.ZERO;
        return safe.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO).toPlainString();
    }
}
