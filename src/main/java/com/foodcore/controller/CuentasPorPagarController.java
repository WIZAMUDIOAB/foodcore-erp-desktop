package com.foodcore.controller;

import java.util.logging.Level;
import java.util.logging.Logger;


import com.foodcore.model.CuentaPorPagar;
import com.foodcore.service.AuthService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasService;
import com.foodcore.service.CuentasPorPagarService;
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


public class CuentasPorPagarController {

    private static final Logger LOGGER = Logger.getLogger(CuentasPorPagarController.class.getName());

    @FXML private TableView<CuentaPorPagar> tblCuentasPorPagar;
    @FXML private TableColumn<CuentaPorPagar, String> colProveedor;
    @FXML private TableColumn<CuentaPorPagar, String> colDocumento;
    @FXML private TableColumn<CuentaPorPagar, String> colEmision;
    @FXML private TableColumn<CuentaPorPagar, String> colVencimiento;
    @FXML private TableColumn<CuentaPorPagar, String> colMontoTotal;
    @FXML private TableColumn<CuentaPorPagar, String> colMontoPagado;
    @FXML private TableColumn<CuentaPorPagar, String> colSaldo;
    @FXML private TableColumn<CuentaPorPagar, String> colEstado;

    @FXML private ComboBox<String> cbFiltroEstado;
    @FXML private Label lblDeudaTotal;
    @FXML private Label lblContadorCxp;
    @FXML private Label lblBadgeAlertaVencidas;
    @FXML private Button btnAbonar;
    @FXML private Button btnActualizar;
    @FXML private Button btnExportarPdf;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnExportarCsv;

    @FXML private VBox modalPago;
    @FXML private Label lblModalDocInfo;
    @FXML private Label lblModalSaldoPendiente;
    @FXML private TextField txtModalMontoAbonar;
    @FXML private ComboBox<String> cbModalMetodoPago;
    @FXML private TextField txtModalNota;
    @FXML private Label lblModalPagoError;

    private final CuentasPorPagarService cuentasPorPagarService = new CuentasPorPagarService();
    private final FinanzasService finanzasService = new FinanzasService();
    private final ExportacionContableService exportacionService = new ExportacionContableService();

    private final ObservableList<CuentaPorPagar> listaCxp = FXCollections.observableArrayList();
    private CuentaPorPagar cuentaSeleccionada = null;

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarFiltros();
        cargarCuentasAsync();
    }

    private void configurarColumnas() {
        colProveedor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocialProveedor()));
        colDocumento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroDocumento()));
        colEmision.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaEmision() != null ? c.getValue().getFechaEmision().toString() : "-"));
        colVencimiento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaVencimiento() != null ? c.getValue().getFechaVencimiento().toString() : "-"));
        colMontoTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "S/ %.2f", c.getValue().getMontoTotal())));
        colMontoPagado.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "S/ %.2f", c.getValue().getMontoPagado())));
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "S/ %.2f", c.getValue().getSaldo())));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        tblCuentasPorPagar.setItems(listaCxp);

        tblCuentasPorPagar.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            this.cuentaSeleccionada = newV;
        });
    }

    private void configurarFiltros() {
        cbFiltroEstado.setItems(FXCollections.observableArrayList("TODAS", "PENDIENTE", "VENCIDA", "PAGADA"));
        cbFiltroEstado.setValue("TODAS");
        cbFiltroEstado.setOnAction(e -> cargarCuentasAsync());

        cbModalMetodoPago.setItems(FXCollections.observableArrayList("EFECTIVO", "TRANSFERENCIA", "TARJETA"));
        cbModalMetodoPago.setValue("EFECTIVO");
    }

    public void cargarCuentasAsync() {
        final String filtro = cbFiltroEstado.getValue();
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                return cuentasPorPagarService.listarPorFiltro(filtro);
            },
            cuentas -> {
                listaCxp.setAll(cuentas);
                BigDecimal deudaTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                int vencidas = 0;
                for (CuentaPorPagar c : cuentas) {
                    if (!"PAGADA".equalsIgnoreCase(c.getEstado())) {
                        deudaTotal = deudaTotal.add(c.getSaldo()).setScale(2, RoundingMode.HALF_UP);
                    }
                    if ("VENCIDA".equalsIgnoreCase(c.getEstado())) {
                        vencidas++;
                    }
                }
                if (!cuentas.isEmpty()) {
                    tblCuentasPorPagar.getSelectionModel().selectFirst();
                }
                lblDeudaTotal.setText(String.format(Locale.US, "S/ %.2f", deudaTotal));
                lblContadorCxp.setText(listaCxp.size() + " Facturas");
                if (vencidas > 0) {
                    lblBadgeAlertaVencidas.setText(vencidas + " FACTURAS VENCIDAS");
                    lblBadgeAlertaVencidas.setVisible(true);
                } else {
                    lblBadgeAlertaVencidas.setVisible(false);
                }
            },
            ex -> LOGGER.log(Level.WARNING, "Error al cargar cuentas por pagar", ex)
        );
    }

    public void cargarCuentasSincrono() {
        cargarCuentasAsync();
    }

    @FXML
    public void handleAbrirModalPago() {
        CuentaPorPagar sel = tblCuentasPorPagar.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if ("PAGADA".equalsIgnoreCase(sel.getEstado())) {
            return;
        }

        this.cuentaSeleccionada = sel;
        lblModalDocInfo.setText(sel.getRazonSocialProveedor() + " — Comprobante: " + sel.getNumeroDocumento());
        lblModalSaldoPendiente.setText(String.format(Locale.US, "S/ %.2f", sel.getSaldo()));
        txtModalMontoAbonar.setText(String.format(Locale.US, "%.2f", sel.getSaldo()));
        txtModalNota.setText("Abono comprobante " + sel.getNumeroDocumento());
        lblModalPagoError.setText("");
        modalPago.setVisible(true);
    }

    @FXML
    public void handleCerrarModalPago() {
        modalPago.setVisible(false);
    }

    @FXML
    public void handleConfirmarPago() {
        if (cuentaSeleccionada == null) return;
        BigDecimal monto;
        try {
            String textoMonto = txtModalMontoAbonar.getText() != null
                    ? txtModalMontoAbonar.getText().trim().replace(',', '.')
                    : "";
            monto = new BigDecimal(textoMonto).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            lblModalPagoError.setText("Ingrese un importe numérico válido");
            return;
        }

        String metodo = cbModalMetodoPago.getValue();
        String nota = txtModalNota.getText().trim();
        int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;

        final int idCuenta = cuentaSeleccionada.getIdCuentaPagar();
        final BigDecimal montoFinal = monto;
        final String metodoFinal = metodo;
        final String notaFinal = nota;
        final int idUsrFinal = idUsuario;

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                Integer idCaja = cuentasPorPagarService.obtenerIdCajaAbiertaOptional();
                finanzasService.registrarPagoProveedor(idCuenta, montoFinal, metodoFinal, idCaja, idUsrFinal, notaFinal);
            },
            () -> {
                modalPago.setVisible(false);
                cargarCuentasAsync();
            },
            ex -> lblModalPagoError.setText("Error al abonar: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    @FXML
    public void handleExportarPdf() {
        exportarCxp(btnExportarPdf, "Guardar Cuentas por Pagar en PDF", ".pdf", FormatoExportacion.PDF);
    }

    @FXML
    public void handleExportarExcel() {
        exportarCxp(btnExportarExcel, "Guardar Cuentas por Pagar en Excel", ".xlsx", FormatoExportacion.EXCEL);
    }

    @FXML
    public void handleExportarCsv() {
        exportarCxp(btnExportarCsv, "Guardar Cuentas por Pagar en CSV", ".csv", FormatoExportacion.CSV);
    }

    private void exportarCxp(Button origen, String tituloDialogo, String ext, FormatoExportacion formato) {
        List<String> headers = List.of(
                "Proveedor", "Comprobante", "Fecha Emisión", "Vencimiento", "Monto Total", "Pagado", "Saldo", "Estado");
        List<List<String>> filas = new ArrayList<>();
        for (CuentaPorPagar c : listaCxp) {
            filas.add(List.of(
                    c.getRazonSocialProveedor() != null ? c.getRazonSocialProveedor() : "",
                    c.getNumeroDocumento() != null ? c.getNumeroDocumento() : "",
                    c.getFechaEmision() != null ? c.getFechaEmision().toString() : "-",
                    c.getFechaVencimiento() != null ? c.getFechaVencimiento().toString() : "-",
                    String.format(Locale.US, "S/ %.2f", c.getMontoTotal()),
                    String.format(Locale.US, "S/ %.2f", c.getMontoPagado()),
                    String.format(Locale.US, "S/ %.2f", c.getSaldo()),
                    c.getEstado() != null ? c.getEstado() : ""
            ));
        }
        String sugerido = "CUENTAS_POR_PAGAR_" + LocalDate.now() + ext;
        ReporteExportUIHelper.exportarTablaConSelector(
                origen != null ? origen : lblContadorCxp,
                lblContadorCxp,
                tituloDialogo,
                sugerido,
                "Reporte de Cuentas por Pagar",
                "CXP",
                formato,
                headers,
                filas,
                exportacionService);
    }
}
