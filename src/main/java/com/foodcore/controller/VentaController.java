package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.controller.venta.DefaultVentaCarritoHandler;
import com.foodcore.controller.venta.DefaultVentaCobroCoordinator;
import com.foodcore.controller.venta.DefaultVentaFidelizacionCoordinator;
import com.foodcore.controller.venta.DefaultVentaMesasCanalCoordinator;
import com.foodcore.controller.venta.JavaFxVentaPosInteractionPort;
import com.foodcore.controller.venta.ModalModoSupervisorHandle;
import com.foodcore.controller.venta.VentaCarritoHandler;
import com.foodcore.controller.venta.VentaCatalogoPresenter;
import com.foodcore.controller.venta.VentaCobroCoordinator;
import com.foodcore.controller.venta.VentaFidelizacionCoordinator;
import com.foodcore.controller.venta.VentaMesasCanalCoordinator;
import com.foodcore.controller.venta.VentaMesasCanalUiRenderer;
import com.foodcore.controller.venta.VentaPosFidelizacionHelper;
import com.foodcore.controller.venta.VentaPosInteractionPort;
import com.foodcore.controller.venta.VentaPosMesasCatalogoHelper;
import com.foodcore.controller.venta.VentaPosOrdenHostPort;
import com.foodcore.controller.venta.VentaPosOrdenOperacionesHelper;
import com.foodcore.controller.venta.VentaPosTicketUiHelper;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Cliente;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.VentaPosConsultaService;
import com.foodcore.service.VentaService;
import com.foodcore.util.ImpresoraTermicaService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador del Punto de Venta (POS) que enlaza la vista FXML
 * con los coordinadores y helpers de {@code controller.venta}.
 */
public class VentaController implements LifecycleAwareController,
        VentaPosOrdenHostPort,
        VentaPosFidelizacionHelper.HostCallbacks,
        VentaPosMesasCatalogoHelper.HostCallbacks {

    private static final Logger LOGGER = Logger.getLogger(VentaController.class.getName());
    public static final int TOTAL_MESAS_SALON = VentaPosMesasCatalogoHelper.TOTAL_MESAS_SALON;
    public static final String MODALIDAD_SPLIT_2 = VentaCobroCoordinator.MODALIDAD_SPLIT_2;
    public static final String MODALIDAD_SPLIT_4 = VentaCobroCoordinator.MODALIDAD_SPLIT_4;
    public static final String MODALIDAD_SPLIT_ITEMS = VentaCobroCoordinator.MODALIDAD_SPLIT_ITEMS;
    private static final VentaMesasCanalCoordinator CANAL_STATICS = new DefaultVentaMesasCanalCoordinator(null);
    private static final VentaCobroCoordinator COBRO_STATICS = new DefaultVentaCobroCoordinator(
            null, null, ImpresoraTermicaService::new, Optional::empty);

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
    private final VentaService ventaService;
    private final PromocionService promocionService;
    private final PersonalizacionProductoService personalizacionService;
    private final VentaPosConsultaService posConsultaService;
    private final FidelizacionService fidelizacionService;
    private final AutorizacionService autorizacionService;

    private final VentaMesasCanalCoordinator mesasCanalCoordinator;
    private final VentaMesasCanalUiRenderer mesasCanalUiRenderer;
    private final VentaCatalogoPresenter catalogoPresenter;
    private final VentaCarritoHandler carritoHandler;
    private final VentaCobroCoordinator cobroCoordinator;
    private final VentaFidelizacionCoordinator fidelizacionCoordinator;

    private PedidoService pedidoService;
    private VentaPosInteractionPort interactionPort;
    private VentaPosFidelizacionHelper fidelizacionHelper;
    private VentaPosOrdenOperacionesHelper operacionesHelper;
    private VentaPosMesasCatalogoHelper mesasCatalogoHelper;

    private boolean descuentoManualFijado;
    private BigDecimal descuentoActivo = BigDecimal.ZERO;
    private volatile String ultimaAlertaTitulo;
    private Timeline autoRefreshTimeline;
    private VentaPosTicketUiHelper.AtajosHandle atajosHandle;

    public VentaController() {
        this(AppContainer.getDefault());
    }

    private VentaController(AppContainer c) {
        this(c.ventaService(), c.cajaService(), c.pedidoService(),
                c.resolve(PromocionService.class), c.personalizacionProductoService(),
                c.resolve(VentaPosConsultaService.class), c.fidelizacionService(),
                c.resolve(AutorizacionService.class));
    }

    public VentaController(VentaService ventaService,
                           CajaService cajaService,
                           PedidoService pedidoService,
                           PromocionService promocionService,
                           PersonalizacionProductoService personalizacionService,
                           VentaPosConsultaService posConsultaService,
                           FidelizacionService fidelizacionService,
                           AutorizacionService autorizacionService) {
        this.ventaService = Objects.requireNonNull(ventaService, "ventaService");
        CajaService effectiveCaja = Objects.requireNonNull(cajaService, "cajaService");
        this.pedidoService = Objects.requireNonNull(pedidoService, "pedidoService");
        this.promocionService = Objects.requireNonNull(promocionService, "promocionService");
        this.personalizacionService = Objects.requireNonNull(personalizacionService, "personalizacionService");
        this.posConsultaService = Objects.requireNonNull(posConsultaService, "posConsultaService");
        this.fidelizacionService = Objects.requireNonNull(fidelizacionService, "fidelizacionService");
        this.autorizacionService = Objects.requireNonNull(autorizacionService, "autorizacionService");
        this.mesasCanalCoordinator = new DefaultVentaMesasCanalCoordinator(this.posConsultaService);
        this.mesasCanalUiRenderer = new VentaMesasCanalUiRenderer();
        this.catalogoPresenter = new VentaCatalogoPresenter(this.posConsultaService);
        this.carritoHandler = new DefaultVentaCarritoHandler(this.promocionService, this.ventaService);
        this.cobroCoordinator = new DefaultVentaCobroCoordinator(
                this.ventaService, this.autorizacionService, ImpresoraTermicaService::new,
                effectiveCaja::obtenerCajaActiva);
        this.fidelizacionCoordinator =
                new DefaultVentaFidelizacionCoordinator(this.posConsultaService, this.fidelizacionService);
        this.interactionPort = new JavaFxVentaPosInteractionPort();
    }

    public VentaController(VentaMesasCanalCoordinator mesasCanalCoordinator,
                           VentaMesasCanalUiRenderer mesasCanalUiRenderer,
                           VentaCatalogoPresenter catalogoPresenter,
                           VentaCarritoHandler carritoHandler,
                           VentaCobroCoordinator cobroCoordinator,
                           VentaFidelizacionCoordinator fidelizacionCoordinator) {
        this(mesasCanalCoordinator, mesasCanalUiRenderer, catalogoPresenter, carritoHandler,
                cobroCoordinator, fidelizacionCoordinator, new JavaFxVentaPosInteractionPort());
    }

    public VentaController(VentaMesasCanalCoordinator mesasCanalCoordinator,
                           VentaMesasCanalUiRenderer mesasCanalUiRenderer,
                           VentaCatalogoPresenter catalogoPresenter,
                           VentaCarritoHandler carritoHandler,
                           VentaCobroCoordinator cobroCoordinator,
                           VentaFidelizacionCoordinator fidelizacionCoordinator,
                           VentaPosInteractionPort interactionPort) {
        AppContainer c = AppContainer.getDefault();
        this.ventaService = c.ventaService();
        this.promocionService = c.resolve(PromocionService.class);
        this.personalizacionService = c.personalizacionProductoService();
        this.posConsultaService = c.resolve(VentaPosConsultaService.class);
        this.fidelizacionService = c.fidelizacionService();
        this.autorizacionService = c.resolve(AutorizacionService.class);
        this.pedidoService = c.pedidoService();
        this.mesasCanalCoordinator = Objects.requireNonNull(mesasCanalCoordinator);
        this.mesasCanalUiRenderer = Objects.requireNonNull(mesasCanalUiRenderer);
        this.catalogoPresenter = Objects.requireNonNull(catalogoPresenter);
        this.carritoHandler = Objects.requireNonNull(carritoHandler);
        this.cobroCoordinator = Objects.requireNonNull(cobroCoordinator);
        this.fidelizacionCoordinator = Objects.requireNonNull(fidelizacionCoordinator);
        this.interactionPort = Objects.requireNonNull(interactionPort);
    }

    @FXML
    public void initialize() {
        Usuario usr = AuthService.getUsuarioSesionActual();
        if (usr != null) {
            VentaPosTicketUiHelper.actualizarHeaderUsuario(lblMozoHeader, usr);
            VentaPosTicketUiHelper.aplicarRestriccionesRolPos(usr, cobroCoordinator,
                    btnEfectivo, btnTarjeta, btnYape, btnDividirCuenta, btnDescuento, btnAnularVenta);
        }

        VentaPosTicketUiHelper.configurarTablaDetalle(
                tblDetalle, colProducto, colCantidad, colPrecio, colSubtotal, itemsVenta,
                dv -> {
                    carritoHandler.incrementarItem(dv);
                    if (tblDetalle != null) {
                        tblDetalle.refresh();
                    }
                    recalcularTotales();
                });

        VentaPosTicketUiHelper.adjuntarFeedbackTactil(
                btnDividirCuenta, btnDescuento, btnAnularVenta, btnEnviarCocina, btnEfectivo,
                btnTarjeta, btnYape, btnBuscarClientePos, btnNuevoClientePos, btnCanjearPuntosPos);

        if (txtBuscar != null) {
            txtBuscar.textProperty().addListener((obs, oldVal, newVal) -> filtrarCatalogo());
        }

        mesasCatalogoHelper();
        fidelizacionHelper();
        operacionesHelper();
        mesasCatalogoHelper().cargarCategorias();
        cargarCatalogoProductosAsync();
        refrescarMesasYCanalesAsync();
        iniciarAutoRefrescoPos();
        Platform.runLater(this::registrarAtajosTeclado);
    }

    private VentaPosMesasCatalogoHelper mesasCatalogoHelper() {
        if (mesasCatalogoHelper == null) {
            mesasCatalogoHelper = new VentaPosMesasCatalogoHelper(
                    mesasCanalCoordinator,
                    mesasCanalUiRenderer,
                    catalogoPresenter,
                    new VentaPosMesasCatalogoHelper.ControlesNavegacionPos(
                            tabPanePosCatalogo, tabCatalogo, tabMesas,
                            lblOrdenHeader, lblCanalBadge, lblResumenMesasPos),
                    new VentaPosMesasCatalogoHelper.ControlesMesasPos(
                            tileMesasPos, flowPedidosMulticanal, boxOrdenesEntrantes),
                    new VentaPosMesasCatalogoHelper.ControlesCatalogoPos(
                            tileProductos, txtBuscar, boxCategorias),
                    this);
        }
        return mesasCatalogoHelper;
    }

    private VentaPosFidelizacionHelper fidelizacionHelper() {
        if (fidelizacionHelper == null) {
            fidelizacionHelper = new VentaPosFidelizacionHelper(
                    fidelizacionCoordinator,
                    carritoHandler,
                    itemsVenta,
                    new VentaPosFidelizacionHelper.PanelClienteControls(
                            txtBuscarClientePos, btnBuscarClientePos, rowClienteAsociadoPos,
                            btnLimpiarClientePos, lblClientePosNombre, lblClientePosPuntos, btnCanjearPuntosPos),
                    new VentaPosFidelizacionHelper.BannerFidelizacionControls(
                            rowAlertaCumplePos, lblAlertaCumplePos, btnAplicarCumplePos, rowDescuentoPuntos),
                    this,
                    interactionPort);
        }
        return fidelizacionHelper;
    }

    private VentaPosOrdenOperacionesHelper operacionesHelper() {
        if (operacionesHelper == null) {
            operacionesHelper = new VentaPosOrdenOperacionesHelper(
                    cobroCoordinator,
                    carritoHandler,
                    mesasCanalCoordinator,
                    personalizacionService,
                    this,
                    new VentaPosOrdenOperacionesHelper.ControlesOrden(
                            tblDetalle, tileProductos, lblTotal, btnAnularVenta, btnPreCuenta),
                    new VentaPosOrdenOperacionesHelper.BotonesPago(
                            btnEfectivo, btnTarjeta, btnYape, btnEnviarCocina, btnDividirCuenta),
                    pedidoService,
                    interactionPort);
        }
        return operacionesHelper;
    }

    private void iniciarAutoRefrescoPos() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(5), ev -> refrescarMesasYCanalesAsync()));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    @Override
    public void detenerRecursos() {
        stopTimeline();
    }

    public void stopTimeline() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        VentaPosTicketUiHelper.desregistrarAtajosTeclado(atajosHandle);
        atajosHandle = null;
    }

    public boolean isAtajoTecladoRegistrado() {
        return atajosHandle != null && atajosHandle.escena() != null && atajosHandle.filtro() != null;
    }

    public void registrarAtajosTeclado() {
        atajosHandle = VentaPosTicketUiHelper.registrarAtajosTeclado(
                tblDetalle,
                atajosHandle,
                () -> handleCobroEfectivo(null),
                () -> handleCobroTarjeta(null),
                () -> handleCobroYape(null),
                this::handleLimpiarConEscape);
    }

    @FXML
    public void handleMostrarTabMesas(ActionEvent event) {
        mesasCatalogoHelper().mostrarTabMesas();
    }

    @FXML
    public void handleRefrescarMesasPos(ActionEvent event) {
        refrescarMesasYCanalesAsync();
    }

    @Override
    public void refrescarMesasYCanalesAsync() {
        mesasCatalogoHelper().refrescarMesasYCanalesAsync();
    }

    public void refrescarMesasYCanalesSincrono() {
        mesasCatalogoHelper().refrescarMesasYCanalesSincrono();
    }

    public void aplicarPedidosActivosEnUI(List<Pedido> activos) {
        mesasCatalogoHelper().aplicarPedidosActivosEnUI(activos);
    }

    public static String determinarCanalPedido(Pedido p) {
        return CANAL_STATICS.determinarCanalPedido(p);
    }

    public static String construirTextoBadgeCanal(Pedido p) {
        return CANAL_STATICS.construirTextoBadgeCanal(p);
    }

    public void seleccionarMesa(String numeroMesa) {
        mesasCatalogoHelper().seleccionarMesa(numeroMesa);
    }

    public void cargarPedidoEnPOS(Pedido pedido) {
        mesasCatalogoHelper().cargarPedidoEnPOS(pedido);
    }

    @Override
    public void aplicarDetallesPedidoEnPOS(List<DetallePedido> detalles) {
        itemsVenta.setAll(mesasCanalCoordinator.convertirDetallesPedidoAVenta(detalles));
        descuentoActivo = BigDecimal.ZERO;
        descuentoManualFijado = false;
        fidelizacionHelper().resetDescuentoPuntosLocal();
        if (tblDetalle != null) {
            tblDetalle.refresh();
        }
        recalcularTotales();
    }

    @Override
    public void actualizarCachePedidoActivo(int idPedido, BigDecimal totalPedido, List<DetallePedido> detalles) {
        mesasCatalogoHelper().actualizarCachePedidoActivo(idPedido, totalPedido, detalles);
    }

    public void cargarCatalogoProductosAsync() {
        mesasCatalogoHelper().cargarCatalogoProductosAsync();
    }

    public void cargarCatalogoProductos() {
        cargarCatalogoProductosAsync();
    }

    public void cargarCatalogoProductosSincrono() {
        mesasCatalogoHelper().cargarCatalogoProductosSincrono();
    }

    public void filtrarCatalogo() {
        mesasCatalogoHelper().filtrarCatalogo();
    }

    @Override
    public void agregarProductoAVenta(Producto p) {
        operacionesHelper().agregarProductoAVenta(p);
    }

    public void agregarProductoPersonalizadoAVenta(Producto p, ProductoVariante variante,
            List<ProductoModificador> modificadores, int cantidad, String notaEspecial) {
        operacionesHelper().agregarProductoPersonalizadoAVenta(p, variante, modificadores, cantidad, notaEspecial);
    }

    @FXML
    public void handleEliminarItem() {
        operacionesHelper().handleEliminarItem();
    }

    @FXML
    public void handleEnviarComanda(ActionEvent event) {
        operacionesHelper().handleEnviarComanda();
    }

    public static List<PagoSplit> construirPagosPorModalidad(String modalidad, BigDecimal totalNeto,
            List<DetalleVenta> items) {
        return COBRO_STATICS.construirPagosPorModalidad(modalidad, totalNeto, items);
    }

    @FXML
    public void handleDividirCuenta(ActionEvent event) {
        operacionesHelper().handleDividirCuenta();
    }

    public Venta ejecutarCobroSplitEnPagos(List<PagoSplit> pagos) throws Exception {
        return operacionesHelper().ejecutarCobroSplitEnPagos(pagos);
    }

    @FXML
    public void handleAplicarDescuento(ActionEvent event) {
        operacionesHelper().handleAplicarDescuento();
    }

    public BigDecimal aplicarDescuentoConPermiso(BigDecimal valor, boolean esPorcentaje) throws NegocioException {
        return operacionesHelper().aplicarDescuentoConPermiso(valor, esPorcentaje);
    }

    public ModalModoSupervisorHandle construirModalModoSupervisor(int idVentaPrellenado) {
        return interactionPort.construirModalModoSupervisor(idVentaPrellenado);
    }

    @FXML
    public void handleAnularVenta(ActionEvent event) {
        operacionesHelper().handleAnularVenta();
    }

    public void handleLimpiarConEscape() {
        if (!itemsVenta.isEmpty() && interactionPort.confirmarLimpiezaCarrito()) {
            limpiarCarritoLocalYDescuentos();
            fidelizacionHelper().limpiarEstadoClienteFidelizacionPos();
            setPedidoActivoId(null);
            recalcularTotales();
        }
    }

    @Override
    public void limpiarCarritoLocalYDescuentos() {
        itemsVenta.clear();
        descuentoActivo = BigDecimal.ZERO;
        descuentoManualFijado = false;
        fidelizacionHelper().resetDescuentoPuntosLocal();
    }

    @Override
    public void finalizarOrdenTrasCobro() {
        limpiarCarritoLocalYDescuentos();
        fidelizacionHelper().limpiarEstadoClienteFidelizacionPos();
        setPedidoActivoId(null);
        recalcularTotales();
        refrescarMesasYCanalesAsync();
    }

    @Override
    public void aplicarDescuentoManual(BigDecimal descuento) {
        this.descuentoActivo = descuento;
        this.descuentoManualFijado = true;
    }

    @Override
    public BigDecimal getBasePreCanjeActual() {
        return carritoHandler.obtenerBasePreCanje(itemsVenta, descuentoActivo);
    }

    @Override
    public BigDecimal getTotalNetoActual() {
        return carritoHandler.obtenerTotalNeto(itemsVenta, descuentoActivo,
                fidelizacionHelper().getDescuentoPuntosActivo());
    }

    @Override
    public void recalcularTotales() {
        try {
            BigDecimal descPts = fidelizacionHelper().getDescuentoPuntosActivo();
            VentaCarritoHandler.ResumenTotalesVenta totales = carritoHandler.calcularTotales(
                    itemsVenta, descuentoActivo, descuentoManualFijado, descPts);
            descuentoActivo = totales.descuentoActivo();
            fidelizacionHelper().sincronizarTrasRecalculo(totales.descuentoPuntosAjustado(), totales.totalBruto());
            VentaPosTicketUiHelper.actualizarTotalesUi(totales, descuentoActivo,
                    fidelizacionHelper().getDescuentoPuntosActivo(),
                    lblSubtotal, lblIgv, lblTotal, rowDescuento, lblDescuento,
                    rowDescuentoPuntos, lblDescuentoPuntos);
        } catch (NegocioException e) {
            LOGGER.log(Level.WARNING, "Error calculando desglose fiscal", e);
        }
    }

    @FXML
    public void handleCobroEfectivo(ActionEvent event) {
        operacionesHelper().abrirModalCobro("EFECTIVO");
    }

    @FXML
    public void handleCobroTarjeta(ActionEvent event) {
        operacionesHelper().abrirModalCobro("TARJETA");
    }

    @FXML
    public void handleCobroYape(ActionEvent event) {
        operacionesHelper().abrirModalCobro("YAPE_PLIN");
    }

    @FXML
    public void handleAbrirArqueo(ActionEvent event) {
        operacionesHelper().handleAbrirArqueo();
    }

    @FXML
    public void handleImprimirPreCuenta(ActionEvent event) {
        operacionesHelper().handleImprimirPreCuenta();
    }

    @FXML
    public void handleNotaItem(ActionEvent event) {
        operacionesHelper().handleNotaItem();
    }

    @FXML
    public void handleBuscarClientePos() {
        fidelizacionHelper().handleBuscarClientePos();
    }

    @FXML
    public void handleRegistroRapidoClientePos() {
        fidelizacionHelper().handleRegistroRapidoClientePos();
    }

    @FXML
    public void handleLimpiarClientePos() {
        fidelizacionHelper().handleLimpiarClientePos();
    }

    public void buscarYAsociarClientePorCriterioAsync(String criterio, Consumer<Optional<Cliente>> onCompletado) {
        buscarYAsociarClientePorCriterioAsync(criterio, null, onCompletado);
    }

    public void buscarYAsociarClientePorCriterioAsync(String criterio, Boolean confirmarCumpleanos,
            Consumer<Optional<Cliente>> onCompletado) {
        fidelizacionHelper().buscarYAsociarClientePorCriterioAsync(criterio, confirmarCumpleanos, onCompletado);
    }

    public Cliente registrarYAsociarClienteRapidoPos(String dni, String nombreCompleto, String telefono,
            LocalDate fechaNacimiento) throws NegocioException {
        return fidelizacionHelper().registrarYAsociarClienteRapidoPos(dni, nombreCompleto, telefono, fechaNacimiento);
    }

    @FXML
    public void handleAplicarCuponCumpleanosPos() {
        fidelizacionHelper().aplicarDescuentoCumpleanosPos();
    }

    @FXML
    public void handleCanjearPuntosPos() {
        fidelizacionHelper().handleCanjearPuntosPos();
    }

    @FXML
    public void handleFidelizacionCanje() {
        fidelizacionHelper().handleFidelizacionCanje();
    }

    @Override
    public void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        this.ultimaAlertaTitulo = titulo;
        interactionPort.mostrarAlerta(titulo, mensaje, tipo);
    }

    public void setPedidoService(PedidoService pedidoService) {
        if (pedidoService != null) {
            this.pedidoService = pedidoService;
            if (operacionesHelper != null) {
                operacionesHelper.setPedidoService(pedidoService);
            }
        }
    }

    public void setInteractionPort(VentaPosInteractionPort interactionPort) {
        if (interactionPort != null) {
            this.interactionPort = interactionPort;
            if (operacionesHelper != null) {
                operacionesHelper.setInteractionPort(interactionPort);
            }
            if (fidelizacionHelper != null) {
                fidelizacionHelper.setInteractionPort(interactionPort);
            }
        }
    }

    public boolean isOperacionPedidoEnProgreso() {
        return operacionesHelper != null && operacionesHelper.isOperacionPedidoEnProgreso();
    }

    public boolean isBusquedaClienteEnProgreso() {
        return fidelizacionHelper != null && fidelizacionHelper.isBusquedaClienteEnProgreso();
    }

    @Override
    public ObservableList<DetalleVenta> getItemsVenta() {
        return itemsVenta;
    }

    public List<Producto> getTodosLosProductos() {
        return mesasCatalogoHelper().getTodosLosProductos();
    }

    public TilePane getTileMesasPos() {
        return tileMesasPos;
    }

    public Label getLblCanalBadge() {
        return lblCanalBadge;
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

    @Override
    public Integer getPedidoActivoId() {
        return mesasCatalogoHelper().getPedidoActivoId();
    }

    @Override
    public void setPedidoActivoId(Integer pedidoActivoId) {
        mesasCatalogoHelper().setPedidoActivoId(pedidoActivoId);
    }

    @Override
    public String getMesaSeleccionadaActual() {
        return mesasCatalogoHelper().getMesaSeleccionadaActual();
    }

    @Override
    public String getCanalSeleccionadoActual() {
        return mesasCatalogoHelper().getCanalSeleccionadoActual();
    }

    @Override
    public BigDecimal getDescuentoActivo() {
        return descuentoActivo;
    }

    public String getUltimaAlertaTitulo() {
        return ultimaAlertaTitulo;
    }

    @Override
    public Cliente getClienteSeleccionadoPos() {
        return fidelizacionHelper().getClienteSeleccionadoPos();
    }

    @Override
    public BigDecimal getDescuentoPuntosActivo() {
        return fidelizacionHelper().getDescuentoPuntosActivo();
    }

    @Override
    public int getPuntosARedimirPos() {
        return fidelizacionHelper().getPuntosARedimirPos();
    }

    public String getUltimoMensajeAlertaCumpleanos() {
        return fidelizacionHelper().getUltimoMensajeAlertaCumpleanos();
    }
}
