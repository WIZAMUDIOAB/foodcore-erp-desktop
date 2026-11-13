package com.foodcore.controller;

import com.foodcore.model.MovimientoPago;
import com.foodcore.model.PlanillaConsolidada;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.PlanillaService;
import com.foodcore.service.impl.PlanillaServiceImpl;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
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
import java.util.Optional;

/**
 * Controlador JavaFX para gestión administrativa de pagos, movimientos y planilla consolidada.
 * Consolida {@code Sueldo Base + Bonos/Comisiones - Adelantos/Descuentos = Neto a Pagar}.
 */
public class PlanillaController {

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

    public PlanillaController() {
        this(new PlanillaServiceImpl());
    }

    public PlanillaController(PlanillaService planillaService) {
        this.planillaService = planillaService;
    }

    @FXML
    public void initialize() {
        configurarCombos();
        configurarTablas();
        cargarColaboradores();
        cargarPlanillaPeriodo();
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
        try {
            List<Usuario> lista = planillaService.listarColaboradoresActivos();
            cbColaborador.setItems(FXCollections.observableArrayList(lista));
            if (!lista.isEmpty()) {
                cbColaborador.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            mostrarError("No se pudieron cargar los colaboradores: " + e.getMessage());
        }
    }

    @FXML
    public void handleRefrescar() {
        cargarPlanillaPeriodo();
    }

    public void cargarPlanillaPeriodo() {
        String periodo = obtenerPeriodoSeleccionado();
        lblBadgePeriodo.setText(periodo);
        try {
            List<MovimientoPago> movs = planillaService.listarMovimientosPorPeriodo(periodo);
            listaMovimientos.setAll(movs);

            List<PlanillaConsolidada> consolidado = planillaService.calcularPlanillaConsolidada(periodo);
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

            lblKpiSueldoBase.setText(formatearMoneda(totalBase));
            lblKpiIngresos.setText(formatearMoneda(totalIng));
            lblKpiEgresos.setText(formatearMoneda(totalEgr));
            lblKpiNetoPagar.setText(formatearMoneda(totalNeto));
        } catch (Exception e) {
            mostrarError("Error al consolidar planilla: " + e.getMessage());
        }
    }

    @FXML
    public void handleRegistrarMovimiento() {
        Usuario colab = cbColaborador.getValue();
        if (colab == null) {
            mostrarError("Seleccione un colaborador.");
            return;
        }
        try {
            BigDecimal monto = new BigDecimal(txtMonto.getText().trim().replace(',', '.'));
            LocalDate fecha = dpFechaMovimiento.getValue() != null ? dpFechaMovimiento.getValue() : LocalDate.now();
            Usuario sesion = AuthService.getUsuarioSesionActual();
            Integer regPor = sesion != null && sesion.getIdUsuario() > 0 ? sesion.getIdUsuario() : null;

            MovimientoPago mov = planillaService.registrarMovimiento(
                    colab.getIdUsuario(),
                    cbTipoMovimiento.getValue(),
                    txtConcepto.getText(),
                    monto,
                    fecha,
                    regPor,
                    null);

            txtConcepto.clear();
            txtMonto.clear();
            cbPeriodo.setValue(mov.getPeriodoMes());
            cargarPlanillaPeriodo();
            mostrarOk("Movimiento " + mov.getTipoMovimiento() + " registrado correctamente.");
        } catch (NumberFormatException e) {
            mostrarError("Ingrese un monto numérico válido mayor a 0.");
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
    }

    @FXML
    public void handleEliminarMovimiento() {
        MovimientoPago sel = tblMovimientos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarError("Seleccione un movimiento de la lista para eliminarlo.");
            return;
        }
        try {
            planillaService.eliminarMovimiento(sel.getIdMovimiento());
            cargarPlanillaPeriodo();
            mostrarOk("Movimiento eliminado correctamente.");
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
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
        try {
            File out = planillaService.exportarPlanillaExcel(listaPlanilla, periodo, destino.get());
            mostrarOk("Excel exportado en: " + out.getName());
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
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
        try {
            File out = planillaService.exportarPlanillaPdf(listaPlanilla, periodo, destino.get());
            mostrarOk("PDF exportado en: " + out.getName());
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
    }

    private String obtenerPeriodoSeleccionado() {
        return cbPeriodo.getValue() != null && !cbPeriodo.getValue().isBlank()
                ? cbPeriodo.getValue()
                : YearMonth.now().toString();
    }

    private String formatearMoneda(BigDecimal valor) {
        BigDecimal v = valor != null ? valor.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return String.format(Locale.US, "S/ %.2f", v.doubleValue());
    }

    private void mostrarOk(String msg) {
        lblEstadoPlanilla.setStyle("-fx-text-fill: #15803d; -fx-font-weight: bold;");
        lblEstadoPlanilla.setText("✅ " + msg);
    }

    private void mostrarError(String msg) {
        lblEstadoPlanilla.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");
        lblEstadoPlanilla.setText("⚠️ " + msg);
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
}
