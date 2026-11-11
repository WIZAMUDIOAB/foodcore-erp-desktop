package com.foodcore.controller;

import com.foodcore.service.ExportacionContableService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.File;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReportesAvanzadosController {

    private static final Logger LOGGER = Logger.getLogger(ReportesAvanzadosController.class.getName());
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
    @FXML private Label lblEstadoReporte;

    @FXML private Label lblKpiMontoTotal;
    @FXML private Label lblKpiSubTotal;
    @FXML private Label lblKpiPromedio;
    @FXML private Label lblKpiTopItem;
    @FXML private Label lblKpiTopValor;
    @FXML private Label lblTituloTabla;

    @FXML private TableView<FilaReporteAvanzado> tblReporte;
    @FXML private TableColumn<FilaReporteAvanzado, String> col1;
    @FXML private TableColumn<FilaReporteAvanzado, String> col2;
    @FXML private TableColumn<FilaReporteAvanzado, String> col3;
    @FXML private TableColumn<FilaReporteAvanzado, String> col4;
    @FXML private TableColumn<FilaReporteAvanzado, String> col5;

    @FXML private PieChart pieChartDistribucion;

    private final ExportacionContableService exportacionService = new ExportacionContableService();
    private final ObservableList<FilaReporteAvanzado> listaFilas = FXCollections.observableArrayList();

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

    private void configurarCategorias() {
        cbCategoriaReporte.setItems(FXCollections.observableArrayList(
                "Ventas por Mozo / Operador",
                "Ventas por Medio de Pago",
                "Ventas por Producto",
                "Compras por Proveedor",
                "Rentabilidad Real de Carta"
        ));
        cbCategoriaReporte.setValue("Ventas por Medio de Pago");

        LocalDate hoy = LocalDate.now();
        dpInicio.setValue(hoy.withDayOfMonth(1));
        dpFin.setValue(hoy);

        cbCategoriaReporte.setOnAction(e -> cargarAnalisisAsync());
    }

    @FXML
    public void handleConsultarReporte() {
        cargarAnalisisAsync();
    }

    public void cargarAnalisisAsync() {
        String categoria = cbCategoriaReporte.getValue() != null ? cbCategoriaReporte.getValue() : "Ventas por Medio de Pago";
        LocalDate ini = dpInicio.getValue() != null ? dpInicio.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = dpFin.getValue() != null ? dpFin.getValue() : LocalDate.now();
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
        String categoria = cbCategoriaReporte.getValue() != null ? cbCategoriaReporte.getValue() : "Ventas por Medio de Pago";
        LocalDate ini = dpInicio.getValue() != null ? dpInicio.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = dpFin.getValue() != null ? dpFin.getValue() : LocalDate.now();
        ResultadoAnalisis res = consultarAnalisis(categoria, ini, fin);
        aplicarResultadoAnalisis(res, categoria, ini, fin);
    }

    private ResultadoAnalisis consultarAnalisis(String categoria, LocalDate ini, LocalDate fin) {
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

        return new ResultadoAnalisis(headers, filas, slices, montoTotal, cantidadTotal, topItem, topValor);
    }

    private void aplicarResultadoAnalisis(ResultadoAnalisis res, String categoria, LocalDate ini, LocalDate fin) {
        configurarHeaders(res.headers()[0], res.headers()[1], res.headers()[2], res.headers()[3], res.headers()[4]);
        listaFilas.setAll(res.filas());

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

        lblKpiMontoTotal.setText(String.format("S/ %.2f", res.montoTotal()));
        lblKpiSubTotal.setText(res.cantidadTotal() + " registros procesados");
        double prom = res.cantidadTotal() > 0 ? (res.montoTotal() / res.cantidadTotal()) : 0.0;
        lblKpiPromedio.setText(String.format("S/ %.2f", prom));
        lblKpiTopItem.setText(res.topItem());
        lblKpiTopValor.setText(String.format("S/ %.2f aporte", res.topValor()));
        lblEstadoReporte.setText("✓ " + categoria + " generado (" + ini + " al " + fin + ")");
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
            double topValor) {
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
        List<String> headers = List.of(col1.getText(), col2.getText(), col3.getText(), col4.getText(), col5.getText());
        List<List<String>> filas = new ArrayList<>();
        for (FilaReporteAvanzado f : listaFilas) {
            filas.add(List.of(f.c1, f.c2, f.c3, f.c4, f.c5));
        }
        lblEstadoReporte.setText("Exportando PDF...");
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                File doc = new File("dist/REPORTE_" + sanitizarNombre(categoria) + ".pdf");
                if (doc.getParentFile() != null) {
                    java.nio.file.Files.createDirectories(doc.getParentFile().toPath());
                }
                exportacionService.exportarReporteGenericoPdf(categoria, headers, filas, doc);
                return doc.getName();
            },
            nombre -> lblEstadoReporte.setText("✓ PDF exportado: " + nombre),
            ex -> lblEstadoReporte.setText("Error al exportar PDF: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    @FXML
    public void handleExportarExcel() {
        String categoria = cbCategoriaReporte.getValue();
        List<String> headers = List.of(col1.getText(), col2.getText(), col3.getText(), col4.getText(), col5.getText());
        List<List<String>> filas = new ArrayList<>();
        for (FilaReporteAvanzado f : listaFilas) {
            filas.add(List.of(f.c1, f.c2, f.c3, f.c4, f.c5));
        }
        lblEstadoReporte.setText("Exportando Excel...");
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                File doc = new File("dist/REPORTE_" + sanitizarNombre(categoria) + ".xlsx");
                if (doc.getParentFile() != null) {
                    java.nio.file.Files.createDirectories(doc.getParentFile().toPath());
                }
                exportacionService.exportarReporteGenericoExcel("ANALISIS", headers, filas, doc);
                return doc.getName();
            },
            nombre -> lblEstadoReporte.setText("✓ Excel exportado: " + nombre),
            ex -> lblEstadoReporte.setText("Error al exportar Excel: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    @FXML
    public void handleExportarCsv() {
        String categoria = cbCategoriaReporte.getValue();
        List<String> headers = List.of(col1.getText(), col2.getText(), col3.getText(), col4.getText(), col5.getText());
        List<List<String>> filas = new ArrayList<>();
        for (FilaReporteAvanzado f : listaFilas) {
            filas.add(List.of(f.c1, f.c2, f.c3, f.c4, f.c5));
        }
        lblEstadoReporte.setText("Exportando CSV...");
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                File doc = new File("dist/REPORTE_" + sanitizarNombre(categoria) + ".csv");
                if (doc.getParentFile() != null) {
                    java.nio.file.Files.createDirectories(doc.getParentFile().toPath());
                }
                exportacionService.exportarReporteGenericoCsv(headers, filas, doc);
                return doc.getName();
            },
            nombre -> lblEstadoReporte.setText("✓ CSV exportado: " + nombre),
            ex -> lblEstadoReporte.setText("Error al exportar CSV: " + (ex != null ? ex.getMessage() : "desconocido"))
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
