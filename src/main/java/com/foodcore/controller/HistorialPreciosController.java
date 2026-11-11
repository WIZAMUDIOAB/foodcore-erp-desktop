package com.foodcore.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.HistorialPrecioDAOImpl;
import com.foodcore.model.HistorialPrecio;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HistorialPreciosController {

    private static final Logger LOGGER = Logger.getLogger(HistorialPreciosController.class.getName());

    @FXML private TableView<HistorialPrecio> tblHistorial;
    @FXML private TableColumn<HistorialPrecio, String> colFecha;
    @FXML private TableColumn<HistorialPrecio, String> colTipo;
    @FXML private TableColumn<HistorialPrecio, String> colEntidad;
    @FXML private TableColumn<HistorialPrecio, String> colPrecioAnt;
    @FXML private TableColumn<HistorialPrecio, String> colPrecioNuevo;
    @FXML private TableColumn<HistorialPrecio, String> colVariacion;
    @FXML private TableColumn<HistorialPrecio, String> colUsuario;
    @FXML private TableColumn<HistorialPrecio, String> colMotivo;

    @FXML private ComboBox<String> cbFiltroTipo;
    @FXML private TextField txtBuscar;
    @FXML private Label lblTotalRegistros;
    @FXML private Button btnNuevoCambio;
    @FXML private Button btnActualizar;

    @FXML private VBox modalAjuste;
    @FXML private ComboBox<String> cbModalTipo;
    @FXML private TextField txtModalItemNombre;
    @FXML private TextField txtModalPrecioAnt;
    @FXML private TextField txtModalPrecioNuevo;
    @FXML private TextField txtModalMotivo;
    @FXML private Label lblModalAjusteError;

    private final HistorialPrecioDAO historialDAO = new HistorialPrecioDAOImpl();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final FinanzasService finanzasService = new FinanzasService();

    private final ObservableList<HistorialPrecio> listaHistorial = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarFiltros();
        cargarHistorialSincrono();
    }

    private void configurarColumnas() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        colFecha.setCellValueFactory(h -> new SimpleStringProperty(h.getValue().getFechaCambio() != null ? h.getValue().getFechaCambio().format(dtf) : "-"));
        colTipo.setCellValueFactory(h -> new SimpleStringProperty(h.getValue().getTipoEntidad()));
        colEntidad.setCellValueFactory(h -> new SimpleStringProperty(h.getValue().getNombreEntidad()));
        colPrecioAnt.setCellValueFactory(h -> new SimpleStringProperty(String.format("S/ %.2f", h.getValue().getPrecioAnterior())));
        colPrecioNuevo.setCellValueFactory(h -> new SimpleStringProperty(String.format("S/ %.2f", h.getValue().getPrecioNuevo())));
        colVariacion.setCellValueFactory(h -> {
            double var = h.getValue().getVariacionPorcentual();
            String prefix = var >= 0 ? "+" : "";
            return new SimpleStringProperty(prefix + String.format("%.1f%%", var));
        });
        colUsuario.setCellValueFactory(h -> new SimpleStringProperty(h.getValue().getNombreUsuario()));
        colMotivo.setCellValueFactory(h -> new SimpleStringProperty(h.getValue().getMotivo()));
        tblHistorial.setItems(listaHistorial);
    }

    private void configurarFiltros() {
        cbFiltroTipo.setItems(FXCollections.observableArrayList("TODOS", "PRODUCTO", "INSUMO"));
        cbFiltroTipo.setValue("TODOS");
        cbFiltroTipo.setOnAction(e -> cargarHistorialSincrono());

        cbModalTipo.setItems(FXCollections.observableArrayList("PRODUCTO", "INSUMO"));
        cbModalTipo.setValue("PRODUCTO");

        txtBuscar.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                cargarHistorialSincrono();
            } else {
                String f = newV.toLowerCase().trim();
                List<HistorialPrecio> filtrados = listaHistorial.stream()
                        .filter(h -> h.getNombreEntidad().toLowerCase().contains(f) || h.getMotivo().toLowerCase().contains(f))
                        .collect(Collectors.toList());
                tblHistorial.setItems(FXCollections.observableArrayList(filtrados));
            }
        });
    }

    public void cargarHistorialSincrono() {
        listaHistorial.clear();
        String filtroTipo = cbFiltroTipo.getValue();

        try (Connection conn = ConexionBD.getConnection()) {
            List<HistorialPrecio> todos = historialDAO.listarTodos(conn);
            if (filtroTipo != null && !"TODOS".equals(filtroTipo)) {
                todos = todos.stream().filter(h -> filtroTipo.equalsIgnoreCase(h.getTipoEntidad())).collect(Collectors.toList());
            }
            listaHistorial.addAll(todos);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar historial precios", ex);
        }

        lblTotalRegistros.setText(listaHistorial.size() + " Modificaciones");
    }

    @FXML
    public void handleAbrirModalAjuste() {
        cbModalTipo.setValue("PRODUCTO");
        txtModalItemNombre.clear();
        txtModalPrecioAnt.clear();
        txtModalPrecioNuevo.clear();
        txtModalMotivo.clear();
        lblModalAjusteError.setText("");
        modalAjuste.setVisible(true);
    }

    @FXML
    public void handleCerrarModalAjuste() {
        modalAjuste.setVisible(false);
    }

    @FXML
    public void handleGuardarAjuste() {
        String tipo = cbModalTipo.getValue();
        String nombre = txtModalItemNombre.getText().trim();
        String motivo = txtModalMotivo.getText().trim();

        if (nombre.isEmpty()) {
            lblModalAjusteError.setText("Debe ingresar el nombre del ítem");
            return;
        }
        if (motivo.length() < 5) {
            lblModalAjusteError.setText("El motivo es obligatorio (mínimo 5 caracteres)");
            return;
        }

        double pAnt, pNuevo;
        try {
            pAnt = Double.parseDouble(txtModalPrecioAnt.getText().trim());
            pNuevo = Double.parseDouble(txtModalPrecioNuevo.getText().trim());
        } catch (Exception ex) {
            lblModalAjusteError.setText("Ingrese valores numéricos válidos para precios");
            return;
        }

        int idEntidad = 1;
        try (Connection conn = ConexionBD.getConnection()) {
            if ("PRODUCTO".equalsIgnoreCase(tipo)) {
                List<Producto> prods = productoDAO.listarTodos(conn);
                if (!prods.isEmpty()) idEntidad = prods.get(0).getIdProducto();
            } else {
                List<Insumo> ins = insumoDAO.listarTodos(conn);
                if (!ins.isEmpty()) idEntidad = (int) ins.get(0).getIdInsumo();
            }
        } catch (java.sql.SQLException | RuntimeException ignored) {}

        int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
        String usuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getUsername() : "admin";

        try {
            finanzasService.registrarCambioPrecioConMotivo(tipo, idEntidad, nombre, pAnt, pNuevo, idUsuario, usuario, motivo);
            modalAjuste.setVisible(false);
            cargarHistorialSincrono();
        } catch (Exception ex) {
            lblModalAjusteError.setText("Error al guardar: " + ex.getMessage());
        }
    }
}
