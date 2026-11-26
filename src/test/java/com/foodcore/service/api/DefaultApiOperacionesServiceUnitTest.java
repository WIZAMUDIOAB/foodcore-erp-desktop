package com.foodcore.service.api;

import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Caja;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Venta;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.service.api.ApiOperacionesPort.EstadoMesasSnapshot;
import com.foodcore.service.api.ApiOperacionesPort.ItemSolicitudPedido;
import com.foodcore.service.api.ApiOperacionesPort.PedidoConDetalles;
import com.foodcore.service.api.ApiOperacionesPort.PedidosActivosSnapshot;
import com.foodcore.service.api.ApiOperacionesPort.ResultadoCierreCaja;
import com.foodcore.service.api.ApiOperacionesPort.ResultadoCobroCaja;
import com.foodcore.service.api.ApiOperacionesPort.ResultadoCreacionPedido;
import com.foodcore.service.api.ApiOperacionesPort.SolicitudCobroCaja;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias determinísticas para {@link DefaultApiOperacionesService},
 * {@link ApiOperacionException} y los records de {@link ApiOperacionesPort} con proxies JDK puros.
 */
class DefaultApiOperacionesServiceUnitTest {

    @Test
    @DisplayName("ApiOperacionException y records de ApiOperacionesPort realizan copias defensivas completas")
    void apiOperacionExceptionYRecordsCopiasDefensivas() {
        ApiOperacionException exSimple = new ApiOperacionException(409, "Conflicto");
        assertEquals(409, exSimple.getStatusCode());
        assertEquals("Conflicto", exSimple.getMessage());

        SQLException causa = new SQLException("Fallo BD");
        ApiOperacionException exConCausa = new ApiOperacionException(503, "Servicio caído", causa);
        assertEquals(503, exConCausa.getStatusCode());
        assertSame(causa, exConCausa.getCause());

        PedidosActivosSnapshot vacioNull = new PedidosActivosSnapshot(null, null);
        assertTrue(vacioNull.pedidos().isEmpty());
        assertTrue(vacioNull.detallesPorPedido().isEmpty());

        PedidosActivosSnapshot vacioMap = new PedidosActivosSnapshot(List.of(), Map.of());
        assertTrue(vacioMap.detallesPorPedido().isEmpty());

        Map<Integer, List<DetallePedido>> mapaConNulos = new HashMap<>();
        mapaConNulos.put(null, List.of());
        mapaConNulos.put(10, null);
        DetallePedido det = new DetallePedido(1, 10, 5, 2, new BigDecimal("10.00"), new BigDecimal("20.00"), "");
        mapaConNulos.put(11, List.of(det));
        Pedido p10 = new Pedido(10, 1, null, "01", "PENDIENTE", new BigDecimal("20.00"));

        PedidosActivosSnapshot poblado = new PedidosActivosSnapshot(List.of(p10), mapaConNulos);
        assertEquals(1, poblado.pedidos().size());
        assertEquals(2, poblado.detallesPorPedido().size());
        assertTrue(poblado.detallesPorPedido().get(10).isEmpty());
        assertEquals(1, poblado.detallesPorPedido().get(11).size());

        EstadoMesasSnapshot mesasNull = new EstadoMesasSnapshot(null, null);
        assertTrue(mesasNull.estadosPorMesa().isEmpty());
        assertTrue(mesasNull.totalesPorMesa().isEmpty());

        ResultadoCreacionPedido resNull = new ResultadoCreacionPedido(p10, null, false);
        assertTrue(resNull.detalles().isEmpty());

        PedidoConDetalles pcdNull = new PedidoConDetalles(p10, null);
        assertTrue(pcdNull.detalles().isEmpty());

        SolicitudCobroCaja solNull = new SolicitudCobroCaja(1, 0, "01", "TICKET", "EFECTIVO", "k", null, null);
        assertTrue(solNull.itemsDirectos().isEmpty());

        DefaultApiOperacionesService ctorPublico = new DefaultApiOperacionesService(
                proxyVacio(ProductoDAO.class),
                proxyVacio(MesaDAO.class),
                proxyVacio(PedidoDAO.class),
                proxyVacio(VentaDAO.class),
                proxyVacio(PedidoService.class),
                proxyVacio(CajaService.class),
                proxyVacio(VentaService.class)
        );
        assertNotNull(ctorPublico);
    }

    @Test
    @DisplayName("listarCatalogoProductos, actualizarDisponibilidadProducto y obtenerImagenUrlProducto cubren rutas y errores")
    void catalogoDisponibilidadEImagen() throws Exception {
        Fixture fx = new Fixture();
        Producto disp = crearProducto(1, "Lomo Saltado", new BigDecimal("35.00"), true);
        fx.productosDisponibles = List.of(disp);
        fx.productosTodos = List.of();

        // Fallback cuando soloDisponibles=false y listarTodos viene vacío
        assertEquals(List.of(disp), fx.service.listarCatalogoProductos(false));
        assertEquals(List.of(disp), fx.service.listarCatalogoProductos(true));

        Producto noDisp = crearProducto(2, "Ceviche", new BigDecimal("40.00"), false);
        fx.productosTodos = List.of(disp, noDisp);
        assertEquals(2, fx.service.listarCatalogoProductos(false).size());

        // Obtener imagen y disponibilidad exitosa
        fx.imagenUrl = Optional.of("uploads/platillos/lomo.png");
        assertEquals(Optional.of("uploads/platillos/lomo.png"), fx.service.obtenerImagenUrlProducto(1));

        fx.service.actualizarDisponibilidadProducto(1, false);
        assertEquals(1, fx.ultimoIdDisponibilidad.get());
        assertFalse(fx.ultimoEstadoDisponibilidad.get());

        // Conexión nula
        fx.nextConnection = () -> null;
        assertTrue(fx.service.listarCatalogoProductos(true).isEmpty());
        assertTrue(fx.service.obtenerImagenUrlProducto(1).isEmpty());
        ApiOperacionException exConnNull = assertThrows(ApiOperacionException.class,
                () -> fx.service.actualizarDisponibilidadProducto(1, true));
        assertEquals(503, exConnNull.getStatusCode());

        // Error SQL en ConnectionProvider
        fx.nextConnection = () -> {
            throw new SQLException("Pool caído");
        };
        assertTrue(fx.service.listarCatalogoProductos(true).isEmpty());
        assertTrue(fx.service.obtenerImagenUrlProducto(1).isEmpty());
        ApiOperacionException exSql = assertThrows(ApiOperacionException.class,
                () -> fx.service.actualizarDisponibilidadProducto(1, true));
        assertEquals(503, exSql.getStatusCode());
    }

    @Test
    @DisplayName("obtenerEstadoMesas combina mesas y pedidos activos y tolera errores BD/runtime")
    void obtenerEstadoMesas() {
        Fixture fx = new Fixture();
        fx.estadosMesas = Map.of("01", "LIBRE", "02", "LIBRE");
        fx.totalesActivosPorMesa = Map.of("02", new BigDecimal("85.50"));

        EstadoMesasSnapshot snap = fx.service.obtenerEstadoMesas();
        assertEquals("LIBRE", snap.estadosPorMesa().get("01"));
        assertEquals("OCUPADA", snap.estadosPorMesa().get("02"));
        assertEquals(new BigDecimal("85.50"), snap.totalesPorMesa().get("02"));

        fx.nextConnection = () -> null;
        assertTrue(fx.service.obtenerEstadoMesas().estadosPorMesa().isEmpty());

        fx.nextConnection = () -> {
            throw new SQLException("Error SQL mesas");
        };
        assertTrue(fx.service.obtenerEstadoMesas().estadosPorMesa().isEmpty());

        fx.nextConnection = () -> {
            throw new IllegalStateException("Fallo runtime");
        };
        assertTrue(fx.service.obtenerEstadoMesas().estadosPorMesa().isEmpty());
    }

    @Test
    @DisplayName("crearPedidoOIdempotente cubre idempotencia, validaciones catálogo, conflictos y sincronización mesa")
    void crearPedidoOIdempotente() throws Exception {
        Fixture fx = new Fixture();

        // 1. Conexión nula -> 503
        fx.nextConnection = () -> null;
        ApiOperacionException exNullConn = assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "uuid-1", List.of()));
        assertEquals(503, exNullConn.getStatusCode());

        // 2. Pedido idempotente existente
        fx.nextConnection = fx::newConnection;
        Pedido existente = new Pedido(50, 1, 1, "01", "PENDIENTE", new BigDecimal("30.00"));
        fx.pedidoPorUuid = Optional.of(existente);
        fx.detallesPedido = List.of(new DetallePedido(1, 50, 1, "Arroz", 1,
                new BigDecimal("30.00"), new BigDecimal("30.00"), ""));
        ResultadoCreacionPedido resIdemp = fx.service.crearPedidoOIdempotente(1, "01", "uuid-1", List.of());
        assertTrue(resIdemp.idempotente());
        assertEquals(50, resIdemp.pedido().getIdPedido());
        assertEquals(1, resIdemp.detalles().size());

        // 3. Validaciones de catálogo (no encontrado, no disponible, precio null y <= 0)
        fx.pedidoPorUuid = Optional.empty();
        Producto prodNoDisp = crearProducto(2, "Agotado", new BigDecimal("15.00"), false);
        Producto prodPrecioNull = crearProducto(3, "SinPrecio", null, true);
        Producto prodPrecioCero = crearProducto(4, "Gratis", BigDecimal.ZERO, true);
        Producto prodOk = crearProducto(5, "Pollo", new BigDecimal("25.50"), true);
        fx.productosTodos = List.of(prodNoDisp, prodPrecioNull, prodPrecioCero, prodOk);

        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u2",
                        List.of(new ItemSolicitudPedido(999, 1, "")))).getStatusCode());
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u3",
                        List.of(new ItemSolicitudPedido(2, 1, "")))).getStatusCode());
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u4",
                        List.of(new ItemSolicitudPedido(3, 1, "")))).getStatusCode());
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u5",
                        List.of(new ItemSolicitudPedido(4, 1, "")))).getStatusCode());

        // 4. Error SQL consultando catálogo -> 503
        fx.sqlErrorEnProductoTodos = true;
        assertEquals(503, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u6", List.of())).getStatusCode());
        fx.sqlErrorEnProductoTodos = false;

        // 5. Errores en pedidoService.registrarPedidoYEnviarComanda (ConflictoEstado, Negocio, SQLException)
        List<ItemSolicitudPedido> itemsValidos = List.of(new ItemSolicitudPedido(5, 2, null));
        fx.errorRegistrarPedido = new ConflictoEstadoException("Mesa ocupada");
        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u7", itemsValidos)).getStatusCode());

        fx.errorRegistrarPedido = new ConflictoEstadoException(null);
        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u8", itemsValidos)).getStatusCode());

        fx.errorRegistrarPedido = new NegocioException("Regla incumplida");
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u9", itemsValidos)).getStatusCode());

        fx.errorRegistrarPedido = new NegocioException((String) null);
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u10", itemsValidos)).getStatusCode());

        fx.errorRegistrarPedido = new SQLException("Deadlock pedido");
        assertEquals(503, assertThrows(ApiOperacionException.class, () ->
                fx.service.crearPedidoOIdempotente(1, "01", "u11", itemsValidos)).getStatusCode());

        // 6. Éxito con mesa alfanumérica sin dígitos (NumberFormatException) y fallo no bloqueante al actualizar mesa
        fx.errorRegistrarPedido = null;
        fx.sqlErrorActualizarMesa = true;
        ResultadoCreacionPedido creadoAlfa = fx.service.crearPedidoOIdempotente(
                1, "BARRA-VIP", "uuid-alfa", List.of(new ItemSolicitudPedido(5, 2, "Sin sal")));
        assertFalse(creadoAlfa.idempotente());
        assertEquals(new BigDecimal("51.00"), creadoAlfa.pedido().getTotal());
        assertNull(creadoAlfa.pedido().getIdMesa());

        // 7. Éxito con mesa numérica y notas null
        fx.sqlErrorActualizarMesa = false;
        ResultadoCreacionPedido creadoNum = fx.service.crearPedidoOIdempotente(
                1, "Mesa-04", "uuid-num", List.of(new ItemSolicitudPedido(5, 1, null)));
        assertEquals(Integer.valueOf(4), creadoNum.pedido().getIdMesa());
        assertEquals("", creadoNum.detalles().get(0).getNotasPreparacion());
    }

    @Test
    @DisplayName("listarPedidosActivosConDetalles, buscarPedidoPorUuid y actualizarEstadoPedido cubren flujo y errores")
    void consultasYActualizacionEstadoPedido() throws Exception {
        Fixture fx = new Fixture();
        Pedido p = new Pedido(77, 1, 2, "02", "EN_PREPARACION", new BigDecimal("40.00"));
        fx.pedidosActivos = List.of(p);
        fx.detallesActivosAgrupados = Map.of(77, List.of());

        PedidosActivosSnapshot snap = fx.service.listarPedidosActivosConDetalles();
        assertEquals(1, snap.pedidos().size());

        // buscarPedidoPorUuid encontrado y vacío
        fx.pedidoPorUuid = Optional.of(p);
        assertTrue(fx.service.buscarPedidoPorUuid("uuid-77").isPresent());
        fx.pedidoPorUuid = Optional.empty();
        assertTrue(fx.service.buscarPedidoPorUuid("uuid-inexistente").isEmpty());

        // actualizarEstadoPedido: no encontrado (404), transición válida, terminal igual, terminal distinto (409)
        fx.pedidoPorId = Optional.empty();
        assertEquals(404, assertThrows(ApiOperacionException.class, () ->
                fx.service.actualizarEstadoPedido(77, "LISTO")).getStatusCode());

        fx.pedidoPorId = Optional.of(p);
        fx.service.actualizarEstadoPedido(77, "LISTO");
        assertEquals("LISTO", fx.ultimoEstadoPedidoActualizado.get());

        Pedido cobrado = new Pedido(78, 1, 2, "02", "cobrado", new BigDecimal("40.00"));
        fx.pedidoPorId = Optional.of(cobrado);
        fx.service.actualizarEstadoPedido(78, "COBRADO");
        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.actualizarEstadoPedido(78, "LISTO")).getStatusCode());

        Pedido sinEstado = new Pedido(79, 1, 2, "02", null, new BigDecimal("40.00"));
        fx.pedidoPorId = Optional.of(sinEstado);
        fx.service.actualizarEstadoPedido(79, "LISTO");

        // Conexión nula -> 503 en los 3 métodos
        fx.nextConnection = () -> null;
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.listarPedidosActivosConDetalles()).getStatusCode());
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.buscarPedidoPorUuid("u")).getStatusCode());
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.actualizarEstadoPedido(77, "LISTO")).getStatusCode());

        // SQLException -> 503 en los 3 métodos
        fx.nextConnection = () -> {
            throw new SQLException("BD caída");
        };
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.listarPedidosActivosConDetalles()).getStatusCode());
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.buscarPedidoPorUuid("u")).getStatusCode());
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.actualizarEstadoPedido(77, "LISTO")).getStatusCode());
    }

    @Test
    @DisplayName("consultarEstadoCaja, abrirTurnoCaja y cerrarTurnoCaja cubren cálculos y mapeo HTTP")
    void operacionesCaja() throws Exception {
        Fixture fx = new Fixture();

        // 1. consultarEstadoCaja sin caja y con error en obtenerCajaActiva
        fx.cajaActiva = Optional.empty();
        assertFalse(fx.service.consultarEstadoCaja().abierta());

        fx.errorObtenerCajaActiva = new SQLException("Error caja");
        assertFalse(fx.service.consultarEstadoCaja().abierta());

        // 2. consultarEstadoCaja con error calculando totales y luego con datos completos
        fx.errorObtenerCajaActiva = null;
        Caja caja = new Caja(5, 1, "Caja 1", new BigDecimal("100.00"), "ABIERTA");
        fx.cajaActiva = Optional.of(caja);
        fx.errorTotalesCaja = new SQLException("Error sumas");
        ApiOperacionesPort.EstadoCajaSnapshot snapErrorTotales = fx.service.consultarEstadoCaja();
        assertTrue(snapErrorTotales.abierta());
        assertEquals(new BigDecimal("100.00"), snapErrorTotales.saldoEsperado());

        fx.errorTotalesCaja = null;
        fx.ventasTurno = new BigDecimal("250.00");
        fx.gastosTurno = new BigDecimal("30.00");
        ApiOperacionesPort.EstadoCajaSnapshot snapOk = fx.service.consultarEstadoCaja();
        assertEquals(new BigDecimal("320.00"), snapOk.saldoEsperado());

        // 3. abrirTurnoCaja: éxito, 409 ya abierta, 422 negocio, 422 null, 503 SQL
        assertEquals(12, fx.service.abrirTurnoCaja(1, "Principal", new BigDecimal("100.00")));
        fx.errorAbrirCaja = new NegocioException("Ya existe una caja abierta en este turno.");
        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.abrirTurnoCaja(1, "Principal", new BigDecimal("100.00"))).getStatusCode());
        fx.errorAbrirCaja = new NegocioException("Monto negativo");
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.abrirTurnoCaja(1, "Principal", new BigDecimal("-1.00"))).getStatusCode());
        fx.errorAbrirCaja = new NegocioException((String) null);
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.abrirTurnoCaja(1, "Principal", BigDecimal.ZERO)).getStatusCode());
        fx.errorAbrirCaja = new SQLException("SQL abrir");
        assertEquals(503, assertThrows(ApiOperacionException.class, () ->
                fx.service.abrirTurnoCaja(1, "Principal", new BigDecimal("10.00"))).getStatusCode());
        fx.errorAbrirCaja = null;

        // 4. cerrarTurnoCaja: éxito con idSolicitado<=0 y explícito, conflictos y errores
        ResultadoCierreCaja resCierre = fx.service.cerrarTurnoCaja(1, 0, new BigDecimal("325.00"));
        assertEquals(5, resCierre.idCaja());
        assertEquals(new BigDecimal("320.00"), resCierre.esperado());
        assertEquals(new BigDecimal("5.00"), resCierre.diferencia());

        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.cerrarTurnoCaja(1, 99, new BigDecimal("320.00"))).getStatusCode());

        fx.cajaActiva = Optional.empty();
        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.cerrarTurnoCaja(1, 5, new BigDecimal("320.00"))).getStatusCode());

        fx.cajaActiva = Optional.of(caja);
        fx.errorCerrarCaja = new CajaCerradaException();
        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.cerrarTurnoCaja(1, 5, new BigDecimal("320.00"))).getStatusCode());
        fx.errorCerrarCaja = new CajaCerradaException(null);
        assertEquals(409, assertThrows(ApiOperacionException.class, () ->
                fx.service.cerrarTurnoCaja(1, 5, new BigDecimal("320.00"))).getStatusCode());
        fx.errorCerrarCaja = new NegocioException("Contado inválido");
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.cerrarTurnoCaja(1, 5, new BigDecimal("320.00"))).getStatusCode());
        fx.errorCerrarCaja = new NegocioException((String) null);
        assertEquals(422, assertThrows(ApiOperacionException.class, () ->
                fx.service.cerrarTurnoCaja(1, 5, new BigDecimal("320.00"))).getStatusCode());
        fx.errorCerrarCaja = new SQLException("SQL cerrar");
        assertEquals(503, assertThrows(ApiOperacionException.class, () ->
                fx.service.cerrarTurnoCaja(1, 5, new BigDecimal("320.00"))).getStatusCode());
    }

    @Test
    @DisplayName("cobrarEnCaja cubre pedido/directo, comprobantes, errores validación/negocio/SQL y liberación mesa")
    void cobrarEnCaja() throws Exception {
        Fixture fx = new Fixture();
        fx.siguienteCorrelativo = 42;
        Producto prodOk = crearProducto(1, "Pollo", new BigDecimal("20.00"), true);
        Producto prodNoDisp = crearProducto(2, "Agotado", new BigDecimal("10.00"), false);
        Producto prodNullPrecio = crearProducto(3, "SinPrecio", null, true);
        fx.productosTodos = List.of(prodOk, prodNoDisp, prodNullPrecio);

        // 1. Conexión nula -> 503
        fx.nextConnection = () -> null;
        SolicitudCobroCaja solBase = new SolicitudCobroCaja(1, 10, " ", "FACTURA", "EFECTIVO",
                "k1", null, List.of());
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solBase)).getStatusCode());

        // 2. Pedido no encontrado (404), pedido terminal (409), pedido sin detalles (422)
        fx.nextConnection = fx::newConnection;
        fx.pedidoPorId = Optional.empty();
        assertEquals(404, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solBase)).getStatusCode());

        fx.pedidoPorId = Optional.of(new Pedido(10, 1, 1, "03", "PAGADO", new BigDecimal("20.00")));
        assertEquals(409, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solBase)).getStatusCode());

        fx.pedidoPorId = Optional.of(new Pedido(10, 1, 1, "03", null, new BigDecimal("20.00")));
        fx.detallesPedido = List.of();
        assertEquals(422, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solBase)).getStatusCode());

        // 3. Cobro exitoso de pedido con mesa heredada del pedido y FACTURA + fallo no bloqueante liberando mesa
        DetallePedido detPed = new DetallePedido(1, 10, 1, "Pollo", 2,
                new BigDecimal("20.00"), new BigDecimal("40.00"), "");
        fx.detallesPedido = List.of(detPed);
        fx.sqlErrorActualizarMesa = true;
        ResultadoCobroCaja resPed = fx.service.cobrarEnCaja(solBase);
        assertEquals("03", resPed.numeroMesa());
        assertEquals("F001-000042", resPed.numeroComprobante());
        assertFalse(resPed.mesaLiberada());

        // 4. Cobro directo inválido (inexistente / no disponible / precio null) y SQL preparando cobro
        fx.sqlErrorActualizarMesa = false;
        assertEquals(422, assertThrows(ApiOperacionException.class, () -> fx.service.cobrarEnCaja(
                new SolicitudCobroCaja(1, 0, "", "BOLETA", "TARJETA", "k2", null,
                        List.of(new ItemSolicitudPedido(99, 1, ""))))).getStatusCode());
        assertEquals(422, assertThrows(ApiOperacionException.class, () -> fx.service.cobrarEnCaja(
                new SolicitudCobroCaja(1, 0, "", "BOLETA", "TARJETA", "k3", null,
                        List.of(new ItemSolicitudPedido(2, 1, ""))))).getStatusCode());
        assertEquals(422, assertThrows(ApiOperacionException.class, () -> fx.service.cobrarEnCaja(
                new SolicitudCobroCaja(1, 0, "", "BOLETA", "TARJETA", "k4", null,
                        List.of(new ItemSolicitudPedido(3, 1, ""))))).getStatusCode());

        fx.sqlErrorEnProductoTodos = true;
        assertEquals(503, assertThrows(ApiOperacionException.class, () -> fx.service.cobrarEnCaja(
                new SolicitudCobroCaja(1, 0, "", "BOLETA", "TARJETA", "k5", null,
                        List.of(new ItemSolicitudPedido(1, 1, ""))))).getStatusCode());
        fx.sqlErrorEnProductoTodos = false;

        // 5. Efectivo insuficiente -> 422; BOLETA con mesa liberada
        SolicitudCobroCaja solInsuficiente = new SolicitudCobroCaja(1, 0, "05", "BOLETA", "EFECTIVO", "k6",
                new BigDecimal("10.00"), List.of(new ItemSolicitudPedido(1, 1, "")));
        assertEquals(422, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solInsuficiente)).getStatusCode());

        ResultadoCobroCaja resDirecto = fx.service.cobrarEnCaja(new SolicitudCobroCaja(
                1, 0, "05", "BOLETA", "EFECTIVO", "k7", new BigDecimal("50.00"),
                List.of(new ItemSolicitudPedido(1, 2, ""))));
        assertEquals("B001-000042", resDirecto.numeroComprobante());
        assertEquals(new BigDecimal("10.00"), resDirecto.vuelto());
        assertTrue(resDirecto.mesaLiberada());

        // 6. Excepciones al persistir venta en VentaService
        SolicitudCobroCaja solPersist = new SolicitudCobroCaja(1, 0, null, "TICKET", "TARJETA", "k8",
                null, List.of(new ItemSolicitudPedido(1, 1, "")));
        fx.errorRegistrarVenta = new CajaCerradaException();
        assertEquals(409, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solPersist)).getStatusCode());
        fx.errorRegistrarVenta = new CajaCerradaException(null);
        assertEquals(409, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solPersist)).getStatusCode());

        fx.errorRegistrarVenta = new StockInsuficienteException("Sin stock");
        assertEquals(422, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solPersist)).getStatusCode());
        fx.errorRegistrarVenta = new InsumoNoEncontradoException(7L);
        assertEquals(422, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solPersist)).getStatusCode());
        fx.errorRegistrarVenta = new NegocioException((String) null);
        assertEquals(422, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solPersist)).getStatusCode());
        fx.errorRegistrarVenta = new SQLException("Error venta");
        assertEquals(503, assertThrows(ApiOperacionException.class,
                () -> fx.service.cobrarEnCaja(solPersist)).getStatusCode());
    }

    private static Producto crearProducto(int id, String nombre, BigDecimal precio, boolean disponible) {
        return new Producto(id, 1, "SKU-" + id, nombre, "", precio, BigDecimal.ONE, disponible);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyVacio(Class<T> iface) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> valorPorDefecto(method.getReturnType())
        );
    }

    private static Object valorPorDefecto(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @FunctionalInterface
    private interface ConnSupplier {
        Connection get() throws SQLException;
    }

    private static final class Fixture {
        private ConnSupplier nextConnection = this::newConnection;
        private List<Producto> productosDisponibles = List.of();
        private List<Producto> productosTodos = List.of();
        private boolean sqlErrorEnProductoTodos;
        private Optional<String> imagenUrl = Optional.empty();
        private final AtomicInteger ultimoIdDisponibilidad = new AtomicInteger();
        private final AtomicBoolean ultimoEstadoDisponibilidad = new AtomicBoolean();

        private Map<String, String> estadosMesas = new LinkedHashMap<>();
        private Map<String, BigDecimal> totalesActivosPorMesa = Map.of();
        private boolean sqlErrorActualizarMesa;

        private Optional<Pedido> pedidoPorUuid = Optional.empty();
        private Optional<Pedido> pedidoPorId = Optional.empty();
        private List<DetallePedido> detallesPedido = List.of();
        private List<Pedido> pedidosActivos = List.of();
        private Map<Integer, List<DetallePedido>> detallesActivosAgrupados = Map.of();
        private final AtomicReference<String> ultimoEstadoPedidoActualizado = new AtomicReference<>();

        private Exception errorRegistrarPedido;
        private Optional<Caja> cajaActiva = Optional.empty();
        private Exception errorObtenerCajaActiva;
        private Exception errorTotalesCaja;
        private BigDecimal ventasTurno = BigDecimal.ZERO;
        private BigDecimal gastosTurno = BigDecimal.ZERO;
        private Exception errorAbrirCaja;
        private Exception errorCerrarCaja;
        private int siguienteCorrelativo = 1;
        private Exception errorRegistrarVenta;

        private final DefaultApiOperacionesService service;

        private Fixture() {
            ProductoDAO productoDAO = crearProductoDAO();
            MesaDAO mesaDAO = crearMesaDAO();
            PedidoDAO pedidoDAO = crearPedidoDAO();
            VentaDAO ventaDAO = crearVentaDAO();
            PedidoService pedidoService = crearPedidoService();
            CajaService cajaService = crearCajaService();
            VentaService ventaService = crearVentaService();
            this.service = new DefaultApiOperacionesService(
                    productoDAO, mesaDAO, pedidoDAO, ventaDAO,
                    pedidoService, cajaService, ventaService,
                    () -> nextConnection.get()
            );
        }

        private Connection newConnection() {
            return proxyVacio(Connection.class);
        }

        private ProductoDAO crearProductoDAO() {
            return (ProductoDAO) Proxy.newProxyInstance(
                    ProductoDAO.class.getClassLoader(),
                    new Class<?>[]{ProductoDAO.class},
                    (p, m, args) -> switch (m.getName()) {
                        case "listarProductosDisponibles" -> productosDisponibles;
                        case "listarTodos" -> {
                            if (sqlErrorEnProductoTodos) {
                                throw new SQLException("Error SQL listarTodos");
                            }
                            yield productosTodos;
                        }
                        case "obtenerImagenUrlPorId" -> imagenUrl;
                        case "cambiarDisponibilidad" -> {
                            ultimoIdDisponibilidad.set((Integer) args[1]);
                            ultimoEstadoDisponibilidad.set((Boolean) args[2]);
                            yield null;
                        }
                        default -> valorPorDefecto(m.getReturnType());
                    }
            );
        }

        private MesaDAO crearMesaDAO() {
            return (MesaDAO) Proxy.newProxyInstance(
                    MesaDAO.class.getClassLoader(),
                    new Class<?>[]{MesaDAO.class},
                    (p, m, args) -> switch (m.getName()) {
                        case "obtenerEstadoTodasMesas" -> estadosMesas;
                        case "actualizarEstadoMesaPorNumero" -> {
                            if (sqlErrorActualizarMesa) {
                                throw new SQLException("Error SQL mesa");
                            }
                            yield true;
                        }
                        default -> valorPorDefecto(m.getReturnType());
                    }
            );
        }

        private PedidoDAO crearPedidoDAO() {
            return (PedidoDAO) Proxy.newProxyInstance(
                    PedidoDAO.class.getClassLoader(),
                    new Class<?>[]{PedidoDAO.class},
                    (p, m, args) -> switch (m.getName()) {
                        case "obtenerTotalesPedidosActivosPorMesa" -> totalesActivosPorMesa;
                        case "buscarPorIdempotencyKey" -> pedidoPorUuid;
                        case "obtenerPorId" -> pedidoPorId;
                        case "listarDetallesPorPedido" -> detallesPedido;
                        case "listarPedidosActivos" -> pedidosActivos;
                        case "listarDetallesPedidosActivosAgrupados" -> detallesActivosAgrupados;
                        case "actualizarEstadoPedido" -> {
                            ultimoEstadoPedidoActualizado.set((String) args[2]);
                            yield null;
                        }
                        default -> valorPorDefecto(m.getReturnType());
                    }
            );
        }

        private VentaDAO crearVentaDAO() {
            return (VentaDAO) Proxy.newProxyInstance(
                    VentaDAO.class.getClassLoader(),
                    new Class<?>[]{VentaDAO.class},
                    (p, m, args) -> {
                        if ("obtenerSiguienteCorrelativoComprobante".equals(m.getName())) {
                            return siguienteCorrelativo;
                        }
                        return valorPorDefecto(m.getReturnType());
                    }
            );
        }

        private PedidoService crearPedidoService() {
            return (PedidoService) Proxy.newProxyInstance(
                    PedidoService.class.getClassLoader(),
                    new Class<?>[]{PedidoService.class},
                    (p, m, args) -> {
                        if ("registrarPedidoYEnviarComanda".equals(m.getName())) {
                            if (errorRegistrarPedido != null) {
                                throw errorRegistrarPedido;
                            }
                            Pedido input = (Pedido) args[0];
                            input.setIdPedido(101);
                            return input;
                        }
                        return valorPorDefecto(m.getReturnType());
                    }
            );
        }

        private CajaService crearCajaService() {
            return (CajaService) Proxy.newProxyInstance(
                    CajaService.class.getClassLoader(),
                    new Class<?>[]{CajaService.class},
                    (p, m, args) -> switch (m.getName()) {
                        case "obtenerCajaActiva" -> {
                            if (errorObtenerCajaActiva != null) {
                                throw errorObtenerCajaActiva;
                            }
                            yield cajaActiva;
                        }
                        case "calcularVentasTurno" -> {
                            if (errorTotalesCaja != null) {
                                throw errorTotalesCaja;
                            }
                            yield ventasTurno;
                        }
                        case "calcularGastosTurno" -> {
                            if (errorTotalesCaja != null) {
                                throw errorTotalesCaja;
                            }
                            yield gastosTurno;
                        }
                        case "abrirTurnoCaja" -> {
                            if (errorAbrirCaja != null) {
                                throw errorAbrirCaja;
                            }
                            yield 12;
                        }
                        case "cerrarTurnoCaja" -> {
                            if (errorCerrarCaja != null) {
                                throw errorCerrarCaja;
                            }
                            yield null;
                        }
                        default -> valorPorDefecto(m.getReturnType());
                    }
            );
        }

        private VentaService crearVentaService() {
            return (VentaService) Proxy.newProxyInstance(
                    VentaService.class.getClassLoader(),
                    new Class<?>[]{VentaService.class},
                    (p, m, args) -> {
                        if ("registrarVenta".equals(m.getName())) {
                            if (errorRegistrarVenta != null) {
                                throw errorRegistrarVenta;
                            }
                            Venta v = (Venta) args[0];
                            v.setIdVenta(505);
                            return v;
                        }
                        return valorPorDefecto(m.getReturnType());
                    }
            );
        }
    }
}
