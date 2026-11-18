package com.foodcore.controller;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Controlador de la pantalla de carga inicial animada (Sub-Fase F - F2).
 */
public class SplashController {

    @FXML private StackPane bootstrapSplashRoot;
    @FXML private VBox logoContainer;
    @FXML private ProgressBar progressBarSplash;
    @FXML private ProgressIndicator spinnerBootstrap;
    @FXML private Label lblEstadoBootstrap;

    @FXML
    public void initialize() {
        animarEntradaLogo();
    }

    /**
     * Ejecuta la animación sutil de entrada (fade-in + scale-up) sobre el logo de FoodCore.
     */
    public void animarEntradaLogo() {
        if (logoContainer == null) {
            return;
        }
        logoContainer.setOpacity(0.0);
        logoContainer.setScaleX(0.85);
        logoContainer.setScaleY(0.85);

        FadeTransition fade = new FadeTransition(Duration.millis(450), logoContainer);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition scale = new ScaleTransition(Duration.millis(450), logoContainer);
        scale.setFromX(0.85);
        scale.setFromY(0.85);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_BOTH);

        new ParallelTransition(fade, scale).play();
    }

    public StackPane getBootstrapSplashRoot() {
        return bootstrapSplashRoot;
    }

    public VBox getLogoContainer() {
        return logoContainer;
    }

    public ProgressBar getProgressBarSplash() {
        return progressBarSplash;
    }

    public ProgressIndicator getSpinnerBootstrap() {
        return spinnerBootstrap;
    }

    public Label getLblEstadoBootstrap() {
        return lblEstadoBootstrap;
    }
}
