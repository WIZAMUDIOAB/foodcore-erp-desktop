package com.foodcore.controller.venta;

import com.foodcore.controller.PersonalizacionProductoController;
import com.foodcore.model.Cliente;
import com.foodcore.model.Producto;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javafx.scene.control.Alert;
import javafx.stage.Window;

/**
 * Doble de pruebas determinístico para {@link VentaPosInteractionPort} sin estado en producción.
 */
public class FakeVentaPosInteractionPort implements VentaPosInteractionPort {

    @FunctionalInterface
    public interface CobroHandler {
        void procesar(SolicitudCobroModal solicitud) throws Exception;
    }

    private CobroHandler cobroHandler;
    private boolean confirmarLimpieza;
    private boolean confirmarCumpleanos;
    private boolean confirmarRegistroNoEncontrado;
    private boolean confirmarAnulacion;
    private String modalidadSplitSeleccionada;
    private int bloqueCanjeIndice;
    private volatile String ultimaAlertaTitulo;
    private volatile String ultimaAlertaMensaje;
    private volatile Alert.AlertType ultimaAlertaTipo;
    private volatile CountDownLatch alertaLatch;

    public void setCobroHandler(CobroHandler cobroHandler) {
        this.cobroHandler = cobroHandler;
    }

    public void configurarCobroDirecto(VentaCobroCoordinator cobroCoordinator) {
        this.cobroHandler = solicitud -> {
            cobroCoordinator.procesarCobroDirecto(
                    solicitud.idPedidoAsociado(),
                    solicitud.clienteSeleccionado() != null && solicitud.clienteSeleccionado().getIdCliente() > 0
                            ? solicitud.clienteSeleccionado().getIdCliente() : null,
                    solicitud.basePreCanje(),
                    solicitud.totalNeto(),
                    solicitud.metodoPago(),
                    solicitud.descuentoActivo(),
                    solicitud.descuentoPuntosActivo(),
                    solicitud.puntosARedimir(),
                    solicitud.items());
            if (solicitud.onVentaCompletada() != null) {
                solicitud.onVentaCompletada().run();
            }
        };
    }

    public void setConfirmarLimpieza(boolean confirmarLimpieza) {
        this.confirmarLimpieza = confirmarLimpieza;
    }

    public void setConfirmarCumpleanos(boolean confirmarCumpleanos) {
        this.confirmarCumpleanos = confirmarCumpleanos;
    }

    public void setConfirmarRegistroNoEncontrado(boolean confirmarRegistroNoEncontrado) {
        this.confirmarRegistroNoEncontrado = confirmarRegistroNoEncontrado;
    }

    public void setConfirmarAnulacion(boolean confirmarAnulacion) {
        this.confirmarAnulacion = confirmarAnulacion;
    }

    public void setModalidadSplitSeleccionada(String modalidadSplitSeleccionada) {
        this.modalidadSplitSeleccionada = modalidadSplitSeleccionada;
    }

    public void setBloqueCanjeIndice(int bloqueCanjeIndice) {
        this.bloqueCanjeIndice = bloqueCanjeIndice;
    }

    public void resetUltimaAlerta(CountDownLatch latch) {
        this.ultimaAlertaTitulo = null;
        this.ultimaAlertaMensaje = null;
        this.ultimaAlertaTipo = null;
        this.alertaLatch = latch;
    }

    public String getUltimaAlertaTitulo() {
        return ultimaAlertaTitulo;
    }

    public String getUltimaAlertaMensaje() {
        return ultimaAlertaMensaje;
    }

    public Alert.AlertType getUltimaAlertaTipo() {
        return ultimaAlertaTipo;
    }

    @Override
    public void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        this.ultimaAlertaTitulo = titulo;
        this.ultimaAlertaMensaje = mensaje;
        this.ultimaAlertaTipo = tipo;
        CountDownLatch latch = this.alertaLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public boolean confirmarLimpiezaCarrito() {
        return confirmarLimpieza;
    }

    @Override
    public boolean confirmarAplicarDescuentoCumpleanos(String mensajeAlerta) {
        return confirmarCumpleanos;
    }

    @Override
    public boolean confirmarRegistroRapidoClienteNoEncontrado(String criterio) {
        return confirmarRegistroNoEncontrado;
    }

    @Override
    public Optional<String> seleccionarModalidadSplit(List<String> modalidades, String predeterminada) {
        return Optional.ofNullable(modalidadSplitSeleccionada).or(() -> Optional.ofNullable(predeterminada));
    }

    @Override
    public Optional<String> seleccionarDescuento() {
        return Optional.empty();
    }

    @Override
    public Optional<String> solicitarIdVentaAAnular() {
        return Optional.empty();
    }

    @Override
    public boolean confirmarAnulacionVenta(int idVenta) {
        return confirmarAnulacion;
    }

    @Override
    public ModalModoSupervisorHandle construirModalModoSupervisor(int idVentaPrellenado) {
        return VentaPosModalHelper.construirModalModoSupervisor(idVentaPrellenado);
    }

    @Override
    public Optional<String> solicitarNotaItem(String nombreProducto, String notaActual) {
        return Optional.empty();
    }

    @Override
    public Optional<String> seleccionarBloqueCanjePuntos(String tituloCliente, int puntosDisponibles,
            List<String> opciones) {
        if (opciones == null || opciones.isEmpty()) {
            return Optional.empty();
        }
        int idx = Math.max(0, Math.min(bloqueCanjeIndice, opciones.size() - 1));
        return Optional.of(opciones.get(idx));
    }

    @Override
    public Optional<String> solicitarCriterioFidelizacion(String valorInicial) {
        return Optional.empty();
    }

    @Override
    public Optional<Cliente> mostrarRegistroRapidoCliente(String prefill,
            VentaPosModalHelper.RegistroClienteRapidoFn registrador) {
        return Optional.empty();
    }

    @Override
    public Optional<PersonalizacionProductoController.ResultadoPersonalizacion> mostrarPersonalizacionProducto(
            Window owner, Producto producto) {
        return Optional.empty();
    }

    @Override
    public void abrirModalCobro(SolicitudCobroModal solicitud) throws Exception {
        if (cobroHandler != null) {
            cobroHandler.procesar(solicitud);
        }
    }

    @Override
    public void abrirModalArqueo() {
        // Sin diálogo modal en doble de pruebas.
    }
}
