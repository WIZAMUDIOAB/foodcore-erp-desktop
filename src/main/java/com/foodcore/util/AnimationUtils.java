package com.foodcore.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Utilitario de animaciones de interfaz y micro-interacciones según
 * UI-REDESIGN-SPEC-FOODCORE-v1.1 (§7, §8, §11, §12.5).
 * Todas las transiciones operan en 150-200ms con EASE_BOTH sobre opacity, translate y scale.
 */
public final class AnimationUtils {

    public static final Interpolator CUBIC_OUT = Interpolator.EASE_BOTH;
    private static final String PULSE_KEY = "fc_kds_pulse_transition";

    private AnimationUtils() {
    }

    /**
     * Aplica feedback táctil inmediato en mouse press / release (scale 0.97 -> 1.0) en 150ms.
     */
    public static void attachTactileFeedback(Node node) {
        if (node == null) {
            return;
        }

        node.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), node);
            st.setToX(0.97);
            st.setToY(0.97);
            st.setInterpolator(Interpolator.EASE_BOTH);
            st.play();
        });

        node.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), node);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_BOTH);
            st.play();
        });
    }

    /**
     * Aplica pequeño desplazamiento vertical en hover (150ms EASE_BOTH, §8).
     */
    public static void attachHoverLift(Node node) {
        if (node == null) {
            return;
        }

        node.setOnMouseEntered(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(150), node);
            tt.setToY(-3.0);
            tt.setInterpolator(Interpolator.EASE_BOTH);
            tt.play();
        });

        node.setOnMouseExited(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(150), node);
            tt.setToY(0.0);
            tt.setInterpolator(Interpolator.EASE_BOTH);
            tt.play();
        });
    }

    /**
     * Entrada suave con desplazamiento vertical y fade (180ms EASE_BOTH, §12.5).
     */
    public static void enterWithFadeSlide(Node node, double fromY, int delayMs) {
        if (node == null) {
            return;
        }
        node.setOpacity(0.0);
        node.setTranslateY(fromY);

        FadeTransition ft = new FadeTransition(Duration.millis(180), node);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition tt = new TranslateTransition(Duration.millis(180), node);
        tt.setToY(0.0);
        tt.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        if (delayMs > 0) {
            pt.setDelay(Duration.millis(delayMs));
        }
        pt.play();
    }

    /**
     * Animación de aparición de modal (scale 0.95 -> 1.0, fade 0 -> 1 en 180ms EASE_BOTH).
     */
    public static void animateModalEnter(Node modalRoot) {
        if (modalRoot == null) {
            return;
        }
        modalRoot.setOpacity(0.0);
        modalRoot.setScaleX(0.95);
        modalRoot.setScaleY(0.95);

        FadeTransition ft = new FadeTransition(Duration.millis(180), modalRoot);
        ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition st = new ScaleTransition(Duration.millis(180), modalRoot);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_BOTH);

        new ParallelTransition(ft, st).play();
    }

    /**
     * Inicia o detiene el pulso suave para tarjetas KDS >10 min (opacidad 1 -> 0.7 -> 1 en 1.5s, §7).
     */
    public static void setSoftPulse(Node node, boolean active) {
        if (node == null) {
            return;
        }
        Object existing = node.getProperties().get(PULSE_KEY);
        if (!active) {
            if (existing instanceof FadeTransition ft) {
                ft.stop();
                node.getProperties().remove(PULSE_KEY);
            }
            node.setOpacity(1.0);
            return;
        }
        if (existing instanceof FadeTransition ft && ft.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        FadeTransition pulse = new FadeTransition(Duration.millis(750), node);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.7);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        node.getProperties().put(PULSE_KEY, pulse);
        pulse.play();
    }

    /**
     * Muestra un Toast no intrusivo en la esquina inferior derecha con auto-cierre de 4s (§11).
     */
    public static void mostrarToast(Pane hostPane, String mensaje, boolean esError) {
        if (hostPane == null || mensaje == null || mensaje.isBlank()) {
            return;
        }
        Region dot = new Region();
        dot.getStyleClass().add(esError ? "status-dot-danger" : "status-dot-online");

        Label lbl = new Label(mensaje);
        lbl.getStyleClass().add("text-caption-inverse");
        lbl.setWrapText(true);

        HBox toast = new HBox(10, dot, lbl);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().add("toast-container");
        toast.setMaxWidth(360);
        toast.setMaxHeight(Region.USE_PREF_SIZE);

        if (hostPane instanceof StackPane stack) {
            StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(toast, new Insets(0, 24, 24, 0));
            stack.getChildren().add(toast);
        } else {
            hostPane.getChildren().add(toast);
        }

        toast.setOpacity(0.0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), toast);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(Interpolator.EASE_BOTH);

        PauseTransition hold = new PauseTransition(Duration.seconds(4));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), toast);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);
        fadeOut.setOnFinished(e -> hostPane.getChildren().remove(toast));

        new SequentialTransition(fadeIn, hold, fadeOut).play();
    }
}
