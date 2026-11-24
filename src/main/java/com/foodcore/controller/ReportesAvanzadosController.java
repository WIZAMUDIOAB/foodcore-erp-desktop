package com.foodcore.controller;

import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.ReporteCohortes;
import com.foodcore.model.ReporteCohortes.CohorteMensualFila;
import com.foodcore.model.ReporteCohortes.SegmentoRetencionFila;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasBiService;
import com.foodcore.controller.reportes.DefaultReportesAvanzadosPresenter;
import com.foodcore.controller.reportes.DefaultReporteExportCoordinator;
import com.foodcore.controller.reportes.ReporteChartUiRenderer;
import com.foodcore.controller.reportes.ReporteExportCoordinator;
import com.foodcore.controller.reportes.ReportesAvanzadosPresenter;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.FormatoBiUiHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReportesAvanzadosController {

    public static final String REPORTE_MEDIO_PAGO = "Ventas por Medio de Pago";
    public static final String REPORTE_MARGENES_CATEGORIA = "Márgenes por Categoría";
    public static final String REPORTE_COHORTES_CLIENTES = "Cohortes y Retención de Clientes";

    private static final Logger LOGGER = Logger.getLogger(ReportesAvanzadosController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String[] PALETA_SAAS = {
        "#EA580C", "#D97706", "#DC2626", "#A39D94"
    };

    @FXML private ComboBox<String> cbCategoriaReporte;
    @FXML private DatePicker dpInicio;
    @FXML private DatePicker dpFin;
    @FXML private Button btnConsultar;
    @FXML private Button btnExportarPdf;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnExportarCsv;
    @FXML private Button btnModoAnalitica;
    @FXML private Button btnModoMargenes;
    @FXML private Button btnModoCohortes;
    @FXML private Label lblEstadoReporte;

    @FXML private HBox panelComparativasReportes;
    @FXML private Label lblRepHoyVsAyerBadge;
    @FXML private Label lblRepHoyVsAyerValor;
    @FXML private Label lblRepHoyVsAyerSub;
    @FXML private Label lblRepMesVsAntBadge;
    @FXML private Label lblRepMesVsAntValor;
    @FXML private Label lblRepMesVsAntSub;

    @FXML private Label lblKpiMontoTotal;
    @FXML private Label lblKpiSubTotal;
    @FXML private Label lblKpiPromedio;
    @FXML private Label lblKpiTopItem;
    @FXML private Label lblKpiTopValor;
    @FXML private Label lblTituloTabla;

    @FXML private HBox panelTablaGrafico;
    @FXML private TableView<FilaReporteAvanzado> tblReporte;
    @FXML private TableColumn<FilaReporteAvanzado, String> col1;
    @FXML private TableColumn<FilaReporteAvanzado, String> col2;
    @FXML private TableColumn<FilaReporteAvanzado, String> col3;
    @FXML private TableColumn<FilaReporteAvanzado, String> col4;
    @FXML private TableColumn<FilaReporteAvanzado, String> col5;

    @FXML private PieChart pieChartDistribucion;

    @FXML private VBox panelCohortes;
    @FXML private Label lblCohortesAviso;
    @FXML private TableView<SegmentoRetencionFila> tblResumenCohortes;
    @FXML private TableColumn<SegmentoRetencionFila, String> colSegNombre;
    @FXML private TableColumn<SegmentoRetencionFila, String> colSegCantidad;
    @FXML private TableColumn<SegmentoRetencionFila, String> colSegPorcentaje;
    @FXML private TableView<CohorteMensualFila> tblMatrizCohortes;
    @FXML private TableColumn<CohorteMensualFila, String> colCohorteMes;
    @FXML private TableColumn<CohorteMensualFila, String> colCohorteTamano;
    @FXML private TableColumn<CohorteMensualFila, String> colCohorteM1;
    @FXML private TableColumn<CohorteMensualFila, String> colCohorteM2;
    @FXML private TableColumn<CohorteMensualFila, String> colCohorteM3;
    @FXML private TableColumn<CohorteMensualFila, String> colCohorteM4;


    private final ReportesAvanzadosPresenter presenter;
    private final ReporteExportCoordinator exportCoordinator;
    private final ReporteChartUiRenderer chartUiRenderer;
    private final ObservableList<FilaReporteAvanzado> listaFilas = FXCollections.observableArrayList();
    private final ObservableList<SegmentoRetencionFila> listaResumenCohortes = FXCollections.observableArrayList();
    private final ObservableList<CohorteMensualFila> listaMatrizCohortes = FXCollections.observableArrayList();

    public ReportesAvanzadosController() {
        this(new ExportacionContableService(), new FinanzasBiService());
    }

    public ReportesAvanzadosController(
            ExportacionContableService exportacionService,
            FinanzasBiService finanzasBiService) {
        this(new DefaultReportesAvanzadosPresenter(finanzasBiService, new com.foodcore.service.ReporteService()),
                new DefaultReporteExportCoordinator(exportacionService), new ReporteChartUiRenderer());
    }

    public ReportesAvanzadosController(ReportesAvanzadosPresenter presenter,
            ReporteExportCoordinator exportCoordinator, ReporteChartUiRenderer chartUiRenderer) {
        this.presenter = presenter;
        this.exportCoordinator = exportCoordinator;
        this.chartUiRenderer = chartUiRenderer;
    }

    @FXML
    public void initialize() {
        if (pieChartDistribucion != null) {
            pieChartDistribucion.setAnimated(false);
            pieChartDistribucion.setLabelsVisible(false);
            pieChartDistribucion.setLegendSide(Side.BOTTOM);
            pieChartDistribucion.needsLayoutProperty().addListener((obs, oldVal, needsLayout) -> {
                if (!needsLayout) {
                    aplicarEstilosDonut();
                }
            });
        }
        configurarColumnas();
        configurarTablasCohortes();
        configurarCategorias();
        cargarAnalisisAsync();
    }

    private void configurarColumnas() {
        col1.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().c1));
        col2.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().c2));
        col3.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().c3));
        col4.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().c4));
        col5.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().c5));
        tblReporte.setItems(listaFilas);
    }

    private void configurarTablasCohortes() {
        if (tblResumenCohortes != null) {
            colSegNombre.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().segmento()));
            colSegCantidad.setCellValueFactory(f -> new SimpleStringProperty(String.valueOf(f.getValue().cantidad())));
            colSegPorcentaje.setCellValueFactory(f -> new SimpleStringProperty(
                    FormatoBiUiHelper.formatearPorcentajeCohorte(f.getValue().porcentaje())));
            tblResumenCohortes.setItems(listaResumenCohortes);
        }
        if (tblMatrizCohortes != null) {
            colCohorteMes.setCellValueFactory(f -> new SimpleStringProperty(f.getValue().etiquetaCohorte()));
            colCohorteTamano.setCellValueFactory(
                    f -> new SimpleStringProperty(f.getValue().tamanoCohorte() + " cli."));
            colCohorteM1.setCellValueFactory(f -> new SimpleStringProperty(
                    FormatoBiUiHelper.formatearPorcentajeCohorte(f.getValue().mes1Pct())));
            colCohorteM2.setCellValueFactory(f -> new SimpleStringProperty(
                    FormatoBiUiHelper.formatearPorcentajeCohorte(f.getValue().mes2Pct())));
            colCohorteM3.setCellValueFactory(f -> new SimpleStringProperty(
                    FormatoBiUiHelper.formatearPorcentajeCohorte(f.getValue().mes3Pct())));
            colCohorteM4.setCellValueFactory(f -> new SimpleStringProperty(
                    FormatoBiUiHelper.formatearPorcentajeCohorte(f.getValue().mes4Pct())));
            configurarCeldaHeatmap(colCohorteM1);
            configurarCeldaHeatmap(colCohorteM2);
            configurarCeldaHeatmap(colCohorteM3);
            configurarCeldaHeatmap(colCohorteM4);
            tblMatrizCohortes.setItems(listaMatrizCohortes);
        }
    }

    private void configurarCeldaHeatmap(TableColumn<CohorteMensualFila, String> columna) {
        chartUiRenderer.configurarCeldaHeatmap(columna, presenter);
    }

    private void configurarCategorias() {
        cbCategoriaReporte.setItems(FXCollections.observableArrayList(
                "Ventas por Mozo / Operador",
                REPORTE_MEDIO_PAGO,
                "Ventas por Producto",
                "Compras por Proveedor",
                "Rentabilidad Real de Carta",
                REPORTE_MARGENES_CATEGORIA,
                REPORTE_COHORTES_CLIENTES
        ));
        cbCategoriaReporte.setValue(REPORTE_MEDIO_PAGO);

        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        dpInicio.setValue(hoy.withDayOfMonth(1));
        dpFin.setValue(hoy);

        cbCategoriaReporte.setOnAction(e -> cargarAnalisisAsync());
    }

    @FXML
    public void handleModoAnalitica() {
        String actual = cbCategoriaReporte.getValue();
        if (REPORTE_MARGENES_CATEGORIA.equals(actual) || REPORTE_COHORTES_CLIENTES.equals(actual) || actual == null) {
            cbCategoriaReporte.setValue(REPORTE_MEDIO_PAGO);
        } else {
            cargarAnalisisAsync();
        }
    }

    @FXML
    public void handleModoMargenes() {
        if (!Objects.equals(cbCategoriaReporte.getValue(), REPORTE_MARGENES_CATEGORIA)) {
            cbCategoriaReporte.setValue(REPORTE_MARGENES_CATEGORIA);
        } else {
            cargarAnalisisAsync();
        }
    }

    @FXML
    public void handleModoCohortes() {
        if (!Objects.equals(cbCategoriaReporte.getValue(), REPORTE_COHORTES_CLIENTES)) {
            cbCategoriaReporte.setValue(REPORTE_COHORTES_CLIENTES);
        } else {
            cargarAnalisisAsync();
        }
    }

    public void seleccionarReporteYCargarSincrono(String categoria) {
        cbCategoriaReporte.setValue(categoria);
        cargarAnalisisSincrono();
    }

    @FXML
    public void handleConsultarReporte() {
        cargarAnalisisAsync();
    }

    public void cargarAnalisisAsync() {
        String categoria = cbCategoriaReporte.getValue() != null ? cbCategoriaReporte.getValue() : REPORTE_MEDIO_PAGO;
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        LocalDate ini = dpInicio.getValue() != null ? dpInicio.getValue() : hoy.withDayOfMonth(1);
        LocalDate fin = dpFin.getValue() != null ? dpFin.getValue() : hoy;
        lblEstadoReporte.setText("Consultando " + categoria + "...");
        UiAsyncExecutor.ejecutarAsync(
            () -> consultarAnalisis(categoria, ini, fin),
            res -> aplicarResultadoAnalisis(res, categoria, ini, fin),
            ex -> {
                LOGGER.log(Level.WARNING, "Error en reporte avanzado", ex);
                lblEstadoReporte.setText("Error al consultar reporte");
            }
        );
    }

    public void cargarAnalisisSincrono() {
        String categoria = cbCategoriaReporte.getValue() != null ? cbCategoriaReporte.getValue() : REPORTE_MEDIO_PAGO;
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        LocalDate ini = dpInicio.getValue() != null ? dpInicio.getValue() : hoy.withDayOfMonth(1);
        LocalDate fin = dpFin.getValue() != null ? dpFin.getValue() : hoy;
        ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado res = consultarAnalisis(categoria, ini, fin);
        aplicarResultadoAnalisis(res, categoria, ini, fin);
    }

    private ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado consultarAnalisis(String categoria, LocalDate ini, LocalDate fin) {
        return presenter.consultarAnalisis(categoria, ini, fin, LocalDate.now(ZONA_LIMA));
    }

    private void aplicarResultadoAnalisis(ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado res, String categoria, LocalDate ini, LocalDate fin) {
        actualizarPanelComparativas(res.comparativa());
        actualizarBotonesModo(categoria);

        configurarHeaders(res.headers()[0], res.headers()[1], res.headers()[2], res.headers()[3], res.headers()[4]);
        listaFilas.setAll(res.filas());

        boolean esCohortes = REPORTE_COHORTES_CLIENTES.equals(categoria) && res.cohortesReporte() != null;
        if (panelTablaGrafico != null) {
            panelTablaGrafico.setVisible(!esCohortes);
            panelTablaGrafico.setManaged(!esCohortes);
        }
        if (panelCohortes != null) {
            panelCohortes.setVisible(esCohortes);
            panelCohortes.setManaged(esCohortes);
        }

        if (esCohortes) {
            aplicarModoCohortes(res.cohortesReporte());
        } else {
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (ReportesAvanzadosPresenter.PieSlice s : res.slices()) {
                pieData.add(new PieChart.Data(s.label(), s.value().doubleValue()));
            }
            if (pieChartDistribucion != null) {
                pieChartDistribucion.setData(pieData);
                pieChartDistribucion.applyCss();
                pieChartDistribucion.layout();
                aplicarEstilosDonut();
            }
        }

        lblKpiMontoTotal.setText(res.kpiMontoTotalText() != null
                ? res.kpiMontoTotalText()
                : String.format(Locale.US, "S/ %.2f", res.montoTotal()));
        lblKpiSubTotal.setText(res.kpiSubTotalText() != null
                ? res.kpiSubTotalText()
                : res.cantidadTotal() + " registros procesados");
        BigDecimal prom = res.cantidadTotal() > 0
                ? res.montoTotal().divide(BigDecimal.valueOf(res.cantidadTotal()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        lblKpiPromedio.setText(res.kpiPromedioText() != null
                ? res.kpiPromedioText()
                : String.format(Locale.US, "S/ %.2f", prom));
        lblKpiTopItem.setText(res.topItem());
        lblKpiTopValor.setText(res.kpiTopValorText() != null
                ? res.kpiTopValorText()
                : String.format(Locale.US, "S/ %.2f aporte", res.topValor()));
        lblEstadoReporte.setText("✓ " + categoria + " generado (" + ini + " al " + fin + ")");
    }

    private void actualizarPanelComparativas(ComparativaPeriodos comp) {
        if (comp == null || lblRepHoyVsAyerValor == null) {
            return;
        }
        lblRepHoyVsAyerValor.setText("S/ " + presenter.fmt2(comp.ventasHoy()));
        lblRepHoyVsAyerSub.setText("Ayer: S/ " + presenter.fmt2(comp.ventasAyer()));
        FormatoBiUiHelper.aplicarIndicadorComparativa(lblRepHoyVsAyerBadge, comp.variacionDiariaPct());

        lblRepMesVsAntValor.setText("S/ " + presenter.fmt2(comp.ventasMesActual()));
        lblRepMesVsAntSub.setText("Mes anterior: S/ " + presenter.fmt2(comp.ventasMesAnterior()));
        FormatoBiUiHelper.aplicarIndicadorComparativa(lblRepMesVsAntBadge, comp.variacionMensualPct());
    }

    private void aplicarModoCohortes(ReporteCohortes reporte) {
        listaResumenCohortes.setAll(reporte.resumenRetencion());
        listaMatrizCohortes.setAll(reporte.cohortes());
        if (lblCohortesAviso != null) {
            lblCohortesAviso.setVisible(true);
            lblCohortesAviso.setManaged(true);
            if (!reporte.datosSuficientesCohortes()) {
                lblCohortesAviso.getStyleClass().setAll("badge-pill-warning");
                lblCohortesAviso.setText(
                        "Historial multi-mes insuficiente (< 2 cohortes activas): mostrando tabla simplificada de retención del mes.");
            } else {
                lblCohortesAviso.getStyleClass().setAll("badge-pill-success");
                lblCohortesAviso.setText(
                        "Análisis de cohortes activo · " + reporte.totalClientesIdentificados()
                                + " clientes identificados · Corte: " + reporte.fechaCorte());
            }
        }
    }

    private void actualizarBotonesModo(String categoria) {
        if (btnModoAnalitica == null || btnModoMargenes == null || btnModoCohortes == null) {
            return;
        }
        String activo = presenter.determinarModoActivo(categoria);
        aplicarEstiloBotonModo(btnModoAnalitica, "ANALITICA".equals(activo));
        aplicarEstiloBotonModo(btnModoMargenes, "MARGENES".equals(activo));
        aplicarEstiloBotonModo(btnModoCohortes, "COHORTES".equals(activo));
    }

    private void aplicarEstiloBotonModo(Button btn, boolean activo) {
        btn.getStyleClass().removeAll("btn-primary", "btn-secondary");
        btn.getStyleClass().add(activo ? "btn-primary" : "btn-secondary");
    }


    /**
     * Aplica la paleta SaaS (Naranja, Azul, Verde, Morado...) usando node.setStyle()
     * y transforma el PieChart en un Donut Chart limpio (SPEC v4.0 §1.2).
     */
    public void aplicarEstilosDonut() {
        chartUiRenderer.aplicarEstilosDonut(pieChartDistribucion, PALETA_SAAS);
    }

    private void configurarHeaders(String h1, String h2, String h3, String h4, String h5) {
        col1.setText(h1);
        col2.setText(h2);
        col3.setText(h3);
        col4.setText(h4);
        col5.setText(h5);
        lblTituloTabla.setText("Resultados: " + cbCategoriaReporte.getValue());
    }

    @FXML
    public void handleExportarPdf() {
        String categoria = cbCategoriaReporte.getValue();
        exportarTablaConSelector(btnExportarPdf, ExportFileChooserHelper.FormatoExportacion.PDF, categoria);
    }

    @FXML
    public void handleExportarExcel() {
        String categoria = cbCategoriaReporte.getValue();
        exportarTablaConSelector(btnExportarExcel, ExportFileChooserHelper.FormatoExportacion.EXCEL, categoria);
    }

    @FXML
    public void handleExportarCsv() {
        String categoria = cbCategoriaReporte.getValue();
        exportarTablaConSelector(btnExportarCsv, ExportFileChooserHelper.FormatoExportacion.CSV, categoria);
    }

    private void exportarTablaConSelector(Button origenBtn,
            ExportFileChooserHelper.FormatoExportacion formato, String categoria) {
        ReporteExportCoordinator.SolicitudExportacion solicitud = exportCoordinator.prepararSolicitud(formato, categoria);
        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                origenBtn != null ? origenBtn : lblEstadoReporte,
                solicitud.tituloDialogo(), solicitud.nombreSugerido(), solicitud.formato());
        if (elegidoOpt.isEmpty()) {
            if (lblEstadoReporte != null) {
                lblEstadoReporte.setText("Exportación cancelada.");
            }
            return;
        }

        File doc = elegidoOpt.get();
        List<String> headers = List.of(col1.getText(), col2.getText(), col3.getText(), col4.getText(), col5.getText());
        List<List<String>> filas = exportCoordinator.convertirFilas(List.copyOf(listaFilas));

        if (lblEstadoReporte != null) {
            lblEstadoReporte.setText(solicitud.estadoProcesando());
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                exportCoordinator.ejecutarExportacion(formato, categoria, headers, filas, doc);
                return doc.getName();
            },
            nombre -> {
                if (lblEstadoReporte != null) {
                    lblEstadoReporte.setText(solicitud.prefijoExito() + nombre);
                }
            },
            ex -> {
                if (lblEstadoReporte != null) {
                    lblEstadoReporte.setText(solicitud.prefijoError() + (ex != null ? ex.getMessage() : "desconocido"));
                }
            }
        );
    }


    public PieChart getPieChartDistribucion() {
        return pieChartDistribucion;
    }

    public List<FilaReporteAvanzado> getFilasActuales() {
        return List.copyOf(listaFilas);
    }

    public List<SegmentoRetencionFila> getResumenCohortesActual() {
        return List.copyOf(listaResumenCohortes);
    }

    public List<CohorteMensualFila> getMatrizCohortesActual() {
        return List.copyOf(listaMatrizCohortes);
    }

    public static class FilaReporteAvanzado {
        public String c1;
        public String c2;
        public String c3;
        public String c4;
        public String c5;

        public FilaReporteAvanzado(String c1, String c2, String c3, String c4, String c5) {
            this.c1 = c1;
            this.c2 = c2;
            this.c3 = c3;
            this.c4 = c4;
            this.c5 = c5;
        }
    }
}
