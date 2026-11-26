package com.foodcore.controller;

import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.KardexConsultaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.PaginadorTablaHelper;
import com.foodcore.util.ReporteExportUIHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Kardex e Inventario de Insumos según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§10)
 * y SPEC-FOODCORE-v1.2 (§1.1): aplica {@link UnidadMedidaFormatter} para mostrar
 * {@code 120 Unds} y {@code 38.2 kg} sin sufijos {@code .0000}.
 */
public class KardexController {

    private static final Logger LOGGER = Logger.getLogger(KardexController.class.getName());

    public record FilaKardex(String fecha, String tipo, String insumo, String cantidad, String saldo, String usuario) {
    }

    @FXML private ComboBox<String> cmbInsumo;
    @FXML private DatePicker dpFechaDesde;
    @FXML private DatePicker dpFechaHasta;
    @FXML private Button btnFiltrar;
    @FXML private Button btnExportarPdf;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnExportarCsv;
    @FXML private Label lblResumenKardex;
    @FXML private Button btnAnteriorKardex;
    @FXML private Label lblPaginaKardex;
    @FXML private Button btnSiguienteKardex;

    @FXML private TableView<FilaKardex> tblKardex;
    @FXML private TableColumn<FilaKardex, String> colFecha;
    @FXML private TableColumn<FilaKardex, String> colTipo;
    @FXML private TableColumn<FilaKardex, String> colInsumo;
    @FXML private TableColumn<FilaKardex, String> colCantidad;
    @FXML private TableColumn<FilaKardex, String> colSaldo;
    @FXML private TableColumn<FilaKardex, String> colUsuario;

    private final ObservableList<FilaKardex> todosLosMovimientos = FXCollections.observableArrayList();
    private final ObservableList<FilaKardex> movimientosFiltrados = FXCollections.observableArrayList();
    private final ObservableList<FilaKardex> movimientosPaginaActual = FXCollections.observableArrayList();
    private final ExportacionContableService exportacionService = new ExportacionContableService();
    private final KardexConsultaService kardexConsultaService = new KardexConsultaService();
    private PaginadorTablaHelper<FilaKardex> paginadorKardex;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnFiltrar);
        AnimationUtils.attachTactileFeedback(btnExportarPdf);
        AnimationUtils.attachTactileFeedback(btnExportarExcel);
        AnimationUtils.attachTactileFeedback(btnExportarCsv);
        dpFechaDesde.setValue(LocalDate.now().minusDays(7));
        dpFechaHasta.setValue(LocalDate.now());
        paginadorKardex = new PaginadorTablaHelper<>(
                movimientosPaginaActual, lblPaginaKardex, btnAnteriorKardex, btnSiguienteKardex);

        configurarColumnas();
        cargarMovimientosDesdeBD();
    }

    private void configurarColumnas() {
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha()));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tipo()));
        colInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().insumo()));
        colCantidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().cantidad()));
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().saldo()));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().usuario()));

        colFecha.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-caption");
                }
            }
        });

        colTipo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    String upper = item.toUpperCase();
                    if (upper.contains("ENTRADA") || upper.contains("COMPRA")) {
                        pill.getStyleClass().add("badge-pill-success");
                    } else if (upper.contains("SALIDA") || upper.contains("VENTA")) {
                        pill.getStyleClass().add("badge-pill-accent");
                    } else if (upper.contains("MERMA")) {
                        pill.getStyleClass().add("badge-pill-danger");
                    } else {
                        pill.getStyleClass().add("badge-pill-warning");
                    }
                    setGraphic(pill);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        colInsumo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-body-strong");
                }
            }
        });

        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        });

        colSaldo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        });

        colUsuario.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-caption");
                }
            }
        });

        tblKardex.setItems(movimientosPaginaActual);
    }

    public void cargarMovimientosDesdeBD() {
        UiAsyncExecutor.ejecutarAsync(
            kardexConsultaService::consultarMovimientosKardex,
            res -> {
                cmbInsumo.setItems(FXCollections.observableArrayList(res.insumos()));
                cmbInsumo.getSelectionModel().selectFirst();
                List<FilaKardex> filas = new ArrayList<>();
                for (KardexConsultaService.FilaKardexData f : res.filas()) {
                    filas.add(new FilaKardex(f.fecha(), f.tipo(), f.insumo(), f.cantidad(), f.saldo(), f.usuario()));
                }
                todosLosMovimientos.setAll(filas);
                aplicarFiltroLocal();
            },
            e -> LOGGER.log(Level.FINE, "Error cargando Kardex", e)
        );
    }

    @FXML
    public void handleFiltrar(ActionEvent event) {
        aplicarFiltroLocal();
    }

    public void aplicarFiltroLocal() {
        String insumoSel = cmbInsumo != null ? cmbInsumo.getValue() : null;
        boolean filtrarPorInsumo = insumoSel != null && !insumoSel.equals("Todos los insumos");

        movimientosFiltrados.clear();
        for (FilaKardex f : todosLosMovimientos) {
            if (!filtrarPorInsumo || f.insumo().equalsIgnoreCase(insumoSel)) {
                movimientosFiltrados.add(f);
            }
        }
        if (paginadorKardex != null) {
            paginadorKardex.setDatos(movimientosFiltrados);
        } else {
            movimientosPaginaActual.setAll(movimientosFiltrados);
        }
        if (lblResumenKardex != null) {
            lblResumenKardex.setText("Mostrando " + movimientosFiltrados.size() + " movimientos");
        }
    }

    /**
     * Navega a la página anterior de movimientos Kardex.
     */
    @FXML
    public void handlePaginaAnterior() {
        if (paginadorKardex != null) {
            paginadorKardex.paginaAnterior();
        }
    }

    /**
     * Navega a la página siguiente de movimientos Kardex.
     */
    @FXML
    public void handlePaginaSiguiente() {
        if (paginadorKardex != null) {
            paginadorKardex.paginaSiguiente();
        }
    }

    /**
     * Reemplaza los movimientos en memoria y actualiza la paginación para pruebas unitarias.
     *
     * @param filas Lista completa de filas Kardex a paginar.
     */
    public void cargarMovimientosDirecto(List<FilaKardex> filas) {
        todosLosMovimientos.setAll(filas != null ? filas : List.of());
        aplicarFiltroLocal();
    }

    @FXML
    public void handleExportarPdf() {
        exportarKardex(btnExportarPdf, "Guardar Reporte de Kardex en PDF", ".pdf", FormatoExportacion.PDF);
    }

    @FXML
    public void handleExportarExcel() {
        exportarKardex(btnExportarExcel, "Guardar Reporte de Kardex en Excel", ".xlsx", FormatoExportacion.EXCEL);
    }

    @FXML
    public void handleExportarCsv() {
        exportarKardex(btnExportarCsv, "Guardar Reporte de Kardex en CSV", ".csv", FormatoExportacion.CSV);
    }

    private void exportarKardex(Button origen, String tituloDialogo, String ext, FormatoExportacion formato) {
        List<String> headers = List.of("Fecha", "Tipo", "Insumo", "Cantidad", "Saldo", "Usuario");
        List<List<String>> filas = new ArrayList<>();
        for (FilaKardex f : movimientosFiltrados) {
            filas.add(List.of(f.fecha(), f.tipo(), f.insumo(), f.cantidad(), f.saldo(), f.usuario()));
        }
        String sugerido = "KARDEX_INSUMOS_" + LocalDate.now() + ext;
        ReporteExportUIHelper.exportarTablaConSelector(
                origen != null ? origen : lblResumenKardex,
                lblResumenKardex,
                tituloDialogo,
                sugerido,
                "Kardex Valorizado de Insumos",
                "KARDEX",
                formato,
                headers,
                filas,
                exportacionService);
    }

    public ObservableList<FilaKardex> getMovimientosFiltrados() {
        return movimientosFiltrados;
    }
}
