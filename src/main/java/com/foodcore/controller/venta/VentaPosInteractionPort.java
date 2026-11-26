package com.foodcore.controller.venta;

import com.foodcore.controller.PersonalizacionProductoController;
import com.foodcore.model.Cliente;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Producto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.stage.Window;

/**
 * Puerto de interacción visual del Punto de Venta (POS) que desacopla el controlador
 * de alertas, diálogos JavaFX y modales para permitir pruebas determinísticas sin hooks.
 */
public interface VentaPosInteractionPort {

    record SolicitudCobroModal(
            Integer idPedidoAsociado,
            BigDecimal basePreCanje,
            BigDecimal totalNeto,
            BigDecimal descuentoActivo,
            BigDecimal descuentoPuntosActivo,
            int puntosARedimir,
            Cliente clienteSeleccionado,
            String metodoPago,
            List<DetalleVenta> items,
            Runnable onVentaCompletada) {
    }

    void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo);

    boolean confirmarLimpiezaCarrito();

    boolean confirmarAplicarDescuentoCumpleanos(String mensajeAlerta);

    boolean confirmarRegistroRapidoClienteNoEncontrado(String criterio);

    Optional<String> seleccionarModalidadSplit(List<String> modalidades, String predeterminada);

    Optional<String> seleccionarDescuento();

    Optional<String> solicitarIdVentaAAnular();

    boolean confirmarAnulacionVenta(int idVenta);

    ModalModoSupervisorHandle construirModalModoSupervisor(int idVentaPrellenado);

    Optional<String> solicitarNotaItem(String nombreProducto, String notaActual);

    Optional<String> seleccionarBloqueCanjePuntos(String tituloCliente, int puntosDisponibles, List<String> opciones);

    Optional<String> solicitarCriterioFidelizacion(String valorInicial);

    Optional<Cliente> mostrarRegistroRapidoCliente(String prefill,
            VentaPosModalHelper.RegistroClienteRapidoFn registrador);

    Optional<PersonalizacionProductoController.ResultadoPersonalizacion> mostrarPersonalizacionProducto(
            Window owner, Producto producto);

    void abrirModalCobro(SolicitudCobroModal solicitud) throws Exception;

    void abrirModalArqueo() throws Exception;
}
