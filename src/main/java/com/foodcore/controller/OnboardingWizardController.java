package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.service.AuthService;
import com.foodcore.service.OnboardingService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.DialogService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador del Wizard de Configuración Inicial (Onboarding v5.5.0 Sub-Fase E2).
 * Guía en 4 pasos: 1 Restaurante -> 2 Primer Platillo -> 3 Primera Mesa -> 4 Resumen y arranque.
 */
public class OnboardingWizardController {

    private static final Logger LOGGER = Logger.getLogger(OnboardingWizardController.class.getName());

    @FXML private StackPane rootOnboardingStack;
    @FXML private Label lblPasoSubtitulo;
    @FXML private Label lblBadgeProgreso;
    @FXML private Label lblStep1Indicator;
    @FXML private Label lblStep2Indicator;
    @FXML private Label lblStep3Indicator;
    @FXML private Label lblStep4Indicator;
    @FXML private Label lblErrorWizard;

    @FXML private VBox panelPaso1;
    @FXML private VBox panelPaso2;
    @FXML private VBox panelPaso3;
    @FXML private VBox panelPaso4;

    @FXML private TextField txtNombreRestaurante;
    @FXML private TextField txtRuc;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtTelefono;

    @FXML private TextField txtNombrePlatillo;
    @FXML private TextField txtPrecioPlatillo;
    @FXML private ComboBox<String> cmbCategoriaPlatillo;

    @FXML private TextField txtNumeroMesa;
    @FXML private TextField txtCapacidadMesa;

    @FXML private Label lblResumenRestaurante;
    @FXML private Label lblResumenPlatillo;
    @FXML private Label lblResumenMesa;

    @FXML private Button btnPasoAnterior;
    @FXML private Button btnPasoSiguiente;
    @FXML private Button btnEmpezarOperar;

    private final OnboardingService onboardingService;
    private int pasoActual = 1;
    private int platilloCreadoId = 0;

    public OnboardingWizardController() {
        this(new OnboardingService());
    }

    OnboardingWizardController(OnboardingService onboardingService) {
        this.onboardingService = Objects.requireNonNull(onboardingService, "onboardingService obligatorio");
    }

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnPasoAnterior);
        AnimationUtils.attachTactileFeedback(btnPasoSiguiente);
        AnimationUtils.attachTactileFeedback(btnEmpezarOperar);

        if (cmbCategoriaPlatillo != null) {
            List<String> cats = onboardingService.listarCategoriasSugeridas();
            cmbCategoriaPlatillo.setItems(FXCollections.observableArrayList(cats));
            if (!cats.isEmpty()) {
                cmbCategoriaPlatillo.getSelectionModel().selectFirst();
            }
        }
        mostrarPaso(1);
    }

    @FXML
    public void handleSiguiente(ActionEvent event) {
        ocultarError();
        try {
            if (pasoActual == 1) {
                avanzarDesdePaso1();
            } else if (pasoActual == 2) {
                avanzarDesdePaso2();
            } else if (pasoActual == 3) {
                avanzarDesdePaso3();
            }
        } catch (IllegalArgumentException ex) {
            mostrarError(ex.getMessage());
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error guardando paso de onboarding en BD", ex);
            DialogService.mostrarAlerta(
                    Alert.AlertType.ERROR,
                    "Error al guardar configuración",
                    "No se pudo guardar el paso " + pasoActual + " en la base de datos: " + ex.getMessage()
            );
        }
    }

    /**
     * Valida los campos del paso 1 sin marcar aún completa {@code configuracion_restaurante}.
     */
    public boolean avanzarDesdePaso1() {
        String nombre = texto(txtNombreRestaurante);
        String ruc = texto(txtRuc);
        String direccion = texto(txtDireccion);
        String telefono = texto(txtTelefono);
        OnboardingService.validarPasoRestaurante(nombre, ruc, direccion, telefono);
        mostrarPaso(2);
        return true;
    }

    /**
     * Valida y persiste el primer platillo en BD, avanzando al paso 3.
     */
    public boolean avanzarDesdePaso2() throws SQLException {
        String nombre = texto(txtNombrePlatillo);
        String precioStr = texto(txtPrecioPlatillo);
        String categoria = resolverCategoriaSeleccionada();
        BigDecimal precio = OnboardingService.validarPasoPlatillo(nombre, precioStr, categoria);
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("Sin conexión activa a base de datos.");
            }
            this.platilloCreadoId = onboardingService.guardarPrimerPlatillo(
                    conn, platilloCreadoId, nombre, precio, categoria);
        }
        mostrarPaso(3);
        return true;
    }

    /**
     * Valida y persiste la primera mesa en BD, avanzando al paso 4 de resumen.
     */
    public boolean avanzarDesdePaso3() throws SQLException {
        String numeroMesa = texto(txtNumeroMesa);
        String capacidadStr = texto(txtCapacidadMesa);
        int capacidad = OnboardingService.validarPasoMesa(numeroMesa, capacidadStr);
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("Sin conexión activa a base de datos.");
            }
            onboardingService.guardarPrimeraMesa(conn, numeroMesa, capacidad);
        }
        actualizarResumenPaso4();
        mostrarPaso(4);
        return true;
    }

    @FXML
    public void handleAnterior(ActionEvent event) {
        ocultarError();
        if (pasoActual > 1) {
            mostrarPaso(pasoActual - 1);
        }
    }

    /**
     * Finaliza el onboarding insertando {@code configuracion_restaurante} y redirigiendo a MainDashboardView.
     */
    @FXML
    public void handleEmpezarOperar(ActionEvent event) {
        ocultarError();
        try {
            String nombre = texto(txtNombreRestaurante);
            String ruc = texto(txtRuc);
            String direccion = texto(txtDireccion);
            String telefono = texto(txtTelefono);
            try (Connection conn = ConexionBD.getConnection()) {
                if (conn == null) {
                    throw new SQLException("Sin conexión activa a base de datos.");
                }
                onboardingService.finalizarOnboarding(conn, nombre, ruc, direccion, telefono);
            }
            redirigirAlDashboard();
        } catch (IllegalArgumentException ex) {
            mostrarError(ex.getMessage());
        } catch (SQLException | IOException ex) {
            LOGGER.log(Level.SEVERE, "Error finalizando configuración inicial", ex);
            DialogService.mostrarAlerta(
                    Alert.AlertType.ERROR,
                    "Error al completar configuración",
                    "No se pudo completar la configuración inicial: " + ex.getMessage()
            );
        }
    }

    protected void redirigirAlDashboard() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
        Parent root = loader.load();
        Scene currentScene = rootOnboardingStack != null ? rootOnboardingStack.getScene() : null;
        if (currentScene != null && currentScene.getWindow() instanceof Stage stage) {
            Scene dashboardScene = new Scene(root, 1920, 1080);
            App.applyStylesheets(dashboardScene);
            stage.setScene(dashboardScene);
            String rol = AuthService.getUsuarioSesionActual() != null
                    ? AuthService.getUsuarioSesionActual().getNombreRol()
                    : "ADMIN";
            stage.setTitle("FoodCore ERP v3.0 - Panel Principal [" + rol + "]");
            stage.setMinWidth(1280);
            stage.setMinHeight(720);
            stage.setMaximized(true);
        } else if (currentScene != null) {
            App.applyStylesheets(currentScene);
            currentScene.setRoot(root);
        }
    }

    private void actualizarResumenPaso4() {
        if (lblResumenRestaurante != null) {
            lblResumenRestaurante.setText(String.format(
                    "%s · RUC %s · %s · Tel: %s",
                    texto(txtNombreRestaurante), texto(txtRuc), texto(txtDireccion), texto(txtTelefono)));
        }
        if (lblResumenPlatillo != null) {
            lblResumenPlatillo.setText(String.format(
                    "%s (%s) · S/ %s",
                    texto(txtNombrePlatillo), resolverCategoriaSeleccionada(), texto(txtPrecioPlatillo)));
        }
        if (lblResumenMesa != null) {
            lblResumenMesa.setText(String.format(
                    "Mesa %s · Capacidad: %s personas",
                    texto(txtNumeroMesa), texto(txtCapacidadMesa)));
        }
    }

    public void mostrarPaso(int paso) {
        this.pasoActual = Math.max(1, Math.min(4, paso));
        VBox[] paneles = {panelPaso1, panelPaso2, panelPaso3, panelPaso4};
        for (int i = 0; i < paneles.length; i++) {
            boolean activo = (i + 1) == this.pasoActual;
            if (paneles[i] != null) {
                paneles[i].setVisible(activo);
                paneles[i].setManaged(activo);
            }
        }

        String[] titulos = {
            "Paso 1 de 4 · Datos del restaurante",
            "Paso 2 de 4 · Crear el primer platillo",
            "Paso 3 de 4 · Crear la primera mesa",
            "Paso 4 de 4 · Resumen final y arranque"
        };
        if (lblPasoSubtitulo != null) {
            lblPasoSubtitulo.setText(titulos[this.pasoActual - 1]);
        }
        if (lblBadgeProgreso != null) {
            lblBadgeProgreso.setText("Paso " + this.pasoActual + " / 4");
        }

        actualizarEstiloIndicador(lblStep1Indicator, 1);
        actualizarEstiloIndicador(lblStep2Indicator, 2);
        actualizarEstiloIndicador(lblStep3Indicator, 3);
        actualizarEstiloIndicador(lblStep4Indicator, 4);

        if (btnPasoAnterior != null) {
            btnPasoAnterior.setDisable(this.pasoActual <= 1);
        }
        boolean ultimo = this.pasoActual == 4;
        if (btnPasoSiguiente != null) {
            btnPasoSiguiente.setVisible(!ultimo);
            btnPasoSiguiente.setManaged(!ultimo);
        }
        if (btnEmpezarOperar != null) {
            btnEmpezarOperar.setVisible(ultimo);
            btnEmpezarOperar.setManaged(ultimo);
        }
    }

    private void actualizarEstiloIndicador(Label badge, int stepNumber) {
        if (badge == null) {
            return;
        }
        badge.getStyleClass().removeAll("badge-pill-accent", "badge-pill-success", "badge-pill-neutral");
        if (stepNumber < pasoActual) {
            badge.getStyleClass().add("badge-pill-success");
        } else if (stepNumber == pasoActual) {
            badge.getStyleClass().add("badge-pill-accent");
        } else {
            badge.getStyleClass().add("badge-pill-neutral");
        }
    }

    private String resolverCategoriaSeleccionada() {
        if (cmbCategoriaPlatillo == null) {
            return "";
        }
        if (cmbCategoriaPlatillo.getValue() != null && !cmbCategoriaPlatillo.getValue().isBlank()) {
            return cmbCategoriaPlatillo.getValue().trim();
        }
        if (cmbCategoriaPlatillo.getEditor() != null && cmbCategoriaPlatillo.getEditor().getText() != null) {
            return cmbCategoriaPlatillo.getEditor().getText().trim();
        }
        return "";
    }

    private static String texto(TextField tf) {
        return tf != null && tf.getText() != null ? tf.getText().trim() : "";
    }

    private void mostrarError(String msg) {
        if (lblErrorWizard != null) {
            lblErrorWizard.setText(msg);
            lblErrorWizard.setVisible(true);
            lblErrorWizard.setManaged(true);
        }
    }

    private void ocultarError() {
        if (lblErrorWizard != null) {
            lblErrorWizard.setText("");
            lblErrorWizard.setVisible(false);
            lblErrorWizard.setManaged(false);
        }
    }

    public int getPasoActual() {
        return pasoActual;
    }

    public int getPlatilloCreadoId() {
        return platilloCreadoId;
    }

    public Label getLblErrorWizard() {
        return lblErrorWizard;
    }
}
