package com.foodcore.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.impl.CuentaPorPagarDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.service.AuthService;
import com.foodcore.service.FinanzasService;
import com.foodcore.util.ConexionBD;
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

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

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

    @FXML private VBox modalPago;
    @FXML private Label lblModalDocInfo;
    @FXML private Label lblModalSaldoPendiente;
    @FXML private TextField txtModalMontoAbonar;
    @FXML private ComboBox<String> cbModalMetodoPago;
    @FXML private TextField txtModalNota;
    @FXML private Label lblModalPagoError;

    private final CuentaPorPagarDAO cuentaPorPagarDAO = new CuentaPorPagarDAOImpl();
    private final CajaDAO cajaDAO = new CajaDAOImpl();
    private final FinanzasService finanzasService = new FinanzasService();

    private final ObservableList<CuentaPorPagar> listaCxp = FXCollections.observableArrayList();
    private CuentaPorPagar cuentaSeleccionada = null;

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarFiltros();
        cargarCuentasSincrono();
    }

    private void configurarColumnas() {
        colProveedor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocialProveedor()));
        colDocumento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroDocumento()));
        colEmision.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaEmision() != null ? c.getValue().getFechaEmision().toString() : "-"));
        colVencimiento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaVencimiento() != null ? c.getValue().getFechaVencimiento().toString() : "-"));
        colMontoTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getMontoTotal())));
        colMontoPagado.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getMontoPagado())));
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getSaldo())));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        tblCuentasPorPagar.setItems(listaCxp);

        tblCuentasPorPagar.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            this.cuentaSeleccionada = newV;
        });
    }

    private void configurarFiltros() {
        cbFiltroEstado.setItems(FXCollections.observableArrayList("TODAS", "PENDIENTE", "VENCIDA", "PAGADA"));
        cbFiltroEstado.setValue("TODAS");
        cbFiltroEstado.setOnAction(e -> cargarCuentasSincrono());

        cbModalMetodoPago.setItems(FXCollections.observableArrayList("EFECTIVO", "TRANSFERENCIA", "TARJETA"));
        cbModalMetodoPago.setValue("EFECTIVO");
    }

    public void cargarCuentasSincrono() {
        listaCxp.clear();
        double deudaTotal = 0.0;
        int vencidas = 0;

        String filtro = cbFiltroEstado.getValue();
        try (Connection conn = ConexionBD.getConnection()) {
            List<CuentaPorPagar> cuentas;
            if (filtro == null || "TODAS".equals(filtro)) {
                cuentas = cuentaPorPagarDAO.listarTodas(conn);
            } else {
                cuentas = cuentaPorPagarDAO.listarPorEstado(conn, filtro);
            }

            listaCxp.addAll(cuentas);
            for (CuentaPorPagar c : cuentas) {
                if (!"PAGADA".equalsIgnoreCase(c.getEstado())) {
                    deudaTotal += c.getSaldo();
                }
                if ("VENCIDA".equalsIgnoreCase(c.getEstado())) {
                    vencidas++;
                }
            }
            if (!cuentas.isEmpty()) {
                tblCuentasPorPagar.getSelectionModel().selectFirst();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar cuentas por pagar", ex);
        }

        lblDeudaTotal.setText(String.format("S/ %.2f", deudaTotal));
        lblContadorCxp.setText(listaCxp.size() + " Facturas");
        if (vencidas > 0) {
            lblBadgeAlertaVencidas.setText(vencidas + " FACTURAS VENCIDAS");
            lblBadgeAlertaVencidas.setVisible(true);
        } else {
            lblBadgeAlertaVencidas.setVisible(false);
        }
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
        lblModalSaldoPendiente.setText(String.format("S/ %.2f", sel.getSaldo()));
        txtModalMontoAbonar.setText(String.format("%.2f", sel.getSaldo()));
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
        double monto;
        try {
            monto = Double.parseDouble(txtModalMontoAbonar.getText().trim());
        } catch (Exception ex) {
            lblModalPagoError.setText("Ingrese un importe numérico válido");
            return;
        }

        String metodo = cbModalMetodoPago.getValue();
        String nota = txtModalNota.getText().trim();
        int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;

        Integer idCaja = null;
        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Caja> cajaOpt = cajaDAO.obtenerCajaAbierta(conn);
            if (cajaOpt.isPresent()) {
                idCaja = cajaOpt.get().getIdCaja();
            }
        } catch (Exception ignored) {}

        try {
            finanzasService.registrarPagoProveedor(cuentaSeleccionada.getIdCuentaPagar(), monto, metodo, idCaja, idUsuario, nota);
            modalPago.setVisible(false);
            cargarCuentasSincrono();
        } catch (Exception ex) {
            lblModalPagoError.setText("Error al abonar: " + ex.getMessage());
        }
    }
}
