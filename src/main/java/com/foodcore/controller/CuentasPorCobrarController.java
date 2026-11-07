package com.foodcore.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.CuentaPorCobrar;
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

    @FXML private VBox modalCobro;
    @FXML private Label lblModalDocInfo;
    @FXML private Label lblModalSaldoPendiente;
    @FXML private TextField txtModalMontoCobrar;
    @FXML private ComboBox<String> cbModalMetodoPago;
    @FXML private TextField txtModalNota;
    @FXML private Label lblModalCobroError;

    private final CuentaPorCobrarDAO cuentaPorCobrarDAO = new CuentaPorCobrarDAOImpl();
    private final CajaDAO cajaDAO = new CajaDAOImpl();
    private final FinanzasService finanzasService = new FinanzasService();

    private final ObservableList<CuentaPorCobrar> listaCxc = FXCollections.observableArrayList();
    private CuentaPorCobrar cuentaSeleccionada = null;

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarFiltros();
        cargarCuentasSincrono();
    }

    private void configurarColumnas() {
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocialCliente()));
        colComprobante.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroComprobante()));
        colEmision.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaEmision() != null ? c.getValue().getFechaEmision().toString() : "-"));
        colVencimiento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaVencimiento() != null ? c.getValue().getFechaVencimiento().toString() : "-"));
        colMontoTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getMontoTotal())));
        colMontoCobrado.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getMontoCobrado())));
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getSaldo())));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        tblCuentasPorCobrar.setItems(listaCxc);

        tblCuentasPorCobrar.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            this.cuentaSeleccionada = newV;
        });
    }

    private void configurarFiltros() {
        cbFiltroEstado.setItems(FXCollections.observableArrayList("TODAS", "PENDIENTE", "VENCIDA", "COBRADA"));
        cbFiltroEstado.setValue("TODAS");
        cbFiltroEstado.setOnAction(e -> cargarCuentasSincrono());

        cbModalMetodoPago.setItems(FXCollections.observableArrayList("EFECTIVO", "TRANSFERENCIA", "YAPE", "TARJETA"));
        cbModalMetodoPago.setValue("TRANSFERENCIA");
    }

    public void cargarCuentasSincrono() {
        listaCxc.clear();
        double saldoTotal = 0.0;
        int vencidas = 0;

        String filtro = cbFiltroEstado.getValue();
        try (Connection conn = ConexionBD.getConnection()) {
            List<CuentaPorCobrar> cuentas;
            if (filtro == null || "TODAS".equals(filtro)) {
                cuentas = cuentaPorCobrarDAO.listarTodas(conn);
            } else {
                cuentas = cuentaPorCobrarDAO.listarPorEstado(conn, filtro);
            }

            listaCxc.addAll(cuentas);
            for (CuentaPorCobrar c : cuentas) {
                if (!"COBRADA".equalsIgnoreCase(c.getEstado())) {
                    saldoTotal += c.getSaldo();
                }
                if ("VENCIDA".equalsIgnoreCase(c.getEstado())) {
                    vencidas++;
                }
            }
            if (!cuentas.isEmpty()) {
                tblCuentasPorCobrar.getSelectionModel().selectFirst();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar cuentas por cobrar", ex);
        }

        lblSaldoTotal.setText(String.format("S/ %.2f", saldoTotal));
        lblContadorCxc.setText(listaCxc.size() + " Comprobantes");
        if (vencidas > 0) {
            lblBadgeAlertaVencidas.setText(vencidas + " COMPROBANTES VENCIDOS");
            lblBadgeAlertaVencidas.setVisible(true);
        } else {
            lblBadgeAlertaVencidas.setVisible(false);
        }
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
        lblModalSaldoPendiente.setText(String.format("S/ %.2f", sel.getSaldo()));
        txtModalMontoCobrar.setText(String.format("%.2f", sel.getSaldo()));
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
        double monto;
        try {
            monto = Double.parseDouble(txtModalMontoCobrar.getText().trim());
        } catch (Exception ex) {
            lblModalCobroError.setText("Ingrese un importe numérico válido");
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
            finanzasService.registrarCobroCliente(cuentaSeleccionada.getIdCuentaCobrar(), monto, metodo, idCaja, idUsuario, nota);
            modalCobro.setVisible(false);
            cargarCuentasSincrono();
        } catch (Exception ex) {
            lblModalCobroError.setText("Error al cobrar: " + ex.getMessage());
        }
    }
}
