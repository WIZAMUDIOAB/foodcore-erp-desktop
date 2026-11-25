package com.foodcore.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.config.AppContainer;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.service.AuthService;
import com.foodcore.service.CuentasPorCobrarService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasService;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.ReporteExportUIHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CuentasPorCobrarController {

    private static final Logger LOGGER = Logger.getLogger(CuentasPorCobrarController.class.getName());

    @FXML private TableView<CuentaPorCobrar> tblCuentasPorCobrar;
    @FXML private TableColumn<CuentaPorCobrar, String> colCliente;
    @FXML private TableColumn<CuentaPorCobrar, String> colComprobante;
    @FXML private TableColumn<CuentaPorCobrar, String> colEmision;
    @FXML private TableColumn<CuentaPorCobrar, String> colVencimiento;
    @FXML private TableColumn<CuentaPorCobrar, String> colMontoTotal;
    @FXML private TableColumn<CuentaPorCobrar, String> colMontoCobrado;
    @FXML private TableColumn<CuentaPorCobrar, String> colSaldo;
    @FXML private TableColumn<CuentaPorCobrar, String> colEstado;

    @FXML private ComboBox<String> cbFiltroEstado;
    @FXML private Label lblSaldoTotal;
    @FXML private Label lblContadorCxc;
    @FXML private Label lblBadgeAlertaVencidas;
    @FXML private Button btnCobrar;
    @FXML private Button btnActualizar;
    @FXML private Button btnExportarPdf;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnExportarCsv;

    @FXML private VBox modalCobro;
    @FXML private Label lblModalDocInfo;
    @FXML private Label lblModalSaldoPendiente;
    @FXML private TextField txtModalMontoCobrar;
    @FXML private ComboBox<String> cbModalMetodoPago;
    @FXML private TextField txtModalNota;
    @FXML private Label lblModalCobroError;

    private final CuentasPorCobrarService cuentasPorCobrarService;
    private final FinanzasService finanzasService;
    private final ExportacionContableService exportacionService;

    private final ObservableList<CuentaPorCobrar> listaCxc = FXCollections.observableArrayList();
    private CuentaPorCobrar cuentaSeleccionada = null;

    public CuentasPorCobrarController() {
        this(AppContainer.getDefault().resolve(CuentasPorCobrarService.class),
                AppContainer.getDefault().finanzasService(),
                AppContainer.getDefault().resolve(ExportacionContableService.class));
    }

    public CuentasPorCobrarController(CuentasPorCobrarService cuentasPorCobrarService,
                                      FinanzasService finanzasService,
                                      ExportacionContableService exportacionService) {
        this.cuentasPorCobrarService =
                java.util.Objects.requireNonNull(cuentasPorCobrarService, "cuentasPorCobrarService");
        this.finanzasService = java.util.Objects.requireNonNull(finanzasService, "finanzasService");
        this.exportacionService = java.util.Objects.requireNonNull(exportacionService, "exportacionService");
    }

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarFiltros();
        cargarCuentasAsync();
    }

    private void configurarColumnas() {
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocialCliente()));
        colComprobante.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroComprobante()));
        colEmision.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaEmision() != null ? c.getValue().getFechaEmision().toString() : "-"));
        colVencimiento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaVencimiento() != null ? c.getValue().getFechaVencimiento().toString() : "-"));
        colMontoTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "S/ %.2f", c.getValue().getMontoTotal())));
        colMontoCobrado.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "S/ %.2f", c.getValue().getMontoCobrado())));
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "S/ %.2f", c.getValue().getSaldo())));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        tblCuentasPorCobrar.setItems(listaCxc);

        tblCuentasPorCobrar.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            this.cuentaSeleccionada = newV;
        });
    }

    private void configurarFiltros() {
        cbFiltroEstado.setItems(FXCollections.observableArrayList("TODAS", "PENDIENTE", "VENCIDA", "COBRADA"));
        cbFiltroEstado.setValue("TODAS");
        cbFiltroEstado.setOnAction(e -> cargarCuentasAsync());

        cbModalMetodoPago.setItems(FXCollections.observableArrayList("EFECTIVO", "TRANSFERENCIA", "YAPE", "TARJETA"));
        cbModalMetodoPago.setValue("TRANSFERENCIA");
    }

    public void cargarCuentasAsync() {
        final String filtro = cbFiltroEstado.getValue();
        UiAsyncExecutor.ejecutarAsync(
            () -> cuentasPorCobrarService.listarPorFiltro(filtro),
            cuentas -> {
                listaCxc.setAll(cuentas);
                BigDecimal saldoTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                int vencidas = 0;
                for (CuentaPorCobrar c : cuentas) {
                    if (!"COBRADA".equalsIgnoreCase(c.getEstado())) {
                        saldoTotal = saldoTotal.add(c.getSaldo()).setScale(2, RoundingMode.HALF_UP);
                    }
                    if ("VENCIDA".equalsIgnoreCase(c.getEstado())) {
                        vencidas++;
                    }
                }
                if (!cuentas.isEmpty()) {
                    tblCuentasPorCobrar.getSelectionModel().selectFirst();
                }
                lblSaldoTotal.setText(String.format(Locale.US, "S/ %.2f", saldoTotal));
                lblContadorCxc.setText(listaCxc.size() + " Comprobantes");
                if (vencidas > 0) {
                    lblBadgeAlertaVencidas.setText(vencidas + " COMPROBANTES VENCIDOS");
                    lblBadgeAlertaVencidas.setVisible(true);
                } else {
                    lblBadgeAlertaVencidas.setVisible(false);
                }
            },
            ex -> LOGGER.log(Level.WARNING, "Error al cargar cuentas por cobrar", ex)
        );
    }

    public void cargarCuentasSincrono() {
        cargarCuentasAsync();
    }

    @FXML
    public void handleAbrirModalCobro() {
        CuentaPorCobrar sel = tblCuentasPorCobrar.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if ("COBRADA".equalsIgnoreCase(sel.getEstado())) {
            return;
        }

        this.cuentaSeleccionada = sel;
        lblModalDocInfo.setText(sel.getRazonSocialCliente() + " — Comprobante: " + sel.getNumeroComprobante());
        lblModalSaldoPendiente.setText(String.format(Locale.US, "S/ %.2f", sel.getSaldo()));
        txtModalMontoCobrar.setText(String.format(Locale.US, "%.2f", sel.getSaldo()));
        txtModalNota.setText("Cobro comprobante " + sel.getNumeroComprobante());
        lblModalCobroError.setText("");
        modalCobro.setVisible(true);
    }

    @FXML
    public void handleCerrarModalCobro() {
        modalCobro.setVisible(false);
    }

    @FXML
    public void handleConfirmarCobro() {
        if (cuentaSeleccionada == null) return;
        BigDecimal monto;
        try {
            String textoMonto = txtModalMontoCobrar.getText() != null
                    ? txtModalMontoCobrar.getText().trim().replace(',', '.')
                    : "";
            monto = new BigDecimal(textoMonto).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            lblModalCobroError.setText("Ingrese un importe numérico válido");
            return;
        }

        String metodo = cbModalMetodoPago.getValue();
        String nota = txtModalNota.getText().trim();
        int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;

        final int idCuenta = cuentaSeleccionada.getIdCuentaCobrar();
        final BigDecimal montoFinal = monto;
        final String metodoFinal = metodo;
        final String notaFinal = nota;
        final int idUsrFinal = idUsuario;

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                Integer idCaja = cuentasPorCobrarService.obtenerIdCajaAbiertaOptional();
                finanzasService.registrarCobroCliente(idCuenta, montoFinal, metodoFinal, idCaja, idUsrFinal, notaFinal);
            },
            () -> {
                modalCobro.setVisible(false);
                cargarCuentasAsync();
            },
            ex -> lblModalCobroError.setText("Error al cobrar: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    @FXML
    public void handleExportarPdf() {
        exportarCxc(btnExportarPdf, "Guardar Cuentas por Cobrar en PDF", ".pdf", FormatoExportacion.PDF);
    }

    @FXML
    public void handleExportarExcel() {
        exportarCxc(btnExportarExcel, "Guardar Cuentas por Cobrar en Excel", ".xlsx", FormatoExportacion.EXCEL);
    }

    @FXML
    public void handleExportarCsv() {
        exportarCxc(btnExportarCsv, "Guardar Cuentas por Cobrar en CSV", ".csv", FormatoExportacion.CSV);
    }

    private void exportarCxc(Button origen, String tituloDialogo, String ext, FormatoExportacion formato) {
        List<String> headers = List.of(
                "Cliente", "Comprobante", "Fecha Emisión", "Vencimiento", "Monto Total", "Cobrado", "Saldo", "Estado");
        List<List<String>> filas = new ArrayList<>();
        for (CuentaPorCobrar c : listaCxc) {
            filas.add(List.of(
                    c.getRazonSocialCliente() != null ? c.getRazonSocialCliente() : "",
                    c.getNumeroComprobante() != null ? c.getNumeroComprobante() : "",
                    c.getFechaEmision() != null ? c.getFechaEmision().toString() : "-",
                    c.getFechaVencimiento() != null ? c.getFechaVencimiento().toString() : "-",
                    String.format(Locale.US, "S/ %.2f", c.getMontoTotal()),
                    String.format(Locale.US, "S/ %.2f", c.getMontoCobrado()),
                    String.format(Locale.US, "S/ %.2f", c.getSaldo()),
                    c.getEstado() != null ? c.getEstado() : ""
            ));
        }
        String sugerido = "CUENTAS_POR_COBRAR_" + LocalDate.now() + ext;
        ReporteExportUIHelper.exportarTablaConSelector(
                origen != null ? origen : lblContadorCxc,
                lblContadorCxc,
                tituloDialogo,
                sugerido,
                "Reporte de Cuentas por Cobrar",
                "CXC",
                formato,
                headers,
                filas,
                exportacionService);
    }
}
