package com.foodcore.controller;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.RecetaDAO;
import com.foodcore.dao.RecetaDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.model.Receta;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import com.foodcore.util.UnidadMedidaFormatter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Recetas y Escandallo según SPEC-FOODCORE-v1.2 (§1.1, §2.3):
 * Selecciona un platillo a la izquierda -> visualiza y edita sus insumos requeridos a la derecha
 * con formateo {@link UnidadMedidaFormatter}.
 */
public class RecetasController {

    private static final Logger LOGGER = Logger.getLogger(RecetasController.class.getName());

    @FXML private StackPane rootRecetasStack;
    @FXML private Label lblPlatilloActivoHeader;
    @FXML private Label lblResumenEscandallo;

    @FXML private TableView<Producto> tblPlatillos;
    @FXML private TableColumn<Producto, String> colProdCodigo;
    @FXML private TableColumn<Producto, String> colProdNombre;
    @FXML private TableColumn<Producto, String> colProdPrecio;

    @FXML private ComboBox<Insumo> cmbInsumoReceta;
    @FXML private TextField txtCantidadReceta;
    @FXML private Button btnAgregarInsumo;
    @FXML private Button btnQuitarInsumo;

    @FXML private TableView<Receta> tblComponentesReceta;
    @FXML private TableColumn<Receta, String> colRecInsumo;
    @FXML private TableColumn<Receta, String> colRecCantidad;
    @FXML private TableColumn<Receta, String> colRecUnidad;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final RecetaDAO recetaDAO = new RecetaDAOImpl();

    private final ObservableList<Producto> listaPlatillos = FXCollections.observableArrayList();
    private final ObservableList<Insumo> listaInsumos = FXCollections.observableArrayList();
    private final ObservableList<Receta> listaComponentes = FXCollections.observableArrayList();
    private final Map<Long, BigDecimal> costosPorInsumo = new HashMap<>();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnAgregarInsumo);
        AnimationUtils.attachTactileFeedback(btnQuitarInsumo);

        configurarTablas();
        tblPlatillos.getSelectionModel().selectedItemProperty().addListener((obs, oldP, newP) -> {
            if (newP != null) {
                cargarRecetaDePlatillo(newP);
            }
        });

        cargarDatosIniciales();
    }

    private void configurarTablas() {
        colProdCodigo.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCodigo() != null ? c.getValue().getCodigo() : ("PLT-" + c.getValue().getIdProducto())
        ));
        colProdNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colProdPrecio.setCellValueFactory(c -> new SimpleStringProperty("S/ " + c.getValue().getPrecioVenta()));

        colRecInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreInsumo()));
        colRecCantidad.setCellValueFactory(c -> new SimpleStringProperty(
            UnidadMedidaFormatter.formatear(c.getValue().getCantidadNecesaria(), c.getValue().getUnidadMedidaInsumo())
        ));
        colRecUnidad.setCellValueFactory(c -> new SimpleStringProperty(
            UnidadMedidaFormatter.formatearSufijo(c.getValue().getUnidadMedidaInsumo())
        ));

        colRecCantidad.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        });

        tblPlatillos.setItems(listaPlatillos);
        cmbInsumoReceta.setItems(listaInsumos);
        tblComponentesReceta.setItems(listaComponentes);
    }

    private record DatosRecetasCarga(List<Producto> prods, List<Insumo> insumos) {
    }

    public void cargarDatosIniciales() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                List<Producto> prods = new ArrayList<>();
                List<Insumo> ins = new ArrayList<>();
                try (Connection conn = ConexionBD.getConnection()) {
                    prods = productoDAO.listarTodos(conn);
                    ins = insumoDAO.listarTodos(conn);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error cargando datos de recetas", e);
                }
                return new DatosRecetasCarga(prods, ins);
            },
            datos -> {
                costosPorInsumo.clear();
                for (Insumo i : datos.insumos()) {
                    costosPorInsumo.put(i.getIdInsumo(), i.getCostoUnitario() != null ? i.getCostoUnitario() : BigDecimal.ZERO);
                }
                listaInsumos.setAll(datos.insumos());
                if (!listaInsumos.isEmpty()) {
                    cmbInsumoReceta.getSelectionModel().selectFirst();
                }
                listaPlatillos.setAll(datos.prods());
                if (!listaPlatillos.isEmpty()) {
                    tblPlatillos.getSelectionModel().selectFirst();
                }
            },
            e -> LOGGER.log(Level.FINE, "Error cargando datos de recetas", e)
        );
    }

    public void cargarRecetaDePlatillo(Producto p) {
        if (p == null) {
            return;
        }
        if (tblPlatillos != null) {
            for (Producto item : listaPlatillos) {
                if (item.getIdProducto() == p.getIdProducto()) {
                    tblPlatillos.getSelectionModel().select(item);
                    break;
                }
            }
        }
        if (lblPlatilloActivoHeader != null) {
            lblPlatilloActivoHeader.setText("Platillo: " + p.getNombre());
        }
        int idProducto = p.getIdProducto();
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    return recetaDAO.listarPorProducto(conn, idProducto);
                }
            },
            recs -> {
                listaComponentes.setAll(recs);
                actualizarEscandallo(p, recs);
            },
            e -> LOGGER.log(Level.FINE, "Error al consultar receta del producto", e)
        );
    }

    private void actualizarEscandallo(Producto p, List<Receta> recs) {
        if (lblResumenEscandallo == null || p == null) {
            return;
        }
        BigDecimal costoTotal = BigDecimal.ZERO;
        for (Receta r : recs) {
            BigDecimal costoUnit = costosPorInsumo.getOrDefault(r.getIdInsumo(), BigDecimal.ZERO);
            String um = UnidadMedidaFormatter.normalizarCodigo(r.getUnidadMedidaInsumo());
            BigDecimal factor = ("GR".equals(um) || "ML".equals(um))
                ? r.getCantidadNecesaria().divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP)
                : r.getCantidadNecesaria();
            // Si el costo unitario ya está por gramo/unidad directa o es pequeño, usamos multiplicación directa cuando costoUnit <= 0.20
            BigDecimal subCosto = (costoUnit.compareTo(new BigDecimal("0.20")) <= 0)
                ? r.getCantidadNecesaria().multiply(costoUnit)
                : factor.multiply(costoUnit);
            costoTotal = costoTotal.add(subCosto);
        }
        costoTotal = costoTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal precio = p.getPrecioVenta() != null ? p.getPrecioVenta() : BigDecimal.ONE;
        BigDecimal margenPct = precio.compareTo(BigDecimal.ZERO) > 0
            ? precio.subtract(costoTotal).multiply(new BigDecimal("100")).divide(precio, 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        lblResumenEscandallo.setText("Costo Receta: S/ " + costoTotal + " · Margen: " + margenPct + "%");
    }

    /**
     * Asocia transaccionalmente un insumo a la receta de un platillo (§2.3).
     */
    public void asociarInsumoARecetaEnBD(int idProducto, long idInsumo, BigDecimal cantidad) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            recetaDAO.asociarInsumo(conn, idProducto, idInsumo, cantidad);
        }
    }

    @FXML
    public void handleAgregarInsumoReceta(ActionEvent event) {
        Producto prod = tblPlatillos.getSelectionModel().getSelectedItem();
        Insumo ins = cmbInsumoReceta.getValue();
        if (prod == null || ins == null) {
            return;
        }
        final BigDecimal cant;
        try {
            cant = new BigDecimal(txtCantidadReceta.getText().trim());
        } catch (RuntimeException e) {
            AnimationUtils.mostrarToast(rootRecetasStack, "Error: Ingrese una cantidad numérica válida.", true);
            return;
        }
        int idProducto = prod.getIdProducto();
        long idInsumo = ins.getIdInsumo();
        if (btnAgregarInsumo != null) {
            btnAgregarInsumo.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> asociarInsumoARecetaEnBD(idProducto, idInsumo, cant),
            () -> {
                if (btnAgregarInsumo != null) {
                    btnAgregarInsumo.setDisable(false);
                }
                cargarRecetaDePlatillo(prod);
                AnimationUtils.mostrarToast(rootRecetasStack, "Insumo asociado a la receta de " + prod.getNombre(), false);
            },
            e -> {
                if (btnAgregarInsumo != null) {
                    btnAgregarInsumo.setDisable(false);
                }
                AnimationUtils.mostrarToast(
                        rootRecetasStack,
                        "Error: " + (e != null ? e.getMessage() : "desconocido"),
                        true);
            }
        );
    }

    @FXML
    public void handleEliminarInsumoReceta(ActionEvent event) {
        Receta sel = tblComponentesReceta.getSelectionModel().getSelectedItem();
        Producto prod = tblPlatillos.getSelectionModel().getSelectedItem();
        if (sel == null || prod == null) {
            return;
        }
        int idReceta = sel.getIdReceta();
        if (btnQuitarInsumo != null) {
            btnQuitarInsumo.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    recetaDAO.eliminarComponente(conn, idReceta);
                }
            },
            () -> {
                if (btnQuitarInsumo != null) {
                    btnQuitarInsumo.setDisable(false);
                }
                cargarRecetaDePlatillo(prod);
            },
            e -> {
                if (btnQuitarInsumo != null) {
                    btnQuitarInsumo.setDisable(false);
                }
                AnimationUtils.mostrarToast(
                        rootRecetasStack,
                        "No se pudo quitar el insumo: " + (e != null ? e.getMessage() : "desconocido"),
                        true);
            }
        );
    }

    public ObservableList<Receta> getListaComponentes() {
        return listaComponentes;
    }

    public TableView<Producto> getTblPlatillos() {
        return tblPlatillos;
    }
}
