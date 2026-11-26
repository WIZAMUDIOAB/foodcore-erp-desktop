package com.foodcore.controller.venta;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.util.UiAsyncExecutor;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper que encapsula el estado y orquestación de mesas, canales multicanal y catálogo de productos en POS.
 */
public class VentaPosMesasCatalogoHelper {

    private static final Logger LOGGER = Logger.getLogger(VentaPosMesasCatalogoHelper.class.getName());
    public static final int TOTAL_MESAS_SALON = 12;

    public record ControlesNavegacionPos(
            TabPane tabPanePosCatalogo,
            Tab tabCatalogo,
            Tab tabMesas,
            Label lblOrdenHeader,
            Label lblCanalBadge,
            Label lblResumenMesasPos) {
    }

    public record ControlesMesasPos(
            TilePane tileMesasPos,
            FlowPane flowPedidosMulticanal,
            HBox boxOrdenesEntrantes) {
    }

    public record ControlesCatalogoPos(
            TilePane tileProductos,
            TextField txtBuscar,
            HBox boxCategorias) {
    }

    public interface HostCallbacks {
        void aplicarDetallesPedidoEnPOS(List<DetallePedido> detalles);

        void limpiarCarritoLocalYDescuentos();

        void recalcularTotales();

        void agregarProductoAVenta(Producto producto);
    }

    private final List<Producto> todosLosProductos = new ArrayList<>();
    private final List<Pedido> pedidosActivosMulticanal = new ArrayList<>();
    private final Map<String, Pedido> pedidosPorMesa = new LinkedHashMap<>();

    private final VentaMesasCanalCoordinator mesasCanalCoordinator;
    private final VentaMesasCanalUiRenderer mesasCanalUiRenderer;
    private final VentaCatalogoPresenter catalogoPresenter;
    private final ControlesNavegacionPos navControls;
    private final ControlesMesasPos mesasControls;
    private final ControlesCatalogoPos catalogoControls;
    private final HostCallbacks callbacks;

    private int categoriaFiltroSeleccionada;
    private String mesaSeleccionadaActual = "01";
    private String canalSeleccionadoActual = "MESA";
    private Integer pedidoActivoId;

    public VentaPosMesasCatalogoHelper(
            VentaMesasCanalCoordinator mesasCanalCoordinator,
            VentaMesasCanalUiRenderer mesasCanalUiRenderer,
            VentaCatalogoPresenter catalogoPresenter,
            ControlesNavegacionPos navControls,
            ControlesMesasPos mesasControls,
            ControlesCatalogoPos catalogoControls,
            HostCallbacks callbacks) {
        this.mesasCanalCoordinator = Objects.requireNonNull(mesasCanalCoordinator);
        this.mesasCanalUiRenderer = Objects.requireNonNull(mesasCanalUiRenderer);
        this.catalogoPresenter = Objects.requireNonNull(catalogoPresenter);
        this.navControls = Objects.requireNonNull(navControls);
        this.mesasControls = Objects.requireNonNull(mesasControls);
        this.catalogoControls = Objects.requireNonNull(catalogoControls);
        this.callbacks = Objects.requireNonNull(callbacks);
    }

    public void mostrarTabMesas() {
        TabPane tabPane = navControls.tabPanePosCatalogo();
        Tab tabMesas = navControls.tabMesas();
        if (tabPane != null && tabMesas != null) {
            tabPane.getSelectionModel().select(tabMesas);
        }
        refrescarMesasYCanalesAsync();
    }

    public void refrescarMesasYCanalesAsync() {
        UiAsyncExecutor.ejecutarAsync(
            mesasCanalCoordinator::listarPedidosActivosConDetallesSafe,
            this::aplicarPedidosActivosEnUI,
            ex -> LOGGER.log(Level.FINE, "Aviso consultando pedidos multicanal en POS", ex)
        );
    }

    public void refrescarMesasYCanalesSincrono() {
        aplicarPedidosActivosEnUI(mesasCanalCoordinator.listarPedidosActivosConDetallesSafe());
    }

    public void aplicarPedidosActivosEnUI(List<Pedido> activos) {
        pedidosActivosMulticanal.clear();
        if (activos != null) {
            pedidosActivosMulticanal.addAll(activos);
        }
        pedidosPorMesa.clear();
        VentaMesasCanalCoordinator.ResumenCanalesPos resumen =
                mesasCanalCoordinator.particionarPedidosActivos(pedidosActivosMulticanal);
        pedidosPorMesa.putAll(resumen.pedidosPorMesa());
        renderizarGridMesasPos();
        renderizarPedidosMulticanal(resumen.ordenesExternas());
        renderizarBarraRapidaOrdenes(pedidosActivosMulticanal);
    }

    public void seleccionarMesa(String numeroMesa) {
        String numNorm = mesasCanalCoordinator.normalizarNumeroMesa(numeroMesa);
        this.mesaSeleccionadaActual = numNorm;
        this.canalSeleccionadoActual = "MESA";
        Pedido activo = pedidosPorMesa.get(numNorm);
        if (activo != null) {
            cargarPedidoEnPOS(activo);
        } else {
            this.pedidoActivoId = null;
            callbacks.limpiarCarritoLocalYDescuentos();
            mesasCanalUiRenderer.actualizarCabeceraCanal(
                    navControls.lblOrdenHeader(),
                    navControls.lblCanalBadge(),
                    mesasCanalCoordinator.resolverCabeceraMesaLibre(numNorm));
            callbacks.recalcularTotales();
        }
        seleccionarTabCatalogo();
    }

    public void cargarPedidoEnPOS(Pedido pedido) {
        if (pedido == null) {
            return;
        }
        this.pedidoActivoId = pedido.getIdPedido() > 0 ? pedido.getIdPedido() : null;
        VentaMesasCanalCoordinator.CabeceraCanalPos cabecera = mesasCanalCoordinator.resolverCabeceraPedido(pedido);
        this.canalSeleccionadoActual = cabecera.canal();
        this.mesaSeleccionadaActual = cabecera.mesaNormalizada();
        mesasCanalUiRenderer.actualizarCabeceraCanal(
                navControls.lblOrdenHeader(),
                navControls.lblCanalBadge(),
                cabecera);

        List<DetallePedido> detallesIniciales = pedido.getDetalles();
        callbacks.aplicarDetallesPedidoEnPOS(detallesIniciales);
        if ((detallesIniciales == null || detallesIniciales.isEmpty()) && pedido.getIdPedido() > 0) {
            final int idObjetivo = pedido.getIdPedido();
            UiAsyncExecutor.ejecutarAsync(
                () -> mesasCanalCoordinator.listarDetallesPorPedido(idObjetivo),
                detallesCargados -> {
                    if (Integer.valueOf(idObjetivo).equals(this.pedidoActivoId)) {
                        callbacks.aplicarDetallesPedidoEnPOS(detallesCargados);
                    }
                },
                ex -> LOGGER.log(Level.FINE, "Aviso cargando detalles de pedido #" + idObjetivo, ex)
            );
        }
    }

    public void actualizarCachePedidoActivo(int idPedido, BigDecimal totalPedido, List<DetallePedido> detalles) {
        mesasCanalCoordinator.actualizarCachePedidoActivo(pedidosActivosMulticanal, pedidosPorMesa,
                idPedido, totalPedido, detalles);
        renderizarGridMesasPos();
        renderizarBarraRapidaOrdenes(pedidosActivosMulticanal);
    }

    public void cargarCategorias() {
        HBox boxCategorias = catalogoControls.boxCategorias();
        if (boxCategorias == null) {
            return;
        }
        UiAsyncExecutor.ejecutarAsync(
            catalogoPresenter::listarCategoriasProductos,
            categorias -> catalogoPresenter.renderizarChipsCategorias(boxCategorias, categorias, categoria -> {
                categoriaFiltroSeleccionada = categoria;
                filtrarCatalogo();
            }),
            ex -> LOGGER.log(Level.FINE, "Categorías no disponibles en arranque", ex)
        );
    }

    public void cargarCatalogoProductosAsync() {
        TilePane tileProductos = catalogoControls.tileProductos();
        if (tileProductos != null) {
            tileProductos.getChildren().clear();
        }
        UiAsyncExecutor.ejecutarAsync(
            catalogoPresenter::cargarProductosConFallbackDemo,
            productos -> {
                todosLosProductos.clear();
                todosLosProductos.addAll(productos);
                filtrarCatalogo();
            },
            ex -> {
                LOGGER.log(Level.WARNING, "Error al cargar catalogo desde BD, activando catalogo demo: {0}",
                        ex != null ? ex.getMessage() : "desconocido");
                todosLosProductos.clear();
                todosLosProductos.addAll(catalogoPresenter.crearProductosDemostracion());
                filtrarCatalogo();
            }
        );
    }

    public void cargarCatalogoProductosSincrono() {
        todosLosProductos.clear();
        todosLosProductos.addAll(catalogoPresenter.cargarProductosConFallbackDemo());
        filtrarCatalogo();
    }

    public void filtrarCatalogo() {
        TilePane tileProductos = catalogoControls.tileProductos();
        if (tileProductos == null) {
            return;
        }
        TextField txtBuscar = catalogoControls.txtBuscar();
        String filtro = txtBuscar != null ? txtBuscar.getText() : "";
        List<Producto> filtrados = catalogoPresenter.filtrarProductos(
                todosLosProductos, categoriaFiltroSeleccionada, filtro);
        catalogoPresenter.renderizarTarjetasCatalogo(tileProductos, filtrados, callbacks::agregarProductoAVenta);
    }

    public List<Producto> getTodosLosProductos() {
        return todosLosProductos;
    }

    public Integer getPedidoActivoId() {
        return pedidoActivoId;
    }

    public void setPedidoActivoId(Integer pedidoActivoId) {
        this.pedidoActivoId = pedidoActivoId;
    }

    public String getMesaSeleccionadaActual() {
        return mesaSeleccionadaActual;
    }

    public String getCanalSeleccionadoActual() {
        return canalSeleccionadoActual;
    }

    private void renderizarGridMesasPos() {
        mesasCanalUiRenderer.renderizarGridMesasPos(
                mesasControls.tileMesasPos(),
                navControls.lblResumenMesasPos(),
                pedidosPorMesa,
                TOTAL_MESAS_SALON,
                this::seleccionarMesa);
    }

    private void renderizarPedidosMulticanal(List<Pedido> externos) {
        mesasCanalUiRenderer.renderizarPedidosMulticanal(
                mesasControls.flowPedidosMulticanal(),
                externos,
                mesasCanalCoordinator,
                pedido -> {
                    cargarPedidoEnPOS(pedido);
                    seleccionarTabCatalogo();
                });
    }

    private void renderizarBarraRapidaOrdenes(List<Pedido> activos) {
        mesasCanalUiRenderer.renderizarBarraRapidaOrdenes(
                mesasControls.boxOrdenesEntrantes(),
                activos,
                mesaSeleccionadaActual,
                mesasCanalCoordinator,
                this::cargarPedidoEnPOS);
    }

    private void seleccionarTabCatalogo() {
        TabPane tabPane = navControls.tabPanePosCatalogo();
        Tab tabCatalogo = navControls.tabCatalogo();
        if (tabPane != null && tabCatalogo != null) {
            tabPane.getSelectionModel().select(tabCatalogo);
        }
    }
}
