package com.foodcore.controller;

import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    @FXML private Label lblResumenStockCritico;
    @FXML private TableView<InsumoCriticoTurno> tblStockCritico;
    @FXML private TableColumn<InsumoCriticoTurno, String> colCritInsumo;
    @FXML private TableColumn<InsumoCriticoTurno, String> colCritUnidad;
    @FXML private TableColumn<InsumoCriticoTurno, String> colCritActual;
    @FXML private TableColumn<InsumoCriticoTurno, String> colCritMinimo;
    @FXML private TableColumn<InsumoCriticoTurno, String> colCritEstado;

    private final CajaService cajaService = new CajaService();
    private final ObservableList<FilaCierreZ> itemsCierreZ = FXCollections.observableArrayList();
    private final ObservableList<InsumoCriticoTurno> itemsStockCritico = FXCollections.observableArrayList();
    private volatile List<CategoriaGasto> categoriasGastoCache = List.of();
    private Caja cajaActual;
    private BigDecimal totalEsperado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @FXML
    public void initialize() {
        configurarTablaCierreZ();
        configurarTablaStockCritico();
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

    private void configurarTablaStockCritico() {
        if (tblStockCritico == null) {
            return;
        }
        colCritInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        colCritUnidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().unidadMedida()));
        colCritActual.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().stockActual().setScale(DesgloseFiscal.ESCALA_STOCK, RoundingMode.HALF_UP).toPlainString()));
        colCritMinimo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().stockMinimo().setScale(DesgloseFiscal.ESCALA_STOCK, RoundingMode.HALF_UP).toPlainString()));
        colCritEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nivel()));
        colCritEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if ("AGOTADO".equalsIgnoreCase(item)) {
                    setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold;");
                }
            }
        });
        tblStockCritico.setItems(itemsStockCritico);
    }

    private record EstadoCajaCarga(
            boolean abierta,
            Caja activa,
            BigDecimal apertura,
            BigDecimal ventas,
            BigDecimal gastos,
            BigDecimal esperado,
            ObservableList<FilaCierreZ> desgloseZ,
            List<InsumoCriticoTurno> criticos) {
    }

    public void cargarDatosCaja() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try {
                    categoriasGastoCache = List.copyOf(cajaService.listarCategoriasGastoActivas());
                } catch (Exception exCat) {
                    LOGGER.log(Level.FINE, "Aviso cargando categorías de gasto", exCat);
                }
                List<InsumoCriticoTurno> criticos = List.of();
                try {
                    criticos = cajaService.obtenerInsumosCriticosTurno();
                } catch (Exception exInv) {
                    LOGGER.log(Level.FINE, "Aviso cargando inventario crítico del relevo", exInv);
                }
                Optional<Caja> opt = cajaService.obtenerCajaActiva();
                if (opt.isPresent()) {
                    Caja activa = opt.get();
                    BigDecimal ventas = cajaService.calcularVentasTurno(activa.getIdCaja())
                        .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal gastos = cajaService.calcularGastosTurno(activa.getIdCaja())
                        .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal apertura = activa.getMontoApertura().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal esperado = apertura.add(ventas).subtract(gastos).setScale(2, RoundingMode.HALF_UP);
                    List<MovimientoCaja> gastosDetallados = cajaService.listarGastosTurno(activa.getIdCaja());
                    ObservableList<FilaCierreZ> desgloseZ = consultarDesgloseCierreZ(activa.getIdCaja(), gastos, gastosDetallados);
                    return new EstadoCajaCarga(true, activa, apertura, ventas, gastos, esperado, desgloseZ, criticos);
                }
                return new EstadoCajaCarga(false, null, null, null, null, null, FXCollections.observableArrayList(), criticos);
            },
            estado -> {
                if (estado.abierta()) {
                    aplicarEstadoCajaAbierta(
                        estado.activa(), estado.apertura(), estado.ventas(),
                        estado.gastos(), estado.esperado(), estado.desgloseZ(), estado.criticos()
                    );
                } else {
                    aplicarEstadoCajaCerrada();
                }
            },
            e -> {
                LOGGER.log(Level.WARNING, "Error al consultar estado de caja", e);
                AnimationUtils.mostrarToast(
                    rootCajaStack, "Error al consultar caja: " + (e != null ? e.getMessage() : "desconocido"), true
                );
            }
        );
    }

    private ObservableList<FilaCierreZ> consultarDesgloseCierreZ(
            int idCaja,
            BigDecimal gastosEfectivo,
            List<MovimientoCaja> gastosDetallados) {
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
            if (gastosDetallados != null && !gastosDetallados.isEmpty()) {
                Map<String, BigDecimal> porCat = new LinkedHashMap<>();
                Map<String, Integer> opsPorCat = new LinkedHashMap<>();
                for (MovimientoCaja mov : gastosDetallados) {
                    String catNom = mov.getNombreCategoriaGasto() != null ? mov.getNombreCategoriaGasto() : "Sin categoría";
                    porCat.merge(catNom, mov.getMonto().abs(), BigDecimal::add);
                    opsPorCat.merge(catNom, 1, Integer::sum);
                }
                for (Map.Entry<String, BigDecimal> e : porCat.entrySet()) {
                    filas.add(new FilaCierreZ(
                        "EGRESO · " + e.getKey(),
                        String.valueOf(opsPorCat.getOrDefault(e.getKey(), 1)),
                        "-S/ " + e.getValue().setScale(2, RoundingMode.HALF_UP)
                    ));
                }
            } else {
                filas.add(new FilaCierreZ("EGRESOS CAJA CHICA", "-", "-S/ " + gastosEfectivo));
            }
        }
        filas.add(new FilaCierreZ("TOTAL CONSOLIDADO Z", String.valueOf(totalOps), "S/ " + granTotal));
        return filas;
    }

    private void aplicarEstadoCajaAbierta(Caja activa,
                                          BigDecimal apertura,
                                          BigDecimal ventas,
                                          BigDecimal gastos,
                                          BigDecimal esperado,
                                          ObservableList<FilaCierreZ> desgloseZ,
                                          List<InsumoCriticoTurno> criticos) {
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
        itemsStockCritico.setAll(criticos != null ? criticos : List.of());
        if (lblResumenStockCritico != null) {
            long agotados = itemsStockCritico.stream().filter(InsumoCriticoTurno::isAgotado).count();
            long minimos = itemsStockCritico.size() - agotados;
            if (itemsStockCritico.isEmpty()) {
                lblResumenStockCritico.setText("STOCK COMPLETO");
                lblResumenStockCritico.getStyleClass().setAll("badge-pill-success");
            } else {
                lblResumenStockCritico.setText(agotados + " AGOTADO(S) · " + minimos + " MÍNIMO");
                lblResumenStockCritico.getStyleClass().setAll("badge-pill-danger");
            }
        }

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
     * Abre modal para registrar un gasto operativo categorizado / salida de caja chica (§3.4, v4.9.0).
     */
    @FXML
    public void handleAbrirModalGasto(ActionEvent event) {
        if (cajaActual == null || rootCajaStack == null) {
            return;
        }
        List<CategoriaGasto> categorias = categoriasGastoCache;
        if (categorias.isEmpty()) {
            try {
                categorias = cajaService.listarCategoriasGastoActivas();
                categoriasGastoCache = List.copyOf(categorias);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "No se pudieron cargar categorías de gasto", ex);
            }
        }

        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        VBox card = new VBox(12);
        card.setMaxWidth(440);
        card.setMaxHeight(410);
        card.getStyleClass().add("modal-dialog-surface");

        Label lblTitle = new Label("Registrar Gasto Operativo");
        lblTitle.getStyleClass().add("text-h1");

        Label lblSub = new Label("Salida de efectivo categorizada para Cierre Z y Estado de Resultados (P&L).");
        lblSub.getStyleClass().add("text-body-secondary");
        lblSub.setWrapText(true);

        Label lblCat = new Label("Categoría de gasto");
        lblCat.getStyleClass().add("text-body-strong");

        ComboBox<CategoriaGasto> cbCategoriaGasto = new ComboBox<>(FXCollections.observableArrayList(categorias));
        cbCategoriaGasto.setId("cbCategoriaGasto");
        cbCategoriaGasto.setPromptText("Seleccione categoría (ej. Pago de Luz)");
        cbCategoriaGasto.setMaxWidth(Double.MAX_VALUE);
        cbCategoriaGasto.getStyleClass().add("form-combo-box");
        cbCategoriaGasto.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CategoriaGasto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getNombre());
            }
        });
        cbCategoriaGasto.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(CategoriaGasto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Seleccione categoría (ej. Pago de Luz)" : item.getNombre());
            }
        });

        TextField txtMontoGasto = new TextField();
        txtMontoGasto.setId("txtMontoGasto");
        txtMontoGasto.setPromptText("Monto en S/ (ej. 180.00)");
        txtMontoGasto.getStyleClass().add("input-field");

        TextField txtConceptoGasto = new TextField();
        txtConceptoGasto.setId("txtConceptoGasto");
        txtConceptoGasto.setPromptText("Motivo / Nro. recibo (ej. Recibo Luz Enel Noviembre)");
        txtConceptoGasto.getStyleClass().add("input-field");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootCajaStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Registrar Egreso");
        btnGuardar.setId("btnConfirmarRegistroGasto");
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setOnAction(ev -> {
            try {
                CategoriaGasto seleccionada = cbCategoriaGasto.getValue();
                if (seleccionada == null) {
                    AnimationUtils.mostrarToast(rootCajaStack, "Seleccione una categoría de gasto operativo.", true);
                    return;
                }
                BigDecimal monto = new BigDecimal(txtMontoGasto.getText().trim()).setScale(2, RoundingMode.HALF_UP);
                String concepto = txtConceptoGasto.getText() != null ? txtConceptoGasto.getText().trim() : "";
                int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
                cajaService.registrarGastoOperativo(
                    cajaActual.getIdCaja(), idUsuario, monto, concepto, seleccionada.getIdCategoriaGasto()
                );
                rootCajaStack.getChildren().remove(backdrop);
                AnimationUtils.mostrarToast(
                    rootCajaStack,
                    "Gasto [" + seleccionada.getNombre() + "] de S/ " + monto + " registrado.",
                    false
                );
                cargarDatosCaja();
            } catch (NumberFormatException nfe) {
                AnimationUtils.mostrarToast(rootCajaStack, "Ingrese un monto numérico válido para el egreso.", true);
            } catch (Exception ex) {
                AnimationUtils.mostrarToast(rootCajaStack, ex.getMessage(), true);
            }
        });

        HBox actions = new HBox(12, btnCancelar, btnGuardar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, lblSub, lblCat, cbCategoriaGasto, txtMontoGasto, txtConceptoGasto, actions);
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
            ejecutarCierreEfectivo(fisico, null);
            return;
        }

        BigDecimal dif = fisico.subtract(totalEsperado).setScale(2, RoundingMode.HALF_UP);
        long agotados = itemsStockCritico.stream().filter(InsumoCriticoTurno::isAgotado).count();
        long minimos = itemsStockCritico.size() - agotados;

        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");

        VBox card = new VBox(14);
        card.setMaxWidth(480);
        card.setMaxHeight(340);
        card.getStyleClass().add("modal-dialog-surface");

        Label lblTitle = new Label("Confirmar Cierre de Turno y Relevo");
        lblTitle.getStyleClass().add("text-h1");

        Label lblSub = new Label("Se emitirá el Cierre Z para " + cajaActual.getNombreCaja()
            + " con efectivo S/ " + fisico + " (Diferencia: S/ " + dif + ").");
        lblSub.getStyleClass().add("text-body-secondary");
        lblSub.setWrapText(true);

        Label lblAlertaInv = new Label(itemsStockCritico.isEmpty()
            ? "Inventario OK: sin insumos agotados ni en stock mínimo. Se generará Acta de Entrega PDF."
            : String.format("Alerta de relevo: %d insumo(s) agotado(s) y %d en mínimo se incluirán en el Acta PDF.",
                    agotados, minimos));
        lblAlertaInv.getStyleClass().add(itemsStockCritico.isEmpty() ? "badge-pill-success" : "badge-pill-danger");
        lblAlertaInv.setWrapText(true);

        TextField txtRelevo = new TextField();
        txtRelevo.setPromptText("Nombre del cajero entrante (relevo opcional)");
        txtRelevo.getStyleClass().add("input-field");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootCajaStack.getChildren().remove(backdrop));

        Button btnConfirmar = new Button("Confirmar Cierre Z y Generar Acta PDF");
        btnConfirmar.getStyleClass().add("btn-primary");
        btnConfirmar.setOnAction(ev -> {
            rootCajaStack.getChildren().remove(backdrop);
            ejecutarCierreEfectivo(fisico, txtRelevo.getText());
        });

        HBox actions = new HBox(12, btnCancelar, btnConfirmar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, lblSub, lblAlertaInv, txtRelevo, actions);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootCajaStack.getChildren().add(backdrop);
    }

    private void ejecutarCierreEfectivo(BigDecimal fisico, String responsableEntrante) {
        Usuario usr = AuthService.getUsuarioSesionActual();
        int idUsuario = usr != null ? usr.getIdUsuario() : 1;
        String respSalida = usr != null && usr.getNombreEmpleado() != null && !usr.getNombreEmpleado().isBlank()
                ? usr.getNombreEmpleado()
                : (usr != null ? usr.getUsername() : "Cajero Saliente");
        int idCajaCerrar = cajaActual.getIdCaja();

        UiAsyncExecutor.ejecutarAsync(
            () -> cajaService.cerrarTurnoConActaPdf(
                    idCajaCerrar, idUsuario, fisico, respSalida, responsableEntrante, null),
            res -> {
                String nombrePdf = res.archivoActaPdf() != null ? res.archivoActaPdf().getName() : "Acta_Turno.pdf";
                AnimationUtils.mostrarToast(rootCajaStack,
                        "Turno cerrado · Acta PDF generada: " + nombrePdf, false);
                cargarDatosCaja();
            },
            e -> {
                LOGGER.log(Level.WARNING, "Error al cerrar turno de caja", e);
                AnimationUtils.mostrarToast(rootCajaStack,
                        "No se pudo cerrar el turno: " + (e != null ? e.getMessage() : "desconocido"), true);
            }
        );
    }

    /** Ejecuta cierre síncrono con ruta PDF explícita para pruebas integrales del Acta de Entrega. */
    public CajaService.ResultadoCierreConActa ejecutarCierreConActaArchivoSync(
            BigDecimal fisico, String respEntrante, File destinoPdf) throws Exception {
        Usuario usr = AuthService.getUsuarioSesionActual();
        int idUsuario = usr != null ? usr.getIdUsuario() : 1;
        String respSalida = usr != null ? usr.getUsername() : "Cajero Saliente";
        return cajaService.cerrarTurnoConActaPdf(
                cajaActual.getIdCaja(), idUsuario, fisico, respSalida, respEntrante, destinoPdf);
    }

    /** Pobla el arqueo y el panel de stock crítico directamente para pruebas de interfaz sin BD. */
    public void aplicarVistaArqueoPrueba(Caja activa,
                                         BigDecimal apertura,
                                         BigDecimal ventas,
                                         BigDecimal gastos,
                                         BigDecimal esperado,
                                         ObservableList<FilaCierreZ> desgloseZ,
                                         List<InsumoCriticoTurno> criticos) {
        aplicarEstadoCajaAbierta(activa, apertura, ventas, gastos, esperado, desgloseZ, criticos);
    }

    public BigDecimal getTotalEsperado() {
        return totalEsperado;
    }

    public ObservableList<FilaCierreZ> getItemsCierreZ() {
        return itemsCierreZ;
    }

    public ObservableList<InsumoCriticoTurno> getItemsStockCritico() {
        return itemsStockCritico;
    }
}
