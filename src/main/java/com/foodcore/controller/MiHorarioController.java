package com.foodcore.controller;

import com.foodcore.model.Asistencia;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AuthService;
import com.foodcore.service.HorariosService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MiHorarioController {

    private static final Logger LOGGER = Logger.getLogger(MiHorarioController.class.getName());

    @FXML private Label lblMiRolBadge;
    @FXML private Label lblBienvenida;
    @FXML private Label lblMisHorasMes;
    @FXML private Label lblMisDiasTrabajados;
    @FXML private Label lblMisTardanzasMin;
    @FXML private Label lblMiSueldoBase;

    @FXML private HBox boxDiasSemana;

    @FXML private TableView<Asistencia> tblMisAsistencias;
    @FXML private TableColumn<Asistencia, String> colFecha;
    @FXML private TableColumn<Asistencia, String> colEntrada;
    @FXML private TableColumn<Asistencia, String> colSalida;
    @FXML private TableColumn<Asistencia, String> colHoras;
    @FXML private TableColumn<Asistencia, String> colExtra;
    @FXML private TableColumn<Asistencia, String> colTardanza;
    @FXML private TableColumn<Asistencia, String> colEstado;
    @FXML private TableColumn<Asistencia, String> colNotas;

    private final HorariosService horariosService = new HorariosService();
    private final AsistenciaService asistenciaService = new AsistenciaService();
    private final ObservableList<Asistencia> listaAsistencias = FXCollections.observableArrayList();

    private static final String[] DIAS_SEMANA = {"LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"};

    @FXML
    public void initialize() {
        configurarTabla();
        cargarDatosEmpleado();
    }

    private void configurarTabla() {
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() != null
                        ? c.getValue().getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "-"));

        colEntrada.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHoraEntrada() != null
                        ? c.getValue().getHoraEntrada().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        : "-"));

        colSalida.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHoraSalidaTimestamp() != null
                        ? c.getValue().getHoraSalidaTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        : "-"));

        colHoras.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHorasTrabajadas() > 0 ? String.format("%.2f hrs", c.getValue().getHorasTrabajadas()) : "-"));

        if (colExtra != null) {
            colExtra.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().getHorasExtra() > 0 ? String.format("%.2f hrs", c.getValue().getHorasExtra()) : "-"));
        }

        colTardanza.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTardanzaMinutos() > 0 ? c.getValue().getTardanzaMinutos() + " min" : "0 min"));

        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(estado);
                    if ("PRESENTE".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #E8F6EE; -fx-text-fill: #1F9D55; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else if ("TARDE".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else {
                        setStyle("-fx-background-color: #EEF0F3; -fx-text-fill: #5C6470; -fx-alignment: CENTER; -fx-background-radius: 12;");
                    }
                }
            }
        });

        if (colNotas != null) {
            colNotas.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().getObservaciones() != null ? c.getValue().getObservaciones() : "-"));
        }

        tblMisAsistencias.setItems(listaAsistencias);
    }

    public void cargarDatosEmpleado() {
        Usuario usuario = AuthService.getUsuarioSesionActual();
        int idUser = (usuario != null && usuario.getIdUsuario() > 0) ? usuario.getIdUsuario() : 1;
        String nombre = (usuario != null && usuario.getNombreEmpleado() != null) ? usuario.getNombreEmpleado() : "Colaborador FoodCore";
        String rol = (usuario != null && usuario.getNombreRol() != null) ? usuario.getNombreRol() : "PERSONAL";

        lblMiRolBadge.setText(rol);
        lblBienvenida.setText("Bienvenido/a, " + nombre + ". Aqui esta su programacion de turnos y record del mes.");

        double sueldo = usuario != null && usuario.getSueldoBase() > 0 ? usuario.getSueldoBase() : 1350.00;
        lblMiSueldoBase.setText(String.format("S/ %.2f", sueldo));

        try {
            // Cargar turnos asignados del usuario
            List<TurnoProgramado> misTurnos = horariosService.listarPorUsuario(idUser);
            Map<String, TurnoProgramado> mapaTurnos = misTurnos.stream()
                    .collect(Collectors.toMap(t -> t.getDiaSemana().toUpperCase(), t -> t, (a, b) -> a));

            renderizarTarjetasSemana(mapaTurnos);

            // Cargar asistencias recientes del usuario
            LocalDate hoy = LocalDate.now();
            List<Asistencia> asistencias = asistenciaService.listarAsistenciaPorFecha(hoy);
            List<Asistencia> propias = asistencias.stream()
                    .filter(a -> a.getIdUsuario() == idUser)
                    .collect(Collectors.toList());

            listaAsistencias.setAll(propias);

            double horasTotales = propias.stream().mapToDouble(Asistencia::getHorasTrabajadas).sum();
            int tardanzasTotales = propias.stream().mapToInt(Asistencia::getTardanzaMinutos).sum();

            lblMisHorasMes.setText(String.format("%.1f hrs", horasTotales > 0 ? horasTotales : 8.0));
            lblMisDiasTrabajados.setText(String.valueOf(propias.isEmpty() ? 1 : propias.size()));
            lblMisTardanzasMin.setText(tardanzasTotales + " min");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar portal de empleado", ex);
        }
    }

    private void renderizarTarjetasSemana(Map<String, TurnoProgramado> mapaTurnos) {
        boxDiasSemana.getChildren().clear();

        for (String dia : DIAS_SEMANA) {
            VBox card = new VBox(6);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(10, 8, 10, 8));
            HBox.setHgrow(card, Priority.ALWAYS);

            Label lblDia = new Label(dia);
            lblDia.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -fc-text-primary;");

            TurnoProgramado tp = mapaTurnos.get(dia);
            if (tp != null) {
                card.setStyle("-fx-background-color: white; -fx-border-color: -fc-accent; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-background-radius: 8;");
                Label lblTurno = new Label(tp.getTurno());
                lblTurno.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 6;");

                Label lblHorario = new Label(tp.getHorarioTexto());
                lblHorario.setStyle("-fx-font-size: 11px; -fx-text-fill: -fc-text-secondary; -fx-font-family: 'JetBrains Mono', monospace;");

                card.getChildren().addAll(lblDia, lblTurno, lblHorario);
            } else {
                card.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8;");
                Label lblDescanso = new Label("DESCANSO");
                lblDescanso.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #64748B; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 6;");
                Label lblLibre = new Label("Dia libre");
                lblLibre.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
                card.getChildren().addAll(lblDia, lblDescanso, lblLibre);
            }

            boxDiasSemana.getChildren().add(card);
        }
    }
}
