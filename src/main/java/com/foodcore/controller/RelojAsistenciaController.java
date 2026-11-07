package com.foodcore.controller;

import com.foodcore.model.Asistencia;
import com.foodcore.model.Usuario;
import com.foodcore.service.AsistenciaService;
import com.foodcore.util.AnimationUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RelojAsistenciaController {

    private static final Logger LOGGER = Logger.getLogger(RelojAsistenciaController.class.getName());

    @FXML private Label lblRelojDigital;
    @FXML private Label lblFechaActual;
    @FXML private TextField txtCredencial;
    @FXML private Button btnMarcar;

    @FXML private VBox cardConfirmacion;
    @FXML private Label lblConfirmacionBadge;
    @FXML private Label lblConfirmacionNombre;
    @FXML private Label lblConfirmacionDetalle;

    private final AsistenciaService asistenciaService = new AsistenciaService();
    private Timeline relojTimeline;

    @FXML
    public void initialize() {
        iniciarReloj();
        AnimationUtils.attachTactileFeedback(btnMarcar);
    }

    private void iniciarReloj() {
        actualizarTiempo();
        relojTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> actualizarTiempo()));
        relojTimeline.setCycleCount(Animation.INDEFINITE);
        relojTimeline.play();
    }

    private void actualizarTiempo() {
        LocalDateTime ahora = LocalDateTime.now();
        lblRelojDigital.setText(ahora.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        DateTimeFormatter dtfFecha = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));
        String fechaTexto = ahora.format(dtfFecha);
        lblFechaActual.setText(fechaTexto.substring(0, 1).toUpperCase() + fechaTexto.substring(1));
    }

    @FXML
    public void handleNumKey(ActionEvent event) {
        if (event.getSource() instanceof Button btn) {
            txtCredencial.appendText(btn.getText());
        }
    }

    @FXML
    public void handleClearKey() {
        txtCredencial.clear();
    }

    @FXML
    public void handleBackspaceKey() {
        String texto = txtCredencial.getText();
        if (texto != null && !texto.isEmpty()) {
            txtCredencial.setText(texto.substring(0, texto.length() - 1));
        }
    }

    @FXML
    public void handleMarcar() {
        String credencial = txtCredencial.getText();
        if (credencial == null || credencial.trim().isEmpty()) {
            mostrarError("Por favor ingrese su DNI o PIN");
            return;
        }

        try {
            AsistenciaService.ResultadoMarcaje res = asistenciaService.registrarMarcajeKiosco(credencial.trim());
            if (res.isExitoso()) {
                mostrarConfirmacion(res);
                txtCredencial.clear();
            } else {
                mostrarError(res.getMensaje());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al registrar marcaje en kiosco", ex);
            mostrarError("Error en base de datos: " + ex.getMessage());
        }
    }

    private void mostrarConfirmacion(AsistenciaService.ResultadoMarcaje res) {
        cardConfirmacion.setVisible(true);
        AnimationUtils.animateModalEnter(cardConfirmacion);

        Usuario u = res.getUsuario();
        String nom = u.getNombreEmpleado() != null && !u.getNombreEmpleado().isBlank() ? u.getNombreEmpleado() : u.getUsername();
        lblConfirmacionNombre.setText(nom + " (" + u.getNombreRol() + ")");
        lblConfirmacionDetalle.setText(res.getMensaje());

        if ("ENTRADA".equalsIgnoreCase(res.getTipo())) {
            Asistencia a = res.getAsistencia();
            if (a != null && a.getTardanzaMinutos() > 0) {
                lblConfirmacionBadge.setText("⚠ ENTRADA CON TARDANZA (" + a.getTardanzaMinutos() + " MIN)");
                lblConfirmacionBadge.setStyle("-fx-background-color: -fc-warning-bg; -fx-text-fill: -fc-warning-text;");
            } else {
                lblConfirmacionBadge.setText("✓ ENTRADA REGISTRADA PUNTUAL");
                lblConfirmacionBadge.setStyle("-fx-background-color: -fc-success-bg; -fx-text-fill: -fc-success-text;");
            }
        } else {
            lblConfirmacionBadge.setText("✓ SALIDA REGISTRADA");
            lblConfirmacionBadge.setStyle("-fx-background-color: -fc-info-bg; -fc-text-fill: -fc-info-text;");
        }

        PauseTransition pause = new PauseTransition(Duration.seconds(4));
        pause.setOnFinished(e -> cardConfirmacion.setVisible(false));
        pause.play();
    }

    private void mostrarError(String error) {
        cardConfirmacion.setVisible(true);
        AnimationUtils.animateModalEnter(cardConfirmacion);
        lblConfirmacionBadge.setText("✕ NO REGISTRADO");
        lblConfirmacionBadge.setStyle("-fx-background-color: -fc-danger-bg; -fx-text-fill: -fc-danger-text;");
        lblConfirmacionNombre.setText("Atención");
        lblConfirmacionDetalle.setText(error);

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> cardConfirmacion.setVisible(false));
        pause.play();
    }

    public void stopTimeline() {
        if (relojTimeline != null) {
            relojTimeline.stop();
        }
    }

    @FXML
    public void handleExpandirVentana(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/foodcore/view/RelojAsistenciaView.fxml"));
            javafx.scene.Parent root = loader.load();
            RelojAsistenciaController newCtrl = loader.getController();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1080, 768);
            com.foodcore.App.applyStylesheets(scene);

            javafx.stage.Stage newStage = new javafx.stage.Stage();
            newStage.initStyle(javafx.stage.StageStyle.DECORATED);
            newStage.setTitle("FoodCore ERP — Reloj Marcador de Asistencia (Monitor Secundario)");
            newStage.setMaximized(true);
            newStage.setScene(scene);
            newStage.setOnCloseRequest(e -> {
                if (newCtrl != null) {
                    newCtrl.stopTimeline();
                }
                newStage.close();
                newStage.setScene(null);
            });
            newStage.setOnHidden(e -> {
                if (newCtrl != null) {
                    newCtrl.stopTimeline();
                }
            });
            newStage.show();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo expandir RelojAsistenciaView en nueva ventana", ex);
        }
    }

    @FXML
    public void handleSalirKiosco(ActionEvent event) {
        stopTimeline();
        if (event.getSource() instanceof Button btn && btn.getScene() != null && btn.getScene().getWindow() instanceof javafx.stage.Stage stage) {
            if (stage.getOwner() != null || stage.getStyle() == javafx.stage.StageStyle.UNDECORATED) {
                stage.close();
                stage.setScene(null);
            } else {
                javafx.scene.Scene currentScene = btn.getScene();
                if (currentScene != null && currentScene.lookup("#contentArea") instanceof javafx.scene.layout.StackPane contentArea) {
                    try {
                        javafx.fxml.FXMLLoader l = new javafx.fxml.FXMLLoader(getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                        javafx.scene.Parent dash = l.load();
                        contentArea.getChildren().setAll(dash);
                    } catch (Exception ex) {
                        LOGGER.log(Level.FINE, "No se pudo volver a DashboardView tras salir de kiosco", ex);
                    }
                }
            }
        }
    }
}
