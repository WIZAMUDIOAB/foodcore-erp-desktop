package com.foodcore.controller;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.model.Cliente;
import com.foodcore.model.CompraClienteResumen;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.FinanzasService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ConfiguracionFidelizacion;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.PaginadorTablaHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientesController {

    private static final Logger LOGGER = Logger.getLogger(ClientesController.class.getName());
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yy");

    @FXML private TabPane tabPaneClientes;
    @FXML private Tab tabCorporativos;
    @FXML private Tab tabMinoristas;

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

    @FXML private TableView<Cliente> tblClientesMinoristas;
    @FXML private TableColumn<Cliente, String> colMinDni;
    @FXML private TableColumn<Cliente, String> colMinNombre;
    @FXML private TableColumn<Cliente, String> colMinTelefono;
    @FXML private TableColumn<Cliente, String> colMinEmail;
    @FXML private TableColumn<Cliente, String> colMinFechaNac;
    @FXML private TableColumn<Cliente, String> colMinPuntos;
    @FXML private TableColumn<Cliente, String> colMinEstado;

    @FXML private TextField txtBuscar;
    @FXML private Label lblTotalClientes;
    @FXML private Button btnAnteriorCorp;
    @FXML private Label lblPaginaCorp;
    @FXML private Button btnSiguienteCorp;
    @FXML private Button btnAnteriorMin;
    @FXML private Label lblPaginaMin;
    @FXML private Button btnSiguienteMin;

    @FXML private Label lblBadgeTipoCliente;
    @FXML private Label lblDetalleRazonSocial;
    @FXML private Label lblDetalleDocumento;
    @FXML private Label lblDetalleContacto;
    @FXML private Label lblDetalleEmail;
    @FXML private Label lblDetalleCumpleanos;
    @FXML private VBox boxFinanzasFicha;
    @FXML private VBox boxCreditoCorpDetalle;
    @FXML private Label lblDetalleMinoristaSinCredito;
    @FXML private Label lblDetalleLimite;
    @FXML private Label lblDetallePuntos;
    @FXML private Label lblBadgeCumpleMes;
    @FXML private Label lblDetalleReglaFidelizacion;
    @FXML private Label lblDetalleSaldoDeudor;
    @FXML private Label lblDetalleComprobantes;
    @FXML private TableView<CompraClienteResumen> tblHistorialCompras;
    @FXML private TableColumn<CompraClienteResumen, String> colCompraFecha;
    @FXML private TableColumn<CompraClienteResumen, String> colCompraMonto;
    @FXML private TableColumn<CompraClienteResumen, String> colCompraProductos;
    @FXML private Button btnEstadoCuentaPdf;
    @FXML private Button btnEditarCliente;
    @FXML private Button btnCambiarEstado;

    @FXML private VBox modalCliente;
    @FXML private Label lblModalTitulo;
    @FXML private ComboBox<String> cbModalTipoCliente;
    @FXML private ComboBox<String> cbModalTipoDoc;
    @FXML private TextField txtModalNumeroDoc;
    @FXML private TextField txtModalRazonSocial;
    @FXML private TextField txtModalContacto;
    @FXML private TextField txtModalTelefono;
    @FXML private TextField txtModalEmail;
    @FXML private DatePicker dpModalFechaNacimiento;
    @FXML private HBox boxModalCredito;
    @FXML private TextField txtModalLimite;
    @FXML private TextField txtModalDiasCredito;
    @FXML private Label lblModalError;

    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final FinanzasService finanzasService = new FinanzasService();
    private final FidelizacionService fidelizacionService = new FidelizacionService();

    private final ObservableList<Cliente> listaTodosClientes = FXCollections.observableArrayList();
    private final ObservableList<Cliente> listaCorporativos = FXCollections.observableArrayList();
    private final ObservableList<Cliente> listaMinoristas = FXCollections.observableArrayList();
    private final ObservableList<Cliente> listaCorporativosPagina = FXCollections.observableArrayList();
    private final ObservableList<Cliente> listaMinoristasPagina = FXCollections.observableArrayList();
    private final ObservableList<CompraClienteResumen> historialCompras = FXCollections.observableArrayList();
    private PaginadorTablaHelper<Cliente> paginadorCorporativos;
    private PaginadorTablaHelper<Cliente> paginadorMinoristas;
    private Cliente clienteSeleccionado = null;
    private boolean modoEdicion = false;

    @FXML
    public void initialize() {
        paginadorCorporativos = new PaginadorTablaHelper<>(
                listaCorporativosPagina, lblPaginaCorp, btnAnteriorCorp, btnSiguienteCorp);
        paginadorMinoristas = new PaginadorTablaHelper<>(
                listaMinoristasPagina, lblPaginaMin, btnAnteriorMin, btnSiguienteMin);
        configurarColumnas();
        configurarColumnasMinoristas();
        configurarTablaHistorialCompras();
        configurarSeleccion();
        configurarBusqueda();
        configurarModal();
        actualizarReglaFidelizacionLabel();
        cargarClientesAsync();
    }

    private void actualizarReglaFidelizacionLabel() {
        if (lblDetalleReglaFidelizacion != null) {
            int ptsSol = ConfiguracionFidelizacion.getPuntosPorSol();
            int ptsCanje = ConfiguracionFidelizacion.getPuntosPorCanje();
            BigDecimal solesCanje = ConfiguracionFidelizacion.getDescuentoPorCanjeSoles();
            lblDetalleReglaFidelizacion.setText(String.format(
                    "S/ 1.00 = %d pt · %d pts = S/ %.2f desc.", ptsSol, ptsCanje, solesCanje));
        }
    }

    private void configurarColumnas() {
        colTipoDoc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipoDocumento()));
        colDocumento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroDocumento()));
        colRazonSocial.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocial()));
        colContacto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContacto() != null ? c.getValue().getContacto() : "-"));
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTelefono() != null ? c.getValue().getTelefono() : "-"));
        colLimiteCredito.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getLimiteCredito())));
        colSaldoDeudor.setCellValueFactory(c -> new SimpleStringProperty(String.format("S/ %.2f", c.getValue().getSaldoDeudor())));
        colPuntos.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPuntosAcumulados() + " pts"));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActivo() ? "ACTIVO" : "INACTIVO"));
        tblClientes.setItems(listaCorporativosPagina);
        tblClientes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colRazonSocial.setCellFactory(col -> crearTooltipCell());
        colContacto.setCellFactory(col -> crearTooltipCell());
    }

    private TableCell<Cliente, String> crearTooltipCell() {
        return new TableCell<>() {
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
        };
    }

    private void configurarColumnasMinoristas() {
        if (tblClientesMinoristas == null) {
            return;
        }
        colMinDni.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNumeroDocumento()));
        colMinNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRazonSocial()));
        colMinTelefono.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTelefono() != null && !c.getValue().getTelefono().isBlank() ? c.getValue().getTelefono() : "-"));
        colMinEmail.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEmail() != null && !c.getValue().getEmail().isBlank() ? c.getValue().getEmail() : "-"));
        colMinFechaNac.setCellValueFactory(c -> {
            LocalDate fn = c.getValue().getFechaNacimiento();
            return new SimpleStringProperty(fn != null ? fn.format(FMT_FECHA) : "-");
        });
        colMinPuntos.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPuntosAcumulados() + " pts"));
        colMinEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActivo() ? "ACTIVO" : "INACTIVO"));

        colMinNombre.setCellFactory(col -> crearTooltipCell());
        colMinEmail.setCellFactory(col -> crearTooltipCell());

        tblClientesMinoristas.setItems(listaMinoristasPagina);
        tblClientesMinoristas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void configurarTablaHistorialCompras() {
        if (tblHistorialCompras == null) {
            return;
        }
        colCompraFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFechaHora() != null ? c.getValue().getFechaHora().format(FMT_FECHA_HORA) : "-"));
        colCompraMonto.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("S/ %.2f", c.getValue().getMontoTotal())));
        colCompraProductos.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getResumenProductos() != null && !c.getValue().getResumenProductos().isBlank()
                        ? c.getValue().getResumenProductos()
                        : "Consumo POS"));
        colCompraProductos.setCellFactory(col -> new TableCell<>() {
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
        tblHistorialCompras.setItems(historialCompras);
        tblHistorialCompras.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void configurarSeleccion() {
        tblClientes.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                seleccionarCliente(newV);
            }
        });
        if (tblClientesMinoristas != null) {
            tblClientesMinoristas.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    seleccionarCliente(newV);
                }
            });
        }
        if (tabPaneClientes != null) {
            tabPaneClientes.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                actualizarContadorTotalPorTab();
                if (newTab == tabMinoristas && !listaMinoristas.isEmpty()) {
                    Cliente sel = tblClientesMinoristas.getSelectionModel().getSelectedItem();
                    if (sel == null) {
                        tblClientesMinoristas.getSelectionModel().selectFirst();
                        sel = tblClientesMinoristas.getSelectionModel().getSelectedItem();
                    }
                    if (sel != null) {
                        seleccionarCliente(sel);
                    }
                } else if (newTab == tabCorporativos && !listaCorporativos.isEmpty()) {
                    Cliente sel = tblClientes.getSelectionModel().getSelectedItem();
                    if (sel == null) {
                        tblClientes.getSelectionModel().selectFirst();
                        sel = tblClientes.getSelectionModel().getSelectedItem();
                    }
                    if (sel != null) {
                        seleccionarCliente(sel);
                    }
                }
            });
        }
    }

    private void configurarBusqueda() {
        txtBuscar.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                aplicarListasDesdeMaestro(listaTodosClientes);
            } else {
                String f = newV.toLowerCase().trim();
                List<Cliente> filtradosCorp = listaTodosClientes.stream()
                        .filter(c -> !c.isMinorista())
                        .filter(c -> coincideBusqueda(c, f))
                        .collect(Collectors.toList());
                List<Cliente> filtradosMin = listaTodosClientes.stream()
                        .filter(Cliente::isMinorista)
                        .filter(c -> coincideBusqueda(c, f))
                        .collect(Collectors.toList());
                actualizarListasPaginadas(filtradosCorp, filtradosMin);
                actualizarContadorTotalPorTab();
            }
        });
    }

    private boolean coincideBusqueda(Cliente c, String f) {
        String doc = c.getNumeroDocumento() != null ? c.getNumeroDocumento().toLowerCase() : "";
        String nom = c.getRazonSocial() != null ? c.getRazonSocial().toLowerCase() : "";
        String tel = c.getTelefono() != null ? c.getTelefono().toLowerCase() : "";
        return doc.contains(f) || nom.contains(f) || tel.contains(f);
    }

    private void configurarModal() {
        cbModalTipoDoc.setItems(FXCollections.observableArrayList("RUC", "DNI"));
        cbModalTipoDoc.setValue("RUC");
        if (cbModalTipoCliente != null) {
            cbModalTipoCliente.setItems(FXCollections.observableArrayList("CORPORATIVO", "MINORISTA"));
            cbModalTipoCliente.setValue("CORPORATIVO");
            cbModalTipoCliente.valueProperty().addListener((obs, oldV, newV) -> aplicarEstiloModalSegmento(newV));
        }
    }

    private void aplicarEstiloModalSegmento(String segmento) {
        boolean esMin = "MINORISTA".equalsIgnoreCase(segmento);
        if (esMin) {
            cbModalTipoDoc.setValue("DNI");
            if (!modoEdicion) {
                txtModalLimite.setText("0.00");
                txtModalDiasCredito.setText("0");
            }
        } else if (!modoEdicion && "DNI".equalsIgnoreCase(cbModalTipoDoc.getValue())) {
            cbModalTipoDoc.setValue("RUC");
            txtModalLimite.setText("5000.00");
            txtModalDiasCredito.setText("30");
        }
        if (boxModalCredito != null) {
            boxModalCredito.setVisible(!esMin);
            boxModalCredito.setManaged(!esMin);
        }
    }

    public void cargarClientesAsync() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    return clienteDAO.listarTodos(conn);
                }
            },
            todos -> {
                listaTodosClientes.setAll(todos);
                aplicarListasDesdeMaestro(todos);
            },
            ex -> LOGGER.log(Level.WARNING, "Error al cargar clientes", ex)
        );
    }

    /**
     * Carga síncrona determinística de clientes para pruebas de integración y capturas (v5.0.0).
     */
    public void cargarDatosSincrono() {
        try (Connection conn = ConexionBD.getConnection()) {
            List<Cliente> todos = clienteDAO.listarTodos(conn);
            listaTodosClientes.setAll(todos);
            aplicarListasDesdeMaestro(todos);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar clientes síncronamente", ex);
        }
    }

    private void aplicarListasDesdeMaestro(List<Cliente> todos) {
        List<Cliente> corp = todos.stream().filter(c -> !c.isMinorista()).collect(Collectors.toList());
        List<Cliente> min = todos.stream().filter(Cliente::isMinorista).collect(Collectors.toList());
        actualizarListasPaginadas(corp, min);
        actualizarContadorTotalPorTab();
        boolean tabMinActivo = tabPaneClientes != null && tabMinoristas != null
                && tabPaneClientes.getSelectionModel().getSelectedItem() == tabMinoristas;
        if (tabMinActivo && !listaMinoristas.isEmpty()) {
            tblClientesMinoristas.getSelectionModel().selectFirst();
            seleccionarCliente(listaMinoristas.get(0));
        } else if (!listaCorporativos.isEmpty()) {
            tblClientes.getSelectionModel().selectFirst();
            seleccionarCliente(listaCorporativos.get(0));
        } else if (!listaMinoristas.isEmpty()) {
            tblClientesMinoristas.getSelectionModel().selectFirst();
            seleccionarCliente(listaMinoristas.get(0));
        }
    }

    private void actualizarListasPaginadas(List<Cliente> corporativos, List<Cliente> minoristas) {
        listaCorporativos.setAll(corporativos);
        listaMinoristas.setAll(minoristas);
        if (paginadorCorporativos != null) {
            paginadorCorporativos.setDatos(corporativos);
        } else {
            listaCorporativosPagina.setAll(corporativos);
        }
        if (paginadorMinoristas != null) {
            paginadorMinoristas.setDatos(minoristas);
        } else {
            listaMinoristasPagina.setAll(minoristas);
        }
    }

    /**
     * Retrocede de página en la tabla de clientes corporativos.
     */
    @FXML
    public void handlePaginaAnteriorCorp() {
        if (paginadorCorporativos != null) {
            paginadorCorporativos.paginaAnterior();
        }
    }

    /**
     * Avanza de página en la tabla de clientes corporativos.
     */
    @FXML
    public void handlePaginaSiguienteCorp() {
        if (paginadorCorporativos != null) {
            paginadorCorporativos.paginaSiguiente();
        }
    }

    /**
     * Retrocede de página en la tabla de clientes minoristas.
     */
    @FXML
    public void handlePaginaAnteriorMin() {
        if (paginadorMinoristas != null) {
            paginadorMinoristas.paginaAnterior();
        }
    }

    /**
     * Avanza de página en la tabla de clientes minoristas.
     */
    @FXML
    public void handlePaginaSiguienteMin() {
        if (paginadorMinoristas != null) {
            paginadorMinoristas.paginaSiguiente();
        }
    }

    /**
     * Carga clientes directamente en memoria para pruebas unitarias y captura de paginación.
     *
     * @param clientes Lista completa de clientes a distribuir y paginar.
     */
    public void cargarClientesDirectoParaPaginacion(List<Cliente> clientes) {
        List<Cliente> datos = clientes != null ? clientes : List.of();
        listaTodosClientes.setAll(datos);
        aplicarListasDesdeMaestro(datos);
    }

    private void actualizarContadorTotalPorTab() {
        if (tabPaneClientes != null && tabMinoristas != null
                && tabPaneClientes.getSelectionModel().getSelectedItem() == tabMinoristas) {
            lblTotalClientes.setText(listaMinoristas.size() + " Minoristas");
        } else {
            lblTotalClientes.setText(listaCorporativos.size() + " Corporativos · " + listaMinoristas.size() + " Minoristas");
        }
    }

    public void seleccionarTabMinoristas() {
        if (tabPaneClientes != null && tabMinoristas != null) {
            tabPaneClientes.getSelectionModel().select(tabMinoristas);
            if (!listaMinoristas.isEmpty()) {
                tblClientesMinoristas.getSelectionModel().selectFirst();
                seleccionarCliente(listaMinoristas.get(0));
            }
        }
    }

    public void seleccionarCliente(Cliente c) {
        if (c == null) {
            return;
        }
        this.clienteSeleccionado = c;
        if (lblBadgeTipoCliente != null) {
            lblBadgeTipoCliente.setText(c.isMinorista() ? "MINORISTA DNI" : "CORPORATIVO RUC");
        }
        lblDetalleRazonSocial.setText(c.getRazonSocial());
        lblDetalleDocumento.setText(c.getTipoDocumento() + ": " + c.getNumeroDocumento());
        lblDetalleContacto.setText((c.getContacto() != null && !c.getContacto().isBlank() ? c.getContacto() : "-")
                + " (" + (c.getTelefono() != null && !c.getTelefono().isBlank() ? c.getTelefono() : "-") + ")");
        lblDetalleEmail.setText(c.getEmail() != null && !c.getEmail().isBlank() ? c.getEmail() : "-");
        if (lblDetalleCumpleanos != null) {
            lblDetalleCumpleanos.setText(c.getFechaNacimiento() != null ? c.getFechaNacimiento().format(FMT_FECHA) : "-");
        }
        if (lblBadgeCumpleMes != null) {
            boolean cumple = c.esMesDeCumpleanos();
            lblBadgeCumpleMes.setVisible(cumple);
            lblBadgeCumpleMes.setManaged(cumple);
        }
        boolean esMin = c.isMinorista();
        if (boxCreditoCorpDetalle != null) {
            boxCreditoCorpDetalle.setVisible(!esMin);
            boxCreditoCorpDetalle.setManaged(!esMin);
        }
        if (lblDetalleMinoristaSinCredito != null) {
            lblDetalleMinoristaSinCredito.setVisible(esMin);
            lblDetalleMinoristaSinCredito.setManaged(esMin);
        }
        lblDetalleLimite.setText(esMin
                ? "Minorista (Sin línea de crédito)"
                : "S/ " + String.format("%.2f", c.getLimiteCredito()) + " (" + c.getDiasCredito() + " días)");
        lblDetallePuntos.setText(c.getPuntosAcumulados() + " Puntos Acumulados");
        lblDetalleSaldoDeudor.setText(String.format("S/ %.2f", c.getSaldoDeudor()));
        lblDetalleComprobantes.setText(c.getComprobantesPendientes() + " facturas pendientes de cobro");
        cargarHistorialComprasAsync(c.getIdCliente());
    }

    private void cargarHistorialComprasAsync(int idCliente) {
        if (tblHistorialCompras == null || idCliente <= 0) {
            historialCompras.clear();
            return;
        }
        UiAsyncExecutor.ejecutarAsync(
                () -> fidelizacionService.listarUltimasComprasCliente(idCliente, 10),
                compras -> historialCompras.setAll(compras),
                ex -> LOGGER.log(Level.FINE, "Error cargando historial de compras del cliente #" + idCliente, ex)
        );
    }

    /**
     * Carga síncrona de últimas 10 compras para verificación determinística en tests/capturas.
     */
    public void cargarHistorialComprasSincrono(int idCliente) {
        if (tblHistorialCompras == null || idCliente <= 0) {
            historialCompras.clear();
            return;
        }
        historialCompras.setAll(fidelizacionService.listarUltimasComprasCliente(idCliente, 10));
    }

    @FXML
    public void handleAbrirModalNuevo() {
        boolean tabMin = tabPaneClientes != null && tabMinoristas != null
                && tabPaneClientes.getSelectionModel().getSelectedItem() == tabMinoristas;
        abrirModalConSegmento(tabMin ? "MINORISTA" : "CORPORATIVO");
    }

    @FXML
    public void handleAbrirModalNuevoMinorista() {
        abrirModalConSegmento("MINORISTA");
    }

    private void abrirModalConSegmento(String segmento) {
        modoEdicion = false;
        boolean esMin = "MINORISTA".equalsIgnoreCase(segmento);
        lblModalTitulo.setText(esMin ? "Registrar Cliente Minorista (DNI)" : "Registrar Cliente Corporativo");
        if (cbModalTipoCliente != null) {
            cbModalTipoCliente.setValue(esMin ? "MINORISTA" : "CORPORATIVO");
        }
        cbModalTipoDoc.setValue(esMin ? "DNI" : "RUC");
        txtModalNumeroDoc.clear();
        txtModalRazonSocial.clear();
        txtModalContacto.clear();
        txtModalTelefono.clear();
        txtModalEmail.clear();
        if (dpModalFechaNacimiento != null) {
            dpModalFechaNacimiento.setValue(null);
        }
        txtModalLimite.setText(esMin ? "0.00" : "5000.00");
        txtModalDiasCredito.setText(esMin ? "0" : "30");
        if (boxModalCredito != null) {
            boxModalCredito.setVisible(!esMin);
            boxModalCredito.setManaged(!esMin);
        }
        lblModalError.setText("");
        modalCliente.setVisible(true);
    }

    @FXML
    public void handleEditarSeleccionado() {
        if (clienteSeleccionado == null) return;
        modoEdicion = true;
        lblModalTitulo.setText("Editar Cliente #" + clienteSeleccionado.getIdCliente());
        if (cbModalTipoCliente != null) {
            cbModalTipoCliente.setValue(clienteSeleccionado.getTipoCliente());
        }
        cbModalTipoDoc.setValue(clienteSeleccionado.getTipoDocumento());
        txtModalNumeroDoc.setText(clienteSeleccionado.getNumeroDocumento());
        txtModalRazonSocial.setText(clienteSeleccionado.getRazonSocial());
        txtModalContacto.setText(clienteSeleccionado.getContacto());
        txtModalTelefono.setText(clienteSeleccionado.getTelefono());
        txtModalEmail.setText(clienteSeleccionado.getEmail());
        if (dpModalFechaNacimiento != null) {
            dpModalFechaNacimiento.setValue(clienteSeleccionado.getFechaNacimiento());
        }
        txtModalLimite.setText(String.valueOf(clienteSeleccionado.getLimiteCredito()));
        txtModalDiasCredito.setText(String.valueOf(clienteSeleccionado.getDiasCredito()));
        if (boxModalCredito != null) {
            boolean esMin = clienteSeleccionado.isMinorista();
            boxModalCredito.setVisible(!esMin);
            boxModalCredito.setManaged(!esMin);
        }
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
        String segmento = cbModalTipoCliente != null && cbModalTipoCliente.getValue() != null
                ? cbModalTipoCliente.getValue()
                : ("DNI".equalsIgnoreCase(cbModalTipoDoc.getValue()) ? "MINORISTA" : "CORPORATIVO");
        String tipoDoc = cbModalTipoDoc.getValue() != null ? cbModalTipoDoc.getValue() : ("MINORISTA".equals(segmento) ? "DNI" : "RUC");

        if (num.isEmpty()) {
            lblModalError.setText("El número de documento es obligatorio");
            return;
        }
        if ("DNI".equalsIgnoreCase(tipoDoc) && !num.matches("\\d{8}")) {
            lblModalError.setText("El DNI debe contener exactamente 8 dígitos numéricos");
            return;
        }
        if (razon.isEmpty()) {
            lblModalError.setText("La razón social o nombre completo es obligatorio");
            return;
        }
        double lim = 0.0;
        int dias = "MINORISTA".equalsIgnoreCase(segmento) ? 0 : 30;
        try {
            lim = Double.parseDouble(txtModalLimite.getText().trim());
            dias = Integer.parseInt(txtModalDiasCredito.getText().trim());
        } catch (Exception ignored) {
        }

        final String contacto = txtModalContacto.getText() != null ? txtModalContacto.getText().trim() : "";
        final String telefono = txtModalTelefono.getText() != null ? txtModalTelefono.getText().trim() : "";
        final String email = txtModalEmail.getText() != null ? txtModalEmail.getText().trim() : "";
        final LocalDate fechaNacimiento = dpModalFechaNacimiento != null ? dpModalFechaNacimiento.getValue() : null;
        final double limFinal = lim;
        final int diasFinal = dias;
        final boolean edicion = modoEdicion && clienteSeleccionado != null;
        final Cliente objEdicion = clienteSeleccionado;

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (edicion) {
                        objEdicion.setTipoCliente(segmento);
                        objEdicion.setTipoDocumento(tipoDoc);
                        objEdicion.setNumeroDocumento(num);
                        objEdicion.setRazonSocial(razon);
                        objEdicion.setContacto(contacto.isEmpty() ? razon : contacto);
                        objEdicion.setTelefono(telefono);
                        objEdicion.setEmail(email);
                        objEdicion.setFechaNacimiento(fechaNacimiento);
                        objEdicion.setLimiteCredito(limFinal);
                        objEdicion.setDiasCredito(diasFinal);
                        clienteDAO.actualizar(conn, objEdicion);
                    } else {
                        Cliente nuevo = new Cliente();
                        nuevo.setTipoCliente(segmento);
                        nuevo.setTipoDocumento(tipoDoc);
                        nuevo.setNumeroDocumento(num);
                        nuevo.setRazonSocial(razon);
                        nuevo.setContacto(contacto.isEmpty() ? razon : contacto);
                        nuevo.setTelefono(telefono);
                        nuevo.setEmail(email);
                        nuevo.setFechaNacimiento(fechaNacimiento);
                        nuevo.setLimiteCredito(limFinal);
                        nuevo.setDiasCredito(diasFinal);
                        clienteDAO.insertar(conn, nuevo);
                    }
                }
            },
            () -> {
                modalCliente.setVisible(false);
                cargarClientesAsync();
            },
            ex -> lblModalError.setText("Error al guardar: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    public ObservableList<Cliente> getListaMinoristas() {
        return listaMinoristas;
    }

    public ObservableList<CompraClienteResumen> getHistorialCompras() {
        return historialCompras;
    }

    public TableView<Cliente> getTblClientesMinoristas() {
        return tblClientesMinoristas;
    }

    public TableView<CompraClienteResumen> getTblHistorialCompras() {
        return tblHistorialCompras;
    }

    @FXML
    public void handleCambiarEstado() {
        if (clienteSeleccionado == null) return;
        final int idCli = clienteSeleccionado.getIdCliente();
        final boolean nuevoEstado = !clienteSeleccionado.isActivo();
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    clienteDAO.cambiarEstado(conn, idCli, nuevoEstado);
                }
            },
            this::cargarClientesAsync,
            ex -> LOGGER.log(Level.WARNING, "Error al cambiar estado", ex)
        );
    }

    @FXML
    public void handleGenerarEstadoCuentaPdf() {
        if (clienteSeleccionado == null) return;
        final int idCli = clienteSeleccionado.getIdCliente();
        final String numDoc = clienteSeleccionado.getNumeroDocumento();
        Optional<File> elegidoOpt = ExportFileChooserHelper.elegirDestinoExportacion(
                btnEstadoCuentaPdf != null ? btnEstadoCuentaPdf : lblDetalleComprobantes,
                "Guardar Estado de Cuenta en PDF",
                "ESTADO_CUENTA_" + numDoc + ".pdf",
                FormatoExportacion.PDF);
        if (elegidoOpt.isEmpty()) {
            if (lblDetalleComprobantes != null) {
                lblDetalleComprobantes.setText("Exportación de estado de cuenta cancelada.");
            }
            return;
        }
        final File doc = elegidoOpt.get();
        if (btnEstadoCuentaPdf != null) {
            btnEstadoCuentaPdf.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                if (doc.getParentFile() != null) {
                    Files.createDirectories(doc.getParentFile().toPath());
                }
                finanzasService.generarEstadoCuentaClientePdf(idCli, doc);
                return doc;
            },
            archivo -> {
                if (btnEstadoCuentaPdf != null) {
                    btnEstadoCuentaPdf.setDisable(false);
                }
                lblDetalleComprobantes.setText("✓ Estado de cuenta generado: " + archivo.getName());
            },
            ex -> {
                if (btnEstadoCuentaPdf != null) {
                    btnEstadoCuentaPdf.setDisable(false);
                }
                lblDetalleComprobantes.setText("Error PDF: " + (ex != null ? ex.getMessage() : "desconocido"));
            }
        );
    }
}
