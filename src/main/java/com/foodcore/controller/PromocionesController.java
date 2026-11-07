package com.foodcore.controller;

import com.foodcore.model.Promocion;
import com.foodcore.service.PromocionService;
import com.foodcore.util.AnimationUtils;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.Optional;

public class PromocionesController {

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

    private final PromocionService promocionService = new PromocionService();
    private final ObservableList<Promocion> masterData = FXCollections.observableArrayList();
    private FilteredList<Promocion> filteredData;

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarFiltros();
        cargarDatos();
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
                    if (item.contains("2x1") || item.contains("3x2")) {
                        badge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; -fx-font-weight: 700;");
                    } else if (item.contains("%")) {
                        badge.setStyle("-fx-background-color: #DEF7EC; -fx-text-fill: #03543F; -fx-font-weight: 700;");
                    } else {
                        badge.setStyle("-fx-background-color: #E1EFFE; -fx-text-fill: #1E429F; -fx-font-weight: 700;");
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
                    btnToggle.setOnAction(e -> toggleEstado(p));
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
        masterData.clear();
        List<Promocion> lista = promocionService.listarTodas();

        if (lista.isEmpty()) {
            poblarPromocionesDemo();
        } else {
            masterData.addAll(lista);
        }

        actualizarMetricas();
        aplicarFiltros();
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

        lblTotalPromos.setText(String.valueOf(total));
        lblPromosActivas.setText(String.valueOf(activas));
        lblTipoPopular.setText("2x1 y 20% OFF");
        lblReglaFidelizacion.setText("S/ 1 = 1 Pto");
    }

    private void aplicarFiltros() {
        String texto = txtBuscar.getText() != null ? txtBuscar.getText().trim().toLowerCase() : "";
        String filtroTipo = cmbFiltroTipo.getValue();
        String filtroEstado = cmbFiltroEstado.getValue();

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

        lblConteoRegistros.setText("Mostrando " + filteredData.size() + " de " + masterData.size() + " promociones");
    }

    private void toggleEstado(Promocion p) {
        boolean nuevo = !p.isActivo();
        try {
            promocionService.cambiarEstado(p.getIdPromocion(), nuevo);
            p.setActivo(nuevo);
            tblPromociones.refresh();
            actualizarMetricas();
        } catch (Exception e) {
            p.setActivo(nuevo);
            tblPromociones.refresh();
            actualizarMetricas();
        }
    }

    @FXML
    public void handleActualizar() {
        cargarDatos();
    }

    @FXML
    public void handleNuevaPromocion() {
        Dialog<Promocion> dialog = new Dialog<>();
        dialog.setTitle("Nueva Regla de Promoción");
        dialog.setHeaderText("Configuración de Descuento Automático");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

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

        grid.add(new Label("Código:"), 0, 0);
        grid.add(txtCod, 1, 0);
        grid.add(new Label("Nombre:"), 0, 1);
        grid.add(txtNom, 1, 1);
        grid.add(new Label("Tipo:"), 0, 2);
        grid.add(cmbTipo, 1, 2);
        grid.add(new Label("Valor Descuento:"), 0, 3);
        grid.add(txtValor, 1, 3);
        grid.add(new Label("Aplica a:"), 0, 4);
        grid.add(cmbAplica, 1, 4);
        grid.add(new Label("Monto Mínimo:"), 0, 5);
        grid.add(txtMinimo, 1, 5);

        dialog.getDialogPane().setContent(grid);
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
                } catch (Exception ignored) {
                    nueva.setValorDescuento(BigDecimal.ZERO);
                }
                nueva.setAplicaA(cmbAplica.getValue());
                try {
                    nueva.setMontoMinimo(new BigDecimal(txtMinimo.getText().trim()));
                } catch (Exception ignored) {
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
            try {
                promocionService.crearPromocion(p);
            } catch (Exception ex) { LOGGER.log(Level.WARNING, "Error al crear promocion: {0}", ex.getMessage()); }
            masterData.add(p);
            actualizarMetricas();
            aplicarFiltros();
        });
    }
}
