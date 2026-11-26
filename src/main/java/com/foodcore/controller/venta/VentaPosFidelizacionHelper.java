package com.foodcore.controller.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Cliente;
import com.foodcore.model.DetalleVenta;
import com.foodcore.service.FidelizacionService;
import com.foodcore.util.ConfiguracionFidelizacion;
import com.foodcore.util.UiAsyncExecutor;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper encargado de la búsqueda asíncrona, asociación, cumpleaños y canje de puntos en POS.
 */
public class VentaPosFidelizacionHelper {

    private static final Logger LOGGER = Logger.getLogger(VentaPosFidelizacionHelper.class.getName());

    public record PanelClienteControls(
            TextField txtBuscarCliente,
            Button btnBuscarCliente,
            HBox rowClienteAsociado,
            Button btnLimpiarCliente,
            Label lblClienteNombre,
            Label lblClientePuntos,
            Button btnCanjearPuntos) {
    }

    public record BannerFidelizacionControls(
            HBox rowAlertaCumple,
            Label lblAlertaCumple,
            Button btnAplicarCumple,
            HBox rowDescuentoPuntos) {
    }

    public interface HostCallbacks {
        BigDecimal getBasePreCanjeActual();

        void aplicarDescuentoManual(BigDecimal descuento);

        void recalcularTotales();

        void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo);
    }

    private final VentaFidelizacionCoordinator fidelizacionCoordinator;
    private final VentaCarritoHandler carritoHandler;
    private final ObservableList<DetalleVenta> itemsVenta;
    private final PanelClienteControls panelControls;
    private final BannerFidelizacionControls bannerControls;
    private final HostCallbacks callbacks;
    private final AtomicBoolean buscandoCliente = new AtomicBoolean(false);

    private VentaPosInteractionPort interactionPort;
    private Cliente clienteSeleccionadoPos;
    private BigDecimal descuentoPuntosActivo = BigDecimal.ZERO;
    private int puntosARedimirPos;
    private String ultimoMensajeAlertaCumpleanos;

    public VentaPosFidelizacionHelper(
            VentaFidelizacionCoordinator fidelizacionCoordinator,
            VentaCarritoHandler carritoHandler,
            ObservableList<DetalleVenta> itemsVenta,
            PanelClienteControls panelControls,
            BannerFidelizacionControls bannerControls,
            HostCallbacks callbacks,
            VentaPosInteractionPort interactionPort) {
        this.fidelizacionCoordinator = Objects.requireNonNull(fidelizacionCoordinator);
        this.carritoHandler = Objects.requireNonNull(carritoHandler);
        this.itemsVenta = Objects.requireNonNull(itemsVenta);
        this.panelControls = Objects.requireNonNull(panelControls);
        this.bannerControls = Objects.requireNonNull(bannerControls);
        this.callbacks = Objects.requireNonNull(callbacks);
        this.interactionPort = Objects.requireNonNull(interactionPort);
    }

    public void setInteractionPort(VentaPosInteractionPort interactionPort) {
        if (interactionPort != null) {
            this.interactionPort = interactionPort;
        }
    }

    public void handleBuscarClientePos() {
        TextField txtBuscar = panelControls.txtBuscarCliente();
        String criterio = txtBuscar != null && txtBuscar.getText() != null
                ? txtBuscar.getText().trim() : "";
        if (criterio.isEmpty()) {
            if (txtBuscar != null) {
                txtBuscar.requestFocus();
            }
            return;
        }
        ejecutarBusquedaClienteAsync(criterio, null, cliOpt -> {
            if (cliOpt.isEmpty() && interactionPort.confirmarRegistroRapidoClienteNoEncontrado(criterio)) {
                abrirDialogoRegistroRapidoCliente(criterio);
            }
        }, null);
    }

    public void handleRegistroRapidoClientePos() {
        TextField txtBuscar = panelControls.txtBuscarCliente();
        String prefill = txtBuscar != null && txtBuscar.getText() != null
                ? txtBuscar.getText().trim() : "";
        abrirDialogoRegistroRapidoCliente(prefill);
    }

    public void handleLimpiarClientePos() {
        limpiarEstadoClienteFidelizacionPos();
        callbacks.recalcularTotales();
    }

    public void handleFidelizacionCanje() {
        TextField txtBuscar = panelControls.txtBuscarCliente();
        String inicial = clienteSeleccionadoPos != null
                ? clienteSeleccionadoPos.getNumeroDocumento()
                : (txtBuscar != null && txtBuscar.getText() != null && !txtBuscar.getText().isBlank()
                        ? txtBuscar.getText().trim() : "71234567");
        interactionPort.solicitarCriterioFidelizacion(inicial).ifPresent(raw -> {
            if (!raw.isBlank()) {
                String doc = raw.trim();
                ejecutarBusquedaClienteAsync(doc, null, cliOpt -> {
                    if (cliOpt.isEmpty()) {
                        abrirDialogoRegistroRapidoCliente(doc);
                    }
                }, null);
            }
        });
    }

    public void buscarYAsociarClientePorCriterioAsync(
            String criterio,
            Boolean confirmarCumpleanos,
            Consumer<Optional<Cliente>> onCompletado) {
        ejecutarBusquedaClienteAsync(criterio, confirmarCumpleanos, onCompletado, () -> {
            if (onCompletado != null) {
                onCompletado.accept(Optional.empty());
            }
        });
    }

    public Cliente registrarYAsociarClienteRapidoPos(
            String dni,
            String nombreCompleto,
            String telefono,
            LocalDate fechaNacimiento) throws NegocioException {
        Cliente cli = fidelizacionCoordinator.registrarClienteRapido(dni, nombreCompleto, telefono, fechaNacimiento);
        asociarClientePos(cli, Boolean.FALSE);
        return cli;
    }

    public void asociarClientePos(Cliente cli, Boolean confirmarCumpleanosOverride) {
        this.clienteSeleccionadoPos = cli;
        if (cli == null) {
            limpiarEstadoClienteFidelizacionPos();
            return;
        }
        this.descuentoPuntosActivo = BigDecimal.ZERO;
        this.puntosARedimirPos = 0;

        VentaPosTicketUiHelper.actualizarPanelClienteUi(
                cli,
                panelControls.txtBuscarCliente(),
                panelControls.rowClienteAsociado(),
                panelControls.btnLimpiarCliente(),
                panelControls.lblClienteNombre(),
                panelControls.lblClientePuntos(),
                panelControls.btnCanjearPuntos());

        VentaFidelizacionCoordinator.EstadoCumpleanosPos estadoCumple = fidelizacionCoordinator.evaluarCumpleanos(cli);
        VentaPosTicketUiHelper.actualizarBannerCumpleanosUi(
                estadoCumple,
                bannerControls.rowAlertaCumple(),
                bannerControls.lblAlertaCumple(),
                bannerControls.btnAplicarCumple());
        if (estadoCumple.esCumpleanos()) {
            String msg = estadoCumple.mensajeAlerta();
            this.ultimoMensajeAlertaCumpleanos = msg;
            if (!itemsVenta.isEmpty()) {
                boolean aplicar = confirmarCumpleanosOverride != null
                        ? Boolean.TRUE.equals(confirmarCumpleanosOverride)
                        : interactionPort.confirmarAplicarDescuentoCumpleanos(msg);
                if (aplicar) {
                    aplicarDescuentoCumpleanosPos();
                }
            }
        } else {
            this.ultimoMensajeAlertaCumpleanos = null;
        }
        callbacks.recalcularTotales();
    }

    public BigDecimal aplicarDescuentoCumpleanosPos() {
        if (itemsVenta.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalBruto = carritoHandler.calcularSumaBruta(itemsVenta);
        BigDecimal desc = fidelizacionCoordinator.calcularDescuentoCumpleanos(totalBruto);
        callbacks.aplicarDescuentoManual(desc);
        if (clienteSeleccionadoPos != null) {
            VentaPosTicketUiHelper.marcarCumpleanosAplicadoUi(
                    bannerControls.lblAlertaCumple(),
                    bannerControls.btnAplicarCumple());
        }
        callbacks.recalcularTotales();
        return desc;
    }

    public void handleCanjearPuntosPos() {
        if (clienteSeleccionadoPos == null) {
            callbacks.mostrarAlerta("Cliente requerido", "Busque o registre un cliente para canjear puntos.",
                    Alert.AlertType.WARNING);
            return;
        }
        if (itemsVenta.isEmpty()) {
            callbacks.mostrarAlerta("Carrito vacío", "Agregue productos antes de canjear puntos.",
                    Alert.AlertType.WARNING);
            return;
        }
        BigDecimal basePreCanje = callbacks.getBasePreCanjeActual();
        FidelizacionService.BeneficioCanje check = fidelizacionCoordinator.calcularCanjePorBloques(
                clienteSeleccionadoPos.getPuntosAcumulados(), 1, basePreCanje);
        if (check.bloquesDisponibles() <= 0) {
            callbacks.mostrarAlerta("Canje no disponible",
                    "Se requieren al menos " + check.puntosPorBloque() + " puntos y saldo suficiente.",
                    Alert.AlertType.WARNING);
            return;
        }
        if (check.bloquesDisponibles() == 1) {
            aplicarCanjePuntosPos(1);
            return;
        }
        List<String> opciones = fidelizacionCoordinator.construirOpcionesCanje(check);
        interactionPort.seleccionarBloqueCanjePuntos(
                clienteSeleccionadoPos.getRazonSocial(), clienteSeleccionadoPos.getPuntosAcumulados(), opciones)
                .ifPresent(sel -> aplicarCanjePuntosPos(Math.max(0, opciones.indexOf(sel)) + 1));
    }

    public FidelizacionService.BeneficioCanje aplicarCanjePuntosPos(int bloques) {
        if (clienteSeleccionadoPos == null || itemsVenta.isEmpty()) {
            return new FidelizacionService.BeneficioCanje(0, ConfiguracionFidelizacion.getPuntosPorCanje(),
                    ConfiguracionFidelizacion.getDescuentoPorCanjeSoles(), 0, BigDecimal.ZERO);
        }
        BigDecimal basePreCanje = callbacks.getBasePreCanjeActual();
        FidelizacionService.BeneficioCanje beneficio = fidelizacionCoordinator.calcularCanjePorBloques(
                clienteSeleccionadoPos.getPuntosAcumulados(), bloques, basePreCanje);
        this.puntosARedimirPos = beneficio.puntosUtilizados();
        this.descuentoPuntosActivo = beneficio.descuentoAplicadoSoles();
        Label lblPuntos = panelControls.lblClientePuntos();
        if (lblPuntos != null && beneficio.puntosUtilizados() > 0) {
            int restantes = Math.max(0, clienteSeleccionadoPos.getPuntosAcumulados() - beneficio.puntosUtilizados());
            lblPuntos.setText(restantes + " pts (-" + beneficio.puntosUtilizados() + ")");
        }
        callbacks.recalcularTotales();
        return beneficio;
    }

    public void resetDescuentoPuntosLocal() {
        this.descuentoPuntosActivo = BigDecimal.ZERO;
        this.puntosARedimirPos = 0;
    }

    public void sincronizarTrasRecalculo(BigDecimal descuentoPuntosAjustado, BigDecimal totalBruto) {
        this.descuentoPuntosActivo = descuentoPuntosAjustado;
        if (totalBruto.compareTo(BigDecimal.ZERO) <= 0) {
            this.puntosARedimirPos = 0;
        }
    }

    public void limpiarEstadoClienteFidelizacionPos() {
        this.clienteSeleccionadoPos = null;
        this.descuentoPuntosActivo = BigDecimal.ZERO;
        this.puntosARedimirPos = 0;
        this.ultimoMensajeAlertaCumpleanos = null;
        VentaPosTicketUiHelper.limpiarPanelClienteUi(
                panelControls.txtBuscarCliente(),
                panelControls.rowClienteAsociado(),
                panelControls.btnLimpiarCliente(),
                panelControls.btnCanjearPuntos(),
                bannerControls.rowAlertaCumple(),
                bannerControls.btnAplicarCumple(),
                bannerControls.rowDescuentoPuntos());
    }

    public boolean isBusquedaClienteEnProgreso() {
        return buscandoCliente.get();
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

    private void abrirDialogoRegistroRapidoCliente(String prefill) {
        Optional<Cliente> creado = interactionPort.mostrarRegistroRapidoCliente(
                prefill, fidelizacionCoordinator::buscarORegistrarClienteRapidoDirecto);
        if (creado.isPresent() && creado.get() != null) {
            asociarClientePos(creado.get(), null);
        } else if (creado.isPresent()) {
            callbacks.mostrarAlerta("Datos Inválidos", "Ingrese un DNI válido de 8 dígitos y el nombre completo.",
                    Alert.AlertType.WARNING);
        }
    }

    private void ejecutarBusquedaClienteAsync(
            String criterio,
            Boolean confirmarCumpleanos,
            Consumer<Optional<Cliente>> onResultado,
            Runnable onError) {
        if (criterio == null || criterio.isBlank()) {
            if (onResultado != null) {
                onResultado.accept(Optional.empty());
            }
            return;
        }
        if (!buscandoCliente.compareAndSet(false, true)) {
            return;
        }
        final String criterioLimpio = criterio.trim();
        VentaPosTicketUiHelper.actualizarEstadoBusquedaClienteUi(panelControls.btnBuscarCliente(), true);
        UiAsyncExecutor.ejecutarAsync(
            () -> fidelizacionCoordinator.buscarClientePorCriterio(criterioLimpio),
            cliOpt -> {
                buscandoCliente.set(false);
                VentaPosTicketUiHelper.actualizarEstadoBusquedaClienteUi(panelControls.btnBuscarCliente(), false);
                cliOpt.ifPresent(cli -> asociarClientePos(cli, confirmarCumpleanos));
                if (onResultado != null) {
                    onResultado.accept(cliOpt);
                }
            },
            ex -> {
                buscandoCliente.set(false);
                VentaPosTicketUiHelper.actualizarEstadoBusquedaClienteUi(panelControls.btnBuscarCliente(), false);
                LOGGER.log(Level.WARNING, "Error buscando cliente en POS", ex);
                callbacks.mostrarAlerta("Error al Buscar Cliente",
                        "No se pudo consultar el cliente: "
                                + (ex.getMessage() != null ? ex.getMessage() : "Error de conexión"),
                        Alert.AlertType.ERROR);
                if (onError != null) {
                    onError.run();
                }
            }
        );
    }
}
