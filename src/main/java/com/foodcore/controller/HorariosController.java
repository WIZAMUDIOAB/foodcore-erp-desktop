package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.service.HorariosService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ToastService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HorariosController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(HorariosController.class.getName());

    private record DatosSemana(
            List<TurnoProgramado> turnos,
            List<String> alertas) {
    }

    private record DatosHorariosCompletos(
            List<Usuario> usuarios,
            List<TurnoProgramado> turnos,
            List<String> alertas) {
    }

    @FXML private StackPane rootHorariosStack;
    @FXML private HBox bannerAlertasCobertura;
    @FXML private Label lblTextoAlerta;
    @FXML private Button btnNuevoTurno;
    @FXML private Button btnPublicarHorarios;
    @FXML private Button btnAsignarRapido;

    @FXML private GridPane gridSemana;
    @FXML private ListView<Usuario> listColaboradores;

    @FXML private VBox modalTurno;
    @FXML private ComboBox<Usuario> cmbModalColaborador;
    @FXML private ComboBox<String> cmbModalDia;
    @FXML private ComboBox<String> cmbModalTurno;
    @FXML private TextField txtModalInicio;
    @FXML private TextField txtModalFin;
    @FXML private Button btnGuardarTurno;

    private final HorariosService horariosService;
    private final ObservableList<Usuario> listaUsuarios = FXCollections.observableArrayList();
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicLong cargaDatosSeq = new AtomicLong(0L);
    private final AtomicLong cargaGrillaSeq = new AtomicLong(0L);
    private final AtomicBoolean guardarTurnoEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean eliminarTurnoEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean publicarEnCurso = new AtomicBoolean(false);

    private static final String[] DIAS_SEMANA = {"LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"};

    public HorariosController() {
        this(AppContainer.getDefault().resolve(HorariosService.class));
    }

    public HorariosController(HorariosService horariosService) {
        this.horariosService = Objects.requireNonNull(horariosService, "horariosService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        AnimationUtils.attachTactileFeedback(btnNuevoTurno);
        AnimationUtils.attachTactileFeedback(btnPublicarHorarios);
        AnimationUtils.attachTactileFeedback(btnAsignarRapido);

        cmbModalDia.setItems(FXCollections.observableArrayList(DIAS_SEMANA));
        cmbModalDia.setValue("LUNES");

        cmbModalTurno.setItems(FXCollections.observableArrayList("MANANA", "TARDE", "NOCHE", "PERSONALIZADO"));
        cmbModalTurno.setValue("MANANA");
        cmbModalTurno.setOnAction(e -> actualizarHorasPredeterminadas());

        configurarListaColaboradores();
        cargarDatos();
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
    }

    private void actualizarHorasPredeterminadas() {
        String t = cmbModalTurno.getValue();
        if ("TARDE".equalsIgnoreCase(t)) {
            txtModalInicio.setText("14:00");
            txtModalFin.setText("22:00");
        } else if ("NOCHE".equalsIgnoreCase(t)) {
            txtModalInicio.setText("18:00");
            txtModalFin.setText("02:00");
        } else {
            txtModalInicio.setText("08:00");
            txtModalFin.setText("16:00");
        }
    }

    private void configurarListaColaboradores() {
        listColaboradores.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox box = new VBox(2);
                    String nombre = u.getNombreEmpleado() != null && !u.getNombreEmpleado().isBlank()
                            ? u.getNombreEmpleado()
                            : u.getUsername();
                    Label lblNom = new Label(nombre);
                    lblNom.getStyleClass().add("fc-schedule-emp-name");
                    Label lblRol = new Label((u.getNombreRol() != null ? u.getNombreRol() : "COLABORADOR")
                            + " • " + (u.getHorarioLaboral() != null ? u.getHorarioLaboral() : "08:00-16:00"));
                    lblRol.getStyleClass().add("fc-schedule-emp-role");
                    box.getChildren().addAll(lblNom, lblRol);
                    setGraphic(box);
                }
            }
        });
        listColaboradores.setItems(listaUsuarios);
    }

    public void cargarDatos() {
        long seq = cargaDatosSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> new DatosHorariosCompletos(
                    horariosService.listarColaboradoresDisponibles(),
                    horariosService.listarSemana(),
                    horariosService.evaluarAlertasCobertura()),
            datos -> {
                if (!vistaActiva.get() || seq != cargaDatosSeq.get()) {
                    return;
                }
                List<Usuario> usuarios = datos != null && datos.usuarios() != null
                        ? datos.usuarios() : List.of();
                listaUsuarios.setAll(usuarios);
                if (cmbModalColaborador != null) {
                    cmbModalColaborador.setItems(FXCollections.observableArrayList(usuarios));
                    if (!usuarios.isEmpty()) {
                        cmbModalColaborador.setValue(usuarios.get(0));
                    }
                }
                renderizarGrillaEnUi(datos != null ? datos.turnos() : List.of());
                aplicarAlertasEnUi(datos != null ? datos.alertas() : List.of());
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error al cargar horarios semanales", ex);
                if (vistaActiva.get()) {
                    mostrarErrorBannerYToast("Error al cargar horarios: " + ex.getMessage());
                }
            }
        );
    }

    private void recargarGrillaYAlertasAsync() {
        long seq = cargaGrillaSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> new DatosSemana(horariosService.listarSemana(), horariosService.evaluarAlertasCobertura()),
            datos -> {
                if (!vistaActiva.get() || seq != cargaGrillaSeq.get()) {
                    return;
                }
                renderizarGrillaEnUi(datos != null ? datos.turnos() : List.of());
                aplicarAlertasEnUi(datos != null ? datos.alertas() : List.of());
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error al recargar grilla de horarios", ex);
                if (vistaActiva.get()) {
                    mostrarErrorBannerYToast("Error al actualizar turnos: " + ex.getMessage());
                }
            }
        );
    }

    private void renderizarGrillaEnUi(List<TurnoProgramado> turnos) {
        if (gridSemana == null) {
            return;
        }
        gridSemana.getChildren().clear();

        List<TurnoProgramado> seguros = turnos != null ? turnos : List.of();
        Map<String, List<TurnoProgramado>> mapaPorDia = seguros.stream()
                .collect(Collectors.groupingBy(t -> t.getDiaSemana().toUpperCase()));

        for (int col = 0; col < DIAS_SEMANA.length; col++) {
            String dia = DIAS_SEMANA[col];
            List<TurnoProgramado> turnosDia = mapaPorDia.getOrDefault(dia, List.of());

            // Cabecera del Día
            VBox headerBox = new VBox(2);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.getStyleClass().add("fc-schedule-header");
            Label lblDia = new Label(dia);
            lblDia.getStyleClass().add("fc-schedule-day-title");
            Label lblCount = new Label(turnosDia.size() + " en turno");
            lblCount.getStyleClass().add("fc-schedule-day-count");
            headerBox.getChildren().addAll(lblDia, lblCount);
            gridSemana.add(headerBox, col, 0);

            // Contenedor de Turnos del Día
            VBox colContent = new VBox(8);
            colContent.setMinWidth(140);
            colContent.setPadding(new Insets(6, 0, 6, 0));
            colContent.getStyleClass().add("fc-schedule-col");

            if (turnosDia.isEmpty()) {
                Label lblVacio = new Label("Sin asignación");
                lblVacio.getStyleClass().add("fc-schedule-empty");
                colContent.getChildren().add(lblVacio);
            } else {
                for (TurnoProgramado tp : turnosDia) {
                    VBox cardTurno = crearTarjetaTurno(tp);
                    colContent.getChildren().add(cardTurno);
                }
            }

            ScrollPane spCol = new ScrollPane(colContent);
            spCol.setFitToWidth(true);
            spCol.getStyleClass().add("fc-schedule-scroll");
            gridSemana.add(spCol, col, 1);
        }
    }

    private VBox crearTarjetaTurno(TurnoProgramado tp) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.getStyleClass().add("fc-schedule-card");

        HBox topH = new HBox(4);
        topH.setAlignment(Pos.CENTER_LEFT);

        String textoTurno = "MANANA".equalsIgnoreCase(tp.getTurno()) ? "MAÑANA" : tp.getTurno();
        Label lblTurnoBadge = new Label(textoTurno);
        lblTurnoBadge.setMinWidth(Region.USE_PREF_SIZE);
        lblTurnoBadge.getStyleClass().removeAll(
                "fc-shift-badge-morning", "fc-shift-badge-afternoon",
                "fc-shift-badge-night", "fc-shift-badge-off");
        if ("MANANA".equalsIgnoreCase(tp.getTurno())) {
            lblTurnoBadge.getStyleClass().add("fc-shift-badge-morning");
        } else if ("TARDE".equalsIgnoreCase(tp.getTurno())) {
            lblTurnoBadge.getStyleClass().add("fc-shift-badge-afternoon");
        } else if ("NOCHE".equalsIgnoreCase(tp.getTurno())) {
            lblTurnoBadge.getStyleClass().add("fc-shift-badge-night");
        } else {
            lblTurnoBadge.getStyleClass().add("fc-shift-badge-off");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnEliminar = new Button("✕");
        btnEliminar.getStyleClass().add("fc-schedule-delete-btn");
        btnEliminar.setOnAction(e -> handleEliminarTurno(tp.getIdTurnoProg(), btnEliminar));

        topH.getChildren().addAll(lblTurnoBadge, spacer, btnEliminar);

        Label lblNom = new Label(tp.getNombreUsuario());
        lblNom.getStyleClass().add("fc-schedule-emp-name");
        lblNom.setWrapText(true);
        lblNom.setMaxWidth(Double.MAX_VALUE);
        lblNom.setTooltip(new javafx.scene.control.Tooltip(
                tp.getNombreUsuario() + " (" + (tp.getRol() != null ? tp.getRol() : "PERSONAL") + ")"));

        Label lblRolHorario = new Label((tp.getRol() != null ? tp.getRol() : "PERSONAL") + "\n" + tp.getHorarioTexto());
        lblRolHorario.getStyleClass().add("fc-schedule-turno-detail");
        lblRolHorario.setWrapText(true);
        lblRolHorario.setMaxWidth(Double.MAX_VALUE);
        lblRolHorario.setTooltip(new javafx.scene.control.Tooltip(
                "Turno: " + tp.getTurno() + " | " + tp.getHorarioTexto()));

        card.getChildren().addAll(topH, lblNom, lblRolHorario);
        return card;
    }

    void eliminarTurnoAsync(int idTurnoProg, Button trigger) {
        handleEliminarTurno(idTurnoProg, trigger);
    }

    private void handleEliminarTurno(int idTurnoProg, Button trigger) {
        if (!eliminarTurnoEnCurso.compareAndSet(false, true)) {
            return;
        }
        if (trigger != null) {
            trigger.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> horariosService.eliminarTurno(idTurnoProg),
            () -> {
                eliminarTurnoEnCurso.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                recargarGrillaYAlertasAsync();
            },
            ex -> {
                eliminarTurnoEnCurso.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error al eliminar turno programado", ex);
                if (vistaActiva.get()) {
                    mostrarErrorBannerYToast("Error al eliminar turno: " + ex.getMessage());
                }
            }
        );
    }

    private void aplicarAlertasEnUi(List<String> alertas) {
        if (bannerAlertasCobertura == null || lblTextoAlerta == null) {
            return;
        }
        if (alertas == null || alertas.isEmpty()) {
            bannerAlertasCobertura.setVisible(false);
            bannerAlertasCobertura.setManaged(false);
        } else {
            bannerAlertasCobertura.getStyleClass().remove("fc-banner-success");
            bannerAlertasCobertura.setVisible(true);
            bannerAlertasCobertura.setManaged(true);
            lblTextoAlerta.setText("Atención de Cobertura: " + String.join(" | ", alertas));
        }
    }

    @FXML
    public void handleNuevoTurnoModal() {
        modalTurno.setVisible(true);
        AnimationUtils.animateModalEnter(modalTurno);
    }

    @FXML
    public void handleCerrarModal() {
        modalTurno.setVisible(false);
    }

    @FXML
    public void handleCerrarModalTurno() {
        handleCerrarModal();
    }

    @FXML
    public void handleGuardarTurno() {
        Usuario u = cmbModalColaborador != null ? cmbModalColaborador.getValue() : null;
        if (u == null) {
            mostrarErrorBannerYToast("Seleccione un colaborador para asignar el turno.");
            return;
        }
        String dia = cmbModalDia != null ? cmbModalDia.getValue() : null;
        String turno = cmbModalTurno != null ? cmbModalTurno.getValue() : null;

        LocalTime ini;
        LocalTime fin;
        try {
            ini = LocalTime.parse(txtModalInicio != null && txtModalInicio.getText() != null
                    ? txtModalInicio.getText().trim() : "");
            fin = LocalTime.parse(txtModalFin != null && txtModalFin.getText() != null
                    ? txtModalFin.getText().trim() : "");
        } catch (DateTimeParseException ex) {
            LOGGER.log(Level.FINE, "Formato de hora inválido al guardar turno", ex);
            mostrarErrorBannerYToast("Formato de hora inválido. Use HH:mm (ejemplo 08:00).");
            return;
        }

        if (!guardarTurnoEnCurso.compareAndSet(false, true)) {
            return;
        }
        int idUsuario = u.getIdUsuario();
        setControlesGuardarTurnoDeshabilitados(true);

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> horariosService.asignarTurno(idUsuario, dia, turno, ini, fin),
            () -> {
                guardarTurnoEnCurso.set(false);
                setControlesGuardarTurnoDeshabilitados(false);
                if (!vistaActiva.get()) {
                    return;
                }
                if (modalTurno != null) {
                    modalTurno.setVisible(false);
                }
                recargarGrillaYAlertasAsync();
            },
            ex -> {
                guardarTurnoEnCurso.set(false);
                setControlesGuardarTurnoDeshabilitados(false);
                LOGGER.log(Level.WARNING, "Error al guardar turno", ex);
                if (vistaActiva.get()) {
                    mostrarErrorBannerYToast("Error al guardar turno: " + ex.getMessage());
                }
            }
        );
    }

    private void setControlesGuardarTurnoDeshabilitados(boolean disabled) {
        if (btnGuardarTurno != null) {
            btnGuardarTurno.setDisable(disabled);
        }
        if (modalTurno != null) {
            modalTurno.setDisable(disabled);
        }
    }

    @FXML
    public void handleGuardarTurnoModal() {
        handleGuardarTurno();
    }

    @FXML
    public void handleAsignarRapido() {
        Usuario sel = listColaboradores.getSelectionModel().getSelectedItem();
        if (sel != null) {
            cmbModalColaborador.setValue(sel);
            handleNuevoTurnoModal();
        }
    }

    @FXML
    public void handlePublicarHorarios() {
        if (!publicarEnCurso.compareAndSet(false, true)) {
            return;
        }
        if (btnPublicarHorarios != null) {
            btnPublicarHorarios.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            horariosService::publicarHorariosSemana,
            () -> {
                publicarEnCurso.set(false);
                if (btnPublicarHorarios != null) {
                    btnPublicarHorarios.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                if (lblTextoAlerta != null) {
                    lblTextoAlerta.setText("✓ ¡Horarios de la semana publicados con éxito para todo el personal!");
                }
                if (bannerAlertasCobertura != null) {
                    if (!bannerAlertasCobertura.getStyleClass().contains("fc-banner-success")) {
                        bannerAlertasCobertura.getStyleClass().add("fc-banner-success");
                    }
                    bannerAlertasCobertura.setVisible(true);
                    bannerAlertasCobertura.setManaged(true);
                }
                mostrarToastSeguro("Horarios semanales publicados con éxito.", false);
            },
            ex -> {
                publicarEnCurso.set(false);
                if (btnPublicarHorarios != null) {
                    btnPublicarHorarios.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error al publicar horarios", ex);
                if (vistaActiva.get()) {
                    mostrarErrorBannerYToast("Error al publicar horarios: " + ex.getMessage());
                }
            }
        );
    }

    private void mostrarErrorBannerYToast(String mensaje) {
        if (bannerAlertasCobertura != null && lblTextoAlerta != null) {
            bannerAlertasCobertura.getStyleClass().remove("fc-banner-success");
            lblTextoAlerta.setText(mensaje);
            bannerAlertasCobertura.setVisible(true);
            bannerAlertasCobertura.setManaged(true);
        }
        mostrarToastSeguro(mensaje, true);
    }

    private void mostrarToastSeguro(String mensaje, boolean esError) {
        if (rootHorariosStack != null && rootHorariosStack.getScene() != null) {
            AnimationUtils.mostrarToast(rootHorariosStack, mensaje, esError);
        } else {
            ToastService.mostrarToast(
                    null,
                    mensaje,
                    esError ? ToastService.TipoToast.ERROR : ToastService.TipoToast.EXITO);
        }
    }

    public ListView<Usuario> getListColaboradores() {
        return listColaboradores;
    }

    public GridPane getGridSemana() {
        return gridSemana;
    }

    public Button getBtnPublicarHorarios() {
        return btnPublicarHorarios;
    }

    public Button getBtnGuardarTurno() {
        return btnGuardarTurno;
    }

    public Label getLblTextoAlerta() {
        return lblTextoAlerta;
    }

    public ComboBox<Usuario> getCmbModalColaborador() {
        return cmbModalColaborador;
    }

    public TextField getTxtModalInicio() {
        return txtModalInicio;
    }

    public TextField getTxtModalFin() {
        return txtModalFin;
    }
}
