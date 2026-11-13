package com.foodcore.controller;

import com.foodcore.model.EstadoResultadosReporte;
import com.foodcore.model.EstadoResultadosReporte.GastoPorCategoria;
import com.foodcore.model.EstadoResultadosReporte.LineaPnl;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasBiService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de la vista Estado de Resultados (P&amp;L) (v4.9.0 Fase 1.2).
 */
public class EstadoResultadosController {

    private static final Logger LOGGER = Logger.getLogger(EstadoResultadosController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private StackPane rootEstadoResultadosStack;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Button btnConsultarPnl;
    @FXML private Button btnRangoHoy;
    @FXML private Button btnRangoMes;
    @FXML private Button btnRangoMesAnterior;
    @FXML private Button btnExportarPnlPdf;
    @FXML private Button btnExportarPnlExcel;
    @FXML private Label lblEstadoPnl;

    @FXML private Label lblPnlIngresosBrutos;
    @FXML private Label lblPnlVentasDetalle;
    @FXML private Label lblPnlCostoVentas;
    @FXML private Label lblPnlCostoDetalle;
    @FXML private Label lblPnlUtilidadBruta;
    @FXML private Label lblPnlMargenBruto;
    @FXML private Label lblPnlUtilidadNeta;
    @FXML private Label lblPnlGastosOperativosSub;
    @FXML private Label lblPnlPeriodoBadge;
    @FXML private Label lblTotalGastosPanel;

    @FXML private TableView<LineaPnl> tblEstadoResultados;
    @FXML private TableColumn<LineaPnl, String> colPnlCodigo;
    @FXML private TableColumn<LineaPnl, String> colPnlConcepto;
    @FXML private TableColumn<LineaPnl, String> colPnlMonto;
    @FXML private TableColumn<LineaPnl, String> colPnlPct;

    @FXML private TableView<GastoPorCategoria> tblGastosCategoria;
    @FXML private TableColumn<GastoPorCategoria, String> colGastoCategoria;
    @FXML private TableColumn<GastoPorCategoria, String> colGastoOps;
    @FXML private TableColumn<GastoPorCategoria, String> colGastoMonto;
    @FXML private TableColumn<GastoPorCategoria, String> colGastoPct;

    private final FinanzasBiService finanzasBiService;
    private final ExportacionContableService exportacionService;
    private final ObservableList<LineaPnl> lineasPnl = FXCollections.observableArrayList();
    private final ObservableList<GastoPorCategoria> filasGastos = FXCollections.observableArrayList();
    private EstadoResultadosReporte ultimoReporte;

    public EstadoResultadosController() {
        this(new FinanzasBiService(), new ExportacionContableService());
    }

    public EstadoResultadosController(
            FinanzasBiService finanzasBiService,
            ExportacionContableService exportacionService) {
        this.finanzasBiService = finanzasBiService;
        this.exportacionService = exportacionService;
    }

    @FXML
    public void initialize() {
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy);

        configurarTablas();
        AnimationUtils.attachTactileFeedback(btnConsultarPnl);
        AnimationUtils.attachTactileFeedback(btnExportarPnlPdf);
        AnimationUtils.attachTactileFeedback(btnExportarPnlExcel);
        cargarPnlAsync();
    }

    private void configurarTablas() {
        colPnlCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().codigo()));
        colPnlConcepto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().concepto()));
        colPnlMonto.setCellValueFactory(c -> new SimpleStringProperty(
                formatearSoles(c.getValue().monto(), c.getValue().tipoLinea())));
        colPnlPct.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().porcentajeSobreIngresos().setScale(1, RoundingMode.HALF_UP) + "%"));

        colPnlConcepto.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    LineaPnl fila = getTableRow() != null ? getTableRow().getItem() : null;
                    boolean destacado = fila != null
                            && ("SUBTOTAL".equals(fila.tipoLinea()) || "NETO".equals(fila.tipoLinea()));
                    setStyle(destacado ? "-fx-font-weight: bold;" : "");
                }
            }
        });
        colPnlMonto.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-mono");
                    LineaPnl fila = getTableRow() != null ? getTableRow().getItem() : null;
                    if (fila != null && "NETO".equals(fila.tipoLinea())) {
                        String color = fila.monto().compareTo(BigDecimal.ZERO) >= 0 ? "#03543F" : "#9B1C1C";
                        setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
                    } else if (fila != null && "SUBTOTAL".equals(fila.tipoLinea())) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #1F2937;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        colPnlPct.setCellFactory(col -> crearCeldaMono());
        tblEstadoResultados.setItems(lineasPnl);

        colGastoCategoria.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().categoria()));
        colGastoOps.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().operaciones())));
        colGastoMonto.setCellValueFactory(c -> new SimpleStringProperty(
                "S/ " + c.getValue().monto().setScale(2, RoundingMode.HALF_UP)));
        colGastoPct.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().participacionGastoPct().setScale(1, RoundingMode.HALF_UP) + "%"));
        colGastoOps.setCellFactory(col -> crearCeldaMono());
        colGastoMonto.setCellFactory(col -> crearCeldaMono());
        colGastoPct.setCellFactory(col -> crearCeldaMono());
        tblGastosCategoria.setItems(filasGastos);
    }

    private <T> TableCell<T, String> crearCeldaMono() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        };
    }

    private String formatearSoles(BigDecimal monto, String tipoLinea) {
        BigDecimal norm = monto != null ? monto.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        if ("COSTO".equals(tipoLinea) || "GASTO".equals(tipoLinea)) {
            return "-S/ " + norm.abs();
        }
        return "S/ " + norm;
    }

    @FXML
    public void handleConsultarPnl() {
        cargarPnlAsync();
    }

    @FXML
    public void handleRangoHoy() {
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        dpDesde.setValue(hoy);
        dpHasta.setValue(hoy);
        cargarPnlAsync();
    }

    @FXML
    public void handleRangoEsteMes() {
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy);
        cargarPnlAsync();
    }

    @FXML
    public void handleRangoMesAnterior() {
        YearMonth prev = YearMonth.now(ZONA_LIMA).minusMonths(1);
        dpDesde.setValue(prev.atDay(1));
        dpHasta.setValue(prev.atEndOfMonth());
        cargarPnlAsync();
    }

    public void cargarPnlAsync() {
        LocalDate desde = dpDesde.getValue() != null ? dpDesde.getValue() : LocalDate.now(ZONA_LIMA).withDayOfMonth(1);
        LocalDate hasta = dpHasta.getValue() != null ? dpHasta.getValue() : LocalDate.now(ZONA_LIMA);
        lblEstadoPnl.setText("Calculando P&L...");
        UiAsyncExecutor.ejecutarAsync(
                () -> finanzasBiService.generarEstadoResultados(desde, hasta),
                this::aplicarReportePnl,
                ex -> {
                    LOGGER.log(Level.WARNING, "Error generando Estado de Resultados (P&L)", ex);
                    lblEstadoPnl.setText("Error: " + ex.getMessage());
                    if (rootEstadoResultadosStack != null) {
                        AnimationUtils.mostrarToast(rootEstadoResultadosStack, ex.getMessage(), true);
                    }
                }
        );
    }

    /**
     * Ejecuta la consulta y renderizado de P&amp;L de forma síncrona (útil en pruebas de UI/integración).
     */
    public void cargarPnlSincrono() throws Exception {
        LocalDate desde = dpDesde.getValue() != null ? dpDesde.getValue() : LocalDate.now(ZONA_LIMA).withDayOfMonth(1);
        LocalDate hasta = dpHasta.getValue() != null ? dpHasta.getValue() : LocalDate.now(ZONA_LIMA);
        EstadoResultadosReporte rep = finanzasBiService.generarEstadoResultados(desde, hasta);
        aplicarReportePnl(rep);
    }

    private void aplicarReportePnl(EstadoResultadosReporte rep) {
        this.ultimoReporte = rep;

        lblPnlIngresosBrutos.setText("S/ " + rep.ingresosBrutos());
        lblPnlVentasDetalle.setText(rep.cantidadVentas() + " ventas completadas · IGV S/ " + rep.igvVentas());
        lblPnlCostoVentas.setText("S/ " + rep.costoVentas());
        lblPnlCostoDetalle.setText("Base gravada: S/ " + rep.subtotalGravado());
        lblPnlUtilidadBruta.setText("S/ " + rep.utilidadBruta());
        lblPnlMargenBruto.setText("Margen bruto: " + rep.margenBrutoPct() + "%");

        lblPnlUtilidadNeta.setText("S/ " + rep.utilidadNeta());
        if (rep.utilidadNeta().compareTo(BigDecimal.ZERO) < 0) {
            lblPnlUtilidadNeta.setStyle("-fx-text-fill: #DC2626;");
        } else {
            lblPnlUtilidadNeta.setStyle("");
        }
        lblPnlGastosOperativosSub.setText(
                "Gastos op.: S/ " + rep.gastosOperativos() + " · Margen neto: " + rep.margenNetoPct() + "%"
        );
        lblTotalGastosPanel.setText("S/ " + rep.gastosOperativos());
        lblPnlPeriodoBadge.setText(rep.desde().format(FMT_FECHA) + " - " + rep.hasta().format(FMT_FECHA));

        lineasPnl.setAll(rep.lineasContables());
        filasGastos.setAll(rep.gastosPorCategoria());
        lblEstadoPnl.setText("✓ P&L actualizado (" + rep.desde() + " al " + rep.hasta() + ")");
    }

    @FXML
    public void handleExportarPdf() {
        exportarConSelector(
                btnExportarPnlPdf,
                "Exportar Estado de Resultados (PDF)",
                "ESTADO_RESULTADOS_PNL.pdf",
                ExportFileChooserHelper.FormatoExportacion.PDF,
                (headers, filas, doc) -> exportacionService.exportarReporteGenericoPdf(
                        "Estado de Resultados (P&L) " + (ultimoReporte != null
                                ? ultimoReporte.desde() + " al " + ultimoReporte.hasta() : ""),
                        headers, filas, doc),
                "✓ PDF exportado: "
        );
    }

    @FXML
    public void handleExportarExcel() {
        exportarConSelector(
                btnExportarPnlExcel,
                "Exportar Estado de Resultados (Excel)",
                "ESTADO_RESULTADOS_PNL.xlsx",
                ExportFileChooserHelper.FormatoExportacion.EXCEL,
                (headers, filas, doc) -> exportacionService.exportarReporteGenericoExcel("PNL", headers, filas, doc),
                "✓ Excel exportado: "
        );
    }

    @FunctionalInterface
    private interface ExportAction {
        void run(List<String> headers, List<List<String>> filas, File destino) throws Exception;
    }

    private void exportarConSelector(
            Button origenBtn,
            String titulo,
            String sugerido,
            ExportFileChooserHelper.FormatoExportacion formato,
            ExportAction action,
            String prefijoExito) {
        Optional<File> elegido = ExportFileChooserHelper.elegirDestinoExportacion(
                origenBtn != null ? origenBtn : lblEstadoPnl, titulo, sugerido, formato
        );
        if (elegido.isEmpty()) {
            lblEstadoPnl.setText("Exportación cancelada.");
            return;
        }
        File destino = elegido.get();
        List<String> headers = List.of("Código", "Concepto Contable", "Importe (S/)", "% s/ Ingresos");
        List<List<String>> filas = new ArrayList<>();
        for (LineaPnl l : lineasPnl) {
            filas.add(List.of(
                    l.codigo(),
                    l.concepto(),
                    formatearSoles(l.monto(), l.tipoLinea()),
                    l.porcentajeSobreIngresos() + "%"
            ));
        }
        if (!filasGastos.isEmpty()) {
            filas.add(List.of("", "--- DESGLOSE GASTOS OPERATIVOS ---", "", ""));
            for (GastoPorCategoria g : filasGastos) {
                filas.add(List.of("GASTO", g.categoria() + " (" + g.operaciones() + " movs)",
                        "S/ " + g.monto(), g.participacionGastoPct() + "% gasto"));
            }
        }
        lblEstadoPnl.setText("Exportando...");
        UiAsyncExecutor.ejecutarAsync(
                () -> {
                    if (destino.getParentFile() != null) {
                        Files.createDirectories(destino.getParentFile().toPath());
                    }
                    action.run(headers, filas, destino);
                    return destino.getName();
                },
                nombre -> lblEstadoPnl.setText(prefijoExito + nombre),
                ex -> lblEstadoPnl.setText("Error exportando: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    public EstadoResultadosReporte getUltimoReporte() {
        return ultimoReporte;
    }

    public ObservableList<LineaPnl> getLineasPnl() {
        return lineasPnl;
    }

    public ObservableList<GastoPorCategoria> getFilasGastos() {
        return filasGastos;
    }
}
