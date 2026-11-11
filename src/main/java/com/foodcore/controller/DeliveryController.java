package com.foodcore.controller;

import com.foodcore.model.Pedido;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Tablero de Entregas y Delivery conforme a SPEC-FOODCORE-v1.3-ENTERPRISE (§3.3).
 * Muestra pedidos en ruta (PENDIENTE, EN_COCINA, EN_CAMINO, ENTREGADO) y asigna repartidores.
 */
public class DeliveryController {

    private static final Logger LOGGER = Logger.getLogger(DeliveryController.class.getName());

    @FXML private StackPane rootDeliveryStack;
    @FXML private Label lblContadorBadge;
    @FXML private Button btnNuevoDeliveryDirecto;
    @FXML private Button btnActualizarTablero;
    @FXML private TextField txtRepartidor;
    @FXML private Button btnAsignarRepartidor;
    @FXML private Button btnPasarEnCamino;
    @FXML private Button btnMarcarEntregado;

    @FXML private TableView<Pedido> tblDelivery;
    @FXML private TableColumn<Pedido, String> colIdPedido;
    @FXML private TableColumn<Pedido, String> colEstado;
    @FXML private TableColumn<Pedido, String> colCliente;
    @FXML private TableColumn<Pedido, String> colTelefono;
    @FXML private TableColumn<Pedido, String> colDireccion;
    @FXML private TableColumn<Pedido, String> colEnvio;
    @FXML private TableColumn<Pedido, String> colTotal;
    @FXML private TableColumn<Pedido, String> colRepartidor;

    private final ObservableList<Pedido> listaPedidosDelivery = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnNuevoDeliveryDirecto);
        AnimationUtils.attachTactileFeedback(btnActualizarTablero);
        AnimationUtils.attachTactileFeedback(btnAsignarRepartidor);
        AnimationUtils.attachTactileFeedback(btnPasarEnCamino);
        AnimationUtils.attachTactileFeedback(btnMarcarEntregado);

        configurarColumnas();
        cargarPedidosDelivery();
    }

    private void configurarColumnas() {
        colIdPedido.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getIdPedido()));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getClienteNombre() != null ? c.getValue().getClienteNombre() : "Cliente General"
        ));
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getClienteTelefono() != null ? c.getValue().getClienteTelefono() : "-"
        ));
        colDireccion.setCellValueFactory(c -> {
            String dir = c.getValue().getClienteDireccion() != null ? c.getValue().getClienteDireccion() : "-";
            String ref = c.getValue().getClienteReferencia();
            return new SimpleStringProperty(ref != null && !ref.isBlank() ? dir + " (" + ref + ")" : dir);
        });
        colEnvio.setCellValueFactory(c -> new SimpleStringProperty(
            "S/ " + (c.getValue().getCostoEnvio() != null ? c.getValue().getCostoEnvio() : BigDecimal.ZERO)
        ));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(
            "S/ " + (c.getValue().getTotal() != null ? c.getValue().getTotal() : BigDecimal.ZERO)
        ));
        colRepartidor.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getRepartidor() != null && !c.getValue().getRepartidor().isBlank()
                ? c.getValue().getRepartidor()
                : "Sin Asignar"
        ));

        // Badges visuales por estado de entrega
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(item);
                switch (item.toUpperCase()) {
                    case "ENTREGADO", "LISTO" -> badge.getStyleClass().add("badge-pill-success");
                    case "EN_CAMINO", "EN_RUTA" -> badge.getStyleClass().add("badge-pill-warning");
                    case "EN_PREPARACION", "EN_COCINA", "PENDIENTE", "REGISTRADO" -> badge.getStyleClass().add("badge-pill-accent");
                    default -> badge.getStyleClass().add("badge-pill-neutral");
                }
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        tblDelivery.setItems(listaPedidosDelivery);
    }

    public void cargarPedidosDeliverySincrono() {
        List<Pedido> pedidos = new ArrayList<>();
        String sql = "SELECT * FROM pedido WHERE tipo_pedido = 'DELIVERY' ORDER BY id_pedido DESC LIMIT 50";
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Pedido p = new Pedido();
                        p.setIdPedido(rs.getInt("id_pedido"));
                        p.setNumeroMesa(rs.getString("numero_mesa"));
                        p.setTipoPedido("DELIVERY");
                        p.setClienteNombre(rs.getString("cliente_nombre"));
                        p.setClienteTelefono(rs.getString("cliente_telefono"));
                        p.setClienteDireccion(rs.getString("cliente_direccion"));
                        p.setClienteReferencia(rs.getString("cliente_referencia"));
                        p.setCostoEnvio(rs.getBigDecimal("costo_envio"));
                        p.setTotal(rs.getBigDecimal("total"));
                        p.setEstado(rs.getString("estado"));
                        p.setRepartidor(rs.getString("repartidor"));
                        pedidos.add(p);
                    }
                }
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al cargar pedidos delivery sincrónicamente: " + e.getMessage());
        }

        listaPedidosDelivery.setAll(pedidos);
        if (lblContadorBadge != null) {
            lblContadorBadge.setText(pedidos.size() + " ACTIVOS");
        }
    }

    public void cargarPedidosDelivery() {
        new Thread(() -> {
            List<Pedido> pedidos = new ArrayList<>();
            String sql = "SELECT * FROM pedido WHERE tipo_pedido = 'DELIVERY' ORDER BY id_pedido DESC LIMIT 50";
            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pedido p = new Pedido();
                    p.setIdPedido(rs.getInt("id_pedido"));
                    p.setNumeroMesa(rs.getString("numero_mesa"));
                    p.setTipoPedido("DELIVERY");
                    p.setClienteNombre(rs.getString("cliente_nombre"));
                    p.setClienteTelefono(rs.getString("cliente_telefono"));
                    p.setClienteDireccion(rs.getString("cliente_direccion"));
                    p.setClienteReferencia(rs.getString("cliente_referencia"));
                    p.setCostoEnvio(rs.getBigDecimal("costo_envio"));
                    p.setTotal(rs.getBigDecimal("total"));
                    p.setEstado(rs.getString("estado"));
                    p.setRepartidor(rs.getString("repartidor"));
                    pedidos.add(p);
                }
            } catch (java.sql.SQLException | RuntimeException e) {
                LOGGER.log(Level.FINE, "Error cargando pedidos delivery", e);
            }

            Platform.runLater(() -> {
                listaPedidosDelivery.setAll(pedidos);
                if (lblContadorBadge != null) {
                    lblContadorBadge.setText(pedidos.size() + " ACTIVOS");
                }
            });
        }).start();
    }

    @FXML
    public void handleCrearDelivery(ActionEvent event) {
        // Modal directo para registrar delivery desde la vista
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");
        VBox card = new VBox(12);
        card.getStyleClass().add("modal-dialog-surface");
        card.setMaxWidth(460);

        Label lblTitle = new Label("Registrar Pedido Delivery Rápido");
        lblTitle.getStyleClass().add("text-h2");

        TextField txtNom = new TextField(); txtNom.setPromptText("Nombre Cliente (Ej. Lucia Ramos)"); txtNom.getStyleClass().add("input-field");
        TextField txtTel = new TextField(); txtTel.setPromptText("Teléfono / WhatsApp (Ej. 981234567)"); txtTel.getStyleClass().add("input-field");
        TextField txtDir = new TextField(); txtDir.setPromptText("Dirección (Ej. Calle Los Pinos 230)"); txtDir.getStyleClass().add("input-field");
        TextField txtRef = new TextField(); txtRef.setPromptText("Referencia"); txtRef.getStyleClass().add("input-field");
        TextField txtTotal = new TextField("38.50"); txtTotal.setPromptText("Total Pedido"); txtTotal.getStyleClass().add("input-field");
        TextField txtEnvio = new TextField("5.00"); txtEnvio.setPromptText("Costo Envío (Sin IGV)"); txtEnvio.getStyleClass().add("input-field");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootDeliveryStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Confirmar y Enviar a Cocina");
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setOnAction(ev -> {
            final BigDecimal envioVal;
            final BigDecimal totalVal;
            try {
                envioVal = new BigDecimal(txtEnvio.getText().trim());
                totalVal = new BigDecimal(txtTotal.getText().trim());
            } catch (NumberFormatException ex) {
                AnimationUtils.mostrarToast(rootDeliveryStack, "Ingrese montos numéricos válidos.", true);
                return;
            }
            final String nombre = txtNom.getText();
            final String tel = txtTel.getText();
            final String dir = txtDir.getText();
            final String ref = txtRef.getText();
            btnGuardar.setDisable(true);
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> {
                    try (Connection conn = ConexionBD.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO pedido (id_usuario, numero_mesa, tipo_pedido, cliente_nombre, cliente_telefono, " +
                             "cliente_direccion, cliente_referencia, costo_envio, total, estado, idempotency_key) " +
                             "VALUES (1, ?, 'DELIVERY', ?, ?, ?, ?, ?, ?, 'EN_COCINA', UUID())")) {
                        ps.setString(1, "DL-" + (System.currentTimeMillis() % 10000));
                        ps.setString(2, nombre);
                        ps.setString(3, tel);
                        ps.setString(4, dir);
                        ps.setString(5, ref);
                        ps.setBigDecimal(6, envioVal);
                        ps.setBigDecimal(7, totalVal);
                        ps.executeUpdate();
                    }
                },
                () -> {
                    btnGuardar.setDisable(false);
                    rootDeliveryStack.getChildren().remove(backdrop);
                    AnimationUtils.mostrarToast(rootDeliveryStack, "Pedido Delivery registrado y enviado a cocina.", false);
                    cargarPedidosDelivery();
                },
                ex -> {
                    btnGuardar.setDisable(false);
                    AnimationUtils.mostrarToast(rootDeliveryStack, "Error: " + (ex != null ? ex.getMessage() : "desconocido"), true);
                }
            );
        });

        HBox actions = new HBox(12, btnCancelar, btnGuardar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, txtNom, txtTel, txtDir, txtRef, txtTotal, txtEnvio, actions);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootDeliveryStack.getChildren().add(backdrop);
    }

    @FXML
    public void handleAsignarRepartidor(ActionEvent event) {
        Pedido sel = tblDelivery.getSelectionModel().getSelectedItem();
        String rep = txtRepartidor.getText() != null ? txtRepartidor.getText().trim() : "";
        if (sel == null || rep.isBlank()) {
            AnimationUtils.mostrarToast(rootDeliveryStack, "Seleccione un pedido e ingrese el nombre del repartidor.", true);
            return;
        }
        actualizarEstadoYRepartidor(sel.getIdPedido(), sel.getEstado(), rep);
        txtRepartidor.clear();
    }

    @FXML
    public void handlePasarEnCamino(ActionEvent event) {
        Pedido sel = tblDelivery.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootDeliveryStack, "Seleccione un pedido del tablero.", true);
            return;
        }
        actualizarEstadoYRepartidor(sel.getIdPedido(), "EN_CAMINO", sel.getRepartidor());
    }

    @FXML
    public void handleMarcarEntregado(ActionEvent event) {
        Pedido sel = tblDelivery.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootDeliveryStack, "Seleccione un pedido del tablero.", true);
            return;
        }
        actualizarEstadoYRepartidor(sel.getIdPedido(), "ENTREGADO", sel.getRepartidor());
    }

    private void actualizarEstadoYRepartidor(int idPedido, String nuevoEstado, String repartidor) {
        final String repVal = repartidor != null ? repartidor : "";
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "UPDATE pedido SET estado = ?, repartidor = ? WHERE id_pedido = ?")) {
                    ps.setString(1, nuevoEstado);
                    ps.setString(2, repVal);
                    ps.setInt(3, idPedido);
                    ps.executeUpdate();
                }
            },
            () -> {
                AnimationUtils.mostrarToast(rootDeliveryStack, "Pedido #" + idPedido + " actualizado a " + nuevoEstado, false);
                cargarPedidosDelivery();
            },
            ex -> AnimationUtils.mostrarToast(
                    rootDeliveryStack,
                    "Error al actualizar pedido: " + (ex != null ? ex.getMessage() : "desconocido"),
                    true)
        );
    }

    public ObservableList<Pedido> getListaPedidosDelivery() {
        return listaPedidosDelivery;
    }
}
