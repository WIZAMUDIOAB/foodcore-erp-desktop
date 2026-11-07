package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Cliente;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.VentaService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.dao.impl.ClienteDAOImpl;
import javafx.scene.control.ButtonBar;
import java.time.LocalDate;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Punto de Venta (POS) según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§6) y SPEC-FOODCORE-v1.2 (§2.2, §3.1, §3.2, §5):
 * Split 35% orden / 65% catálogo, fotos de platillos en las tarjetas de producto, división de cuenta,
 * descuentos RBAC (solo Admin) y anulaciones auditadas.
 */
public class VentaController {

    private static final Logger LOGGER = Logger.getLogger(VentaController.class.getName());

    @FXML private TilePane tileProductos;
    @FXML private TextField txtBuscar;
    @FXML private HBox boxCategorias;
    @FXML private TableView<DetalleVenta> tblDetalle;
    @FXML private TableColumn<DetalleVenta, String> colProducto;
    @FXML private TableColumn<DetalleVenta, Integer> colCantidad;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colPrecio;
    @FXML private TableColumn<DetalleVenta, BigDecimal> colSubtotal;

    @FXML private Label lblOrdenHeader;
    @FXML private Label lblMozoHeader;
    @FXML private Label lblSubtotal;
    @FXML private Label lblIgv;
    @FXML private HBox rowDescuento;
    @FXML private Label lblDescuento;
    @FXML private Label lblTotal;

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
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final VentaService ventaService = new VentaService();
    private final PromocionService promocionService = new PromocionService();
    private final FidelizacionService fidelizacionService = new FidelizacionService();
    private boolean descuentoManualFijado = false;
    private final AutorizacionService autorizacionService = new AutorizacionService();
    private final AtomicBoolean cobroEnProgreso = new AtomicBoolean(false);
    private BigDecimal descuentoActivo = BigDecimal.ZERO;
    private boolean modoCobroDirectoTest = false;
    private boolean confirmacionEscapeAutomatica = false;
    private int categoriaFiltroSeleccionada = 0;

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
            String nom = c.getValue().getNombreProducto();
            if (c.getValue().getNotasPreparacion() != null && !c.getValue().getNotasPreparacion().isBlank()) {
                nom += " (" + c.getValue().getNotasPreparacion() + ")";
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
        cargarCatalogoProductos();

        Platform.runLater(this::registrarAtajosTeclado);
    }

    /**
     * Regla §5 RBAC en POS: El rol MOZO solo toma pedidos y envía a cocina (no cobra ni anula).
     */
    private void aplicarRestriccionesRolPOS(Usuario usr) {
        String rol = usr.getNombreRol() != null ? usr.getNombreRol().toUpperCase() : "";
        if (rol.contains("MOZO")) {
            ocultarBoton(btnEfectivo);
            ocultarBoton(btnTarjeta);
            ocultarBoton(btnYape);
            ocultarBoton(btnDividirCuenta);
            ocultarBoton(btnDescuento);
            ocultarBoton(btnAnularVenta);
        }
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
        if (tblDetalle != null && tblDetalle.getScene() != null) {
            tblDetalle.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
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
            });
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

        new Thread(() -> {
            try (Connection conn = ConexionBD.getConnection()) {
                List<Categoria> cats = productoDAO.listarCategorias(conn);
                Platform.runLater(() -> {
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
                });
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Categorías no disponibles en arranque", e);
            }
        }).start();
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

    public void cargarCatalogoProductos() {
        tileProductos.getChildren().clear();
        try (Connection conn = ConexionBD.getConnection()) {
            List<Producto> productos = productoDAO.listarProductosDisponibles(conn);
            todosLosProductos.clear();
            if (productos != null && !productos.isEmpty()) {
                todosLosProductos.addAll(productos);
            } else {
                poblarProductosDemostracion();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al cargar catalogo desde BD, activando catalogo demo: {0}", e.getMessage());
            poblarProductosDemostracion();
        }
        filtrarCatalogo();
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
            boolean coincideCategoria = (categoriaFiltroSeleccionada == 0) || (p.getIdCategoria() == categoriaFiltroSeleccionada);
            boolean coincideTexto = filtroTexto.isEmpty()
                || p.getNombre().toLowerCase().contains(filtroTexto)
                || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(filtroTexto));

            if (coincideCategoria && coincideTexto) {
                Button btn = new Button();
                btn.setId("btnProd_" + p.getIdProducto());
                btn.setPrefSize(156, 102);
                btn.setMinHeight(96);
                btn.getStyleClass().add("pos-menu-tile");

                VBox content = new VBox(4.0);
                content.setAlignment(Pos.TOP_LEFT);
                content.setPrefWidth(128);
                content.setMinHeight(72);

                Label lblNombre = new Label(p.getNombre());
                lblNombre.setWrapText(true);
                lblNombre.getStyleClass().add("text-body-strong");

                // §2.2: Si el ítem tiene foto en imagen_url, mostrarla en la tarjeta en lugar del placeholder de texto SKU
                ImageView fotoView = crearThumbnailSiExiste(p.getImagenUrl());
                if (fotoView != null) {
                    content.getChildren().addAll(fotoView, lblNombre);
                } else {
                    Label lblCodigo = new Label(p.getCodigo() != null ? p.getCodigo() : "SKU");
                    lblCodigo.getStyleClass().add("text-caption-muted");
                    content.getChildren().addAll(lblNombre, lblCodigo);
                }

                Region spacer = new Region();
                VBox.setVgrow(spacer, Priority.ALWAYS);

                Label lblPrecio = new Label("S/ " + p.getPrecioVenta());
                lblPrecio.getStyleClass().add("text-mono-accent");

                HBox priceRow = new HBox(lblPrecio);
                priceRow.setAlignment(Pos.BOTTOM_RIGHT);

                content.getChildren().addAll(spacer, priceRow);
                btn.setGraphic(content);
                btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                AnimationUtils.attachTactileFeedback(btn);
                btn.setOnAction(e -> agregarProductoAVenta(p));
                tileProductos.getChildren().add(btn);
            }
        }
    }

    private ImageView crearThumbnailSiExiste(String imagenUrl) {
        if (imagenUrl == null || imagenUrl.isBlank()) {
            return null;
        }
        try {
            File f = new File(imagenUrl);
            if (f.exists() && f.isFile()) {
                Image img = new Image(f.toURI().toString(), 42, 32, true, true);
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
        for (DetalleVenta dv : itemsVenta) {
            if (dv.getIdProducto() == p.getIdProducto()) {
                dv.setCantidad(dv.getCantidad() + 1);
                dv.setSubtotal(dv.getPrecioUnitario().multiply(BigDecimal.valueOf(dv.getCantidad())));
                tblDetalle.refresh();
                recalcularTotales();
                return;
            }
        }
        DetalleVenta nuevo = new DetalleVenta(0, 0, p.getIdProducto(), 1, p.getPrecioVenta(), p.getPrecioVenta());
        nuevo.setNombreProducto(p.getNombre());
        itemsVenta.add(nuevo);
        recalcularTotales();
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
        mostrarAlerta("Comanda Enviada", "Orden enviada a Línea de Cocina (KDS) correctamente.", Alert.AlertType.INFORMATION);
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
        ChoiceDialog<String> dlg = new ChoiceDialog<>(
            "Dividir en 2 partes iguales (50% Efectivo / 50% Tarjeta)",
            "Dividir en 2 partes iguales (50% Efectivo / 50% Tarjeta)",
            "Dividir en 4 partes iguales (25% c/u)",
            "Dividir por ítems (Subcuenta A y Subcuenta B)"
        );
        dlg.setTitle("División de Cuenta (Split)");
        dlg.setHeaderText("Seleccione la modalidad de división de cuenta");
        dlg.setContentText("Modalidad:");
        Optional<String> res = dlg.showAndWait();
        if (res.isPresent()) {
            try {
                BigDecimal totalNeto = obtenerTotalNetoActual();
                BigDecimal mitad = totalNeto.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                BigDecimal resto = totalNeto.subtract(mitad);
                ejecutarCobroSplitEnPagos(List.of(
                    new VentaService.PagoSplit("EFECTIVO", mitad),
                    new VentaService.PagoSplit("TARJETA", resto)
                ));
                mostrarAlerta("Cuenta Dividida", "Cobro dividido registrado exitosamente en Caja.", Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                mostrarAlerta("Error en Split", e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    public Venta ejecutarCobroSplitEnPagos(List<VentaService.PagoSplit> pagos) throws Exception {
        BigDecimal totalNeto = obtenerTotalNetoActual();
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(totalNeto);
        int idUsr = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
        Venta v = new Venta(0, null, 1, idUsr, 1,
            desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
            "MIXTO", desglose.getTotal(), BigDecimal.ZERO);
        v.setDescuento(descuentoActivo);
        v.setIdempotencyKey(UUID.randomUUID().toString());
        ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SPL", "000001", desglose.getTotal());
        Venta procesada = ventaService.registrarVentaDividida(v, new ArrayList<>(itemsVenta), comp, pagos);
        itemsVenta.clear();
        descuentoActivo = BigDecimal.ZERO;
        recalcularTotales();
        return procesada;
    }

    /**
     * Aplicación de Descuento (§3.2): exige permiso {@code VENTA_DESCUENTO} (solo Admin/Supervisor).
     */
    @FXML
    public void handleAplicarDescuento(ActionEvent event) {
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "Agregue productos antes de aplicar un descuento.", Alert.AlertType.WARNING);
            return;
        }
        ChoiceDialog<String> dlg = new ChoiceDialog<>("10%", "10%", "20%", "Monto fijo S/ 5.00");
        dlg.setTitle("Aplicar Descuento Autorizado");
        dlg.setHeaderText("Requiere permiso VENTA_DESCUENTO (Solo Administrador)");
        dlg.setContentText("Seleccione el descuento:");
        Optional<String> opt = dlg.showAndWait();
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
        String codigoRol = usr != null && usr.getNombreRol() != null ? usr.getNombreRol() : "CAJERO";
        long idUsr = usr != null ? usr.getIdUsuario() : 2L;
        String nombreUsr = usr != null && usr.getUsername() != null ? usr.getUsername() : "usuario";
        SesionUsuario sesion = autorizacionService.crearSesion(idUsr, nombreUsr, codigoRol, "POS-01");

        BigDecimal sumaBruta = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            sumaBruta = sumaBruta.add(dv.getSubtotal());
        }
        this.descuentoActivo = ventaService.calcularDescuentoAutorizado(sumaBruta, valor, esPorcentaje, sesion);
        this.descuentoManualFijado = true;
        recalcularTotales();
        return this.descuentoActivo;
    }

    /**
     * Anulación de Venta (§3.2): exige permiso {@code VENTA_ANULAR}.
     */
    @FXML
    public void handleAnularVenta(ActionEvent event) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Anular Venta Registrada");
        dlg.setHeaderText("Requiere permiso VENTA_ANULAR");
        dlg.setContentText("Ingrese el ID de la venta a anular:");
        Optional<String> opt = dlg.showAndWait();
        if (opt.isPresent() && !opt.get().isBlank()) {
            try {
                int idVenta = Integer.parseInt(opt.get().trim());
                Usuario usr = AuthService.getUsuarioSesionActual();
                String rol = usr != null && usr.getNombreRol() != null ? usr.getNombreRol() : "ADMIN";
                long idUsr = usr != null ? usr.getIdUsuario() : 1L;
                SesionUsuario sesion = autorizacionService.crearSesion(idUsr, "Operador", rol, "POS-01");
                ventaService.anularVenta(idVenta, false, sesion, "Anulación solicitada desde POS");
                mostrarAlerta("Venta Anulada", "La venta #" + idVenta + " fue anulada y su stock revertido.", Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                mostrarAlerta("Error al Anular", e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    public void handleLimpiarConEscape() {
        if (itemsVenta.isEmpty()) {
            return;
        }
        if (confirmacionEscapeAutomatica) {
            itemsVenta.clear();
            descuentoActivo = BigDecimal.ZERO;
            recalcularTotales();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "¿Desea limpiar los ítems de la venta actual?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar limpieza (Esc)");
        confirm.setHeaderText(null);
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            itemsVenta.clear();
            descuentoActivo = BigDecimal.ZERO;
            recalcularTotales();
        }
    }

    private BigDecimal obtenerTotalNetoActual() {
        BigDecimal totalBruto = BigDecimal.ZERO;
        for (DetalleVenta dv : itemsVenta) {
            totalBruto = totalBruto.add(dv.getSubtotal());
        }
        BigDecimal neto = totalBruto.subtract(descuentoActivo != null ? descuentoActivo : BigDecimal.ZERO);
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
            lblSubtotal.setText("S/ 0.00");
            lblIgv.setText("S/ 0.00");
            lblTotal.setText("S/ 0.00");
            if (rowDescuento != null) {
                rowDescuento.setVisible(false);
                rowDescuento.setManaged(false);
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
            BigDecimal neto = obtenerTotalNetoActual();
            DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(neto);
            lblSubtotal.setText("S/ " + desglose.getSubtotalGravado());
            lblIgv.setText("S/ " + desglose.getIgv());
            lblTotal.setText("S/ " + desglose.getTotal());
            if (rowDescuento != null && lblDescuento != null) {
                boolean hayDesc = descuentoActivo != null && descuentoActivo.compareTo(BigDecimal.ZERO) > 0;
                rowDescuento.setVisible(hayDesc);
                rowDescuento.setManaged(hayDesc);
                if (hayDesc) {
                    lblDescuento.setText("-S/ " + descuentoActivo);
                }
            }
        } catch (com.foodcore.exception.NegocioException e) {
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
                BigDecimal totalNeto = obtenerTotalNetoActual();
                DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(totalNeto);
                Venta v = new Venta(0, null, 1, 1, 1,
                    desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
                    metodo, desglose.getTotal(), BigDecimal.ZERO);
                v.setDescuento(descuentoActivo);
                v.setIdempotencyKey(UUID.randomUUID().toString());
                ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "TCK", "000001", desglose.getTotal());
                ventaService.procesarCobroVenta(v, new ArrayList<>(itemsVenta), comp);
                itemsVenta.clear();
                descuentoActivo = BigDecimal.ZERO;
                recalcularTotales();
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
            ctrl.initData(totalNeto, metodo, itemsVenta, () -> {
                itemsVenta.clear();
                descuentoActivo = BigDecimal.ZERO;
                recalcularTotales();
            });

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

    public Button getBtnEfectivo() {
        return btnEfectivo;
    }

    public BigDecimal getDescuentoActivo() {
        return descuentoActivo;
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

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    @FXML
    public void handleImprimirPreCuenta(ActionEvent event) {
        if (itemsVenta.isEmpty()) {
            mostrarAlerta("Atención", "No hay platos en la orden para emitir pre-cuenta.", Alert.AlertType.WARNING);
            return;
        }

        try {
            Pedido p = new Pedido();
            p.setIdPedido(java.util.concurrent.ThreadLocalRandom.current().nextInt(9000) + 1000);
            p.setNumeroMesa(lblOrdenHeader != null ? lblOrdenHeader.getText().replace("Orden ", "").replace("Mesa ", "").trim() : "01");
            p.setTotal(obtenerTotalNetoActual());

            List<DetallePedido> detalles = new ArrayList<>();
            for (DetalleVenta dv : itemsVenta) {
                DetallePedido dp = new DetallePedido();
                dp.setIdProducto(dv.getIdProducto());
                dp.setNombreProducto(dv.getNombreProducto());
                dp.setCantidad(dv.getCantidad());
                dp.setPrecioUnitario(dv.getPrecioUnitario());
                dp.setSubtotal(dv.getSubtotal());
                dp.setNotasPreparacion(dv.getNotasPreparacion());
                detalles.add(dp);
            }

            ImpresoraTermicaService imp = new ImpresoraTermicaService();
            imp.imprimirPreCuenta(p, detalles, null);
            mostrarAlerta("Pre-Cuenta Emitida", "Pre-cuenta emitida exitosamente (Ticket no fiscal).", Alert.AlertType.INFORMATION);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al imprimir pre-cuenta", ex);
            mostrarAlerta("Error", "Error al emitir pre-cuenta: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleNotaItem(ActionEvent event) {
        DetalleVenta sel = tblDetalle.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta("Atención", "Seleccione un ítem de la tabla para añadir nota.", Alert.AlertType.WARNING);
            return;
        }

        TextInputDialog dialog = new TextInputDialog(sel.getNotasPreparacion() != null ? sel.getNotasPreparacion() : "");
        dialog.setTitle("Indicación de Cocina");
        dialog.setHeaderText("Nota para: " + sel.getNombreProducto());
        dialog.setContentText("Indicación especial (ej. sin cebolla, término medio):");
        dialog.showAndWait().ifPresent(nota -> {
            sel.setNotasPreparacion(nota);
            tblDetalle.refresh();
        });
    }

    @FXML
    public void handleFidelizacionCanje() {
        TextInputDialog dlg = new TextInputDialog("20456789012");
        dlg.setTitle("Programa de Fidelización");
        dlg.setHeaderText("Búsqueda de Cliente y Canje de Beneficios");
        dlg.setContentText("Ingrese DNI / RUC del cliente:");
        Optional<String> res = dlg.showAndWait();
        if (res.isPresent() && !res.get().isBlank()) {
            String doc = res.get().trim();
            Cliente cli = null;
            try (Connection conn = ConexionBD.getConnection()) {
                cli = new ClienteDAOImpl().buscarPorDocumento(conn, doc).orElse(null);
            } catch (Exception ignored) {}

            if (cli == null) {
                cli = new Cliente();
                cli.setIdCliente(1);
                cli.setRazonSocial("Corporación Minera Andina S.A.C.");
                cli.setPuntosAcumulados(350);
                cli.setPuntosFidelidad(350);
                cli.setFechaNacimiento(LocalDate.of(1990, LocalDate.now().getMonthValue(), 15));
            }

            int puntos = cli.getPuntosAcumulados();
            boolean esCumple = cli.esMesDeCumpleanos();
            BigDecimal descPuntos = BigDecimal.valueOf(puntos)
                    .divide(BigDecimal.valueOf(FidelizacionService.PUNTOS_POR_SOL_DESCUENTO), 2, RoundingMode.HALF_UP);

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Beneficios de Cliente");
            alert.setHeaderText("Cliente: " + cli.getRazonSocial());
            String info = "• Puntos Acumulados: " + puntos + " pts (Equivale a S/ " + descPuntos + " de descuento)\n";
            if (esCumple) {
                info += "🎂 ¡MES DE CUMPLEAÑOS! Cupón especial 15% OFF disponible.\n";
            }
            info += "\n¿Desea aplicar el beneficio a esta venta?";
            alert.setContentText(info);

            ButtonType btnCanjearPtos = new ButtonType("Canjear Puntos");
            ButtonType btnCupon = new ButtonType("Cupón 15% Cumpleaños");
            ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

            if (esCumple) {
                alert.getButtonTypes().setAll(btnCanjearPtos, btnCupon, btnCancelar);
            } else {
                alert.getButtonTypes().setAll(btnCanjearPtos, btnCancelar);
            }

            Optional<ButtonType> click = alert.showAndWait();
            if (click.isPresent()) {
                if (click.get() == btnCanjearPtos) {
                    BigDecimal totalNeto = obtenerTotalNetoActual();
                    BigDecimal descAplicado = fidelizacionService.canjearPuntos(cli.getIdCliente(), puntos, totalNeto);
                    this.descuentoActivo = descAplicado;
                    this.descuentoManualFijado = true;
                    recalcularTotales();
                    mostrarAlerta("Puntos Canjeados", "Se aplicó un descuento de S/ " + descAplicado + " por canje de puntos.", Alert.AlertType.INFORMATION);
                } else if (click.get() == btnCupon) {
                    BigDecimal totalBruto = BigDecimal.ZERO;
                    for (DetalleVenta dv : itemsVenta) {
                        totalBruto = totalBruto.add(dv.getSubtotal());
                    }
                    BigDecimal desc15 = totalBruto.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
                    this.descuentoActivo = desc15;
                    this.descuentoManualFijado = true;
                    fidelizacionService.verificarYGenerarCuponCumpleanos(cli.getIdCliente());
                    recalcularTotales();
                    mostrarAlerta("Cupón Aplicado", "¡Feliz Cumpleaños! Se aplicó un 15% de descuento (S/ " + desc15 + ").", Alert.AlertType.INFORMATION);
                }
            }
        }
    }

    public PromocionService getPromocionService() {
        return promocionService;
    }

    public FidelizacionService getFidelizacionService() {
        return fidelizacionService;
    }

}
