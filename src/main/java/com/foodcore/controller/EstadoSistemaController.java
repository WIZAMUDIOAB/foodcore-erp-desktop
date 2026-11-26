package com.foodcore.controller;

import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.service.AlertaService;
import com.foodcore.service.SistemaMonitorService;
import com.foodcore.service.SistemaMonitorService.EstadoSistemaSnapshot;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.UiAsyncExecutor;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de {@code EstadoSistemaView.fxml} según Sub-Fase C Ítems 4 y 5 (v5.3.0):
 * muestra el estado de servicios críticos y métricas de rendimiento cada 5 segundos usando
 * {@link Timeline} y {@link UiAsyncExecutor}.
 */
public class EstadoSistemaController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(EstadoSistemaController.class.getName());
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final double AUTO_REFRESH_SECONDS = 5.0;

    @FXML private StackPane rootEstadoSistemaStack;
    @FXML private Label lblUltimaActualizacion;
    @FXML private Button btnRefrescarAhora;

    @FXML private Label lblMysqlEstado;
    @FXML private Label lblMysqlVersion;
    @FXML private Label lblMysqlLatencia;

    @FXML private Label lblHttpEstado;
    @FXML private Label lblHttpDetalle;

    @FXML private Label lblHttpsEstado;
    @FXML private Label lblHttpsDetalle;

    @FXML private Label lblKdsSocketEstado;
    @FXML private Label lblKdsSocketPuerto;
    @FXML private Label lblKdsSocketClientes;

    @FXML private Label lblTunnelEstado;
    @FXML private Label lblTunnelUrl;

    @FXML private Label lblFlywayBadge;
    @FXML private Label lblFlywayVersion;
    @FXML private Label lblSistemaUptime;

    @FXML private Label lblAlertaHardwareBadge;
    @FXML private Label lblCpuPorcentaje;
    @FXML private ProgressBar barCpuUso;

    @FXML private Label lblRamPorcentaje;
    @FXML private ProgressBar barRamUso;
    @FXML private Label lblRamDetalle;

    @FXML private Label lblDiscoPorcentaje;
    @FXML private ProgressBar barDiscoUso;
    @FXML private Label lblDiscoDetalle;

    private final SistemaMonitorService monitorService = new SistemaMonitorService();
    private Timeline refreshTimeline;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnRefrescarAhora);
        refrescarEstadoAsync();
        iniciarAutoRefresco();
    }

    private void iniciarAutoRefresco() {
        stopTimeline();
        refreshTimeline = new Timeline(new KeyFrame(
                Duration.seconds(AUTO_REFRESH_SECONDS),
                e -> refrescarEstadoAsync()
        ));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    @Override
    public void detenerRecursos() {
        stopTimeline();
    }

    public void stopTimeline() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }

    @FXML
    public void handleRefrescarAhora(ActionEvent event) {
        refrescarEstadoAsync();
    }

    public void refrescarEstadoAsync() {
        UiAsyncExecutor.ejecutarAsync(
                monitorService::capturarSnapshot,
                this::aplicarSnapshotEnUi,
                ex -> LOGGER.log(Level.WARNING, "No se pudo actualizar telemetría del sistema", ex)
        );
    }

    /**
     * Renderiza una instantánea completa del estado del sistema en los indicadores JavaFX.
     */
    public void aplicarSnapshotEnUi(EstadoSistemaSnapshot snap) {
        if (snap == null) {
            return;
        }
        if (lblUltimaActualizacion != null) {
            lblUltimaActualizacion.setText("Actualizado: " + LocalTime.now().format(HORA_FMT));
        }

        aplicarBadgeEstado(lblMysqlEstado, snap.mysqlUp(), "UP", "DOWN");
        if (lblMysqlVersion != null) {
            lblMysqlVersion.setText("Versión: " + (snap.mysqlVersion() != null ? snap.mysqlVersion() : "N/D"));
        }
        if (lblMysqlLatencia != null) {
            lblMysqlLatencia.setText(snap.mysqlUp()
                    ? "Tiempo de respuesta: " + snap.mysqlLatenciaMs() + " ms"
                    : "Tiempo de respuesta: SIN CONEXIÓN");
        }

        aplicarBadgeEstado(lblHttpEstado, snap.javalinHttpUp(), "UP", "DOWN");
        if (lblHttpDetalle != null) {
            lblHttpDetalle.setText("Puerto " + snap.javalinHttpPort());
        }

        aplicarBadgeEstado(lblHttpsEstado, snap.javalinHttpsUp(), "UP", "DOWN");
        if (lblHttpsDetalle != null) {
            lblHttpsDetalle.setText("Puerto " + snap.javalinHttpsPort());
        }

        aplicarBadgeEstado(lblKdsSocketEstado, snap.socketKdsUp(), "UP", "DOWN");
        if (lblKdsSocketPuerto != null) {
            lblKdsSocketPuerto.setText("Puerto " + snap.socketKdsPort());
        }
        if (lblKdsSocketClientes != null) {
            lblKdsSocketClientes.setText("Clientes conectados: " + snap.socketKdsClientes());
        }

        aplicarBadgeEstado(lblTunnelEstado, snap.tunnelActivo(), "ACTIVO", "INACTIVO");
        if (lblTunnelUrl != null) {
            lblTunnelUrl.setText(snap.tunnelUrl() != null && !snap.tunnelUrl().isBlank()
                    ? snap.tunnelUrl() : "Sin túnel activo");
        }

        if (lblFlywayVersion != null) {
            lblFlywayVersion.setText(snap.flywayVersion() != null ? snap.flywayVersion() : "N/D");
        }
        if (lblFlywayBadge != null) {
            aplicarBadgeEstado(lblFlywayBadge, snap.mysqlUp(), "READY", "SIN BD");
        }
        if (lblSistemaUptime != null) {
            lblSistemaUptime.setText("Uptime del sistema: " + snap.uptimeFormateado());
        }

        double cpuFrac = Math.min(1.0, Math.max(0.0, snap.cpuPorcentaje() / 100.0));
        if (lblCpuPorcentaje != null) {
            lblCpuPorcentaje.setText(String.format(Locale.US, "%.1f %%", snap.cpuPorcentaje()));
        }
        if (barCpuUso != null) {
            barCpuUso.setProgress(cpuFrac);
        }

        double ramFrac = Math.min(1.0, Math.max(0.0, snap.ramPorcentaje() / 100.0));
        if (lblRamPorcentaje != null) {
            lblRamPorcentaje.setText(String.format(Locale.US, "%.1f %%", snap.ramPorcentaje()));
        }
        if (barRamUso != null) {
            barRamUso.setProgress(ramFrac);
        }
        if (lblRamDetalle != null) {
            lblRamDetalle.setText("Usada: " + SistemaMonitorService.formatearBytesGb(snap.ramUsadaBytes())
                    + " / Total: " + SistemaMonitorService.formatearBytesGb(snap.ramTotalBytes()));
        }

        double discoFrac = Math.min(1.0, Math.max(0.0, snap.discoUsadoPorcentaje() / 100.0));
        if (lblDiscoPorcentaje != null) {
            lblDiscoPorcentaje.setText(String.format(Locale.US, "%.1f %%", snap.discoUsadoPorcentaje()));
        }
        if (barDiscoUso != null) {
            barDiscoUso.setProgress(discoFrac);
        }
        if (lblDiscoDetalle != null) {
            lblDiscoDetalle.setText("Libre: " + SistemaMonitorService.formatearBytesGb(snap.discoLibreBytes())
                    + " / Total: " + SistemaMonitorService.formatearBytesGb(snap.discoTotalBytes()));
        }

        if (lblAlertaHardwareBadge != null) {
            boolean discoCritico = snap.discoLibreBytes() < AlertaService.UMBRAL_DISCO_LIBRE_BYTES;
            boolean ramCritica = snap.ramPorcentaje() >= AlertaService.UMBRAL_RAM_PORCENTAJE;
            boolean critico = !snap.mysqlUp() || discoCritico || ramCritica;
            lblAlertaHardwareBadge.getStyleClass().removeAll(
                    "badge-pill-success", "badge-pill-danger", "badge-pill-warning", "badge-pill-neutral");
            if (!snap.mysqlUp() || discoCritico) {
                lblAlertaHardwareBadge.setText(!snap.mysqlUp() ? "ALERTA: MYSQL DOWN" : "ALERTA: DISCO < 5 GB");
                lblAlertaHardwareBadge.getStyleClass().add("badge-pill-danger");
            } else if (ramCritica) {
                lblAlertaHardwareBadge.setText("ADVERTENCIA: RAM >= 90%");
                lblAlertaHardwareBadge.getStyleClass().add("badge-pill-warning");
            } else {
                lblAlertaHardwareBadge.setText("SALUD ÓPTIMA");
                lblAlertaHardwareBadge.getStyleClass().add("badge-pill-success");
            }
            if (critico) {
                LOGGER.fine("EstadoSistemaView detectó condición crítica/advertencia en telemetría.");
            }
        }
    }

    private static void aplicarBadgeEstado(Label label, boolean up, String textoOk, String textoDown) {
        if (label == null) {
            return;
        }
        label.setText(up ? textoOk : textoDown);
        label.getStyleClass().removeAll("badge-pill-success", "badge-pill-danger", "badge-pill-warning", "badge-pill-neutral");
        label.getStyleClass().add(up ? "badge-pill-success" : "badge-pill-danger");
    }

    public Label getLblMysqlEstado() {
        return lblMysqlEstado;
    }

    public Label getLblHttpEstado() {
        return lblHttpEstado;
    }

    public Label getLblHttpsEstado() {
        return lblHttpsEstado;
    }

    public Label getLblKdsSocketEstado() {
        return lblKdsSocketEstado;
    }

    public Label getLblKdsSocketClientes() {
        return lblKdsSocketClientes;
    }

    public Label getLblTunnelUrl() {
        return lblTunnelUrl;
    }

    public Label getLblFlywayVersion() {
        return lblFlywayVersion;
    }

    public Label getLblSistemaUptime() {
        return lblSistemaUptime;
    }

    public Label getLblCpuPorcentaje() {
        return lblCpuPorcentaje;
    }

    public Label getLblRamDetalle() {
        return lblRamDetalle;
    }

    public Label getLblDiscoDetalle() {
        return lblDiscoDetalle;
    }

    public boolean isTimelineActivo() {
        return refreshTimeline != null && refreshTimeline.getStatus() == Animation.Status.RUNNING;
    }
}
