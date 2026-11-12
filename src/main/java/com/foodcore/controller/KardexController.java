package com.foodcore.controller;

import com.foodcore.service.ExportacionContableService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.ReporteExportUIHelper;
import com.foodcore.util.UnidadMedidaFormatter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Kardex e Inventario de Insumos según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§10)
 * y SPEC-FOODCORE-v1.2 (§1.1): aplica {@link UnidadMedidaFormatter} para mostrar
 * {@code 120 Unds} y {@code 38.2 kg} sin sufijos {@code .0000}.
 */
public class KardexController {

    private static final Logger LOGGER = Logger.getLogger(KardexController.class.getName());

    public record FilaKardex(String fecha, String tipo, String insumo, String cantidad, String saldo, String usuario) {
    }

    @FXML private ComboBox<String> cmbInsumo;
    @FXML private DatePicker dpFechaDesde;
    @FXML private DatePicker dpFechaHasta;
    @FXML private Button btnFiltrar;
    @FXML private Button btnExportarPdf;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnExportarCsv;
    @FXML private Label lblResumenKardex;

    @FXML private TableView<FilaKardex> tblKardex;
    @FXML private TableColumn<FilaKardex, String> colFecha;
    @FXML private TableColumn<FilaKardex, String> colTipo;
    @FXML private TableColumn<FilaKardex, String> colInsumo;
    @FXML private TableColumn<FilaKardex, String> colCantidad;
    @FXML private TableColumn<FilaKardex, String> colSaldo;
    @FXML private TableColumn<FilaKardex, String> colUsuario;

    private final ObservableList<FilaKardex> todosLosMovimientos = FXCollections.observableArrayList();
    private final ObservableList<FilaKardex> movimientosFiltrados = FXCollections.observableArrayList();
    private final ExportacionContableService exportacionService = new ExportacionContableService();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnFiltrar);
        AnimationUtils.attachTactileFeedback(btnExportarPdf);
        AnimationUtils.attachTactileFeedback(btnExportarExcel);
        AnimationUtils.attachTactileFeedback(btnExportarCsv);
        dpFechaDesde.setValue(LocalDate.now().minusDays(7));
        dpFechaHasta.setValue(LocalDate.now());

        configurarColumnas();
        cargarMovimientosDesdeBD();
    }

    private void configurarColumnas() {
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha()));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tipo()));
        colInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().insumo()));
        colCantidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().cantidad()));
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().saldo()));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().usuario()));

        colFecha.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-caption");
                }
            }
        });

        colTipo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    String upper = item.toUpperCase();
                    if (upper.contains("ENTRADA") || upper.contains("COMPRA")) {
                        pill.getStyleClass().add("badge-pill-success");
                    } else if (upper.contains("SALIDA") || upper.contains("VENTA")) {
                        pill.getStyleClass().add("badge-pill-accent");
                    } else if (upper.contains("MERMA")) {
                        pill.getStyleClass().add("badge-pill-danger");
                    } else {
                        pill.getStyleClass().add("badge-pill-warning");
                    }
                    setGraphic(pill);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        colInsumo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-body-strong");
                }
            }
        });

        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        });

        colSaldo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        });

        colUsuario.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().setAll("table-cell", "text-caption");
                }
            }
        });

        tblKardex.setItems(movimientosFiltrados);
    }

    public void cargarMovimientosDesdeBD() {
        new Thread(() -> {
            List<String> listaInsumos = new ArrayList<>();
            listaInsumos.add("Todos los insumos");
            List<FilaKardex> filas = new ArrayList<>();

            String sql = "SELECT k.created_at, k.tipo_movimiento, k.referencia_tipo, i.nombre AS insumo_nombre, i.unidad_medida, "
                + "k.cantidad, k.saldo_posterior, COALESCE(u.username, 'admin') AS usuario "
                + "FROM kardex_movimiento k "
                + "JOIN insumo i ON k.id_insumo = i.id_insumo "
                + "LEFT JOIN usuario u ON k.id_usuario = u.id_usuario "
                + "ORDER BY k.created_at DESC, k.id_kardex DESC LIMIT 200";

            try (Connection conn = ConexionBD.getConnection()) {
                try (PreparedStatement psIns = conn.prepareStatement("SELECT nombre FROM insumo ORDER BY nombre");
                     ResultSet rsI = psIns.executeQuery()) {
                    while (rsI.next()) {
                        listaInsumos.add(rsI.getString("nombre"));
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fecha = String.valueOf(rs.getTimestamp("created_at")).substring(0, 16);
                        String tipoRaw = rs.getString("tipo_movimiento");
                        String refTipo = rs.getString("referencia_tipo");
                        String tipo = resolverEtiquetaTipo(tipoRaw, refTipo);
                        String ins = rs.getString("insumo_nombre");
                        String um = rs.getString("unidad_medida");
                        BigDecimal cantBd = rs.getBigDecimal("cantidad");
                        BigDecimal saldoBd = rs.getBigDecimal("saldo_posterior");
                        String cant = UnidadMedidaFormatter.formatear(cantBd, um, true);
                        String saldo = UnidadMedidaFormatter.formatear(saldoBd, um, false);
                        String usr = rs.getString("usuario");
                        filas.add(new FilaKardex(fecha, tipo, ins, cant, saldo, usr));
                    }
                }
            } catch (java.sql.SQLException | RuntimeException e) {
                LOGGER.log(Level.FINE, "Cargando datos semilla de Kardex", e);
            }

            if (filas.isEmpty()) {
                filas.add(new FilaKardex("2026-11-04 18:10", "ENTRADA_COMPRA", "Carne de Res Angus 150g",
                    UnidadMedidaFormatter.formatear(new BigDecimal("50.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("120.0000"), "UNIDAD", false),
                    "admin · Almacén"));
                filas.add(new FilaKardex("2026-11-04 18:42", "SALIDA_VENTA", "Carne de Res Angus 150g",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-4.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("116.0000"), "UNIDAD", false),
                    "cajero · POS-01"));
                filas.add(new FilaKardex("2026-11-04 19:05", "SALIDA_VENTA", "Papa Amarilla Bastón",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-1.8000"), "KG", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("38.2000"), "KG", false),
                    "cajero · POS-01"));
                filas.add(new FilaKardex("2026-11-04 19:18", "AJUSTE_INV", "Queso Cheddar Laminado",
                    UnidadMedidaFormatter.formatear(new BigDecimal("10.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("85.0000"), "UNIDAD", false),
                    "admin · Auditoría"));
                filas.add(new FilaKardex("2026-11-04 19:35", "MERMA", "Pan Brioche Artesanal",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-2.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("64.0000"), "UNIDAD", false),
                    "cocinero · KDS"));
                filas.add(new FilaKardex("2026-11-04 19:50", "SALIDA_VENTA", "Alitas de Pollo Frescas",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-6.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("94.0000"), "UNIDAD", false),
                    "cajero · POS-01"));
                if (listaInsumos.size() == 1) {
                    listaInsumos.add("Carne de Res Angus 150g");
                    listaInsumos.add("Papa Amarilla Bastón");
                    listaInsumos.add("Queso Cheddar Laminado");
                    listaInsumos.add("Pan Brioche Artesanal");
                    listaInsumos.add("Alitas de Pollo Frescas");
                }
            }

            Platform.runLater(() -> {
                cmbInsumo.setItems(FXCollections.observableArrayList(listaInsumos));
                cmbInsumo.getSelectionModel().selectFirst();
                todosLosMovimientos.setAll(filas);
                aplicarFiltroLocal();
            });
        }).start();
    }

    private String resolverEtiquetaTipo(String tipoRaw, String refTipo) {
        if (tipoRaw == null) {
            return "MOVIMIENTO";
        }
        if ("SALIDA".equalsIgnoreCase(tipoRaw) && "VENTA".equalsIgnoreCase(refTipo)) {
            return "SALIDA_VENTA";
        }
        if ("ENTRADA".equalsIgnoreCase(tipoRaw) && "COMPRA".equalsIgnoreCase(refTipo)) {
            return "ENTRADA_COMPRA";
        }
        return tipoRaw;
    }

    @FXML
    public void handleFiltrar(ActionEvent event) {
        aplicarFiltroLocal();
    }

    public void aplicarFiltroLocal() {
        String insumoSel = cmbInsumo != null ? cmbInsumo.getValue() : null;
        boolean filtrarPorInsumo = insumoSel != null && !insumoSel.equals("Todos los insumos");

        movimientosFiltrados.clear();
        for (FilaKardex f : todosLosMovimientos) {
            if (!filtrarPorInsumo || f.insumo().equalsIgnoreCase(insumoSel)) {
                movimientosFiltrados.add(f);
            }
        }
        if (lblResumenKardex != null) {
            lblResumenKardex.setText("Mostrando " + movimientosFiltrados.size() + " movimientos");
        }
    }

    @FXML
    public void handleExportarPdf() {
        exportarKardex(btnExportarPdf, "Guardar Reporte de Kardex en PDF", ".pdf", FormatoExportacion.PDF);
    }

    @FXML
    public void handleExportarExcel() {
        exportarKardex(btnExportarExcel, "Guardar Reporte de Kardex en Excel", ".xlsx", FormatoExportacion.EXCEL);
    }

    @FXML
    public void handleExportarCsv() {
        exportarKardex(btnExportarCsv, "Guardar Reporte de Kardex en CSV", ".csv", FormatoExportacion.CSV);
    }

    private void exportarKardex(Button origen, String tituloDialogo, String ext, FormatoExportacion formato) {
        List<String> headers = List.of("Fecha", "Tipo", "Insumo", "Cantidad", "Saldo", "Usuario");
        List<List<String>> filas = new ArrayList<>();
        for (FilaKardex f : movimientosFiltrados) {
            filas.add(List.of(f.fecha(), f.tipo(), f.insumo(), f.cantidad(), f.saldo(), f.usuario()));
        }
        String sugerido = "KARDEX_INSUMOS_" + LocalDate.now() + ext;
        ReporteExportUIHelper.exportarTablaConSelector(
                origen != null ? origen : lblResumenKardex,
                lblResumenKardex,
                tituloDialogo,
                sugerido,
                "Kardex Valorizado de Insumos",
                "KARDEX",
                formato,
                headers,
                filas,
                exportacionService);
    }

    public ObservableList<FilaKardex> getMovimientosFiltrados() {
        return movimientosFiltrados;
    }
}
