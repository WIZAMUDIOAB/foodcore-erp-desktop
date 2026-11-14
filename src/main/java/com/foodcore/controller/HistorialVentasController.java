package com.foodcore.controller;

import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.VentaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Historial de Ventas y Devoluciones Parciales (v5.2.0):
 * permite seleccionar ventas por fecha/búsqueda, marcar ítems individuales y ejecutar
 * {@link VentaService#devolverItemsParcial}.
 */
public class HistorialVentasController {

    private static final Logger LOGGER = Logger.getLogger(HistorialVentasController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private StackPane rootHistorialVentasStack;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private TextField txtBuscarVenta;
    @FXML private Button btnBuscarHistorial;

    @FXML private TableView<Venta> tblVentas;
    @FXML private TableColumn<Venta, String> colVentaId;
    @FXML private TableColumn<Venta, String> colVentaFecha;
    @FXML private TableColumn<Venta, String> colVentaMesa;
    @FXML private TableColumn<Venta, String> colVentaMetodo;
    @FXML private TableColumn<Venta, String> colVentaTotal;
    @FXML private TableColumn<Venta, String> colVentaEstado;

    @FXML private Label lblVentaSeleccionada;
    @FXML private TableView<DetalleVenta> tblDetallesVenta;
    @FXML private TableColumn<DetalleVenta, String> colDetSeleccion;
    @FXML private TableColumn<DetalleVenta, String> colDetProducto;
    @FXML private TableColumn<DetalleVenta, String> colDetCant;
    @FXML private TableColumn<DetalleVenta, String> colDetSubtotal;
    @FXML private TableColumn<DetalleVenta, String> colDetEstado;

    @FXML private TextField txtMotivoDevolucion;
    @FXML private Label lblTotalReembolso;
    @FXML private Button btnDevolucionParcial;

    private final VentaService ventaService = new VentaService();
    private final AutorizacionService autorizacionService = new AutorizacionService();
    private final ObservableList<Venta> ventas = FXCollections.observableArrayList();
    private final ObservableList<DetalleVenta> detalles = FXCollections.observableArrayList();
    private final Set<Integer> idsDetallesSeleccionados = new LinkedHashSet<>();
    private Venta ventaActual;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnBuscarHistorial);
        AnimationUtils.attachTactileFeedback(btnDevolucionParcial);

        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        if (dpDesde != null) {
            dpDesde.setValue(hoy.minusDays(7));
        }
        if (dpHasta != null) {
            dpHasta.setValue(hoy);
        }

        configurarTablas();
        cargarVentasAsync();
    }

    private void configurarTablas() {
        colVentaId.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getIdVenta()));
        colVentaFecha.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFechaHora() != null ? d.getValue().getFechaHora().format(FMT_FECHA_HORA) : ""));
        colVentaMesa.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getIdPedido() != null && d.getValue().getIdPedido() > 0
                        ? "Pedido #" + d.getValue().getIdPedido() : "Directa"));
        colVentaMetodo.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getMetodoPago() != null ? d.getValue().getMetodoPago() : "EFECTIVO"));
        colVentaTotal.setCellValueFactory(d -> new SimpleStringProperty(
                "S/ " + formatearMoneda(d.getValue().getTotal())));
        colVentaEstado.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getEstado() != null ? d.getValue().getEstado() : "COMPLETADA"));

        colVentaEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if ("ANULADA".equalsIgnoreCase(item)) {
                    setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                }
            }
        });

        tblVentas.setItems(ventas);
        tblVentas.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                seleccionarVentaAsync(newSel);
            }
        });

        colDetSeleccion.setCellValueFactory(d -> new SimpleStringProperty(""));
        colDetSeleccion.setCellFactory(col -> new TableCell<>() {
            private final CheckBox chk = new CheckBox();

            {
                setAlignment(Pos.CENTER);
                chk.setOnAction(e -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        DetalleVenta dv = getTableRow().getItem();
                        if (chk.isSelected()) {
                            idsDetallesSeleccionados.add(dv.getIdDetalleVenta());
                        } else {
                            idsDetallesSeleccionados.remove(dv.getIdDetalleVenta());
                        }
                        actualizarResumenReembolso();
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    DetalleVenta dv = getTableRow().getItem();
                    boolean yaDevuelto = dv.isDevuelto()
                            || (ventaActual != null && "ANULADA".equalsIgnoreCase(ventaActual.getEstado()));
                    chk.setDisable(yaDevuelto);
                    chk.setSelected(!yaDevuelto && idsDetallesSeleccionados.contains(dv.getIdDetalleVenta()));
                    setGraphic(chk);
                }
            }
        });

        colDetProducto.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getNombreProducto() != null
                        ? d.getValue().getNombreProducto() : "Producto #" + d.getValue().getIdProducto()));
        colDetCant.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getCantidad())));
        colDetSubtotal.setCellValueFactory(d -> new SimpleStringProperty(
                "S/ " + formatearMoneda(d.getValue().getSubtotal())));
        colDetEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEstado()));
        colDetEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if ("DEVUELTO".equalsIgnoreCase(item)) {
                    setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                }
            }
        });

        tblDetallesVenta.setItems(detalles);
    }

    @FXML
    public void handleBuscarVentas(ActionEvent event) {
        cargarVentasAsync();
    }

    public void cargarVentasAsync() {
        LocalDate desde = dpDesde != null ? dpDesde.getValue() : LocalDate.now(ZONA_LIMA).minusDays(7);
        LocalDate hasta = dpHasta != null ? dpHasta.getValue() : LocalDate.now(ZONA_LIMA);
        String filtro = txtBuscarVenta != null ? txtBuscarVenta.getText() : null;

        UiAsyncExecutor.ejecutarAsync(
            () -> ventaService.listarVentasHistorial(desde, hasta, filtro),
            lista -> aplicarListaVentas(lista),
            ex -> {
                LOGGER.log(Level.WARNING, "Error cargando historial de ventas", ex);
                AnimationUtils.mostrarToast(rootHistorialVentasStack,
                        "No se pudo cargar historial: " + ex.getMessage(), true);
            }
        );
    }

    public void aplicarListaVentas(List<Venta> lista) {
        ventas.setAll(lista != null ? lista : List.of());
        if (!ventas.isEmpty()) {
            tblVentas.getSelectionModel().selectFirst();
        } else {
            mostrarDetalleVenta(null, List.of());
        }
    }

    public void seleccionarVentaAsync(Venta venta) {
        if (venta == null) {
            mostrarDetalleVenta(null, List.of());
            return;
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> ventaService.listarDetallesVenta(venta.getIdVenta()),
            dets -> mostrarDetalleVenta(venta, dets),
            ex -> LOGGER.log(Level.WARNING, "Error cargando detalles de venta #" + venta.getIdVenta(), ex)
        );
    }

    public void mostrarDetalleVenta(Venta venta, List<DetalleVenta> listaDetalles) {
        this.ventaActual = venta;
        idsDetallesSeleccionados.clear();
        detalles.setAll(listaDetalles != null ? listaDetalles : List.of());
        if (lblVentaSeleccionada != null) {
            if (venta == null) {
                lblVentaSeleccionada.setText("Seleccione una venta para revisar sus ítems");
            } else {
                lblVentaSeleccionada.setText("Venta #" + venta.getIdVenta()
                        + " · Total S/ " + formatearMoneda(venta.getTotal())
                        + " (" + (venta.getEstado() != null ? venta.getEstado() : "COMPLETADA") + ")");
            }
        }
        actualizarResumenReembolso();
        if (tblDetallesVenta != null) {
            tblDetallesVenta.refresh();
        }
    }

    public void marcarDetalleParaDevolucion(int idDetalleVenta, boolean seleccionado) {
        if (seleccionado) {
            idsDetallesSeleccionados.add(idDetalleVenta);
        } else {
            idsDetallesSeleccionados.remove(idDetalleVenta);
        }
        actualizarResumenReembolso();
        if (tblDetallesVenta != null) {
            tblDetallesVenta.refresh();
        }
    }

    private void actualizarResumenReembolso() {
        BigDecimal totalReembolso = BigDecimal.ZERO;
        for (DetalleVenta d : detalles) {
            if (idsDetallesSeleccionados.contains(d.getIdDetalleVenta()) && !d.isDevuelto()) {
                totalReembolso = totalReembolso.add(d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO);
            }
        }
        if (lblTotalReembolso != null) {
            lblTotalReembolso.setText("Reembolso seleccionado: S/ " + formatearMoneda(totalReembolso));
        }
        if (btnDevolucionParcial != null) {
            boolean deshabilitar = ventaActual == null || idsDetallesSeleccionados.isEmpty()
                    || "ANULADA".equalsIgnoreCase(ventaActual.getEstado());
            btnDevolucionParcial.setDisable(deshabilitar);
        }
    }

    @FXML
    public void handleDevolucionParcial(ActionEvent event) {
        if (ventaActual == null || idsDetallesSeleccionados.isEmpty()) {
            AnimationUtils.mostrarToast(rootHistorialVentasStack,
                    "Seleccione al menos un ítem activo para devolver.", true);
            return;
        }
        Usuario usr = AuthService.getUsuarioSesionActual();
        long idUsr = usr != null ? usr.getIdUsuario() : 1L;
        String username = usr != null ? usr.getUsername() : "admin";
        String rol = usr != null && usr.getNombreRol() != null ? usr.getNombreRol() : "ADMIN";
        SesionUsuario sesion = autorizacionService.crearSesion(idUsr, username, rol, "POS-HISTORIAL");

        List<Integer> idsSnapshot = new ArrayList<>(idsDetallesSeleccionados);
        String motivo = txtMotivoDevolucion != null ? txtMotivoDevolucion.getText() : "Devolución parcial";
        int idVentaObj = ventaActual.getIdVenta();

        if (btnDevolucionParcial != null) {
            btnDevolucionParcial.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> ventaService.devolverItemsParcial(idVentaObj, idsSnapshot, sesion, motivo),
            res -> {
                if (txtMotivoDevolucion != null) {
                    txtMotivoDevolucion.clear();
                }
                AnimationUtils.mostrarToast(rootHistorialVentasStack,
                        "Devolución parcial Venta #" + res.idVenta()
                                + " · Egreso S/ " + formatearMoneda(res.montoReembolso())
                                + " (" + res.cantidadInsumosRevertidos() + " insumo(s) revertido(s)).",
                        false);
                cargarVentasAsync();
            },
            ex -> {
                actualizarResumenReembolso();
                AnimationUtils.mostrarToast(rootHistorialVentasStack,
                        ex != null ? ex.getMessage() : "Error al procesar devolución parcial.",
                        true);
            }
        );
    }

    public ObservableList<Venta> getVentas() {
        return ventas;
    }

    public ObservableList<DetalleVenta> getDetalles() {
        return detalles;
    }

    private String formatearMoneda(BigDecimal val) {
        BigDecimal safe = val != null ? val : BigDecimal.ZERO;
        return safe.setScale(DesgloseFiscal.ESCALA_MONETARIA, RoundingMode.HALF_UP).toPlainString();
    }
}
