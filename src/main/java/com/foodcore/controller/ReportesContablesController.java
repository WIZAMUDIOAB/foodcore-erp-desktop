package com.foodcore.controller;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.ExportacionContableService.PeriodoContableResumen;
import com.foodcore.service.ExportacionContableService.VentaRegistroSunat;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.PaginadorTablaHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public class ReportesContablesController {

    private static final Logger LOGGER = Logger.getLogger(ReportesContablesController.class.getName());

    @FXML private ComboBox<String> cbPeriodo;
    @FXML private DatePicker dpInicio;
    @FXML private DatePicker dpFin;
    @FXML private Button btnFiltrar;
    @FXML private Button btnExportarVentasSunat;
    @FXML private Button btnExportarComprasSunat;
    @FXML private Button btnExportarResumenCaja;
    @FXML private Label lblEstadoExportacion;
    @FXML private VBox cardKpiVentas;
    @FXML private VBox cardKpiBase;
    @FXML private VBox cardKpiIgv;
    @FXML private VBox cardKpiCompras;
    @FXML private Label lblTotalVentas;
    @FXML private Label lblBaseImponible;
    @FXML private Label lblTotalIgv;
    @FXML private Label lblTotalCompras;
    @FXML private Label lblContadorFilas;
    @FXML private Button btnAnteriorVentas;
    @FXML private Label lblPaginaVentas;
    @FXML private Button btnSiguienteVentas;

    @FXML private TableView<VentaRegistroSunat> tblVentasSunat;
    @FXML private TableColumn<VentaRegistroSunat, String> colFecha;
    @FXML private TableColumn<VentaRegistroSunat, String> colComprobante;
    @FXML private TableColumn<VentaRegistroSunat, String> colCliente;
    @FXML private TableColumn<VentaRegistroSunat, String> colBaseImponible;
    @FXML private TableColumn<VentaRegistroSunat, String> colIgv;
    @FXML private TableColumn<VentaRegistroSunat, String> colTotal;
    @FXML private TableColumn<VentaRegistroSunat, String> colMedioPago;
    @FXML private TableColumn<VentaRegistroSunat, String> colEstado;

    private final ExportacionContableService exportacionService = new ExportacionContableService();
    private final ObservableList<VentaRegistroSunat> listaVentas = FXCollections.observableArrayList();
    private PaginadorTablaHelper<VentaRegistroSunat> paginadorVentas;

    @FXML
    public void initialize() {
        paginadorVentas = new PaginadorTablaHelper<>(listaVentas, lblPaginaVentas, btnAnteriorVentas, btnSiguienteVentas);
        configurarColumnas();
        configurarFiltrosPeriodo();
        lblTotalVentas.setText("S/ 0.00");
        lblBaseImponible.setText("S/ 0.00");
        lblTotalIgv.setText("S/ 0.00");
        lblTotalCompras.setText("S/ 0.00");
        lblContadorFilas.setText("0 Comprobantes");
        lblEstadoExportacion.setText("Cargando período...");
        paginadorVentas.setDatos(List.of());
        cargarDatosPeriodoAsync();
    }

    private void configurarColumnas() {
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fechaEmision));
        colComprobante.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().serie + "-" + String.format("%08d", d.getValue().idVenta)));
        colCliente.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nombreCliente));
        colBaseImponible.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "S/ %,.2f", normalizarMoneda(d.getValue().subtotal))));
        colIgv.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "S/ %,.2f", normalizarMoneda(d.getValue().igv))));
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "S/ %,.2f", normalizarMoneda(d.getValue().total))));
        colMedioPago.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medioPago));
        colEstado.setCellValueFactory(d -> new SimpleStringProperty("1".equals(d.getValue().estado) ? "VÁLIDO" : "ANULADO"));
        tblVentasSunat.setItems(listaVentas);
    }

    private void configurarFiltrosPeriodo() {
        cbPeriodo.setItems(FXCollections.observableArrayList("Mes Actual", "Mes Anterior", "Rango Personalizado"));
        cbPeriodo.setValue("Mes Actual");

        LocalDate hoy = LocalDate.now();
        dpInicio.setValue(hoy.withDayOfMonth(1));
        dpFin.setValue(hoy);

        cbPeriodo.setOnAction(e -> {
            String sel = cbPeriodo.getValue();
            if ("Mes Actual".equals(sel)) {
                dpInicio.setValue(LocalDate.now().withDayOfMonth(1));
                dpFin.setValue(LocalDate.now());
                cargarDatosPeriodoAsync();
            } else if ("Mes Anterior".equals(sel)) {
                YearMonth prev = YearMonth.now().minusMonths(1);
                dpInicio.setValue(prev.atDay(1));
                dpFin.setValue(prev.atEndOfMonth());
                cargarDatosPeriodoAsync();
            }
        });
    }

    @FXML
    public void handleFiltrar() {
        cargarDatosPeriodoAsync();
    }

    public void cargarDatosPeriodoAsync() {
        LocalDate ini = dpInicio.getValue() != null ? dpInicio.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = dpFin.getValue() != null ? dpFin.getValue() : LocalDate.now();
        lblEstadoExportacion.setText("Consultando período...");
        UiAsyncExecutor.ejecutarAsync(
            () -> consultarDatosPeriodo(ini, fin),
            resultado -> aplicarResultadoPeriodo(resultado, ini, fin),
            ex -> {
                LOGGER.log(Level.WARNING, "Error al cargar datos contables", ex);
                lblEstadoExportacion.setText("Error al cargar datos contables");
            }
        );
    }

    public void cargarDatosPeriodoSincrono() {
        LocalDate ini = dpInicio.getValue() != null ? dpInicio.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = dpFin.getValue() != null ? dpFin.getValue() : LocalDate.now();
        PeriodoContableResumen resultado = consultarDatosPeriodo(ini, fin);
        aplicarResultadoPeriodo(resultado, ini, fin);
    }

    private PeriodoContableResumen consultarDatosPeriodo(LocalDate ini, LocalDate fin) {
        return exportacionService.consultarResumenPeriodoContable(ini, fin);
    }

    private void aplicarResultadoPeriodo(PeriodoContableResumen res, LocalDate ini, LocalDate fin) {
        if (paginadorVentas != null) {
            paginadorVentas.setDatos(res.ventas());
        } else {
            listaVentas.setAll(res.ventas());
        }
        lblTotalVentas.setText(String.format(Locale.US, "S/ %,.2f", res.sumTotal()));
        lblBaseImponible.setText(String.format(Locale.US, "S/ %,.2f", res.sumBase()));
        lblTotalIgv.setText(String.format(Locale.US, "S/ %,.2f", res.sumIgv()));
        lblTotalCompras.setText(String.format(Locale.US, "S/ %,.2f", res.sumCompras()));
        lblContadorFilas.setText(res.ventas().size() + " Comprobantes");
        lblEstadoExportacion.setText("Período cargado: " + ini + " al " + fin);
    }

    /**
     * Navega a la página anterior de ventas.
     */
    @FXML
    public void handlePaginaAnterior() {
        if (paginadorVentas != null) {
            paginadorVentas.paginaAnterior();
        }
    }

    /**
     * Navega a la página siguiente de ventas.
     */
    @FXML
    public void handlePaginaSiguiente() {
        if (paginadorVentas != null) {
            paginadorVentas.paginaSiguiente();
        }
    }

    /**
     * Carga ventas directamente en el paginador para pruebas unitarias e inspección visual.
     *
     * @param ventas Lista completa de ventas a paginar.
     */
    public void cargarVentasDirectoParaPaginacion(List<VentaRegistroSunat> ventas) {
        BigDecimal sumTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sumBase = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal sumIgv = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (ventas != null) {
            for (VentaRegistroSunat v : ventas) {
                sumTotal = sumTotal.add(normalizarMoneda(v.total)).setScale(2, RoundingMode.HALF_UP);
                sumBase = sumBase.add(normalizarMoneda(v.subtotal)).setScale(2, RoundingMode.HALF_UP);
                sumIgv = sumIgv.add(normalizarMoneda(v.igv)).setScale(2, RoundingMode.HALF_UP);
            }
        }
        LocalDate hoy = LocalDate.now();
        aplicarResultadoPeriodo(
                new PeriodoContableResumen(ventas != null ? ventas : List.of(),
                        sumTotal, sumBase, sumIgv, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)),
                hoy.withDayOfMonth(1),
                hoy);
    }

    private static BigDecimal normalizarMoneda(BigDecimal valor) {
        return valor != null
                ? valor.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @FXML
    public void handleExportarVentasSunat() {
        LocalDate ini = dpInicio.getValue();
        LocalDate fin = dpFin.getValue();
        exportarConSelector(
            btnExportarVentasSunat,
            "Guardar Registro de Ventas PLE 14.1",
            "PLE_SUNAT_14.1_VENTAS_" + ini + "_" + fin + ".xlsx",
            ExportFileChooserHelper.FormatoExportacion.EXCEL,
            "Exportando registro de ventas...",
            archivo -> exportacionService.exportarVentasSunat14_1_Excel(ini, fin, archivo),
            "✓ Exportado con éxito: ",
            "Error al exportar ventas: "
        );
    }

    @FXML
    public void handleExportarComprasSunat() {
        LocalDate ini = dpInicio.getValue();
        LocalDate fin = dpFin.getValue();
        exportarConSelector(
            btnExportarComprasSunat,
            "Guardar Registro de Compras PLE 8.1",
            "PLE_SUNAT_8.1_COMPRAS_" + ini + "_" + fin + ".xlsx",
            ExportFileChooserHelper.FormatoExportacion.EXCEL,
            "Exportando registro de compras...",
            archivo -> exportacionService.exportarComprasSunat8_1_Excel(ini, fin, archivo),
            "✓ Exportado con éxito: ",
            "Error al exportar compras: "
        );
    }

    @FXML
    public void handleExportarResumenCaja() {
        LocalDate ini = dpInicio.getValue();
        LocalDate fin = dpFin.getValue();
        exportarConSelector(
            btnExportarResumenCaja,
            "Guardar Resumen Mensual de Caja Z",
            "RESUMEN_CAJA_Z_" + ini + "_" + fin + ".pdf",
            ExportFileChooserHelper.FormatoExportacion.PDF,
            "Generando resumen Z...",
            archivo -> exportacionService.exportarResumenCajaMensualPdf(ini, fin, archivo),
            "✓ Resumen Z generado: ",
            "Error al exportar resumen caja: "
        );
    }

    @FunctionalInterface
    private interface ExportAction {
        void write(File destino) throws Exception;
    }

    private void exportarConSelector(
            Button origenBtn,
            String tituloDialogo,
            String nombreSugerido,
            ExportFileChooserHelper.FormatoExportacion formato,
            String estadoProcesando,
            ExportAction accion,
            String prefijoExito,
            String prefijoError) {
        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                origenBtn != null ? origenBtn : lblEstadoExportacion,
                tituloDialogo,
                nombreSugerido,
                formato);
        if (elegidoOpt.isEmpty()) {
            if (lblEstadoExportacion != null) {
                lblEstadoExportacion.setText("Exportación cancelada.");
            }
            return;
        }
        File archivo = elegidoOpt.get();
        if (lblEstadoExportacion != null) {
            lblEstadoExportacion.setText(estadoProcesando);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                if (archivo.getParentFile() != null) {
                    Files.createDirectories(archivo.getParentFile().toPath());
                }
                accion.write(archivo);
                return archivo.getName();
            },
            nombre -> {
                if (lblEstadoExportacion != null) {
                    lblEstadoExportacion.setText(prefijoExito + nombre);
                }
            },
            ex -> {
                if (lblEstadoExportacion != null) {
                    lblEstadoExportacion.setText(prefijoError + (ex != null ? ex.getMessage() : "desconocido"));
                }
            }
        );
    }

    public VBox getCardKpiVentas() {
        return cardKpiVentas;
    }

    public VBox getCardKpiBase() {
        return cardKpiBase;
    }

    public VBox getCardKpiIgv() {
        return cardKpiIgv;
    }

    public VBox getCardKpiCompras() {
        return cardKpiCompras;
    }

    public Label getLblTotalVentas() {
        return lblTotalVentas;
    }

    public Label getLblBaseImponible() {
        return lblBaseImponible;
    }

    public Label getLblTotalIgv() {
        return lblTotalIgv;
    }

    public Label getLblTotalCompras() {
        return lblTotalCompras;
    }
}
