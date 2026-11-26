package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.Asistencia;
import com.foodcore.model.Usuario;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AuthService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.UiAsyncExecutor;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RelojAsistenciaController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(RelojAsistenciaController.class.getName());

    @FXML private Label lblRelojDigital;
    @FXML private Label lblFechaActual;
    @FXML private TextField txtCredencial;
    @FXML private Button btnMarcar;

    @FXML private VBox cardConfirmacion;
    @FXML private Label lblConfirmacionBadge;
    @FXML private Label lblConfirmacionNombre;
    @FXML private Label lblConfirmacionDetalle;

    private final AsistenciaService asistenciaService;
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicBoolean marcajeEnCurso = new AtomicBoolean(false);
    private Timeline relojTimeline;
    private PauseTransition feedbackPause;

    public RelojAsistenciaController() {
        this(AppContainer.getDefault().resolve(AsistenciaService.class));
    }

    public RelojAsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = Objects.requireNonNull(
                asistenciaService, "asistenciaService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        iniciarReloj();
        AnimationUtils.attachTactileFeedback(btnMarcar);
    }

    private void iniciarReloj() {
        stopTimeline();
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
        String credencial = txtCredencial != null ? txtCredencial.getText() : null;
        if (credencial == null || credencial.trim().isEmpty()) {
            mostrarError("Por favor ingrese su DNI o PIN");
            return;
        }
        if (!marcajeEnCurso.compareAndSet(false, true)) {
            return;
        }
        String credLimpia = credencial.trim();
        setControlesMarcajeDeshabilitados(true);

        UiAsyncExecutor.ejecutarAsync(
            () -> asistenciaService.registrarMarcajeKiosco(credLimpia),
            res -> {
                marcajeEnCurso.set(false);
                setControlesMarcajeDeshabilitados(false);
                if (!vistaActiva.get()) {
                    return;
                }
                if (res != null && res.isExitoso()) {
                    mostrarConfirmacion(res);
                    if (txtCredencial != null) {
                        txtCredencial.clear();
                    }
                } else {
                    mostrarError(res != null ? res.getMensaje() : "No se pudo registrar asistencia");
                }
            },
            ex -> {
                marcajeEnCurso.set(false);
                setControlesMarcajeDeshabilitados(false);
                LOGGER.log(Level.WARNING, "Error al registrar marcaje en kiosco", ex);
                if (!vistaActiva.get()) {
                    return;
                }
                mostrarError("Error en base de datos: " + ex.getMessage());
            }
        );
    }

    private void setControlesMarcajeDeshabilitados(boolean disabled) {
        if (btnMarcar != null) {
            btnMarcar.setDisable(disabled);
        }
        if (txtCredencial != null) {
            txtCredencial.setDisable(disabled);
        }
    }

    private void mostrarConfirmacion(AsistenciaService.ResultadoMarcaje res) {
        if (cardConfirmacion != null) {
            cardConfirmacion.setVisible(true);
            AnimationUtils.animateModalEnter(cardConfirmacion);
        }

        Usuario u = res.getUsuario();
        String nom = u != null && u.getNombreEmpleado() != null && !u.getNombreEmpleado().isBlank()
                ? u.getNombreEmpleado()
                : (u != null ? u.getUsername() : "Colaborador");
        String rol = u != null && u.getNombreRol() != null ? u.getNombreRol() : "PERSONAL";
        if (lblConfirmacionNombre != null) {
            lblConfirmacionNombre.setText(nom + " (" + rol + ")");
        }
        if (lblConfirmacionDetalle != null) {
            lblConfirmacionDetalle.setText(res.getMensaje());
        }

        if (lblConfirmacionBadge != null) {
            lblConfirmacionBadge.getStyleClass().removeAll(
                    "fc-kiosk-badge-warning", "fc-kiosk-badge-success",
                    "fc-kiosk-badge-info", "fc-kiosk-badge-danger");
            if ("ENTRADA".equalsIgnoreCase(res.getTipo())) {
                Asistencia a = res.getAsistencia();
                if (a != null && a.getTardanzaMinutos() > 0) {
                    lblConfirmacionBadge.setText("⚠ ENTRADA CON TARDANZA (" + a.getTardanzaMinutos() + " MIN)");
                    lblConfirmacionBadge.getStyleClass().add("fc-kiosk-badge-warning");
                } else {
                    lblConfirmacionBadge.setText("✓ ENTRADA REGISTRADA PUNTUAL");
                    lblConfirmacionBadge.getStyleClass().add("fc-kiosk-badge-success");
                }
            } else {
                lblConfirmacionBadge.setText("✓ SALIDA REGISTRADA");
                lblConfirmacionBadge.getStyleClass().add("fc-kiosk-badge-info");
            }
        }

        reiniciarFeedbackAutoOcultar(Duration.seconds(4));
    }

    private void mostrarError(String error) {
        if (cardConfirmacion != null) {
            cardConfirmacion.setVisible(true);
            AnimationUtils.animateModalEnter(cardConfirmacion);
        }
        if (lblConfirmacionBadge != null) {
            lblConfirmacionBadge.setText("✕ NO REGISTRADO");
            lblConfirmacionBadge.getStyleClass().removeAll(
                    "fc-kiosk-badge-warning", "fc-kiosk-badge-success",
                    "fc-kiosk-badge-info", "fc-kiosk-badge-danger");
            lblConfirmacionBadge.getStyleClass().add("fc-kiosk-badge-danger");
        }
        if (lblConfirmacionNombre != null) {
            lblConfirmacionNombre.setText("Atención");
        }
        if (lblConfirmacionDetalle != null) {
            lblConfirmacionDetalle.setText(error);
        }

        reiniciarFeedbackAutoOcultar(Duration.seconds(3));
    }

    private void reiniciarFeedbackAutoOcultar(Duration duracion) {
        if (!vistaActiva.get()) {
            return;
        }
        if (feedbackPause != null) {
            feedbackPause.stop();
        }
        feedbackPause = new PauseTransition(duracion);
        feedbackPause.setOnFinished(e -> {
            if (cardConfirmacion != null) {
                cardConfirmacion.setVisible(false);
            }
        });
        feedbackPause.play();
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
        stopTimeline();
    }

    public Animation.Status getRelojTimelineStatus() {
        return relojTimeline != null ? relojTimeline.getStatus() : Animation.Status.STOPPED;
    }

    public TextField getTxtCredencial() {
        return txtCredencial;
    }

    public Button getBtnMarcar() {
        return btnMarcar;
    }

    public VBox getCardConfirmacion() {
        return cardConfirmacion;
    }

    public Label getLblConfirmacionBadge() {
        return lblConfirmacionBadge;
    }

    public Label getLblConfirmacionDetalle() {
        return lblConfirmacionDetalle;
    }

    public void stopTimeline() {
        if (relojTimeline != null) {
            relojTimeline.stop();
        }
        if (feedbackPause != null) {
            feedbackPause.stop();
        }
    }

    @FXML
    public void handleExpandirVentana(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = AppContainer.getDefault().createLoader(
                    getClass().getResource("/com/foodcore/view/RelojAsistenciaView.fxml"));
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
                    newCtrl.detenerRecursos();
                }
                newStage.close();
                newStage.setScene(null);
            });
            newStage.setOnHidden(e -> {
                if (newCtrl != null) {
                    newCtrl.detenerRecursos();
                }
            });
            newStage.show();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo expandir RelojAsistenciaView en nueva ventana", ex);
        }
    }

    /**
     * Solo un administrador autenticado puede retornar directamente al Dashboard desde el Kiosco;
     * cualquier otro usuario o sesión inexistente debe autenticarse nuevamente en LoginView.
     */
    public static boolean puedeAccederADashboardDesdeKiosco(Usuario user) {
        if (user == null || user.getNombreRol() == null) {
            return false;
        }
        String rol = user.getNombreRol().trim().toUpperCase(Locale.ROOT);
        return rol.contains("ADMIN");
    }

    @FXML
    public void handleSalirKiosco(ActionEvent event) {
        detenerRecursos();
        if (event != null && event.getSource() instanceof Button btn && btn.getScene() != null
                && btn.getScene().getWindow() instanceof javafx.stage.Stage stage) {
            if (stage.getOwner() != null || stage.getStyle() == javafx.stage.StageStyle.UNDECORATED) {
                stage.close();
                stage.setScene(null);
                return;
            }
            javafx.scene.Scene currentScene = btn.getScene();
            Usuario usuarioActual = AuthService.getUsuarioSesionActual();
            if (puedeAccederADashboardDesdeKiosco(usuarioActual)
                    && currentScene.lookup("#contentArea") instanceof javafx.scene.layout.StackPane contentArea) {
                try {
                    javafx.fxml.FXMLLoader l = AppContainer.getDefault().createLoader(
                            getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                    javafx.scene.Parent dash = l.load();
                    contentArea.getChildren().setAll(dash);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "No se pudo volver a DashboardView tras salir de kiosco", ex);
                }
                return;
            }

            // Fail-closed: usuarios no ADMIN nunca pueden ingresar al Dashboard desde el Kiosco
            AuthService.logout();
            try {
                javafx.fxml.FXMLLoader loader = AppContainer.getDefault().createLoader(
                        getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                javafx.scene.Parent loginRoot = loader.load();
                javafx.scene.Scene loginScene = new javafx.scene.Scene(loginRoot, 1080, 700);
                App.applyStylesheets(loginScene);
                stage.setScene(loginScene);
                stage.setTitle("FoodCore ERP - Iniciar Sesión");
                stage.centerOnScreen();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "No se pudo redirigir a LoginView al salir del kiosco", ex);
            }
        }
    }
}
