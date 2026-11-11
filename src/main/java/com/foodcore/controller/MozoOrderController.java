package com.foodcore.controller;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.PedidoService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

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

    private String mesa = "01";
    private String tipoPedido = "MESA";
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDireccion;
    private String clienteReferencia;
    private BigDecimal costoEnvio = BigDecimal.ZERO;
    private final ObservableList<DetallePedido> items = FXCollections.observableArrayList();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final PedidoService pedidoService = new PedidoService();

    public void setNumeroMesa(String mesa) {
        this.mesa = mesa;
        this.tipoPedido = "MESA";
        lblTituloMesa.setText("Comanda de Salón · Mesa " + mesa);
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

        colPlato.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreProducto()));
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
        cargarPlatosDisponiblesSincrono();
    }

    public void cargarPlatosDisponiblesSincrono() {
        if (tilePlatos == null) {
            return;
        }
        List<Producto> productos = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                productos = productoDAO.listarProductosDisponibles(conn);
            }
        } catch (java.sql.SQLException | RuntimeException ignored) {
            // Se usará catálogo demostrativo de respaldo
        }

        if (productos.isEmpty()) {
            productos.add(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("9.50"), true));
            productos.add(new Producto(2, 1, "HAM-02", "Cheese Burger Clásica", "", new BigDecimal("18.00"), new BigDecimal("6.80"), true));
            productos.add(new Producto(3, 2, "ALI-01", "Alitas BBQ Picantes x6", "", new BigDecimal("22.00"), new BigDecimal("8.50"), true));
            productos.add(new Producto(4, 2, "CRIS-01", "Combo Pollo Crispy 3 Pzs", "", new BigDecimal("26.00"), new BigDecimal("10.20"), true));
            productos.add(new Producto(5, 3, "BEB-01", "Limonada Frozen 1L", "", new BigDecimal("14.00"), new BigDecimal("3.50"), true));
            productos.add(new Producto(6, 3, "BEB-02", "Chicha Morada 1L", "", new BigDecimal("12.00"), new BigDecimal("3.00"), true));
            productos.add(new Producto(7, 4, "COM-01", "Papas Fritas Supremas", "", new BigDecimal("11.50"), new BigDecimal("4.00"), true));
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

            infoBox.getChildren().addAll(lblNombre, lblPrecio);

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
        for (DetallePedido dp : items) {
            if (dp.getIdProducto() == p.getIdProducto()) {
                dp.setCantidad(dp.getCantidad() + 1);
                dp.setSubtotal(dp.getPrecioUnitario().multiply(BigDecimal.valueOf(dp.getCantidad())));
                tblItems.refresh();
                calcularTotal();
                return;
            }
        }
        DetallePedido nuevo = new DetallePedido(0, 0, p.getIdProducto(), 1, p.getPrecioVenta(), p.getPrecioVenta(), "");
        nuevo.setNombreProducto(p.getNombre());
        items.add(nuevo);
        calcularTotal();
    }

    @FXML
    public void handleAumentarCantidad() {
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null) {
            sel.setCantidad(sel.getCantidad() + 1);
            sel.setSubtotal(sel.getPrecioUnitario().multiply(BigDecimal.valueOf(sel.getCantidad())));
            tblItems.refresh();
            calcularTotal();
        }
    }

    @FXML
    public void handleDisminuirCantidad() {
        DetallePedido sel = tblItems.getSelectionModel().getSelectedItem();
        if (sel != null) {
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
        if (sel != null) {
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

    @FXML
    public void handleEnviarComanda(ActionEvent event) {
        if (items.isEmpty()) {
            mostrarAlerta("Atención", "Agregue al menos un plato a la comanda.", Alert.AlertType.WARNING);
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

        try {
            com.foodcore.offline.SyncWorker worker = com.foodcore.offline.SyncWorker.getInstancia();
            if (worker.isOnline()) {
                try {
                    pedidoService.registrarPedidoYEnviarComanda(pedido, items);
                } catch (Exception exNet) {
                    worker.guardarPedidoEnOutboxLocal(pedido, items);
                    mostrarAlerta("Modo Offline", "Sin conexión con el servidor. El pedido se guardó localmente en la terminal y se sincronizará automáticamente.", Alert.AlertType.INFORMATION);
                    Stage s = (Stage) lblTotal.getScene().getWindow();
                    s.close();
                    s.setScene(null);
                    return;
                }
            } else {
                worker.guardarPedidoEnOutboxLocal(pedido, items);
                mostrarAlerta("Modo Offline", "Terminal desconectada. Pedido encolado en Outbox Local SQLite con éxito.", Alert.AlertType.INFORMATION);
                Stage s = (Stage) lblTotal.getScene().getWindow();
                s.close();
                s.setScene(null);
                return;
            }
            mostrarAlerta("Comanda Despachada", "La comanda de la Mesa " + mesa + " fue registrada en BD y enviada en tiempo real vía Socket TCP 9090 a Cocina.", Alert.AlertType.INFORMATION);
            Stage s = (Stage) lblTotal.getScene().getWindow();
            s.close();
            s.setScene(null);
        } catch (Exception e) {
            mostrarAlerta("Error al Enviar", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }
}
