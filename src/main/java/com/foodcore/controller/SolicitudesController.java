package com.foodcore.controller;

import com.foodcore.dao.SolicitudAusenciaDAO;
import com.foodcore.dao.impl.SolicitudAusenciaDAOImpl;
import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Gestión de Ausencias, Vacaciones y Permisos (FoodCore v4.4.0).
 */
public class SolicitudesController {

    private static final Logger LOGGER = Logger.getLogger(SolicitudesController.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yy");

    @FXML private StackPane rootSolicitudesStack;
    @FXML private ComboBox<String> cbTipoAusencia;
    @FXML private DatePicker dpFechaInicio;
    @FXML private DatePicker dpFechaFin;
    @FXML private TextField txtMotivo;
    @FXML private Button btnEnviarSolicitud;
    @FXML private Button btnActualizar;

    @FXML private Label lblCountPendientes;
    @FXML private Label lblCountAprobadas;
    @FXML private Label lblCountRechazadas;
    @FXML private Label lblEstadoTabla;

    @FXML private TableView<SolicitudAusencia> tblSolicitudes;
    @FXML private TableColumn<SolicitudAusencia, String> colEmpleado;
    @FXML private TableColumn<SolicitudAusencia, String> colTipo;
    @FXML private TableColumn<SolicitudAusencia, String> colRango;
    @FXML private TableColumn<SolicitudAusencia, String> colMotivo;
    @FXML private TableColumn<SolicitudAusencia, String> colEstado;
    @FXML private TableColumn<SolicitudAusencia, Void> colAcciones;

    private final SolicitudAusenciaDAO solicitudDAO = new SolicitudAusenciaDAOImpl();
    private final ObservableList<SolicitudAusencia> listaSolicitudes = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnEnviarSolicitud);
        AnimationUtils.attachTactileFeedback(btnActualizar);

        if (cbTipoAusencia != null) {
            cbTipoAusencia.setItems(FXCollections.observableArrayList(
                "VACACIONES", "PERMISO_MEDICO", "ASUNTO_PERSONAL", "LICENCIA"
            ));
            cbTipoAusencia.setValue("VACACIONES");
        }
        if (dpFechaInicio != null) {
            dpFechaInicio.setValue(LocalDate.now().plusDays(1));
        }
        if (dpFechaFin != null) {
            dpFechaFin.setValue(LocalDate.now().plusDays(3));
        }

        configurarTabla();
        cargarSolicitudesSincrono();
    }

    private boolean usuarioEsGerente() {
        Usuario u = AuthService.getUsuarioSesionActual();
        if (u == null || u.getNombreRol() == null) {
            return true; // Default en tests sin login previo
        }
        String r = u.getNombreRol().toUpperCase();
        return r.contains("ADMIN") || r.contains("GERENTE") || r.contains("SUPERVISOR");
    }

    private void configurarTabla() {
        colEmpleado.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getNombreUsuario() != null ? c.getValue().getNombreUsuario() : "Colaborador #" + c.getValue().getIdUsuario()
        ));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
        colRango.setCellValueFactory(c -> {
            String ini = c.getValue().getFechaInicio() != null ? c.getValue().getFechaInicio().format(FMT) : "-";
            String fin = c.getValue().getFechaFin() != null ? c.getValue().getFechaFin().format(FMT) : "-";
            return new SimpleStringProperty(ini + " al " + fin);
        });
        colMotivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMotivo()));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(estado);
                    if ("APROBADA".equalsIgnoreCase(estado)) {
                        pill.getStyleClass().add("badge-pill-success");
                    } else if ("RECHAZADA".equalsIgnoreCase(estado)) {
                        pill.getStyleClass().add("badge-pill-danger");
                    } else {
                        pill.getStyleClass().add("badge-pill-warning");
                    }
                    setGraphic(pill);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnAprobar = new Button("Aprobar");
            private final Button btnRechazar = new Button("Rechazar");
            private final HBox box = new HBox(6.0, btnAprobar, btnRechazar);

            {
                box.setAlignment(Pos.CENTER);
                btnAprobar.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 4 10; -fx-background-radius: 6; -fx-cursor: hand;");
                btnRechazar.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 4 10; -fx-background-radius: 6; -fx-cursor: hand;");
                AnimationUtils.attachTactileFeedback(btnAprobar);
                AnimationUtils.attachTactileFeedback(btnRechazar);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    SolicitudAusencia sol = getTableRow().getItem();
                    boolean pendiente = "PENDIENTE".equalsIgnoreCase(sol.getEstado());
                    boolean puedeGestionar = usuarioEsGerente() && pendiente;
                    btnAprobar.setDisable(!puedeGestionar);
                    btnRechazar.setDisable(!puedeGestionar);
                    if (!pendiente) {
                        Label lblResuelto = new Label("✓ Procesado");
                        lblResuelto.getStyleClass().add("text-caption-muted");
                        setGraphic(lblResuelto);
                    } else if (!usuarioEsGerente()) {
                        Label lblEsp = new Label("En revisión");
                        lblEsp.getStyleClass().add("text-caption-muted");
                        setGraphic(lblEsp);
                    } else {
                        btnAprobar.setOnAction(ev -> aprobarSolicitud(sol));
                        btnRechazar.setOnAction(ev -> rechazarSolicitud(sol));
                        setGraphic(box);
                    }
                }
            }
        });

        tblSolicitudes.setItems(listaSolicitudes);
    }

    public void cargarSolicitudesSincrono() {
        try (Connection conn = ConexionBD.getConnection()) {
            List<SolicitudAusencia> data;
            if (usuarioEsGerente()) {
                data = solicitudDAO.listarTodas(conn);
            } else {
                Usuario u = AuthService.getUsuarioSesionActual();
                int idUsr = u != null ? u.getIdUsuario() : 1;
                data = solicitudDAO.listarPorUsuario(conn, idUsr);
            }
            listaSolicitudes.setAll(data);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Aviso al cargar solicitudes de ausencia", e);
        }
        actualizarContadores();
    }

    private void actualizarContadores() {
        int pend = 0;
        int aprob = 0;
        int rech = 0;
        for (SolicitudAusencia s : listaSolicitudes) {
            if ("APROBADA".equalsIgnoreCase(s.getEstado())) {
                aprob++;
            } else if ("RECHAZADA".equalsIgnoreCase(s.getEstado())) {
                rech++;
            } else {
                pend++;
            }
        }
        if (lblCountPendientes != null) lblCountPendientes.setText(String.valueOf(pend));
        if (lblCountAprobadas != null) lblCountAprobadas.setText(String.valueOf(aprob));
        if (lblCountRechazadas != null) lblCountRechazadas.setText(String.valueOf(rech));
        if (lblEstadoTabla != null) lblEstadoTabla.setText(listaSolicitudes.size() + " registros");
    }

    @FXML
    public void handleRefrescar(ActionEvent event) {
        cargarSolicitudesSincrono();
    }

    @FXML
    public void handleEnviarSolicitud(ActionEvent event) {
        String tipo = cbTipoAusencia.getValue() != null ? cbTipoAusencia.getValue() : "VACACIONES";
        LocalDate ini = dpFechaInicio.getValue();
        LocalDate fin = dpFechaFin.getValue();
        String motivo = txtMotivo.getText() != null ? txtMotivo.getText().trim() : "";

        if (ini == null || fin == null || fin.isBefore(ini)) {
            mostrarToast("La fecha de fin debe ser posterior o igual a la fecha de inicio.", true);
            return;
        }
        if (motivo.isEmpty()) {
            mostrarToast("Ingrese un motivo para su solicitud.", true);
            return;
        }

        Usuario u = AuthService.getUsuarioSesionActual();
        int idUsr = (u != null && u.getIdUsuario() > 0) ? u.getIdUsuario() : 1;

        try {
            registrarSolicitud(idUsr, tipo, ini, fin, motivo);
            txtMotivo.clear();
            mostrarToast("Solicitud enviada correctamente para revisión gerencial.", false);
        } catch (Exception e) {
            mostrarToast("Error al registrar solicitud: " + e.getMessage(), true);
        }
    }

    public SolicitudAusencia registrarSolicitud(int idUsuario, String tipo, LocalDate inicio, LocalDate fin, String motivo) throws Exception {
        SolicitudAusencia sol = new SolicitudAusencia(0, idUsuario, tipo, inicio, fin, motivo, "PENDIENTE");
        try (Connection conn = ConexionBD.getConnection()) {
            solicitudDAO.crear(conn, sol);
        }
        cargarSolicitudesSincrono();
        return sol;
    }

    public void aprobarSolicitud(SolicitudAusencia sol) {
        if (sol == null) return;
        try (Connection conn = ConexionBD.getConnection()) {
            solicitudDAO.actualizarEstado(conn, sol.getIdSolicitud(), "APROBADA", "Aprobado por Gerencia");
            sol.setEstado("APROBADA");
            tblSolicitudes.refresh();
            actualizarContadores();
            mostrarToast("Solicitud #" + sol.getIdSolicitud() + " APROBADA.", false);
        } catch (Exception e) {
            mostrarToast("Error al aprobar solicitud: " + e.getMessage(), true);
        }
    }

    public void rechazarSolicitud(SolicitudAusencia sol) {
        if (sol == null) return;
        try (Connection conn = ConexionBD.getConnection()) {
            solicitudDAO.actualizarEstado(conn, sol.getIdSolicitud(), "RECHAZADA", "Rechazado por cobertura de turno");
            sol.setEstado("RECHAZADA");
            tblSolicitudes.refresh();
            actualizarContadores();
            mostrarToast("Solicitud #" + sol.getIdSolicitud() + " RECHAZADA.", false);
        } catch (Exception e) {
            mostrarToast("Error al rechazar solicitud: " + e.getMessage(), true);
        }
    }

    private void mostrarToast(String msg, boolean error) {
        if (rootSolicitudesStack != null && rootSolicitudesStack.getScene() != null) {
            AnimationUtils.mostrarToast(rootSolicitudesStack, msg, error);
        }
    }

    public ObservableList<SolicitudAusencia> getListaSolicitudes() {
        return listaSolicitudes;
    }
}
