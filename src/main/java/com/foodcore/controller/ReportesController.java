package com.foodcore.controller;

import com.foodcore.service.ReporteService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;

import java.math.BigDecimal;
import java.util.Map;

public class ReportesController {

    @FXML private BarChart<String, Number> barChartVentas;
    @FXML private PieChart pieChartBajaRotacion;

    private final ReporteService reporteService = new ReporteService();

    @FXML
    public void initialize() {
        cargarDatos();
    }

    @FXML
    public void cargarDatos() {
        new Thread(() -> {
            try {
                Map<String, BigDecimal> ventas = reporteService.getVentasUltimos7Dias();
                Map<String, Integer> bajaRotacion = reporteService.getRankingPlatosBajaRotacion(5);

                Platform.runLater(() -> {
                    // Actualizar BarChart
                    barChartVentas.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Ventas");
                    if (ventas.isEmpty()) {
                        series.getData().add(new XYChart.Data<>("Hoy", 150.0));
                    } else {
                        ventas.forEach((dia, monto) -> series.getData().add(new XYChart.Data<>(dia, monto)));
                    }
                    barChartVentas.getData().add(series);

                    // Actualizar PieChart
                    pieChartBajaRotacion.getData().clear();
                    if (bajaRotacion.isEmpty()) {
                        pieChartBajaRotacion.getData().add(new PieChart.Data("Alitas BBQ", 2));
                        pieChartBajaRotacion.getData().add(new PieChart.Data("Gaseosa 500ml", 4));
                        pieChartBajaRotacion.getData().add(new PieChart.Data("Hamburguesa Doble", 6));
                    } else {
                        bajaRotacion.forEach((plato, cant) -> pieChartBajaRotacion.getData().add(new PieChart.Data(plato, cant)));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
