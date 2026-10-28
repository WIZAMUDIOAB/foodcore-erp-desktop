package com.foodcore.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Utilitario de animaciones de interfaz y micro-interacciones de alta fidelidad
 * basadas en los principios de diseño de Emil Kowalski (animations.dev).
 * Curva base de aceleracion: cubic-bezier(0.16, 1, 0.3, 1).
 */
public final class AnimationUtils {

    public static final Interpolator CUBIC_OUT = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);

    private AnimationUtils() {}

    /**
     * Aplica feedback tactil inmediato en mouse press / release (scale 0.965).
     * Tiempo de respuesta: 90ms a 140ms.
     */
    public static void attachTactileFeedback(Node node) {
        if (node == null) return;

        node.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(90), node);
            st.setToX(0.965);
            st.setToY(0.965);
            st.setInterpolator(CUBIC_OUT);
            st.play();
        });

        node.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(CUBIC_OUT);
            st.play();
        });
    }

    /**
     * Entrada suave con desplazamiento vertical y fade con aceleracion inicial.
     * Duracion: 200ms - 220ms.
     */
    public static void enterWithFadeSlide(Node node, double fromY, int delayMs) {
        if (node == null) return;
        node.setOpacity(0.0);
        node.setTranslateY(fromY);

        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setToValue(1.0);
        ft.setInterpolator(CUBIC_OUT);

        TranslateTransition tt = new TranslateTransition(Duration.millis(220), node);
        tt.setToY(0.0);
        tt.setInterpolator(CUBIC_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        if (delayMs > 0) {
            pt.setDelay(Duration.millis(delayMs));
        }
        pt.play();
    }

    /**
     * Animacion de aparicion de modal / popover origin-aware (scale 0.93 -> 1.0, fade 0 -> 1).
     * Duracion: 200ms.
     */
    public static void animateModalEnter(Node modalRoot) {
        if (modalRoot == null) return;
        modalRoot.setOpacity(0.0);
        modalRoot.setScaleX(0.93);
        modalRoot.setScaleY(0.93);

        FadeTransition ft = new FadeTransition(Duration.millis(180), modalRoot);
        ft.setToValue(1.0);
        ft.setInterpolator(CUBIC_OUT);

        ScaleTransition st = new ScaleTransition(Duration.millis(220), modalRoot);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(CUBIC_OUT);

        new ParallelTransition(ft, st).play();
    }
}
