package com.foodcore.controller.venta;

import com.foodcore.App;
import com.foodcore.controller.CobroModalController;
import com.foodcore.controller.PersonalizacionProductoController;
import com.foodcore.model.Cliente;
import com.foodcore.model.Producto;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.DialogService;
import com.foodcore.util.ToastService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Implementación JavaFX de producción para {@link VentaPosInteractionPort}.
 */
public class JavaFxVentaPosInteractionPort implements VentaPosInteractionPort {

    @Override
    public void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        ToastService.mostrarPorAlertType(tipo, titulo + ": " + mensaje);
    }

    @Override
    public boolean confirmarLimpiezaCarrito() {
        Alert confirm = DialogService.crearConfirmacionDestructiva(
                "Confirmar limpieza (Esc)",
                "¿Desea limpiar los ítems de la venta actual?");
        Optional<ButtonType> res = confirm.showAndWait();
        return res.isPresent() && res.get() == DialogService.BTN_SI_ANULAR;
    }

    @Override
    public boolean confirmarAplicarDescuentoCumpleanos(String mensajeAlerta) {
        Alert alertCumple = DialogService.crearAlerta(
                Alert.AlertType.CONFIRMATION,
                "Cupón de Cumpleaños",
                mensajeAlerta,
                ButtonType.YES, ButtonType.NO);
        alertCumple.setHeaderText("Cliente de Cumpleaños");
        Optional<ButtonType> opt = alertCumple.showAndWait();
        return opt.isPresent() && opt.get() == ButtonType.YES;
    }

    @Override
    public boolean confirmarRegistroRapidoClienteNoEncontrado(String criterio) {
        Alert noEncontrado = DialogService.crearAlerta(
                Alert.AlertType.CONFIRMATION,
                "Cliente no encontrado",
                "No existe cliente con DNI o teléfono \"" + criterio + "\"",
                "¿Desea registrar rápidamente un cliente minorista (Nombre y DNI)?");
        Optional<ButtonType> r = noEncontrado.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    @Override
    public Optional<String> seleccionarModalidadSplit(List<String> modalidades, String predeterminada) {
        return DialogService.crearChoiceDialog(
                predeterminada,
                modalidades,
                "División de Cuenta (Split)",
                "Seleccione la modalidad de división de cuenta",
                "Modalidad:").showAndWait();
    }

    @Override
    public Optional<String> seleccionarDescuento() {
        return DialogService.crearChoiceDialog(
                "10%",
                List.of("10%", "20%", "Monto fijo S/ 5.00"),
                "Aplicar Descuento Autorizado",
                "Requiere permiso VENTA_DESCUENTO (Solo Administrador/Supervisor)",
                "Seleccione el descuento:").showAndWait();
    }

    @Override
    public Optional<String> solicitarIdVentaAAnular() {
        return DialogService.crearTextInputDialog(
                "",
                "Anular Venta Registrada",
                "Requiere permiso VENTA_ANULAR",
                "Ingrese el ID de la venta a anular:").showAndWait();
    }

    @Override
    public boolean confirmarAnulacionVenta(int idVenta) {
        Alert confirmAnular = DialogService.crearConfirmacionDestructiva(
                "Confirmar Anulación de Venta #" + idVenta,
                "Esta operación anulará la venta #" + idVenta + " y revertirá el inventario. ¿Desea continuar?");
        Optional<ButtonType> confirmRes = confirmAnular.showAndWait();
        return confirmRes.isPresent() && confirmRes.get() == DialogService.BTN_SI_ANULAR;
    }

    @Override
    public ModalModoSupervisorHandle construirModalModoSupervisor(int idVentaPrellenado) {
        return VentaPosModalHelper.construirModalModoSupervisor(idVentaPrellenado);
    }

    @Override
    public Optional<String> solicitarNotaItem(String nombreProducto, String notaActual) {
        return DialogService.crearTextInputDialog(
                notaActual != null ? notaActual : "",
                "Indicación de Cocina",
                "Nota para: " + nombreProducto,
                "Indicación especial (ej. sin cebolla, término medio):").showAndWait();
    }

    @Override
    public Optional<String> seleccionarBloqueCanjePuntos(String tituloCliente, int puntosDisponibles,
            List<String> opciones) {
        return DialogService.crearChoiceDialog(
                opciones.get(0),
                opciones,
                "Canjear Puntos de Fidelización",
                tituloCliente + " · Saldo: " + puntosDisponibles + " pts",
                "Seleccione el canje (no altera IGV):").showAndWait();
    }

    @Override
    public Optional<String> solicitarCriterioFidelizacion(String valorInicial) {
        return DialogService.crearTextInputDialog(
                valorInicial,
                "Programa de Fidelización",
                "Búsqueda de Cliente por DNI / Teléfono / RUC",
                "Ingrese DNI, Teléfono o RUC:").showAndWait();
    }

    @Override
    public Optional<Cliente> mostrarRegistroRapidoCliente(String prefill,
            VentaPosModalHelper.RegistroClienteRapidoFn registrador) {
        return VentaPosModalHelper.mostrarDialogoRegistroRapidoCliente(prefill, registrador);
    }

    @Override
    public Optional<PersonalizacionProductoController.ResultadoPersonalizacion> mostrarPersonalizacionProducto(
            Window owner, Producto producto) {
        return PersonalizacionProductoController.mostrarModal(
                owner, producto, producto.getVariantes(), producto.getModificadores());
    }

    @Override
    public void abrirModalCobro(SolicitudCobroModal solicitud) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/CobroModalView.fxml"));
        Parent root = loader.load();

        CobroModalController ctrl = loader.getController();
        ctrl.setIdPedidoAsociado(solicitud.idPedidoAsociado());
        ctrl.initData(
                solicitud.totalNeto(),
                solicitud.descuentoActivo(),
                solicitud.descuentoPuntosActivo(),
                solicitud.puntosARedimir(),
                solicitud.clienteSeleccionado(),
                solicitud.metodoPago(),
                new ArrayList<>(solicitud.items()),
                solicitud.onVentaCompletada()
        );

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
    }

    @Override
    public void abrirModalArqueo() throws IOException {
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
    }
}
