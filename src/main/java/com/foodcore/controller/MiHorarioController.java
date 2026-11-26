package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.Asistencia;
import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.model.Vacaciones;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AusenciasVacacionesService;
import com.foodcore.service.AuthService;
import com.foodcore.service.HorariosService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.ToastService;
import com.foodcore.util.UiAsyncExecutor;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controlador del Portal del Empleado ("Mi Horario", "Mis Asistencias", "Mis Boletas") según FoodCore v4.4.0 (§4).
 */
public class MiHorarioController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(MiHorarioController.class.getName());
    private static final Locale LOCALE_ES = Locale.forLanguageTag("es-PE");

    private record DatosEmpleadoPortal(
            List<TurnoProgramado> turnos,
            List<Asistencia> asistencias) {
    }

    private record DatosVacacionesPortal(
            Vacaciones saldo,
            List<SolicitudAusencia> solicitudes) {
    }

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

    @FXML private Label lblVacDisponibles;
    @FXML private Label lblVacEnRevision;
    @FXML private ComboBox<String> cbVacTipo;
    @FXML private DatePicker dpVacInicio;
    @FXML private DatePicker dpVacFin;
    @FXML private Label lblVacDiasCalculados;
    @FXML private TextField txtVacMotivo;
    @FXML private Button btnSolicitarVacaciones;
    @FXML private Label lblVacEstadoMsg;
    @FXML private TableView<SolicitudAusencia> tblMisVacaciones;
    @FXML private TableColumn<SolicitudAusencia, String> colMisVacTipo;
    @FXML private TableColumn<SolicitudAusencia, String> colMisVacPeriodo;
    @FXML private TableColumn<SolicitudAusencia, String> colMisVacDias;
    @FXML private TableColumn<SolicitudAusencia, String> colMisVacMotivo;
    @FXML private TableColumn<SolicitudAusencia, String> colMisVacEstado;

    private final HorariosService horariosService;
    private final AsistenciaService asistenciaService;
    private final AusenciasVacacionesService ausenciasService;
    private final ObservableList<Asistencia> listaAsistencias = FXCollections.observableArrayList();
    private final ObservableList<BoletaPago> listaBoletas = FXCollections.observableArrayList();
    private final ObservableList<SolicitudAusencia> listaMisVacaciones = FXCollections.observableArrayList();
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicLong cargaDatosSeq = new AtomicLong(0L);
    private final AtomicLong cargaVacSeq = new AtomicLong(0L);
    private final AtomicBoolean solicitarVacEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean exportarBoletaEnCurso = new AtomicBoolean(false);

    public MiHorarioController() {
        this(AppContainer.getDefault().resolve(HorariosService.class),
                AppContainer.getDefault().resolve(AsistenciaService.class),
                AppContainer.getDefault().ausenciasVacacionesService());
    }

    public MiHorarioController(HorariosService horariosService,
                               AsistenciaService asistenciaService,
                               AusenciasVacacionesService ausenciasService) {
        this.horariosService = Objects.requireNonNull(horariosService, "horariosService");
        this.asistenciaService = Objects.requireNonNull(asistenciaService, "asistenciaService");
        this.ausenciasService = Objects.requireNonNull(ausenciasService, "ausenciasService");
    }

    private static final String[] DIAS_SEMANA = {"LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"};

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        if (btnDescargarBoletaPdf != null) {
            AnimationUtils.attachTactileFeedback(btnDescargarBoletaPdf);
        }
        configurarTablaAsistencias();
        configurarTablaBoletas();
        configurarVacacionesPortal();
        cargarDatosEmpleado();
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
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
                    getStyleClass().removeAll("fc-status-ok", "fc-status-warning", "fc-status-neutral");
                } else {
                    setText(estado);
                    getStyleClass().removeAll("fc-status-ok", "fc-status-warning", "fc-status-neutral");
                    if ("PRESENTE".equalsIgnoreCase(estado)) {
                        getStyleClass().add("fc-status-ok");
                    } else if ("TARDE".equalsIgnoreCase(estado)) {
                        getStyleClass().add("fc-status-warning");
                    } else {
                        getStyleClass().add("fc-status-neutral");
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
                btnPdf.getStyleClass().add("fc-btn-compact");
                AnimationUtils.attachTactileFeedback(btnPdf);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    BoletaPago boleta = getTableRow().getItem();
                    btnPdf.setOnAction(ev -> exportarBoletaConFeedback(boleta, btnPdf));
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

        if (lblMiRolBadge != null) {
            lblMiRolBadge.setText(rol);
        }
        if (lblBienvenida != null) {
            lblBienvenida.setText("Bienvenido/a, " + nombre + ". Aquí puede consultar su horario, asistencias y boletas.");
        }

        BigDecimal sueldo = (usuario != null && usuario.getSueldoBase() != null
                && usuario.getSueldoBase().compareTo(BigDecimal.ZERO) > 0)
                ? usuario.getSueldoBase().setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("1350.00");
        if (lblMiSueldoBase != null) {
            lblMiSueldoBase.setText(String.format(Locale.US, "S/ %.2f", sueldo));
        }

        LocalDate hoy = LocalDate.now();
        long seq = cargaDatosSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> new DatosEmpleadoPortal(
                    horariosService.listarPorUsuario(idUser),
                    asistenciaService.listarAsistenciaPorFecha(hoy)),
            datos -> {
                if (!vistaActiva.get() || seq != cargaDatosSeq.get()) {
                    return;
                }
                List<TurnoProgramado> misTurnos = datos != null && datos.turnos() != null
                        ? datos.turnos() : List.of();
                Map<String, TurnoProgramado> mapaTurnos = misTurnos.stream()
                        .collect(Collectors.toMap(
                                t -> t.getDiaSemana().toUpperCase(Locale.ROOT),
                                t -> t,
                                (a, b) -> a));
                if (boxDiasSemana != null) {
                    renderizarTarjetasSemana(mapaTurnos);
                }

                List<Asistencia> asistencias = datos != null && datos.asistencias() != null
                        ? datos.asistencias() : List.of();
                List<Asistencia> propias = asistencias.stream()
                        .filter(a -> a.getIdUsuario() == idUser)
                        .collect(Collectors.toList());

                listaAsistencias.setAll(propias);

                double horasTotales = propias.stream().mapToDouble(Asistencia::getHorasTrabajadas).sum();
                double horasExtrasMes = propias.stream().mapToDouble(Asistencia::getHorasExtra).sum();
                int tardanzasTotales = propias.stream().mapToInt(Asistencia::getTardanzaMinutos).sum();

                if (lblMisHorasMes != null) {
                    lblMisHorasMes.setText(String.format(Locale.US, "%.1f hrs",
                            horasTotales > 0 ? horasTotales : 8.0));
                }
                if (lblMisDiasTrabajados != null) {
                    lblMisDiasTrabajados.setText(String.valueOf(propias.isEmpty() ? 1 : propias.size()));
                }
                if (lblMisTardanzasMin != null) {
                    lblMisTardanzasMin.setText(tardanzasTotales + " min");
                }
                cargarBoletasMensuales(sueldo, horasExtrasMes);
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error al cargar portal de empleado", ex);
                if (!vistaActiva.get()) {
                    return;
                }
                cargarBoletasMensuales(sueldo, 0.0);
                mostrarToastSeguro("Error al cargar portal de empleado: " + ex.getMessage(), true);
            }
        );

        cargarSaldoYSolicitudesVacaciones(idUser);
    }

    private void cargarBoletasMensuales(BigDecimal sueldoBase, double horasExtrasMes) {
        listaBoletas.clear();
        BigDecimal base = (sueldoBase != null ? sueldoBase : new BigDecimal("1350.00"))
                .setScale(2, RoundingMode.HALF_UP);
        YearMonth mesActual = YearMonth.now();

        for (int i = 0; i < 4; i++) {
            YearMonth ym = mesActual.minusMonths(i);
            String periodo = ym.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES).toUpperCase(LOCALE_ES) + " " + ym.getYear();
            BigDecimal bonoExtras = (i == 0 && horasExtrasMes > 0)
                    ? BigDecimal.valueOf(horasExtrasMes).multiply(new BigDecimal("9.50")).setScale(2, RoundingMode.HALF_UP)
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
        exportarBoletaConFeedback(seleccionada, btnDescargarBoletaPdf);
    }

    private void exportarBoletaConFeedback(BoletaPago boleta, Button trigger) {
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
        if (!exportarBoletaEnCurso.compareAndSet(false, true)) {
            return;
        }
        File pdfFile = elegidoOpt.get();
        Usuario usuarioSnapshot = copiarUsuarioBoleta(usr);
        setBotonesExportBoletaDisabled(trigger, true);

        UiAsyncExecutor.ejecutarAsync(
            () -> generarBoletaPdf(boleta, usuarioSnapshot, pdfFile),
            out -> {
                exportarBoletaEnCurso.set(false);
                setBotonesExportBoletaDisabled(trigger, false);
                if (!vistaActiva.get()) {
                    return;
                }
                if (lblEstadoBoleta != null) {
                    lblEstadoBoleta.setText("Boleta PDF exportada: " + out.getPath());
                }
                mostrarToastSeguro("Boleta PDF descargada en " + out.getPath(), false);
            },
            e -> {
                exportarBoletaEnCurso.set(false);
                setBotonesExportBoletaDisabled(trigger, false);
                LOGGER.log(Level.WARNING, "No se pudo generar PDF de boleta", e);
                if (!vistaActiva.get()) {
                    return;
                }
                if (lblEstadoBoleta != null) {
                    lblEstadoBoleta.setText("Error generando boleta PDF: " + e.getMessage());
                }
                mostrarToastSeguro("Error generando boleta PDF: " + e.getMessage(), true);
            }
        );
    }

    private void setBotonesExportBoletaDisabled(Button trigger, boolean disabled) {
        if (trigger != null) {
            trigger.setDisable(disabled);
        }
        if (btnDescargarBoletaPdf != null && btnDescargarBoletaPdf != trigger) {
            btnDescargarBoletaPdf.setDisable(disabled);
        }
    }

    private static Usuario copiarUsuarioBoleta(Usuario usr) {
        if (usr == null) {
            return null;
        }
        Usuario copy = new Usuario();
        copy.setIdUsuario(usr.getIdUsuario());
        copy.setUsername(usr.getUsername());
        copy.setNombreEmpleado(usr.getNombreEmpleado());
        copy.setNombreRol(usr.getNombreRol());
        copy.setDni(usr.getDni());
        return copy;
    }

    private void mostrarToastSeguro(String mensaje, boolean esError) {
        if (rootMiHorarioStack != null && rootMiHorarioStack.getScene() != null) {
            AnimationUtils.mostrarToast(rootMiHorarioStack, mensaje, esError);
        } else {
            ToastService.mostrarToast(
                    null,
                    mensaje,
                    esError ? ToastService.TipoToast.ERROR : ToastService.TipoToast.EXITO);
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
            lblDia.getStyleClass().add("fc-schedule-day-title");

            TurnoProgramado tp = mapaTurnos.get(dia);
            if (tp != null) {
                card.getStyleClass().add("fc-my-schedule-card-active");
                Label lblTurno = new Label(tp.getTurno());
                lblTurno.getStyleClass().add("fc-shift-badge-morning");

                Label lblHorario = new Label(tp.getHorarioTexto());
                lblHorario.getStyleClass().add("fc-my-schedule-time-mono");

                card.getChildren().addAll(lblDia, lblTurno, lblHorario);
            } else {
                card.getStyleClass().add("fc-my-schedule-card-off");
                Label lblDescanso = new Label("DESCANSO");
                lblDescanso.getStyleClass().add("fc-shift-badge-off");
                Label lblLibre = new Label("Día libre");
                lblLibre.getStyleClass().add("fc-my-schedule-free-day");
                card.getChildren().addAll(lblDia, lblDescanso, lblLibre);
            }

            boxDiasSemana.getChildren().add(card);
        }
    }

    private void configurarVacacionesPortal() {
        if (cbVacTipo != null) {
            cbVacTipo.setItems(FXCollections.observableArrayList(
                    "VACACIONES", "PERMISO_MEDICO", "ASUNTO_PERSONAL", "LICENCIA"));
            cbVacTipo.getSelectionModel().select("VACACIONES");
        }
        if (dpVacInicio != null) {
            dpVacInicio.setValue(LocalDate.now().plusDays(1));
            dpVacInicio.valueProperty().addListener((obs, o, n) -> actualizarDiasCalculadosVacaciones());
        }
        if (dpVacFin != null) {
            dpVacFin.setValue(LocalDate.now().plusDays(1));
            dpVacFin.valueProperty().addListener((obs, o, n) -> actualizarDiasCalculadosVacaciones());
        }
        actualizarDiasCalculadosVacaciones();

        if (tblMisVacaciones != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            colMisVacTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo()));
            colMisVacPeriodo.setCellValueFactory(c -> {
                String ini = c.getValue().getFechaInicio() != null ? c.getValue().getFechaInicio().format(fmt) : "-";
                String fin = c.getValue().getFechaFin() != null ? c.getValue().getFechaFin().format(fmt) : "-";
                return new SimpleStringProperty(ini + " - " + fin);
            });
            colMisVacDias.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDiasSolicitados() + "d"));
            colMisVacMotivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMotivo()));
            colMisVacEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
            tblMisVacaciones.setItems(listaMisVacaciones);
        }
    }

    private void actualizarDiasCalculadosVacaciones() {
        if (lblVacDiasCalculados == null || dpVacInicio == null || dpVacFin == null) {
            return;
        }
        LocalDate ini = dpVacInicio.getValue();
        LocalDate fin = dpVacFin.getValue();
        if (ini == null || fin == null || fin.isBefore(ini)) {
            lblVacDiasCalculados.setText("Rango inválido");
            return;
        }
        int dias = ausenciasService.calcularDiasSolicitados(ini, fin);
        lblVacDiasCalculados.setText(dias + (dias == 1 ? " día" : " días"));
    }

    public void cargarSaldoYSolicitudesVacaciones(int idUser) {
        int anio = LocalDate.now().getYear();
        long seq = cargaVacSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> new DatosVacacionesPortal(
                    ausenciasService.obtenerSaldoVacaciones(idUser, anio),
                    ausenciasService.listarPorUsuario(idUser)),
            datos -> {
                if (!vistaActiva.get() || seq != cargaVacSeq.get()) {
                    return;
                }
                if (datos != null && datos.saldo() != null) {
                    Vacaciones saldo = datos.saldo();
                    if (lblVacDisponibles != null) {
                        lblVacDisponibles.setText(String.format(
                                "Disponibles: %d días (Gozados: %d / %d)",
                                saldo.getDiasDisponibles(), saldo.getDiasGozados(), saldo.getDiasAsignados()));
                    }
                    if (lblVacEnRevision != null) {
                        lblVacEnRevision.setText("En revisión: " + saldo.getDiasPendientesAprobacion() + " días");
                    }
                }
                if (tblMisVacaciones != null && datos != null && datos.solicitudes() != null) {
                    listaMisVacaciones.setAll(datos.solicitudes());
                }
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error cargando saldo y solicitudes de vacaciones", ex);
                if (vistaActiva.get()) {
                    aplicarEstadoVacaciones(false, "Error al consultar vacaciones: " + ex.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleSolicitarVacaciones() {
        Usuario u = AuthService.getUsuarioSesionActual();
        int idUser = (u != null && u.getIdUsuario() > 0) ? u.getIdUsuario() : 1;
        String tipo = cbVacTipo != null && cbVacTipo.getValue() != null ? cbVacTipo.getValue() : "VACACIONES";
        LocalDate ini = dpVacInicio != null ? dpVacInicio.getValue() : null;
        LocalDate fin = dpVacFin != null ? dpVacFin.getValue() : null;
        String motivo = txtVacMotivo != null ? txtVacMotivo.getText() : "";

        if (!solicitarVacEnCurso.compareAndSet(false, true)) {
            return;
        }
        if (btnSolicitarVacaciones != null) {
            btnSolicitarVacaciones.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> ausenciasService.registrarSolicitud(idUser, tipo, ini, fin, motivo),
            () -> {
                solicitarVacEnCurso.set(false);
                if (btnSolicitarVacaciones != null) {
                    btnSolicitarVacaciones.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                if (txtVacMotivo != null) {
                    txtVacMotivo.clear();
                }
                cargarSaldoYSolicitudesVacaciones(idUser);
                aplicarEstadoVacaciones(true, "Solicitud registrada para aprobación gerencial.");
            },
            e -> {
                solicitarVacEnCurso.set(false);
                if (btnSolicitarVacaciones != null) {
                    btnSolicitarVacaciones.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error registrando solicitud de vacaciones", e);
                if (vistaActiva.get()) {
                    aplicarEstadoVacaciones(false, e.getMessage());
                }
            }
        );
    }

    private void aplicarEstadoVacaciones(boolean ok, String mensaje) {
        if (lblVacEstadoMsg == null) {
            return;
        }
        lblVacEstadoMsg.getStyleClass().removeAll("fc-status-msg-ok", "fc-status-msg-error");
        lblVacEstadoMsg.getStyleClass().add(ok ? "fc-status-msg-ok" : "fc-status-msg-error");
        lblVacEstadoMsg.setText((ok ? "✅ " : "⚠️ ") + mensaje);
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

    public Label getLblVacDisponibles() {
        return lblVacDisponibles;
    }

    public DatePicker getDpVacInicio() {
        return dpVacInicio;
    }

    public DatePicker getDpVacFin() {
        return dpVacFin;
    }

    public ComboBox<String> getCbVacTipo() {
        return cbVacTipo;
    }

    public TextField getTxtVacMotivo() {
        return txtVacMotivo;
    }

    public Label getLblVacDiasCalculados() {
        return lblVacDiasCalculados;
    }

    public Label getLblVacEstadoMsg() {
        return lblVacEstadoMsg;
    }

    public ObservableList<SolicitudAusencia> getListaMisVacaciones() {
        return listaMisVacaciones;
    }

    public Button getBtnSolicitarVacaciones() {
        return btnSolicitarVacaciones;
    }

    public Button getBtnDescargarBoletaPdf() {
        return btnDescargarBoletaPdf;
    }

    public ObservableList<Asistencia> getListaAsistencias() {
        return listaAsistencias;
    }

    public Label getLblEstadoBoleta() {
        return lblEstadoBoleta;
    }
}
