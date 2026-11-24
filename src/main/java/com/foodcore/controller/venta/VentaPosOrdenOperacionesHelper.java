package com.foodcore.controller.venta;

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
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.VentaService;
import com.foodcore.util.FormatoPersonalizacionUtil;
import com.foodcore.util.UiAsyncExecutor;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.TilePane;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper de operaciones transaccionales del POS: ítems, comanda, split, descuentos, anulaciones y cobros.
 */
public class VentaPosOrdenOperacionesHelper {

    private static final Logger LOGGER = Logger.getLogger(VentaPosOrdenOperacionesHelper.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record ControlesOrden(
            TableView<DetalleVenta> tblDetalle,
            TilePane tileProductos,
            Label lblTotal,
            Button btnAnularVenta,
            Button btnPreCuenta) {
    }

    public record BotonesPago(
            Button btnEfectivo,
            Button btnTarjeta,
            Button btnYape,
            Button btnEnviarCocina,
            Button btnDividirCuenta) {
    }

    private final VentaCobroCoordinator cobroCoordinator;
    private final VentaCarritoHandler carritoHandler;
    private final VentaMesasCanalCoordinator mesasCanalCoordinator;
    private final PersonalizacionProductoService personalizacionService;
    private final VentaPosOrdenHostPort host;
    private final ControlesOrden controles;
    private final BotonesPago botonesPago;
    private final AtomicBoolean cobroEnProgreso = new AtomicBoolean(false);
    private final AtomicBoolean agregandoItemPedido = new AtomicBoolean(false);

    private PedidoService pedidoService;
    private VentaPosInteractionPort interactionPort;

    public VentaPosOrdenOperacionesHelper(
            VentaCobroCoordinator cobroCoordinator,
            VentaCarritoHandler carritoHandler,
            VentaMesasCanalCoordinator mesasCanalCoordinator,
            PersonalizacionProductoService personalizacionService,
            VentaPosOrdenHostPort host,
            ControlesOrden controles,
            BotonesPago botonesPago,
            PedidoService pedidoService,
            VentaPosInteractionPort interactionPort) {
        this.cobroCoordinator = Objects.requireNonNull(cobroCoordinator);
        this.carritoHandler = Objects.requireNonNull(carritoHandler);
        this.mesasCanalCoordinator = Objects.requireNonNull(mesasCanalCoordinator);
        this.personalizacionService = Objects.requireNonNull(personalizacionService);
        this.host = Objects.requireNonNull(host);
        this.controles = Objects.requireNonNull(controles);
        this.botonesPago = Objects.requireNonNull(botonesPago);
        this.pedidoService = Objects.requireNonNull(pedidoService);
        this.interactionPort = Objects.requireNonNull(interactionPort);
    }

    public void setPedidoService(PedidoService pedidoService) {
        if (pedidoService != null) {
            this.pedidoService = pedidoService;
        }
    }

    public void setInteractionPort(VentaPosInteractionPort interactionPort) {
        if (interactionPort != null) {
            this.interactionPort = interactionPort;
        }
    }

    public boolean isOperacionPedidoEnProgreso() {
        return agregandoItemPedido.get();
    }

    public void agregarProductoAVenta(Producto p) {
        if (p == null || !p.isDisponible()) {
            return;
        }
        Producto productoResuelto = carritoHandler.resolverOpcionesProducto(personalizacionService, p);
        if (productoResuelto.tieneOpcionesPersonalizacion()) {
            TableView<DetalleVenta> tblDetalle = controles.tblDetalle();
            Window owner = tblDetalle != null && tblDetalle.getScene() != null
                    ? tblDetalle.getScene().getWindow() : null;
            interactionPort.mostrarPersonalizacionProducto(owner, productoResuelto)
                    .ifPresent(res -> agregarProductoPersonalizadoAVenta(
                            res.producto(), res.variante(), res.modificadores(),
                            res.cantidad(), res.notaEspecial()));
            return;
        }
        agregarProductoPersonalizadoAVenta(productoResuelto, null, List.of(), 1, "");
    }

    public void agregarProductoPersonalizadoAVenta(
            Producto p,
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

        Integer pedidoActivoId = host.getPedidoActivoId();
        if (pedidoActivoId != null && pedidoActivoId > 0) {
            agregarProductoAPedidoExistenteAsync(
                    p, pedidoActivoId, idVariante, nombreVariante, mods, modsTexto, qty, nota,
                    precioUnitario, subtotal);
            return;
        }
        agregarProductoEnCarritoLocal(
                p, idVariante, nombreVariante, mods, modsTexto, qty, nota, precioUnitario, subtotal);
    }

    public void handleEliminarItem() {
        TableView<DetalleVenta> tblDetalle = controles.tblDetalle();
        if (tblDetalle == null) {
            return;
        }
        ObservableList<DetalleVenta> itemsVenta = host.getItemsVenta();
        DetalleVenta sel = tblDetalle.getSelectionModel().getSelectedItem();
        if (carritoHandler.decrementarOEliminar(itemsVenta, sel)) {
            tblDetalle.refresh();
            host.recalcularTotales();
        }
    }

    public void handleNotaItem() {
        TableView<DetalleVenta> tblDetalle = controles.tblDetalle();
        if (tblDetalle == null) {
            return;
        }
        DetalleVenta sel = tblDetalle.getSelectionModel().getSelectedItem();
        if (sel == null) {
            host.mostrarAlerta("Atención", "Seleccione un ítem de la tabla para añadir nota.",
                    Alert.AlertType.WARNING);
            return;
        }
        interactionPort.solicitarNotaItem(sel.getNombreProducto(), sel.getNotasPreparacion()).ifPresent(nota -> {
            sel.setNotasPreparacion(nota);
            tblDetalle.refresh();
        });
    }

    public void handleEnviarComanda() {
        ObservableList<DetalleVenta> itemsVenta = host.getItemsVenta();
        if (itemsVenta.isEmpty()) {
            host.mostrarAlerta("Atención", "Agregue al menos un ítem para enviar a cocina.",
                    Alert.AlertType.WARNING);
            return;
        }
        Usuario usr = AuthService.getUsuarioSesionActual();
        if (usr == null || usr.getIdUsuario() <= 0) {
            host.mostrarAlerta("Sesión Requerida", "Debe iniciar sesión para enviar comandas a cocina.",
                    Alert.AlertType.ERROR);
            return;
        }
        Integer pedidoActivoId = host.getPedidoActivoId();
        int idObjetivo = pedidoActivoId != null ? pedidoActivoId : 0;
        List<DetallePedido> detallesCocina = new ArrayList<>();
        for (DetalleVenta dv : itemsVenta) {
            detallesCocina.add(mesasCanalCoordinator.convertirDetalleVentaAPedido(dv, idObjetivo));
        }

        Pedido pedido = new Pedido();
        pedido.setIdPedido(idObjetivo);
        pedido.setIdUsuario(usr.getIdUsuario());
        pedido.setNumeroMesa(host.getMesaSeleccionadaActual() != null ? host.getMesaSeleccionadaActual() : "01");
        pedido.setTipoPedido(host.getCanalSeleccionadoActual() != null ? host.getCanalSeleccionadoActual() : "MESA");
        pedido.setEstado("REGISTRADO");
        pedido.setTotal(host.getTotalNetoActual());

        setBotonDisable(botonesPago.btnEnviarCocina(), true);
        UiAsyncExecutor.ejecutarAsync(
            () -> cobroCoordinator.enviarComandaACocina(pedidoService, pedido, detallesCocina),
            resultado -> {
                setBotonDisable(botonesPago.btnEnviarCocina(), false);
                Pedido registrado = resultado.pedido();
                if (registrado != null && registrado.getIdPedido() > 0) {
                    host.setPedidoActivoId(registrado.getIdPedido());
                    if (registrado.getDetalles() != null && !registrado.getDetalles().isEmpty()) {
                        host.aplicarDetallesPedidoEnPOS(registrado.getDetalles());
                    }
                    host.refrescarMesasYCanalesAsync();
                }
                if (resultado.impresionOk()) {
                    String sufijo = registrado != null && registrado.getIdPedido() > 0
                            ? "Orden #" + registrado.getIdPedido() : "Orden";
                    host.mostrarAlerta("Comanda Enviada",
                            sufijo + " enviada a Línea de Cocina (KDS) correctamente.",
                            Alert.AlertType.INFORMATION);
                } else {
                    host.mostrarAlerta("Comanda en KDS (Sin Impresión)",
                            "La orden se envió a KDS pero falló la impresora térmica.",
                            Alert.AlertType.WARNING);
                }
            },
            ex -> {
                setBotonDisable(botonesPago.btnEnviarCocina(), false);
                LOGGER.log(Level.WARNING, "Error registrando/enviando comanda a cocina", ex);
                host.mostrarAlerta("Error al Enviar Comanda",
                        "No se pudo registrar ni enviar la comanda a cocina: "
                                + (ex.getMessage() != null ? ex.getMessage() : "Error de conexión"),
                        Alert.AlertType.ERROR);
            }
        );
    }

    public void handleDividirCuenta() {
        ObservableList<DetalleVenta> itemsVenta = host.getItemsVenta();
        if (itemsVenta.isEmpty()) {
            host.mostrarAlerta("Atención", "Agregue ítems a la orden antes de dividir la cuenta.",
                    Alert.AlertType.WARNING);
            return;
        }
        Usuario usr = AuthService.getUsuarioSesionActual();
        if (usr == null || usr.getIdUsuario() <= 0) {
            host.mostrarAlerta("Sesión Requerida", "Debe iniciar sesión para dividir la cuenta.",
                    Alert.AlertType.ERROR);
            return;
        }
        Optional<String> res = interactionPort.seleccionarModalidadSplit(
                List.of(VentaCobroCoordinator.MODALIDAD_SPLIT_2,
                        VentaCobroCoordinator.MODALIDAD_SPLIT_4,
                        VentaCobroCoordinator.MODALIDAD_SPLIT_ITEMS),
                VentaCobroCoordinator.MODALIDAD_SPLIT_2);
        if (res.isEmpty() || !cobroEnProgreso.compareAndSet(false, true)) {
            return;
        }
        setBotonesCobroDeshabilitados(true);

        BigDecimal totalNeto = host.getTotalNetoActual();
        BigDecimal basePreCanje = host.getBasePreCanjeActual();
        List<DetalleVenta> itemsSnapshot = new ArrayList<>(itemsVenta);
        List<PagoSplit> pagos = cobroCoordinator.construirPagosPorModalidad(
                res.get(), totalNeto, itemsSnapshot);
        int idUsr = usr.getIdUsuario();
        Cliente cli = host.getClienteSeleccionadoPos();
        Integer idCliVenta = cli != null && cli.getIdCliente() > 0 ? cli.getIdCliente() : null;
        Integer idPedidoSnap = host.getPedidoActivoId();
        BigDecimal descSnap = host.getDescuentoActivo();
        BigDecimal descPtsSnap = host.getDescuentoPuntosActivo();
        int ptsSnap = host.getPuntosARedimirPos();

        UiAsyncExecutor.ejecutarAsync(
            () -> cobroCoordinator.registrarVentaDividida(idPedidoSnap, idUsr, idCliVenta, basePreCanje,
                    totalNeto, descSnap, descPtsSnap, ptsSnap, itemsSnapshot, pagos),
            procesada -> {
                cobroEnProgreso.set(false);
                setBotonesCobroDeshabilitados(false);
                host.finalizarOrdenTrasCobro();
                host.mostrarAlerta("Cuenta Dividida",
                        "Cobro dividido en " + pagos.size() + " pagos registrado exitosamente en Caja.",
                        Alert.AlertType.INFORMATION);
            },
            ex -> {
                cobroEnProgreso.set(false);
                setBotonesCobroDeshabilitados(false);
                host.mostrarAlerta("Error en Split", ex.getMessage(), Alert.AlertType.ERROR);
            }
        );
    }

    public Venta ejecutarCobroSplitEnPagos(List<PagoSplit> pagos) throws Exception {
        Usuario usr = AuthService.getUsuarioSesionActual();
        if (usr == null || usr.getIdUsuario() <= 0) {
            throw new NegocioException("No hay una sesión de usuario activa para procesar la operación.");
        }
        Cliente cli = host.getClienteSeleccionadoPos();
        Integer idCliVenta = cli != null && cli.getIdCliente() > 0 ? cli.getIdCliente() : null;
        Venta procesada = cobroCoordinator.registrarVentaDividida(
                host.getPedidoActivoId(), usr.getIdUsuario(), idCliVenta,
                host.getBasePreCanjeActual(), host.getTotalNetoActual(),
                host.getDescuentoActivo(), host.getDescuentoPuntosActivo(), host.getPuntosARedimirPos(),
                new ArrayList<>(host.getItemsVenta()), pagos);
        host.finalizarOrdenTrasCobro();
        return procesada;
    }

    public void handleAplicarDescuento() {
        SesionUsuario sesion = cobroCoordinator.crearSesionActualPOS(AuthService.getUsuarioSesionActual());
        if (!cobroCoordinator.tienePermiso(sesion, Permiso.VENTA_DESCUENTO)) {
            host.mostrarAlerta("Acceso Denegado", "Solo un Administrador o Supervisor puede autorizar descuentos.",
                    Alert.AlertType.ERROR);
            return;
        }
        if (host.getItemsVenta().isEmpty()) {
            host.mostrarAlerta("Atención", "Agregue productos antes de aplicar un descuento.",
                    Alert.AlertType.WARNING);
            return;
        }
        interactionPort.seleccionarDescuento().ifPresent(sel -> {
            try {
                if ("10%".equals(sel)) {
                    aplicarDescuentoConPermiso(new BigDecimal("10"), true);
                } else if ("20%".equals(sel)) {
                    aplicarDescuentoConPermiso(new BigDecimal("20"), true);
                } else {
                    aplicarDescuentoConPermiso(new BigDecimal("5.00"), false);
                }
            } catch (Exception e) {
                host.mostrarAlerta("Acceso Denegado / Error", e.getMessage(), Alert.AlertType.ERROR);
            }
        });
    }

    public BigDecimal aplicarDescuentoConPermiso(BigDecimal valor, boolean esPorcentaje) throws NegocioException {
        SesionUsuario sesion = cobroCoordinator.crearSesionActualPOS(AuthService.getUsuarioSesionActual());
        BigDecimal descuento = carritoHandler.calcularDescuentoAutorizado(
                host.getItemsVenta(), valor, esPorcentaje, sesion);
        host.aplicarDescuentoManual(descuento);
        host.recalcularTotales();
        return descuento;
    }

    public void handleAnularVenta() {
        SesionUsuario sesion = cobroCoordinator.crearSesionActualPOS(AuthService.getUsuarioSesionActual());
        if (!cobroCoordinator.tienePermiso(sesion, Permiso.VENTA_ANULAR)) {
            ejecutarAnulacionSupervisor(sesion);
            return;
        }
        Optional<String> opt = interactionPort.solicitarIdVentaAAnular();
        if (opt.isEmpty() || opt.get().isBlank()) {
            return;
        }
        final int idVenta;
        try {
            idVenta = Integer.parseInt(opt.get().trim());
        } catch (NumberFormatException nfe) {
            host.mostrarAlerta("Dato inválido", "El ID de la venta debe ser numérico.", Alert.AlertType.WARNING);
            return;
        }
        if (!interactionPort.confirmarAnulacionVenta(idVenta)) {
            return;
        }
        setBotonDisable(controles.btnAnularVenta(), true);
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                cobroCoordinator.anularVentaDirecta(idVenta, sesion, "Anulación solicitada desde POS");
                return Boolean.TRUE;
            },
            ok -> {
                setBotonDisable(controles.btnAnularVenta(), false);
                host.mostrarAlerta("Venta Anulada",
                        "La venta #" + idVenta + " fue anulada y su stock revertido.",
                        Alert.AlertType.INFORMATION);
            },
            e -> {
                setBotonDisable(controles.btnAnularVenta(), false);
                host.mostrarAlerta("Error al Anular", e.getMessage(), Alert.AlertType.ERROR);
            }
        );
    }

    public void abrirModalCobro(String metodo) {
        if (!cobroEnProgreso.compareAndSet(false, true)) {
            return;
        }
        setBotonesCobroDeshabilitados(true);

        ObservableList<DetalleVenta> itemsVenta = host.getItemsVenta();
        if (itemsVenta.isEmpty()) {
            cobroEnProgreso.set(false);
            setBotonesCobroDeshabilitados(false);
            host.mostrarAlerta("Atención", "No hay productos en la venta activa.", Alert.AlertType.WARNING);
            return;
        }

        VentaPosInteractionPort.SolicitudCobroModal solicitud = new VentaPosInteractionPort.SolicitudCobroModal(
                host.getPedidoActivoId(),
                host.getBasePreCanjeActual(),
                host.getTotalNetoActual(),
                host.getDescuentoActivo(),
                host.getDescuentoPuntosActivo(),
                host.getPuntosARedimirPos(),
                host.getClienteSeleccionadoPos(),
                metodo,
                new ArrayList<>(itemsVenta),
                host::finalizarOrdenTrasCobro);

        try {
            interactionPort.abrirModalCobro(solicitud);
        } catch (Exception e) {
            host.mostrarAlerta("Error", "No se pudo abrir o procesar el cobro: " + e.getMessage(),
                    Alert.AlertType.ERROR);
        } finally {
            cobroEnProgreso.set(false);
            setBotonesCobroDeshabilitados(false);
        }
    }

    public void handleAbrirArqueo() {
        try {
            interactionPort.abrirModalArqueo();
        } catch (Exception e) {
            host.mostrarAlerta("Error", "No se pudo abrir el arqueo: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void handleImprimirPreCuenta() {
        ObservableList<DetalleVenta> itemsVenta = host.getItemsVenta();
        if (itemsVenta.isEmpty()) {
            host.mostrarAlerta("Atención", "No hay platos en la orden para emitir pre-cuenta.",
                    Alert.AlertType.WARNING);
            return;
        }
        Pedido p = new Pedido();
        Integer pedidoActivoId = host.getPedidoActivoId();
        p.setIdPedido(pedidoActivoId != null ? pedidoActivoId : SECURE_RANDOM.nextInt(9000) + 1000);
        p.setNumeroMesa(host.getMesaSeleccionadaActual());
        p.setTotal(host.getTotalNetoActual());

        List<DetallePedido> detalles = new ArrayList<>();
        for (DetalleVenta dv : itemsVenta) {
            detalles.add(mesasCanalCoordinator.convertirDetalleVentaAPedido(dv, p.getIdPedido()));
        }

        setBotonDisable(controles.btnPreCuenta(), true);
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                cobroCoordinator.imprimirPreCuenta(p, detalles);
                return Boolean.TRUE;
            },
            ok -> {
                setBotonDisable(controles.btnPreCuenta(), false);
                host.mostrarAlerta("Pre-Cuenta Emitida",
                        "Pre-cuenta emitida exitosamente (Ticket no fiscal).", Alert.AlertType.INFORMATION);
            },
            ex -> {
                setBotonDisable(controles.btnPreCuenta(), false);
                LOGGER.log(Level.WARNING, "Error al imprimir pre-cuenta", ex);
                host.mostrarAlerta("Error", "Error al emitir pre-cuenta: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        );
    }

    private void agregarProductoAPedidoExistenteAsync(
            Producto p,
            int idPedido,
            Integer idVariante,
            String nombreVariante,
            List<ProductoModificador> mods,
            String modsTexto,
            int qty,
            String nota,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
        if (!agregandoItemPedido.compareAndSet(false, true)) {
            return;
        }
        DetallePedido nuevoItem = new DetallePedido(
                0, idPedido, p.getIdProducto(), qty, precioUnitario, subtotal, nota);
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
        TilePane tileProductos = controles.tileProductos();
        if (tileProductos != null) {
            tileProductos.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> cobroCoordinator.agregarItemAPedidoExistente(pedidoService, idPedido, nuevoItem),
            resultado -> {
                agregandoItemPedido.set(false);
                if (tileProductos != null) {
                    tileProductos.setDisable(false);
                }
                if (Integer.valueOf(idPedido).equals(host.getPedidoActivoId())) {
                    host.aplicarDetallesPedidoEnPOS(resultado.detallesCompletos());
                    BigDecimal desc = host.getDescuentoActivo();
                    Label lblTotal = controles.lblTotal();
                    if (lblTotal != null && (desc == null || desc.compareTo(BigDecimal.ZERO) <= 0)) {
                        lblTotal.setText("S/ " + resultado.totalPedido().setScale(2, RoundingMode.HALF_UP));
                    }
                }
                host.actualizarCachePedidoActivo(idPedido, resultado.totalPedido(), resultado.detallesCompletos());
            },
            ex -> {
                agregandoItemPedido.set(false);
                if (tileProductos != null) {
                    tileProductos.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error agregando ítem al pedido #" + idPedido, ex);
                String msg = ex.getMessage() != null ? ex.getMessage() : "Error de conexión con la base de datos";
                host.mostrarAlerta("Error al Agregar Ítem",
                        "No se pudo guardar el producto en el pedido: " + msg, Alert.AlertType.ERROR);
            }
        );
    }

    private void agregarProductoEnCarritoLocal(
            Producto p,
            Integer idVariante,
            String nombreVariante,
            List<ProductoModificador> mods,
            String modsTexto,
            int qty,
            String nota,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
        ObservableList<DetalleVenta> itemsVenta = host.getItemsVenta();
        boolean nuevoItemAgregado = carritoHandler.agregarItemLocal(itemsVenta, p, idVariante, nombreVariante,
                mods, modsTexto, qty, nota, precioUnitario, subtotal);
        if (nuevoItemAgregado) {
            itemsVenta.get(itemsVenta.size() - 1).setNombreProducto(p.getNombre());
        }
        TableView<DetalleVenta> tblDetalle = controles.tblDetalle();
        if (tblDetalle != null) {
            tblDetalle.refresh();
        }
        host.recalcularTotales();
    }

    private void ejecutarAnulacionSupervisor(SesionUsuario sesion) {
        ModalModoSupervisorHandle modal = interactionPort.construirModalModoSupervisor(0);
        Optional<ButtonType> res = modal.dialog().showAndWait();
        if (res.isEmpty() || res.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return;
        }
        String idRaw = modal.txtIdVenta().getText() != null ? modal.txtIdVenta().getText().trim() : "";
        String pinSup = modal.txtPinSupervisor().getText() != null ? modal.txtPinSupervisor().getText().trim() : "";
        String motivo = modal.txtMotivo().getText() != null ? modal.txtMotivo().getText().trim() : "";
        boolean yaPreparado = modal.chkPlatoPreparado().isSelected();
        VentaCobroCoordinator.ValidacionAnulacionSupervisor validacion =
                cobroCoordinator.validarAnulacionSupervisor(idRaw, motivo);
        if (!validacion.valido()) {
            host.mostrarAlerta(validacion.tituloError(), validacion.mensajeError(), Alert.AlertType.WARNING);
            return;
        }
        final int idVenta = validacion.idVenta();
        final String motivoFinal = validacion.motivoFinal();
        setBotonDisable(controles.btnAnularVenta(), true);
        UiAsyncExecutor.ejecutarAsync(
            () -> cobroCoordinator.anularVentaConSupervisor(idVenta, yaPreparado, sesion, pinSup, motivoFinal),
            supervisor -> {
                setBotonDisable(controles.btnAnularVenta(), false);
                String nomSup = supervisor.getNombreEmpleado() != null && !supervisor.getNombreEmpleado().isBlank()
                        ? supervisor.getNombreEmpleado() : supervisor.getUsername();
                host.mostrarAlerta("Venta Anulada (Modo Supervisor)",
                        "La venta #" + idVenta + " fue anulada correctamente.\n"
                                + "Autorizado por: " + nomSup + " (" + supervisor.getNombreRol() + ").",
                        Alert.AlertType.INFORMATION);
            },
            e -> {
                setBotonDisable(controles.btnAnularVenta(), false);
                host.mostrarAlerta("Autorización Denegada", e.getMessage(), Alert.AlertType.ERROR);
            }
        );
    }

    private void setBotonesCobroDeshabilitados(boolean deshabilitado) {
        setBotonDisable(botonesPago.btnEfectivo(), deshabilitado);
        setBotonDisable(botonesPago.btnTarjeta(), deshabilitado);
        setBotonDisable(botonesPago.btnYape(), deshabilitado);
        setBotonDisable(botonesPago.btnEnviarCocina(), deshabilitado);
        setBotonDisable(botonesPago.btnDividirCuenta(), deshabilitado);
    }

    private static void setBotonDisable(Button boton, boolean disable) {
        if (boton != null) {
            boton.setDisable(disable);
        }
    }
}
