package com.foodcore.controller.reportes;

import com.foodcore.model.ReporteCohortes.CohorteMensualFila;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import java.util.Set;

public class ReporteChartUiRenderer {
    public void aplicarEstilosDonut(PieChart pieChartDistribucion, String[] paleta) {
        if (pieChartDistribucion == null || pieChartDistribucion.getData().isEmpty()) {
            return;
        }
        int i = 0;
        for (PieChart.Data data : pieChartDistribucion.getData()) {
            String color = paleta[i++ % paleta.length];
            Node node = data.getNode();
            if (node != null) {
                node.setStyle("-fx-pie-color: " + color + "; -fx-background-color: " + color
                        + "; -fx-border-color: #FFFFFF; -fx-border-width: 2px;");
            }
        }
        Set<Node> symbols = pieChartDistribucion.lookupAll(".chart-legend-item-symbol");
        int sIdx = 0;
        for (Node symbol : symbols) {
            String color = paleta[sIdx++ % paleta.length];
            symbol.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6px;");
        }
        if (!(pieChartDistribucion.lookup(".chart-content") instanceof Pane contentPane)) {
            return;
        }
        double w = contentPane.getWidth();
        double h = contentPane.getHeight();
        if (w <= 20 || h <= 20) {
            return;
        }
        Circle hole = null;
        for (Node child : contentPane.getChildren()) {
            if (child instanceof Circle circle && "donut-hole".equals(circle.getId())) {
                hole = circle;
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
        hole.setCenterX(w / 2.0);
        hole.setCenterY(h / 2.0);
        hole.setRadius(Math.min(w, h) * 0.25);
        hole.toFront();
    }

    public void configurarCeldaHeatmap(TableColumn<CohorteMensualFila, String> columna,
            ReportesAvanzadosPresenter presenter) {
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
                setStyle(presenter.resolverEstiloHeatmap(item));
            }
        });
    }
}
