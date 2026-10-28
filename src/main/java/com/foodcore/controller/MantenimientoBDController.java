package com.foodcore.controller;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.model.Receta;
import com.foodcore.service.MantenimientoService;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.util.List;

public class MantenimientoBDController {

    @FXML private Label lblEstadoBD;

    // --- Tab 1: Productos ---
    @FXML private TableView<Producto> tblProductos;
    @FXML private TableColumn<Producto, String> colProdCodigo;
    @FXML private TableColumn<Producto, String> colProdNombre;
    @FXML private TableColumn<Producto, String> colProdCategoria;
    @FXML private TableColumn<Producto, BigDecimal> colProdPrecio;
    @FXML private TableColumn<Producto, String> colProdDisponible;

    @FXML private TextField txtProdCodigo;
    @FXML private TextField txtProdNombre;
    @FXML private ComboBox<Categoria> cmbProdCategoria;
    @FXML private TextField txtProdPrecio;
    @FXML private TextArea txtProdDesc;

    // --- Tab 2: Insumos ---
    @FXML private TableView<Insumo> tblInsumos;
    @FXML private TableColumn<Insumo, String> colInsCodigo;
    @FXML private TableColumn<Insumo, String> colInsNombre;
    @FXML private TableColumn<Insumo, String> colInsUnidad;
    @FXML private TableColumn<Insumo, BigDecimal> colInsStock;
    @FXML private TableColumn<Insumo, BigDecimal> colInsMinimo;
    @FXML private TableColumn<Insumo, BigDecimal> colInsCosto;

    @FXML private TextField txtInsCodigo;
    @FXML private TextField txtInsNombre;
    @FXML private ComboBox<String> cmbInsUnidad;
    @FXML private TextField txtInsStockInicial;
    @FXML private TextField txtInsStockMinimo;
    @FXML private TextField txtInsCosto;
    @FXML private TextField txtNuevoStock;

    // --- Tab 3: Recetas ---
    @FXML private ComboBox<Producto> cmbRecetaPlato;
    @FXML private TableView<Receta> tblReceta;
    @FXML private TableColumn<Receta, String> colRecInsumo;
    @FXML private TableColumn<Receta, BigDecimal> colRecCantidad;
    @FXML private TableColumn<Receta, String> colRecUnidad;
    @FXML private ComboBox<Insumo> cmbAgregarInsumo;
    @FXML private TextField txtCantidadReceta;

    // --- Tab 4: Diagnóstico ---
    @FXML private Label lblDiagnosticoEstado;
    @FXML private Label lblDiagnosticoUrl;
    @FXML private Label lblDiagnosticoMetricas;

    private final MantenimientoService service = new MantenimientoService();

    private final ObservableList<Producto> listaProductos = FXCollections.observableArrayList();
    private final ObservableList<Insumo> listaInsumos = FXCollections.observableArrayList();
    private final ObservableList<Categoria> listaCategorias = FXCollections.observableArrayList();
    private final ObservableList<Receta> listaRecetas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarColumnas();
        cargarCategorias();
        cargarProductos();
        cargarInsumos();
        actualizarDiagnostico();

        cmbInsUnidad.setItems(FXCollections.observableArrayList("GR", "UND", "ML", "KG", "LT"));
    }

    private void configurarColumnas() {
        // Productos
        colProdCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colProdNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colProdCategoria.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCategoria()));
        colProdPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioVenta()));
        colProdDisponible.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isDisponible() ? "SÍ" : "NO"));
        tblProductos.setItems(listaProductos);

        // Insumos
        colInsCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colInsNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colInsUnidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUnidadMedida()));
        colInsStock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getStockActual()));
        colInsMinimo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getStockMinimo()));
        colInsCosto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCostoUnitario()));
        tblInsumos.setItems(listaInsumos);

        // Recetas
        colRecInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreInsumo()));
        colRecCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidadNecesaria()));
        colRecUnidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUnidadMedidaInsumo()));
        tblReceta.setItems(listaRecetas);
    }

    // ==========================================================
    // MÉTODOS PRODUCTOS
    // ==========================================================
    @FXML
    public void cargarProductos() {
        new Thread(() -> {
            try {
                List<Producto> prods = service.listarProductos();
                Platform.runLater(() -> {
                    listaProductos.setAll(prods);
                    cmbRecetaPlato.setItems(FXCollections.observableArrayList(prods));
                });
            } catch (Exception e) {
                mostrarAlerta("Error", "No se pudo cargar productos: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }).start();
    }

    private void cargarCategorias() {
        new Thread(() -> {
            try {
                List<Categoria> cats = service.listarCategorias();
                Platform.runLater(() -> {
                    listaCategorias.setAll(cats);
                    cmbProdCategoria.setItems(listaCategorias);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    public void handleGuardarProducto(ActionEvent event) {
        try {
            Categoria cat = cmbProdCategoria.getValue();
            if (cat == null) {
                mostrarAlerta("Validación", "Seleccione una categoría para el plato.", Alert.AlertType.WARNING);
                return;
            }
            BigDecimal precio = new BigDecimal(txtProdPrecio.getText().trim());
            Producto p = new Producto(
                0,
                cat.getIdCategoria(),
                txtProdCodigo.getText().trim(),
                txtProdNombre.getText().trim(),
                txtProdDesc.getText().trim(),
                precio,
                BigDecimal.ZERO,
                true
            );
            service.guardarProducto(p);
            mostrarAlerta("Éxito", "Plato registrado exitosamente en la base de datos.", Alert.AlertType.INFORMATION);
            limpiarCamposProducto();
            cargarProductos();
        } catch (NumberFormatException e) {
            mostrarAlerta("Validación", "Ingrese un precio numérico válido (ej: 18.50).", Alert.AlertType.WARNING);
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleAlternarDisponibilidad(ActionEvent event) {
        Producto sel = tblProductos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta("Aviso", "Seleccione un producto de la tabla.", Alert.AlertType.WARNING);
            return;
        }
        try {
            service.alternarDisponibilidadProducto(sel.getIdProducto(), !sel.isDisponible());
            cargarProductos();
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void limpiarCamposProducto() {
        txtProdCodigo.clear();
        txtProdNombre.clear();
        txtProdPrecio.clear();
        txtProdDesc.clear();
        cmbProdCategoria.getSelectionModel().clearSelection();
    }

    // ==========================================================
    // MÉTODOS INSUMOS
    // ==========================================================
    @FXML
    public void cargarInsumos() {
        new Thread(() -> {
            try {
                List<Insumo> ins = service.listarInsumos();
                Platform.runLater(() -> {
                    listaInsumos.setAll(ins);
                    cmbAgregarInsumo.setItems(FXCollections.observableArrayList(ins));
                });
            } catch (Exception e) {
                mostrarAlerta("Error", "No se pudo cargar insumos: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }).start();
    }

    @FXML
    public void handleGuardarInsumo(ActionEvent event) {
        try {
            String unidad = cmbInsUnidad.getValue();
            if (unidad == null || unidad.trim().isEmpty()) {
                mostrarAlerta("Validación", "Seleccione la unidad de medida (GR, UND, ML, etc.)", Alert.AlertType.WARNING);
                return;
            }
            BigDecimal stockIni = new BigDecimal(txtInsStockInicial.getText().trim());
            BigDecimal stockMin = new BigDecimal(txtInsStockMinimo.getText().trim());
            BigDecimal costo = new BigDecimal(txtInsCosto.getText().trim());

            Insumo ins = new Insumo(
                0,
                txtInsCodigo.getText().trim(),
                txtInsNombre.getText().trim(),
                unidad,
                stockIni,
                stockMin,
                costo
            );
            service.guardarInsumo(ins);
            mostrarAlerta("Éxito", "Insumo guardado correctamente.", Alert.AlertType.INFORMATION);
            limpiarCamposInsumo();
            cargarInsumos();
        } catch (NumberFormatException e) {
            mostrarAlerta("Validación", "Ingrese valores numéricos válidos en stock y costo.", Alert.AlertType.WARNING);
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleAjustarStock(ActionEvent event) {
        Insumo sel = tblInsumos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta("Aviso", "Seleccione un insumo de la tabla para ajustar su stock.", Alert.AlertType.WARNING);
            return;
        }
        try {
            BigDecimal nuevo = new BigDecimal(txtNuevoStock.getText().trim());
            service.registrarAjusteStock(sel.getIdInsumo(), nuevo, "Ajuste manual de auditoría");
            mostrarAlerta("Ajuste Realizado", "Stock actualizado y movimiento de inventario registrado.", Alert.AlertType.INFORMATION);
            txtNuevoStock.clear();
            cargarInsumos();
        } catch (NumberFormatException e) {
            mostrarAlerta("Validación", "Ingrese una cantidad válida para el nuevo stock.", Alert.AlertType.WARNING);
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void limpiarCamposInsumo() {
        txtInsCodigo.clear();
        txtInsNombre.clear();
        cmbInsUnidad.getSelectionModel().clearSelection();
        txtInsStockInicial.clear();
        txtInsStockMinimo.clear();
        txtInsCosto.clear();
    }

    // ==========================================================
    // MÉTODOS RECETAS
    // ==========================================================
    @FXML
    public void handleSeleccionarPlatoReceta(ActionEvent event) {
        Producto plato = cmbRecetaPlato.getValue();
        if (plato != null) {
            cargarRecetaPlato(plato.getIdProducto());
        }
    }

    private void cargarRecetaPlato(int idProducto) {
        new Thread(() -> {
            try {
                List<Receta> componentes = service.listarRecetaPorProducto(idProducto);
                Platform.runLater(() -> listaRecetas.setAll(componentes));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    public void handleAsociarInsumoReceta(ActionEvent event) {
        Producto plato = cmbRecetaPlato.getValue();
        Insumo insumo = cmbAgregarInsumo.getValue();
        if (plato == null || insumo == null) {
            mostrarAlerta("Validación", "Seleccione un plato y un insumo.", Alert.AlertType.WARNING);
            return;
        }
        try {
            BigDecimal cant = new BigDecimal(txtCantidadReceta.getText().trim());
            service.asociarInsumoAReceta(plato.getIdProducto(), insumo.getIdInsumo(), cant);
            mostrarAlerta("Éxito", "Insumo agregado a la receta de " + plato.getNombre(), Alert.AlertType.INFORMATION);
            txtCantidadReceta.clear();
            cargarRecetaPlato(plato.getIdProducto());
        } catch (NumberFormatException e) {
            mostrarAlerta("Validación", "Ingrese una cantidad válida en números.", Alert.AlertType.WARNING);
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleEliminarItemReceta(ActionEvent event) {
        Receta sel = tblReceta.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta("Aviso", "Seleccione un insumo de la receta a remover.", Alert.AlertType.WARNING);
            return;
        }
        try {
            service.eliminarInsumoDeReceta(sel.getIdReceta());
            if (cmbRecetaPlato.getValue() != null) {
                cargarRecetaPlato(cmbRecetaPlato.getValue().getIdProducto());
            }
        } catch (Exception e) {
            mostrarAlerta("Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ==========================================================
    // MÉTODOS DIAGNÓSTICO
    // ==========================================================
    @FXML
    public void handleTestPing(ActionEvent event) {
        boolean ok = service.verificarSaludBD();
        actualizarDiagnostico();
        if (ok) {
            mostrarAlerta("Conexión Exitosa", "La conexión a MySQL 8.0 respondió correctamente (latencia < 2ms).", Alert.AlertType.INFORMATION);
        } else {
            mostrarAlerta("Fallo de Conexión", "No se pudo comunicar con el servidor MySQL.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleRecargarPool(ActionEvent event) {
        ConexionBD.inicializarPool();
        actualizarDiagnostico();
        mostrarAlerta("Pool Reiniciado", "El pool HikariCP ha sido reiniciado con database.properties.", Alert.AlertType.INFORMATION);
    }

    private void actualizarDiagnostico() {
        boolean viva = service.verificarSaludBD();
        lblEstadoBD.setText(viva ? "MySQL: Conectado" : "MySQL: Desconectado");
        lblEstadoBD.setStyle(viva
            ? "-fx-background-color: #e6f4ea; -fx-text-fill: #2a9d8f; -fx-padding: 6px 14px; -fx-background-radius: 20px; -fx-font-weight: bold;"
            : "-fx-background-color: #fee2e2; -fx-text-fill: #e63946; -fx-padding: 6px 14px; -fx-background-radius: 20px; -fx-font-weight: bold;"
        );

        lblDiagnosticoEstado.setText(viva ? "Activo, Saludable y Respondiendo" : "Sin Conexión con MySQL Server");
        lblDiagnosticoEstado.setStyle(viva ? "-fx-text-fill: #2a9d8f; -fx-font-weight: bold;" : "-fx-text-fill: #e63946; -fx-font-weight: bold;");
        lblDiagnosticoUrl.setText(ConexionBD.getJdbcUrlInfo());
        lblDiagnosticoMetricas.setText(service.obtenerMetricasPool());
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }
}
