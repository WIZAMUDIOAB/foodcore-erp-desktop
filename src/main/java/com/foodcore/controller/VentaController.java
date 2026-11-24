package com.foodcore.controller;

import com.foodcore.App;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Cliente;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.model.Caja;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.VentaService;
import com.foodcore.service.VentaPosConsultaService;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;
import com.foodcore.util.AnimationUtils;

import com.foodcore.util.ConfiguracionFidelizacion;
import com.foodcore.util.DialogService;
import com.foodcore.util.FormatoPersonalizacionUtil;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.ToastService;
import com.foodcore.util.UiAsyncExecutor;
import com.foodcore.controller.venta.DefaultVentaCarritoHandler;
import com.foodcore.controller.venta.DefaultVentaCobroCoordinator;
import com.foodcore.controller.venta.DefaultVentaFidelizacionCoordinator;
import com.foodcore.controller.venta.DefaultVentaMesasCanalCoordinator;
import com.foodcore.controller.venta.VentaCarritoHandler;
import com.foodcore.controller.venta.VentaCatalogoPresenter;
import com.foodcore.controller.venta.VentaCobroCoordinator;
import com.foodcore.controller.venta.VentaFidelizacionCoordinator;
import com.foodcore.controller.venta.VentaMesasCanalCoordinator;
import com.foodcore.controller.venta.VentaMesasCanalUiRenderer;
import com.foodcore.controller.venta.VentaPosModalHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Punto de Venta (POS) según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§6),
 * SPEC-FOODCORE-v1.2 (§2.2, §3.1, §3.2, §5) y v4.4.0 (§2, §5):
 * Split 35% orden / 65% catálogo y 12 mesas multicanal, badges [MESA XX] / [KIOSCO] / [DELIVERY],
 * auto-refresco 5s, fotos de platillos, división de cuenta, descuentos RBAC y anulaciones auditadas.
 */
public class VentaController {

    private static final Logger LOGGER = Logger.getLogger(VentaController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static final int TOTAL_MESAS_SALON = 12;
    public static final String MODALIDAD_SPLIT_2 = "Dividir en 2 partes iguales (50% Efectivo / 50% Tarjeta)";
    public static final String MODALIDAD_SPLIT_4 = "Dividir en 4 partes iguales (25% c/u)";
    public static final String MODALIDAD_SPLIT_ITEMS = "Dividir por ítems (Subcuenta A y Subcuenta B)";
    private static final VentaMesasCanalCoordinator CANAL_STATICS = new DefaultVentaMesasCanalCoordinator(null);
    private static final VentaCobroCoordinator COBRO_STATICS = new DefaultVentaCobroCoordinator(
            new VentaService(), new AutorizacionService());

    @FXML private TabPane tabPanePosCatalogo;
    @FXML private Tab tabCatalogo;
    @FXML private Tab tabMesas;
    @FXML private TilePane tileProductos;
    @FXML private TilePane tileMesasPos;
    @FXML private FlowPane flowPedidosMulticanal;
    @FXML private HBox boxOrdenesEntrantes;
    @FXML private TextField txtBuscar;
    @FXML private HBox boxCategorias;
    @FXML private TableView<DetalleVenta> tblDetalle;
    @FXML private TableColumn<DetalleVenta, String> colProducto;
    @FXML private TableColumn<DetalleVenta, Integer> colCantidad;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colPrecio;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colSubtotal;

    @FXML private Label lblOrdenHeader;
    @FXML private Label lblCanalBadge;
    @FXML private Label lblMozoHeader;
    @FXML private Label lblResumenMesasPos;
    @FXML private Label lblAutoRefreshStatus;
    @FXML private Label lblSubtotal;
    @FXML private Label lblIgv;
    @FXML private HBox rowDescuento;
    @FXML private Label lblDescuento;
    @FXML private HBox rowDescuentoPuntos;
    @FXML private Label lblDescuentoPuntos;
    @FXML private Label lblTotal;

    @FXML private TextField txtBuscarClientePos;
    @FXML private Button btnBuscarClientePos;
    @FXML private Button btnNuevoClientePos;
    @FXML private Button btnLimpiarClientePos;
    @FXML private HBox rowClienteAsociadoPos;
    @FXML private Label lblClientePosNombre;
    @FXML private Label lblClientePosPuntos;
    @FXML private Button btnCanjearPuntosPos;
    @FXML private HBox rowAlertaCumplePos;
    @FXML private Label lblAlertaCumplePos;
    @FXML private Button btnAplicarCumplePos;

    @FXML private Button btnVerMesasPos;
    @FXML private Button btnDividirCuenta;
    @FXML private Button btnDescuento;
    @FXML private Button btnAnularVenta;
    @FXML private Button btnEnviarCocina;
    @FXML private Button btnEfectivo;
    @FXML private Button btnTarjeta;
    @FXML private Button btnYape;
    @FXML private Button btnPreCuenta;
    @FXML private Button btnNotaItem;

    private final ObservableList<DetalleVenta> itemsVenta = FXCollections.observableArrayList();
    private final List<Producto> todosLosProductos = new ArrayList<>();
    private final List<Pedido> pedidosActivosMulticanal = new ArrayList<>();
    private final Map<String, Pedido> pedidosPorMesa = new LinkedHashMap<>();

    private PedidoService pedidoService = new PedidoService();
    private final VentaService ventaService = new VentaService();
    private final PromocionService promocionService = new PromocionService();
    private final PersonalizacionProductoService personalizacionService = new PersonalizacionProductoServiceImpl();
    private final VentaPosConsultaService posConsultaService = new VentaPosConsultaService();
    private final FidelizacionService fidelizacionService = new FidelizacionService();
    private final AutorizacionService autorizacionService = new AutorizacionService();

    private final VentaMesasCanalCoordinator mesasCanalCoordinator;
    private final VentaMesasCanalUiRenderer mesasCanalUiRenderer;
    private final VentaCatalogoPresenter catalogoPresenter;
    private final VentaCarritoHandler carritoHandler;
    private final VentaCobroCoordinator cobroCoordinator;
    private final VentaFidelizacionCoordinator fidelizacionCoordinator;

    public VentaController() {
        this.mesasCanalCoordinator = new DefaultVentaMesasCanalCoordinator(posConsultaService);
        this.mesasCanalUiRenderer = new VentaMesasCanalUiRenderer();
        this.catalogoPresenter = new VentaCatalogoPresenter(posConsultaService);
        this.carritoHandler = new DefaultVentaCarritoHandler(promocionService, ventaService);
        this.cobroCoordinator = new DefaultVentaCobroCoordinator(ventaService, autorizacionService);
        this.fidelizacionCoordinator = new DefaultVentaFidelizacionCoordinator(posConsultaService, fidelizacionService);
    }

    public VentaController(VentaMesasCanalCoordinator mesasCanalCoordinator,
            VentaMesasCanalUiRenderer mesasCanalUiRenderer, VentaCatalogoPresenter catalogoPresenter,
            VentaCarritoHandler carritoHandler, VentaCobroCoordinator cobroCoordinator,
            VentaFidelizacionCoordinator fidelizacionCoordinator) {
        this.mesasCanalCoordinator = java.util.Objects.requireNonNull(mesasCanalCoordinator);
        this.mesasCanalUiRenderer = java.util.Objects.requireNonNull(mesasCanalUiRenderer);
        this.catalogoPresenter = java.util.Objects.requireNonNull(catalogoPresenter);
        this.carritoHandler = java.util.Objects.requireNonNull(carritoHandler);
        this.cobroCoordinator = java.util.Objects.requireNonNull(cobroCoordinator);
        this.fidelizacionCoordinator = java.util.Objects.requireNonNull(fidelizacionCoordinator);
    }

    private boolean descuentoManualFijado = false;
    private final AtomicBoolean cobroEnProgreso = new AtomicBoolean(false);
    private BigDecimal descuentoActivo = BigDecimal.ZERO;
    private BigDecimal descuentoPuntosActivo = BigDecimal.ZERO;
    private int puntosARedimirPos = 0;
    private Cliente clienteSeleccionadoPos = null;
    private String ultimoMensajeAlertaCumpleanos = null;
    private volatile String ultimaAlertaTitulo = null;
    private Boolean respuestaAutomaticaCumpleanosTest = null;
    private boolean modoCobroDirectoTest = false;
    private boolean confirmacionEscapeAutomatica = false;
    private int categoriaFiltroSeleccionada = 0;

    private String mesaSeleccionadaActual = "01";
    private String canalSeleccionadoActual = "MESA";
    private Integer pedidoActivoId = null;
    private Timeline autoRefreshTimeline;
    private Scene escenaAtajosRegistrada;
    private EventHandler<KeyEvent> keyEventFilter;

    @FXML
    public void initialize() {
        Usuario usr = AuthService.getUsuarioSesionActual();
        if (usr != null && lblMozoHeader != null) {
            String nombre = usr.getNombreEmpleado();
            if (nombre == null || nombre.isBlank()) {
                nombre = usr.getUsername();
            }
            lblMozoHeader.setText("Mozo/Cajero: " + nombre + " · Terminal POS-01");
            aplicarRestriccionesRolPOS(usr);
        }

        colProducto.setCellValueFactory(c -> {
            String nom = FormatoPersonalizacionUtil.formatearDescripcionItem(c.getValue());
            if (c.getValue().getNotasPreparacion() != null && !c.getValue().getNotasPreparacion().isBlank()) {
                nom += " [Nota: " + c.getValue().getNotasPreparacion().trim() + "]";
            }
            return new SimpleStringProperty(nom);
        });
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
        colSubtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getSubtotal()));

        configurarCeldasMonoespaciadas();

        tblDetalle.setItems(itemsVenta);

        AnimationUtils.attachTactileFeedback(btnDividirCuenta);
        AnimationUtils.attachTactileFeedback(btnDescuento);
        AnimationUtils.attachTactileFeedback(btnAnularVenta);
        AnimationUtils.attachTactileFeedback(btnEnviarCocina);
        AnimationUtils.attachTactileFeedback(btnEfectivo);
        AnimationUtils.attachTactileFeedback(btnTarjeta);
        AnimationUtils.attachTactileFeedback(btnYape);
        AnimationUtils.attachTactileFeedback(btnBuscarClientePos);
        AnimationUtils.attachTactileFeedback(btnNuevoClientePos);
        AnimationUtils.attachTactileFeedback(btnCanjearPuntosPos);

        tblDetalle.setRowFactory(tv -> {
            TableRow<DetalleVenta> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    DetalleVenta dv = row.getItem();
                    dv.setCantidad(dv.getCantidad() + 1);
                    dv.setSubtotal(dv.getPrecioUnitario().multiply(BigDecimal.valueOf(dv.getCantidad())));
                    tblDetalle.refresh();
                    recalcularTotales();
                }
            });
            return row;
        });

        txtBuscar.textProperty().addListener((obs, oldVal, newVal) -> filtrarCatalogo());

        cargarCategorias();
        cargarCatalogoProductosAsync();
        refrescarMesasYCanalesAsync();
        iniciarAutoRefrescoPos();

        Platform.runLater(this::registrarAtajosTeclado);
    }

    /**
     * Inicia polling de 5 segundos para actualizar las 12 mesas y órdenes de Kiosco/Delivery/PWA (v4.4.0 §2).
     */
    private void iniciarAutoRefrescoPos() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), ev -> refrescarMesasYCanalesAsync()));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    public void stopTimeline() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        desregistrarAtajosTeclado();
    }

    private void desregistrarAtajosTeclado() {
        if (escenaAtajosRegistrada != null && keyEventFilter != null) {
            escenaAtajosRegistrada.removeEventFilter(KeyEvent.KEY_PRESSED, keyEventFilter);
        }
        escenaAtajosRegistrada = null;
        keyEventFilter = null;
    }

    public boolean isAtajoTecladoRegistrado() {
        return escenaAtajosRegistrada != null && keyEventFilter != null;
    }

    @FXML
    public void handleMostrarTabMesas(ActionEvent event) {
        if (tabPanePosCatalogo != null && tabMesas != null) {
            tabPanePosCatalogo.getSelectionModel().select(tabMesas);
        }
        refrescarMesasYCanalesAsync();
    }

    @FXML
    public void handleRefrescarMesasPos(ActionEvent event) {
        refrescarMesasYCanalesAsync();
    }

    private void refrescarMesasYCanalesAsync() {
        UiAsyncExecutor.ejecutarAsync(
            this::consultarPedidosActivosDesdeBD,
            this::aplicarPedidosActivosEnUI,
            ex -> LOGGER.log(Level.FINE, "Aviso consultando pedidos multicanal en POS", ex)
        );
    }

    public void refrescarMesasYCanalesSincrono() {
        List<Pedido> activos = consultarPedidosActivosDesdeBD();
        aplicarPedidosActivosEnUI(activos);
    }

    private List<Pedido> consultarPedidosActivosDesdeBD() {
        try {
            return posConsultaService.listarPedidosActivosConDetalles();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso consultando pedidos multicanal en POS", e);
            return new ArrayList<>();
        }
    }

    public void aplicarPedidosActivosEnUI(List<Pedido> activos) {
        pedidosActivosMulticanal.clear();
        if (activos != null) {
            pedidosActivosMulticanal.addAll(activos);
        }

        pedidosPorMesa.clear();
        VentaMesasCanalCoordinator.ResumenCanalesPos resumen = mesasCanalCoordinator.particionarPedidosActivos(pedidosActivosMulticanal);
        pedidosPorMesa.putAll(resumen.pedidosPorMesa());
        renderizarGridMesasPos();
        renderizarPedidosMulticanal(resumen.ordenesExternas());
        renderizarBarraRapidaOrdenes(pedidosActivosMulticanal);
    }

    public static String determinarCanalPedido(Pedido p) {
        return CANAL_STATICS.determinarCanalPedido(p);
    }

    public static String construirTextoBadgeCanal(Pedido p) {
        return CANAL_STATICS.construirTextoBadgeCanal(p);
    }

    private static String normalizarNumeroMesa(String raw) {
        return CANAL_STATICS.normalizarNumeroMesa(raw);
    }

    private void renderizarGridMesasPos() {
        mesasCanalUiRenderer.renderizarGridMesasPos(tileMesasPos, lblResumenMesasPos, pedidosPorMesa,
                TOTAL_MESAS_SALON, this::seleccionarMesa);
    }

    private void renderizarPedidosMulticanal(List<Pedido> externos) {
        mesasCanalUiRenderer.renderizarPedidosMulticanal(flowPedidosMulticanal, externos, mesasCanalCoordinator, pedido -> {
            cargarPedidoEnPOS(pedido);
            if (tabPanePosCatalogo != null && tabCatalogo != null) tabPanePosCatalogo.getSelectionModel().select(tabCatalogo);
        });
    }

    private void renderizarBarraRapidaOrdenes(List<Pedido> activos) {
        mesasCanalUiRenderer.renderizarBarraRapidaOrdenes(boxOrdenesEntrantes, activos,
                mesaSeleccionadaActual, mesasCanalCoordinator, this::cargarPedidoEnPOS);
    }

    /**
     * Selecciona una mesa del salón (01..12). Si está ocupada, carga su pedido activo en el ticket POS.
     */
    public void seleccionarMesa(String numeroMesa) {
        String numNorm = normalizarNumeroMesa(numeroMesa);
        this.mesaSeleccionadaActual = numNorm;
        this.canalSeleccionadoActual = "MESA";
        Pedido activo = pedidosPorMesa.get(numNorm);
        if (activo != null) {
            cargarPedidoEnPOS(activo);
        } else {
            this.pedidoActivoId = null;
            itemsVenta.clear();
            descuentoActivo = BigDecimal.ZERO;
            descuentoPuntosActivo = BigDecimal.ZERO;
            puntosARedimirPos = 0;
            descuentoManualFijado = false;
            actualizarCabeceraCanal("Mesa " + numNorm + " · Salón", "[MESA " + numNorm + "]", "MESA");
            recalcularTotales();
        }
        if (tabPanePosCatalogo != null && tabCatalogo != null) {
            tabPanePosCatalogo.getSelectionModel().select(tabCatalogo);
        }
    }

    /**
     * Carga un pedido existente (MESA, KIOSCO o DELIVERY) dentro del ticket activo del POS.
     */
    public void cargarPedidoEnPOS(Pedido pedido) {
        if (pedido == null) {
            return;
        }
        this.pedidoActivoId = pedido.getIdPedido() > 0 ? pedido.getIdPedido() : null;
        this.canalSeleccionadoActual = determinarCanalPedido(pedido);
        String badge = construirTextoBadgeCanal(pedido);

        if ("KIOSCO".equals(canalSeleccionadoActual)) {
            this.mesaSeleccionadaActual = pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "T-101";
            actualizarCabeceraCanal("Kiosco · " + this.mesaSeleccionadaActual, badge, "KIOSCO");
        } else if ("DELIVERY".equals(canalSeleccionadoActual)) {
            String cliente = pedido.getClienteNombre() != null && !pedido.getClienteNombre().isBlank()
                    ? pedido.getClienteNombre() : "Pedido #" + pedido.getIdPedido();
            this.mesaSeleccionadaActual = pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "DL-01";
            actualizarCabeceraCanal("Delivery · " + cliente, badge, "DELIVERY");
        } else {
            String mesaNorm = normalizarNumeroMesa(pedido.getNumeroMesa());
            this.mesaSeleccionadaActual = mesaNorm;
            actualizarCabeceraCanal("Mesa " + mesaNorm + " · Salón", badge, "MESA");
        }

        List<DetallePedido> detallesIniciales = pedido.getDetalles();
        aplicarDetallesPedidoEnPOS(detallesIniciales);
        if ((detallesIniciales == null || detallesIniciales.isEmpty()) && pedido.getIdPedido() > 0) {
            final int idObjetivo = pedido.getIdPedido();
            UiAsyncExecutor.ejecutarAsync(
                () -> posConsultaService.listarDetallesPorPedido(idObjetivo),
                detallesCargados -> {
                    if (Integer.valueOf(idObjetivo).equals(this.pedidoActivoId)) {
                        aplicarDetallesPedidoEnPOS(detallesCargados);
                    }
                },
                ex -> LOGGER.log(Level.FINE, "Aviso cargando detalles de pedido #" + idObjetivo, ex)
            );
        }
    }

    private void aplicarDetallesPedidoEnPOS(List<DetallePedido> detalles) {
        itemsVenta.clear();
        if (detalles != null) {
            for (DetallePedido dp : detalles) {
                BigDecimal precioUnit = dp.getPrecioUnitario() != null ? dp.getPrecioUnitario() : BigDecimal.ZERO;
                int cant = Math.max(1, dp.getCantidad());
                BigDecimal subtotal = dp.getSubtotal() != null
                        ? dp.getSubtotal()
                        : precioUnit.multiply(BigDecimal.valueOf(cant));
                DetalleVenta dv = new DetalleVenta(0, 0, dp.getIdProducto(), cant, precioUnit, subtotal);
                dv.setNombreProducto(dp.getNombreProducto() != null ? dp.getNombreProducto() : ("Plato #" + dp.getIdProducto()));
                dv.setNotasPreparacion(dp.getNotasPreparacion());
                dv.setIdVariante(dp.getIdVariante());
                dv.setNombreVariante(dp.getNombreVariante());
                dv.setModificadoresTexto(dp.getModificadoresTexto());
                dv.setModificadoresSeleccionados(dp.getModificadoresSeleccionados());
                dv.setIdCombo(dp.getIdCombo());
                dv.setComboComponentesTexto(dp.getComboComponentesTexto());
                itemsVenta.add(dv);
            }
        }
        descuentoActivo = BigDecimal.ZERO;
        descuentoPuntosActivo = BigDecimal.ZERO;
        puntosARedimirPos = 0;
        descuentoManualFijado = false;
        if (tblDetalle != null) {
            tblDetalle.refresh();
        }
        recalcularTotales();
    }

    private void actualizarCabeceraCanal(String headerTexto, String badgeTexto, String canal) {
        if (lblOrdenHeader != null) {
            lblOrdenHeader.setText(headerTexto);
        }
        if (lblCanalBadge != null) {
            lblCanalBadge.setText(badgeTexto);
            lblCanalBadge.getStyleClass().removeAll("badge-canal-mesa", "badge-canal-kiosco", "badge-canal-delivery");
            if ("KIOSCO".equals(canal)) {
                lblCanalBadge.getStyleClass().add("badge-canal-kiosco");
            } else if ("DELIVERY".equals(canal)) {
                lblCanalBadge.getStyleClass().add("badge-canal-delivery");
            } else {
                lblCanalBadge.getStyleClass().add("badge-canal-mesa");
            }
        }
    }

    /**
     * Regla §5 y v4.4.0 §5 RBAC en POS:
     * - MOZO solo toma pedidos y envía a cocina (no cobra ni anula).
     * - CAJERO no puede aplicar descuentos ni anular ventas sin permiso de SUPERVISOR/ADMIN.
     */
    private void aplicarRestriccionesRolPOS(Usuario usr) {
        String rol = usr.getNombreRol() != null ? usr.getNombreRol().toUpperCase(Locale.ROOT) : "";
        if (rol.contains("MOZO")) {
            ocultarBoton(btnEfectivo);
            ocultarBoton(btnTarjeta);
            ocultarBoton(btnYape);
            ocultarBoton(btnDividirCuenta);
            ocultarBoton(btnDescuento);
            ocultarBoton(btnAnularVenta);
        } else if (rol.contains("CAJERO")) {
            SesionUsuario sesion = crearSesionActualPOS(usr);
            if (btnDescuento != null && !cobroCoordinator.tienePermiso(sesion, Permiso.VENTA_DESCUENTO)) {
                btnDescuento.setDisable(true);
            }
            if (btnAnularVenta != null) {
                // v5.2.0: El CAJERO mantiene habilitado Anular Venta; al pulsar solicita PIN de Supervisor
                btnAnularVenta.setDisable(false);
                if (!cobroCoordinator.tienePermiso(sesion, Permiso.VENTA_ANULAR)) {
                    btnAnularVenta.setTooltip(new Tooltip("Modo Supervisor: requiere PIN de SUPERVISOR/ADMIN para anular"));
                }
            }
        }
    }

    private SesionUsuario crearSesionActualPOS(Usuario usr) {
        return cobroCoordinator.crearSesionActualPOS(usr);
    }

    private void ocultarBoton(Button btn) {
        if (btn != null) {
            btn.setVisible(false);
            btn.setManaged(false);
        }
    }

    private void configurarCeldasMonoespaciadas() {
        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.valueOf(item));
                    if (!getStyleClass().contains("text-mono-sm")) {
                        getStyleClass().add("text-mono-sm");
                    }
                }
            }
        });

        colPrecio.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("S/ " + item);
                    if (!getStyleClass().contains("text-mono-sm")) {
                        getStyleClass().add("text-mono-sm");
                    }
                }
            }
        });

        colSubtotal.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("S/ " + item);
                    if (!getStyleClass().contains("text-mono")) {
                        getStyleClass().add("text-mono");
                    }
                }
            }
        });
    }

    public void registrarAtajosTeclado() {
        desregistrarAtajosTeclado();
        if (tblDetalle != null && tblDetalle.getScene() != null) {
            Scene escena = tblDetalle.getScene();
            EventHandler<KeyEvent> filter = event -> {
                if (event.getCode() == KeyCode.F5) {
                    handleCobroEfectivo(null);
                    event.consume();
                } else if (event.getCode() == KeyCode.F6) {
                    handleCobroTarjeta(null);
                    event.consume();
                } else if (event.getCode() == KeyCode.F7) {
                    handleCobroYape(null);
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    handleLimpiarConEscape();
                    event.consume();
                }
            };
            escena.addEventFilter(KeyEvent.KEY_PRESSED, filter);
            this.escenaAtajosRegistrada = escena;
            this.keyEventFilter = filter;
        }
    }

    private void cargarCategorias() {
        if (boxCategorias == null) return;
        UiAsyncExecutor.ejecutarAsync(
            () -> catalogoPresenter.listarCategoriasProductos(),
            categorias -> catalogoPresenter.renderizarChipsCategorias(boxCategorias, categorias, categoria -> {
                categoriaFiltroSeleccionada = categoria;
                filtrarCatalogo();
            }),
            ex -> LOGGER.log(Level.FINE, "Categorías no disponibles en arranque", ex)
        );
    }

    private void actualizarEstiloBotonesCategorias(Button activo) {
        // El estado visual del chip se gestiona dentro de VentaCatalogoPresenter.
    }

    public void cargarCatalogoProductosAsync() {
        tileProductos.getChildren().clear();
        UiAsyncExecutor.ejecutarAsync(
            () -> catalogoPresenter.cargarProductosConFallbackDemo(),
            productos -> {
                todosLosProductos.clear();
                todosLosProductos.addAll(productos);
                filtrarCatalogo();
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error al cargar catalogo desde BD, activando catalogo demo: {0}",
                    ex != null ? ex.getMessage() : "desconocido");
                poblarProductosDemostracion();
                filtrarCatalogo();
            }
        );
    }

    public void cargarCatalogoProductos() {
        cargarCatalogoProductosAsync();
    }

    /**
     * Carga síncrona del catálogo POS para tests de integración y capturas determinísticas.
     */
    public void cargarCatalogoProductosSincrono() {
        todosLosProductos.clear();
        todosLosProductos.addAll(catalogoPresenter.cargarProductosConFallbackDemo());
        filtrarCatalogo();
    }

    private void poblarProductosDemostracion() {
        todosLosProductos.clear();
        todosLosProductos.addAll(catalogoPresenter.crearProductosDemostracion());
    }

    public void filtrarCatalogo() {
        List<Producto> filtrados = catalogoPresenter.filtrarProductos(
            todosLosProductos, categoriaFiltroSeleccionada, txtBuscar.getText());
        catalogoPresenter.renderizarTarjetasCatalogo(tileProductos, filtrados, this::agregarProductoAVenta);
    }

    private ImageView crearThumbnailSiExiste(String imagenUrl) {
        if (imagenUrl == null || imagenUrl.isBlank()) {
            return null;
        }
        try {
            Path rutaSegura = MenuController.resolverImagenPlatilloSegura(imagenUrl);
            if (rutaSegura != null && Files.isRegularFile(rutaSegura)) {
                Image img = new Image(rutaSegura.toUri().toString(), 42, 32, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(42);
                iv.setFitHeight(32);
                iv.setPreserveRatio(true);
                return iv;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void agregarProductoAVenta(Producto p) {
        if (p == null || !p.isDisponible()) {
            return;
        }
        Producto productoResuelto = carritoHandler.resolverOpcionesProducto(personalizacionService, p);
        if (productoResuelto.tieneOpcionesPersonalizacion()) {
            javafx.stage.Window owner = tblDetalle != null && tblDetalle.getScene() != null
                    ? tblDetalle.getScene().getWindow()
                    : null;
            Optional<PersonalizacionProductoController.ResultadoPersonalizacion> seleccion =
                    PersonalizacionProductoController.mostrarModal(
                            owner,
                            productoResuelto,
                            productoResuelto.getVariantes(),
                            productoResuelto.getModificadores()
                    );
            seleccion.ifPresent(res -> agregarProductoPersonalizadoAVenta(
                    res.producto(), res.variante(), res.modificadores(), res.cantidad(), res.notaEspecial()
            ));
            return;
        }
        agregarProductoPersonalizadoAVenta(productoResuelto, null, List.of(), 1, "");
    }

    /**
     * Agrega directamente una selección ya configurada de producto + variante + extras al pedido activo o carrito.
     */
    public void agregarProductoPersonalizadoAVenta(Producto p,
                                                   ProductoVariante variante,
                                                   List<ProductoModificador> modificadores,
                                                   int cantidad,
                                                   String notaEspecial) {
        if (p == null || !p.isDisponible()) {
            return;
        }
        int qty = Math.max(1, cantidad);
        List<ProductoModificador> mods = modificadores != null ? List.copyOf(modificadores) : List.of();
        Integer idVariante = variante != null && variante.getIdVariante() > 0 ? variante.getIdVariante() : null;
        String nombreVariante = variante != null ? variante.getNombreVariante() : null;
        String modsTexto = FormatoPersonalizacionUtil.formatearTextoModificadores(mods);
        String nota = notaEspecial != null ? notaEspecial.trim() : "";
        BigDecimal precioUnitario = FormatoPersonalizacionUtil.calcularPrecioUnitario(p, variante, mods);
        BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);

        if (this.pedidoActivoId != null && this.pedidoActivoId > 0) {
            agregarProductoAPedidoExistente(
                    p, this.pedidoActivoId, idVariante, nombreVariante, mods, modsTexto, qty, nota, precioUnitario, subtotal
            );
            return;
        }
        agregarProductoEnCarritoLocal(
                p, idVariante, nombreVariante, mods, modsTexto, qty, nota, precioUnitario, subtotal
        );
    }

    private void agregarProductoAPedidoExistente(Producto p,
                                                 int idPedido,
                                                 Integer idVariante,
                                                 String nombreVariante,
                                                 List<ProductoModificador> mods,
                                                 String modsTexto,
                                                 int qty,
                                                 String nota,
                                                 BigDecimal precioUnitario,
                                                 BigDecimal subtotal) {
        DetallePedido nuevoItem = new DetallePedido(0, idPedido, p.getIdProducto(), qty, precioUnitario, subtotal, nota);
        nuevoItem.setNombreProducto(p.getNombre());
        nuevoItem.setIdVariante(idVariante);
        nuevoItem.setNombreVariante(nombreVariante);
        nuevoItem.setModificadoresTexto(modsTexto);
        nuevoItem.setModificadoresSeleccionados(mods);
        if (p.isEsCombo()) {
            nuevoItem.setIdCombo(p.getIdCombo());
            nuevoItem.setComboComponentesTexto(
                    FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(p.getComboComponentes()));
        }

        try {
            Pedido actualizado = pedidoService.agregarItemsAPedido(idPedido, List.of(nuevoItem));
            BigDecimal totalPedido = pedidoService.calcularTotal(idPedido);
            actualizado.setTotal(totalPedido);

            List<DetallePedido> detallesCompletos = actualizado.getDetalles() != null
                    ? actualizado.getDetalles()
                    : pedidoService.listarDetallesPorPedido(idPedido);

            aplicarDetallesPedidoEnPOS(detallesCompletos);
            if (lblTotal != null && (descuentoActivo == null || descuentoActivo.compareTo(BigDecimal.ZERO) <= 0)) {
                lblTotal.setText("S/ " + totalPedido.setScale(2, RoundingMode.HALF_UP));
            }
            actualizarCachePedidoActivo(idPedido, totalPedido, detallesCompletos);
        } catch (NegocioException | SQLException ex) {
            LOGGER.log(Level.WARNING, "Error agregando ítem al pedido #" + idPedido, ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : "Error de conexión con la base de datos";
            mostrarAlerta("Error al Agregar Ítem", "No se pudo guardar el producto en el pedido: " + msg, Alert.AlertType.ERROR);
        }
    }

    private void agregarProductoEnCarritoLocal(Producto p,
                                               Integer idVariante,
                                               String nombreVariante,
                                               List<ProductoModificador> mods,
                                               String modsTexto,
                                               int qty,
                                               String nota,
                                               BigDecimal precioUnitario,
                                               BigDecimal subtotal) {
        boolean nuevoItemAgregado = carritoHandler.agregarItemLocal(itemsVenta, p, idVariante, nombreVariante,
                mods, modsTexto, qty, nota, precioUnitario, subtotal);
        if (nuevoItemAgregado) {
            itemsVenta.get(itemsVenta.size() - 1).setNombreProducto(p.getNombre());
        }
        if (tblDetalle != null) {
            tblDetalle.refresh();
        }
        recalcularTotales();
    }

    private void actualizarCachePedidoActivo(int idPedido, BigDecimal totalPedido, List<DetallePedido> detalles) {
        for (Pedido cached : pedidosActivosMulticanal) {
            if (cached.getIdPedido() == idPedido) {
                cached.setTotal(totalPedido);
                cached.setDetalles(detalles);
            }
        }
        for (Pedido cachedMesa : pedidosPorMesa.values()) {
            if (cachedMesa.getIdPedido() == idPedido) {
                cachedMesa.setTotal(totalPedido);
                cachedMesa.setDetalles(detalles);
            }
        }
        renderizarGridMesasPos();
        renderizarBarraRapidaOrdenes(pedidosActivosMulticanal);
    }

    @FXML
    public void handleEliminarItem() {
        DetalleVenta sel = tblDetalle.getSelectionModel().getSelectedItem();
        if (carritoHandler.decrementarOEliminar(itemsVenta, sel)) {
            tblDetalle.refresh();
            recalcularTotales();
        }
    }

    @FXML
    public void handleEnviarComanda(ActionEvent event) {
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "Agregue al menos un ítem para enviar a cocina.", Alert.AlertType.WARNING);
            return;
        }
        int idObjetivo = pedidoActivoId != null ? pedidoActivoId : 0;
        List<DetallePedido> detallesCocina = new ArrayList<>();
        for (DetalleVenta dv : itemsVenta) {
            detallesCocina.add(convertirDetalleVentaAPedido(dv, idObjetivo));
        }

        Pedido pedido = new Pedido();
        pedido.setIdPedido(idObjetivo);
        if (AuthService.getUsuarioSesionActual() == null || AuthService.getUsuarioSesionActual().getIdUsuario() <= 0) {
            mostrarAlerta("Sesión Requerida", "Debe iniciar sesión para enviar comandas a cocina.", Alert.AlertType.ERROR);
            return;
        }
        pedido.setIdUsuario(AuthService.getUsuarioSesionActual().getIdUsuario());
        pedido.setNumeroMesa(mesaSeleccionadaActual != null ? mesaSeleccionadaActual : "01");
        pedido.setTipoPedido(canalSeleccionadoActual != null ? canalSeleccionadoActual : "MESA");
        pedido.setEstado("REGISTRADO");
        pedido.setTotal(obtenerTotalNetoActual());

        if (btnEnviarCocina != null) {
            btnEnviarCocina.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                return cobroCoordinator.enviarComandaACocina(pedidoService, pedido, detallesCocina);
            },
            resultado -> {
                if (btnEnviarCocina != null) {
                    btnEnviarCocina.setDisable(false);
                }
                Pedido registrado = resultado.pedido();
                if (registrado != null && registrado.getIdPedido() > 0) {
                    this.pedidoActivoId = registrado.getIdPedido();
                    if (registrado.getDetalles() != null && !registrado.getDetalles().isEmpty()) {
                        aplicarDetallesPedidoEnPOS(registrado.getDetalles());
                    }
                    refrescarMesasYCanalesAsync();
                }
                if (resultado.impresionOk()) {
                    String sufijo = registrado != null && registrado.getIdPedido() > 0
                            ? "Orden #" + registrado.getIdPedido() : "Orden";
                    mostrarAlerta("Comanda Enviada",
                            sufijo + " enviada a Línea de Cocina (KDS) correctamente.",
                            Alert.AlertType.INFORMATION);
                } else {
                    mostrarAlerta("Comanda en KDS (Sin Impresión)",
                            "La orden se envió a KDS pero falló la impresora térmica.",
                            Alert.AlertType.WARNING);
                }
            },
            ex -> {
                if (btnEnviarCocina != null) {
                    btnEnviarCocina.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error registrando/enviando comanda a cocina", ex);
                mostrarAlerta("Error al Enviar Comanda",
                        "No se pudo registrar ni enviar la comanda a cocina: "
                                + (ex.getMessage() != null ? ex.getMessage() : "Error de conexión"),
                        Alert.AlertType.ERROR);
            }
        );
    }


    /**
     * Construye los pagos divididos respetando la modalidad seleccionada por el usuario y sumando exactamente
     * {@code totalNeto} al centavo.
     */
    public static List<VentaService.PagoSplit> construirPagosPorModalidad(String modalidad,
                                                                          BigDecimal totalNeto,
                                                                          List<DetalleVenta> items) {
        return COBRO_STATICS.construirPagosPorModalidad(modalidad, totalNeto, items);
    }

    /**
     * División de Cuentas (§3.1): permite dividir por partes iguales o por ítems y cobrar en distintos medios de pago.
     */
    @FXML
    public void handleDividirCuenta(ActionEvent event) {
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "Agregue ítems a la orden antes de dividir la cuenta.", Alert.AlertType.WARNING);
            return;
        }
        Optional<String> res = DialogService.crearChoiceDialog(
                MODALIDAD_SPLIT_2,
                List.of(MODALIDAD_SPLIT_2, MODALIDAD_SPLIT_4, MODALIDAD_SPLIT_ITEMS),
                "División de Cuenta (Split)",
                "Seleccione la modalidad de división de cuenta",
                "Modalidad:").showAndWait();
        if (res.isPresent()) {
            BigDecimal totalNeto = obtenerTotalNetoActual();
            BigDecimal basePreCanje = obtenerBasePreCanjeActual();
            List<DetalleVenta> itemsSnapshot = new ArrayList<>(itemsVenta);
            List<VentaService.PagoSplit> pagos = construirPagosPorModalidad(res.get(), totalNeto, itemsSnapshot);
            if (AuthService.getUsuarioSesionActual() == null || AuthService.getUsuarioSesionActual().getIdUsuario() <= 0) {
                mostrarAlerta("Sesión Requerida", "Debe iniciar sesión para dividir la cuenta.", Alert.AlertType.ERROR);
                return;
            }
            int idUsr = AuthService.getUsuarioSesionActual().getIdUsuario();
            Integer idCliVenta = clienteSeleccionadoPos != null && clienteSeleccionadoPos.getIdCliente() > 0
                    ? clienteSeleccionadoPos.getIdCliente() : null;
            Integer idPedidoSnap = pedidoActivoId;
            BigDecimal descSnap = descuentoActivo;
            BigDecimal descPtsSnap = descuentoPuntosActivo;
            int ptsSnap = puntosARedimirPos;

            if (btnDividirCuenta != null) {
                btnDividirCuenta.setDisable(true);
            }
            UiAsyncExecutor.ejecutarAsync(
                () -> {
                    DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
                    int idCajaActiva = resolverIdCajaActiva();
                    Venta v = new Venta(0, idPedidoSnap, idCajaActiva, idUsr, idCliVenta,
                            desglose.getSubtotalGravado(), desglose.getIgv(), totalNeto,
                            "MIXTO", totalNeto, BigDecimal.ZERO);
                    v.setDescuento(descSnap);
                    v.setDescuentoPuntos(descPtsSnap);
                    v.setPuntosACanjear(ptsSnap);
                    v.setIdempotencyKey(UUID.randomUUID().toString());
                    String corrSplit = String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);
                    ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SPL", corrSplit, totalNeto);
                    return ventaService.registrarVentaDividida(v, itemsSnapshot, comp, pagos);
                },
                procesada -> {
                    if (btnDividirCuenta != null) {
                        btnDividirCuenta.setDisable(false);
                    }
                    itemsVenta.clear();
                    descuentoActivo = BigDecimal.ZERO;
                    descuentoManualFijado = false;
                    limpiarEstadoClienteFidelizacionPos();
                    pedidoActivoId = null;
                    recalcularTotales();
                    refrescarMesasYCanalesAsync();
                    mostrarAlerta("Cuenta Dividida",
                            "Cobro dividido en " + pagos.size() + " pagos registrado exitosamente en Caja.",
                            Alert.AlertType.INFORMATION);
                },
                ex -> {
                    if (btnDividirCuenta != null) {
                        btnDividirCuenta.setDisable(false);
                    }
                    mostrarAlerta("Error en Split", ex.getMessage(), Alert.AlertType.ERROR);
                }
            );
        }
    }

    private int resolverIdUsuarioActivo() throws NegocioException {
        if (AuthService.getUsuarioSesionActual() == null || AuthService.getUsuarioSesionActual().getIdUsuario() <= 0) {
            throw new NegocioException("No hay una sesión de usuario activa para procesar la operación.");
        }
        return AuthService.getUsuarioSesionActual().getIdUsuario();
    }

    private int resolverIdCajaActiva() throws SQLException, NegocioException {
        return new CajaService().obtenerCajaActiva()
                .map(Caja::getIdCaja)
                .orElseThrow(() -> new CajaCerradaException("No hay una caja abierta para registrar el cobro dividido."));
    }

    public Venta ejecutarCobroSplitEnPagos(List<VentaService.PagoSplit> pagos) throws Exception {
        BigDecimal totalNeto = obtenerTotalNetoActual();
        BigDecimal basePreCanje = obtenerBasePreCanjeActual();
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
        int idUsr = resolverIdUsuarioActivo();
        int idCajaActiva = resolverIdCajaActiva();
        Integer idCliVenta = clienteSeleccionadoPos != null && clienteSeleccionadoPos.getIdCliente() > 0
                ? clienteSeleccionadoPos.getIdCliente() : null;
        Venta v = new Venta(0, pedidoActivoId, idCajaActiva, idUsr, idCliVenta,
            desglose.getSubtotalGravado(), desglose.getIgv(), totalNeto,
            "MIXTO", totalNeto, BigDecimal.ZERO);
        v.setDescuento(descuentoActivo);
        v.setDescuentoPuntos(descuentoPuntosActivo);
        v.setPuntosACanjear(puntosARedimirPos);
        v.setIdempotencyKey(UUID.randomUUID().toString());
        String corrSplit = String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);
                    ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SPL", corrSplit, totalNeto);
        Venta procesada = ventaService.registrarVentaDividida(v, new ArrayList<>(itemsVenta), comp, pagos);
        itemsVenta.clear();
        descuentoActivo = BigDecimal.ZERO;
        descuentoManualFijado = false;
        limpiarEstadoClienteFidelizacionPos();
        pedidoActivoId = null;
        recalcularTotales();
        refrescarMesasYCanalesSincrono();
        return procesada;
    }

    /**
     * Aplicación de Descuento (§3.2 y v4.4.0 §5): exige permiso {@code VENTA_DESCUENTO} (solo Admin/Supervisor).
     */
    @FXML
    public void handleAplicarDescuento(ActionEvent event) {
        Usuario usr = AuthService.getUsuarioSesionActual();
        SesionUsuario sesion = crearSesionActualPOS(usr);
        if (!autorizacionService.tienePermiso(sesion, Permiso.VENTA_DESCUENTO)) {
            mostrarAlerta("Acceso Denegado", "Solo un Administrador o Supervisor puede autorizar descuentos.", Alert.AlertType.ERROR);
            return;
        }
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "Agregue productos antes de aplicar un descuento.", Alert.AlertType.WARNING);
            return;
        }
        Optional<String> opt = DialogService.crearChoiceDialog(
                "10%",
                List.of("10%", "20%", "Monto fijo S/ 5.00"),
                "Aplicar Descuento Autorizado",
                "Requiere permiso VENTA_DESCUENTO (Solo Administrador/Supervisor)",
                "Seleccione el descuento:").showAndWait();
        if (opt.isPresent()) {
            try {
                String sel = opt.get();
                if ("10%".equals(sel)) {
                    aplicarDescuentoConPermiso(new BigDecimal("10"), true);
                } else if ("20%".equals(sel)) {
                    aplicarDescuentoConPermiso(new BigDecimal("20"), true);
                } else {
                    aplicarDescuentoConPermiso(new BigDecimal("5.00"), false);
                }
            } catch (Exception e) {
                mostrarAlerta("Acceso Denegado / Error", e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    public BigDecimal aplicarDescuentoConPermiso(BigDecimal valor, boolean esPorcentaje) throws NegocioException {
        Usuario usr = AuthService.getUsuarioSesionActual();
        SesionUsuario sesion = crearSesionActualPOS(usr);

        this.descuentoActivo = carritoHandler.calcularDescuentoAutorizado(itemsVenta, valor, esPorcentaje, sesion);
        this.descuentoManualFijado = true;
        recalcularTotales();
        return this.descuentoActivo;
    }

    /** Modal estructurado para autorizar anulaciones desde caja en Modo Supervisor (v5.2.0). */
    public record ModalModoSupervisorHandle(
            Dialog<ButtonType> dialog,
            VBox rootContenido,
            TextField txtIdVenta,
            PasswordField txtPinSupervisor,
            TextField txtMotivo,
            CheckBox chkPlatoPreparado
    ) {
    }

    /** Construye el modal de Modo Supervisor sin bloquear el hilo UI para uso interactivo y pruebas FX. */
    public ModalModoSupervisorHandle construirModalModoSupervisor(int idVentaPrellenado) {
        Dialog<ButtonType> dialog = DialogService.crearDialogo(
                "Modo Supervisor — Autorización de Anulación",
                null);
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(16);
        root.setPadding(new Insets(16, 20, 16, 20));
        root.setPrefWidth(460);

        Label badge = new Label("AUTORIZACIÓN DE SUPERVISOR / ADMIN REQUERIDA");
        badge.getStyleClass().add("badge-pill-warning");

        Label lblSub = new Label("Su rol actual no tiene permiso directo para anular ventas. "
                + "Solicite a un Supervisor o Administrador ingresar su PIN para autorizar y auditar la operación.");
        lblSub.setWrapText(true);
        lblSub.getStyleClass().add("text-secondary");

        TextField txtId = new TextField(idVentaPrellenado > 0 ? String.valueOf(idVentaPrellenado) : "");
        txtId.setPromptText("Ej. 1024");
        txtId.setId("txtIdVentaSupervisor");

        PasswordField txtPin = new PasswordField();
        txtPin.setPromptText("PIN secreto");
        txtPin.setId("txtPinSupervisor");

        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo de anulación (obligatorio)");
        txtMotivo.setId("txtMotivoSupervisor");

        CheckBox chkMerma = new CheckBox("Plato ya preparado en cocina (registrar MERMA)");

        root.getChildren().addAll(
                badge,
                lblSub,
                DialogService.construirFilaDobleModal(
                        DialogService.construirCampoModal("ID de Venta:", txtId),
                        DialogService.construirCampoModal("PIN Supervisor:", txtPin)),
                DialogService.construirCampoModal("Motivo:", txtMotivo),
                chkMerma);

        ButtonType btnConfirmar = new ButtonType("Autorizar y Anular Venta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().setAll(btnConfirmar, ButtonType.CANCEL);

        return new ModalModoSupervisorHandle(dialog, root, txtId, txtPin, txtMotivo, chkMerma);
    }

    /**
     * Anulación de Venta (§3.2 y Modo Supervisor v5.2.0): si el usuario tiene {@code VENTA_ANULAR}
     * procede directamente; si es {@code CAJERO}, abre modal de Modo Supervisor exigiendo PIN de SUPERVISOR/ADMIN.
     */
    @FXML
    public void handleAnularVenta(ActionEvent event) {
        Usuario usr = AuthService.getUsuarioSesionActual();
        SesionUsuario sesion = crearSesionActualPOS(usr);
        if (!cobroCoordinator.tienePermiso(sesion, Permiso.VENTA_ANULAR)) {
            ModalModoSupervisorHandle modal = VentaPosModalHelper.construirModalModoSupervisor(0);
            Optional<ButtonType> res = modal.dialog().showAndWait();
            if (res.isPresent() && res.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                String idRaw = modal.txtIdVenta().getText() != null ? modal.txtIdVenta().getText().trim() : "";
                String pinSup = modal.txtPinSupervisor().getText() != null ? modal.txtPinSupervisor().getText().trim() : "";
                String motivo = modal.txtMotivo().getText() != null ? modal.txtMotivo().getText().trim() : "";
                boolean yaPreparado = modal.chkPlatoPreparado().isSelected();
                VentaCobroCoordinator.ValidacionAnulacionSupervisor validacion =
                        cobroCoordinator.validarAnulacionSupervisor(idRaw, motivo);
                if (!validacion.valido()) {
                    mostrarAlerta(validacion.tituloError(), validacion.mensajeError(), Alert.AlertType.WARNING);
                    return;
                }
                final int idVenta = validacion.idVenta();
                final String motivoFinal = validacion.motivoFinal();
                if (btnAnularVenta != null) {
                    btnAnularVenta.setDisable(true);
                }
                UiAsyncExecutor.ejecutarAsync(
                    () -> cobroCoordinator.anularVentaConSupervisor(
                            idVenta, yaPreparado, sesion, pinSup, motivoFinal),
                    supervisor -> {
                        if (btnAnularVenta != null) {
                            btnAnularVenta.setDisable(false);
                        }
                        String nomSup = supervisor.getNombreEmpleado() != null && !supervisor.getNombreEmpleado().isBlank()
                                ? supervisor.getNombreEmpleado() : supervisor.getUsername();
                        mostrarAlerta("Venta Anulada (Modo Supervisor)",
                                "La venta #" + idVenta + " fue anulada correctamente.\n"
                                        + "Autorizado por: " + nomSup + " (" + supervisor.getNombreRol() + ").",
                                Alert.AlertType.INFORMATION);
                    },
                    e -> {
                        if (btnAnularVenta != null) {
                            btnAnularVenta.setDisable(false);
                        }
                        mostrarAlerta("Autorización Denegada", e.getMessage(), Alert.AlertType.ERROR);
                    }
                );
            }
            return;
        }
        Optional<String> opt = DialogService.crearTextInputDialog(
                "",
                "Anular Venta Registrada",
                "Requiere permiso VENTA_ANULAR",
                "Ingrese el ID de la venta a anular:").showAndWait();
        if (opt.isPresent() && !opt.get().isBlank()) {
            final int idVenta;
            try {
                idVenta = Integer.parseInt(opt.get().trim());
            } catch (NumberFormatException nfe) {
                mostrarAlerta("Dato inválido", "El ID de la venta debe ser numérico.", Alert.AlertType.WARNING);
                return;
            }
            Alert confirmAnular = DialogService.crearConfirmacionDestructiva(
                    "Confirmar Anulación de Venta #" + idVenta,
                    "Esta operación anulará la venta #" + idVenta + " y revertirá el inventario. ¿Desea continuar?");
            Optional<ButtonType> confirmRes = confirmAnular.showAndWait();
            if (confirmRes.isEmpty() || confirmRes.get() != DialogService.BTN_SI_ANULAR) {
                return;
            }
            if (btnAnularVenta != null) {
                btnAnularVenta.setDisable(true);
            }
            UiAsyncExecutor.ejecutarAsync(
                () -> {
                    cobroCoordinator.anularVentaDirecta(idVenta, sesion, "Anulación solicitada desde POS");
                    return Boolean.TRUE;
                },
                ok -> {
                    if (btnAnularVenta != null) {
                        btnAnularVenta.setDisable(false);
                    }
                    mostrarAlerta("Venta Anulada",
                            "La venta #" + idVenta + " fue anulada y su stock revertido.",
                            Alert.AlertType.INFORMATION);
                },
                e -> {
                    if (btnAnularVenta != null) {
                        btnAnularVenta.setDisable(false);
                    }
                    mostrarAlerta("Error al Anular", e.getMessage(), Alert.AlertType.ERROR);
                }
            );
        }
    }

    public void handleLimpiarConEscape() {
        if (itemsVenta.isEmpty()) {
            return;
        }
        if (confirmacionEscapeAutomatica) {
            itemsVenta.clear();
            descuentoActivo = BigDecimal.ZERO;
            descuentoManualFijado = false;
            limpiarEstadoClienteFidelizacionPos();
            pedidoActivoId = null;
            recalcularTotales();
            return;
        }
        Alert confirm = DialogService.crearConfirmacionDestructiva(
                "Confirmar limpieza (Esc)",
                "¿Desea limpiar los ítems de la venta actual?");
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == DialogService.BTN_SI_ANULAR) {
            itemsVenta.clear();
            descuentoActivo = BigDecimal.ZERO;
            descuentoManualFijado = false;
            limpiarEstadoClienteFidelizacionPos();
            pedidoActivoId = null;
            recalcularTotales();
        }
    }

    private BigDecimal obtenerBasePreCanjeActual() {
        return carritoHandler.obtenerBasePreCanje(itemsVenta, descuentoActivo);
    }

    private BigDecimal obtenerTotalNetoActual() {
        return carritoHandler.obtenerTotalNeto(itemsVenta, descuentoActivo, descuentoPuntosActivo);
    }

    private void recalcularTotales() {
        try {
            VentaCarritoHandler.ResumenTotalesVenta totales = carritoHandler.calcularTotales(
                    itemsVenta, descuentoActivo, descuentoManualFijado, descuentoPuntosActivo);
            descuentoActivo = totales.descuentoActivo();
            descuentoPuntosActivo = totales.descuentoPuntosAjustado();
            if (totales.totalBruto().compareTo(BigDecimal.ZERO) <= 0) {
                puntosARedimirPos = 0;
            }
            lblSubtotal.setText("S/ " + totales.subtotalGravado());
            lblIgv.setText("S/ " + totales.igv());
            lblTotal.setText("S/ " + totales.totalNeto());
            if (rowDescuento != null && lblDescuento != null) {
                rowDescuento.setVisible(totales.hayDescuento());
                rowDescuento.setManaged(totales.hayDescuento());
                if (totales.hayDescuento()) {
                    lblDescuento.setText("-S/ " + descuentoActivo.setScale(2, RoundingMode.HALF_UP));
                }
            }
            if (rowDescuentoPuntos != null && lblDescuentoPuntos != null) {
                rowDescuentoPuntos.setVisible(totales.hayDescuentoPuntos());
                rowDescuentoPuntos.setManaged(totales.hayDescuentoPuntos());
                if (totales.hayDescuentoPuntos()) {
                    lblDescuentoPuntos.setText("-S/ " + descuentoPuntosActivo.setScale(2, RoundingMode.HALF_UP));
                }
            }
        } catch (NegocioException e) {
            LOGGER.log(Level.WARNING, "Error calculando desglose fiscal", e);
        }
    }

    @FXML
    public void handleCobroEfectivo(ActionEvent event) {
        abrirModalCobro("EFECTIVO");
    }

    @FXML
    public void handleCobroTarjeta(ActionEvent event) {
        abrirModalCobro("TARJETA");
    }

    @FXML
    public void handleCobroYape(ActionEvent event) {
        abrirModalCobro("YAPE_PLIN");
    }

    private void setBotonesCobroDeshabilitados(boolean deshabilitado) {
        if (btnEfectivo != null) {
            btnEfectivo.setDisable(deshabilitado);
        }
        if (btnTarjeta != null) {
            btnTarjeta.setDisable(deshabilitado);
        }
        if (btnYape != null) {
            btnYape.setDisable(deshabilitado);
        }
        if (btnEnviarCocina != null) {
            btnEnviarCocina.setDisable(deshabilitado);
        }
    }

    private void abrirModalCobro(String metodo) {
        if (!cobroEnProgreso.compareAndSet(false, true)) {
            return;
        }
        setBotonesCobroDeshabilitados(true);

        if (itemsVenta.isEmpty()) {
            cobroEnProgreso.set(false);
            setBotonesCobroDeshabilitados(false);
            if (!modoCobroDirectoTest) {
                mostrarAlerta("Atención", "No hay productos en la venta activa.", Alert.AlertType.WARNING);
            }
            return;
        }

        if (modoCobroDirectoTest) {
            try {
                BigDecimal basePreCanje = obtenerBasePreCanjeActual();
                BigDecimal totalNeto = obtenerTotalNetoActual();
                Integer idCliVenta = clienteSeleccionadoPos != null && clienteSeleccionadoPos.getIdCliente() > 0
                        ? clienteSeleccionadoPos.getIdCliente() : null;
                cobroCoordinator.procesarCobroDirecto(pedidoActivoId, idCliVenta, basePreCanje, totalNeto,
                        metodo, descuentoActivo, descuentoPuntosActivo, puntosARedimirPos,
                        new ArrayList<>(itemsVenta));
                itemsVenta.clear();
                descuentoActivo = BigDecimal.ZERO;
                descuentoManualFijado = false;
                limpiarEstadoClienteFidelizacionPos();
                pedidoActivoId = null;
                recalcularTotales();
                refrescarMesasYCanalesSincrono();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error en cobro directo POS", e);
            } finally {
                cobroEnProgreso.set(false);
                setBotonesCobroDeshabilitados(false);
            }
            return;
        }

        BigDecimal totalNeto = obtenerTotalNetoActual();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/CobroModalView.fxml"));
            Parent root = loader.load();

            CobroModalController ctrl = loader.getController();
            ctrl.setIdPedidoAsociado(pedidoActivoId);
            ctrl.initData(
                    totalNeto,
                    descuentoActivo,
                    descuentoPuntosActivo,
                    puntosARedimirPos,
                    clienteSeleccionadoPos,
                    metodo,
                    itemsVenta,
                    () -> {
                        itemsVenta.clear();
                        descuentoActivo = BigDecimal.ZERO;
                        descuentoManualFijado = false;
                        limpiarEstadoClienteFidelizacionPos();
                        pedidoActivoId = null;
                        recalcularTotales();
                        refrescarMesasYCanalesAsync();
                    }
            );

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Finalizar Venta y Cobro - FoodCore");

            Scene scene = new Scene(root);
            App.applyStylesheets(scene);

            AnimationUtils.animateModalEnter(root);

            stage.setScene(scene);
            stage.sizeToScene();
            stage.setResizable(false);
            stage.showAndWait();

        } catch (IOException e) {
            mostrarAlerta("Error", "No se pudo abrir la ventana de cobro: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
            cobroEnProgreso.set(false);
            setBotonesCobroDeshabilitados(false);
        }
    }

    @FXML
    public void handleAbrirArqueo(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/CajaView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Control de Caja, Arqueo y Cierre Z");

            Scene scene = new Scene(root, 780, 680);
            App.applyStylesheets(scene);

            AnimationUtils.animateModalEnter(root);

            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            mostrarAlerta("Error", "No se pudo abrir el arqueo: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public ObservableList<DetalleVenta> getItemsVenta() {
        return itemsVenta;
    }

    public List<Producto> getTodosLosProductos() {
        return todosLosProductos;
    }

    public TilePane getTileProductos() {
        return tileProductos;
    }

    public TilePane getTileMesasPos() {
        return tileMesasPos;
    }

    public Label getLblCanalBadge() {
        return lblCanalBadge;
    }

    public Label getLblOrdenHeader() {
        return lblOrdenHeader;
    }

    public TabPane getTabPanePosCatalogo() {
        return tabPanePosCatalogo;
    }

    public Button getBtnEfectivo() {
        return btnEfectivo;
    }

    public Label getLblTotal() {
        return lblTotal;
    }

    public TableView<DetalleVenta> getTblDetalle() {
        return tblDetalle;
    }

    public Button getBtnDescuento() {
        return btnDescuento;
    }

    public Button getBtnAnularVenta() {
        return btnAnularVenta;
    }

    public BigDecimal getDescuentoActivo() {
        return descuentoActivo;
    }

    public String getMesaSeleccionadaActual() {
        return mesaSeleccionadaActual;
    }

    public String getCanalSeleccionadoActual() {
        return canalSeleccionadoActual;
    }

    public Integer getPedidoActivoId() {
        return pedidoActivoId;
    }

    public void setModoCobroDirectoTest(boolean modoCobroDirectoTest) {
        this.modoCobroDirectoTest = modoCobroDirectoTest;
    }

    public void setConfirmacionEscapeAutomatica(boolean confirmacionEscapeAutomatica) {
        this.confirmacionEscapeAutomatica = confirmacionEscapeAutomatica;
    }

    public void simularEstadoTransaccionEnCurso(boolean enCurso) {
        cobroEnProgreso.set(enCurso);
        setBotonesCobroDeshabilitados(enCurso);
    }

    public void setPedidoService(PedidoService pedidoService) {
        if (pedidoService != null) {
            this.pedidoService = pedidoService;
        }
    }

    public String getUltimaAlertaTitulo() {
        return ultimaAlertaTitulo;
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        this.ultimaAlertaTitulo = titulo;
        ToastService.mostrarPorAlertType(tipo, titulo + ": " + mensaje);
    }

    @FXML
    public void handleImprimirPreCuenta(ActionEvent event) {
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "No hay platos en la orden para emitir pre-cuenta.", Alert.AlertType.WARNING);
            return;
        }

        Pedido p = new Pedido();
        p.setIdPedido(pedidoActivoId != null ? pedidoActivoId : SECURE_RANDOM.nextInt(9000) + 1000);
        p.setNumeroMesa(mesaSeleccionadaActual);
        p.setTotal(obtenerTotalNetoActual());

        List<DetallePedido> detalles = new ArrayList<>();
        for (DetalleVenta dv : itemsVenta) {
            detalles.add(convertirDetalleVentaAPedido(dv, p.getIdPedido()));
        }

        if (btnPreCuenta != null) {
            btnPreCuenta.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                cobroCoordinator.imprimirPreCuenta(p, detalles);
                return Boolean.TRUE;
            },
            ok -> {
                if (btnPreCuenta != null) {
                    btnPreCuenta.setDisable(false);
                }
                mostrarAlerta("Pre-Cuenta Emitida",
                        "Pre-cuenta emitida exitosamente (Ticket no fiscal).",
                        Alert.AlertType.INFORMATION);
            },
            ex -> {
                if (btnPreCuenta != null) {
                    btnPreCuenta.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error al imprimir pre-cuenta", ex);
                mostrarAlerta("Error", "Error al emitir pre-cuenta: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        );
    }

    private DetallePedido convertirDetalleVentaAPedido(DetalleVenta dv, int idPedido) {
        DetallePedido dp = new DetallePedido();
        dp.setIdPedido(idPedido);
        dp.setIdProducto(dv.getIdProducto());
        dp.setNombreProducto(dv.getNombreProducto());
        dp.setCantidad(dv.getCantidad());
        dp.setPrecioUnitario(dv.getPrecioUnitario());
        dp.setSubtotal(dv.getSubtotal());
        dp.setNotasPreparacion(dv.getNotasPreparacion());
        dp.setIdVariante(dv.getIdVariante());
        dp.setNombreVariante(dv.getNombreVariante());
        dp.setModificadoresTexto(dv.getModificadoresTexto());
        dp.setModificadoresSeleccionados(dv.getModificadoresSeleccionados());
        dp.setIdCombo(dv.getIdCombo());
        dp.setComboComponentesTexto(dv.getComboComponentesTexto());
        return dp;
    }

    @FXML
    public void handleNotaItem(ActionEvent event) {
        DetalleVenta sel = tblDetalle.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta("Atención", "Seleccione un ítem de la tabla para añadir nota.", Alert.AlertType.WARNING);
            return;
        }

        DialogService.crearTextInputDialog(
                sel.getNotasPreparacion() != null ? sel.getNotasPreparacion() : "",
                "Indicación de Cocina",
                "Nota para: " + sel.getNombreProducto(),
                "Indicación especial (ej. sin cebolla, término medio):").showAndWait().ifPresent(nota -> {
            sel.setNotasPreparacion(nota);
            tblDetalle.refresh();
        });
    }

    @FXML
    public void handleBuscarClientePos() {
        String criterio = txtBuscarClientePos != null && txtBuscarClientePos.getText() != null
                ? txtBuscarClientePos.getText().trim() : "";
        if (criterio.isEmpty()) {
            if (txtBuscarClientePos != null) {
                txtBuscarClientePos.requestFocus();
            }
            return;
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> fidelizacionCoordinator.buscarClientePorCriterio(criterio).orElse(null),
            cli -> {
                if (cli != null) {
                    asociarClientePos(cli, null);
                } else {
                    Alert noEncontrado = DialogService.crearAlerta(
                            Alert.AlertType.CONFIRMATION,
                            "Cliente no encontrado",
                            "No existe cliente con DNI o teléfono \"" + criterio + "\"",
                            "¿Desea registrar rápidamente un cliente minorista (Nombre y DNI)?");
                    Optional<ButtonType> r = noEncontrado.showAndWait();
                    if (r.isPresent() && r.get() == ButtonType.OK) {
                        abrirDialogoRegistroRapidoCliente(criterio);
                    }
                }
            },
            ex -> LOGGER.log(Level.WARNING, "Error buscando cliente en POS", ex)
        );
    }

    @FXML
    public void handleRegistroRapidoClientePos() {
        String prefill = txtBuscarClientePos != null && txtBuscarClientePos.getText() != null
                ? txtBuscarClientePos.getText().trim() : "";
        abrirDialogoRegistroRapidoCliente(prefill);
    }

    @FXML
    public void handleLimpiarClientePos() {
        limpiarEstadoClienteFidelizacionPos();
        recalcularTotales();
    }

    private void abrirDialogoRegistroRapidoCliente(String prefill) {
        Optional<Cliente> creado = VentaPosModalHelper.mostrarDialogoRegistroRapidoCliente(
                prefill, fidelizacionCoordinator::buscarORegistrarClienteRapidoDirecto);
        if (creado.isPresent() && creado.get() != null) {
            asociarClientePos(creado.get(), null);
        } else if (creado.isPresent()) {
            mostrarAlerta("Datos Inválidos", "Ingrese un DNI válido de 8 dígitos y el nombre completo.", Alert.AlertType.WARNING);
        }
    }

    /**
     * Búsqueda síncrona por DNI o teléfono para pruebas determinísticas POS (v5.0.0).
     */
    public Optional<Cliente> buscarYAsociarClientePorCriterioSincrono(String criterio, Boolean confirmarCumpleanos) {
        if (criterio == null || criterio.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<Cliente> cliOpt = fidelizacionCoordinator.buscarClientePorCriterio(criterio.trim());
            cliOpt.ifPresent(c -> asociarClientePos(c, confirmarCumpleanos));
            return cliOpt;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error buscando cliente por criterio POS", ex);
            return Optional.empty();
        }
    }

    /**
     * Registra un cliente minorista (Nombre + DNI) directamente desde el POS y lo asocia a la venta actual.
     */
    public Cliente registrarYAsociarClienteRapidoPos(String dni, String nombreCompleto, String telefono, LocalDate fechaNacimiento)
            throws NegocioException {
        Cliente cli = fidelizacionCoordinator.registrarClienteRapido(dni, nombreCompleto, telefono, fechaNacimiento);
        asociarClientePos(cli, respuestaAutomaticaCumpleanosTest != null ? respuestaAutomaticaCumpleanosTest : Boolean.FALSE);
        return cli;
    }

    /**
     * Asocia un cliente al ticket activo POS, actualiza los controles visuales de puntos
     * y evalúa la alerta de cumpleaños (SPEC v5.0.0 §2.1, §2.3, §3.2).
     */
    public void asociarClientePos(Cliente cli, Boolean confirmarCumpleanosOverride) {
        this.clienteSeleccionadoPos = cli;
        if (cli == null) {
            limpiarEstadoClienteFidelizacionPos();
            return;
        }
        this.descuentoPuntosActivo = BigDecimal.ZERO;
        this.puntosARedimirPos = 0;

        if (txtBuscarClientePos != null) {
            txtBuscarClientePos.setText(cli.getNumeroDocumento());
        }
        if (rowClienteAsociadoPos != null) {
            rowClienteAsociadoPos.setVisible(true);
            rowClienteAsociadoPos.setManaged(true);
        }
        if (btnLimpiarClientePos != null) {
            btnLimpiarClientePos.setVisible(true);
            btnLimpiarClientePos.setManaged(true);
        }
        if (lblClientePosNombre != null) {
            lblClientePosNombre.setText(cli.getRazonSocial() + " · " + cli.getTipoDocumento() + " " + cli.getNumeroDocumento());
        }
        int pts = cli.getPuntosAcumulados();
        if (lblClientePosPuntos != null) {
            lblClientePosPuntos.setText(pts + " pts");
        }
        if (btnCanjearPuntosPos != null) {
            boolean mostrarCanje = pts > 0;
            btnCanjearPuntosPos.setVisible(mostrarCanje);
            btnCanjearPuntosPos.setManaged(mostrarCanje);
            btnCanjearPuntosPos.setDisable(pts < ConfiguracionFidelizacion.getPuntosPorCanje());
            btnCanjearPuntosPos.setText("Canjear Puntos");
        }

        VentaFidelizacionCoordinator.EstadoCumpleanosPos estadoCumple = fidelizacionCoordinator.evaluarCumpleanos(cli);
        boolean esCumple = estadoCumple.esCumpleanos();
        if (rowAlertaCumplePos != null) {
            rowAlertaCumplePos.setVisible(esCumple);
            rowAlertaCumplePos.setManaged(esCumple);
        }
        if (esCumple) {
            String msg = estadoCumple.mensajeAlerta();
            this.ultimoMensajeAlertaCumpleanos = msg;
            if (lblAlertaCumplePos != null) {
                lblAlertaCumplePos.setText(estadoCumple.textoBadge());
            }
            if (btnAplicarCumplePos != null) {
                btnAplicarCumplePos.setText("Aplicar 10%");
                btnAplicarCumplePos.setDisable(false);
            }
            Boolean decision = confirmarCumpleanosOverride != null ? confirmarCumpleanosOverride : respuestaAutomaticaCumpleanosTest;
            if (decision == null && !itemsVenta.isEmpty()) {
                Alert alertCumple = DialogService.crearAlerta(
                        Alert.AlertType.CONFIRMATION,
                        "Cupón de Cumpleaños",
                        msg,
                        ButtonType.YES, ButtonType.NO);
                alertCumple.setHeaderText("Cliente de Cumpleaños");
                Optional<ButtonType> opt = alertCumple.showAndWait();
                if (opt.isPresent() && opt.get() == ButtonType.YES) {
                    aplicarDescuentoCumpleanosPos();
                }
            } else if (Boolean.TRUE.equals(decision) && !itemsVenta.isEmpty()) {
                aplicarDescuentoCumpleanosPos();
            }
        } else {
            this.ultimoMensajeAlertaCumpleanos = null;
        }
        recalcularTotales();
    }

    @FXML
    public void handleAplicarCuponCumpleanosPos() {
        aplicarDescuentoCumpleanosPos();
    }

    public BigDecimal aplicarDescuentoCumpleanosPos() {
        if (itemsVenta.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalBruto = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            totalBruto = totalBruto.add(dv.getSubtotal());
        }
        BigDecimal desc = fidelizacionCoordinator.calcularDescuentoCumpleanos(totalBruto);
        this.descuentoActivo = desc;
        this.descuentoManualFijado = true;
        if (lblAlertaCumplePos != null && clienteSeleccionadoPos != null) {
            lblAlertaCumplePos.setText("Cumpleaños · -10% aplicado");
        }
        if (btnAplicarCumplePos != null) {
            btnAplicarCumplePos.setText("Aplicado");
            btnAplicarCumplePos.setDisable(true);
        }
        recalcularTotales();
        return desc;
    }

    @FXML
    public void handleCanjearPuntosPos() {
        if (clienteSeleccionadoPos == null) {
            mostrarAlerta("Cliente requerido", "Busque o registre un cliente para canjear puntos.", Alert.AlertType.WARNING);
            return;
        }
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Carrito vacío", "Agregue productos antes de canjear puntos.", Alert.AlertType.WARNING);
            return;
        }
        BigDecimal basePreCanje = obtenerBasePreCanjeActual();
        FidelizacionService.BeneficioCanje check = fidelizacionCoordinator.calcularCanjePorBloques(
                clienteSeleccionadoPos.getPuntosAcumulados(), 1, basePreCanje);
        if (check.bloquesDisponibles() <= 0) {
            mostrarAlerta("Canje no disponible",
                    "Se requieren al menos " + check.puntosPorBloque() + " puntos y saldo suficiente.",
                    Alert.AlertType.WARNING);
            return;
        }
        if (check.bloquesDisponibles() == 1 || modoCobroDirectoTest || respuestaAutomaticaCumpleanosTest != null) {
            aplicarCanjePuntosPos(1);
            return;
        }
        List<String> opciones = fidelizacionCoordinator.construirOpcionesCanje(check);
        Optional<String> sel = DialogService.crearChoiceDialog(
                opciones.get(0),
                opciones,
                "Canjear Puntos de Fidelización",
                clienteSeleccionadoPos.getRazonSocial() + " · Saldo: " + clienteSeleccionadoPos.getPuntosAcumulados() + " pts",
                "Seleccione el canje (no altera IGV):").showAndWait();
        if (sel.isPresent()) {
            int idx = Math.max(0, opciones.indexOf(sel.get()));
            aplicarCanjePuntosPos(idx + 1);
        }
    }

    /**
     * Aplica un canje de N bloques de puntos al total actual sin modificar base imponible ni IGV (SPEC v5.0.0 §2.3).
     */
    public FidelizacionService.BeneficioCanje aplicarCanjePuntosPos(int bloques) {
        if (clienteSeleccionadoPos == null || itemsVenta.isEmpty()) {
            return new FidelizacionService.BeneficioCanje(0, ConfiguracionFidelizacion.getPuntosPorCanje(),
                    ConfiguracionFidelizacion.getDescuentoPorCanjeSoles(), 0, BigDecimal.ZERO);
        }
        BigDecimal basePreCanje = obtenerBasePreCanjeActual();
        FidelizacionService.BeneficioCanje beneficio = fidelizacionCoordinator.calcularCanjePorBloques(
                clienteSeleccionadoPos.getPuntosAcumulados(), bloques, basePreCanje);
        this.puntosARedimirPos = beneficio.puntosUtilizados();
        this.descuentoPuntosActivo = beneficio.descuentoAplicadoSoles();
        if (lblClientePosPuntos != null && beneficio.puntosUtilizados() > 0) {
            int restantes = Math.max(0, clienteSeleccionadoPos.getPuntosAcumulados() - beneficio.puntosUtilizados());
            lblClientePosPuntos.setText(restantes + " pts (-" + beneficio.puntosUtilizados() + ")");
        }
        recalcularTotales();
        return beneficio;
    }

    private void limpiarEstadoClienteFidelizacionPos() {
        this.clienteSeleccionadoPos = null;
        this.descuentoPuntosActivo = BigDecimal.ZERO;
        this.puntosARedimirPos = 0;
        this.ultimoMensajeAlertaCumpleanos = null;
        if (txtBuscarClientePos != null) {
            txtBuscarClientePos.clear();
        }
        if (rowClienteAsociadoPos != null) {
            rowClienteAsociadoPos.setVisible(false);
            rowClienteAsociadoPos.setManaged(false);
        }
        if (btnLimpiarClientePos != null) {
            btnLimpiarClientePos.setVisible(false);
            btnLimpiarClientePos.setManaged(false);
        }
        if (btnCanjearPuntosPos != null) {
            btnCanjearPuntosPos.setVisible(false);
            btnCanjearPuntosPos.setManaged(false);
            btnCanjearPuntosPos.setDisable(false);
        }
        if (rowAlertaCumplePos != null) {
            rowAlertaCumplePos.setVisible(false);
            rowAlertaCumplePos.setManaged(false);
        }
        if (btnAplicarCumplePos != null) {
            btnAplicarCumplePos.setText("Aplicar 10%");
            btnAplicarCumplePos.setDisable(false);
        }
        if (rowDescuentoPuntos != null) {
            rowDescuentoPuntos.setVisible(false);
            rowDescuentoPuntos.setManaged(false);
        }
    }

    public Cliente getClienteSeleccionadoPos() {
        return clienteSeleccionadoPos;
    }

    public BigDecimal getDescuentoPuntosActivo() {
        return descuentoPuntosActivo;
    }

    public int getPuntosARedimirPos() {
        return puntosARedimirPos;
    }

    public String getUltimoMensajeAlertaCumpleanos() {
        return ultimoMensajeAlertaCumpleanos;
    }

    public void setRespuestaAutomaticaCumpleanosTest(Boolean respuesta) {
        this.respuestaAutomaticaCumpleanosTest = respuesta;
    }

    @FXML
    public void handleFidelizacionCanje() {
        String inicial = clienteSeleccionadoPos != null
                ? clienteSeleccionadoPos.getNumeroDocumento()
                : (txtBuscarClientePos != null && !txtBuscarClientePos.getText().isBlank() ? txtBuscarClientePos.getText().trim() : "71234567");
        Optional<String> res = DialogService.crearTextInputDialog(
                inicial,
                "Programa de Fidelización",
                "Búsqueda de Cliente por DNI / Teléfono / RUC",
                "Ingrese DNI, Teléfono o RUC:").showAndWait();
        if (res.isPresent() && !res.get().isBlank()) {
            String doc = res.get().trim();
            UiAsyncExecutor.ejecutarAsync(
                () -> fidelizacionCoordinator.buscarClientePorCriterio(doc).orElse(null),
                cliObtenido -> {
                    if (cliObtenido != null) {
                        asociarClientePos(cliObtenido, null);
                    } else {
                        abrirDialogoRegistroRapidoCliente(doc);
                    }
                },
                ex -> abrirDialogoRegistroRapidoCliente(doc)
            );
        }
    }

    public PromocionService getPromocionService() {
        return promocionService;
    }

    public FidelizacionService getFidelizacionService() {
        return fidelizacionService;
    }
}
