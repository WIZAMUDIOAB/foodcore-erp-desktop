package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.model.Asistencia;
import com.foodcore.model.CumplimientoTurnoFila;
import com.foodcore.model.ReporteCumplimientoTurno;
import com.foodcore.service.AsistenciaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AsistenciaController {

    private static final Logger LOGGER = Logger.getLogger(AsistenciaController.class.getName());

    @FXML private Label lblKpiActivos;
    @FXML private Label lblKpiPuntuales;
    @FXML private Label lblKpiTardanzas;
    @FXML private Label lblKpiTardanzasMin;
    @FXML private Label lblKpiHorasTotales;

    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<String> cmbFiltroEstado;
    @FXML private Label lblTotalRegistros;
    @FXML private Button btnReporteCumplimiento;
    @FXML private Button btnAbrirKiosco;
    @FXML private Button btnExportarPlanilla;

    @FXML private TableView<Asistencia> tblAsistencia;
    @FXML private TableColumn<Asistencia, String> colHora;
    @FXML private TableColumn<Asistencia, String> colEmpleado;
    @FXML private TableColumn<Asistencia, String> colDni;
    @FXML private TableColumn<Asistencia, String> colRol;
    @FXML private TableColumn<Asistencia, String> colEntrada;
    @FXML private TableColumn<Asistencia, String> colSalida;
    @FXML private TableColumn<Asistencia, String> colHoras;
    @FXML private TableColumn<Asistencia, String> colExtra;
    @FXML private TableColumn<Asistencia, String> colTardanza;
    @FXML private TableColumn<Asistencia, String> colTurnoProgramado;
    @FXML private TableColumn<Asistencia, String> colCumplimientoTurno;
    @FXML private TableColumn<Asistencia, String> colEstado;
    @FXML private TableColumn<Asistencia, String> colObservaciones;

    private final AsistenciaService asistenciaService = new AsistenciaService();
    private final ObservableList<Asistencia> listaAsistencias = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        dpFecha.setValue(LocalDate.now());
        cmbFiltroEstado.setItems(FXCollections.observableArrayList("TODOS", "PRESENTE", "TARDE", "FINALIZADO"));
        cmbFiltroEstado.setValue("TODOS");

        configurarTabla();
        AnimationUtils.attachTactileFeedback(btnReporteCumplimiento);
        AnimationUtils.attachTactileFeedback(btnAbrirKiosco);
        AnimationUtils.attachTactileFeedback(btnExportarPlanilla);

        cargarDatos();
    }

    private void configurarTabla() {
        colHora.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHoraEntrada() != null
                        ? c.getValue().getHoraEntrada().format(DateTimeFormatter.ofPattern("HH:mm"))
                        : "-"));

        colEmpleado.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNombreEmpleado() != null && !c.getValue().getNombreEmpleado().isBlank()
                        ? c.getValue().getNombreEmpleado()
                        : c.getValue().getNombreUsuario()));

        colDni.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDniEmpleado() != null ? c.getValue().getDniEmpleado() : "-"));

        colRol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRol() != null ? c.getValue().getRol() : "PERSONAL"));

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

        colExtra.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHorasExtra() > 0 ? String.format("%.2f hrs", c.getValue().getHorasExtra()) : "-"));

        colTardanza.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTardanzaMinutos() > 0 ? c.getValue().getTardanzaMinutos() + " min" : "0 min"));

        if (colTurnoProgramado != null) {
            colTurnoProgramado.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().getTurnoProgramadoTexto() != null
                            ? c.getValue().getTurnoProgramadoTexto() : "Sin turno"));
        }
        if (colCumplimientoTurno != null) {
            colCumplimientoTurno.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().getCumplimientoTurno() != null
                            ? c.getValue().getCumplimientoTurno() : "SIN TURNO"));
            colCumplimientoTurno.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                        return;
                    }
                    setText(item);
                    String upper = item.toUpperCase();
                    if (upper.startsWith("A TIEMPO")) {
                        setStyle("-fx-background-color: #E8F6EE; -fx-text-fill: #059669; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else if (upper.startsWith("ATRASO") || upper.contains("FALTA")) {
                        setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else if (upper.startsWith("SALIDA")) {
                        setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else {
                        setStyle("-fx-background-color: #EEF0F3; -fx-text-fill: #5C6470; -fx-alignment: CENTER; -fx-background-radius: 12;");
                    }
                }
            });
        }

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

        colObservaciones.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getObservaciones() != null ? c.getValue().getObservaciones() : "-"));

        tblAsistencia.setItems(listaAsistencias);
    }

    @FXML
    public void handleFiltrar() {
        cargarDatos();
    }

    public void cargarDatos() {
        LocalDate f = dpFecha.getValue() != null ? dpFecha.getValue() : LocalDate.now();
        try {
            List<Asistencia> todos = asistenciaService.listarAsistenciaConCumplimiento(f);

            // Calcular KPIs sobre todos los marcajes del día
            long activos = todos.stream().filter(a -> a.getHoraSalidaTimestamp() == null).count();
            long puntuales = todos.stream().filter(a -> a.getTardanzaMinutos() == 0).count();
            long tardanzas = todos.stream().filter(a -> a.getTardanzaMinutos() > 0).count();
            int tardanzasMinTotal = todos.stream().mapToInt(Asistencia::getTardanzaMinutos).sum();
            double horasTotal = todos.stream().mapToDouble(Asistencia::getHorasTrabajadas).sum();

            lblKpiActivos.setText(String.valueOf(activos));
            lblKpiPuntuales.setText(String.valueOf(puntuales));
            lblKpiTardanzas.setText(String.valueOf(tardanzas));
            lblKpiTardanzasMin.setText(tardanzasMinTotal + " minutos acumulados");
            lblKpiHorasTotales.setText(String.format("%.1f hrs", horasTotal));

            // Filtro por Estado
            String filtroEst = cmbFiltroEstado.getValue();
            List<Asistencia> filtrados = todos;
            if (filtroEst != null && !"TODOS".equalsIgnoreCase(filtroEst)) {
                filtrados = todos.stream()
                        .filter(a -> filtroEst.equalsIgnoreCase(a.getEstado()))
                        .collect(Collectors.toList());
            }

            listaAsistencias.setAll(filtrados);
            lblTotalRegistros.setText(filtrados.size() + " registros encontrados");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar asistencias", ex);
        }
    }

    @FXML
    public void handleAbrirKiosco() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/RelojAsistenciaView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Kiosco de Control de Asistencia — FoodCore");

            Scene scene = new Scene(root, 720, 680);
            App.applyStylesheets(scene);

            AnimationUtils.animateModalEnter(root);
            stage.setScene(scene);
            stage.setOnHidden(e -> cargarDatos());
            stage.show();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al abrir vista Kiosco de Asistencia", ex);
        }
    }

    @FXML
    public void handleExportarPlanillaExcel() {
        LocalDate f = dpFecha.getValue() != null ? dpFecha.getValue() : LocalDate.now();
        int anio = f.getYear();
        int mes = f.getMonthValue();

        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                btnExportarPlanilla != null ? btnExportarPlanilla : lblTotalRegistros,
                "Guardar Planilla Mensual en Excel",
                "PLANILLA_MENSUAL_" + anio + "_" + String.format("%02d", mes) + ".xlsx",
                FormatoExportacion.EXCEL);
        if (elegidoOpt.isEmpty()) {
            if (lblTotalRegistros != null) {
                lblTotalRegistros.setText("Exportación de planilla cancelada.");
            }
            return;
        }

        try {
            File doc = elegidoOpt.get();
            if (doc.getParentFile() != null) {
                Files.createDirectories(doc.getParentFile().toPath());
            }
            asistenciaService.exportarPlanillaMensualExcel(anio, mes, doc);
            if (lblTotalRegistros != null) {
                lblTotalRegistros.setText("✓ Planilla exportada con éxito: " + doc.getName());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al exportar planilla mensual Excel", ex);
            if (lblTotalRegistros != null) {
                lblTotalRegistros.setText("Error exportando planilla: " + ex.getMessage());
            }
        }
    }

    @FXML
    public void handleAbrirReporteAtrasosYFaltas() {
        LocalDate ref = dpFecha != null && dpFecha.getValue() != null ? dpFecha.getValue() : LocalDate.now();
        LocalDate desde = ref.minusDays(6);
        UiAsyncExecutor.ejecutarAsync(
                () -> asistenciaService.generarReporteAtrasosYFaltas(desde, ref),
                rep -> {
                    Stage modal = construirStageReporteCumplimiento(rep);
                    modal.show();
                },
                ex -> {
                    LOGGER.log(Level.WARNING, "Error al generar reporte de atrasos y faltas", ex);
                    if (lblTotalRegistros != null) {
                        lblTotalRegistros.setText("Error generando reporte: " + ex.getMessage());
                    }
                }
        );
    }

    /** Construye el modal con el cruce turno_programado vs asistencia para exhibir Atrasos y Faltas. */
    public Stage construirStageReporteCumplimiento(ReporteCumplimientoTurno rep) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Reporte de Atrasos y Faltas vs Turnos Programados");

        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.setPrefSize(920, 560);
        root.getStyleClass().add("app-canvas");

        Label title = new Label("Cumplimiento de Turnos Programados (" + rep.desde() + " al " + rep.hasta() + ")");
        title.getStyleClass().add("view-heading");

        Label kpis = new Label(String.format(
                "Turnos evaluados: %d   |   A tiempo: %d   |   Atrasos: %d   |   Faltas: %d",
                rep.totalTurnosEvaluados(), rep.totalPuntuales(), rep.totalAtrasos(), rep.totalFaltas()));
        kpis.getStyleClass().add("badge-pill-accent");

        TableView<CumplimientoTurnoFila> tbl = new TableView<>();
        VBox.setVgrow(tbl, Priority.ALWAYS);

        TableColumn<CumplimientoTurnoFila, String> cFecha = new TableColumn<>("Fecha");
        cFecha.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().fecha() + " (" + d.getValue().diaSemana() + ")"));

        TableColumn<CumplimientoTurnoFila, String> cColab = new TableColumn<>("Colaborador");
        cColab.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nombreColaborador()));

        TableColumn<CumplimientoTurnoFila, String> cTurno = new TableColumn<>("Turno Prog.");
        cTurno.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().horarioProgramadoTexto()));

        TableColumn<CumplimientoTurnoFila, String> cIngreso = new TableColumn<>("Ingreso Real");
        cIngreso.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().horaIngresoReal() != null
                        ? d.getValue().horaIngresoReal().format(DateTimeFormatter.ofPattern("HH:mm"))
                        : "Sin marcaje"));

        TableColumn<CumplimientoTurnoFila, String> cEstado = new TableColumn<>("Cumplimiento");
        cEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().estadoCumplimiento()));
        cEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if ("A TIEMPO".equalsIgnoreCase(item)) {
                    setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                }
            }
        });

        tbl.getColumns().addAll(List.of(cFecha, cColab, cTurno, cIngreso, cEstado));
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tbl.getItems().setAll(rep.todasLasFilas());

        Button btnExportar = new Button("Exportar Reporte (.xlsx)");
        btnExportar.getStyleClass().add("btn-primary");
        btnExportar.setOnAction(ev -> {
            Optional<File> dest = ExportFileChooserHelper.elegirDestinoExportacion(
                    btnExportar,
                    "Exportar Reporte Atrasos y Faltas",
                    "REPORTE_ATRASOS_FALTAS_" + rep.hasta() + ".xlsx",
                    FormatoExportacion.EXCEL);
            dest.ifPresent(file -> {
                try {
                    asistenciaService.exportarReporteCumplimientoExcel(rep, file);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error exportando reporte cumplimiento", ex);
                }
            });
        });

        Button btnCerrar = new Button("Cerrar");
        btnCerrar.getStyleClass().add("btn-secondary");
        btnCerrar.setOnAction(ev -> stage.close());

        HBox footer = new HBox(10, new Region(), btnExportar, btnCerrar);
        HBox.setHgrow(footer.getChildren().get(0), Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, kpis, tbl, footer);
        Scene scene = new Scene(root);
        App.applyStylesheets(scene);
        stage.setScene(scene);
        return stage;
    }
}
