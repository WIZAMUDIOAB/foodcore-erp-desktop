package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.config.AppContainer;
import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.ResumenDashboard;
import com.foodcore.model.ResumenSeguridadHoy;
import com.foodcore.service.ReporteService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.FormatoBiUiHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador del Dashboard Gerencial y Reportes ejecutivos según SPEC-FOODCORE-v3.0.3 y v4.4.0:
 * KPIs del día, Smooth Area Chart con curva Bézier (CubicCurveTo), rentabilidad por receta,
 * reporte por mozo/turno y widget de alertas de vencimiento de personal (próximos 30 días).
 */
public class DashboardController {

    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record FilaRentabilidad(String platillo, String costoReceta, String precioVenta, String margenPorcentaje, BigDecimal margenNum) {
    }

    public record FilaVentasMozo(String usuario, String ventasTotales, String nroPedidos, String ticketPromedio) {
    }

    public record AlertaVencimiento(String colaborador, String cargo, String documento, LocalDate fechaVencimiento, long diasRestantes) {
    }

    @FXML private StackPane rootGerencialStack;
    @FXML private Button btnActualizarDashboard;
    @FXML private Button btnExpandirVentana;

    @FXML private Label lblKpiVentasDia;
    @FXML private Label lblVarHoyVsAyer;
    @FXML private Label lblDetalleAyer;
    @FXML private Label lblKpiTicketPromedio;
    @FXML private Label lblVarMesVsAnteriorMini;
    @FXML private Label lblKpiTransacciones;
    @FXML private Label lblKpiPlatoTop;
    @FXML private Label lblKpiPlatoTopDetalle;

    @FXML private Label lblDashCompHoyBadge;
    @FXML private Label lblDashCompHoyMonto;
    @FXML private Label lblDashCompHoyDetalle;
    @FXML private Label lblDashCompMesBadge;
    @FXML private Label lblDashCompMesMonto;
    @FXML private Label lblDashCompMesDetalle;

    @FXML private AreaChart<String, Number> areaChartVentas;

    @FXML private TableView<FilaVentasMozo> tblVentasMozo;
    @FXML private TableColumn<FilaVentasMozo, String> colMozoUsuario;
    @FXML private TableColumn<FilaVentasMozo, String> colMozoVentas;
    @FXML private TableColumn<FilaVentasMozo, String> colMozoPedidos;
    @FXML private TableColumn<FilaVentasMozo, String> colMozoTicket;

    @FXML private TableView<FilaRentabilidad> tblRentabilidad;
    @FXML private TableColumn<FilaRentabilidad, String> colRentPlatillo;
    @FXML private TableColumn<FilaRentabilidad, String> colRentCosto;
    @FXML private TableColumn<FilaRentabilidad, String> colRentPrecio;
    @FXML private TableColumn<FilaRentabilidad, String> colRentMargen;

    @FXML private VBox vboxAlertasVencimiento;
    @FXML private Label lblCountAlertasPersonal;
    @FXML private VBox cardAlertasSeguridad;
    @FXML private Label lblSecEstadoBadge;
    @FXML private Label lblSecIntentosFallidos;
    @FXML private Label lblSecAnulacionesHoy;
    @FXML private Label lblSecUltimoSospechoso;
    @FXML private Label lblSecUltimoSospechosoDetalle;

    private final ObservableList<FilaRentabilidad> itemsRentabilidad = FXCollections.observableArrayList();
    private final ObservableList<FilaVentasMozo> itemsVentasMozo = FXCollections.observableArrayList();
    private final List<AlertaVencimiento> alertasVencimientoActuales = new ArrayList<>();
    private final ReporteService reporteService = new ReporteService();
    private ComparativaPeriodos ultimaComparativa;
    private ResumenSeguridadHoy ultimoResumenSeguridad = ResumenSeguridadHoy.normal();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnActualizarDashboard);
        if (btnExpandirVentana != null) {
            AnimationUtils.attachTactileFeedback(btnExpandirVentana);
        }
        if (areaChartVentas != null) {
            areaChartVentas.setAnimated(false);
            areaChartVentas.setCreateSymbols(false);
            areaChartVentas.setLegendVisible(false);
            areaChartVentas.setHorizontalGridLinesVisible(false);
            areaChartVentas.setVerticalGridLinesVisible(false);
            areaChartVentas.needsLayoutProperty().addListener((obs, oldVal, needsLayout) -> {
                if (!needsLayout) {
                    suavizarCurvaBezierAreaChart();
                }
            });
        }
        configurarTablas();
        cargarMetricasDashboard();
    }

    /**
     * Abre el Dashboard en una ventana independiente maximizada (Pop-Out para monitor secundario §5).
     */
    @FXML
    public void handleExpandirVentana(ActionEvent event) {
        try {
            FXMLLoader loader = AppContainer.getDefault().createLoader(
                    getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1280, 768);
            App.applyStylesheets(scene);

            Stage newStage = new Stage();
            newStage.initStyle(StageStyle.DECORATED);
            newStage.setTitle("FoodCore ERP — Dashboard Gerencial (Monitor Secundario)");
            newStage.setMaximized(true);
            newStage.setScene(scene);
            newStage.setOnCloseRequest(e -> {
                newStage.close();
                newStage.setScene(null);
            });
            newStage.show();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo expandir DashboardView en nueva ventana", ex);
        }
    }

    /**
     * Transforma los segmentos rectos (LineTo) del AreaChart en curvas Bézier suaves (CubicCurveTo)
     * y aplica el gradiente naranja institucional sin líneas de cuadrícula (SPEC v3.0.3 §3).
     */
    public void suavizarCurvaBezierAreaChart() {
        if (areaChartVentas == null) {
            return;
        }
        for (Node grid : areaChartVentas.lookupAll(
                ".chart-vertical-grid-lines, .chart-horizontal-grid-lines, .chart-alternative-row-fill, .chart-alternative-column-fill")) {
            grid.setVisible(false);
            grid.setStyle("-fx-stroke: transparent; -fx-fill: transparent;");
        }
        Node plotBg = areaChartVentas.lookup(".chart-plot-background");
        if (plotBg != null) {
            plotBg.setStyle("-fx-background-color: transparent;");
        }

        for (XYChart.Series<String, Number> serie : areaChartVentas.getData()) {
            if (!(serie.getNode() instanceof Group group) || group.getChildren().size() < 2) {
                continue;
            }
            if (!(group.getChildren().get(0) instanceof Path fillPath)
                    || !(group.getChildren().get(1) instanceof Path linePath)) {
                continue;
            }

            boolean oscuro = App.isDarkModeActivo();
            linePath.setStroke(Color.web(oscuro ? "#F97316" : "#EA580C"));
            linePath.setStrokeWidth(3.0);
            linePath.setStrokeLineCap(StrokeLineCap.ROUND);
            linePath.setStrokeLineJoin(StrokeLineJoin.ROUND);

            fillPath.setStroke(Color.TRANSPARENT);
            fillPath.setFill(new LinearGradient(
                    0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, oscuro ? Color.rgb(249, 115, 22, 0.20) : Color.rgb(234, 88, 12, 0.20)),
                    new Stop(1.0, oscuro ? Color.rgb(249, 115, 22, 0.00) : Color.rgb(234, 88, 12, 0.00))
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

            double baselineY = areaChartVentas.getYAxis().getZeroPosition();
            if (Double.isNaN(baselineY) || baselineY <= 0.0) {
                baselineY = areaChartVentas.getYAxis().getHeight();
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

                CubicCurveTo bezierLine = new CubicCurveTo(
                        p0[0] + dx, p0[1],
                        p1[0] - dx, p1[1],
                        p1[0], p1[1]
                );
                CubicCurveTo bezierFill = new CubicCurveTo(
                        p0[0] + dx, p0[1],
                        p1[0] - dx, p1[1],
                        p1[0], p1[1]
                );
                nuevosLineElements.add(bezierLine);
                nuevosFillElements.add(bezierFill);
            }

            double xLast = puntos.get(puntos.size() - 1)[0];
            nuevosFillElements.add(new LineTo(xLast, baselineY));
            nuevosFillElements.add(new LineTo(x0, baselineY));
            nuevosFillElements.add(new ClosePath());

            linePath.getElements().setAll(nuevosLineElements);
            fillPath.getElements().setAll(nuevosFillElements);
        }
    }

    private void configurarTablas() {
        colMozoUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().usuario()));
        colMozoVentas.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().ventasTotales()));
        colMozoPedidos.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nroPedidos()));
        colMozoTicket.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().ticketPromedio()));

        colMozoVentas.setCellFactory(col -> crearCeldaMono());
        colMozoPedidos.setCellFactory(col -> crearCeldaMono());
        colMozoTicket.setCellFactory(col -> crearCeldaMono());

        colRentPlatillo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().platillo()));
        colRentCosto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().costoReceta()));
        colRentPrecio.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().precioVenta()));
        colRentMargen.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().margenPorcentaje()));

        colRentCosto.setCellFactory(col -> crearCeldaMono());
        colRentPrecio.setCellFactory(col -> crearCeldaMono());
        colRentMargen.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    FilaRentabilidad row = getTableRow() != null ? getTableRow().getItem() : null;
                    BigDecimal m = row != null && row.margenNum() != null ? row.margenNum() : new BigDecimal("50");
                    if (m.compareTo(new BigDecimal("50")) >= 0) {
                        pill.getStyleClass().add("badge-pill-success");
                    } else if (m.compareTo(new BigDecimal("25")) >= 0) {
                        pill.getStyleClass().add("badge-pill-warning");
                    } else {
                        pill.getStyleClass().add("badge-pill-danger");
                    }
                    setGraphic(pill);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        tblVentasMozo.setItems(itemsVentasMozo);
        tblRentabilidad.setItems(itemsRentabilidad);
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

    @FXML
    public void handleActualizarDashboard(ActionEvent event) {
        cargarMetricasDashboard();
    }

    public void cargarMetricasDashboard() {
        UiAsyncExecutor.ejecutarVoidAsync(this::cargarMetricasDashboardSincrono, null, null);
    }

    public void cargarMetricasDashboardSincrono() {
        ResumenDashboard res = reporteService.consultarResumenDashboard(LocalDate.now(ZONA_LIMA));
        ComparativaPeriodos comparativa = res.comparativa() != null
                ? res.comparativa() : comparativaSinDatos(LocalDate.now(ZONA_LIMA));
        BigDecimal ventasDia = res.ventasDia() != null ? res.ventasDia() : BigDecimal.ZERO;
        BigDecimal ticketProm = res.ticketPromedio() != null ? res.ticketPromedio() : BigDecimal.ZERO;
        String platoTop = res.platoTop();
        int platoTopCant = res.platoTopUnidades();
        Map<String, BigDecimal> ventasPorHora = res.ventasPorHora();
        List<FilaVentasMozo> filasMozo = mapearFilasMozo(res.filasMozo());
        List<FilaRentabilidad> filasRent = mapearFilasRentabilidad(res.filasRentabilidad());
        List<AlertaVencimiento> alertasPersonal = mapearAlertasVencimiento(res.alertasVencimiento());
        ResumenSeguridadHoy seguridadHoy = res.seguridadHoy() != null
                ? res.seguridadHoy() : ResumenSeguridadHoy.normal();

        final ResumenSeguridadHoy seguridadFinal = seguridadHoy;
        final ComparativaPeriodos compFinal = comparativa;
        final BigDecimal vDiaFinal = ventasDia;
        final int txFinal = res.numTransacciones();
        final BigDecimal tPromFinal = ticketProm;
        final String pTopFinal = platoTop;
        final int pCantFinal = platoTopCant;
        final Map<String, BigDecimal> horaFinal = ventasPorHora;
        final List<FilaVentasMozo> mozoFinal = filasMozo;
        final List<FilaRentabilidad> rentFinal = filasRent;
        final List<AlertaVencimiento> alertasFinal = alertasPersonal;

        Runnable updateUI = () -> {
            this.ultimaComparativa = compFinal;
            lblKpiVentasDia.setText("S/ " + vDiaFinal);
            lblKpiTicketPromedio.setText("S/ " + tPromFinal);
            lblKpiTransacciones.setText(String.valueOf(txFinal));
            lblKpiPlatoTop.setText(pTopFinal);
            lblKpiPlatoTopDetalle.setText(pCantFinal + " unidades despachadas");

            FormatoBiUiHelper.aplicarIndicadorComparativa(lblVarHoyVsAyer, compFinal.variacionDiariaPct());
            if (lblDetalleAyer != null) {
                lblDetalleAyer.setText("vs ayer (S/ " + compFinal.ventasAyer() + ")");
            }
            FormatoBiUiHelper.aplicarIndicadorComparativa(lblVarMesVsAnteriorMini, compFinal.variacionMensualPct());

            FormatoBiUiHelper.aplicarIndicadorComparativa(lblDashCompHoyBadge, compFinal.variacionDiariaPct());
            if (lblDashCompHoyMonto != null) {
                lblDashCompHoyMonto.setText("S/ " + compFinal.ventasHoy());
            }
            if (lblDashCompHoyDetalle != null) {
                lblDashCompHoyDetalle.setText(
                        "Hoy: S/ " + compFinal.ventasHoy() + " · Ayer: S/ " + compFinal.ventasAyer()
                );
            }

            FormatoBiUiHelper.aplicarIndicadorComparativa(lblDashCompMesBadge, compFinal.variacionMensualPct());
            if (lblDashCompMesMonto != null) {
                lblDashCompMesMonto.setText("S/ " + compFinal.ventasMesActual());
            }
            if (lblDashCompMesDetalle != null) {
                lblDashCompMesDetalle.setText(
                        "Este mes: S/ " + compFinal.ventasMesActual() + " · Mes anterior: S/ " + compFinal.ventasMesAnterior()
                );
            }

            if (areaChartVentas != null) {
                areaChartVentas.getData().clear();
                XYChart.Series<String, Number> serie = new XYChart.Series<>();
                serie.setName("Ventas por Hora");
                for (Map.Entry<String, BigDecimal> entry : horaFinal.entrySet()) {
                    serie.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
                }
                areaChartVentas.getData().add(serie);
                areaChartVentas.applyCss();
                areaChartVentas.layout();
                suavizarCurvaBezierAreaChart();
            }

            itemsVentasMozo.setAll(mozoFinal);
            itemsRentabilidad.setAll(rentFinal);
            renderizarAlertasVencimiento(alertasFinal);
            renderizarAlertasSeguridad(seguridadFinal);
        };

        if (Platform.isFxApplicationThread()) {
            updateUI.run();
        } else {
            Platform.runLater(updateUI);
        }
    }

    /**
     * Consulta colaboradores activos cuyo contrato o carné de sanidad venza en los próximos 30 días (v4.4.0 §4).
     * Delega en {@link ReporteService}, que abre su propia conexión del pool HikariCP.
     *
     * @return Alertas de vencimiento ordenadas por cercanía.
     */
    public List<AlertaVencimiento> obtenerAlertasVencimiento30Dias() {
        return mapearAlertasVencimiento(reporteService.consultarAlertasVencimiento30Dias());
    }

    private List<AlertaVencimiento> mapearAlertasVencimiento(List<com.foodcore.model.AlertaVencimiento> alertas) {
        List<AlertaVencimiento> resultado = new ArrayList<>();
        if (alertas == null) {
            return resultado;
        }
        for (com.foodcore.model.AlertaVencimiento a : alertas) {
            resultado.add(new AlertaVencimiento(
                    a.colaborador(),
                    a.cargo(),
                    a.documento(),
                    a.fechaVencimiento(),
                    a.diasRestantes()
            ));
        }
        return resultado;
    }

    private List<FilaVentasMozo> mapearFilasMozo(List<com.foodcore.model.FilaVentasMozo> filas) {
        List<FilaVentasMozo> resultado = new ArrayList<>();
        if (filas == null) {
            return resultado;
        }
        for (com.foodcore.model.FilaVentasMozo f : filas) {
            resultado.add(new FilaVentasMozo(
                    f.usuario(),
                    f.ventasTotales(),
                    f.nroPedidos(),
                    f.ticketPromedio()
            ));
        }
        return resultado;
    }

    private List<FilaRentabilidad> mapearFilasRentabilidad(List<com.foodcore.model.FilaRentabilidad> filas) {
        List<FilaRentabilidad> resultado = new ArrayList<>();
        if (filas == null) {
            return resultado;
        }
        for (com.foodcore.model.FilaRentabilidad f : filas) {
            resultado.add(new FilaRentabilidad(
                    f.platillo(),
                    f.costoReceta(),
                    f.precioVenta(),
                    f.margenPorcentaje(),
                    f.margenNum()
            ));
        }
        return resultado;
    }

    private ComparativaPeriodos comparativaSinDatos(LocalDate fecha) {
        return new ComparativaPeriodos(
                fecha,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
        );
    }

    private void renderizarAlertasVencimiento(List<AlertaVencimiento> alertas) {
        alertasVencimientoActuales.clear();
        if (alertas != null) {
            alertasVencimientoActuales.addAll(alertas);
        }
        if (lblCountAlertasPersonal != null) {
            lblCountAlertasPersonal.setText(alertasVencimientoActuales.size() + " alertas");
        }
        if (vboxAlertasVencimiento == null) {
            return;
        }
        vboxAlertasVencimiento.getChildren().clear();

        if (alertasVencimientoActuales.isEmpty()) {
            Label lblOk = new Label("✓ Todo el personal tiene contratos y carnés de sanidad vigentes (> 30 días).");
            lblOk.getStyleClass().add("text-caption");
            vboxAlertasVencimiento.getChildren().add(lblOk);
            return;
        }

        for (AlertaVencimiento a : alertasVencimientoActuales) {
            HBox fila = new HBox(12.0);
            fila.setAlignment(Pos.CENTER_LEFT);
            boolean urgente = a.diasRestantes() <= 15;
            fila.getStyleClass().add(urgente ? "alert-expiry-urgent" : "alert-expiry-soon");

            Label lblDoc = new Label(a.documento());
            lblDoc.getStyleClass().add(urgente ? "badge-pill-danger" : "badge-pill-warning");

            VBox datos = new VBox(2.0);
            Label lblColab = new Label(a.colaborador() + " · " + a.cargo());
            lblColab.getStyleClass().add("text-body-strong");
            Label lblFecha = new Label("Vence el " + a.fechaVencimiento().format(FMT_FECHA));
            lblFecha.getStyleClass().add("text-caption");
            datos.getChildren().addAll(lblColab, lblFecha);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label lblDias = new Label(a.diasRestantes() == 0 ? "Vence HOY" : "En " + a.diasRestantes() + " días");
            lblDias.getStyleClass().add("text-mono-sm");

            fila.getChildren().addAll(lblDoc, datos, sp, lblDias);
            vboxAlertasVencimiento.getChildren().add(fila);
        }
    }

    /**
     * Renderiza la tarjeta KPI de alertas de seguridad operacional (ÍTEM 9 v5.1.0).
     *
     * @param resumen Métricas del día extraídas de {@code auditoria}.
     */
    public void renderizarAlertasSeguridad(ResumenSeguridadHoy resumen) {
        ResumenSeguridadHoy datos = resumen != null ? resumen : ResumenSeguridadHoy.normal();
        this.ultimoResumenSeguridad = datos;
        if (lblSecIntentosFallidos != null) {
            lblSecIntentosFallidos.setText(String.valueOf(datos.intentosFallidosHoy()));
        }
        if (lblSecAnulacionesHoy != null) {
            lblSecAnulacionesHoy.setText(String.valueOf(datos.anulacionesHoy()));
        }
        if (lblSecUltimoSospechoso != null) {
            lblSecUltimoSospechoso.setText(datos.ultimoAccesoSospechoso());
        }
        if (lblSecEstadoBadge != null) {
            lblSecEstadoBadge.getStyleClass().removeAll("badge-pill-success", "badge-pill-warning", "badge-pill-danger");
            if (datos.intentosFallidosHoy() > 0 || datos.anulacionesHoy() > 0) {
                lblSecEstadoBadge.getStyleClass().add("badge-pill-danger");
                lblSecEstadoBadge.setText("ATENCIÓN REQUERIDA");
            } else if (datos.requiereAtencion()) {
                lblSecEstadoBadge.getStyleClass().add("badge-pill-warning");
                lblSecEstadoBadge.setText("REVISAR BITÁCORA");
            } else {
                lblSecEstadoBadge.getStyleClass().add("badge-pill-success");
                lblSecEstadoBadge.setText("SIN ANOMALÍAS");
            }
        }
    }

    public Label getLblSecIntentosFallidos() {
        return lblSecIntentosFallidos;
    }

    public Label getLblSecAnulacionesHoy() {
        return lblSecAnulacionesHoy;
    }

    public Label getLblSecUltimoSospechoso() {
        return lblSecUltimoSospechoso;
    }

    public Label getLblSecEstadoBadge() {
        return lblSecEstadoBadge;
    }

    public ResumenSeguridadHoy getUltimoResumenSeguridad() {
        return ultimoResumenSeguridad;
    }

    public Label getLblKpiVentasDia() {
        return lblKpiVentasDia;
    }

    public AreaChart<String, Number> getAreaChartVentas() {
        return areaChartVentas;
    }

    public ObservableList<FilaRentabilidad> getItemsRentabilidad() {
        return itemsRentabilidad;
    }

    public ObservableList<FilaVentasMozo> getItemsVentasMozo() {
        return itemsVentasMozo;
    }

    public VBox getVboxAlertasVencimiento() {
        return vboxAlertasVencimiento;
    }

    public Label getLblCountAlertasPersonal() {
        return lblCountAlertasPersonal;
    }

    public List<AlertaVencimiento> getAlertasVencimientoActuales() {
        return List.copyOf(alertasVencimientoActuales);
    }

    public Label getLblVarHoyVsAyer() {
        return lblVarHoyVsAyer;
    }

    public Label getLblDashCompHoyBadge() {
        return lblDashCompHoyBadge;
    }

    public Label getLblDashCompMesBadge() {
        return lblDashCompMesBadge;
    }

    public Label getLblDashCompHoyDetalle() {
        return lblDashCompHoyDetalle;
    }

    public Label getLblDashCompMesDetalle() {
        return lblDashCompMesDetalle;
    }

    public ComparativaPeriodos getUltimaComparativa() {
        return ultimaComparativa;
    }
}
