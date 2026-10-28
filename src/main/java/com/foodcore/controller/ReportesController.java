package com.foodcore.controller;

import com.foodcore.model.ResumenKpi;
import com.foodcore.service.ReporteService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

public class ReportesController {

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
    private final NumberFormat monedaFormat = NumberFormat.getCurrencyInstance(new Locale("es", "PE"));

    @FXML
    public void initialize() {
        cmbPeriodo.getItems().addAll("Últimos 7 días", "Últimos 15 días", "Últimos 30 días", "Histórico Completo");
        cmbPeriodo.getSelectionModel().selectFirst();
        cmbPeriodo.setOnAction(e -> cargarDatos());

        cargarDatos();
    }

    private int obtenerDiasFiltro() {
        String seleccion = cmbPeriodo.getSelectionModel().getSelectedItem();
        if (seleccion == null) return 7;
        if (seleccion.contains("7")) return 7;
        if (seleccion.contains("15")) return 15;
        if (seleccion.contains("30")) return 30;
        return 0; // Histórico completo
    }

    @FXML
    public void cargarDatos() {
        int dias = obtenerDiasFiltro();

        new Thread(() -> {
            try {
                ResumenKpi kpi = reporteService.getResumenKpi(dias);
                Map<String, BigDecimal> ventas = reporteService.getVentasPeriodo(dias);
                Map<String, Integer> bajaRotacion = reporteService.getRankingPlatosBajaRotacion(6);
                Map<String, BigDecimal> metodosPago = reporteService.getVentasPorMetodoPago(dias);

                Platform.runLater(() -> {
                    // 1. Métricas KPI
                    lblTotalVentas.setText(monedaFormat.format(kpi.getTotalVentas()));
                    lblCantidadVentas.setText(String.valueOf(kpi.getCantidadTransacciones()) + " pedidos");
                    lblTicketPromedio.setText(monedaFormat.format(kpi.getTicketPromedio()));
                    lblPlatoEstrella.setText(kpi.getPlatoMasVendido());
                    lblPlatoEstrellaQty.setText(kpi.getCantidadPlatoMasVendido() + " unidades vendidas");

                    // 2. BarChart: Ventas por Día
                    barChartVentas.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Ventas (S/)");
                    if (ventas.isEmpty()) {
                        series.getData().add(new XYChart.Data<>("Sin ventas", 0));
                    } else {
                        ventas.forEach((dia, monto) -> series.getData().add(new XYChart.Data<>(dia, monto)));
                    }
                    barChartVentas.getData().add(series);

                    // 3. PieChart: Baja Rotación
                    pieChartBajaRotacion.getData().clear();
                    if (bajaRotacion.isEmpty()) {
                        pieChartBajaRotacion.getData().add(new PieChart.Data("Sin datos", 1));
                    } else {
                        bajaRotacion.forEach((plato, cant) ->
                            pieChartBajaRotacion.getData().add(new PieChart.Data(plato + " (" + cant + ")", cant))
                        );
                    }

                    // 4. PieChart: Métodos de Pago
                    pieChartMetodosPago.getData().clear();
                    if (metodosPago.isEmpty()) {
                        pieChartMetodosPago.getData().add(new PieChart.Data("Sin pagos", 1));
                    } else {
                        metodosPago.forEach((metodo, monto) ->
                            pieChartMetodosPago.getData().add(new PieChart.Data(metodo + " (S/ " + monto + ")", monto.doubleValue()))
                        );
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
