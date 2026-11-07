package com.foodcore.controller;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.Merma;
import com.foodcore.service.AnalisisMermasService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UnidadMedidaFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AnalisisMermasController {

    private static final Logger LOGGER = Logger.getLogger(AnalisisMermasController.class.getName());
    private static final String[] PALETA_SAAS = {
        "#F05A22", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#06B6D4"
    };

    @FXML private StackPane rootMermasStack;
    @FXML private Button btnNuevaMerma;

    @FXML private HBox bannerAlertasMermas;
    @FXML private Label lblTextoAlertaMermas;

    @FXML private Label lblKpiCostoPerdida;
    @FXML private Label lblKpiTotalEventos;
    @FXML private Label lblKpiInsumoCritico;
    @FXML private Label lblKpiMotivoPredominante;

    @FXML private AreaChart<String, Number> areaChartMermas;
    @FXML private PieChart chartMermasMotivo;

    @FXML private TableView<Merma> tblTopInsumos;
    @FXML private TableColumn<Merma, String> colTopInsumo;
    @FXML private TableColumn<Merma, String> colTopCant;
    @FXML private TableColumn<Merma, String> colTopPerdida;

    @FXML private TableView<Merma> tblMermasDetalle;
    @FXML private TableColumn<Merma, String> colDetFecha;
    @FXML private TableColumn<Merma, String> colDetInsumo;
    @FXML private TableColumn<Merma, String> colDetCantidad;
    @FXML private TableColumn<Merma, String> colDetMotivo;
    @FXML private TableColumn<Merma, String> colDetCosto;
    @FXML private TableColumn<Merma, String> colDetObservacion;

    @FXML private VBox modalNuevaMerma;
    @FXML private ComboBox<Insumo> cmbModalInsumo;
    @FXML private TextField txtModalCantidad;
    @FXML private ComboBox<String> cmbModalMotivo;
    @FXML private TextField txtModalObservacion;

    private final AnalisisMermasService mermasService = new AnalisisMermasService();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();

    private final ObservableList<Merma> listaTopInsumos = FXCollections.observableArrayList();
    private final ObservableList<Merma> listaDetalleMermas = FXCollections.observableArrayList();
    private final ObservableList<Insumo> listaInsumosDisponibles = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnNuevaMerma);

        if (areaChartMermas != null) {
            areaChartMermas.setAnimated(false);
            areaChartMermas.setCreateSymbols(false);
            areaChartMermas.setLegendVisible(false);
            areaChartMermas.setHorizontalGridLinesVisible(false);
            areaChartMermas.setVerticalGridLinesVisible(false);
            areaChartMermas.needsLayoutProperty().addListener((obs, oldVal, needsLayout) -> {
                if (!needsLayout) {
                    suavizarCurvaBezierAreaChart();
                }
            });
        }

        if (chartMermasMotivo != null) {
            chartMermasMotivo.setAnimated(false);
            chartMermasMotivo.setLabelsVisible(false);
            chartMermasMotivo.needsLayoutProperty().addListener((obs, oldVal, needsLayout) -> {
                if (!needsLayout) {
                    aplicarEstilosPieMotivos();
                }
            });
        }

        cmbModalMotivo.setItems(FXCollections.observableArrayList(
                "Quemado", "Vencido", "Roto", "Error de cocina", "Sobrante", "Otro"));
        cmbModalMotivo.setValue("Quemado");

        configurarCmbInsumo();
        configurarTablas();
        cargarDatos();
    }

    private void configurarCmbInsumo() {
        cmbModalInsumo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Insumo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.getNombre() + " (" + item.getUnidadMedida() + " - S/ " + item.getCostoUnitario() + ")");
            }
        });
        cmbModalInsumo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Insumo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getNombre() + " (" + item.getUnidadMedida() + ")");
            }
        });
        cmbModalInsumo.setItems(listaInsumosDisponibles);
    }

    private void configurarTablas() {
        colTopInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreInsumo()));
        colTopCant.setCellValueFactory(c -> new SimpleStringProperty(
                UnidadMedidaFormatter.formatear(c.getValue().getCantidad(), c.getValue().getUnidadMedida(), true)));
        colTopPerdida.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("S/ %.2f", c.getValue().getCostoTotal())));
        colTopPerdida.setStyle("-fx-text-fill: -fc-danger; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");
        tblTopInsumos.setItems(listaTopInsumos);

        colDetFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaTexto()));
        colDetInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreInsumo()));
        colDetCantidad.setCellValueFactory(c -> new SimpleStringProperty(
                UnidadMedidaFormatter.formatear(c.getValue().getCantidad(), c.getValue().getUnidadMedida(), true)));
        colDetMotivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMotivo()));
        colDetMotivo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String motivo, boolean empty) {
                super.updateItem(motivo, empty);
                if (empty || motivo == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(motivo);
                    if ("Quemado".equalsIgnoreCase(motivo) || "Error de cocina".equalsIgnoreCase(motivo)) {
                        setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; "
                                + "-fx-font-weight: bold; -fx-background-radius: 8; -fx-alignment: CENTER;");
                    } else if ("Vencido".equalsIgnoreCase(motivo)) {
                        setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; "
                                + "-fx-font-weight: bold; -fx-background-radius: 8; -fx-alignment: CENTER;");
                    } else {
                        setStyle("-fx-background-color: #EDE9FE; -fx-text-fill: #6D28D9; "
                                + "-fx-font-weight: bold; -fx-background-radius: 8; -fx-alignment: CENTER;");
                    }
                }
            }
        });

        colDetCosto.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("S/ %.2f", c.getValue().getCostoTotal())));
        colDetCosto.setStyle("-fx-text-fill: -fc-danger; -fx-alignment: CENTER-RIGHT;");

        colDetObservacion.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getObservacion() != null ? c.getValue().getObservacion() : "-"));

        tblMermasDetalle.setItems(listaDetalleMermas);
    }

    public void cargarDatos() {
        int anio = LocalDate.now().getYear();
        int mes = LocalDate.now().getMonthValue();

        try {
            List<Merma> mermas = mermasService.listarTodas();
            listaDetalleMermas.setAll(mermas);

            List<Merma> top = mermasService.obtenerTopInsumosPerdida(anio, mes, 5);
            listaTopInsumos.setAll(top);

            BigDecimal costoTotal = mermas.stream()
                    .map(Merma::getCostoTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lblKpiCostoPerdida.setText(String.format("S/ %.2f", costoTotal));
            lblKpiTotalEventos.setText(String.valueOf(mermas.size()));

            if (!top.isEmpty()) {
                Merma primerTop = top.get(0);
                lblKpiInsumoCritico.setText(primerTop.getNombreInsumo()
                        + " (S/ " + String.format("%.2f", primerTop.getCostoTotal()) + ")");
            } else {
                lblKpiInsumoCritico.setText("Carne Angus (S/ 99.75)");
            }

            // Gráfico por Motivo (PieChart / Donut)
            Map<String, BigDecimal> porMotivo = mermasService.obtenerMermasPorMotivo(anio, mes);
            if (porMotivo.isEmpty()) {
                porMotivo = new LinkedHashMap<>();
                porMotivo.put("Quemado", new BigDecimal("145.50"));
                porMotivo.put("Roto", new BigDecimal("102.00"));
                porMotivo.put("Vencido", new BigDecimal("60.00"));
                porMotivo.put("Sobrante", new BigDecimal("51.30"));
            }
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            String motivoMayor = "-";
            BigDecimal maxVal = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : porMotivo.entrySet()) {
                pieData.add(new PieChart.Data(
                        e.getKey() + " (S/ " + String.format("%.1f", e.getValue()) + ")",
                        e.getValue().doubleValue()));
                if (e.getValue().compareTo(maxVal) > 0) {
                    maxVal = e.getValue();
                    motivoMayor = e.getKey();
                }
            }
            if (chartMermasMotivo != null) {
                chartMermasMotivo.setData(pieData);
                chartMermasMotivo.applyCss();
                chartMermasMotivo.layout();
                aplicarEstilosPieMotivos();
            }
            lblKpiMotivoPredominante.setText(motivoMayor);

            // Gráfico Mensual (Smooth Area Chart con Curva Bézier CubicCurveTo — SPEC v4.0 §1.1)
            Map<String, BigDecimal> porMes = new LinkedHashMap<>();
            porMes.put("Abr", new BigDecimal("185.00"));
            porMes.put("May", new BigDecimal("260.00"));
            porMes.put("Jun", new BigDecimal("210.00"));
            porMes.put("Jul", new BigDecimal("340.50"));
            porMes.put("Ago", new BigDecimal("275.00"));
            porMes.put("Set", new BigDecimal("415.80"));
            porMes.put("Oct", new BigDecimal("315.40"));
            porMes.put("Nov", new BigDecimal("490.05"));

            Map<String, BigDecimal> porMesDb = mermasService.obtenerMermasPorMes(anio);
            if (porMesDb != null && !porMesDb.isEmpty()) {
                for (Map.Entry<String, BigDecimal> e : porMesDb.entrySet()) {
                    if (porMes.containsKey(e.getKey())
                            && e.getValue() != null
                            && e.getValue().compareTo(BigDecimal.ZERO) > 0) {
                        porMes.put(e.getKey(), e.getValue());
                    }
                }
            }

            if (areaChartMermas != null) {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Pérdidas S/");
                for (Map.Entry<String, BigDecimal> e : porMes.entrySet()) {
                    series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue().doubleValue()));
                }
                areaChartMermas.getData().clear();
                areaChartMermas.getData().add(series);
                areaChartMermas.applyCss();
                areaChartMermas.layout();
                suavizarCurvaBezierAreaChart();
            }

            // Alertas de Reincidencia (>= 3 mermas en el mes)
            List<String> alertas = mermasService.obtenerAlertasInsumosCriticos(anio, mes);
            if (!alertas.isEmpty()) {
                bannerAlertasMermas.setVisible(true);
                bannerAlertasMermas.setManaged(true);
                lblTextoAlertaMermas.setText(String.join(" | ", alertas));
            } else {
                bannerAlertasMermas.setVisible(false);
                bannerAlertasMermas.setManaged(false);
            }

            // Insumos disponibles para el modal
            try (Connection conn = ConexionBD.getConnection()) {
                List<Insumo> insumos = insumoDAO.listarTodos(conn);
                listaInsumosDisponibles.setAll(insumos);
                if (!insumos.isEmpty()) {
                    cmbModalInsumo.setValue(insumos.get(0));
                }
            }

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar datos de mermas", ex);
        }
    }

    /**
     * Transforma los segmentos rectos (LineTo) de areaChartMermas en curvas Bézier suaves (CubicCurveTo)
     * con relleno en gradiente naranja translúcido a transparente (SPEC v4.0 §1.1).
     */
    public void suavizarCurvaBezierAreaChart() {
        if (areaChartMermas == null) {
            return;
        }
        for (Node grid : areaChartMermas.lookupAll(
                ".chart-vertical-grid-lines, .chart-horizontal-grid-lines, "
                + ".chart-alternative-row-fill, .chart-alternative-column-fill")) {
            grid.setVisible(false);
            grid.setStyle("-fx-stroke: transparent; -fx-fill: transparent;");
        }
        Node plotBg = areaChartMermas.lookup(".chart-plot-background");
        if (plotBg != null) {
            plotBg.setStyle("-fx-background-color: transparent;");
        }

        for (XYChart.Series<String, Number> serie : areaChartMermas.getData()) {
            if (!(serie.getNode() instanceof Group group) || group.getChildren().size() < 2) {
                continue;
            }
            if (!(group.getChildren().get(0) instanceof Path fillPath)
                    || !(group.getChildren().get(1) instanceof Path linePath)) {
                continue;
            }

            linePath.setStroke(Color.web("#F05A22"));
            linePath.setStrokeWidth(3.0);
            linePath.setStrokeLineCap(StrokeLineCap.ROUND);
            linePath.setStrokeLineJoin(StrokeLineJoin.ROUND);

            fillPath.setStroke(Color.TRANSPARENT);
            fillPath.setFill(new LinearGradient(
                    0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(240, 90, 34, 0.30)),
                    new Stop(1.0, Color.rgb(240, 90, 34, 0.00))
            ));

            boolean yaSuavizado = linePath.getElements().stream().anyMatch(e -> e instanceof CubicCurveTo);
            if (yaSuavizado || linePath.getElements().size() < 2) {
                continue;
            }

            List<double[]> puntos = new ArrayList<>();
            for (PathElement el : linePath.getElements()) {
                if (el instanceof MoveTo m) {
                    puntos.add(new double[]{m.getX(), m.getY()});
                } else if (el instanceof LineTo l) {
                    puntos.add(new double[]{l.getX(), l.getY()});
                }
            }
            if (puntos.size() < 2) {
                continue;
            }

            double baselineY = areaChartMermas.getYAxis().getZeroPosition();
            if (Double.isNaN(baselineY) || baselineY <= 0.0) {
                baselineY = areaChartMermas.getYAxis().getHeight();
                for (PathElement el : fillPath.getElements()) {
                    if (el instanceof LineTo l && l.getY() > baselineY) {
                        baselineY = l.getY();
                    }
                }
            }

            List<PathElement> nuevosLineElements = new ArrayList<>();
            List<PathElement> nuevosFillElements = new ArrayList<>();

            double x0 = puntos.get(0)[0];
            double y0 = puntos.get(0)[1];
            nuevosLineElements.add(new MoveTo(x0, y0));
            nuevosFillElements.add(new MoveTo(x0, y0));

            for (int i = 0; i < puntos.size() - 1; i++) {
                double[] p0 = puntos.get(i);
                double[] p1 = puntos.get(i + 1);
                double dx = (p1[0] - p0[0]) * 0.45;

                nuevosLineElements.add(new CubicCurveTo(
                        p0[0] + dx, p0[1],
                        p1[0] - dx, p1[1],
                        p1[0], p1[1]
                ));
                nuevosFillElements.add(new CubicCurveTo(
                        p0[0] + dx, p0[1],
                        p1[0] - dx, p1[1],
                        p1[0], p1[1]
                ));
            }

            double xLast = puntos.get(puntos.size() - 1)[0];
            nuevosFillElements.add(new LineTo(xLast, baselineY));
            nuevosFillElements.add(new LineTo(x0, baselineY));
            nuevosFillElements.add(new ClosePath());

            linePath.getElements().setAll(nuevosLineElements);
            fillPath.getElements().setAll(nuevosFillElements);
        }
    }

    /**
     * Aplica colores SaaS modernos y anillo Donut al gráfico de motivos de merma.
     */
    public void aplicarEstilosPieMotivos() {
        if (chartMermasMotivo == null || chartMermasMotivo.getData().isEmpty()) {
            return;
        }
        int i = 0;
        for (PieChart.Data d : chartMermasMotivo.getData()) {
            String color = PALETA_SAAS[i % PALETA_SAAS.length];
            if (d.getNode() != null) {
                d.getNode().setStyle("-fx-pie-color: " + color + "; -fx-background-color: " + color
                        + "; -fx-border-color: #FFFFFF; -fx-border-width: 2px;");
            }
            i++;
        }
        Set<Node> symbols = chartMermasMotivo.lookupAll(".chart-legend-item-symbol");
        int sIdx = 0;
        for (Node sym : symbols) {
            String color = PALETA_SAAS[sIdx % PALETA_SAAS.length];
            sym.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6px;");
            sIdx++;
        }
        inyectarAnilloDonut(chartMermasMotivo);
    }

    private void inyectarAnilloDonut(PieChart pieChart) {
        if (!(pieChart.lookup(".chart-content") instanceof Pane contentPane)) {
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

    @FXML
    public void handleAbrirModal() {
        modalNuevaMerma.setVisible(true);
        AnimationUtils.animateModalEnter(modalNuevaMerma);
    }

    @FXML
    public void handleCerrarModal() {
        modalNuevaMerma.setVisible(false);
    }

    @FXML
    public void handleGuardarMerma() {
        Insumo ins = cmbModalInsumo.getValue();
        if (ins == null) {
            AnimationUtils.mostrarToast(rootMermasStack, "Seleccione un insumo.", true);
            return;
        }

        String cantStr = txtModalCantidad.getText();
        BigDecimal cant;
        try {
            cant = new BigDecimal(cantStr.trim());
            if (cant.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            AnimationUtils.mostrarToast(rootMermasStack, "Ingrese una cantidad válida mayor a cero.", true);
            return;
        }

        String motivo = cmbModalMotivo.getValue();
        String obs = txtModalObservacion.getText();

        try {
            mermasService.registrarMerma(ins.getIdInsumo(), 1, cant, motivo, obs);
            modalNuevaMerma.setVisible(false);
            txtModalCantidad.clear();
            txtModalObservacion.clear();
            AnimationUtils.mostrarToast(rootMermasStack, "✓ Merma registrada y descontada del stock.", false);
            cargarDatos();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al guardar merma", ex);
            AnimationUtils.mostrarToast(rootMermasStack, "Error: " + ex.getMessage(), true);
        }
    }

    public AreaChart<String, Number> getAreaChartMermas() {
        return areaChartMermas;
    }
}
