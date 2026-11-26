package com.foodcore.controller.venta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.controller.VentaController;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Cliente;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.AuthService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.VentaPosConsultaService;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VentaCoordinatorsUnitTest {

    @BeforeAll
    static void initFx() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @AfterEach
    void limpiarSesion() {
        AuthService.logout();
    }

    @Test
    void mesasCanalClasificaBadgeParticionaYConsultaConStub() {
        Pedido pedidoMesa = pedido(" 5 ", "MESA");
        Pedido kiosco = pedido("T-101", "MESA");
        Pedido delivery = pedido("DL-08", "DELIVERY");
        VentaPosConsultaService consulta = new VentaPosConsultaService() {
            @Override public List<Pedido> listarPedidosActivosConDetalles() { return List.of(pedidoMesa, kiosco); }
            @Override public List<DetallePedido> listarDetallesPorPedido(int id) {
                return List.of();
            }
        };
        DefaultVentaMesasCanalCoordinator coordinator = new DefaultVentaMesasCanalCoordinator(consulta);

        assertEquals("MESA", coordinator.determinarCanalPedido(pedidoMesa));
        assertEquals("KIOSCO", coordinator.determinarCanalPedido(kiosco));
        assertEquals("DELIVERY", coordinator.determinarCanalPedido(delivery));
        assertEquals("[MESA 05]", coordinator.construirTextoBadgeCanal(pedidoMesa));
        assertEquals("[KIOSCO]", coordinator.construirTextoBadgeCanal(kiosco));
        assertEquals("[DELIVERY]", coordinator.construirTextoBadgeCanal(delivery));
        assertEquals(2, coordinator.listarPedidosActivosConDetallesSafe().size());
        assertEquals(List.of(), coordinator.listarDetallesPorPedido(9));
        VentaMesasCanalCoordinator.ResumenCanalesPos resumen = coordinator
                .particionarPedidosActivos(List.of(pedidoMesa, kiosco, delivery));
        assertEquals(pedidoMesa, resumen.pedidosPorMesa().get("05"));
        assertEquals(List.of(kiosco, delivery), resumen.ordenesExternas());
    }

    @Test
    void catalogoCargaFiltraYUsaCatalogoDemoSinToolkit() throws Exception {
        Producto hamburguesa = producto(1, 2, "HAM-01", "Hamburguesa", true);
        Producto oculto = producto(2, 2, "HAM-02", "Oculta", false);
        AtomicInteger llamadas = new AtomicInteger();
        VentaCatalogoPresenter presenter = new VentaCatalogoPresenter(
                () -> List.of(new Categoria(2, "Comida", "", true)),
                () -> { llamadas.incrementAndGet(); return List.of(); });
        assertEquals(1, presenter.listarCategoriasProductos().size());
        assertEquals(8, presenter.cargarProductosConFallbackDemo().size());
        assertEquals(1, llamadas.get());
        assertEquals(List.of(hamburguesa), presenter.filtrarProductos(List.of(hamburguesa, oculto), 2, " ham "));
        assertEquals(List.of(hamburguesa), presenter.filtrarProductos(List.of(hamburguesa), 0, "HAM-01"));
        assertTrue(presenter.filtrarProductos(null, 0, "").isEmpty());
        assertEquals("descripción", presenter.construirDescripcionProducto(
                productoConDescripcion("descripción")));
        assertEquals("COMBO · PACK", presenter.construirDescripcionProducto(combo()));
    }

    @Test
    void carritoActualizaLineasTotalesYLimitaCanje() throws NegocioException {
        DefaultVentaCarritoHandler carrito = new DefaultVentaCarritoHandler(
                (items, bruto) -> new PromocionService.ResultadoEvaluacion(new BigDecimal("2.00"),
                        bruto.subtract(new BigDecimal("2.00")), List.of("promo")), new VentaServiceImpl());
        Producto producto = producto(4, 1, "P-04", "Producto", true);
        List<DetalleVenta> items = new ArrayList<>();
        assertTrue(carrito.agregarItemLocal(items, producto, null, null, List.of(), null, 2,
                null, new BigDecimal("10.00"), new BigDecimal("20.00")));
        assertFalse(carrito.agregarItemLocal(items, producto, null, null, List.of(), null, 1,
                null, new BigDecimal("10.00"), new BigDecimal("10.00")));
        assertEquals(3, items.get(0).getCantidad());
        assertEquals(new BigDecimal("30.00"), items.get(0).getSubtotal());
        assertEquals(new BigDecimal("30.00"), carrito.calcularSumaBruta(items));
        assertEquals(new BigDecimal("28.00"), carrito.obtenerBasePreCanje(items, new BigDecimal("2.00")));
        assertEquals(new BigDecimal("23.00"), carrito.obtenerTotalNeto(items,
                new BigDecimal("2.00"), new BigDecimal("5.00")));
        VentaCarritoHandler.ResumenTotalesVenta promocion = carrito.calcularTotales(items,
                BigDecimal.ZERO, false, new BigDecimal("50.00"));
        assertEquals(new BigDecimal("2.00"), promocion.descuentoActivo());
        assertEquals(new BigDecimal("28.00"), promocion.basePreCanje());
        assertEquals(new BigDecimal("28.00"), promocion.descuentoPuntosAjustado());
        assertEquals(BigDecimal.ZERO.setScale(2), promocion.totalNeto());
        VentaCarritoHandler.ResumenTotalesVenta manual = carrito.calcularTotales(items,
                new BigDecimal("3.00"), true, BigDecimal.ZERO);
        assertEquals(new BigDecimal("3.00"), manual.descuentoActivo());
        assertTrue(carrito.decrementarOEliminar(items, items.get(0)));
        assertEquals(2, items.get(0).getCantidad());
        assertEquals(new BigDecimal("20.00"), items.get(0).getSubtotal());
        assertTrue(carrito.decrementarOEliminar(items, items.get(0)));
        assertTrue(carrito.decrementarOEliminar(items, items.get(0)));
        assertTrue(items.isEmpty());
        assertFalse(carrito.decrementarOEliminar(items, null));
    }

    @Test
    void cobroConstruyeSplitsConSumaExactaYValidaAnulacion() {
        DefaultVentaCobroCoordinator cobro = new DefaultVentaCobroCoordinator(null, null, null);
        List<DetalleVenta> lineas = List.of(detalle(1, 1, "10.00", "10.00"),
                detalle(2, 1, "20.00", "20.00"), detalle(3, 1, "30.00", "30.00"));
        assertSplit(cobro.construirPagosPorModalidad(VentaCobroCoordinator.MODALIDAD_SPLIT_2,
                new BigDecimal("60.01"), lineas), "60.01");
        assertSplit(cobro.construirPagosPorModalidad(VentaCobroCoordinator.MODALIDAD_SPLIT_4,
                new BigDecimal("60.01"), lineas), "60.01");
        assertSplit(cobro.construirPagosPorModalidad(VentaCobroCoordinator.MODALIDAD_SPLIT_ITEMS,
                new BigDecimal("60.01"), lineas), "60.01");
        List<DetalleVenta> qty = List.of(detalle(4, 3, "7.00", "21.00"));
        assertSplit(cobro.construirPagosPorModalidad(VentaCobroCoordinator.MODALIDAD_SPLIT_ITEMS,
                new BigDecimal("21.00"), qty), "21.00");
        Assertions.assertThrows(IllegalArgumentException.class, () -> cobro.construirPagosPorModalidad(
                VentaCobroCoordinator.MODALIDAD_SPLIT_2, BigDecimal.ZERO, lineas));
        VentaCobroCoordinator.ValidacionAnulacionSupervisor valida = cobro
                .validarAnulacionSupervisor(" 42 ", "  motivo  ");
        assertTrue(valida.valido());
        assertEquals(42, valida.idVenta());
        assertEquals("motivo", valida.motivoFinal());
        assertFalse(cobro.validarAnulacionSupervisor("x", "").valido());
        assertFalse(cobro.validarAnulacionSupervisor(" ", "").valido());
    }

    @Test
    void fidelizacionValidaYEvaluaConServiciosStub() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Ada");
        cliente.setFechaNacimiento(LocalDate.now());
        VentaPosConsultaService consulta = new VentaPosConsultaService() {
            @Override public Optional<Cliente> buscarClientePorDniOTelefono(String criterio) {
                return "12345678".equals(criterio) ? Optional.of(cliente) : Optional.empty();
            }
            @Override public Cliente buscarORegistrarClienteMinoristaRapido(String dni, String nombre,
                    String telefono, LocalDate nacimiento) { return cliente; }
        };
        DefaultVentaFidelizacionCoordinator fidelizacion = new DefaultVentaFidelizacionCoordinator(
                consulta, new FidelizacionService(null, null));
        assertEquals(Optional.of(cliente), fidelizacion.buscarClientePorCriterio("12345678"));
        assertNotNull(fidelizacion.registrarClienteRapido("12345678", " Ada ", null, LocalDate.now()));
        assertThrows(NegocioException.class,
                () -> fidelizacion.registrarClienteRapido("12", "Ada", null, null));
        assertThrows(NegocioException.class,
                () -> fidelizacion.registrarClienteRapido("12345678", " ", null, null));
        assertTrue(fidelizacion.evaluarCumpleanos(cliente).esCumpleanos());
        assertFalse(fidelizacion.evaluarCumpleanos(null).esCumpleanos());
        assertEquals(new BigDecimal("10.00"), fidelizacion.calcularDescuentoCumpleanos(
                new BigDecimal("100.00")));
        FidelizacionService.BeneficioCanje beneficio = fidelizacion.calcularCanjePorBloques(
                1000, 10, new BigDecimal("100.00"));
        assertNotNull(beneficio);
        assertEquals(beneficio.bloquesDisponibles(), fidelizacion.construirOpcionesCanje(beneficio).size());
        assertTrue(fidelizacion.buscarClientePorCriterio("inexistente").isEmpty());
    }

    @Test
    void errorBdEnAgregarProductoAPedidoExistenteNoMutaCarritoYMuestraAlerta() throws Exception {
        FakeVentaPosInteractionPort fakePort = new FakeVentaPosInteractionPort();
        DefaultVentaFidelizacionCoordinator fid = new DefaultVentaFidelizacionCoordinator(
                criterio -> Optional.empty(), (d, n, t, f) -> null, cli -> false, b -> BigDecimal.ZERO,
                (pts, blk, base) -> null);
        VentaController controller = crearControllerConStubs(fakePort, fid, new AtomicInteger());

        DetallePedido inicial = new DetallePedido(1, 42, 1, "Lomo", 1,
                new BigDecimal("20.00"), new BigDecimal("20.00"), "");
        Pedido pedidoExistente = new Pedido(42, 1, null, "04", "REGISTRADO", new BigDecimal("20.00"));
        pedidoExistente.setDetalles(List.of(inicial));

        controller.setPedidoService(new PedidoServiceImpl() {
            @Override
            public Pedido agregarItemsAPedido(int idPedido, List<DetallePedido> nuevos) throws SQLException {
                throw new SQLException("BD no disponible simulada");
            }
        });

        CountDownLatch alertaLatch = new CountDownLatch(1);
        fakePort.resetUltimaAlerta(alertaLatch);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            controller.cargarPedidoEnPOS(pedidoExistente);
            assertEquals(1, controller.getItemsVenta().size());
            controller.agregarProductoAVenta(producto(99, 1, "P-99", "Chicha", true));
        });

        assertTrue(alertaLatch.await(5, TimeUnit.SECONDS), "Debe reportar error asíncrono mediante alerta");
        assertEquals("Error al Agregar Ítem", fakePort.getUltimaAlertaTitulo());
        assertEquals(Alert.AlertType.ERROR, fakePort.getUltimaAlertaTipo());
        assertFalse(controller.isOperacionPedidoEnProgreso());
        assertEquals(1, controller.getItemsVenta().size(),
                "Ante error en BD NO debe mutar itemsVenta en RAM con fallback silencioso");
        assertEquals(1, controller.getItemsVenta().get(0).getIdProducto());
    }

    @Test
    void busquedaAsincronaClienteAsociaEnFxYReportaErrorSinBloquear() throws Exception {
        FakeVentaPosInteractionPort fakePort = new FakeVentaPosInteractionPort();
        Cliente cli = new Cliente();
        cli.setIdCliente(12);
        cli.setTipoDocumento("DNI");
        cli.setNumeroDocumento("12345678");
        cli.setRazonSocial("Ada Lovelace");
        cli.setPuntosAcumulados(120);

        AtomicBoolean ejecutoFueraDeFx = new AtomicBoolean(false);
        AtomicBoolean simularError = new AtomicBoolean(false);
        DefaultVentaFidelizacionCoordinator fid = new DefaultVentaFidelizacionCoordinator(
                criterio -> {
                    ejecutoFueraDeFx.set(!Platform.isFxApplicationThread());
                    if (simularError.get()) {
                        throw new IllegalStateException(new SQLException("Timeout consultando cliente"));
                    }
                    return "12345678".equals(criterio) ? Optional.of(cli) : Optional.empty();
                },
                (d, n, t, f) -> null,
                c -> false,
                b -> BigDecimal.ZERO,
                (pts, blk, base) -> null);

        VentaController controller = crearControllerConStubs(fakePort, fid, new AtomicInteger());

        CountDownLatch okLatch = new CountDownLatch(1);
        AtomicBoolean callbackEnFx = new AtomicBoolean(false);
        AtomicReference<Optional<Cliente>> okRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() ->
                controller.buscarYAsociarClientePorCriterioAsync(" 12345678 ", Boolean.FALSE, opt -> {
                    callbackEnFx.set(Platform.isFxApplicationThread());
                    okRef.set(opt);
                    okLatch.countDown();
                }));

        assertTrue(okLatch.await(5, TimeUnit.SECONDS));
        assertTrue(ejecutoFueraDeFx.get(), "La búsqueda debe ejecutarse fuera del JavaFX Application Thread");
        assertTrue(callbackEnFx.get(), "El callback debe regresar al JavaFX Application Thread");
        assertTrue(okRef.get().isPresent());
        assertSame(cli, controller.getClienteSeleccionadoPos());
        assertFalse(controller.isBusquedaClienteEnProgreso());

        simularError.set(true);
        CountDownLatch errLatch = new CountDownLatch(1);
        AtomicReference<Optional<Cliente>> errRef = new AtomicReference<>();
        fakePort.resetUltimaAlerta(null);

        JavaFxTestSupport.runOnFxThreadAndWait(() ->
                controller.buscarYAsociarClientePorCriterioAsync("99999999", Boolean.FALSE, opt -> {
                    errRef.set(opt);
                    errLatch.countDown();
                }));

        assertTrue(errLatch.await(5, TimeUnit.SECONDS));
        assertTrue(errRef.get().isEmpty());
        assertEquals("Error al Buscar Cliente", fakePort.getUltimaAlertaTitulo());
        assertEquals(Alert.AlertType.ERROR, fakePort.getUltimaAlertaTipo());
        assertFalse(controller.isBusquedaClienteEnProgreso());
    }

    @Test
    void operacionesPosRechazanSinSesionActiva() throws Exception {
        AuthService.logout();
        FakeVentaPosInteractionPort fakePort = new FakeVentaPosInteractionPort();
        DefaultVentaFidelizacionCoordinator fid = new DefaultVentaFidelizacionCoordinator(
                criterio -> Optional.empty(), (d, n, t, f) -> null, c -> false, b -> BigDecimal.ZERO,
                (pts, blk, base) -> null);
        AtomicInteger llamadasPersistencia = new AtomicInteger();
        VentaController controller = crearControllerConStubs(fakePort, fid, llamadasPersistencia);

        controller.setPedidoService(new PedidoServiceImpl() {
            @Override
            public Pedido registrarPedidoYEnviarComanda(Pedido pedido, List<DetallePedido> detalles) {
                llamadasPersistencia.incrementAndGet();
                return pedido;
            }
        });

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            controller.getItemsVenta().setAll(detalle(1, 1, "25.00", "25.00"));

            fakePort.resetUltimaAlerta(null);
            controller.handleEnviarComanda(null);
            assertEquals("Sesión Requerida", fakePort.getUltimaAlertaTitulo());
            assertEquals(Alert.AlertType.ERROR, fakePort.getUltimaAlertaTipo());

            fakePort.resetUltimaAlerta(null);
            controller.handleDividirCuenta(null);
            assertEquals("Sesión Requerida", fakePort.getUltimaAlertaTitulo());
            assertEquals(Alert.AlertType.ERROR, fakePort.getUltimaAlertaTipo());
        });

        assertThrows(NegocioException.class, () -> controller.ejecutarCobroSplitEnPagos(
                List.of(new PagoSplit("EFECTIVO", new BigDecimal("25.00")))));
        assertEquals(0, llamadasPersistencia.get(),
                "Ninguna operación POS debe persistir sin sesión de usuario activa");
        assertNull(AuthService.getUsuarioSesionActual());
    }

    private static VentaController crearControllerConStubs(
            FakeVentaPosInteractionPort fakePort,
            VentaFidelizacionCoordinator fidCoordinator,
            AtomicInteger llamadasPersistencia) {
        DefaultVentaMesasCanalCoordinator mesas = new DefaultVentaMesasCanalCoordinator(null);
        VentaMesasCanalUiRenderer renderer = new VentaMesasCanalUiRenderer();
        VentaCatalogoPresenter catalogo = new VentaCatalogoPresenter(List::of, List::of);
        DefaultVentaCarritoHandler carrito = new DefaultVentaCarritoHandler(
                (items, bruto) -> new PromocionService.ResultadoEvaluacion(BigDecimal.ZERO, bruto, List.of()),
                new VentaServiceImpl());
        DefaultVentaCobroCoordinator cobro = new DefaultVentaCobroCoordinator(
                new VentaServiceImpl() {
                    @Override
                    public com.foodcore.model.Venta registrarVentaDividida(com.foodcore.model.Venta venta,
                            List<DetalleVenta> det, com.foodcore.model.ComprobantePago comp,
                            List<PagoSplit> pagos) {
                        llamadasPersistencia.incrementAndGet();
                        return venta;
                    }
                },
                new AutorizacionService());
        return new VentaController(mesas, renderer, catalogo, carrito, cobro, fidCoordinator, fakePort);
    }

    private static Pedido pedido(String mesa, String tipo) {
        Pedido pedido = new Pedido();
        pedido.setNumeroMesa(mesa);
        pedido.setTipoPedido(tipo);
        return pedido;
    }

    private static Producto producto(int id, int categoria, String codigo, String nombre, boolean disponible) {
        return new Producto(id, categoria, codigo, nombre, "desc", new BigDecimal("10.00"),
                BigDecimal.ONE, disponible);
    }

    private static Producto productoConDescripcion(String descripcion) {
        Producto producto = producto(9, 1, "A", "A", true);
        producto.setDescripcion(descripcion);
        return producto;
    }

    private static Producto combo() {
        Producto producto = producto(10, 1, "PACK", "Pack", true);
        producto.setEsCombo(true);
        return producto;
    }

    private static DetalleVenta detalle(int id, int cantidad, String precio, String subtotal) {
        return new DetalleVenta(0, 0, id, cantidad, new BigDecimal(precio), new BigDecimal(subtotal));
    }

    private static void assertSplit(List<PagoSplit> pagos, String total) {
        assertTrue(pagos.size() >= 2);
        assertEquals(0, pagos.stream().map(PagoSplit::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal(total)));
    }
}
