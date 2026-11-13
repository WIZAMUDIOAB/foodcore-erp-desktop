package com.foodcore.controller;

import com.foodcore.dao.ConfiguracionImpresoraDAO;
import com.foodcore.dao.impl.ConfiguracionImpresoraDAOImpl;
import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Configuración del Restaurante y Múltiples Impresoras por Categoría (v5.2.0):
 * persiste datos generales del comprobante y asigna {@code Categoría -> Impresora Windows}.
 */
public class ConfiguracionController {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracionController.class.getName());

    public record CategoriaOpcion(int idCategoria, String nombre) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    @FXML private StackPane rootConfigStack;
    @FXML private TextField txtNombreRestaurante;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtRuc;
    @FXML private TextField txtImpresora;
    @FXML private Button btnGuardarConfig;

    @FXML private ComboBox<CategoriaOpcion> cmbCategoriaImpresora;
    @FXML private ComboBox<String> cmbNombreImpresoraWindows;
    @FXML private Button btnAsignarImpresora;
    @FXML private Button btnQuitarImpresora;
    @FXML private TableView<ConfiguracionImpresora> tblRuteoImpresoras;
    @FXML private TableColumn<ConfiguracionImpresora, String> colCatNombre;
    @FXML private TableColumn<ConfiguracionImpresora, String> colImpWindows;
    @FXML private TableColumn<ConfiguracionImpresora, String> colImpEstado;

    private final ConfiguracionImpresoraDAO configImpresoraDAO = new ConfiguracionImpresoraDAOImpl();
    private final ObservableList<ConfiguracionImpresora> asignaciones = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnGuardarConfig);
        AnimationUtils.attachTactileFeedback(btnAsignarImpresora);
        AnimationUtils.attachTactileFeedback(btnQuitarImpresora);
        configurarTablaImpresoras();
        poblarImpresorasDetectadas();
        cargarConfiguracion();
        cargarRuteoImpresorasAsync();
    }

    private void configurarTablaImpresoras() {
        if (tblRuteoImpresoras == null) {
            return;
        }
        colCatNombre.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getNombreCategoria() != null ? d.getValue().getNombreCategoria()
                        : "Categoría #" + d.getValue().getIdCategoriaProducto()));
        colImpWindows.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getNombreImpresoraWindows() != null ? d.getValue().getNombreImpresoraWindows() : ""));
        colImpEstado.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().isActiva() ? "ACTIVA" : "INACTIVA"));
        tblRuteoImpresoras.setItems(asignaciones);
    }

    private void poblarImpresorasDetectadas() {
        if (cmbNombreImpresoraWindows == null) {
            return;
        }
        Set<String> opciones = new LinkedHashSet<>();
        opciones.addAll(ImpresoraTermicaService.listarImpresorasWindows());
        opciones.add("Cocina");
        opciones.add("Barra");
        opciones.add("Parrilla");
        opciones.add("Caja");
        cmbNombreImpresoraWindows.getItems().setAll(opciones);
    }

    public void cargarRuteoImpresorasAsync() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    Map<Integer, String> categorias = configImpresoraDAO.obtenerMapaNombreCategorias(conn);
                    List<ConfiguracionImpresora> lista = configImpresoraDAO.listarTodas(conn);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("categorias", categorias);
                    res.put("lista", lista);
                    return res;
                }
            },
            res -> {
                @SuppressWarnings("unchecked")
                Map<Integer, String> categorias = (Map<Integer, String>) res.get("categorias");
                @SuppressWarnings("unchecked")
                List<ConfiguracionImpresora> lista = (List<ConfiguracionImpresora>) res.get("lista");

                if (cmbCategoriaImpresora != null && categorias != null) {
                    cmbCategoriaImpresora.getItems().clear();
                    for (Map.Entry<Integer, String> entry : categorias.entrySet()) {
                        cmbCategoriaImpresora.getItems().add(new CategoriaOpcion(entry.getKey(), entry.getValue()));
                    }
                    if (!cmbCategoriaImpresora.getItems().isEmpty() && cmbCategoriaImpresora.getValue() == null) {
                        cmbCategoriaImpresora.getSelectionModel().selectFirst();
                    }
                }
                asignaciones.setAll(lista != null ? lista : List.of());
            },
            ex -> LOGGER.log(Level.WARNING, "No se pudo cargar ruteo de impresoras por categoría", ex)
        );
    }

    @FXML
    public void handleAsignarImpresoraCategoria(ActionEvent event) {
        CategoriaOpcion catSel = cmbCategoriaImpresora != null ? cmbCategoriaImpresora.getValue() : null;
        String impresoraRaw = cmbNombreImpresoraWindows != null
                ? cmbNombreImpresoraWindows.getEditor().getText() : null;
        if ((impresoraRaw == null || impresoraRaw.isBlank()) && cmbNombreImpresoraWindows != null) {
            impresoraRaw = cmbNombreImpresoraWindows.getValue();
        }
        if (catSel == null || impresoraRaw == null || impresoraRaw.isBlank()) {
            AnimationUtils.mostrarToast(rootConfigStack,
                    "Seleccione una categoría e indique el nombre de la impresora Windows.", true);
            return;
        }
        final String impresoraFinal = impresoraRaw.trim();
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    configImpresoraDAO.guardarOActualizar(conn, catSel.idCategoria(), impresoraFinal);
                }
            },
            () -> {
                AnimationUtils.mostrarToast(rootConfigStack,
                        "Impresora asignada: " + catSel.nombre() + " → " + impresoraFinal, false);
                cargarRuteoImpresorasAsync();
            },
            ex -> AnimationUtils.mostrarToast(rootConfigStack,
                    "No se pudo guardar asignación de impresora: " + ex.getMessage(), true)
        );
    }

    @FXML
    public void handleEliminarImpresoraCategoria(ActionEvent event) {
        ConfiguracionImpresora sel = tblRuteoImpresoras != null
                ? tblRuteoImpresoras.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            AnimationUtils.mostrarToast(rootConfigStack, "Seleccione una asignación en la tabla para quitar.", true);
            return;
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    configImpresoraDAO.eliminarPorCategoria(conn, sel.getIdCategoriaProducto());
                }
            },
            () -> {
                AnimationUtils.mostrarToast(rootConfigStack, "Asignación de impresora eliminada.", false);
                cargarRuteoImpresorasAsync();
            },
            ex -> AnimationUtils.mostrarToast(rootConfigStack,
                    "Error al quitar asignación: " + ex.getMessage(), true)
        );
    }

    public ObservableList<ConfiguracionImpresora> getAsignaciones() {
        return asignaciones;
    }

    public ComboBox<CategoriaOpcion> getCmbCategoriaImpresora() {
        return cmbCategoriaImpresora;
    }

    public ComboBox<String> getCmbNombreImpresoraWindows() {
        return cmbNombreImpresoraWindows;
    }

    public void cargarConfiguracion() {
        String sql = "SELECT nombre_restaurante, direccion, telefono, ruc, nombre_impresora "
            + "FROM configuracion_restaurante WHERE id_config = 1";
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new String[] {
                            rs.getString("nombre_restaurante"),
                            rs.getString("direccion"),
                            rs.getString("telefono"),
                            rs.getString("ruc"),
                            rs.getString("nombre_impresora")
                        };
                    }
                    return null;
                }
            },
            datos -> {
                if (datos != null) {
                    txtNombreRestaurante.setText(datos[0]);
                    txtDireccion.setText(datos[1]);
                    txtTelefono.setText(datos[2]);
                    txtRuc.setText(datos[3] != null ? datos[3] : "");
                    txtImpresora.setText(datos[4]);
                }
            },
            e -> LOGGER.log(Level.FINE, "Usando configuración por defecto", e)
        );
    }

    @FXML
    public void handleGuardarConfiguracion(ActionEvent event) {
        final String nombre = txtNombreRestaurante.getText();
        final String direccion = txtDireccion.getText();
        final String telefono = txtTelefono.getText();
        final String ruc = txtRuc.getText();
        final String impresora = txtImpresora.getText();
        final String sql = "INSERT INTO configuracion_restaurante (id_config, nombre_restaurante, direccion, telefono, ruc, nombre_impresora) "
            + "VALUES (1, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE nombre_restaurante = VALUES(nombre_restaurante), direccion = VALUES(direccion), "
            + "telefono = VALUES(telefono), ruc = VALUES(ruc), nombre_impresora = VALUES(nombre_impresora)";

        if (btnGuardarConfig != null) {
            btnGuardarConfig.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, nombre);
                    ps.setString(2, direccion);
                    ps.setString(3, telefono);
                    ps.setString(4, ruc);
                    ps.setString(5, impresora);
                    ps.executeUpdate();
                }
            },
            () -> {
                if (btnGuardarConfig != null) {
                    btnGuardarConfig.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootConfigStack, "Configuración del restaurante e impresora guardada.", false);
            },
            e -> {
                if (btnGuardarConfig != null) {
                    btnGuardarConfig.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootConfigStack, "Error al guardar configuración: " + (e != null ? e.getMessage() : "desconocido"), true);
            }
        );
    }
}
