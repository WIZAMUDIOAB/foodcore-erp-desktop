package com.foodcore.controller;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.model.Cliente;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientesController {

    private static final Logger LOGGER = Logger.getLogger(ClientesController.class.getName());

    @FXML private TableView<Cliente> tblClientes;
    @FXML private TableColumn<Cliente, String> colTipoDoc;
    @FXML private TableColumn<Cliente, String> colDocumento;
    @FXML private TableColumn<Cliente, String> colRazonSocial;
    @FXML private TableColumn<Cliente, String> colContacto;
    @FXML private TableColumn<Cliente, String> colTelefono;
    @FXML private TableColumn<Cliente, String> colLimiteCredito;
    @FXML private TableColumn<Cliente, String> colSaldoDeudor;
    @FXML private TableColumn<Cliente, String> colPuntos;
    @FXML private TableColumn<Cliente, String> colEstado;

    @FXML private TextField txtBuscar;
    @FXML private Label lblTotalClientes;

    @FXML private Label lblDetalleRazonSocial;
    @FXML private Label lblDetalleDocumento;
    @FXML private Label lblDetalleContacto;
    @FXML private Label lblDetalleEmail;
    @FXML private Label lblDetalleLimite;
    @FXML private Label lblDetallePuntos;
    @FXML private Label lblDetalleSaldoDeudor;
    @FXML private Label lblDetalleComprobantes;
    @FXML private Button btnEstadoCuentaPdf;
    @FXML private Button btnEditarCliente;
    @FXML private Button btnCambiarEstado;

    @FXML private VBox modalCliente;
    @FXML private Label lblModalTitulo;
    @FXML private ComboBox<String> cbModalTipoDoc;
    @FXML private TextField txtModalNumeroDoc;
    @FXML private TextField txtModalRazonSocial;
    @FXML private TextField txtModalContacto;
    @FXML private TextField txtModalTelefono;
    @FXML private TextField txtModalEmail;
    @FXML private TextField txtModalLimite;
    @FXML private TextField txtModalDiasCredito;
    @FXML private Label lblModalError;

    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final FinanzasService finanzasService = new FinanzasService();

    private final ObservableList<Cliente> listaClientes = FXCollections.observableArrayList();
    private Cliente clienteSeleccionado = null;
    private boolean modoEdicion = false;

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarSeleccion();
        configurarBusqueda();
        configurarModal();
        cargarClientesSincrono();
    }

    private void configurarColumnas() {
        colTipoDoc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipoDocumento()));
        colDocumento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroDocumento()));
        colRazonSocial.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocial()));
        colContacto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContacto() != null ? c.getValue().getContacto() : "-"));
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTelefono() != null ? c.getValue().getTelefono() : "-"));
        colLimiteCredito.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getLimiteCredito())));
        colSaldoDeudor.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getSaldoDeudor())));
        colPuntos.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPuntosFidelidad() + " pts"));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActivo() ? "ACTIVO" : "INACTIVO"));
        tblClientes.setItems(listaClientes);
        tblClientes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colRazonSocial.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        colContacto.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank() || "-".equals(item)) {
                    setText(empty ? null : "-");
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });
    }

    private void configurarSeleccion() {
        tblClientes.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                seleccionarCliente(newV);
            }
        });
    }

    private void configurarBusqueda() {
        txtBuscar.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                cargarClientesSincrono();
            } else {
                String f = newV.toLowerCase().trim();
                List<Cliente> filtrados = listaClientes.stream()
                        .filter(c -> c.getNumeroDocumento().toLowerCase().contains(f) || c.getRazonSocial().toLowerCase().contains(f))
                        .collect(Collectors.toList());
                tblClientes.setItems(FXCollections.observableArrayList(filtrados));
            }
        });
    }

    private void configurarModal() {
        cbModalTipoDoc.setItems(FXCollections.observableArrayList("RUC", "DNI"));
        cbModalTipoDoc.setValue("RUC");
    }

    public void cargarClientesSincrono() {
        listaClientes.clear();
        try (Connection conn = ConexionBD.getConnection()) {
            List<Cliente> todos = clienteDAO.listarTodos(conn);
            listaClientes.addAll(todos);
            lblTotalClientes.setText(todos.size() + " Clientes");
            if (!todos.isEmpty()) {
                tblClientes.getSelectionModel().selectFirst();
                seleccionarCliente(todos.get(0));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar clientes", ex);
        }
    }

    public void seleccionarCliente(Cliente c) {
        this.clienteSeleccionado = c;
        lblDetalleRazonSocial.setText(c.getRazonSocial());
        lblDetalleDocumento.setText(c.getTipoDocumento() + ": " + c.getNumeroDocumento());
        lblDetalleContacto.setText((c.getContacto() != null ? c.getContacto() : "-") + " (" + (c.getTelefono() != null ? c.getTelefono() : "-") + ")");
        lblDetalleEmail.setText(c.getEmail() != null ? c.getEmail() : "-");
        lblDetalleLimite.setText("S/ " + String.format("%.2f", c.getLimiteCredito()) + " (" + c.getDiasCredito() + " días)");
        lblDetallePuntos.setText(c.getPuntosFidelidad() + " Puntos Acumulados");
        lblDetalleSaldoDeudor.setText(String.format("S/ %.2f", c.getSaldoDeudor()));
        lblDetalleComprobantes.setText(c.getComprobantesPendientes() + " facturas pendientes de cobro");
    }

    @FXML
    public void handleAbrirModalNuevo() {
        modoEdicion = false;
        lblModalTitulo.setText("Registrar Cliente Corporativo");
        cbModalTipoDoc.setValue("RUC");
        txtModalNumeroDoc.clear();
        txtModalRazonSocial.clear();
        txtModalContacto.clear();
        txtModalTelefono.clear();
        txtModalEmail.clear();
        txtModalLimite.setText("5000.00");
        txtModalDiasCredito.setText("30");
        lblModalError.setText("");
        modalCliente.setVisible(true);
    }

    @FXML
    public void handleEditarSeleccionado() {
        if (clienteSeleccionado == null) return;
        modoEdicion = true;
        lblModalTitulo.setText("Editar Cliente #" + clienteSeleccionado.getIdCliente());
        cbModalTipoDoc.setValue(clienteSeleccionado.getTipoDocumento());
        txtModalNumeroDoc.setText(clienteSeleccionado.getNumeroDocumento());
        txtModalRazonSocial.setText(clienteSeleccionado.getRazonSocial());
        txtModalContacto.setText(clienteSeleccionado.getContacto());
        txtModalTelefono.setText(clienteSeleccionado.getTelefono());
        txtModalEmail.setText(clienteSeleccionado.getEmail());
        txtModalLimite.setText(String.valueOf(clienteSeleccionado.getLimiteCredito()));
        txtModalDiasCredito.setText(String.valueOf(clienteSeleccionado.getDiasCredito()));
        lblModalError.setText("");
        modalCliente.setVisible(true);
    }

    @FXML
    public void handleCerrarModal() {
        modalCliente.setVisible(false);
    }

    @FXML
    public void handleGuardarCliente() {
        String num = txtModalNumeroDoc.getText().trim();
        String razon = txtModalRazonSocial.getText().trim();
        if (num.isEmpty()) {
            lblModalError.setText("El número de documento es obligatorio");
            return;
        }
        if (razon.isEmpty()) {
            lblModalError.setText("La razón social o nombre es obligatorio");
            return;
        }
        double lim = 0.0;
        int dias = 30;
        try {
            lim = Double.parseDouble(txtModalLimite.getText().trim());
            dias = Integer.parseInt(txtModalDiasCredito.getText().trim());
        } catch (Exception ignored) {}

        try (Connection conn = ConexionBD.getConnection()) {
            if (modoEdicion && clienteSeleccionado != null) {
                clienteSeleccionado.setTipoDocumento(cbModalTipoDoc.getValue());
                clienteSeleccionado.setNumeroDocumento(num);
                clienteSeleccionado.setRazonSocial(razon);
                clienteSeleccionado.setContacto(txtModalContacto.getText().trim());
                clienteSeleccionado.setTelefono(txtModalTelefono.getText().trim());
                clienteSeleccionado.setEmail(txtModalEmail.getText().trim());
                clienteSeleccionado.setLimiteCredito(lim);
                clienteSeleccionado.setDiasCredito(dias);
                clienteDAO.actualizar(conn, clienteSeleccionado);
            } else {
                Cliente nuevo = new Cliente();
                nuevo.setTipoDocumento(cbModalTipoDoc.getValue());
                nuevo.setNumeroDocumento(num);
                nuevo.setRazonSocial(razon);
                nuevo.setContacto(txtModalContacto.getText().trim());
                nuevo.setTelefono(txtModalTelefono.getText().trim());
                nuevo.setEmail(txtModalEmail.getText().trim());
                nuevo.setLimiteCredito(lim);
                nuevo.setDiasCredito(dias);
                clienteDAO.insertar(conn, nuevo);
            }
            modalCliente.setVisible(false);
            cargarClientesSincrono();
        } catch (Exception ex) {
            lblModalError.setText("Error al guardar: " + ex.getMessage());
        }
    }

    @FXML
    public void handleCambiarEstado() {
        if (clienteSeleccionado == null) return;
        try (Connection conn = ConexionBD.getConnection()) {
            boolean nuevoEstado = !clienteSeleccionado.isActivo();
            clienteDAO.cambiarEstado(conn, clienteSeleccionado.getIdCliente(), nuevoEstado);
            cargarClientesSincrono();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cambiar estado", ex);
        }
    }

    @FXML
    public void handleGenerarEstadoCuentaPdf() {
        if (clienteSeleccionado == null) return;
        try {
            File doc = new File("dist/ESTADO_CUENTA_" + clienteSeleccionado.getNumeroDocumento() + ".pdf");
            if (doc.getParentFile() != null) { java.nio.file.Files.createDirectories(doc.getParentFile().toPath()); }
            finanzasService.generarEstadoCuentaClientePdf(clienteSeleccionado.getIdCliente(), doc);
            lblDetalleComprobantes.setText("✓ Estado de cuenta generado: " + doc.getName());
        } catch (Exception ex) {
            lblDetalleComprobantes.setText("Error PDF: " + ex.getMessage());
        }
    }
}
