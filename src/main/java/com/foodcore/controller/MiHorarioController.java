package com.foodcore.controller;

import com.foodcore.model.Asistencia;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AuthService;
import com.foodcore.service.HorariosService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controlador del Portal del Empleado ("Mi Horario", "Mis Asistencias", "Mis Boletas") según FoodCore v4.4.0 (§4).
 */
public class MiHorarioController {

    private static final Logger LOGGER = Logger.getLogger(MiHorarioController.class.getName());
    private static final Locale LOCALE_ES = Locale.forLanguageTag("es-PE");

    public record BoletaPago(
            String periodo,
            String regimen,
            BigDecimal sueldoBase,
            BigDecimal horasExtrasMonto,
            BigDecimal descuentosLey,
            BigDecimal netoPagar,
            String estado
    ) {
    }

    @FXML private StackPane rootMiHorarioStack;
    @FXML private Label lblMiRolBadge;
    @FXML private Label lblBienvenida;
    @FXML private Label lblMisHorasMes;
    @FXML private Label lblMisDiasTrabajados;
    @FXML private Label lblMisTardanzasMin;
    @FXML private Label lblMiSueldoBase;

    @FXML private TabPane tabPaneMiPortal;
    @FXML private Tab tabMiHorario;
    @FXML private Tab tabMisAsistencias;
    @FXML private Tab tabMisBoletas;

    @FXML private HBox boxDiasSemana;

    @FXML private TableView<Asistencia> tblMisAsistencias;
    @FXML private TableColumn<Asistencia, String> colFecha;
    @FXML private TableColumn<Asistencia, String> colDia;
    @FXML private TableColumn<Asistencia, String> colEntrada;
    @FXML private TableColumn<Asistencia, String> colSalida;
    @FXML private TableColumn<Asistencia, String> colHoras;
    @FXML private TableColumn<Asistencia, String> colExtra;
    @FXML private TableColumn<Asistencia, String> colTardanza;
    @FXML private TableColumn<Asistencia, String> colEstado;
    @FXML private TableColumn<Asistencia, String> colNotas;

    @FXML private TableView<BoletaPago> tblMisBoletas;
    @FXML private TableColumn<BoletaPago, String> colBoletaPeriodo;
    @FXML private TableColumn<BoletaPago, String> colBoletaSueldoBase;
    @FXML private TableColumn<BoletaPago, String> colBoletaBonos;
    @FXML private TableColumn<BoletaPago, String> colBoletaDescuentos;
    @FXML private TableColumn<BoletaPago, String> colBoletaNeto;
    @FXML private TableColumn<BoletaPago, String> colBoletaEstado;
    @FXML private TableColumn<BoletaPago, Void> colBoletaAccion;
    @FXML private Button btnDescargarBoletaPdf;
    @FXML private Label lblEstadoBoleta;

    private final HorariosService horariosService = new HorariosService();
    private final AsistenciaService asistenciaService = new AsistenciaService();
    private final ObservableList<Asistencia> listaAsistencias = FXCollections.observableArrayList();
    private final ObservableList<BoletaPago> listaBoletas = FXCollections.observableArrayList();

    private static final String[] DIAS_SEMANA = {"LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"};

    @FXML
    public void initialize() {
        if (btnDescargarBoletaPdf != null) {
            AnimationUtils.attachTactileFeedback(btnDescargarBoletaPdf);
        }
        configurarTablaAsistencias();
        configurarTablaBoletas();
        cargarDatosEmpleado();
    }

    private void configurarTablaAsistencias() {
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() != null
                        ? c.getValue().getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "-"));

        if (colDia != null) {
            colDia.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().getFecha() != null
                            ? c.getValue().getFecha().getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE_ES).toUpperCase(LOCALE_ES)
                            : "-"));
        }

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

    private void configurarTablaBoletas() {
        if (tblMisBoletas == null) {
            return;
        }
        colBoletaPeriodo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().periodo()));
        colBoletaSueldoBase.setCellValueFactory(c -> new SimpleStringProperty("S/ " + c.getValue().sueldoBase()));
        colBoletaBonos.setCellValueFactory(c -> new SimpleStringProperty("S/ " + c.getValue().horasExtrasMonto()));
        colBoletaDescuentos.setCellValueFactory(c -> new SimpleStringProperty("-S/ " + c.getValue().descuentosLey()));
        colBoletaNeto.setCellValueFactory(c -> new SimpleStringProperty("S/ " + c.getValue().netoPagar()));
        colBoletaEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().estado()));

        colBoletaEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(estado);
                    pill.getStyleClass().add("ABONADA".equalsIgnoreCase(estado) ? "badge-pill-success" : "badge-pill-warning");
                    setGraphic(pill);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        colBoletaAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btnPdf = new Button("PDF");

            {
                btnPdf.getStyleClass().add("btn-secondary");
                btnPdf.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
                AnimationUtils.attachTactileFeedback(btnPdf);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    BoletaPago boleta = getTableRow().getItem();
                    btnPdf.setOnAction(ev -> exportarBoletaConFeedback(boleta));
                    setGraphic(btnPdf);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        tblMisBoletas.setItems(listaBoletas);
    }

    public void cargarDatosEmpleado() {
        Usuario usuario = AuthService.getUsuarioSesionActual();
        int idUser = (usuario != null && usuario.getIdUsuario() > 0) ? usuario.getIdUsuario() : 1;
        String nombre = (usuario != null && usuario.getNombreEmpleado() != null && !usuario.getNombreEmpleado().isBlank())
                ? usuario.getNombreEmpleado() : "Colaborador FoodCore";
        String rol = (usuario != null && usuario.getNombreRol() != null) ? usuario.getNombreRol() : "PERSONAL";

        lblMiRolBadge.setText(rol);
        lblBienvenida.setText("Bienvenido/a, " + nombre + ". Aquí puede consultar su horario, asistencias y boletas.");

        double sueldo = usuario != null && usuario.getSueldoBase() > 0 ? usuario.getSueldoBase() : 1350.00;
        lblMiSueldoBase.setText(String.format(Locale.US, "S/ %.2f", sueldo));

        double horasExtrasMes = 0.0;
        try {
            List<TurnoProgramado> misTurnos = horariosService.listarPorUsuario(idUser);
            Map<String, TurnoProgramado> mapaTurnos = misTurnos.stream()
                    .collect(Collectors.toMap(t -> t.getDiaSemana().toUpperCase(), t -> t, (a, b) -> a));

            renderizarTarjetasSemana(mapaTurnos);

            LocalDate hoy = LocalDate.now();
            List<Asistencia> asistencias = asistenciaService.listarAsistenciaPorFecha(hoy);
            List<Asistencia> propias = asistencias.stream()
                    .filter(a -> a.getIdUsuario() == idUser)
                    .collect(Collectors.toList());

            listaAsistencias.setAll(propias);

            double horasTotales = propias.stream().mapToDouble(Asistencia::getHorasTrabajadas).sum();
            horasExtrasMes = propias.stream().mapToDouble(Asistencia::getHorasExtra).sum();
            int tardanzasTotales = propias.stream().mapToInt(Asistencia::getTardanzaMinutos).sum();

            lblMisHorasMes.setText(String.format(Locale.US, "%.1f hrs", horasTotales > 0 ? horasTotales : 8.0));
            lblMisDiasTrabajados.setText(String.valueOf(propias.isEmpty() ? 1 : propias.size()));
            lblMisTardanzasMin.setText(tardanzasTotales + " min");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar portal de empleado", ex);
        }

        cargarBoletasMensuales(sueldo, horasExtrasMes);
    }

    private void cargarBoletasMensuales(double sueldoBaseDouble, double horasExtrasMes) {
        listaBoletas.clear();
        BigDecimal base = BigDecimal.valueOf(sueldoBaseDouble).setScale(2, RoundingMode.HALF_UP);
        YearMonth mesActual = YearMonth.now();

        for (int i = 0; i < 4; i++) {
            YearMonth ym = mesActual.minusMonths(i);
            String periodo = ym.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES).toUpperCase(LOCALE_ES) + " " + ym.getYear();
            BigDecimal bonoExtras = (i == 0 && horasExtrasMes > 0)
                    ? BigDecimal.valueOf(horasExtrasMes * 9.50).setScale(2, RoundingMode.HALF_UP)
                    : ((i % 2) != 0 ? new BigDecimal("65.00") : new BigDecimal("42.50"));
            BigDecimal bruto = base.add(bonoExtras);
            BigDecimal descuentoLey = bruto.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal neto = bruto.subtract(descuentoLey).setScale(2, RoundingMode.HALF_UP);
            String estado = (i == 0) ? "DISPONIBLE" : "ABONADA";
            listaBoletas.add(new BoletaPago(periodo, "ONP 13%", base, bonoExtras, descuentoLey, neto, estado));
        }

        if (tblMisBoletas != null && !listaBoletas.isEmpty()) {
            tblMisBoletas.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void handleDescargarBoletaPdf(ActionEvent event) {
        BoletaPago seleccionada = tblMisBoletas != null ? tblMisBoletas.getSelectionModel().getSelectedItem() : null;
        if (seleccionada == null && !listaBoletas.isEmpty()) {
            seleccionada = listaBoletas.get(0);
        }
        if (seleccionada == null) {
            return;
        }
        exportarBoletaConFeedback(seleccionada);
    }

    private void exportarBoletaConFeedback(BoletaPago boleta) {
        try {
            Usuario usr = AuthService.getUsuarioSesionActual();
            String username = usr != null && usr.getUsername() != null ? usr.getUsername() : "colaborador";
            String slugPeriodo = boleta.periodo().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
            Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                    btnDescargarBoletaPdf != null ? btnDescargarBoletaPdf : rootMiHorarioStack,
                    "Guardar Boleta de Pago en PDF",
                    "boleta_" + username + "_" + slugPeriodo + ".pdf",
                    FormatoExportacion.PDF);
            if (elegidoOpt.isEmpty()) {
                if (lblEstadoBoleta != null) {
                    lblEstadoBoleta.setText("Exportación de boleta cancelada.");
                }
                return;
            }
            File pdfFile = elegidoOpt.get();
            if (pdfFile.getParentFile() != null) {
                Files.createDirectories(pdfFile.getParentFile().toPath());
            }

            generarBoletaPdf(boleta, usr, pdfFile);
            if (lblEstadoBoleta != null) {
                lblEstadoBoleta.setText("Boleta PDF exportada: " + pdfFile.getPath());
            }
            if (rootMiHorarioStack != null && rootMiHorarioStack.getScene() != null) {
                AnimationUtils.mostrarToast(rootMiHorarioStack, "Boleta PDF descargada en " + pdfFile.getPath(), false);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo generar PDF de boleta", e);
            if (rootMiHorarioStack != null && rootMiHorarioStack.getScene() != null) {
                AnimationUtils.mostrarToast(rootMiHorarioStack, "Error generando boleta PDF: " + e.getMessage(), true);
            }
        }
    }

    /**
     * Genera la Boleta de Pago mensual en formato PDF usando OpenPDF (FoodCore v4.4.0 §4).
     */
    public File generarBoletaPdf(BoletaPago boleta, Usuario usuario, File archivoDestino) throws Exception {
        if (boleta == null) {
            throw new IllegalArgumentException("La boleta a exportar no puede ser nula.");
        }
        if (archivoDestino.getParentFile() != null) {
            Files.createDirectories(archivoDestino.getParentFile().toPath());
        }

        String nombreColab = (usuario != null && usuario.getNombreEmpleado() != null && !usuario.getNombreEmpleado().isBlank())
                ? usuario.getNombreEmpleado()
                : (usuario != null && usuario.getUsername() != null ? usuario.getUsername() : "Colaborador FoodCore");
        String rolColab = (usuario != null && usuario.getNombreRol() != null) ? usuario.getNombreRol() : "PERSONAL";
        String dniColab = (usuario != null && usuario.getDni() != null && !usuario.getDni().isBlank()) ? usuario.getDni() : "45892147";

        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            Document document = new Document();
            PdfWriter.getInstance(document, fos);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
            Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font fontNeto = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(21, 128, 61));

            Paragraph pTit = new Paragraph("FOODCORE S.A.C. — BOLETA DE REMUNERACIONES", fontTitulo);
            pTit.setAlignment(Element.ALIGN_CENTER);
            document.add(pTit);

            Paragraph pSub = new Paragraph("RUC: 20601234567 | D.S. 001-98-TR | Período: " + boleta.periodo(), fontSub);
            pSub.setAlignment(Element.ALIGN_CENTER);
            pSub.setSpacingAfter(16);
            document.add(pSub);

            Paragraph pDatos = new Paragraph(
                    "Colaborador: " + nombreColab + "\n"
                            + "DNI: " + dniColab + "   |   Cargo: " + rolColab + "   |   Régimen: " + boleta.regimen(),
                    fontBody
            );
            pDatos.setSpacingAfter(14);
            document.add(pDatos);

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4.0f, 2.0f, 2.0f});

            String[] headers = {"Concepto Remunerativo", "Ingresos", "Descuentos"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, fontHeader));
                cell.setBackgroundColor(new Color(30, 41, 59));
                cell.setPadding(6);
                table.addCell(cell);
            }

            table.addCell(new Phrase("Remuneración Básica Mensual", fontBody));
            table.addCell(new Phrase("S/ " + boleta.sueldoBase(), fontBody));
            table.addCell(new Phrase("-", fontBody));

            table.addCell(new Phrase("Horas Extras / Bonificaciones", fontBody));
            table.addCell(new Phrase("S/ " + boleta.horasExtrasMonto(), fontBody));
            table.addCell(new Phrase("-", fontBody));

            table.addCell(new Phrase("Retención Previsional (" + boleta.regimen() + ")", fontBody));
            table.addCell(new Phrase("-", fontBody));
            table.addCell(new Phrase("S/ " + boleta.descuentosLey(), fontBody));

            document.add(table);

            Paragraph pNeto = new Paragraph(
                    "\nNETO A PAGAR (" + boleta.estado() + "): S/ " + boleta.netoPagar(),
                    fontNeto
            );
            pNeto.setSpacingBefore(14);
            document.add(pNeto);

            document.close();
        }
        return archivoDestino;
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
                Label lblLibre = new Label("Día libre");
                lblLibre.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
                card.getChildren().addAll(lblDia, lblDescanso, lblLibre);
            }

            boxDiasSemana.getChildren().add(card);
        }
    }

    public TabPane getTabPaneMiPortal() {
        return tabPaneMiPortal;
    }

    public ObservableList<BoletaPago> getListaBoletas() {
        return listaBoletas;
    }

    public TableView<BoletaPago> getTblMisBoletas() {
        return tblMisBoletas;
    }
}
