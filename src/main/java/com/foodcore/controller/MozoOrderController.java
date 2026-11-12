package com.foodcore.controller;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.FormatoPersonalizacionUtil;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Comanda de Salón (Mozo) rediseñado según SPEC-FOODCORE-v3.0.3 (§4):
 * SplitPane 50%/50%, catálogo rápido en TilePane de 2 columnas con tarjetas legibles
 * (Nombre wrapText bold, Precio en #F05A22 monoespaciado y botón +) y tabla de 5 columnas.
 */
public class MozoOrderController {

    @FXML private Label lblTituloMesa;
    @FXML private Label lblMozo;
    @FXML private TilePane tilePlatos;
    @FXML private TableView<DetallePedido> tblItems;
    @FXML private TableColumn<DetallePedido, String> colPlato;
    @FXML private TableColumn<DetallePedido, Integer> colCantidad;
    @FXML private TableColumn<DetallePedido, String> colPrecio;
    @FXML private TableColumn<DetallePedido, String> colSubtotal;
    @FXML private TableColumn<DetallePedido, String> colNotas;

    @FXML private TextField txtNotaEspecial;
    @FXML private Label lblTotal;
    @FXML private Button btnEnviarCocina;

    private static final Logger LOGGER = Logger.getLogger(MozoOrderController.class.getName());

    private String mesa = "01";
    private Integer idPedidoExistente = null;
    private String tipoPedido = "MESA";
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDireccion;
    private String clienteReferencia;
    private BigDecimal costoEnvio = BigDecimal.ZERO;
    private final ObservableList<DetallePedido> items = FXCollections.observableArrayList();
    private final PersonalizacionProductoService personalizacionService = new PersonalizacionProductoServiceImpl();
    private final PedidoService pedidoService = new PedidoService();

    public void setNumeroMesa(String mesa) {
        this.mesa = mesa;
        this.tipoPedido = "MESA";
        lblTituloMesa.setText("Comanda de Salón · Mesa " + mesa);
    }

    /**
     * Precarga una orden activa existente en el modal de Mozo para agregar ítems adicionales
     * mostrando todos los ítems previos y el total acumulado de BD (SPEC-FOODCORE-v4.6.0 §1.1).
     */
    public void cargarPedidoExistente(Pedido pedido) {
        if (pedido == null || pedido.getIdPedido() <= 0) {
            return;
        }
        this.idPedidoExistente = pedido.getIdPedido();
        if (pedido.getNumeroMesa() != null && !pedido.getNumeroMesa().isBlank()) {
            this.mesa = pedido.getNumeroMesa();
        }
        if (lblTituloMesa != null) {
            lblTituloMesa.setText("Comanda #" + this.idPedidoExistente + " · Mesa " + this.mesa);
        }

        List<DetallePedido> detalles = List.of();
        try {
            detalles = pedidoService.listarDetallesPorPedido(this.idPedidoExistente);
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Fallback a detalles en memoria al abrir pedido #" + this.idPedidoExistente, ex);
        }
        if ((detalles == null || detalles.isEmpty()) && pedido.getDetalles() != null) {
            detalles = pedido.getDetalles();
        }
        items.setAll(detalles != null ? detalles : List.of());
        if (tblItems != null) {
            tblItems.refresh();
        }

        try {
            BigDecimal totalActual = pedidoService.calcularTotal(this.idPedidoExistente);
            if (lblTotal != null) {
                lblTotal.setText("Total: S/ " + totalActual.setScale(2, RoundingMode.HALF_UP));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Fallback a cálculo local al cargar pedido #" + this.idPedidoExistente, ex);
            calcularTotal();
        }
    }

    public void configurarParaLlevar(String correlativo) {
        this.mesa = correlativo != null ? correlativo : "LLEVAR";
        this.tipoPedido = "LLEVAR";
        lblTituloMesa.setText("Pedido Para Llevar · " + this.mesa);
    }

    public void configurarDelivery(String correlativo, String cliente, String tel, String dir, String ref, BigDecimal envio) {
        this.mesa = correlativo != null ? correlativo : "DELIVERY";
        this.tipoPedido = "DELIVERY";
        this.clienteNombre = cliente;
        this.clienteTelefono = tel;
        this.clienteDireccion = dir;
        this.clienteReferencia = ref;
        this.costoEnvio = envio != null ? envio : BigDecimal.ZERO;
        lblTituloMesa.setText("Delivery · " + (cliente != null && !cliente.isBlank() ? cliente : this.mesa) + " (Envío S/ " + this.costoEnvio + ")");
    }

    @FXML
    public void initialize() {
        Usuario u = AuthService.getUsuarioSesionActual();
        if (u != null) {
            lblMozo.setText("Mozo: " + u.getUsername() + " (" + u.getNombreRol() + ")");
        } else {
            lblMozo.setText("Mozo: Turno Activo (Mateo Quispe)");
        }

        Label lblPlaceholder = new Label("Aún no hay platos en la comanda");
        lblPlaceholder.getStyleClass().add("text-body-secondary");
        tblItems.setPlaceholder(lblPlaceholder);

        colPlato.setCellValueFactory(c -> new SimpleStringProperty(
                FormatoPersonalizacionUtil.formatearDescripcionItem(c.getValue())));
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colPrecio.setCellValueFactory(c -> {
            BigDecimal p = c.getValue().getPrecioUnitario();
            return new SimpleStringProperty("S/ " + (p != null ? p.setScale(2, RoundingMode.HALF_UP) : "0.00"));
        });
        if (colSubtotal != null) {
            colSubtotal.setCellValueFactory(c -> {
                BigDecimal sub = c.getValue().getSubtotal();
                return new SimpleStringProperty("S/ " + (sub != null ? sub.setScale(2, RoundingMode.HALF_UP) : "0.00"));
            });
        }
        colNotas.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNotasPreparacion()));

        tblItems.setItems(items);
        cargarPlatosDisponiblesAsync();
    }

    public void cargarPlatosDisponiblesAsync() {
        if (tilePlatos == null) {
            return;
        }
        UiAsyncExecutor.ejecutarAsync(
            this::consultarPlatosDisponiblesConRespaldo,
            this::renderizarPlatosEnTilePane,
            ex -> renderizarPlatosEnTilePane(crearCatalogoDemoRespaldo())
        );
    }

    public void cargarPlatosDisponiblesSincrono() {
        if (tilePlatos == null) {
            return;
        }
        renderizarPlatosEnTilePane(consultarPlatosDisponiblesConRespaldo());
    }

    private List<Producto> consultarPlatosDisponiblesConRespaldo() {
        List<Producto> productos = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                productos = personalizacionService.listarProductosDisponiblesConOpciones(conn);
            }
        } catch (java.sql.SQLException | RuntimeException ignored) {
            // Se usará catálogo demostrativo de respaldo
        }
        return productos.isEmpty() ? crearCatalogoDemoRespaldo() : productos;
    }

    private List<Producto> crearCatalogoDemoRespaldo() {
        List<Producto> productos = new ArrayList<>();
        productos.add(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("9.50"), true));
        productos.add(new Producto(2, 1, "HAM-02", "Cheese Burger Clásica", "", new BigDecimal("18.00"), new BigDecimal("6.80"), true));
        productos.add(new Producto(3, 2, "ALI-01", "Alitas BBQ Picantes x6", "", new BigDecimal("22.00"), new BigDecimal("8.50"), true));
        productos.add(new Producto(4, 2, "CRIS-01", "Combo Pollo Crispy 3 Pzs", "", new BigDecimal("26.00"), new BigDecimal("10.20"), true));
        productos.add(new Producto(5, 3, "BEB-01", "Limonada Frozen 1L", "", new BigDecimal("14.00"), new BigDecimal("3.50"), true));
        productos.add(new Producto(6, 3, "BEB-02", "Chicha Morada 1L", "", new BigDecimal("12.00"), new BigDecimal("3.00"), true));
        productos.add(new Producto(7, 4, "COM-01", "Papas Fritas Supremas", "", new BigDecimal("11.50"), new BigDecimal("4.00"), true));
        return productos;
    }

    private void renderizarPlatosEnTilePane(List<Producto> productos) {
        if (tilePlatos == null) {
            return;
        }
        tilePlatos.getChildren().clear();
        for (Producto p : productos) {
            HBox card = new HBox(10.0);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPrefWidth(225.0);
            card.setMinHeight(86.0);
            card.getStyleClass().add("surface-card-interactive");
            card.setStyle("-fx-padding: 12 14;");

            VBox infoBox = new VBox(6.0);
            infoBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            Label lblNombre = new Label(p.getNombre());
            lblNombre.setWrapText(true);
            lblNombre.setMaxWidth(155.0);
            lblNombre.setStyle("-fx-font-family: 'Inter', 'Segoe UI', sans-serif; -fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #111827;");

            BigDecimal precioVal = p.getPrecioVenta() != null ? p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            Label lblPrecio = new Label("S/ " + precioVal);
            lblPrecio.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #F05A22;");

            if (p.isEsCombo()) {
                Label lblCombo = new Label("COMBO");
                lblCombo.setStyle("-fx-background-color: #EEF2FF; -fx-text-fill: #4338CA; "
                        + "-fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 6;");
                infoBox.getChildren().addAll(lblNombre, lblCombo, lblPrecio);
            } else if (p.tieneOpcionesPersonalizacion()) {
                Label lblOpciones = new Label(FormatoPersonalizacionUtil.construirResumenOpciones(p));
                lblOpciones.setStyle("-fx-background-color: #FFF4EC; -fx-text-fill: #C2410C; "
                        + "-fx-font-size: 10px; -fx-font-weight: 700; -fx-padding: 2 6; -fx-background-radius: 6;");
                infoBox.getChildren().addAll(lblNombre, lblOpciones, lblPrecio);
            } else {
                infoBox.getChildren().addAll(lblNombre, lblPrecio);
            }

            Button btnMas = new Button("+");
            btnMas.setFocusTraversable(false);
            btnMas.setStyle("-fx-background-color: #F05A22; -fx-text-fill: #FFFFFF; -fx-font-weight: 800; -fx-font-size: 16px; "
                    + "-fx-min-width: 38px; -fx-pref-width: 38px; -fx-min-height: 38px; -fx-pref-height: 38px; -fx-padding: 0; -fx-background-radius: 8px; -fx-cursor: hand;");
            AnimationUtils.attachTactileFeedback(btnMas);
            btnMas.setOnAction(e -> {
                e.consume();
                agregarPlato(p);
            });

            card.getChildren().addAll(infoBox, btnMas);
            card.setOnMouseClicked(e -> agregarPlato(p));
            tilePlatos.getChildren().add(card);
        }
    }

    public void agregarPlato(Producto p) {
        if (p == null) {
            return;
        }
        Producto resuelto = personalizacionService.resolverOpcionesProducto(p);
        if (resuelto.tieneOpcionesPersonalizacion()) {
            Window owner = tblItems != null && tblItems.getScene() != null
                    ? tblItems.getScene().getWindow()
                    : null;
            Optional<PersonalizacionProductoController.ResultadoPersonalizacion> seleccion =
                    PersonalizacionProductoController.mostrarModal(
                            owner, resuelto, resuelto.getVariantes(), resuelto.getModificadores());
            seleccion.ifPresent(res -> agregarPlatoPersonalizado(
                    res.producto(), res.variante(), res.modificadores(), res.cantidad(), res.notaEspecial()));
            return;
        }
        agregarPlatoPersonalizado(resuelto, null, List.of(), 1, "");
    }

    /**
     * Agrega una línea ya personalizada (variante + modificadores + cantidad) a la comanda del mozo.
     */
    public void agregarPlatoPersonalizado(Producto p,
                                          ProductoVariante variante,
                                          List<ProductoModificador> modificadores,
                                          int cantidad,
                                          String notaEspecial) {
        if (p == null) {
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

        for (DetallePedido dp : items) {
            if (dp.getIdDetallePedido() <= 0) {
                String modsExistentes = FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                        dp.getModificadoresSeleccionados(), dp.getModificadoresTexto());
                if (FormatoPersonalizacionUtil.mismaPersonalizacion(
                        dp.getIdProducto(), dp.getIdVariante(), modsExistentes, dp.getNotasPreparacion(),
                        p.getIdProducto(), idVariante, modsTexto, nota)) {
                    dp.setCantidad(dp.getCantidad() + qty);
                    dp.setSubtotal(dp.getPrecioUnitario().multiply(BigDecimal.valueOf(dp.getCantidad()))
                            .setScale(2, RoundingMode.HALF_UP));
                    if (tblItems != null) {
                        tblItems.refresh();
                    }
                    calcularTotal();
                    return;
                }
            }
        }
        int idPedidoObjetivo = idPedidoExistente != null ? idPedidoExistente : 0;
        DetallePedido nuevo = new DetallePedido(0, idPedidoObjetivo, p.getIdProducto(), qty, precioUnitario, subtotal, nota);
        nuevo.setNombreProducto(p.getNombre());
        nuevo.setIdVariante(idVariante);
        nuevo.setNombreVariante(nombreVariante);
        nuevo.setModificadoresTexto(modsTexto);
        nuevo.setModificadoresSeleccionados(mods);
        if (p.isEsCombo()) {
            nuevo.setIdCombo(p.getIdCombo());
            nuevo.setComboComponentesTexto(
                    FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(p.getComboComponentes()));
        }
        items.add(nuevo);
        if (tblItems != null) {
            tblItems.refresh();
        }
        calcularTotal();
    }

    @FXML
    public void handleAumentarCantidad() {
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getIdDetallePedido() <= 0) {
            sel.setCantidad(sel.getCantidad() + 1);
            sel.setSubtotal(sel.getPrecioUnitario().multiply(BigDecimal.valueOf(sel.getCantidad())));
            tblItems.refresh();
            calcularTotal();
        }
    }

    @FXML
    public void handleDisminuirCantidad() {
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getIdDetallePedido() <= 0) {
            if (sel.getCantidad() > 1) {
                sel.setCantidad(sel.getCantidad() - 1);
                sel.setSubtotal(sel.getPrecioUnitario().multiply(BigDecimal.valueOf(sel.getCantidad())));
                tblItems.refresh();
                calcularTotal();
            } else {
                handleQuitarPlato();
            }
        }
    }

    @FXML
    public void handleQuitarPlato() {
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getIdDetallePedido() <= 0) {
            items.remove(sel);
            calcularTotal();
        }
    }

    @FXML
    public void handleNotaRapida(ActionEvent event) {
        Button b = (Button) event.getSource();
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null) {
            String actual = sel.getNotasPreparacion() != null && !sel.getNotasPreparacion().isEmpty()
                ? sel.getNotasPreparacion() + ", " + b.getText()
                : b.getText();
            sel.setNotasPreparacion(actual);
            tblItems.refresh();
        } else {
            txtNotaEspecial.setText(b.getText());
        }
    }

    @FXML
    public void handleAplicarNota(ActionEvent event) {
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null && !txtNotaEspecial.getText().trim().isEmpty()) {
            sel.setNotasPreparacion(txtNotaEspecial.getText().trim());
            tblItems.refresh();
            txtNotaEspecial.clear();
        }
    }

    private void calcularTotal() {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (DetallePedido dp : items) {
            if (dp.getSubtotal() != null) {
                total = total.add(dp.getSubtotal());
            }
        }
        lblTotal.setText("Total: S/ " + total.setScale(2, RoundingMode.HALF_UP));
    }

    public ObservableList<DetallePedido> getItems() {
        return items;
    }

    public Label getLblTotal() {
        return lblTotal;
    }

    public TableView<DetallePedido> getTblItems() {
        return tblItems;
    }

    public Integer getIdPedidoExistente() {
        return idPedidoExistente;
    }

    @FXML
    public void handleEnviarComanda(ActionEvent event) {
        if (items.isEmpty()) {
            mostrarAlerta("Atención", "Agregue al menos un plato a la comanda.", Alert.AlertType.WARNING);
            return;
        }

        if (idPedidoExistente != null && idPedidoExistente > 0) {
            List<DetallePedido> nuevosItems = items.stream()
                    .filter(dp -> dp.getIdDetallePedido() <= 0)
                    .toList();
            if (nuevosItems.isEmpty()) {
                mostrarAlerta("Atención", "Agregue al menos un plato nuevo para enviar a cocina.", Alert.AlertType.WARNING);
                return;
            }
            if (btnEnviarCocina != null) {
                btnEnviarCocina.setDisable(true);
            }
            final int idObjetivo = idPedidoExistente;
            UiAsyncExecutor.ejecutarAsync(
                () -> {
                    List<DetallePedido> adicionales = new ArrayList<>(nuevosItems);
                    Pedido actualizado = pedidoService.agregarItemsAPedido(idObjetivo, adicionales);
                    BigDecimal totalConfirmado = pedidoService.calcularTotal(idObjetivo);
                    actualizado.setTotal(totalConfirmado);
                    new ImpresoraTermicaService().imprimirComandaCocina(actualizado, adicionales, null);
                    return actualizado;
                },
                actualizado -> {
                    if (btnEnviarCocina != null) {
                        btnEnviarCocina.setDisable(false);
                    }
                    if (actualizado.getDetalles() != null) {
                        items.setAll(actualizado.getDetalles());
                    }
                    if (tblItems != null) {
                        tblItems.refresh();
                    }
                    BigDecimal tot = actualizado.getTotal() != null
                            ? actualizado.getTotal().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    if (lblTotal != null) {
                        lblTotal.setText("Total: S/ " + tot);
                    }
                    mostrarAlerta("Comanda Actualizada",
                        "Se agregaron los nuevos platos al pedido #" + idObjetivo + " de la Mesa " + mesa + ". Total acumulado: S/ " + tot,
                        Alert.AlertType.INFORMATION);
                    if (lblTotal != null && lblTotal.getScene() != null && lblTotal.getScene().getWindow() instanceof Stage s) {
                        s.close();
                        s.setScene(null);
                    }
                },
                e -> {
                    if (btnEnviarCocina != null) {
                        btnEnviarCocina.setDisable(false);
                    }
                    mostrarAlerta("Error al Enviar", e != null ? e.getMessage() : "desconocido", Alert.AlertType.ERROR);
                }
            );
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido dp : items) {
            total = total.add(dp.getSubtotal());
        }

        int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 3;
        Pedido pedido = new Pedido(0, idUsuario, null, mesa, "REGISTRADO", total);
        pedido.setTipoPedido(this.tipoPedido);
        pedido.setClienteNombre(this.clienteNombre);
        pedido.setClienteTelefono(this.clienteTelefono);
        pedido.setClienteDireccion(this.clienteDireccion);
        pedido.setClienteReferencia(this.clienteReferencia);
        pedido.setCostoEnvio(this.costoEnvio);

        List<DetallePedido> snapshot = new ArrayList<>(items);
        if (btnEnviarCocina != null) {
            btnEnviarCocina.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                com.foodcore.offline.SyncWorker worker = com.foodcore.offline.SyncWorker.getInstancia();
                if (worker.isOnline()) {
                    try {
                        Pedido registrado = pedidoService.registrarPedidoYEnviarComanda(pedido, snapshot);
                        new ImpresoraTermicaService().imprimirComandaCocina(registrado, snapshot, null);
                        return "ONLINE";
                    } catch (Exception exNet) {
                        worker.guardarPedidoEnOutboxLocal(pedido, snapshot);
                        return "OFFLINE_FALLBACK";
                    }
                }
                worker.guardarPedidoEnOutboxLocal(pedido, snapshot);
                return "OFFLINE";
            },
            estado -> {
                if (btnEnviarCocina != null) {
                    btnEnviarCocina.setDisable(false);
                }
                if ("OFFLINE_FALLBACK".equals(estado)) {
                    mostrarAlerta("Modo Offline", "Sin conexión con el servidor. El pedido se guardó localmente en la terminal y se sincronizará automáticamente.", Alert.AlertType.INFORMATION);
                } else if ("OFFLINE".equals(estado)) {
                    mostrarAlerta("Modo Offline", "Terminal desconectada. Pedido encolado en Outbox Local SQLite con éxito.", Alert.AlertType.INFORMATION);
                } else {
                    mostrarAlerta("Comanda Despachada", "La comanda de la Mesa " + mesa + " fue registrada en BD y enviada en tiempo real vía Socket TCP 9090 a Cocina.", Alert.AlertType.INFORMATION);
                }
                Stage s = (Stage) lblTotal.getScene().getWindow();
                s.close();
                s.setScene(null);
            },
            e -> {
                if (btnEnviarCocina != null) {
                    btnEnviarCocina.setDisable(false);
                }
                mostrarAlerta("Error al Enviar", e != null ? e.getMessage() : "desconocido", Alert.AlertType.ERROR);
            }
        );
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }
}
