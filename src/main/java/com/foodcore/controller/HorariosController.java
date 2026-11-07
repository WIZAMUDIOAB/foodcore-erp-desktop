package com.foodcore.controller;

import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.service.HorariosService;
import com.foodcore.util.AnimationUtils;
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
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HorariosController {

    private static final Logger LOGGER = Logger.getLogger(HorariosController.class.getName());

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

    private final HorariosService horariosService = new HorariosService();
    private final ObservableList<Usuario> listaUsuarios = FXCollections.observableArrayList();

    private static final String[] DIAS_SEMANA = {"LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"};

    @FXML
    public void initialize() {
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
                    lblNom.setStyle("-fx-font-weight: bold; -fx-text-fill: -fc-text-primary;");
                    Label lblRol = new Label((u.getNombreRol() != null ? u.getNombreRol() : "COLABORADOR")
                            + " • " + (u.getHorarioLaboral() != null ? u.getHorarioLaboral() : "08:00-16:00"));
                    lblRol.setStyle("-fx-font-size: 11px; -fx-text-fill: -fc-text-secondary;");
                    box.getChildren().addAll(lblNom, lblRol);
                    setGraphic(box);
                }
            }
        });
        listColaboradores.setItems(listaUsuarios);
    }

    public void cargarDatos() {
        try {
            List<Usuario> usuarios = horariosService.listarColaboradoresDisponibles();
            listaUsuarios.setAll(usuarios);
            cmbModalColaborador.setItems(FXCollections.observableArrayList(usuarios));
            if (!usuarios.isEmpty()) {
                cmbModalColaborador.setValue(usuarios.get(0));
            }

            renderizarGrillaSemanal();
            evaluarAlertas();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar horarios semanales", ex);
        }
    }

    private void renderizarGrillaSemanal() throws SQLException {
        gridSemana.getChildren().clear();

        List<TurnoProgramado> turnos = horariosService.listarSemana();
        Map<String, List<TurnoProgramado>> mapaPorDia = turnos.stream()
                .collect(Collectors.groupingBy(t -> t.getDiaSemana().toUpperCase()));

        for (int col = 0; col < DIAS_SEMANA.length; col++) {
            String dia = DIAS_SEMANA[col];
            List<TurnoProgramado> turnosDia = mapaPorDia.getOrDefault(dia, List.of());

            // Cabecera del Día
            VBox headerBox = new VBox(2);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.setStyle("-fx-background-color: -fc-surface-card; -fx-padding: 8 6; -fx-background-radius: 8; -fx-border-color: -fc-border; -fx-border-radius: 8;");
            Label lblDia = new Label(dia);
            lblDia.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -fc-text-primary;");
            Label lblCount = new Label(turnosDia.size() + " en turno");
            lblCount.setStyle("-fx-font-size: 11px; -fx-text-fill: -fc-text-secondary;");
            headerBox.getChildren().addAll(lblDia, lblCount);
            gridSemana.add(headerBox, col, 0);

            // Contenedor de Turnos del Día
            VBox colContent = new VBox(8);
            colContent.setMinWidth(140);
            colContent.setPadding(new Insets(6, 0, 6, 0));
            colContent.setStyle("-fx-background-color: #F8FAFC; -fx-padding: 8; -fx-background-radius: 8;");

            if (turnosDia.isEmpty()) {
                Label lblVacio = new Label("Sin asignación");
                lblVacio.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic; -fx-font-size: 11px;");
                colContent.getChildren().add(lblVacio);
            } else {
                for (TurnoProgramado tp : turnosDia) {
                    VBox cardTurno = crearTarjetaTurno(tp);
                    colContent.getChildren().add(cardTurno);
                }
            }

            ScrollPane spCol = new ScrollPane(colContent);
            spCol.setFitToWidth(true);
            spCol.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            gridSemana.add(spCol, col, 1);
        }
    }

    private VBox crearTarjetaTurno(TurnoProgramado tp) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.03), 4, 0, 0, 1);");

        HBox topH = new HBox(4);
        topH.setAlignment(Pos.CENTER_LEFT);

        String textoTurno = "MANANA".equalsIgnoreCase(tp.getTurno()) ? "MAÑANA" : tp.getTurno();
        Label lblTurnoBadge = new Label(textoTurno);
        lblTurnoBadge.setMinWidth(Region.USE_PREF_SIZE);
        if ("MANANA".equalsIgnoreCase(tp.getTurno())) {
            lblTurnoBadge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 6;");
        } else if ("TARDE".equalsIgnoreCase(tp.getTurno())) {
            lblTurnoBadge.setStyle("-fx-background-color: #E0E7FF; -fx-text-fill: #3730A3; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 6;");
        } else {
            lblTurnoBadge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #334155; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 6;");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnEliminar = new Button("✕");
        btnEliminar.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 0 4;");
        btnEliminar.setOnAction(e -> handleEliminarTurno(tp.getIdTurnoProg()));

        topH.getChildren().addAll(lblTurnoBadge, spacer, btnEliminar);

        Label lblNom = new Label(tp.getNombreUsuario());
        lblNom.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -fc-text-primary;");
        lblNom.setWrapText(true);
        lblNom.setMaxWidth(Double.MAX_VALUE);
        lblNom.setTooltip(new javafx.scene.control.Tooltip(tp.getNombreUsuario() + " (" + (tp.getRol() != null ? tp.getRol() : "PERSONAL") + ")"));

        Label lblRolHorario = new Label((tp.getRol() != null ? tp.getRol() : "PERSONAL") + "\n" + tp.getHorarioTexto());
        lblRolHorario.setStyle("-fx-font-size: 10px; -fx-text-fill: -fc-text-secondary; -fx-line-spacing: 1px;");
        lblRolHorario.setWrapText(true);
        lblRolHorario.setMaxWidth(Double.MAX_VALUE);
        lblRolHorario.setTooltip(new javafx.scene.control.Tooltip("Turno: " + tp.getTurno() + " | " + tp.getHorarioTexto()));

        card.getChildren().addAll(topH, lblNom, lblRolHorario);
        return card;
    }

    private void handleEliminarTurno(int idTurnoProg) {
        try {
            horariosService.eliminarTurno(idTurnoProg);
            renderizarGrillaSemanal();
            evaluarAlertas();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al eliminar turno programado", ex);
        }
    }

    private void evaluarAlertas() throws SQLException {
        List<String> alertas = horariosService.evaluarAlertasCobertura();
        if (alertas.isEmpty()) {
            bannerAlertasCobertura.setVisible(false);
            bannerAlertasCobertura.setManaged(false);
        } else {
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
        Usuario u = cmbModalColaborador.getValue();
        if (u == null) return;
        String dia = cmbModalDia.getValue();
        String turno = cmbModalTurno.getValue();

        try {
            LocalTime ini = LocalTime.parse(txtModalInicio.getText().trim());
            LocalTime fin = LocalTime.parse(txtModalFin.getText().trim());
            horariosService.asignarTurno(u.getIdUsuario(), dia, turno, ini, fin);
            modalTurno.setVisible(false);
            renderizarGrillaSemanal();
            evaluarAlertas();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al guardar turno", ex);
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
        try {
            horariosService.publicarHorariosSemana();
            lblTextoAlerta.setText("✓ ¡Horarios de la semana publicados con éxito para todo el personal!");
            bannerAlertasCobertura.setStyle("-fx-background-color: -fc-success-bg; -fx-padding: 10 16; -fx-background-radius: 8; -fx-border-color: -fc-success-border; -fx-border-radius: 8;");
            bannerAlertasCobertura.setVisible(true);
            bannerAlertasCobertura.setManaged(true);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al publicar horarios", ex);
        }
    }
}
