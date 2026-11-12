package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.model.Asistencia;
import com.foodcore.service.AsistenciaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
            List<Asistencia> todos = asistenciaService.listarAsistenciaPorFecha(f);

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
}
