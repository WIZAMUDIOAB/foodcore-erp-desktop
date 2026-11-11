package com.foodcore.controller;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.dao.CompraDAO;
import com.foodcore.dao.impl.CompraDAOImpl;
import com.foodcore.model.Compra;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.ExportacionContableService.VentaRegistroSunat;
import com.foodcore.util.ConexionBD;
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
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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
    private final CompraDAO compraDAO = new CompraDAOImpl();
    private final ObservableList<VentaRegistroSunat> listaVentas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarFiltrosPeriodo();
        cargarDatosPeriodoSincrono();
    }

    private void configurarColumnas() {
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fechaEmision));
        colComprobante.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().serie + "-" + String.format("%08d", d.getValue().idVenta)));
        colCliente.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nombreCliente));
        colBaseImponible.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "S/ %,.2f", d.getValue().subtotal)));
        colIgv.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "S/ %,.2f", d.getValue().igv)));
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(String.format(Locale.US, "S/ %,.2f", d.getValue().total)));
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
                cargarDatosPeriodoSincrono();
            } else if ("Mes Anterior".equals(sel)) {
                YearMonth prev = YearMonth.now().minusMonths(1);
                dpInicio.setValue(prev.atDay(1));
                dpFin.setValue(prev.atEndOfMonth());
                cargarDatosPeriodoSincrono();
            }
        });
    }

    @FXML
    public void handleFiltrar() {
        cargarDatosPeriodoSincrono();
    }

    public void cargarDatosPeriodoSincrono() {
        LocalDate ini = dpInicio.getValue() != null ? dpInicio.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate fin = dpFin.getValue() != null ? dpFin.getValue() : LocalDate.now();

        listaVentas.clear();
        double sumTotal = 0.0;
        double sumBase = 0.0;
        double sumIgv = 0.0;
        double sumCompras = 0.0;

        try (Connection conn = ConexionBD.getConnection()) {
            // Cargar ventas
            String sqlVentas = "SELECT v.*, p.cliente_nombre "
                    + "FROM venta v "
                    + "LEFT JOIN pedido p ON v.id_pedido = p.id_pedido "
                    + "WHERE DATE(v.fecha_hora) BETWEEN ? AND ? "
                    + "ORDER BY v.fecha_hora DESC";

            try (PreparedStatement ps = conn.prepareStatement(sqlVentas)) {
                ps.setDate(1, Date.valueOf(ini));
                ps.setDate(2, Date.valueOf(fin));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        VentaRegistroSunat r = new VentaRegistroSunat();
                        r.idVenta = rs.getInt("id_venta");
                        r.fechaEmision = rs.getTimestamp("fecha_hora").toLocalDateTime().toLocalDate().toString();
                        r.serie = "B001";
                        String cli = rs.getString("cliente_nombre");
                        r.nombreCliente = (cli != null && !cli.isBlank()) ? cli : "CLIENTES VARIOS";
                        r.subtotal = rs.getDouble("subtotal");
                        r.igv = rs.getDouble("igv");
                        r.total = rs.getDouble("total");
                        r.medioPago = rs.getString("metodo_pago");
                        r.estado = "COMPLETADA".equalsIgnoreCase(rs.getString("estado")) ? "1" : "2";

                        listaVentas.add(r);
                        sumTotal += r.total;
                        sumBase += r.subtotal;
                        sumIgv += r.igv;
                    }
                }
            }

            // Cargar total compras
            List<Compra> compras = compraDAO.listarPorRangoFecha(conn, ini, fin);
            for (Compra c : compras) {
                sumCompras += c.getTotal();
            }

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar datos contables", ex);
        }

        lblTotalVentas.setText(String.format(Locale.US, "S/ %,.2f", sumTotal));
        lblBaseImponible.setText(String.format(Locale.US, "S/ %,.2f", sumBase));
        lblTotalIgv.setText(String.format(Locale.US, "S/ %,.2f", sumIgv));
        lblTotalCompras.setText(String.format(Locale.US, "S/ %,.2f", sumCompras));
        lblContadorFilas.setText(listaVentas.size() + " Comprobantes");
        lblEstadoExportacion.setText("Período cargado: " + ini + " al " + fin);
    }

    @FXML
    public void handleExportarVentasSunat() {
        try {
            LocalDate ini = dpInicio.getValue();
            LocalDate fin = dpFin.getValue();
            File archivo = new File("dist/PLE_SUNAT_14.1_VENTAS_" + ini + "_" + fin + ".xlsx");
            if (archivo.getParentFile() != null) { java.nio.file.Files.createDirectories(archivo.getParentFile().toPath()); }
            exportacionService.exportarVentasSunat14_1_Excel(ini, fin, archivo);
            lblEstadoExportacion.setText("✓ Exportado con éxito: " + archivo.getName());
        } catch (Exception ex) {
            lblEstadoExportacion.setText("Error al exportar ventas: " + ex.getMessage());
        }
    }

    @FXML
    public void handleExportarComprasSunat() {
        try {
            LocalDate ini = dpInicio.getValue();
            LocalDate fin = dpFin.getValue();
            File archivo = new File("dist/PLE_SUNAT_8.1_COMPRAS_" + ini + "_" + fin + ".xlsx");
            if (archivo.getParentFile() != null) { java.nio.file.Files.createDirectories(archivo.getParentFile().toPath()); }
            exportacionService.exportarComprasSunat8_1_Excel(ini, fin, archivo);
            lblEstadoExportacion.setText("✓ Exportado con éxito: " + archivo.getName());
        } catch (Exception ex) {
            lblEstadoExportacion.setText("Error al exportar compras: " + ex.getMessage());
        }
    }

    @FXML
    public void handleExportarResumenCaja() {
        try {
            LocalDate ini = dpInicio.getValue();
            LocalDate fin = dpFin.getValue();
            File archivo = new File("dist/RESUMEN_CAJA_Z_" + ini + "_" + fin + ".pdf");
            if (archivo.getParentFile() != null) { java.nio.file.Files.createDirectories(archivo.getParentFile().toPath()); }
            exportacionService.exportarResumenCajaMensualPdf(ini, fin, archivo);
            lblEstadoExportacion.setText("✓ Resumen Z generado: " + archivo.getName());
        } catch (Exception ex) {
            lblEstadoExportacion.setText("Error al exportar resumen caja: " + ex.getMessage());
        }
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
