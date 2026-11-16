package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.service.SalonService;
import com.foodcore.service.ReservasService;
import com.foodcore.model.ReservaMesa;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.DialogService;
import com.foodcore.util.UiAsyncExecutor;
import com.foodcore.util.ConexionBD;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Salón y Comedores rediseñado según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§8):
 * Tarjetas >= 120x120px, borde superior de 4px según estado (Libre/Ocupada/Por cobrar),
 * número de mesa en H1 centrado, tiempo de ocupación en caption y hover con traslación 150ms.
 */
public class SalonMesasController {

    private static final Logger LOGGER = Logger.getLogger(SalonMesasController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @FXML private FlowPane flowMesas;

    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();
    private final SalonService salonService = new SalonService();
    private final ReservasService reservasService = new ReservasService();
    private final ImpresoraTermicaService impresoraService = new ImpresoraTermicaService();
    private final Map<String, Pedido> pedidosActivosPorMesa = new HashMap<>();

    @FXML
    public void initialize() {
        cargarMesas();
    }

    public void cargarMesasSincrono() {
        if (flowMesas == null) {
            return;
        }
        pedidosActivosPorMesa.clear();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                List<Pedido> activos = pedidoDAO.listarPedidosActivos(conn);
                for (Pedido p : activos) {
                    if (p.getNumeroMesa() != null) {
                        pedidosActivosPorMesa.put(p.getNumeroMesa(), p);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "No se pudo consultar estado de mesas en BD (usando fallback offline): " + e.getMessage());
        }
        renderizarTarjetasMesas();
    }

    @FXML
    public void cargarMesas() {
        if (flowMesas == null) {
            return;
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                Map<String, Pedido> nuevosPedidos = new HashMap<>();
                try (Connection conn = ConexionBD.getConnection()) {
                    if (conn != null) {
                        List<Pedido> activos = pedidoDAO.listarPedidosActivos(conn);
                        for (Pedido p : activos) {
                            if (p.getNumeroMesa() != null) {
                                nuevosPedidos.put(p.getNumeroMesa(), p);
                            }
                        }
                    }
                }
                return nuevosPedidos;
            },
            nuevosPedidos -> {
                pedidosActivosPorMesa.clear();
                pedidosActivosPorMesa.putAll(nuevosPedidos);
                renderizarTarjetasMesas();
            },
            ex -> LOGGER.log(Level.WARNING, "Error al actualizar estado de mesas en BD", ex)
        );
    }

    public void renderizarTarjetasMesas() {
        if (flowMesas == null) {
            return;
        }
        flowMesas.getChildren().clear();
        LocalDateTime ahoraLima = LocalDateTime.now(ZONA_LIMA);

        for (int i = 1; i <= 12; i++) {
            final String numMesa = String.format("%02d", i);
            boolean tienePedido = pedidosActivosPorMesa.containsKey(numMesa);
            Pedido pedidoActivo = pedidosActivosPorMesa.get(numMesa);

            Button btnMesa = new Button();
            btnMesa.setPrefSize(195, 148);
            btnMesa.setMinSize(160, 140);
            btnMesa.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            VBox box = new VBox(8.0);
            box.setAlignment(Pos.CENTER);

            Label lblBadge = new Label();
            Label lblMesaNum = new Label("Mesa " + numMesa);
            lblMesaNum.getStyleClass().add("text-h1");
            lblMesaNum.setAlignment(Pos.CENTER);

            Label lblTiempoOcupacion = new Label();
            lblTiempoOcupacion.getStyleClass().add("text-caption");

            Label lblImporte = new Label();
            lblImporte.getStyleClass().add("text-mono");

            if (tienePedido && pedidoActivo != null) {
                String estado = pedidoActivo.getEstado() != null ? pedidoActivo.getEstado().toUpperCase() : "OCUPADA";
                boolean porCobrar = "LISTO".equals(estado) || "ENTREGADO".equals(estado) || "POR_COBRAR".equals(estado);

                if (porCobrar) {
                    btnMesa.getStyleClass().add("mesa-card-cobrar");
                    lblBadge.setText("POR COBRAR");
                    lblBadge.getStyleClass().add("badge-pill-warning");
                } else {
                    btnMesa.getStyleClass().add("mesa-card-ocupada");
                    lblBadge.setText("OCUPADA");
                    lblBadge.getStyleClass().add("badge-pill-accent");
                }

                long minOcupacion = calcularMinutosOcupacion(pedidoActivo, ahoraLima);
                lblTiempoOcupacion.setText("Ocupación: " + minOcupacion + " min");
                BigDecimal totalCard = pedidoActivo.getTotal() != null
                        ? pedidoActivo.getTotal().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                lblImporte.setText("S/ " + totalCard);

                box.getChildren().addAll(lblBadge, lblMesaNum, lblTiempoOcupacion, lblImporte);
                btnMesa.setOnAction(e -> gestionarMesaOcupada(numMesa, pedidoActivo));
            } else {
                boolean tieneReserva = false;
                String horaRes = "";
                try {
                    Optional<ReservaMesa> resOpt = reservasService.buscarReservaProximaParaMesa(numMesa, 60);
                    if (resOpt.isPresent()) {
                        tieneReserva = true;
                        horaRes = resOpt.get().getHoraTexto();
                    }
                } catch (Exception ignored) {}

                if (tieneReserva) {
                    btnMesa.getStyleClass().add("mesa-card-reservada");
                    lblBadge.setText("RESERVADA " + horaRes);
                    lblBadge.setStyle("-fx-background-color: #FFFBEB; -fx-text-fill: #D97706; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 2 8;");
                } else {
                    btnMesa.getStyleClass().add("mesa-card-libre");
                    lblBadge.setText("LIBRE");
                }
                lblBadge.getStyleClass().add("badge-pill-neutral");
                lblTiempoOcupacion.setText("Disponible · 4 pax");
                lblImporte.setText("S/ 0.00");
                lblImporte.getStyleClass().setAll("text-mono-sm");

                box.getChildren().addAll(lblBadge, lblMesaNum, lblTiempoOcupacion, lblImporte);
                btnMesa.setOnAction(e -> abrirTomaPedido(numMesa));
            }

            btnMesa.setGraphic(box);
            AnimationUtils.attachHoverLift(btnMesa);
            AnimationUtils.attachTactileFeedback(btnMesa);
            flowMesas.getChildren().add(btnMesa);
        }
    }

    private long calcularMinutosOcupacion(Pedido pedido, LocalDateTime ahoraLima) {
        if (pedido.getFechaHora() == null) {
            return 14L;
        }
        long min = Duration.between(pedido.getFechaHora(), ahoraLima).toMinutes();
        return Math.max(1L, min);
    }

    public Map<String, Pedido> getPedidosActivosPorMesa() {
        return pedidosActivosPorMesa;
    }

    private void abrirTomaPedido(String mesa) {
        abrirTomaPedido(mesa, null);
    }

    private void abrirTomaPedido(String mesa, Pedido pedidoExistente) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MozoOrderView.fxml"));
            Parent root = loader.load();

            MozoOrderController controller = loader.getController();
            controller.setNumeroMesa(mesa);
            if (pedidoExistente != null && pedidoExistente.getIdPedido() > 0) {
                controller.cargarPedidoExistente(pedidoExistente);
            }

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Comanda de Salón - Mesa " + mesa);

            Scene scene = new Scene(root, 1060, 720);
            stage.setMinWidth(960);
            stage.setMinHeight(680);
            App.applyStylesheets(scene);

            AnimationUtils.animateModalEnter(root);

            stage.setScene(scene);
            stage.setOnCloseRequest(ev -> {
                stage.close();
                stage.setScene(null);
            });
            stage.showAndWait();

            cargarMesas();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al abrir toma de pedido", e);
        }
    }

    private void gestionarMesaOcupada(String mesa, Pedido pedido) {
        Alert alert = DialogService.crearAlerta(
                Alert.AlertType.CONFIRMATION,
                "Gestión Operativa · Mesa " + mesa,
                "Mesa " + mesa + " · Comanda Activa #" + pedido.getIdPedido() + " (Total: S/ " + pedido.getTotal() + ")",
                "Estado: " + pedido.getEstado() + "\nSeleccione la operación a ejecutar:");

        ButtonType btnAgregarMas = new ButtonType("Agregar Platos");
        ButtonType btnTransferir = new ButtonType("Transferir Mesa");
        ButtonType btnUnir = new ButtonType("Unir Mesas");
        ButtonType btnPreCuenta = new ButtonType("Pre-Cuenta");
        ButtonType btnCobrarMesa = new ButtonType("Cobrar Mesa");
        ButtonType btnCancelar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnAgregarMas, btnTransferir, btnUnir, btnPreCuenta, btnCobrarMesa, btnCancelar);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            ButtonType sel = result.get();
            if (sel == btnAgregarMas) {
                abrirTomaPedido(mesa, pedido);
            } else if (sel == btnTransferir) {
                ejecutarTransferenciaDesde(mesa);
            } else if (sel == btnUnir) {
                ejecutarUnionDesde(mesa);
            } else if (sel == btnPreCuenta) {
                emitirPreCuentaMesa(mesa, pedido);
            } else if (sel == btnCobrarMesa) {
                abrirLiquidacionFiscalMesa(mesa, pedido);
            }
        }
    }

    public void ejecutarTransferenciaDesde(String mesaOrigen) {
        List<String> destinos = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String m = String.format("%02d", i);
            if (!m.equals(mesaOrigen) && !pedidosActivosPorMesa.containsKey(m)) {
                destinos.add(m);
            }
        }
        if (destinos.isEmpty()) {
            DialogService.mostrarAlerta(Alert.AlertType.WARNING, "Advertencia",
                    "No hay mesas libres disponibles para transferir el pedido.");
            return;
        }

        DialogService.crearChoiceDialog(
                destinos.get(0),
                destinos,
                "Transferir Mesa",
                "Mover comanda activa de Mesa " + mesaOrigen + " a otra mesa",
                "Seleccione Mesa Destino:").showAndWait().ifPresent(dest ->
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> salonService.transferirMesa(mesaOrigen, dest, 1),
                () -> {
                    DialogService.mostrarAlerta(Alert.AlertType.INFORMATION, "Mesa Transferida",
                            "Comanda transferida exitosamente de Mesa " + mesaOrigen + " a Mesa " + dest);
                    cargarMesas();
                },
                ex -> {
                    LOGGER.log(Level.WARNING, "Error al transferir mesa", ex);
                    DialogService.mostrarAlerta(Alert.AlertType.ERROR, "Error al Transferir",
                            "Error al transferir: " + (ex != null ? ex.getMessage() : "desconocido"));
                }
            )
        );
    }

    public void ejecutarUnionDesde(String mesaPrincipal) {
        List<String> disponibles = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String m = String.format("%02d", i);
            if (!m.equals(mesaPrincipal) && !pedidosActivosPorMesa.containsKey(m)) {
                disponibles.add(m);
            }
        }
        if (disponibles.isEmpty()) {
            DialogService.mostrarAlerta(Alert.AlertType.WARNING, "Advertencia",
                    "No hay mesas libres para unir a la Mesa " + mesaPrincipal);
            return;
        }

        DialogService.crearChoiceDialog(
                disponibles.get(0),
                disponibles,
                "Unir Mesas",
                "Unir Mesa Secundaria con Mesa Principal " + mesaPrincipal,
                "Seleccione mesa adicional a unificar:").showAndWait().ifPresent(sec ->
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> salonService.unirMesas(mesaPrincipal, List.of(sec), 1),
                () -> {
                    DialogService.mostrarAlerta(Alert.AlertType.INFORMATION, "Mesas Unidas",
                            "Mesas unidas exitosamente: Mesa " + mesaPrincipal + " + Mesa " + sec);
                    cargarMesas();
                },
                ex -> {
                    LOGGER.log(Level.WARNING, "Error al unir mesas", ex);
                    DialogService.mostrarAlerta(Alert.AlertType.ERROR, "Error al Unir",
                            "Error al unir mesas: " + (ex != null ? ex.getMessage() : "desconocido"));
                }
            )
        );
    }

    public void emitirPreCuentaMesa(String mesa, Pedido pedido) {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    return pedidoDAO.listarDetallesPorPedido(conn, pedido.getIdPedido());
                }
            },
            detalles -> {
                if (detalles.isEmpty()) {
                    DialogService.mostrarAlerta(Alert.AlertType.WARNING, "Advertencia",
                            "La comanda no tiene platos registrados.");
                    return;
                }
                impresoraService.imprimirPreCuenta(pedido, detalles, null);
                DialogService.mostrarAlerta(Alert.AlertType.INFORMATION, "Pre-Cuenta Emitida",
                        "Pre-cuenta emitida e impresa para Mesa " + mesa + " (Comanda #" + pedido.getIdPedido() + ").\n*** NO ES COMPROBANTE DE PAGO ***");
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error emitiendo pre-cuenta", ex);
                DialogService.mostrarAlerta(Alert.AlertType.ERROR, "Error",
                        "Error al emitir pre-cuenta: " + (ex != null ? ex.getMessage() : "desconocido"));
            }
        );
    }

    @FXML
    public void handleTransferirMesaDialog() {
        if (pedidosActivosPorMesa.isEmpty()) {
            DialogService.mostrarAlerta(Alert.AlertType.WARNING, "Advertencia",
                    "No hay mesas ocupadas actualmente para transferir.");
            return;
        }
        List<String> ocupadas = new ArrayList<>(pedidosActivosPorMesa.keySet());
        DialogService.crearChoiceDialog(
                ocupadas.get(0),
                ocupadas,
                "Transferir Mesa",
                "Seleccionar mesa con comanda activa a transferir",
                "Mesa Origen:").showAndWait().ifPresent(this::ejecutarTransferenciaDesde);
    }

    @FXML
    public void handleUnirMesasDialog() {
        if (pedidosActivosPorMesa.isEmpty()) {
            DialogService.mostrarAlerta(Alert.AlertType.WARNING, "Advertencia",
                    "No hay mesas ocupadas actualmente para unir.");
            return;
        }
        List<String> ocupadas = new ArrayList<>(pedidosActivosPorMesa.keySet());
        DialogService.crearChoiceDialog(
                ocupadas.get(0),
                ocupadas,
                "Unir Mesas",
                "Seleccionar mesa principal con comanda activa",
                "Mesa Principal:").showAndWait().ifPresent(this::ejecutarUnionDesde);
    }

    @FXML
    public void handleAbrirReservas() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReservasView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Libro de Reservas y Bloqueo Preventivo de Salón");
            Scene scene = new Scene(root, 1140, 720);
            App.applyStylesheets(scene);
            stage.setScene(scene);
            stage.showAndWait();
            cargarMesas();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error abriendo libro de reservas", ex);
        }
    }

    private void abrirLiquidacionFiscalMesa(String mesa, Pedido pedido) {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    return pedidoDAO.listarDetallesPorPedido(conn, pedido.getIdPedido());
                }
            },
            detallesPed -> {
                if (detallesPed.isEmpty()) {
                    DialogService.mostrarAlerta(Alert.AlertType.WARNING, "Advertencia",
                            "El pedido no contiene ítems para facturar.");
                    return;
                }

                List<DetalleVenta> itemsParaFacturar = new ArrayList<>();
                for (DetallePedido dp : detallesPed) {
                    DetalleVenta dv = new DetalleVenta(
                        0,
                        0,
                        dp.getIdProducto(),
                        dp.getCantidad(),
                        dp.getPrecioUnitario(),
                        dp.getSubtotal()
                    );
                    dv.setNombreProducto(dp.getNombreProducto());
                    dv.setNotasPreparacion(dp.getNotasPreparacion());
                    itemsParaFacturar.add(dv);
                }

                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/CobroModalView.fxml"));
                    Parent root = loader.load();

                    CobroModalController ctrl = loader.getController();
                    ctrl.setIdPedidoAsociado(pedido.getIdPedido());
                    ctrl.initData(pedido.getTotal(), "EFECTIVO", itemsParaFacturar, () ->
                        UiAsyncExecutor.ejecutarVoidAsync(
                            () -> salonService.liberarMesas(pedido.getIdPedido()),
                            this::cargarMesas,
                            ex -> {
                                LOGGER.log(Level.WARNING, "Error al liberar mesas unidas tras cobro", ex);
                                cargarMesas();
                            }
                        )
                    );

                    Stage stage = new Stage();
                    stage.initModality(Modality.APPLICATION_MODAL);
                    stage.setTitle("Liquidación y Facturación · Mesa " + mesa + " (Comanda #" + pedido.getIdPedido() + ")");

                    Scene scene = new Scene(root);
                    App.applyStylesheets(scene);

                    AnimationUtils.animateModalEnter(root);

                    stage.setScene(scene);
                    stage.sizeToScene();
                    stage.setResizable(false);
                    stage.showAndWait();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Error al iniciar liquidación de mesa " + mesa, ex);
                    DialogService.mostrarAlerta(Alert.AlertType.ERROR, "Error",
                            "No se pudo abrir la pasarela de cobro: " + ex.getMessage());
                }
            },
            ex -> {
                LOGGER.log(Level.SEVERE, "Error al iniciar liquidación de mesa " + mesa, ex);
                DialogService.mostrarAlerta(Alert.AlertType.ERROR, "Error",
                        "No se pudo abrir la pasarela de cobro: " + (ex != null ? ex.getMessage() : "desconocido"));
            }
        );
    }

    @FXML
    public void handleNuevoTakeaway() {
        abrirTomaPedido("LLEVAR");
    }

    @FXML
    public void handleNuevoDelivery() {
        abrirTomaPedido("DELIVERY");
    }
}
