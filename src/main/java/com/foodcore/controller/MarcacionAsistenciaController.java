package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.exception.NegocioException;
import com.foodcore.service.AuthService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarcacionAsistenciaController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(MarcacionAsistenciaController.class.getName());

    @FXML private TextField txtDni;
    @FXML private Label lblEstado;

    private final AuthService authService;
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicBoolean marcandoEnCurso = new AtomicBoolean(false);

    public MarcacionAsistenciaController() {
        this(AppContainer.getDefault().authService());
    }

    public MarcacionAsistenciaController(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
    }

    @FXML
    public void handleNumClick(ActionEvent event) {
        Button b = (Button) event.getSource();
        if (txtDni.getText().length() < 8) {
            txtDni.setText(txtDni.getText() + b.getText());
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        txtDni.clear();
        lblEstado.setText("");
    }

    @FXML
    public void handleBackspace(ActionEvent event) {
        String act = txtDni.getText();
        if (!act.isEmpty()) {
            txtDni.setText(act.substring(0, act.length() - 1));
        }
    }

    @FXML
    public void handleRegistrar(ActionEvent event) {
        String dni = txtDni != null && txtDni.getText() != null ? txtDni.getText().trim() : "";
        Button trigger = (event != null && event.getSource() instanceof Button b) ? b : null;
        if (!marcandoEnCurso.compareAndSet(false, true)) {
            return;
        }
        if (trigger != null) {
            trigger.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> authService.marcarAsistenciaInteligente(dni),
            resultado -> {
                marcandoEnCurso.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                aplicarEstadoEnUi(true, resultado);
                if (txtDni != null) {
                    txtDni.clear();
                }
            },
            e -> {
                marcandoEnCurso.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error al registrar marcación de asistencia", e);
                if (!vistaActiva.get()) {
                    return;
                }
                String msg = e instanceof NegocioException
                        ? e.getMessage()
                        : "Error al registrar marcación: " + e.getMessage();
                aplicarEstadoEnUi(false, msg);
            }
        );
    }

    private void aplicarEstadoEnUi(boolean ok, String mensaje) {
        if (lblEstado == null) {
            return;
        }
        lblEstado.getStyleClass().removeAll("fc-status-ok", "fc-status-error");
        lblEstado.getStyleClass().add(ok ? "fc-status-ok" : "fc-status-error");
        lblEstado.setText(mensaje);
    }

    public TextField getTxtDni() {
        return txtDni;
    }

    public Label getLblEstado() {
        return lblEstado;
    }
}
