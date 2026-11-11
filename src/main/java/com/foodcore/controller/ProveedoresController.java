package com.foodcore.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.dao.impl.ProveedorDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.Proveedor;
import com.foodcore.service.FinanzasService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

public class ProveedoresController {

    private static final Logger LOGGER = Logger.getLogger(ProveedoresController.class.getName());

    @FXML private TableView<Proveedor> tblProveedores;
    @FXML private TableColumn<Proveedor, String> colRuc;
    @FXML private TableColumn<Proveedor, String> colRazonSocial;
    @FXML private TableColumn<Proveedor, String> colContacto;
    @FXML private TableColumn<Proveedor, String> colTelefono;
    @FXML private TableColumn<Proveedor, String> colDiasCredito;
    @FXML private TableColumn<Proveedor, String> colDeudaActual;
    @FXML private TableColumn<Proveedor, String> colEstado;

    @FXML private TextField txtBuscar;
    @FXML private Label lblTotalProveedores;

    @FXML private Label lblDetalleNombre;
    @FXML private Label lblDetalleRuc;
    @FXML private Label lblDetalleContacto;
    @FXML private Label lblDetalleEmail;
    @FXML private Label lblDetalleDias;
    @FXML private Label lblDetalleDeuda;
    @FXML private Label lblDetalleFacturas;
    @FXML private Button btnGenerarOrdenCompra;
    @FXML private Button btnEditarProveedor;
    @FXML private Button btnCambiarEstado;

    @FXML private VBox modalProveedor;
    @FXML private Label lblModalTitulo;
    @FXML private TextField txtModalRuc;
    @FXML private TextField txtModalRazonSocial;
    @FXML private TextField txtModalContacto;
    @FXML private TextField txtModalTelefono;
    @FXML private TextField txtModalEmail;
    @FXML private TextField txtModalDiasCredito;
    @FXML private Label lblModalError;

    private final ProveedorDAO proveedorDAO = new ProveedorDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final FinanzasService finanzasService = new FinanzasService();

    private final ObservableList<Proveedor> listaProveedores = FXCollections.observableArrayList();
    private Proveedor proveedorSeleccionado = null;
    private boolean modoEdicion = false;

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarSeleccion();
        configurarBusqueda();
        cargarProveedoresAsync();
    }

    private void configurarColumnas() {
        colRuc.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getRuc()));
        colRazonSocial.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getRazonSocial()));
        colContacto.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getContacto() != null ? p.getValue().getContacto() : "-"));
        colTelefono.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getTelefono() != null ? p.getValue().getTelefono() : "-"));
        colDiasCredito.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getDiasCredito() + " d"));
        colDeudaActual.setCellValueFactory(p -> new SimpleStringProperty(String.format("S/ %.2f", p.getValue().getDeudaActual())));
        colEstado.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().isActivo() ? "ACTIVO" : "INACTIVO"));
        tblProveedores.setItems(listaProveedores);
    }

    private void configurarSeleccion() {
        tblProveedores.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                seleccionarProveedor(newV);
            }
        });
    }

    private void configurarBusqueda() {
        txtBuscar.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                cargarProveedoresAsync();
            } else {
                String filtro = newV.toLowerCase().trim();
                List<Proveedor> filtrados = listaProveedores.stream()
                        .filter(p -> p.getRuc().toLowerCase().contains(filtro) || p.getRazonSocial().toLowerCase().contains(filtro))
                        .collect(Collectors.toList());
                tblProveedores.setItems(FXCollections.observableArrayList(filtrados));
            }
        });
    }

    public void cargarProveedoresAsync() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    return proveedorDAO.listarTodos(conn);
                }
            },
            todos -> {
                listaProveedores.setAll(todos);
                tblProveedores.setItems(listaProveedores);
                lblTotalProveedores.setText(todos.size() + " Proveedores");
                if (!todos.isEmpty()) {
                    tblProveedores.getSelectionModel().selectFirst();
                    seleccionarProveedor(todos.get(0));
                }
            },
            ex -> LOGGER.log(Level.WARNING, "Error al cargar proveedores", ex)
        );
    }

    public void cargarProveedoresSincrono() {
        cargarProveedoresAsync();
    }

    public void seleccionarProveedor(Proveedor p) {
        this.proveedorSeleccionado = p;
        lblDetalleNombre.setText(p.getRazonSocial());
        lblDetalleRuc.setText(p.getRuc());
        lblDetalleContacto.setText((p.getContacto() != null ? p.getContacto() : "-") + " (" + (p.getTelefono() != null ? p.getTelefono() : "-") + ")");
        lblDetalleEmail.setText(p.getEmail() != null ? p.getEmail() : "-");
        lblDetalleDias.setText(p.getDiasCredito() + " días calendario");
        lblDetalleDeuda.setText(String.format("S/ %.2f", p.getDeudaActual()));
        lblDetalleFacturas.setText(p.getFacturasPendientes() + " facturas pendientes de pago");
    }

    @FXML
    public void handleAbrirModalNuevo() {
        modoEdicion = false;
        lblModalTitulo.setText("Registrar Nuevo Proveedor");
        txtModalRuc.clear();
        txtModalRazonSocial.clear();
        txtModalContacto.clear();
        txtModalTelefono.clear();
        txtModalEmail.clear();
        txtModalDiasCredito.setText("15");
        lblModalError.setText("");
        modalProveedor.setVisible(true);
    }

    @FXML
    public void handleEditarSeleccionado() {
        if (proveedorSeleccionado == null) return;
        modoEdicion = true;
        lblModalTitulo.setText("Editar Proveedor #" + proveedorSeleccionado.getIdProveedor());
        txtModalRuc.setText(proveedorSeleccionado.getRuc());
        txtModalRazonSocial.setText(proveedorSeleccionado.getRazonSocial());
        txtModalContacto.setText(proveedorSeleccionado.getContacto());
        txtModalTelefono.setText(proveedorSeleccionado.getTelefono());
        txtModalEmail.setText(proveedorSeleccionado.getEmail());
        txtModalDiasCredito.setText(String.valueOf(proveedorSeleccionado.getDiasCredito()));
        lblModalError.setText("");
        modalProveedor.setVisible(true);
    }

    @FXML
    public void handleCerrarModal() {
        modalProveedor.setVisible(false);
    }

    @FXML
    public void handleGuardarProveedor() {
        String ruc = txtModalRuc.getText().trim();
        String razon = txtModalRazonSocial.getText().trim();
        if (ruc.length() != 11) {
            lblModalError.setText("El RUC debe tener exactamente 11 dígitos numéricos");
            return;
        }
        if (razon.isEmpty()) {
            lblModalError.setText("La razón social es obligatoria");
            return;
        }
        int dias = 15;
        try {
            dias = Integer.parseInt(txtModalDiasCredito.getText().trim());
        } catch (Exception ignored) {}

        final int diasFinal = dias;
        final String contacto = txtModalContacto.getText().trim();
        final String telefono = txtModalTelefono.getText().trim();
        final String email = txtModalEmail.getText().trim();
        final boolean editarFinal = modoEdicion && proveedorSeleccionado != null;
        final Proveedor sel = proveedorSeleccionado;

        lblModalError.setText("");
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (editarFinal) {
                        sel.setRuc(ruc);
                        sel.setRazonSocial(razon);
                        sel.setContacto(contacto);
                        sel.setTelefono(telefono);
                        sel.setEmail(email);
                        sel.setDiasCredito(diasFinal);
                        proveedorDAO.actualizar(conn, sel);
                    } else {
                        Proveedor nuevo = new Proveedor();
                        nuevo.setRuc(ruc);
                        nuevo.setRazonSocial(razon);
                        nuevo.setContacto(contacto);
                        nuevo.setTelefono(telefono);
                        nuevo.setEmail(email);
                        nuevo.setDiasCredito(diasFinal);
                        proveedorDAO.insertar(conn, nuevo);
                    }
                }
            },
            () -> {
                modalProveedor.setVisible(false);
                cargarProveedoresAsync();
            },
            ex -> lblModalError.setText("Error al guardar: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    @FXML
    public void handleCambiarEstado() {
        if (proveedorSeleccionado == null) return;
        final int idProveedor = proveedorSeleccionado.getIdProveedor();
        final boolean nuevoEstado = !proveedorSeleccionado.isActivo();
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    proveedorDAO.cambiarEstado(conn, idProveedor, nuevoEstado);
                }
            },
            this::cargarProveedoresAsync,
            ex -> LOGGER.log(Level.WARNING, "Error al cambiar estado de proveedor", ex)
        );
    }

    @FXML
    public void handleGenerarOrdenCompra() {
        if (proveedorSeleccionado == null) return;
        final int idProveedor = proveedorSeleccionado.getIdProveedor();
        final String ruc = proveedorSeleccionado.getRuc();
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                List<Insumo> insumosBajos;
                try (Connection conn = ConexionBD.getConnection()) {
                    insumosBajos = insumoDAO.listarTodos(conn).stream()
                            .filter(i -> i.getStockActualDisponible() != null && i.getStockMinimo() != null
                                    && i.getStockActualDisponible().compareTo(i.getStockMinimo()) <= 0)
                            .collect(Collectors.toList());
                    if (insumosBajos.isEmpty()) {
                        insumosBajos = insumoDAO.listarTodos(conn).stream().limit(3).collect(Collectors.toList());
                    }
                }
                File doc = new File("dist/ORDEN_COMPRA_" + ruc + ".pdf");
                if (doc.getParentFile() != null) {
                    java.nio.file.Files.createDirectories(doc.getParentFile().toPath());
                }
                finanzasService.generarOrdenCompraPdf(idProveedor, insumosBajos, doc);
                return doc.getName();
            },
            docName -> lblDetalleFacturas.setText("✓ Orden de compra generada: " + docName),
            ex -> lblDetalleFacturas.setText("Error en PDF: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }
}
