package com.foodcore.controller.venta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.controller.VentaController;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Cliente;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Categoria;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.VentaPosConsultaService;
import com.foodcore.service.VentaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VentaCoordinatorsUnitTest {
    @Test
    void mesasCanalClasificaBadgeParticionaYConsultaConStub() {
        Pedido pedidoMesa = pedido(" 5 ", "MESA");
        Pedido kiosco = pedido("T-101", "MESA");
        Pedido delivery = pedido("DL-08", "DELIVERY");
        VentaPosConsultaService consulta = new VentaPosConsultaService() {
            @Override public List<Pedido> listarPedidosActivosConDetalles() { return List.of(pedidoMesa, kiosco); }
            @Override public List<com.foodcore.model.DetallePedido> listarDetallesPorPedido(int id) {
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
                        bruto.subtract(new BigDecimal("2.00")), List.of("promo")), new VentaService());
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
        assertSplit(cobro.construirPagosPorModalidad(VentaController.MODALIDAD_SPLIT_2,
                new BigDecimal("60.01"), lineas), "60.01");
        assertSplit(cobro.construirPagosPorModalidad(VentaController.MODALIDAD_SPLIT_4,
                new BigDecimal("60.01"), lineas), "60.01");
        assertSplit(cobro.construirPagosPorModalidad(VentaController.MODALIDAD_SPLIT_ITEMS,
                new BigDecimal("60.01"), lineas), "60.01");
        List<DetalleVenta> qty = List.of(detalle(4, 3, "7.00", "21.00"));
        assertSplit(cobro.construirPagosPorModalidad(VentaController.MODALIDAD_SPLIT_ITEMS,
                new BigDecimal("21.00"), qty), "21.00");
        Assertions.assertThrows(IllegalArgumentException.class, () -> cobro.construirPagosPorModalidad(
                VentaController.MODALIDAD_SPLIT_2, BigDecimal.ZERO, lineas));
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

    private static void assertSplit(List<VentaService.PagoSplit> pagos, String total) {
        assertTrue(pagos.size() >= 2);
        assertEquals(0, pagos.stream().map(VentaService.PagoSplit::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal(total)));
    }
}
