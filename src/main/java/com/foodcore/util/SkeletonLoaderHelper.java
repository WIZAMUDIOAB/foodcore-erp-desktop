package com.foodcore.util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Helper visual para renderizar skeletons animados (shimmer) en JavaFX mientras
 * se completan cargas asíncronas de tablas, tarjetas o paneles (Sub-Fase D - D3).
 */
public final class SkeletonLoaderHelper {

    private static final String SHIMMER_KEY = "fc_skeleton_shimmer_timeline";
    private static final String OVERLAY_TAG = "fc_skeleton_container";
    private static final double DEFAULT_SHIMMER_MS = 1500.0;

    private SkeletonLoaderHelper() {
    }

    /**
     * Construye un contenedor vertical con filas shimmer animadas.
     *
     * @param filas número de filas placeholder a renderizar (mínimo 1)
     * @param columnas número de bloques horizontales por fila (mínimo 1)
     * @return VBox con clase CSS {@code skeleton-container} y filas animadas
     */
    public static VBox crearFilasSkeleton(int filas, int columnas) {
        int filasSeguras = Math.max(1, filas);
        int colsSeguras = Math.max(1, columnas);

        VBox contenedor = new VBox(10);
        contenedor.getStyleClass().add("skeleton-container");
        contenedor.setPadding(new Insets(12));
        contenedor.getProperties().put(OVERLAY_TAG, Boolean.TRUE);

        for (int i = 0; i < filasSeguras; i++) {
            HBox fila = crearFilaShimmer(colsSeguras, i);
            contenedor.getChildren().add(fila);
        }
        return contenedor;
    }

    /**
     * Construye una fila shimmer individual con efecto de pulso luminoso continuo.
     *
     * @param columnas cantidad de celdas/barras horizontales en la fila
     * @return HBox con clase CSS {@code skeleton-row} y bloques {@code skeleton-bar}
     */
    public static HBox crearFilaShimmer(int columnas) {
        return crearFilaShimmer(columnas, 0);
    }

    private static HBox crearFilaShimmer(int columnas, int filaIndex) {
        int colsSeguras = Math.max(1, columnas);
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("skeleton-row");
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setMinHeight(44);

        for (int c = 0; c < colsSeguras; c++) {
            Region bar = new Region();
            bar.getStyleClass().addAll("skeleton-bar", "skeleton-shimmer");
            bar.setMinHeight(14);
            bar.setPrefHeight(16);
            bar.setMaxWidth(Double.MAX_VALUE);
            if (c == 0) {
                bar.setPrefWidth(180);
                HBox.setHgrow(bar, Priority.ALWAYS);
            } else if (c == colsSeguras - 1) {
                bar.setPrefWidth(88);
            } else {
                bar.setPrefWidth(120);
                HBox.setHgrow(bar, Priority.SOMETIMES);
            }
            iniciarAnimacionShimmer(bar, (filaIndex * 90) + (c * 45));
            row.getChildren().add(bar);
        }
        return row;
    }

    /**
     * Aplica un skeleton shimmer dentro de {@code contenedor} y lo oculta automáticamente
     * al cumplirse {@code duracion}. Si ya existían nodos previos en el contenedor, se conservan ocultos
     * temporalmente y se restauran cuando finaliza la carga simulada o real.
     *
     * @param contenedor panel anfitrión JavaFX
     * @param filas número de filas placeholder
     * @param duracion tiempo tras el cual se restaura el contenido (si es null o <= 0 queda hasta llamar {@link #ocultarSkeleton(Pane)})
     * @return VBox del skeleton insertado (o null si contenedor es null)
     */
    public static VBox aplicarSkeletonTemporal(Pane contenedor, int filas, Duration duracion) {
        if (contenedor == null) {
            return null;
        }
        List<Node> originales = List.copyOf(contenedor.getChildren());
        for (Node n : originales) {
            if (!Boolean.TRUE.equals(n.getProperties().get(OVERLAY_TAG))) {
                n.setVisible(false);
                n.setManaged(false);
            }
        }
        VBox skeleton = mostrarSkeleton(contenedor, filas, 3);
        if (duracion != null && duracion.greaterThan(Duration.ZERO)) {
            PauseTransition wait = new PauseTransition(duracion);
            wait.setOnFinished(e -> {
                ocultarSkeleton(contenedor);
                for (Node n : originales) {
                    n.setVisible(true);
                    n.setManaged(true);
                }
            });
            wait.play();
        }
        return skeleton;
    }

    /**
     * Inserta un bloque de filas shimmer en {@code contenedor}.
     */
    public static VBox mostrarSkeleton(Pane contenedor, int filas, int columnas) {
        if (contenedor == null) {
            return null;
        }
        ocultarSkeleton(contenedor);
        VBox skeleton = crearFilasSkeleton(filas, columnas);
        contenedor.getChildren().add(0, skeleton);
        return skeleton;
    }

    /**
     * Detiene animaciones activas y retira cualquier skeleton presente en {@code contenedor}.
     */
    public static void ocultarSkeleton(Pane contenedor) {
        if (contenedor == null) {
            return;
        }
        contenedor.getChildren().removeIf(node -> {
            if (Boolean.TRUE.equals(node.getProperties().get(OVERLAY_TAG))) {
                detenerShimmerRecursivo(node);
                return true;
            }
            return false;
        });
    }

    private static void iniciarAnimacionShimmer(Node target, int delayMs) {
        Timeline shimmer = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(target.opacityProperty(), 0.42)),
                new KeyFrame(Duration.millis(DEFAULT_SHIMMER_MS * 0.5), new KeyValue(target.opacityProperty(), 0.95)),
                new KeyFrame(Duration.millis(DEFAULT_SHIMMER_MS), new KeyValue(target.opacityProperty(), 0.42))
        );
        shimmer.setCycleCount(Animation.INDEFINITE);
        if (delayMs > 0) {
            shimmer.setDelay(Duration.millis(delayMs % 350));
        }
        target.getProperties().put(SHIMMER_KEY, shimmer);
        shimmer.play();
    }

    private static void detenerShimmerRecursivo(Node node) {
        if (node == null) {
            return;
        }
        Object anim = node.getProperties().get(SHIMMER_KEY);
        if (anim instanceof Timeline timeline) {
            timeline.stop();
            node.getProperties().remove(SHIMMER_KEY);
        }
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                detenerShimmerRecursivo(child);
            }
        }
    }
}
