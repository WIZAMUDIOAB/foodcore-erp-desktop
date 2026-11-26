package com.foodcore.api;

import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.service.api.ApiOperacionesPort;
import com.foodcore.service.api.DefaultApiOperacionesService;
import io.javalin.http.Context;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Fachada compatible delgada que compone y delega las rutas REST en {@link SalonApiController},
 * {@link KdsSyncApiController} y {@link CobroApiController}.
 */
public class PedidoController {

    private final SalonApiController salonController;
    private final KdsSyncApiController kdsSyncController;
    private final CobroApiController cobroController;

    public PedidoController(ApiState state,
                            ProductoDAO productoDAO,
                            MesaDAO mesaDAO,
                            PedidoDAO pedidoDAO,
                            PedidoService pedidoService,
                            CajaService cajaService) {
        this(state, productoDAO, mesaDAO, pedidoDAO, pedidoService, cajaService, new VentaServiceImpl());
    }

    public PedidoController(ApiState state,
                            ProductoDAO productoDAO,
                            MesaDAO mesaDAO,
                            PedidoDAO pedidoDAO,
                            PedidoService pedidoService,
                            CajaService cajaService,
                            VentaService ventaService) {
        ApiOperacionesPort operaciones = new DefaultApiOperacionesService(
                productoDAO,
                mesaDAO,
                pedidoDAO,
                new VentaDAOImpl(),
                pedidoService,
                cajaService,
                ventaService != null ? ventaService : new VentaServiceImpl());
        this.salonController = new SalonApiController(state, operaciones);
        this.kdsSyncController = new KdsSyncApiController(state, operaciones);
        this.cobroController = new CobroApiController(state, operaciones);
    }

    SalonApiController salonController() {
        return salonController;
    }

    KdsSyncApiController kdsSyncController() {
        return kdsSyncController;
    }

    CobroApiController cobroController() {
        return cobroController;
    }

    public void handleListarProductos(Context ctx) {
        salonController.handleListarProductos(ctx);
    }

    public void handleListarProductosDisponibles(Context ctx) {
        salonController.handleListarProductosDisponibles(ctx);
    }

    public void handleListarProductosPublicos(Context ctx) {
        salonController.handleListarProductosPublicos(ctx);
    }

    public void handleListarProductosDisponiblesPublicos(Context ctx) {
        salonController.handleListarProductosDisponiblesPublicos(ctx);
    }

    public void handleActualizarDisponibilidad(Context ctx) {
        salonController.handleActualizarDisponibilidad(ctx);
    }

    public void handleImagenProducto(Context ctx) {
        salonController.handleImagenProducto(ctx);
    }

    public void handleServirUploadPlatillo(Context ctx) {
        salonController.handleServirUploadPlatillo(ctx);
    }

    public void handleListarMesas(Context ctx) {
        salonController.handleListarMesas(ctx);
    }

    public void handleCrearPedido(Context ctx) {
        kdsSyncController.handleCrearPedido(ctx);
    }

    public void handleListarPedidos(Context ctx) {
        kdsSyncController.handleListarPedidos(ctx);
    }

    public void handleObtenerPedidoPorUuid(Context ctx) {
        kdsSyncController.handleObtenerPedidoPorUuid(ctx);
    }

    public void handleActualizarEstadoPedido(Context ctx) {
        kdsSyncController.handleActualizarEstadoPedido(ctx);
    }

    public void handleCajaEstado(Context ctx) {
        cobroController.handleCajaEstado(ctx);
    }

    public void handleCajaAbrir(Context ctx) {
        cobroController.handleCajaAbrir(ctx);
    }

    public void handleCajaCerrar(Context ctx) {
        cobroController.handleCajaCerrar(ctx);
    }

    public void handleCajaCobrar(Context ctx) {
        cobroController.handleCajaCobrar(ctx);
    }

    static boolean esRutaImagenSospechosa(String rawInput) {
        return ApiPedidoJsonMapper.esRutaImagenSospechosa(rawInput);
    }

    static Optional<Path> resolverArchivoImagenConfinado(Path baseDirRaw, String rawInput) {
        return ApiPedidoJsonMapper.resolverArchivoImagenConfinado(baseDirRaw, rawInput);
    }
}
