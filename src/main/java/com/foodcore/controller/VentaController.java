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
import com.foodcore.service.AuthService;
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
import javafx.scene.control.DatePicker;
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
        List<Pedido> ordenesExternas = new ArrayList<>();
        for (Pedido p : pedidosActivosMulticanal) {
            String tipo = determinarCanalPedido(p);
            if ("MESA".equals(tipo)) {
                String numNorm = normalizarNumeroMesa(p.getNumeroMesa());
                pedidosPorMesa.putIfAbsent(numNorm, p);
            } else {
                ordenesExternas.add(p);
            }
        }

        renderizarGridMesasPos();
        renderizarPedidosMulticanal(ordenesExternas);
        renderizarBarraRapidaOrdenes(pedidosActivosMulticanal);
    }

    public static String determinarCanalPedido(Pedido p) {
        if (p == null) {
            return "MESA";
        }
        String tipo = p.getTipoPedido() != null ? p.getTipoPedido().trim().toUpperCase(Locale.ROOT) : "";
        String mesa = p.getNumeroMesa() != null ? p.getNumeroMesa().trim().toUpperCase(Locale.ROOT) : "";
        if (tipo.contains("KIOSCO") || mesa.startsWith("T-") || mesa.contains("KIOSCO")) {
            return "KIOSCO";
        }
        if (tipo.contains("DELIVERY") || mesa.startsWith("DL-") || mesa.contains("DELIVERY")) {
            return "DELIVERY";
        }
        return "MESA";
    }

    public static String construirTextoBadgeCanal(Pedido p) {
        String canal = determinarCanalPedido(p);
        if ("KIOSCO".equals(canal)) {
            return "[KIOSCO]";
        }
        if ("DELIVERY".equals(canal)) {
            return "[DELIVERY]";
        }
        return "[MESA " + normalizarNumeroMesa(p != null ? p.getNumeroMesa() : "01") + "]";
    }

    private static String normalizarNumeroMesa(String raw) {
        if (raw == null || raw.isBlank()) {
            return "01";
        }
        String digits = raw.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return raw.trim();
        }
        int num = Integer.parseInt(digits);
        return String.format("%02d", num);
    }

    private void renderizarGridMesasPos() {
        if (tileMesasPos == null) {
            return;
        }
        tileMesasPos.getChildren().clear();
        int ocupadas = 0;
        LocalDateTime ahoraLima = LocalDateTime.now(ZONA_LIMA);

        for (int i = 1; i <= TOTAL_MESAS_SALON; i++) {
            String numMesa = String.format("%02d", i);
            Pedido pedidoActivo = pedidosPorMesa.get(numMesa);
            boolean ocupada = pedidoActivo != null;
            if (ocupada) {
                ocupadas++;
            }

            VBox card = new VBox(6.0);
            card.setPrefSize(158, 118);
            card.setMinSize(148, 110);
            card.getStyleClass().add(ocupada ? "pos-mesa-card-ocupada" : "pos-mesa-card-libre");
            card.setId("posMesaCard_" + numMesa);

            HBox header = new HBox(6.0);
            header.setAlignment(Pos.CENTER_LEFT);
            Label lblMesa = new Label("Mesa " + numMesa);
            lblMesa.getStyleClass().add("text-body-strong");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label badgeEstado = new Label(ocupada ? "OCUPADA" : "LIBRE");
            badgeEstado.getStyleClass().add(ocupada ? "badge-pill-warning" : "badge-pill-success");
            header.getChildren().addAll(lblMesa, sp, badgeEstado);

            BigDecimal monto = ocupada && pedidoActivo.getTotal() != null
                    ? pedidoActivo.getTotal().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Label lblMonto = new Label("S/ " + monto);
            lblMonto.getStyleClass().add(ocupada ? "text-mono-accent" : "text-mono");

            long minTranscurridos = 0;
            if (ocupada && pedidoActivo.getFechaHora() != null) {
                minTranscurridos = Math.max(0, ChronoUnit.MINUTES.between(pedidoActivo.getFechaHora(), ahoraLima));
            }
            Label lblTiempo = new Label(ocupada ? (minTranscurridos + " min") : "Disponible");
            lblTiempo.getStyleClass().add("text-caption-muted");

            card.getChildren().addAll(header, lblMonto, lblTiempo);
            card.setOnMouseClicked(ev -> seleccionarMesa(numMesa));
            tileMesasPos.getChildren().add(card);
        }

        if (lblResumenMesasPos != null) {
            int libres = TOTAL_MESAS_SALON - ocupadas;
            lblResumenMesasPos.setText(ocupadas + " Ocupadas · " + libres + " Libres");
        }
    }

    private void renderizarPedidosMulticanal(List<Pedido> externos) {
        if (flowPedidosMulticanal == null) {
            return;
        }
        flowPedidosMulticanal.getChildren().clear();
        if (externos == null || externos.isEmpty()) {
            Label vacio = new Label("Sin pedidos de Kiosco o Delivery pendientes de cobro.");
            vacio.getStyleClass().add("text-caption-muted");
            flowPedidosMulticanal.getChildren().add(vacio);
            return;
        }

        for (Pedido p : externos) {
            String badgeTxt = construirTextoBadgeCanal(p);
            String ref = p.getNumeroMesa() != null && !p.getNumeroMesa().isBlank() ? p.getNumeroMesa() : ("#" + p.getIdPedido());
            BigDecimal tot = p.getTotal() != null ? p.getTotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Button btn = new Button(badgeTxt + " " + ref + " · S/ " + tot);
            btn.getStyleClass().add("KIOSCO".equals(determinarCanalPedido(p)) ? "badge-canal-kiosco" : "badge-canal-delivery");
            btn.setOnAction(ev -> {
                cargarPedidoEnPOS(p);
                if (tabPanePosCatalogo != null && tabCatalogo != null) {
                    tabPanePosCatalogo.getSelectionModel().select(tabCatalogo);
                }
            });
            flowPedidosMulticanal.getChildren().add(btn);
        }
    }

    private void renderizarBarraRapidaOrdenes(List<Pedido> activos) {
        if (boxOrdenesEntrantes == null) {
            return;
        }
        boxOrdenesEntrantes.getChildren().clear();
        if (activos == null || activos.isEmpty()) {
            Label info = new Label("Orden manual lista · Mesa " + mesaSeleccionadaActual);
            info.getStyleClass().add("text-caption-muted");
            boxOrdenesEntrantes.getChildren().add(info);
            return;
        }

        for (Pedido p : activos) {
            String canal = determinarCanalPedido(p);
            String badge = construirTextoBadgeCanal(p);
            BigDecimal tot = p.getTotal() != null ? p.getTotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Button pill = new Button(badge + " S/ " + tot);
            pill.setPadding(new Insets(3, 8, 3, 8));
            if ("KIOSCO".equals(canal)) {
                pill.getStyleClass().add("badge-canal-kiosco");
            } else if ("DELIVERY".equals(canal)) {
                pill.getStyleClass().add("badge-canal-delivery");
            } else {
                pill.getStyleClass().add("badge-canal-mesa");
            }
            pill.setOnAction(ev -> cargarPedidoEnPOS(p));
            boxOrdenesEntrantes.getChildren().add(pill);
        }
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
            if (btnDescuento != null && !autorizacionService.tienePermiso(sesion, Permiso.VENTA_DESCUENTO)) {
                btnDescuento.setDisable(true);
            }
            if (btnAnularVenta != null) {
                // v5.2.0: El CAJERO mantiene habilitado Anular Venta; al pulsar solicita PIN de Supervisor
                btnAnularVenta.setDisable(false);
                if (!autorizacionService.tienePermiso(sesion, Permiso.VENTA_ANULAR)) {
                    btnAnularVenta.setTooltip(new Tooltip("Modo Supervisor: requiere PIN de SUPERVISOR/ADMIN para anular"));
                }
            }
        }
    }

    private SesionUsuario crearSesionActualPOS(Usuario usr) {
        String codigoRol = usr != null && usr.getNombreRol() != null ? usr.getNombreRol() : "CAJERO";
        long idUsr = usr != null ? usr.getIdUsuario() : 2L;
        String nombreUsr = usr != null && usr.getUsername() != null ? usr.getUsername() : "usuario";
        return autorizacionService.crearSesion(idUsr, nombreUsr, codigoRol, "POS-01");
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
        if (boxCategorias == null) {
            return;
        }
        boxCategorias.getChildren().clear();

        Button btnTodos = new Button("Todos");
        btnTodos.getStyleClass().addAll("category-chip", "category-chip-active");
        AnimationUtils.attachTactileFeedback(btnTodos);
        btnTodos.setOnAction(e -> {
            categoriaFiltroSeleccionada = 0;
            actualizarEstiloBotonesCategorias(btnTodos);
            filtrarCatalogo();
        });
        boxCategorias.getChildren().add(btnTodos);

        UiAsyncExecutor.ejecutarAsync(
            () -> posConsultaService.listarCategoriasProductos(),
            cats -> {
                for (Categoria c : cats) {
                    Button btn = new Button(c.getNombre());
                    btn.getStyleClass().add("category-chip");
                    AnimationUtils.attachTactileFeedback(btn);
                    btn.setOnAction(ev -> {
                        categoriaFiltroSeleccionada = c.getIdCategoria();
                        actualizarEstiloBotonesCategorias(btn);
                        filtrarCatalogo();
                    });
                    boxCategorias.getChildren().add(btn);
                }
            },
            ex -> LOGGER.log(Level.FINE, "Categorías no disponibles en arranque", ex)
        );
    }

    private void actualizarEstiloBotonesCategorias(Button activo) {
        boxCategorias.getChildren().forEach(node -> {
            if (node instanceof Button b) {
                b.getStyleClass().remove("category-chip-active");
                if (b == activo) {
                    b.getStyleClass().add("category-chip-active");
                }
            }
        });
    }

    public void cargarCatalogoProductosAsync() {
        tileProductos.getChildren().clear();
        UiAsyncExecutor.ejecutarAsync(
            () -> posConsultaService.listarProductosDisponiblesConOpciones(),
            productos -> {
                todosLosProductos.clear();
                if (productos != null && !productos.isEmpty()) {
                    todosLosProductos.addAll(productos);
                } else {
                    poblarProductosDemostracion();
                }
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
        try {
            List<Producto> productos = posConsultaService.listarProductosDisponiblesConOpciones();
            todosLosProductos.clear();
            if (productos != null && !productos.isEmpty()) {
                todosLosProductos.addAll(productos);
            } else {
                poblarProductosDemostracion();
            }
            filtrarCatalogo();
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Aviso cargando catalogo sincrono POS, activando demo", ex);
            poblarProductosDemostracion();
            filtrarCatalogo();
        }
    }

    private void poblarProductosDemostracion() {
        todosLosProductos.clear();
        todosLosProductos.add(new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "Carne 180g, lechuga, tomate", new BigDecimal("18.00"), new BigDecimal("7.50"), true));
        todosLosProductos.add(new Producto(2, 1, "HAM-02", "Hamburguesa Doble Queso", "Doble carne, cheddar, bacon", new BigDecimal("24.00"), new BigDecimal("10.00"), true));
        todosLosProductos.add(new Producto(3, 2, "PIZ-01", "Pizza Artesanal Familiar", "Mozzarella fior di latte, albahaca", new BigDecimal("35.00"), new BigDecimal("14.00"), true));
        todosLosProductos.add(new Producto(4, 3, "ALI-01", "Alitas BBQ x12", "Alitas crujientes con salsa BBQ ahumada", new BigDecimal("22.00"), new BigDecimal("9.00"), true));
        todosLosProductos.add(new Producto(5, 3, "PAP-01", "Papas Rústicas Nativas", "Con salsa de queso huancaína", new BigDecimal("12.00"), new BigDecimal("4.00"), true));
        todosLosProductos.add(new Producto(6, 4, "BEB-01", "Cerveza Artesanal IPA", "Botella 330ml", new BigDecimal("14.00"), new BigDecimal("5.50"), true));
        todosLosProductos.add(new Producto(7, 4, "BEB-02", "Gaseosa Personal 500ml", "Inca Kola / Coca Cola", new BigDecimal("6.00"), new BigDecimal("2.50"), true));
        todosLosProductos.add(new Producto(8, 4, "BEB-03", "Limonada Frozen 1L", "Jarra clásica", new BigDecimal("10.00"), new BigDecimal("3.00"), true));
    }

    public void filtrarCatalogo() {
        tileProductos.getChildren().clear();
        String filtroTexto = txtBuscar.getText() != null ? txtBuscar.getText().trim().toLowerCase() : "";

        for (Producto p : todosLosProductos) {
            if (!p.isDisponible()) {
                continue;
            }
            boolean coincideCategoria = (categoriaFiltroSeleccionada == 0) || (p.getIdCategoria() == categoriaFiltroSeleccionada);
            boolean coincideTexto = filtroTexto.isEmpty()
                || p.getNombre().toLowerCase().contains(filtroTexto)
                || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(filtroTexto));

            if (coincideCategoria && coincideTexto) {
                HBox card = new HBox(16.0);
                card.setId("btnProd_" + p.getIdProducto());
                card.setAlignment(Pos.CENTER_LEFT);
                card.setMinWidth(295.0);
                card.setPrefWidth(310.0);
                card.getStyleClass().addAll("card", "pos-menu-tile");
                card.setPadding(new Insets(16.0));

                VBox content = new VBox(4.0);
                content.setMinWidth(0.0);
                content.setPrefWidth(150.0);
                content.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(content, Priority.ALWAYS);

                ImageView fotoView = crearThumbnailSiExiste(p.getImagenUrl());
                if (fotoView != null) {
                    content.getChildren().add(fotoView);
                }

                Label lblNombre = new Label(p.getNombre());
                lblNombre.setMinWidth(0.0);
                lblNombre.setWrapText(true);
                lblNombre.getStyleClass().add("h3");

                String descTexto;
                if (p.isEsCombo()) {
                    descTexto = "COMBO · " + (p.getCodigo() != null ? p.getCodigo() : "Paquete");
                } else if (p.tieneOpcionesPersonalizacion()) {
                    descTexto = FormatoPersonalizacionUtil.construirResumenOpciones(p);
                } else if (p.getDescripcion() != null && !p.getDescripcion().isBlank()) {
                    descTexto = p.getDescripcion();
                } else {
                    descTexto = p.getCodigo() != null ? p.getCodigo() : "Platillo";
                }
                Label lblDesc = new Label(descTexto);
                lblDesc.setMinWidth(0.0);
                lblDesc.setWrapText(true);
                lblDesc.getStyleClass().add("text-secondary");

                Label lblPrecio = new Label("S/ " + p.getPrecioVenta());
                lblPrecio.setMinWidth(Region.USE_PREF_SIZE);
                lblPrecio.getStyleClass().add("price");

                content.getChildren().addAll(lblNombre, lblDesc, lblPrecio);

                Button btnAdd = new Button("+ Agregar");
                btnAdd.setMinWidth(Region.USE_PREF_SIZE);
                btnAdd.getStyleClass().add("btn-primary");
                btnAdd.setGraphicTextGap(8.0);
                AnimationUtils.attachTactileFeedback(btnAdd);
                btnAdd.setOnAction(e -> {
                    e.consume();
                    agregarProductoAVenta(p);
                });

                card.getChildren().addAll(content, btnAdd);
                AnimationUtils.attachTactileFeedback(card);
                card.setOnMouseClicked(e -> agregarProductoAVenta(p));
                tileProductos.getChildren().add(card);
            }
        }
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
        Producto productoResuelto = personalizacionService.resolverOpcionesProducto(p);
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
            LOGGER.log(Level.FINE, "Fallback en memoria al agregar ítem al pedido #" + idPedido, ex);
            agregarProductoEnCarritoLocal(
                    p, idVariante, nombreVariante, mods, modsTexto, qty, nota, precioUnitario, subtotal
            );
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
        for (DetalleVenta dv : itemsVenta) {
            String modsExistentes = FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                    dv.getModificadoresSeleccionados(), dv.getModificadoresTexto());
            if (FormatoPersonalizacionUtil.mismaPersonalizacion(
                    dv.getIdProducto(), dv.getIdVariante(), modsExistentes, dv.getNotasPreparacion(),
                    p.getIdProducto(), idVariante, modsTexto, nota)) {
                dv.setCantidad(dv.getCantidad() + qty);
                BigDecimal unit = dv.getPrecioUnitario() != null ? dv.getPrecioUnitario() : precioUnitario;
                dv.setSubtotal(unit.multiply(BigDecimal.valueOf(dv.getCantidad())).setScale(2, RoundingMode.HALF_UP));
                if (tblDetalle != null) {
                    tblDetalle.refresh();
                }
                recalcularTotales();
                return;
            }
        }
        DetalleVenta nuevo = new DetalleVenta(0, 0, p.getIdProducto(), qty, precioUnitario, subtotal);
        nuevo.setNombreProducto(p.getNombre());
        nuevo.setIdVariante(idVariante);
        nuevo.setNombreVariante(nombreVariante);
        nuevo.setModificadoresTexto(modsTexto);
        nuevo.setModificadoresSeleccionados(mods);
        nuevo.setNotasPreparacion(nota);
        if (p.isEsCombo()) {
            nuevo.setIdCombo(p.getIdCombo());
            nuevo.setComboComponentesTexto(
                    FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(p.getComboComponentes()));
        }
        itemsVenta.add(nuevo);
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
        if (sel != null) {
            if (sel.getCantidad() > 1) {
                sel.setCantidad(sel.getCantidad() - 1);
                sel.setSubtotal(sel.getPrecioUnitario().multiply(BigDecimal.valueOf(sel.getCantidad())));
                tblDetalle.refresh();
            } else {
                itemsVenta.remove(sel);
            }
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
        pedido.setIdUsuario(AuthService.getUsuarioSesionActual() != null
                ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1);
        pedido.setNumeroMesa(mesaSeleccionadaActual != null ? mesaSeleccionadaActual : "01");
        pedido.setTipoPedido(canalSeleccionadoActual != null ? canalSeleccionadoActual : "MESA");
        pedido.setEstado("REGISTRADO");
        pedido.setTotal(obtenerTotalNetoActual());

        if (btnEnviarCocina != null) {
            btnEnviarCocina.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                Pedido pedidoFinal = pedido;
                if (idObjetivo <= 0) {
                    pedidoFinal = pedidoService.registrarPedidoYEnviarComanda(pedido, detallesCocina);
                }
                boolean impresionOk = true;
                try {
                    new ImpresoraTermicaService().imprimirComandaPorCategorias(pedidoFinal, detallesCocina);
                } catch (Exception exImp) {
                    impresionOk = false;
                    LOGGER.log(Level.WARNING, "Comanda persistida pero falló la impresión térmica", exImp);
                }
                return new ResultadoEnvioComanda(pedidoFinal, impresionOk);
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

    private record ResultadoEnvioComanda(Pedido pedido, boolean impresionOk) {
    }

    /**
     * Construye los pagos divididos respetando la modalidad seleccionada por el usuario y sumando exactamente
     * {@code totalNeto} al centavo.
     */
    public static List<VentaService.PagoSplit> construirPagosPorModalidad(String modalidad,
                                                                          BigDecimal totalNeto,
                                                                          List<DetalleVenta> items) {
        if (totalNeto == null || totalNeto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El total neto a dividir debe ser mayor a cero.");
        }
        BigDecimal total = totalNeto.setScale(2, RoundingMode.HALF_UP);
        if (MODALIDAD_SPLIT_4.equals(modalidad)) {
            BigDecimal cuarta = total.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
            BigDecimal acum = cuarta.multiply(new BigDecimal("3"));
            BigDecimal ultimo = total.subtract(acum);
            if (ultimo.compareTo(BigDecimal.ZERO) <= 0) {
                cuarta = total.divide(new BigDecimal("4"), 2, RoundingMode.DOWN);
                ultimo = total.subtract(cuarta.multiply(new BigDecimal("3")));
            }
            List<VentaService.PagoSplit> pagos4 = new ArrayList<>();
            String[] metodos = {"EFECTIVO", "TARJETA", "YAPE_PLIN", "EFECTIVO"};
            BigDecimal[] montos = {cuarta, cuarta, cuarta, ultimo};
            for (int i = 0; i < montos.length; i++) {
                if (montos[i].compareTo(BigDecimal.ZERO) > 0) {
                    pagos4.add(new VentaService.PagoSplit(metodos[i], montos[i]));
                }
            }
            return pagos4;
        }
        if (MODALIDAD_SPLIT_ITEMS.equals(modalidad) && items != null && !items.isEmpty()) {
            BigDecimal brutoA = BigDecimal.ZERO;
            BigDecimal brutoB = BigDecimal.ZERO;
            if (items.size() >= 2) {
                for (int i = 0; i < items.size(); i++) {
                    BigDecimal sub = items.get(i) != null && items.get(i).getSubtotal() != null
                            ? items.get(i).getSubtotal() : BigDecimal.ZERO;
                    if (i % 2 == 0) {
                        brutoA = brutoA.add(sub);
                    } else {
                        brutoB = brutoB.add(sub);
                    }
                }
            } else {
                DetalleVenta unico = items.get(0);
                int qty = unico != null ? unico.getCantidad() : 1;
                if (qty >= 2 && unico.getPrecioUnitario() != null) {
                    int qtyA = (qty + 1) / 2;
                    int qtyB = qty - qtyA;
                    brutoA = unico.getPrecioUnitario().multiply(BigDecimal.valueOf(qtyA));
                    brutoB = unico.getPrecioUnitario().multiply(BigDecimal.valueOf(qtyB));
                }
            }
            BigDecimal sumaBruta = brutoA.add(brutoB);
            if (sumaBruta.compareTo(BigDecimal.ZERO) > 0
                    && brutoA.compareTo(BigDecimal.ZERO) > 0
                    && brutoB.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal montoA = total.multiply(brutoA).divide(sumaBruta, 2, RoundingMode.HALF_UP);
                BigDecimal montoB = total.subtract(montoA);
                if (montoA.compareTo(BigDecimal.ZERO) > 0 && montoB.compareTo(BigDecimal.ZERO) > 0) {
                    return List.of(
                            new VentaService.PagoSplit("EFECTIVO", montoA),
                            new VentaService.PagoSplit("TARJETA", montoB)
                    );
                }
            }
        }
        BigDecimal mitad = total.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        BigDecimal resto = total.subtract(mitad);
        return List.of(
                new VentaService.PagoSplit("EFECTIVO", mitad),
                new VentaService.PagoSplit("TARJETA", resto)
        );
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
            int idUsr = AuthService.getUsuarioSesionActual() != null
                    ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
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
                    Venta v = new Venta(0, idPedidoSnap, 1, idUsr, idCliVenta,
                            desglose.getSubtotalGravado(), desglose.getIgv(), totalNeto,
                            "MIXTO", totalNeto, BigDecimal.ZERO);
                    v.setDescuento(descSnap);
                    v.setDescuentoPuntos(descPtsSnap);
                    v.setPuntosACanjear(ptsSnap);
                    v.setIdempotencyKey(UUID.randomUUID().toString());
                    ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SPL", "000001", totalNeto);
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

    public Venta ejecutarCobroSplitEnPagos(List<VentaService.PagoSplit> pagos) throws Exception {
        BigDecimal totalNeto = obtenerTotalNetoActual();
        BigDecimal basePreCanje = obtenerBasePreCanjeActual();
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
        int idUsr = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
        Integer idCliVenta = clienteSeleccionadoPos != null && clienteSeleccionadoPos.getIdCliente() > 0
                ? clienteSeleccionadoPos.getIdCliente() : null;
        Venta v = new Venta(0, pedidoActivoId, 1, idUsr, idCliVenta,
            desglose.getSubtotalGravado(), desglose.getIgv(), totalNeto,
            "MIXTO", totalNeto, BigDecimal.ZERO);
        v.setDescuento(descuentoActivo);
        v.setDescuentoPuntos(descuentoPuntosActivo);
        v.setPuntosACanjear(puntosARedimirPos);
        v.setIdempotencyKey(UUID.randomUUID().toString());
        ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SPL", "000001", totalNeto);
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

        BigDecimal sumaBruta = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            sumaBruta = sumaBruta.add(dv.getSubtotal());
        }
        this.descuentoActivo = ventaService.calcularDescuentoAutorizado(sumaBruta, valor, esPorcentaje, sesion);
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
        if (!autorizacionService.tienePermiso(sesion, Permiso.VENTA_ANULAR)) {
            ModalModoSupervisorHandle modal = construirModalModoSupervisor(0);
            Optional<ButtonType> res = modal.dialog().showAndWait();
            if (res.isPresent() && res.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                String idRaw = modal.txtIdVenta().getText() != null ? modal.txtIdVenta().getText().trim() : "";
                String pinSup = modal.txtPinSupervisor().getText() != null ? modal.txtPinSupervisor().getText().trim() : "";
                String motivo = modal.txtMotivo().getText() != null ? modal.txtMotivo().getText().trim() : "";
                boolean yaPreparado = modal.chkPlatoPreparado().isSelected();
                if (idRaw.isEmpty()) {
                    mostrarAlerta("Dato requerido", "Debe indicar el ID de la venta a anular.", Alert.AlertType.WARNING);
                    return;
                }
                final int idVenta;
                try {
                    idVenta = Integer.parseInt(idRaw);
                } catch (NumberFormatException nfe) {
                    mostrarAlerta("Dato inválido", "El ID de la venta debe ser numérico.", Alert.AlertType.WARNING);
                    return;
                }
                final String motivoFinal = motivo.isEmpty()
                        ? "Anulación autorizada en caja (Modo Supervisor)" : motivo;
                if (btnAnularVenta != null) {
                    btnAnularVenta.setDisable(true);
                }
                UiAsyncExecutor.ejecutarAsync(
                    () -> ventaService.anularVentaConSupervisor(
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
                    ventaService.anularVenta(idVenta, false, sesion, "Anulación solicitada desde POS");
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
        BigDecimal totalBruto = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            totalBruto = totalBruto.add(dv.getSubtotal());
        }
        BigDecimal base = totalBruto.subtract(descuentoActivo != null ? descuentoActivo : BigDecimal.ZERO);
        return base.compareTo(BigDecimal.ZERO) > 0 ? base : BigDecimal.ZERO;
    }

    private BigDecimal obtenerTotalNetoActual() {
        BigDecimal base = obtenerBasePreCanjeActual();
        BigDecimal descPts = descuentoPuntosActivo != null ? descuentoPuntosActivo : BigDecimal.ZERO;
        if (descPts.compareTo(base) > 0) {
            descPts = base;
        }
        BigDecimal neto = base.subtract(descPts);
        return neto.compareTo(BigDecimal.ZERO) > 0 ? neto : BigDecimal.ZERO;
    }

    private void recalcularTotales() {
        BigDecimal totalBruto = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            totalBruto = totalBruto.add(dv.getSubtotal());
        }
        if (totalBruto.compareTo(BigDecimal.ZERO) <= 0) {
            descuentoActivo = BigDecimal.ZERO;
            descuentoManualFijado = false;
            descuentoPuntosActivo = BigDecimal.ZERO;
            puntosARedimirPos = 0;
            lblSubtotal.setText("S/ 0.00");
            lblIgv.setText("S/ 0.00");
            lblTotal.setText("S/ 0.00");
            if (rowDescuento != null) {
                rowDescuento.setVisible(false);
                rowDescuento.setManaged(false);
            }
            if (rowDescuentoPuntos != null) {
                rowDescuentoPuntos.setVisible(false);
                rowDescuentoPuntos.setManaged(false);
            }
            return;
        }

        // Evaluación automática de Promociones si no hay descuento manual forzado (§11)
        if (!descuentoManualFijado && !itemsVenta.isEmpty()) {
            PromocionService.ResultadoEvaluacion resPromo = promocionService.evaluarPromociones(new ArrayList<>(itemsVenta), totalBruto);
            if (resPromo.tieneDescuento()) {
                this.descuentoActivo = resPromo.getDescuentoTotal();
            } else {
                this.descuentoActivo = BigDecimal.ZERO;
            }
        }
        try {
            // La base gravada y el IGV se calculan ANTES del canje de puntos (SPEC v5.0.0 §2.3)
            BigDecimal basePreCanje = obtenerBasePreCanjeActual();
            DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
            lblSubtotal.setText("S/ " + desglose.getSubtotalGravado());
            lblIgv.setText("S/ " + desglose.getIgv());

            BigDecimal descPts = descuentoPuntosActivo != null ? descuentoPuntosActivo : BigDecimal.ZERO;
            if (descPts.compareTo(basePreCanje) > 0) {
                descPts = basePreCanje;
                descuentoPuntosActivo = descPts;
            }
            BigDecimal totalFinal = basePreCanje.subtract(descPts).setScale(2, RoundingMode.HALF_UP);
            lblTotal.setText("S/ " + totalFinal);

            if (rowDescuento != null && lblDescuento != null) {
                boolean hayDesc = descuentoActivo != null && descuentoActivo.compareTo(BigDecimal.ZERO) > 0;
                rowDescuento.setVisible(hayDesc);
                rowDescuento.setManaged(hayDesc);
                if (hayDesc) {
                    lblDescuento.setText("-S/ " + descuentoActivo.setScale(2, RoundingMode.HALF_UP));
                }
            }
            if (rowDescuentoPuntos != null && lblDescuentoPuntos != null) {
                boolean hayDescPts = descPts.compareTo(BigDecimal.ZERO) > 0;
                rowDescuentoPuntos.setVisible(hayDescPts);
                rowDescuentoPuntos.setManaged(hayDescPts);
                if (hayDescPts) {
                    lblDescuentoPuntos.setText("-S/ " + descPts.setScale(2, RoundingMode.HALF_UP));
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
                DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
                Integer idCliVenta = clienteSeleccionadoPos != null && clienteSeleccionadoPos.getIdCliente() > 0
                        ? clienteSeleccionadoPos.getIdCliente() : null;
                Venta v = new Venta(0, pedidoActivoId, 1, 1, idCliVenta,
                    desglose.getSubtotalGravado(), desglose.getIgv(), totalNeto,
                    metodo, totalNeto, BigDecimal.ZERO);
                v.setDescuento(descuentoActivo);
                v.setDescuentoPuntos(descuentoPuntosActivo);
                v.setPuntosACanjear(puntosARedimirPos);
                v.setIdempotencyKey(UUID.randomUUID().toString());
                ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "TCK", "000001", totalNeto);
                ventaService.procesarCobroVenta(v, new ArrayList<>(itemsVenta), comp);
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
                ImpresoraTermicaService imp = new ImpresoraTermicaService();
                imp.imprimirPreCuenta(p, detalles, null);
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
            () -> posConsultaService.buscarClientePorDniOTelefono(criterio).orElse(null),
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
        Dialog<Cliente> dialog = DialogService.crearDialogo(
                "Registro Rápido Cliente Minorista (POS)",
                "Asociar cliente por DNI para acumulación y canje de puntos");
        ButtonType btnRegistrar = new ButtonType("Registrar y Asociar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnRegistrar, ButtonType.CANCEL);

        TextField txtDni = new TextField(prefill != null && prefill.matches("\\d{8}") ? prefill : "");
        txtDni.setPromptText("8 dígitos (ej. 70112233)");
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre completo del cliente");
        TextField txtTel = new TextField(prefill != null && prefill.matches("\\d{9}") ? prefill : "");
        txtTel.setPromptText("Opcional (9 dígitos)");
        DatePicker dpNacimiento = new DatePicker();
        dpNacimiento.setPromptText("Opcional (cumpleaños)");

        VBox form = new VBox(16.0);
        form.setPadding(new Insets(16, 24, 24, 24));
        form.setPrefWidth(460.0);
        form.getChildren().addAll(
                DialogService.construirFilaDobleModal(
                        DialogService.construirCampoModal("DNI *:", txtDni),
                        DialogService.construirCampoModal("Teléfono:", txtTel)),
                DialogService.construirCampoModal("Nombre Completo *:", txtNombre),
                DialogService.construirCampoModal("Fecha Nacimiento:", dpNacimiento));
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(btn -> {
            if (btn != btnRegistrar) {
                return null;
            }
            String dniVal = txtDni.getText().trim();
            String nomVal = txtNombre.getText().trim();
            if (!dniVal.matches("\\d{8}") || nomVal.isEmpty()) {
                return null;
            }
            return registrarClienteRapidoInterno(dniVal, nomVal, txtTel.getText().trim(), dpNacimiento.getValue());
        });

        Optional<Cliente> creado = dialog.showAndWait();
        if (creado.isPresent() && creado.get() != null) {
            asociarClientePos(creado.get(), null);
        } else if (creado.isPresent()) {
            mostrarAlerta("Datos Inválidos", "Ingrese un DNI válido de 8 dígitos y el nombre completo.", Alert.AlertType.WARNING);
        }
    }

    private Cliente registrarClienteRapidoInterno(String dni, String nombreCompleto, String telefono, LocalDate fechaNacimiento) {
        return posConsultaService.buscarORegistrarClienteMinoristaRapido(dni, nombreCompleto, telefono, fechaNacimiento);
    }

    /**
     * Búsqueda síncrona por DNI o teléfono para pruebas determinísticas POS (v5.0.0).
     */
    public Optional<Cliente> buscarYAsociarClientePorCriterioSincrono(String criterio, Boolean confirmarCumpleanos) {
        if (criterio == null || criterio.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<Cliente> cliOpt = posConsultaService.buscarClientePorDniOTelefono(criterio.trim());
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
        if (dni == null || !dni.trim().matches("\\d{8}")) {
            throw new NegocioException("El DNI del cliente minorista debe tener exactamente 8 dígitos.");
        }
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            throw new NegocioException("El nombre completo del cliente minorista es obligatorio.");
        }
        Cliente cli = registrarClienteRapidoInterno(dni.trim(), nombreCompleto.trim(), telefono, fechaNacimiento);
        if (cli == null) {
            throw new NegocioException("No se pudo registrar el cliente minorista en la base de datos.");
        }
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

        boolean esCumple = fidelizacionService.esCumpleanosEnMesActual(cli);
        if (rowAlertaCumplePos != null) {
            rowAlertaCumplePos.setVisible(esCumple);
            rowAlertaCumplePos.setManaged(esCumple);
        }
        if (esCumple) {
            String msg = "¡Hoy es el cumpleaños de " + cli.getRazonSocial() + "! ¿Deseas aplicar un 10% de descuento?";
            this.ultimoMensajeAlertaCumpleanos = msg;
            if (lblAlertaCumplePos != null) {
                lblAlertaCumplePos.setText("Cumpleaños de " + cli.getRazonSocial() + " (10%)");
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
        BigDecimal desc = fidelizacionService.calcularDescuentoCumpleanosPos(totalBruto);
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
        FidelizacionService.BeneficioCanje check = fidelizacionService.calcularCanjePorBloques(
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
        List<String> opciones = new ArrayList<>();
        for (int b = 1; b <= check.bloquesDisponibles(); b++) {
            int pts = b * check.puntosPorBloque();
            BigDecimal desc = check.descuentoPorBloqueSoles().multiply(BigDecimal.valueOf(b)).setScale(2, RoundingMode.HALF_UP);
            opciones.add(b + " bloque(s): " + pts + " pts -> -S/ " + desc);
        }
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
        FidelizacionService.BeneficioCanje beneficio = fidelizacionService.calcularCanjePorBloques(
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
                () -> posConsultaService.buscarClientePorDniOTelefono(doc).orElse(null),
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
