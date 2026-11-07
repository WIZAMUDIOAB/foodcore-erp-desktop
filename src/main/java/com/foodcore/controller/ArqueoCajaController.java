package com.foodcore.controller;

import com.foodcore.model.Caja;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Caja, Arqueo, Gastos Operativos (§3.4) y Cierre Z (§9):
 * Una columna centrada max 720px, inputs de 48px con foco naranja, cifras en mono grande,
 * diferencia coloreada condicionalmente (0.00 en verde, != 0 en rojo), confirmación modal
 * y Cierre Z con tabla monoespaciada por medio de pago.
 */
public class ArqueoCajaController {

    private static final Logger LOGGER = Logger.getLogger(ArqueoCajaController.class.getName());

    public record FilaCierreZ(String medioPago, String operaciones, String totalRecaudado) {
    }

    @FXML private StackPane rootCajaStack;
    @FXML private Label lblEstadoCajaBadge;
    @FXML private VBox panelApertura;
    @FXML private TextField txtAperturaNombre;
    @FXML private TextField txtAperturaMonto;

    @FXML private VBox panelArqueo;
    @FXML private Label lblNombreCaja;
    @FXML private Label lblFondoApertura;
    @FXML private Label lblTotalVentas;
    @FXML private Label lblTotalEsperado;
    @FXML private Label lblMontoContado;
    @FXML private TextField txtEfectivoFisico;
    @FXML private Label lblDiferencia;
    @FXML private Button btnRegistrarGasto;
    @FXML private Button btnCerrarTurno;

    @FXML private TableView<FilaCierreZ> tblCierreZ;
    @FXML private TableColumn<FilaCierreZ, String> colMedioPago;
    @FXML private TableColumn<FilaCierreZ, String> colOperaciones;
    @FXML private TableColumn<FilaCierreZ, String> colTotalMedio;

    private final CajaService cajaService = new CajaService();
    private final ObservableList<FilaCierreZ> itemsCierreZ = FXCollections.observableArrayList();
    private Caja cajaActual;
    private BigDecimal totalEsperado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @FXML
    public void initialize() {
        configurarTablaCierreZ();
        AnimationUtils.attachTactileFeedback(btnRegistrarGasto);
        AnimationUtils.attachTactileFeedback(btnCerrarTurno);
        cargarDatosCaja();
    }

    private void configurarTablaCierreZ() {
        if (tblCierreZ == null) {
            return;
        }
        colMedioPago.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().medioPago()));
        colOperaciones.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().operaciones()));
        colTotalMedio.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().totalRecaudado()));

        colOperaciones.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if (!getStyleClass().contains("text-mono")) {
                        getStyleClass().add("text-mono");
                    }
                }
            }
        });

        colTotalMedio.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if (!getStyleClass().contains("text-mono")) {
                        getStyleClass().add("text-mono");
                    }
                }
            }
        });

        tblCierreZ.setItems(itemsCierreZ);
    }

    public void cargarDatosCaja() {
        new Thread(() -> {
            try {
                Optional<Caja> opt = cajaService.obtenerCajaActiva();
                if (opt.isPresent()) {
                    Caja activa = opt.get();
                    BigDecimal ventas = cajaService.calcularVentasTurno(activa.getIdCaja())
                        .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal gastos = cajaService.calcularGastosTurno(activa.getIdCaja())
                        .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal apertura = activa.getMontoApertura().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal esperado = apertura.add(ventas).subtract(gastos).setScale(2, RoundingMode.HALF_UP);
                    ObservableList<FilaCierreZ> desgloseZ = consultarDesgloseCierreZ(activa.getIdCaja(), gastos);

                    Platform.runLater(() -> aplicarEstadoCajaAbierta(activa, apertura, ventas, gastos, esperado, desgloseZ));
                } else {
                    Platform.runLater(this::aplicarEstadoCajaCerrada);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al consultar estado de caja", e);
                Platform.runLater(() -> AnimationUtils.mostrarToast(
                    rootCajaStack, "Error al consultar caja: " + e.getMessage(), true
                ));
            }
        }).start();
    }

    private ObservableList<FilaCierreZ> consultarDesgloseCierreZ(int idCaja, BigDecimal gastosEfectivo) {
        Map<String, Integer> conteoPorMedio = new LinkedHashMap<>();
        Map<String, BigDecimal> montoPorMedio = new LinkedHashMap<>();
        for (String medio : new String[]{"EFECTIVO", "TARJETA", "YAPE_PLIN"}) {
            conteoPorMedio.put(medio, 0);
            montoPorMedio.put(medio, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        String sql = "SELECT metodo_pago, COUNT(*) AS ops, COALESCE(SUM(total), 0) AS monto "
            + "FROM venta WHERE id_caja = ? AND estado = 'COMPLETADA' GROUP BY metodo_pago";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String m = rs.getString("metodo_pago");
                    int ops = rs.getInt("ops");
                    BigDecimal monto = rs.getBigDecimal("monto").setScale(2, RoundingMode.HALF_UP);
                    conteoPorMedio.put(m, ops);
                    montoPorMedio.put(m, monto);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Sin ventas registradas en Cierre Z", e);
        }

        ObservableList<FilaCierreZ> filas = FXCollections.observableArrayList();
        int totalOps = 0;
        BigDecimal granTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Map.Entry<String, BigDecimal> entry : montoPorMedio.entrySet()) {
            String medio = entry.getKey();
            int ops = conteoPorMedio.getOrDefault(medio, 0);
            BigDecimal sub = entry.getValue();
            totalOps += ops;
            granTotal = granTotal.add(sub);
            filas.add(new FilaCierreZ(medio, String.valueOf(ops), "S/ " + sub));
        }
        if (gastosEfectivo != null && gastosEfectivo.compareTo(BigDecimal.ZERO) > 0) {
            filas.add(new FilaCierreZ("EGRESOS CAJA CHICA", "-", "-S/ " + gastosEfectivo));
        }
        filas.add(new FilaCierreZ("TOTAL CONSOLIDADO Z", String.valueOf(totalOps), "S/ " + granTotal));
        return filas;
    }

    private void aplicarEstadoCajaAbierta(Caja activa,
                                          BigDecimal apertura,
                                          BigDecimal ventas,
                                          BigDecimal gastos,
                                          BigDecimal esperado,
                                          ObservableList<FilaCierreZ> desgloseZ) {
        this.cajaActual = activa;
        this.totalEsperado = esperado;

        if (lblEstadoCajaBadge != null) {
            lblEstadoCajaBadge.setText("TURNO ABIERTO");
            lblEstadoCajaBadge.getStyleClass().setAll("badge-pill-success");
        }
        lblNombreCaja.setText(activa.getNombreCaja());
        lblFondoApertura.setText("S/ " + apertura);
        if (gastos != null && gastos.compareTo(BigDecimal.ZERO) > 0) {
            lblTotalVentas.setText("Ventas: S/ " + ventas + " · Gastos: -S/ " + gastos);
        } else {
            lblTotalVentas.setText("Ventas del turno: S/ " + ventas);
        }
        lblTotalEsperado.setText("S/ " + esperado);

        txtEfectivoFisico.setText(esperado.toPlainString());
        calcularDiferencia();

        itemsCierreZ.setAll(desgloseZ);

        panelApertura.setVisible(false);
        panelApertura.setManaged(false);
        panelArqueo.setVisible(true);
        panelArqueo.setManaged(true);
    }

    private void aplicarEstadoCajaCerrada() {
        this.cajaActual = null;
        if (lblEstadoCajaBadge != null) {
            lblEstadoCajaBadge.setText("TURNO CERRADO");
            lblEstadoCajaBadge.getStyleClass().setAll("badge-pill-neutral");
        }
        panelArqueo.setVisible(false);
        panelArqueo.setManaged(false);
        panelApertura.setVisible(true);
        panelApertura.setManaged(true);
    }

    @FXML
    public void handleAbrirTurnoCaja(ActionEvent event) {
        try {
            String nombre = txtAperturaNombre.getText() != null ? txtAperturaNombre.getText().trim() : "Caja Principal 01";
            BigDecimal fondo = new BigDecimal(txtAperturaMonto.getText().trim()).setScale(2, RoundingMode.HALF_UP);

            int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
            cajaService.abrirTurnoCaja(idUsuario, nombre, fondo);

            AnimationUtils.mostrarToast(rootCajaStack, "Turno de caja abierto con saldo inicial S/ " + fondo, false);
            cargarDatosCaja();
        } catch (NumberFormatException e) {
            AnimationUtils.mostrarToast(rootCajaStack, "Ingrese un monto numérico válido para el saldo inicial.", true);
        } catch (Exception e) {
            AnimationUtils.mostrarToast(rootCajaStack, e.getMessage(), true);
        }
    }

    /**
     * Abre modal para registrar un gasto operativo / salida de caja chica (§3.4).
     */
    @FXML
    public void handleAbrirModalGasto(ActionEvent event) {
        if (cajaActual == null || rootCajaStack == null) {
            return;
        }
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        VBox card = new VBox(14);
        card.setMaxWidth(420);
        card.setMaxHeight(310);
        card.getStyleClass().add("modal-dialog-surface");

        Label lblTitle = new Label("Registrar Gasto Operativo");
        lblTitle.getStyleClass().add("text-h1");

        Label lblSub = new Label("Salida de efectivo de caja chica (afecta el arqueo físico pero no el IGV ni las ventas).");
        lblSub.getStyleClass().add("text-body-secondary");
        lblSub.setWrapText(true);

        TextField txtMontoGasto = new TextField();
        txtMontoGasto.setPromptText("Monto en S/ (ej. 15.00)");
        txtMontoGasto.getStyleClass().add("input-field");

        TextField txtConceptoGasto = new TextField();
        txtConceptoGasto.setPromptText("Motivo (ej. Compra de hielo, movilidad)");
        txtConceptoGasto.getStyleClass().add("input-field");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootCajaStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Registrar Egreso");
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setOnAction(ev -> {
            try {
                BigDecimal monto = new BigDecimal(txtMontoGasto.getText().trim()).setScale(2, RoundingMode.HALF_UP);
                String concepto = txtConceptoGasto.getText() != null ? txtConceptoGasto.getText().trim() : "";
                int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
                cajaService.registrarGastoOperativo(cajaActual.getIdCaja(), idUsuario, monto, concepto);
                rootCajaStack.getChildren().remove(backdrop);
                AnimationUtils.mostrarToast(rootCajaStack, "Gasto operativo de S/ " + monto + " registrado.", false);
                cargarDatosCaja();
            } catch (Exception ex) {
                AnimationUtils.mostrarToast(rootCajaStack, ex.getMessage(), true);
            }
        });

        HBox actions = new HBox(12, btnCancelar, btnGuardar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, lblSub, txtMontoGasto, txtConceptoGasto, actions);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootCajaStack.getChildren().add(backdrop);
    }

    @FXML
    public void calcularDiferencia() {
        try {
            String texto = txtEfectivoFisico.getText() != null ? txtEfectivoFisico.getText().trim() : "";
            if (texto.isEmpty()) {
                if (lblMontoContado != null) {
                    lblMontoContado.setText("S/ 0.00");
                }
                actualizarEstiloDiferencia(BigDecimal.ZERO.subtract(totalEsperado));
                return;
            }
            BigDecimal fisico = new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
            if (lblMontoContado != null) {
                lblMontoContado.setText("S/ " + fisico);
            }
            BigDecimal dif = fisico.subtract(totalEsperado).setScale(2, RoundingMode.HALF_UP);
            actualizarEstiloDiferencia(dif);
        } catch (NumberFormatException e) {
            lblDiferencia.setText("Monto inválido");
            lblDiferencia.getStyleClass().setAll("text-mono-lg-danger");
        }
    }

    private void actualizarEstiloDiferencia(BigDecimal dif) {
        BigDecimal normalizada = dif.setScale(2, RoundingMode.HALF_UP);
        lblDiferencia.setText("S/ " + normalizada);
        if (normalizada.compareTo(BigDecimal.ZERO) == 0) {
            lblDiferencia.getStyleClass().setAll("text-mono-lg-success");
        } else {
            lblDiferencia.getStyleClass().setAll("text-mono-lg-danger");
        }
    }

    @FXML
    public void handleCerrarCaja(ActionEvent event) {
        if (cajaActual == null) {
            AnimationUtils.mostrarToast(rootCajaStack, "No hay un turno de caja abierto para cerrar.", true);
            return;
        }

        String txt = txtEfectivoFisico.getText() != null ? txtEfectivoFisico.getText().trim() : "";
        if (txt.isEmpty()) {
            AnimationUtils.mostrarToast(rootCajaStack, "Ingrese el efectivo físico contado en gaveta.", true);
            return;
        }

        final BigDecimal fisico;
        try {
            fisico = new BigDecimal(txt).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            AnimationUtils.mostrarToast(rootCajaStack, "El monto físico ingresado no es válido.", true);
            return;
        }

        mostrarConfirmacionCierreModal(fisico);
    }

    private void mostrarConfirmacionCierreModal(BigDecimal fisico) {
        if (rootCajaStack == null) {
            ejecutarCierreEfectivo(fisico);
            return;
        }

        BigDecimal dif = fisico.subtract(totalEsperado).setScale(2, RoundingMode.HALF_UP);
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        VBox card = new VBox(16);
        card.setMaxWidth(420);
        card.setMaxHeight(260);
        card.getStyleClass().add("modal-dialog-surface");

        Label lblTitle = new Label("Confirmar cierre de turno");
        lblTitle.getStyleClass().add("text-h1");

        Label lblSub = new Label("Se emitirá el Cierre Z definitivo para " + cajaActual.getNombreCaja()
            + " con efectivo contado S/ " + fisico + " (Diferencia: S/ " + dif + ").");
        lblSub.getStyleClass().add("text-body-secondary");
        lblSub.setWrapText(true);

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootCajaStack.getChildren().remove(backdrop));

        Button btnConfirmar = new Button("Confirmar Cierre Z");
        btnConfirmar.getStyleClass().add("btn-primary");
        btnConfirmar.setOnAction(ev -> {
            rootCajaStack.getChildren().remove(backdrop);
            ejecutarCierreEfectivo(fisico);
        });

        HBox actions = new HBox(12, btnCancelar, btnConfirmar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, lblSub, actions);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootCajaStack.getChildren().add(backdrop);
    }

    private void ejecutarCierreEfectivo(BigDecimal fisico) {
        try {
            int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
            cajaService.cerrarTurnoCaja(cajaActual.getIdCaja(), idUsuario, fisico);
            AnimationUtils.mostrarToast(rootCajaStack, "Turno cerrado y Cierre Z registrado exitosamente.", false);
            cargarDatosCaja();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al cerrar turno de caja", e);
            AnimationUtils.mostrarToast(rootCajaStack, "No se pudo cerrar el turno: " + e.getMessage(), true);
        }
    }

    public BigDecimal getTotalEsperado() {
        return totalEsperado;
    }

    public ObservableList<FilaCierreZ> getItemsCierreZ() {
        return itemsCierreZ;
    }
}
