package com.foodcore.controller;

import com.foodcore.model.ResumenKpi;
import com.foodcore.service.ReporteService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportesController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportesController.class);

    private static final String[] PALETA_DONUT = {
        "fc-donut-palette-0", "fc-donut-palette-1", "fc-donut-palette-2", "fc-donut-palette-3"
    };

    @FXML private ComboBox<String> cmbPeriodo;
    @FXML private Label lblTotalVentas;
    @FXML private Label lblCantidadVentas;
    @FXML private Label lblTicketPromedio;
    @FXML private Label lblPlatoEstrella;
    @FXML private Label lblPlatoEstrellaQty;

    @FXML private BarChart<String, Number> barChartVentas;
    @FXML private PieChart pieChartBajaRotacion;
    @FXML private PieChart pieChartMetodosPago;

    private final ReporteService reporteService = new ReporteService();
    private final NumberFormat monedaFormat = NumberFormat.getCurrencyInstance(Locale.of("es", "PE"));

    @FXML
    public void initialize() {
        cmbPeriodo.getItems().addAll("Últimos 7 días", "Últimos 15 días", "Últimos 30 días", "Histórico Completo");
        cmbPeriodo.getSelectionModel().selectFirst();
        cmbPeriodo.setOnAction(e -> cargarDatos());

        configurarPieChart(pieChartBajaRotacion);
        configurarPieChart(pieChartMetodosPago);

        cargarDatos();
    }

    private void configurarPieChart(PieChart chart) {
        if (chart == null) {
            return;
        }
        chart.setAnimated(false);
        chart.setLabelsVisible(false);
        chart.setLegendSide(Side.BOTTOM);
        chart.needsLayoutProperty().addListener((obs, oldVal, needsLayout) -> {
            if (!needsLayout) {
                aplicarColoresDonut(chart);
            }
        });
    }

    private void aplicarColoresDonut(PieChart chart) {
        if (chart == null || chart.getData().isEmpty()) {
            return;
        }
        int idx = 0;
        for (PieChart.Data slice : chart.getData()) {
            Node node = slice.getNode();
            if (node != null) {
                node.getStyleClass().removeAll(PALETA_DONUT);
                node.getStyleClass().add(PALETA_DONUT[idx % PALETA_DONUT.length]);
            }
            idx++;
        }
        Set<Node> symbols = chart.lookupAll(".chart-legend-item-symbol");
        int sIdx = 0;
        for (Node sym : symbols) {
            sym.getStyleClass().removeAll(PALETA_DONUT);
            sym.getStyleClass().add(PALETA_DONUT[sIdx % PALETA_DONUT.length]);
            sIdx++;
        }
    }

    private int obtenerDiasFiltro() {
        String seleccion = cmbPeriodo.getSelectionModel().getSelectedItem();
        if (seleccion == null) return 7;
        if (seleccion.contains("7")) return 7;
        if (seleccion.contains("15")) return 15;
        if (seleccion.contains("30")) return 30;
        return 0; // Histórico completo
    }

    private record DatosReportesCarga(
            ResumenKpi kpi,
            Map<String, BigDecimal> ventas,
            Map<String, Integer> bajaRotacion,
            Map<String, BigDecimal> metodosPago) {
    }

    @FXML
    public void cargarDatos() {
        int dias = obtenerDiasFiltro();
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                ResumenKpi kpi = reporteService.getResumenKpi(dias);
                Map<String, BigDecimal> ventas = reporteService.getVentasPeriodo(dias);
                Map<String, Integer> bajaRotacion = reporteService.getRankingPlatosBajaRotacion(6);
                Map<String, BigDecimal> metodosPago = reporteService.getVentasPorMetodoPago(dias);
                return new DatosReportesCarga(kpi, ventas, bajaRotacion, metodosPago);
            },
            datos -> {
                // 1. Métricas KPI
                ResumenKpi kpi = datos.kpi();
                lblTotalVentas.setText(monedaFormat.format(kpi.getTotalVentas()));
                lblCantidadVentas.setText(String.valueOf(kpi.getCantidadTransacciones()) + " pedidos");
                lblTicketPromedio.setText(monedaFormat.format(kpi.getTicketPromedio()));
                lblPlatoEstrella.setText(kpi.getPlatoMasVendido());
                lblPlatoEstrellaQty.setText(kpi.getCantidadPlatoMasVendido() + " unidades vendidas");

                // 2. BarChart: Ventas por Día
                barChartVentas.getData().clear();
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Ventas (S/)");
                if (datos.ventas().isEmpty()) {
                    series.getData().add(new XYChart.Data<>("Sin ventas", 0));
                } else {
                    datos.ventas().forEach((dia, monto) -> series.getData().add(new XYChart.Data<>(dia, monto)));
                }
                barChartVentas.getData().add(series);

                // 3. PieChart: Baja Rotación
                pieChartBajaRotacion.getData().clear();
                if (datos.bajaRotacion().isEmpty()) {
                    pieChartBajaRotacion.getData().add(new PieChart.Data("Sin datos", 1));
                } else {
                    datos.bajaRotacion().forEach((plato, cant) ->
                        pieChartBajaRotacion.getData().add(new PieChart.Data(plato + " (" + cant + ")", cant))
                    );
                }
                aplicarColoresDonut(pieChartBajaRotacion);

                // 4. PieChart: Métodos de Pago
                pieChartMetodosPago.getData().clear();
                if (datos.metodosPago().isEmpty()) {
                    pieChartMetodosPago.getData().add(new PieChart.Data("Sin pagos", 1));
                } else {
                    datos.metodosPago().forEach((metodo, monto) ->
                        pieChartMetodosPago.getData().add(new PieChart.Data(metodo + " (S/ " + monto + ")", monto.doubleValue()))
                    );
                }
                aplicarColoresDonut(pieChartMetodosPago);
            },
            e -> LOGGER.error("Error al cargar datos de reportes para {} días", dias, e)
        );
    }
}
