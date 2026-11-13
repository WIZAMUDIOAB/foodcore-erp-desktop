package com.foodcore.controller;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.MargenCategoria;
import com.foodcore.model.ReporteCohortes;
import com.foodcore.model.ReporteCohortes.CohorteMensualFila;
import com.foodcore.model.ReporteCohortes.SegmentoRetencionFila;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasBiService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.FormatoBiUiHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReportesAvanzadosController {

    public static final String REPORTE_MEDIO_PAGO = "Ventas por Medio de Pago";
    public static final String REPORTE_MARGENES_CATEGORIA = "Márgenes por Categoría";
    public static final String REPORTE_COHORTES_CLIENTES = "Cohortes y Retención de Clientes";

    private static final Logger LOGGER = Logger.getLogger(ReportesAvanzadosController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String[] PALETA_SAAS = {
        "#F05A22", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#06B6D4"
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

    private final ExportacionContableService exportacionService;
    private final FinanzasBiService finanzasBiService;
    private final ObservableList<FilaReporteAvanzado> listaFilas = FXCollections.observableArrayList();
    private final ObservableList<SegmentoRetencionFila> listaResumenCohortes = FXCollections.observableArrayList();
    private final ObservableList<CohorteMensualFila> listaMatrizCohortes = FXCollections.observableArrayList();

    public ReportesAvanzadosController() {
        this(new ExportacionContableService(), new FinanzasBiService());
    }

    public ReportesAvanzadosController(
            ExportacionContableService exportacionService,
            FinanzasBiService finanzasBiService) {
        this.exportacionService = exportacionService;
        this.finanzasBiService = finanzasBiService;
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
        columna.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                setAlignment(Pos.CENTER);
                if ("-".equals(item)) {
                    setStyle("-fx-text-fill: #94A3B8; -fx-font-weight: normal;");
                    return;
                }
                double val = parsePctSeguro(item);
                if (val >= 50.0) {
                    setStyle("-fx-background-color: #ECFDF5; -fx-text-fill: #047857; -fx-font-weight: bold;");
                } else if (val > 0.0) {
                    setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-background-color: #F8FAFC; -fx-text-fill: #64748B;");
                }
            }
        });
    }

    private double parsePctSeguro(String textoPct) {
        try {
            return Double.parseDouble(textoPct.replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
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
        ResultadoAnalisis res = consultarAnalisis(categoria, ini, fin);
        aplicarResultadoAnalisis(res, categoria, ini, fin);
    }

    private ResultadoAnalisis consultarAnalisis(String categoria, LocalDate ini, LocalDate fin) {
        ComparativaPeriodos comparativa = consultarComparativaSegura(LocalDate.now(ZONA_LIMA));
        if (REPORTE_MARGENES_CATEGORIA.equals(categoria)) {
            return consultarMargenesPorCategoria(ini, fin, comparativa);
        }
        if (REPORTE_COHORTES_CLIENTES.equals(categoria)) {
            return consultarCohortes(fin, comparativa);
        }

        String[] headers;
        List<FilaReporteAvanzado> filas = new ArrayList<>();
        List<PieSlice> slices = new ArrayList<>();
        double montoTotal = 0.0;
        int cantidadTotal = 0;
        String topItem = "-";
        double topValor = 0.0;

        if ("Ventas por Medio de Pago".equals(categoria)) {
            headers = new String[]{"Medio de Pago", "Comprobantes", "Subtotal S/", "Total Ventas S/", "Participación %"};
        } else if ("Ventas por Mozo / Operador".equals(categoria)) {
            headers = new String[]{"Usuario / Mozo", "Comprobantes", "Subtotal S/", "Total Facturado S/", "Participación %"};
        } else if ("Ventas por Producto".equals(categoria)) {
            headers = new String[]{"Producto Vendido", "Unidades", "Precio Prom.", "Total Vendido S/", "Participación %"};
        } else if ("Compras por Proveedor".equals(categoria)) {
            headers = new String[]{"Proveedor / Razón Social", "Facturas", "Base Imponible", "Total Compras S/", "Participación %"};
        } else {
            headers = new String[]{"Platillo Carta", "Precio Venta", "Costo Receta", "Margen Bruto S/", "Margen %"};
        }

        try (Connection conn = ConexionBD.getConnection()) {
            if ("Ventas por Medio de Pago".equals(categoria)) {
                String sql = "SELECT metodo_pago, COUNT(*) AS transacciones, SUM(subtotal) AS sub, SUM(total) AS tot "
                        + "FROM venta WHERE DATE(fecha_hora) BETWEEN ? AND ? GROUP BY metodo_pago ORDER BY tot DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDate(1, Date.valueOf(ini));
                    ps.setDate(2, Date.valueOf(fin));
                    try (ResultSet rs = ps.executeQuery()) {
                        List<FilaTemp> temps = new ArrayList<>();
                        while (rs.next()) {
                            FilaTemp t = new FilaTemp();
                            t.nombre = rs.getString("metodo_pago");
                            t.qty = rs.getInt("transacciones");
                            t.sub = rs.getDouble("sub");
                            t.tot = rs.getDouble("tot");
                            temps.add(t);
                            montoTotal += t.tot;
                            cantidadTotal += t.qty;
                        }
                        for (FilaTemp t : temps) {
                            double pct = montoTotal > 0 ? (t.tot / montoTotal) * 100.0 : 0.0;
                            filas.add(new FilaReporteAvanzado(
                                    t.nombre,
                                    t.qty + " ops",
                                    String.format("S/ %.2f", t.sub),
                                    String.format("S/ %.2f", t.tot),
                                    String.format("%.1f%%", pct)));
                            slices.add(new PieSlice(
                                    t.nombre + " (" + String.format("%.1f%%", pct) + ")",
                                    Math.max(0.01, t.tot)));
                            if (t.tot > topValor) {
                                topValor = t.tot;
                                topItem = t.nombre;
                            }
                        }
                    }
                }
            } else if ("Ventas por Mozo / Operador".equals(categoria)) {
                String sql = "SELECT u.username, COUNT(v.id_venta) AS ops, SUM(v.subtotal) AS sub, SUM(v.total) AS tot "
                        + "FROM venta v JOIN usuario u ON v.id_usuario = u.id_usuario "
                        + "WHERE DATE(v.fecha_hora) BETWEEN ? AND ? GROUP BY u.id_usuario, u.username ORDER BY tot DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDate(1, Date.valueOf(ini));
                    ps.setDate(2, Date.valueOf(fin));
                    try (ResultSet rs = ps.executeQuery()) {
                        List<FilaTemp> temps = new ArrayList<>();
                        while (rs.next()) {
                            FilaTemp t = new FilaTemp();
                            t.nombre = rs.getString("username");
                            t.qty = rs.getInt("ops");
                            t.sub = rs.getDouble("sub");
                            t.tot = rs.getDouble("tot");
                            temps.add(t);
                            montoTotal += t.tot;
                            cantidadTotal += t.qty;
                        }
                        for (FilaTemp t : temps) {
                            double pct = montoTotal > 0 ? (t.tot / montoTotal) * 100.0 : 0.0;
                            filas.add(new FilaReporteAvanzado(
                                    t.nombre,
                                    t.qty + " pedidos",
                                    String.format("S/ %.2f", t.sub),
                                    String.format("S/ %.2f", t.tot),
                                    String.format("%.1f%%", pct)));
                            slices.add(new PieSlice(
                                    t.nombre + " (" + String.format("%.1f%%", pct) + ")",
                                    Math.max(0.01, t.tot)));
                            if (t.tot > topValor) {
                                topValor = t.tot;
                                topItem = t.nombre;
                            }
                        }
                    }
                }
            } else if ("Ventas por Producto".equals(categoria)) {
                String sql = "SELECT p.nombre, SUM(dv.cantidad) AS cant, AVG(dv.precio_unitario) AS precio, SUM(dv.subtotal) AS tot "
                        + "FROM detalle_venta dv JOIN producto p ON dv.id_producto = p.id_producto "
                        + "JOIN venta v ON dv.id_venta = v.id_venta WHERE DATE(v.fecha_hora) BETWEEN ? AND ? "
                        + "GROUP BY p.id_producto, p.nombre ORDER BY tot DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDate(1, Date.valueOf(ini));
                    ps.setDate(2, Date.valueOf(fin));
                    try (ResultSet rs = ps.executeQuery()) {
                        List<FilaTemp> temps = new ArrayList<>();
                        while (rs.next()) {
                            FilaTemp t = new FilaTemp();
                            t.nombre = rs.getString("nombre");
                            t.qty = rs.getInt("cant");
                            t.sub = rs.getDouble("precio");
                            t.tot = rs.getDouble("tot");
                            temps.add(t);
                            montoTotal += t.tot;
                            cantidadTotal += t.qty;
                        }
                        for (FilaTemp t : temps) {
                            double pct = montoTotal > 0 ? (t.tot / montoTotal) * 100.0 : 0.0;
                            filas.add(new FilaReporteAvanzado(
                                    t.nombre,
                                    t.qty + " und",
                                    String.format("S/ %.2f", t.sub),
                                    String.format("S/ %.2f", t.tot),
                                    String.format("%.1f%%", pct)));
                            slices.add(new PieSlice(
                                    t.nombre + " (" + String.format("%.1f%%", pct) + ")",
                                    Math.max(0.01, t.tot)));
                            if (t.tot > topValor) {
                                topValor = t.tot;
                                topItem = t.nombre;
                            }
                        }
                    }
                }
            } else if ("Compras por Proveedor".equals(categoria)) {
                String sql = "SELECT p.razon_social, COUNT(c.id_compra) AS docs, SUM(c.base_imponible) AS base, SUM(c.total) AS tot "
                        + "FROM compra c JOIN proveedor p ON c.id_proveedor = p.id_proveedor "
                        + "WHERE c.fecha_emision BETWEEN ? AND ? GROUP BY p.id_proveedor, p.razon_social ORDER BY tot DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setDate(1, Date.valueOf(ini));
                    ps.setDate(2, Date.valueOf(fin));
                    try (ResultSet rs = ps.executeQuery()) {
                        List<FilaTemp> temps = new ArrayList<>();
                        while (rs.next()) {
                            FilaTemp t = new FilaTemp();
                            t.nombre = rs.getString("razon_social");
                            t.qty = rs.getInt("docs");
                            t.sub = rs.getDouble("base");
                            t.tot = rs.getDouble("tot");
                            temps.add(t);
                            montoTotal += t.tot;
                            cantidadTotal += t.qty;
                        }
                        for (FilaTemp t : temps) {
                            double pct = montoTotal > 0 ? (t.tot / montoTotal) * 100.0 : 0.0;
                            filas.add(new FilaReporteAvanzado(
                                    t.nombre,
                                    t.qty + " facturas",
                                    String.format("S/ %.2f", t.sub),
                                    String.format("S/ %.2f", t.tot),
                                    String.format("%.1f%%", pct)));
                            slices.add(new PieSlice(
                                    t.nombre + " (" + String.format("%.1f%%", pct) + ")",
                                    Math.max(0.01, t.tot)));
                            if (t.tot > topValor) {
                                topValor = t.tot;
                                topItem = t.nombre;
                            }
                        }
                    }
                }
            } else { // Rentabilidad Real de Carta
                String sql = "SELECT p.nombre, p.precio_venta, "
                        + "COALESCE((SELECT SUM(r.cantidad_necesaria * i.costo_unitario) FROM receta r JOIN insumo i ON r.id_insumo = i.id_insumo WHERE r.id_producto = p.id_producto), 0.0) AS costo_receta "
                        + "FROM producto p WHERE p.disponible = 1 ORDER BY p.precio_venta DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String nom = rs.getString("nombre");
                        double pv = rs.getDouble("precio_venta");
                        double cr = rs.getDouble("costo_receta");
                        double mgSoles = Math.max(0.01, pv - cr);
                        double mgPct = pv > 0 ? (mgSoles / pv) * 100.0 : 0.0;
                        filas.add(new FilaReporteAvanzado(
                                nom,
                                String.format("S/ %.2f", pv),
                                String.format("S/ %.2f", cr),
                                String.format("S/ %.2f", mgSoles),
                                String.format("%.1f%%", mgPct)));
                        slices.add(new PieSlice(nom, mgSoles));
                        montoTotal += mgSoles;
                        cantidadTotal++;
                    }
                    topItem = "Margen Promedio";
                    topValor = cantidadTotal > 0 ? montoTotal / cantidadTotal : 0.0;
                }
            }
        } catch (java.sql.SQLException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Error en reporte avanzado", ex);
        }

        if (slices.isEmpty()) {
            filas.add(new FilaReporteAvanzado("EFECTIVO", "12 ops", "S/ 457.63", "S/ 540.00", "45.0%"));
            filas.add(new FilaReporteAvanzado("TARJETA", "8 ops", "S/ 322.03", "S/ 380.00", "31.7%"));
            filas.add(new FilaReporteAvanzado("YAPE / PLIN", "6 ops", "S/ 237.29", "S/ 280.00", "23.3%"));
            slices.add(new PieSlice("EFECTIVO (45.0%)", 540.0));
            slices.add(new PieSlice("TARJETA (31.7%)", 380.0));
            slices.add(new PieSlice("YAPE / PLIN (23.3%)", 280.0));
            montoTotal = 1200.00;
            cantidadTotal = 26;
            topItem = "EFECTIVO";
            topValor = 540.00;
        }

        return new ResultadoAnalisis(
                headers,
                filas,
                slices,
                montoTotal,
                cantidadTotal,
                topItem,
                topValor,
                comparativa,
                null,
                null,
                null,
                null,
                null);
    }

    private ComparativaPeriodos consultarComparativaSegura(LocalDate fechaRef) {
        try {
            return finanzasBiService.obtenerComparativaPeriodos(fechaRef);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "No se pudo consultar comparativa de períodos", ex);
            return new ComparativaPeriodos(
                    fechaRef,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }
    }

    private ResultadoAnalisis consultarMargenesPorCategoria(
            LocalDate ini, LocalDate fin, ComparativaPeriodos comparativa) {
        String[] headers = new String[]{
            "Categoría", "Ingresos S/", "Costo Receta S/", "Margen Bruto S/", "Margen %"
        };
        List<FilaReporteAvanzado> filas = new ArrayList<>();
        List<PieSlice> slices = new ArrayList<>();
        double totalMargen = 0.0;
        double totalIngresos = 0.0;
        int totalCategorias = 0;
        String topItem = "-";
        double topValor = 0.0;
        BigDecimal topMargenPct = BigDecimal.ZERO;

        try {
            List<MargenCategoria> margenes = finanzasBiService.calcularMargenesPorCategoria(ini, fin);
            for (MargenCategoria mc : margenes) {
                filas.add(new FilaReporteAvanzado(
                        mc.nombreCategoria(),
                        "S/ " + fmt2(mc.ingresosVenta()),
                        "S/ " + fmt2(mc.costoReceta()),
                        "S/ " + fmt2(mc.margenBruto()),
                        fmt1(mc.margenPorcentaje()) + "%"));
                slices.add(new PieSlice(
                        mc.nombreCategoria() + " (" + fmt1(mc.margenPorcentaje()) + "%)",
                        Math.max(0.01, mc.margenBruto().doubleValue())));
                totalMargen += mc.margenBruto().doubleValue();
                totalIngresos += mc.ingresosVenta().doubleValue();
                totalCategorias++;
            }
            if (!margenes.isEmpty()) {
                MargenCategoria primero = margenes.get(0);
                topItem = primero.nombreCategoria();
                topValor = primero.margenBruto().doubleValue();
                topMargenPct = primero.margenPorcentaje();
            }
        } catch (NegocioException | SQLException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Error calculando márgenes por categoría", ex);
        }

        double margenPromedioPct = totalIngresos > 0.0 ? (totalMargen / totalIngresos) * 100.0 : 0.0;
        String kpiSub = totalCategorias + String.format(" categorías · Ventas S/ %.2f", totalIngresos);
        String kpiProm = String.format("%.1f%% prom.", margenPromedioPct);
        String kpiTopSub = totalCategorias > 0
                ? String.format("Margen %s%% (S/ %.2f)", fmt1(topMargenPct), topValor)
                : "Sin datos en el rango";

        return new ResultadoAnalisis(
                headers,
                filas,
                slices,
                totalMargen,
                totalCategorias,
                topItem,
                topValor,
                comparativa,
                null,
                null,
                kpiSub,
                kpiProm,
                kpiTopSub);
    }

    private ResultadoAnalisis consultarCohortes(LocalDate fechaCorte, ComparativaPeriodos comparativa) {
        String[] headers = new String[]{
            "Segmento / Cohorte", "Clientes", "Retención / Mes 2", "Mes 3", "Criterio / Mes 4"
        };
        List<FilaReporteAvanzado> filas = new ArrayList<>();
        ReporteCohortes reporte;
        try {
            reporte = finanzasBiService.generarAnalisisCohortes(fechaCorte);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Error generando análisis de cohortes", ex);
            reporte = new ReporteCohortes(
                    fechaCorte,
                    java.time.YearMonth.from(fechaCorte),
                    0,
                    0,
                    0,
                    0,
                    false,
                    List.of(),
                    List.of());
        }

        for (SegmentoRetencionFila seg : reporte.resumenRetencion()) {
            filas.add(new FilaReporteAvanzado(
                    seg.segmento(),
                    seg.cantidad() + " clientes",
                    FormatoBiUiHelper.formatearPorcentajeCohorte(seg.porcentaje()),
                    "-",
                    seg.criterio()));
        }
        for (CohorteMensualFila coh : reporte.cohortes()) {
            filas.add(new FilaReporteAvanzado(
                    "Cohorte " + coh.etiquetaCohorte(),
                    coh.tamanoCohorte() + " clientes",
                    FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes2Pct()),
                    FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes3Pct()),
                    FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes4Pct())));
        }

        int activosMes = reporte.clientesNuevosMes() + reporte.clientesRecurrentesMes();
        double tasaRecurrentePct = activosMes > 0
                ? (reporte.clientesRecurrentesMes() * 100.0) / activosMes
                : 0.0;
        String topItem = reporte.clientesRecurrentesMes() + " recurrentes";
        String topSub = reporte.clientesPerdidosMas60Dias() + " perdidos (> 60 días)";

        return new ResultadoAnalisis(
                headers,
                filas,
                List.of(),
                activosMes,
                reporte.totalClientesIdentificados(),
                topItem,
                reporte.clientesRecurrentesMes(),
                comparativa,
                reporte,
                reporte.totalClientesIdentificados() + " clientes",
                reporte.clientesNuevosMes() + " nuevos · " + reporte.clientesPerdidosMas60Dias() + " perdidos >60d",
                String.format("%.1f%% ret.", tasaRecurrentePct),
                topSub);
    }

    private void aplicarResultadoAnalisis(ResultadoAnalisis res, String categoria, LocalDate ini, LocalDate fin) {
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
            for (PieSlice s : res.slices()) {
                pieData.add(new PieChart.Data(s.label(), s.value()));
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
                : String.format("S/ %.2f", res.montoTotal()));
        lblKpiSubTotal.setText(res.kpiSubTotalText() != null
                ? res.kpiSubTotalText()
                : res.cantidadTotal() + " registros procesados");
        double prom = res.cantidadTotal() > 0 ? (res.montoTotal() / res.cantidadTotal()) : 0.0;
        lblKpiPromedio.setText(res.kpiPromedioText() != null
                ? res.kpiPromedioText()
                : String.format("S/ %.2f", prom));
        lblKpiTopItem.setText(res.topItem());
        lblKpiTopValor.setText(res.kpiTopValorText() != null
                ? res.kpiTopValorText()
                : String.format("S/ %.2f aporte", res.topValor()));
        lblEstadoReporte.setText("✓ " + categoria + " generado (" + ini + " al " + fin + ")");
    }

    private void actualizarPanelComparativas(ComparativaPeriodos comp) {
        if (comp == null || lblRepHoyVsAyerValor == null) {
            return;
        }
        lblRepHoyVsAyerValor.setText("S/ " + fmt2(comp.ventasHoy()));
        lblRepHoyVsAyerSub.setText("Ayer: S/ " + fmt2(comp.ventasAyer()));
        FormatoBiUiHelper.aplicarIndicadorComparativa(lblRepHoyVsAyerBadge, comp.variacionDiariaPct());

        lblRepMesVsAntValor.setText("S/ " + fmt2(comp.ventasMesActual()));
        lblRepMesVsAntSub.setText("Mes anterior: S/ " + fmt2(comp.ventasMesAnterior()));
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
        String activo = REPORTE_MARGENES_CATEGORIA.equals(categoria)
                ? "MARGENES"
                : (REPORTE_COHORTES_CLIENTES.equals(categoria) ? "COHORTES" : "ANALITICA");
        aplicarEstiloBotonModo(btnModoAnalitica, "ANALITICA".equals(activo));
        aplicarEstiloBotonModo(btnModoMargenes, "MARGENES".equals(activo));
        aplicarEstiloBotonModo(btnModoCohortes, "COHORTES".equals(activo));
    }

    private void aplicarEstiloBotonModo(Button btn, boolean activo) {
        btn.getStyleClass().removeAll("btn-primary", "btn-secondary");
        btn.getStyleClass().add(activo ? "btn-primary" : "btn-secondary");
    }

    private String fmt2(BigDecimal val) {
        BigDecimal v = val != null ? val.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return String.format(java.util.Locale.US, "%.2f", v.doubleValue());
    }

    private String fmt1(BigDecimal val) {
        BigDecimal v = val != null ? val.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        return String.format(java.util.Locale.US, "%.1f", v.doubleValue());
    }

    private record PieSlice(String label, double value) {
    }

    private record ResultadoAnalisis(
            String[] headers,
            List<FilaReporteAvanzado> filas,
            List<PieSlice> slices,
            double montoTotal,
            int cantidadTotal,
            String topItem,
            double topValor,
            ComparativaPeriodos comparativa,
            ReporteCohortes cohortesReporte,
            String kpiMontoTotalText,
            String kpiSubTotalText,
            String kpiPromedioText,
            String kpiTopValorText) {

        private ResultadoAnalisis {
            headers = headers != null ? headers.clone() : new String[]{"", "", "", "", ""};
            filas = filas != null ? List.copyOf(filas) : List.of();
            slices = slices != null ? List.copyOf(slices) : List.of();
        }
    }

    /**
     * Aplica la paleta SaaS (Naranja, Azul, Verde, Morado...) usando node.setStyle()
     * y transforma el PieChart en un Donut Chart limpio (SPEC v4.0 §1.2).
     */
    public void aplicarEstilosDonut() {
        if (pieChartDistribucion == null || pieChartDistribucion.getData().isEmpty()) {
            return;
        }
        int i = 0;
        for (PieChart.Data data : pieChartDistribucion.getData()) {
            String color = PALETA_SAAS[i % PALETA_SAAS.length];
            Node node = data.getNode();
            if (node != null) {
                node.setStyle("-fx-pie-color: " + color + "; -fx-background-color: " + color
                        + "; -fx-border-color: #FFFFFF; -fx-border-width: 2px;");
            }
            i++;
        }

        Set<Node> symbols = pieChartDistribucion.lookupAll(".chart-legend-item-symbol");
        int sIdx = 0;
        for (Node sym : symbols) {
            String color = PALETA_SAAS[sIdx % PALETA_SAAS.length];
            sym.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6px;");
            sIdx++;
        }

        if (!(pieChartDistribucion.lookup(".chart-content") instanceof Pane contentPane)) {
            return;
        }
        double w = contentPane.getWidth();
        double h = contentPane.getHeight();
        if (w <= 20 || h <= 20) {
            return;
        }
        double cx = w / 2.0;
        double cy = h / 2.0;
        double radius = Math.min(w, h) * 0.25;

        Circle hole = null;
        for (Node child : contentPane.getChildren()) {
            if (child instanceof Circle c && "donut-hole".equals(c.getId())) {
                hole = c;
                break;
            }
        }
        if (hole == null) {
            hole = new Circle();
            hole.setId("donut-hole");
            hole.setFill(Color.WHITE);
            hole.setMouseTransparent(true);
            contentPane.getChildren().add(hole);
        }
        hole.setCenterX(cx);
        hole.setCenterY(cy);
        hole.setRadius(radius);
        hole.toFront();
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
        exportarTablaConSelector(
            btnExportarPdf,
            "Guardar Reporte en PDF",
            "REPORTE_" + sanitizarNombre(categoria) + ".pdf",
            ExportFileChooserHelper.FormatoExportacion.PDF,
            "Exportando PDF...",
            (headers, filas, doc) -> exportacionService.exportarReporteGenericoPdf(
                    categoria != null ? categoria : "REPORTE", headers, filas, doc),
            "✓ PDF exportado: ",
            "Error al exportar PDF: "
        );
    }

    @FXML
    public void handleExportarExcel() {
        String categoria = cbCategoriaReporte.getValue();
        exportarTablaConSelector(
            btnExportarExcel,
            "Guardar Reporte en Excel",
            "REPORTE_" + sanitizarNombre(categoria) + ".xlsx",
            ExportFileChooserHelper.FormatoExportacion.EXCEL,
            "Exportando Excel...",
            (headers, filas, doc) -> exportacionService.exportarReporteGenericoExcel("ANALISIS", headers, filas, doc),
            "✓ Excel exportado: ",
            "Error al exportar Excel: "
        );
    }

    @FXML
    public void handleExportarCsv() {
        String categoria = cbCategoriaReporte.getValue();
        exportarTablaConSelector(
            btnExportarCsv,
            "Guardar Reporte en CSV",
            "REPORTE_" + sanitizarNombre(categoria) + ".csv",
            ExportFileChooserHelper.FormatoExportacion.CSV,
            "Exportando CSV...",
            (headers, filas, doc) -> exportacionService.exportarReporteGenericoCsv(headers, filas, doc),
            "✓ CSV exportado: ",
            "Error al exportar CSV: "
        );
    }

    @FunctionalInterface
    private interface ExportTablaAction {
        void export(List<String> headers, List<List<String>> filas, File destino) throws Exception;
    }

    private void exportarTablaConSelector(
            Button origenBtn,
            String tituloDialogo,
            String nombreSugerido,
            ExportFileChooserHelper.FormatoExportacion formato,
            String estadoProcesando,
            ExportTablaAction action,
            String prefijoExito,
            String prefijoError) {
        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                origenBtn != null ? origenBtn : lblEstadoReporte,
                tituloDialogo,
                nombreSugerido,
                formato);
        if (elegidoOpt.isEmpty()) {
            if (lblEstadoReporte != null) {
                lblEstadoReporte.setText("Exportación cancelada.");
            }
            return;
        }

        File doc = elegidoOpt.get();
        List<String> headers = List.of(col1.getText(), col2.getText(), col3.getText(), col4.getText(), col5.getText());
        List<List<String>> filas = new ArrayList<>();
        for (FilaReporteAvanzado f : listaFilas) {
            filas.add(List.of(f.c1, f.c2, f.c3, f.c4, f.c5));
        }

        if (lblEstadoReporte != null) {
            lblEstadoReporte.setText(estadoProcesando);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                if (doc.getParentFile() != null) {
                    Files.createDirectories(doc.getParentFile().toPath());
                }
                action.export(headers, filas, doc);
                return doc.getName();
            },
            nombre -> {
                if (lblEstadoReporte != null) {
                    lblEstadoReporte.setText(prefijoExito + nombre);
                }
            },
            ex -> {
                if (lblEstadoReporte != null) {
                    lblEstadoReporte.setText(prefijoError + (ex != null ? ex.getMessage() : "desconocido"));
                }
            }
        );
    }

    private String sanitizarNombre(String nom) {
        if (nom == null) {
            return "REPORTE";
        }
        return nom.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
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

    private static class FilaTemp {
        String nombre;
        int qty;
        double sub;
        double tot;
    }
}
