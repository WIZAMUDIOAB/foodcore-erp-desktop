package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.Promocion;
import com.foodcore.service.PromocionService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.DialogService;
import com.foodcore.util.ToastService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PromocionesController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(PromocionesController.class.getName());

    @FXML private Label lblTotalPromos;
    @FXML private Label lblPromosActivas;
    @FXML private Label lblTipoPopular;
    @FXML private Label lblReglaFidelizacion;

    @FXML private TextField txtBuscar;
    @FXML private ComboBox<String> cmbFiltroTipo;
    @FXML private ComboBox<String> cmbFiltroEstado;
    @FXML private Label lblConteoRegistros;

    @FXML private TableView<Promocion> tblPromociones;
    @FXML private TableColumn<Promocion, String> colCodigo;
    @FXML private TableColumn<Promocion, String> colNombre;
    @FXML private TableColumn<Promocion, String> colTipo;
    @FXML private TableColumn<Promocion, String> colDescuento;
    @FXML private TableColumn<Promocion, String> colAplicaA;
    @FXML private TableColumn<Promocion, String> colHorario;
    @FXML private TableColumn<Promocion, String> colEstado;
    @FXML private TableColumn<Promocion, Void> colAcciones;

    private final PromocionService promocionService;
    private final ObservableList<Promocion> masterData = FXCollections.observableArrayList();
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicLong cargaSeq = new AtomicLong(0L);
    private final AtomicBoolean mutacionEnCurso = new AtomicBoolean(false);
    private FilteredList<Promocion> filteredData;

    public PromocionesController() {
        this(AppContainer.getDefault().resolve(PromocionService.class));
    }

    public PromocionesController(PromocionService promocionService) {
        this.promocionService = Objects.requireNonNull(promocionService, "promocionService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        configurarColumnas();
        configurarFiltros();
        cargarDatos();
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
    }

    private void configurarColumnas() {
        colCodigo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCodigo()));
        colNombre.setCellValueFactory(cell -> {
            Promocion p = cell.getValue();
            String desc = p.getDescripcion() != null ? " (" + p.getDescripcion() + ")" : "";
            return new SimpleStringProperty(p.getNombre() + desc);
        });

        colTipo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getEtiquetaTipo()));
        colTipo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge-pill-accent");
                    badge.getStyleClass().removeAll(
                            "fc-promo-badge-combo", "fc-promo-badge-pct", "fc-promo-badge-fixed");
                    if (item.contains("2x1") || item.contains("3x2")) {
                        badge.getStyleClass().add("fc-promo-badge-combo");
                    } else if (item.contains("%")) {
                        badge.getStyleClass().add("fc-promo-badge-pct");
                    } else {
                        badge.getStyleClass().add("fc-promo-badge-fixed");
                    }
                    HBox box = new HBox(badge);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        colDescuento.setCellValueFactory(cell -> {
            Promocion p = cell.getValue();
            if ("DOS_POR_UNO".equalsIgnoreCase(p.getTipoPromocion())) {
                return new SimpleStringProperty("2da unidad Gratis");
            }
            if ("TRES_POR_DOS".equalsIgnoreCase(p.getTipoPromocion())) {
                return new SimpleStringProperty("3ra unidad Gratis");
            }
            if ("MONTO_FIJO".equalsIgnoreCase(p.getTipoPromocion())) {
                return new SimpleStringProperty("S/ " + p.getValorDescuento());
            }
            return new SimpleStringProperty(p.getValorDescuento() + "% de desc.");
        });

        colAplicaA.setCellValueFactory(cell -> {
            Promocion p = cell.getValue();
            String tipo = p.getAplicaA();
            if ("PRODUCTO".equalsIgnoreCase(tipo)) {
                return new SimpleStringProperty("Plato específico" + (p.getNombreProductoAplica() != null ? " (" + p.getNombreProductoAplica() + ")" : ""));
            }
            if ("CATEGORIA".equalsIgnoreCase(tipo)) {
                return new SimpleStringProperty("Categoría completa" + (p.getNombreCategoriaAplica() != null ? " (" + p.getNombreCategoriaAplica() + ")" : ""));
            }
            return new SimpleStringProperty("Total Orden" + (p.getMontoMinimo() != null && p.getMontoMinimo().compareTo(BigDecimal.ZERO) > 0 ? " (Min S/ " + p.getMontoMinimo() + ")" : ""));
        });

        colHorario.setCellValueFactory(cell -> {
            Promocion p = cell.getValue();
            String dias = p.getDiasSemana() != null ? p.getDiasSemana() : "Todos";
            String horas = "";
            if (p.getHoraInicio() != null && p.getHoraFin() != null) {
                horas = " [" + p.getHoraInicio() + " - " + p.getHoraFin() + "]";
            }
            return new SimpleStringProperty(dias + horas);
        });

        colEstado.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isActivo() ? "ACTIVA" : "INACTIVA"));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    if ("ACTIVA".equals(item)) {
                        badge.getStyleClass().add("badge-pill-success");
                    } else {
                        badge.getStyleClass().add("badge-pill-neutral");
                    }
                    HBox box = new HBox(badge);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnToggle = new Button();
            private final HBox pane = new HBox(8.0, btnToggle);

            {
                pane.setAlignment(Pos.CENTER_LEFT);
                AnimationUtils.attachTactileFeedback(btnToggle);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Promocion p = getTableRow().getItem();
                    btnToggle.setText(p.isActivo() ? "Desactivar" : "Activar");
                    btnToggle.getStyleClass().clear();
                    btnToggle.getStyleClass().add(p.isActivo() ? "btn-secondary" : "btn-primary");
                    btnToggle.setOnAction(e -> toggleEstado(p, btnToggle));
                    setGraphic(pane);
                }
            }
        });
    }

    private void configurarFiltros() {
        cmbFiltroTipo.getItems().addAll("Todos los Tipos", "2x1", "3x2", "Porcentaje", "Monto Fijo");
        cmbFiltroTipo.setValue("Todos los Tipos");
        cmbFiltroTipo.setOnAction(e -> aplicarFiltros());

        cmbFiltroEstado.getItems().addAll("Todos", "Activas", "Inactivas");
        cmbFiltroEstado.setValue("Todos");
        cmbFiltroEstado.setOnAction(e -> aplicarFiltros());

        txtBuscar.textProperty().addListener((obs, oldVal, newVal) -> aplicarFiltros());

        filteredData = new FilteredList<>(masterData, p -> true);
        tblPromociones.setItems(filteredData);
    }

    public void cargarDatos() {
        long seq = cargaSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            promocionService::listarTodas,
            lista -> {
                if (!vistaActiva.get() || seq != cargaSeq.get()) {
                    return;
                }
                masterData.clear();
                if (lista == null || lista.isEmpty()) {
                    poblarPromocionesDemo();
                } else {
                    masterData.addAll(lista);
                }
                actualizarMetricas();
                aplicarFiltros();
            },
            ex -> {
                if (!vistaActiva.get()) {
                    return;
                }
                LOGGER.log(Level.WARNING, "Error al cargar promociones", ex);
                ToastService.mostrarPorAlertType(
                        Alert.AlertType.ERROR, "No se pudieron cargar las promociones: " + ex.getMessage());
            }
        );
    }

    private void poblarPromocionesDemo() {
        masterData.add(new Promocion(1, "PROMO-2X1-ALITAS", "2x1 Alitas BBQ Crujientes", "Compra 2 porciones y paga 1", "DOS_POR_UNO", BigDecimal.ZERO, "PRODUCTO", 2, null, BigDecimal.ZERO, "LUN,MAR,MIE,JUE,VIE,SAB,DOM", LocalTime.of(0, 0), LocalTime.of(23, 59), true));
        masterData.add(new Promocion(2, "PROMO-20-BURGERS", "20% OFF en Hamburguesas", "Descuento en toda la categoría hamburguesas", "PORCENTAJE", new BigDecimal("20.00"), "CATEGORIA", null, 1, BigDecimal.ZERO, "LUN,MAR,MIE,JUE,VIE,SAB,DOM", LocalTime.of(0, 0), LocalTime.of(23, 59), true));
        masterData.add(new Promocion(3, "PROMO-5-TOTAL", "S/ 5.00 OFF en Compras > S/ 50", "Descuento directo al superar S/ 50", "MONTO_FIJO", new BigDecimal("5.00"), "TOTAL_ORDEN", null, null, new BigDecimal("50.00"), "LUN,MAR,MIE,JUE,VIE,SAB,DOM", LocalTime.of(0, 0), LocalTime.of(23, 59), true));
        masterData.add(new Promocion(4, "PROMO-3X2-BEBIDAS", "3x2 en Bebidas Seleccionadas", "Pide 3 gaseosas y paga 2", "TRES_POR_DOS", BigDecimal.ZERO, "PRODUCTO", 3, null, BigDecimal.ZERO, "VIE,SAB,DOM", LocalTime.of(12, 0), LocalTime.of(23, 59), true));
    }

    private void actualizarMetricas() {
        long total = masterData.size();
        long activas = masterData.stream().filter(Promocion::isActivo).count();

        if (lblTotalPromos != null) {
            lblTotalPromos.setText(String.valueOf(total));
        }
        if (lblPromosActivas != null) {
            lblPromosActivas.setText(String.valueOf(activas));
        }
        if (lblTipoPopular != null) {
            lblTipoPopular.setText("2x1 y 20% OFF");
        }
        if (lblReglaFidelizacion != null) {
            lblReglaFidelizacion.setText("S/ 1 = 1 Pto");
        }
    }

    private void aplicarFiltros() {
        String texto = txtBuscar != null && txtBuscar.getText() != null
                ? txtBuscar.getText().trim().toLowerCase() : "";
        String filtroTipo = cmbFiltroTipo != null ? cmbFiltroTipo.getValue() : null;
        String filtroEstado = cmbFiltroEstado != null ? cmbFiltroEstado.getValue() : null;

        if (filteredData != null) {
            filteredData.setPredicate(p -> {
                boolean coincideTexto = texto.isEmpty()
                        || p.getCodigo().toLowerCase().contains(texto)
                        || p.getNombre().toLowerCase().contains(texto)
                        || (p.getDescripcion() != null && p.getDescripcion().toLowerCase().contains(texto));

                boolean coincideTipo = filtroTipo == null || "Todos los Tipos".equals(filtroTipo)
                        || ("2x1".equals(filtroTipo) && "DOS_POR_UNO".equalsIgnoreCase(p.getTipoPromocion()))
                        || ("3x2".equals(filtroTipo) && "TRES_POR_DOS".equalsIgnoreCase(p.getTipoPromocion()))
                        || ("Porcentaje".equals(filtroTipo) && "PORCENTAJE".equalsIgnoreCase(p.getTipoPromocion()))
                        || ("Monto Fijo".equals(filtroTipo) && "MONTO_FIJO".equalsIgnoreCase(p.getTipoPromocion()));

                boolean coincideEstado = filtroEstado == null || "Todos".equals(filtroEstado)
                        || ("Activas".equals(filtroEstado) && p.isActivo())
                        || ("Inactivas".equals(filtroEstado) && !p.isActivo());

                return coincideTexto && coincideTipo && coincideEstado;
            });
            if (lblConteoRegistros != null) {
                lblConteoRegistros.setText("Mostrando " + filteredData.size() + " de " + masterData.size() + " promociones");
            }
        }
    }

    void toggleEstadoPromocion(Promocion p, Button trigger) {
        toggleEstado(p, trigger);
    }

    private void toggleEstado(Promocion p, Button trigger) {
        if (p == null) {
            return;
        }
        if (!mutacionEnCurso.compareAndSet(false, true)) {
            return;
        }
        int idPromocion = p.getIdPromocion();
        boolean nuevo = !p.isActivo();
        if (trigger != null) {
            trigger.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> promocionService.cambiarEstado(idPromocion, nuevo),
            () -> {
                mutacionEnCurso.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                p.setActivo(nuevo);
                if (tblPromociones != null) {
                    tblPromociones.refresh();
                }
                actualizarMetricas();
                aplicarFiltros();
            },
            ex -> {
                mutacionEnCurso.set(false);
                if (trigger != null) {
                    trigger.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error al cambiar estado de promoción", ex);
                ToastService.mostrarPorAlertType(
                        Alert.AlertType.ERROR, "Error al actualizar promoción: " + ex.getMessage());
            }
        );
    }

    @FXML
    public void handleActualizar() {
        cargarDatos();
    }

    @FXML
    public void handleNuevaPromocion() {
        Dialog<Promocion> dialog = DialogService.crearDialogo(
                "Nueva Regla de Promoción",
                "Configuración de Descuento Automático");

        TextField txtCod = new TextField("PROMO-" + (System.currentTimeMillis() % 10000));
        TextField txtNom = new TextField();
        txtNom.setPromptText("Ej. 2x1 en Pizzas Artesanales");
        ComboBox<String> cmbTipo = new ComboBox<>();
        cmbTipo.getItems().addAll("DOS_POR_UNO (2x1)", "TRES_POR_DOS (3x2)", "PORCENTAJE", "MONTO_FIJO");
        cmbTipo.setValue("DOS_POR_UNO (2x1)");
        TextField txtValor = new TextField("0.00");
        ComboBox<String> cmbAplica = new ComboBox<>();
        cmbAplica.getItems().addAll("PRODUCTO", "CATEGORIA", "TOTAL_ORDEN");
        cmbAplica.setValue("PRODUCTO");
        TextField txtMinimo = new TextField("0.00");

        VBox form = new VBox(16.0);
        form.setPadding(new Insets(16.0, 24.0, 24.0, 24.0));
        form.setPrefWidth(460.0);
        form.getChildren().addAll(
                DialogService.construirFilaDobleModal(
                        DialogService.construirCampoModal("Código:", txtCod),
                        DialogService.construirCampoModal("Tipo:", cmbTipo)),
                DialogService.construirCampoModal("Nombre:", txtNom),
                DialogService.construirFilaDobleModal(
                        DialogService.construirCampoModal("Valor Descuento:", txtValor),
                        DialogService.construirCampoModal("Monto Mínimo:", txtMinimo)),
                DialogService.construirCampoModal("Aplica a:", cmbAplica)
        );

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !txtNom.getText().isBlank()) {
                String rawTipo = cmbTipo.getValue().split(" ")[0];
                Promocion nueva = new Promocion();
                nueva.setCodigo(txtCod.getText().trim());
                nueva.setNombre(txtNom.getText().trim());
                nueva.setTipoPromocion(rawTipo);
                try {
                    nueva.setValorDescuento(new BigDecimal(txtValor.getText().trim()));
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.FINE, "Valor de descuento no numérico, usando 0.00", ex);
                    nueva.setValorDescuento(BigDecimal.ZERO);
                }
                nueva.setAplicaA(cmbAplica.getValue());
                try {
                    nueva.setMontoMinimo(new BigDecimal(txtMinimo.getText().trim()));
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.FINE, "Monto mínimo no numérico, usando 0.00", ex);
                    nueva.setMontoMinimo(BigDecimal.ZERO);
                }
                nueva.setDiasSemana("LUN,MAR,MIE,JUE,VIE,SAB,DOM");
                nueva.setActivo(true);
                return nueva;
            }
            return null;
        });

        Optional<Promocion> res = dialog.showAndWait();
        res.ifPresent(p -> {
            if (!mutacionEnCurso.compareAndSet(false, true)) {
                return;
            }
            UiAsyncExecutor.ejecutarAsync(
                () -> promocionService.crearPromocion(p),
                idCreado -> {
                    mutacionEnCurso.set(false);
                    if (!vistaActiva.get()) {
                        return;
                    }
                    if (idCreado != null && idCreado > 0) {
                        p.setIdPromocion(idCreado);
                    }
                    masterData.add(p);
                    actualizarMetricas();
                    aplicarFiltros();
                },
                ex -> {
                    mutacionEnCurso.set(false);
                    LOGGER.log(Level.WARNING, "Error al crear promocion: {0}", ex.getMessage());
                    ToastService.mostrarPorAlertType(
                            Alert.AlertType.ERROR, "No se pudo crear la promoción: " + ex.getMessage());
                }
            );
        });
    }

    public TableView<Promocion> getTblPromociones() {
        return tblPromociones;
    }

    public Label getLblTotalPromos() {
        return lblTotalPromos;
    }

    public Label getLblPromosActivas() {
        return lblPromosActivas;
    }

    public Label getLblConteoRegistros() {
        return lblConteoRegistros;
    }
}
