package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.MovimientoPago;
import com.foodcore.model.PlanillaConsolidada;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.PlanillaService;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador JavaFX para gestión administrativa de pagos, movimientos y planilla consolidada.
 * Consolida {@code Sueldo Base + Bonos/Comisiones - Adelantos/Descuentos = Neto a Pagar}.
 */
public class PlanillaController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(PlanillaController.class.getName());

    private record DatosPlanillaPeriodo(
            List<MovimientoPago> movimientos,
            List<PlanillaConsolidada> consolidado) {
    }

    @FXML private StackPane rootPlanillaStack;
    @FXML private ComboBox<String> cbPeriodo;
    @FXML private Label lblKpiSueldoBase;
    @FXML private Label lblKpiIngresos;
    @FXML private Label lblKpiEgresos;
    @FXML private Label lblKpiNetoPagar;
    @FXML private Label lblBadgePeriodo;

    @FXML private ComboBox<Usuario> cbColaborador;
    @FXML private ComboBox<String> cbTipoMovimiento;
    @FXML private TextField txtConcepto;
    @FXML private TextField txtMonto;
    @FXML private DatePicker dpFechaMovimiento;
    @FXML private Button btnRegistrarMovimiento;
    @FXML private Button btnEliminarMovimiento;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnExportarPdf;
    @FXML private Label lblEstadoPlanilla;

    @FXML private TableView<MovimientoPago> tblMovimientos;
    @FXML private TableColumn<MovimientoPago, String> colMovColab;
    @FXML private TableColumn<MovimientoPago, String> colMovTipo;
    @FXML private TableColumn<MovimientoPago, String> colMovConcepto;
    @FXML private TableColumn<MovimientoPago, String> colMovMonto;

    @FXML private TableView<PlanillaConsolidada> tblPlanilla;
    @FXML private TableColumn<PlanillaConsolidada, String> colPlanNombre;
    @FXML private TableColumn<PlanillaConsolidada, String> colPlanCargo;
    @FXML private TableColumn<PlanillaConsolidada, String> colPlanBase;
    @FXML private TableColumn<PlanillaConsolidada, String> colPlanIngresos;
    @FXML private TableColumn<PlanillaConsolidada, String> colPlanEgresos;
    @FXML private TableColumn<PlanillaConsolidada, String> colPlanNeto;

    private final PlanillaService planillaService;
    private final ObservableList<MovimientoPago> listaMovimientos = FXCollections.observableArrayList();
    private final ObservableList<PlanillaConsolidada> listaPlanilla = FXCollections.observableArrayList();
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicLong cargaColabSeq = new AtomicLong(0L);
    private final AtomicLong cargaPeriodoSeq = new AtomicLong(0L);
    private final AtomicBoolean registrarEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean eliminarEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean exportExcelEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean exportPdfEnCurso = new AtomicBoolean(false);

    public PlanillaController() {
        this(AppContainer.getDefault().planillaService());
    }

    public PlanillaController(PlanillaService planillaService) {
        this.planillaService = Objects.requireNonNull(planillaService, "planillaService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        configurarCombos();
        configurarTablas();
        cargarColaboradores();
        cargarPlanillaPeriodo();
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
    }

    private void configurarCombos() {
        YearMonth actual = YearMonth.now();
        ObservableList<String> periodos = FXCollections.observableArrayList();
        for (int i = 0; i < 6; i++) {
            periodos.add(actual.minusMonths(i).toString());
        }
        cbPeriodo.setItems(periodos);
        cbPeriodo.getSelectionModel().selectFirst();
        cbPeriodo.valueProperty().addListener((obs, oldVal, newVal) -> cargarPlanillaPeriodo());

        cbTipoMovimiento.setItems(FXCollections.observableArrayList("BONO", "COMISION", "ADELANTO", "DESCUENTO"));
        cbTipoMovimiento.getSelectionModel().select("BONO");

        dpFechaMovimiento.setValue(LocalDate.now());

        cbColaborador.setConverter(new StringConverter<>() {
            @Override
            public String toString(Usuario u) {
                if (u == null) {
                    return "";
                }
                return u.getNombreEmpleado() != null && !u.getNombreEmpleado().isBlank()
                        ? u.getNombreEmpleado()
                        : u.getUsername();
            }

            @Override
            public Usuario fromString(String string) {
                return null;
            }
        });
    }

    private void configurarTablas() {
        colMovColab.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreEmpleado()));
        colMovTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipoMovimiento()));
        colMovConcepto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getConcepto()));
        colMovMonto.setCellValueFactory(c -> new SimpleStringProperty(
                (c.getValue().esIngreso() ? "+S/ " : "-S/ ")
                        + c.getValue().getMonto().setScale(2, RoundingMode.HALF_UP)));
        tblMovimientos.setItems(listaMovimientos);

        colPlanNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombreEmpleado()));
        colPlanCargo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().cargo()));
        colPlanBase.setCellValueFactory(c -> new SimpleStringProperty(formatearMoneda(c.getValue().sueldoBase())));
        colPlanIngresos.setCellValueFactory(c -> new SimpleStringProperty(
                "+" + formatearMoneda(c.getValue().totalIngresosAdicionales())));
        colPlanEgresos.setCellValueFactory(c -> new SimpleStringProperty(
                "-" + formatearMoneda(c.getValue().totalEgresos())));
        colPlanNeto.setCellValueFactory(c -> new SimpleStringProperty(formatearMoneda(c.getValue().netoPagar())));
        colPlanNeto.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #15803d; -fx-font-family: 'JetBrains Mono', monospace;");
                }
            }
        });
        tblPlanilla.setItems(listaPlanilla);
    }

    private void cargarColaboradores() {
        long seq = cargaColabSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            planillaService::listarColaboradoresActivos,
            lista -> {
                if (!vistaActiva.get() || seq != cargaColabSeq.get()) {
                    return;
                }
                List<Usuario> colaboradores = lista != null ? lista : List.of();
                if (cbColaborador != null) {
                    cbColaborador.setItems(FXCollections.observableArrayList(colaboradores));
                    if (!colaboradores.isEmpty()) {
                        cbColaborador.getSelectionModel().selectFirst();
                    }
                }
            },
            e -> {
                LOGGER.log(Level.WARNING, "Error cargando colaboradores en planilla", e);
                if (vistaActiva.get()) {
                    mostrarError("No se pudieron cargar los colaboradores: " + e.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleRefrescar() {
        cargarPlanillaPeriodo();
    }

    public void cargarPlanillaPeriodo() {
        String periodo = obtenerPeriodoSeleccionado();
        if (lblBadgePeriodo != null) {
            lblBadgePeriodo.setText(periodo);
        }
        long seq = cargaPeriodoSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> new DatosPlanillaPeriodo(
                    planillaService.listarMovimientosPorPeriodo(periodo),
                    planillaService.calcularPlanillaConsolidada(periodo)),
            datos -> {
                if (!vistaActiva.get() || seq != cargaPeriodoSeq.get()) {
                    return;
                }
                aplicarDatosPeriodoEnUi(datos);
            },
            e -> {
                LOGGER.log(Level.WARNING, "Error consolidando planilla", e);
                if (vistaActiva.get()) {
                    mostrarError("Error al consolidar planilla: " + e.getMessage());
                }
            }
        );
    }

    private void aplicarDatosPeriodoEnUi(DatosPlanillaPeriodo datos) {
        List<MovimientoPago> movs = datos != null && datos.movimientos() != null ? datos.movimientos() : List.of();
        List<PlanillaConsolidada> consolidado = datos != null && datos.consolidado() != null
                ? datos.consolidado() : List.of();
        listaMovimientos.setAll(movs);
        listaPlanilla.setAll(consolidado);

        BigDecimal totalBase = consolidado.stream()
                .map(PlanillaConsolidada::sueldoBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIng = consolidado.stream()
                .map(PlanillaConsolidada::totalIngresosAdicionales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEgr = consolidado.stream()
                .map(PlanillaConsolidada::totalEgresos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNeto = consolidado.stream()
                .map(PlanillaConsolidada::netoPagar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (lblKpiSueldoBase != null) {
            lblKpiSueldoBase.setText(formatearMoneda(totalBase));
        }
        if (lblKpiIngresos != null) {
            lblKpiIngresos.setText(formatearMoneda(totalIng));
        }
        if (lblKpiEgresos != null) {
            lblKpiEgresos.setText(formatearMoneda(totalEgr));
        }
        if (lblKpiNetoPagar != null) {
            lblKpiNetoPagar.setText(formatearMoneda(totalNeto));
        }
    }

    @FXML
    public void handleRegistrarMovimiento() {
        Usuario colab = cbColaborador != null ? cbColaborador.getValue() : null;
        if (colab == null) {
            mostrarError("Seleccione un colaborador.");
            return;
        }
        BigDecimal monto;
        try {
            String montoTxt = txtMonto != null && txtMonto.getText() != null ? txtMonto.getText().trim() : "";
            monto = new BigDecimal(montoTxt.replace(',', '.'));
        } catch (NumberFormatException e) {
            mostrarError("Ingrese un monto numérico válido mayor a 0.");
            return;
        }
        if (!registrarEnCurso.compareAndSet(false, true)) {
            return;
        }
        int idColab = colab.getIdUsuario();
        String tipoMov = cbTipoMovimiento != null ? cbTipoMovimiento.getValue() : "BONO";
        String concepto = txtConcepto != null ? txtConcepto.getText() : "";
        LocalDate fecha = dpFechaMovimiento != null && dpFechaMovimiento.getValue() != null
                ? dpFechaMovimiento.getValue() : LocalDate.now();
        Usuario sesion = AuthService.getUsuarioSesionActual();
        Integer regPor = sesion != null && sesion.getIdUsuario() > 0 ? sesion.getIdUsuario() : null;

        if (btnRegistrarMovimiento != null) {
            btnRegistrarMovimiento.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> planillaService.registrarMovimiento(idColab, tipoMov, concepto, monto, fecha, regPor, null),
            mov -> {
                registrarEnCurso.set(false);
                if (btnRegistrarMovimiento != null) {
                    btnRegistrarMovimiento.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                if (txtConcepto != null) {
                    txtConcepto.clear();
                }
                if (txtMonto != null) {
                    txtMonto.clear();
                }
                String periodoMov = mov.getPeriodoMes();
                if (cbPeriodo != null && !Objects.equals(cbPeriodo.getValue(), periodoMov)) {
                    cbPeriodo.setValue(periodoMov);
                } else {
                    cargarPlanillaPeriodo();
                }
                mostrarOk("Movimiento " + mov.getTipoMovimiento() + " registrado correctamente.");
            },
            e -> {
                registrarEnCurso.set(false);
                if (btnRegistrarMovimiento != null) {
                    btnRegistrarMovimiento.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error registrando movimiento de planilla", e);
                if (vistaActiva.get()) {
                    mostrarError(e.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleEliminarMovimiento() {
        MovimientoPago sel = tblMovimientos != null ? tblMovimientos.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            mostrarError("Seleccione un movimiento de la lista para eliminarlo.");
            return;
        }
        if (!eliminarEnCurso.compareAndSet(false, true)) {
            return;
        }
        long idMovimiento = sel.getIdMovimiento();
        if (btnEliminarMovimiento != null) {
            btnEliminarMovimiento.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> planillaService.eliminarMovimiento(idMovimiento),
            () -> {
                eliminarEnCurso.set(false);
                if (btnEliminarMovimiento != null) {
                    btnEliminarMovimiento.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                cargarPlanillaPeriodo();
                mostrarOk("Movimiento eliminado correctamente.");
            },
            e -> {
                eliminarEnCurso.set(false);
                if (btnEliminarMovimiento != null) {
                    btnEliminarMovimiento.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error eliminando movimiento de planilla", e);
                if (vistaActiva.get()) {
                    mostrarError(e.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleExportarExcel() {
        String periodo = obtenerPeriodoSeleccionado();
        Optional<File> destino = ExportFileChooserHelper.elegirDestinoExportacion(
                btnExportarExcel,
                "Guardar Planilla Administrativa en Excel",
                "Planilla_" + periodo + ".xlsx",
                FormatoExportacion.EXCEL);
        if (destino.isEmpty()) {
            return;
        }
        exportarPlanillaAsync(btnExportarExcel, exportExcelEnCurso, true, periodo,
                List.copyOf(listaPlanilla), destino.get());
    }

    @FXML
    public void handleExportarPdf() {
        String periodo = obtenerPeriodoSeleccionado();
        Optional<File> destino = ExportFileChooserHelper.elegirDestinoExportacion(
                btnExportarPdf,
                "Guardar Planilla Administrativa en PDF",
                "Planilla_" + periodo + ".pdf",
                FormatoExportacion.PDF);
        if (destino.isEmpty()) {
            return;
        }
        exportarPlanillaAsync(btnExportarPdf, exportPdfEnCurso, false, periodo,
                List.copyOf(listaPlanilla), destino.get());
    }

    private void exportarPlanillaAsync(Button trigger, AtomicBoolean flag, boolean esExcel,
                                       String periodo, List<PlanillaConsolidada> snapshot, File destino) {
        if (!flag.compareAndSet(false, true)) {
            return;
        }
        if (trigger != null) {
            trigger.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> esExcel
                    ? planillaService.exportarPlanillaExcel(snapshot, periodo, destino)
                    : planillaService.exportarPlanillaPdf(snapshot, periodo, destino),
            out -> {
                flag.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                mostrarOk((esExcel ? "Excel" : "PDF") + " exportado en: " + out.getName());
            },
            e -> {
                flag.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error exportando planilla", e);
                if (vistaActiva.get()) {
                    mostrarError(e.getMessage());
                }
            }
        );
    }

    private String obtenerPeriodoSeleccionado() {
        return cbPeriodo != null && cbPeriodo.getValue() != null && !cbPeriodo.getValue().isBlank()
                ? cbPeriodo.getValue()
                : YearMonth.now().toString();
    }

    private String formatearMoneda(BigDecimal valor) {
        BigDecimal v = valor != null ? valor.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return String.format(Locale.US, "S/ %.2f", v.doubleValue());
    }

    private void mostrarOk(String msg) {
        if (lblEstadoPlanilla != null) {
            lblEstadoPlanilla.getStyleClass().removeAll("fc-status-msg-ok", "fc-status-msg-error");
            lblEstadoPlanilla.getStyleClass().add("fc-status-msg-ok");
            lblEstadoPlanilla.setText("✅ " + msg);
        }
    }

    private void mostrarError(String msg) {
        if (lblEstadoPlanilla != null) {
            lblEstadoPlanilla.getStyleClass().removeAll("fc-status-msg-ok", "fc-status-msg-error");
            lblEstadoPlanilla.getStyleClass().add("fc-status-msg-error");
            lblEstadoPlanilla.setText("⚠️ " + msg);
        }
    }

    public ObservableList<PlanillaConsolidada> getListaPlanilla() {
        return listaPlanilla;
    }

    public ObservableList<MovimientoPago> getListaMovimientos() {
        return listaMovimientos;
    }

    public ComboBox<Usuario> getCbColaborador() {
        return cbColaborador;
    }

    public ComboBox<String> getCbTipoMovimiento() {
        return cbTipoMovimiento;
    }

    public TextField getTxtConcepto() {
        return txtConcepto;
    }

    public TextField getTxtMonto() {
        return txtMonto;
    }

    public Label getLblKpiNetoPagar() {
        return lblKpiNetoPagar;
    }

    public Label getLblEstadoPlanilla() {
        return lblEstadoPlanilla;
    }

    public Button getBtnRegistrarMovimiento() {
        return btnRegistrarMovimiento;
    }

    public Button getBtnEliminarMovimiento() {
        return btnEliminarMovimiento;
    }

    public Button getBtnExportarExcel() {
        return btnExportarExcel;
    }

    public Button getBtnExportarPdf() {
        return btnExportarPdf;
    }
}
