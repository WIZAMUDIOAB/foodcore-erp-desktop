package com.foodcore.service.venta;

import com.foodcore.dao.ComboDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.model.Receta;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.model.dto.ResultadoDevolucionParcial;
import com.foodcore.model.dto.SubcuentaItems;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.VentaServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VentaKardexHandlerUnitTest {
    @Test
    void prepararYLockearInsumosVentaExpandeComboYOrdenaIdsAscendente() throws Exception {
        List<Long> idsBloqueados = new ArrayList<>();
        Combo combo = new Combo(1, 10, "Combo Duo", new BigDecimal("25.00"), true);
        combo.setDetalles(List.of(new ComboDetalle(20, "Papas", 2)));
        ComboDAO comboDAO = proxy(ComboDAO.class, (method, args) -> switch (method.getName()) {
            case "obtenerPorIdProducto" -> Optional.of(combo);
            case "obtenerComponentesParaVenta" -> List.of(new ComboDetalle(1, 1, 20, 2), new ComboDetalle(2, 1, 21, 1));
            default -> defaultValue(method.getReturnType());
        });
        InsumoDAO insumoDAO = proxy(InsumoDAO.class, (method, args) -> switch (method.getName()) {
            case "obtenerRecetaPorProducto" -> args[1].equals(20)
                    ? List.of(new Receta(1, 20, 9L, new BigDecimal("0.5000")))
                    : List.of(new Receta(2, 21, 3L, new BigDecimal("1.0000")));
            case "lockearPorIds" -> {
                idsBloqueados.addAll((List<Long>) args[1]);
                yield Map.of(3L, BigDecimal.TEN, 9L, BigDecimal.TEN);
            }
            default -> defaultValue(method.getReturnType());
        });
        DetalleVenta detalle = new DetalleVenta(0, 0, 10, 2, BigDecimal.TEN, BigDecimal.ZERO);

        VentaInventarioKardexHandler.PlanConsumoInsumos plan = handler(insumoDAO, comboDAO)
                .prepararYLockearInsumosVenta(null, List.of(detalle));

        assertEquals(List.of(3L, 9L), idsBloqueados);
        assertEquals(List.of(3L, 9L), plan.idsOrdenados());
        assertEquals(1, detalle.getIdCombo());
        assertTrue(detalle.getComboComponentesTexto() != null && !detalle.getComboComponentesTexto().isBlank());
        assertEquals(new BigDecimal("2.0000"), plan.requerimientosPorInsumo().get(9L));
        assertEquals(new BigDecimal("2.0000"), plan.requerimientosPorInsumo().get(3L));
    }

    @Test
    void descontarStockYRegistrarKardexFlujoFelizYExcepciones() throws Exception {
        AtomicReference<KardexMovimiento> movimiento = new AtomicReference<>();
        List<String> tiposMovimiento = new ArrayList<>();
        Insumo insumo = new Insumo(3L, "INS-3", "Papa", "KG", BigDecimal.TEN,
                BigDecimal.ONE, new BigDecimal("4.50"));
        InsumoDAO dao = proxy(InsumoDAO.class, (method, args) -> {
            if (method.getName().equals("descontarStock")) return null;
            if (method.getName().equals("obtenerPorId")) return insumo;
            if (method.getName().equals("registrarMovimientoInventario")) {
                tiposMovimiento.add((String) args[2]);
                return null;
            }
            if (method.getName().equals("insertarKardex")) {
                movimiento.set((KardexMovimiento) args[1]);
                return 1L;
            }
            return defaultValue(method.getReturnType());
        });
        DefaultVentaInventarioKardexHandler handler = handler(dao, emptyComboDAO());
        VentaInventarioKardexHandler.PlanConsumoInsumos plan = new VentaInventarioKardexHandler.PlanConsumoInsumos(
                Map.of(3L, new BigDecimal("2.0000")), List.of(3L), Map.of(3L, new BigDecimal("8.0000")));

        handler.descontarStockYRegistrarKardex(null, 77, 4L, plan);

        assertEquals(List.of("SALIDA"), tiposMovimiento);
        assertEquals(new BigDecimal("-2.0000"), movimiento.get().getCantidad());
        assertEquals("VENTA", movimiento.get().getReferenciaTipo());

        InsumoNoEncontradoException noEncontrado = assertThrows(InsumoNoEncontradoException.class,
                () -> handler.descontarStockYRegistrarKardex(null, 77, 4L,
                        new VentaInventarioKardexHandler.PlanConsumoInsumos(Map.of(5L, BigDecimal.ONE),
                                List.of(5L), Map.of())));
        assertEquals(5L, noEncontrado.getIdInsumo());

        StockInsuficienteException stock = new StockInsuficienteException(3L, BigDecimal.ONE);
        InsumoDAO daoConFallo = proxy(InsumoDAO.class, (method, args) -> {
            if (method.getName().equals("descontarStock")) throw stock;
            return defaultValue(method.getReturnType());
        });
        StockInsuficienteException propagada = assertThrows(StockInsuficienteException.class,
                () -> handler(daoConFallo, emptyComboDAO()).descontarStockYRegistrarKardex(null, 77, 4L, plan));
        assertSame(stock, propagada);
    }

    @Test
    void revertirOMermarInsumosPorAnulacionCubreEntradaYMerma() throws Exception {
        AtomicReference<String> kardexAtomicoTipo = new AtomicReference<>();
        InsumoDAO daoEntrada = proxy(InsumoDAO.class, (method, args) -> {
            if (method.getName().equals("registrarMovimientoKardexAtomico")) {
                kardexAtomicoTipo.set((String) args[2]);
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        handler(daoEntrada, emptyComboDAO()).revertirOMermarInsumosPorAnulacion(null, 8, false, 2L,
                "motivo", Map.of(3L, BigDecimal.ONE));
        assertEquals("ENTRADA", kardexAtomicoTipo.get());

        List<Long> bloqueados = new ArrayList<>();
        List<String> movimientos = new ArrayList<>();
        AtomicReference<KardexMovimiento> mermaKardex = new AtomicReference<>();
        Insumo insumo = new Insumo(3L, "INS-3", "Papa", "KG", BigDecimal.TEN,
                BigDecimal.ONE, new BigDecimal("4.50"));
        InsumoDAO daoMerma = proxy(InsumoDAO.class, (method, args) -> {
            switch (method.getName()) {
                case "lockearPorIds" -> {
                    bloqueados.addAll((List<Long>) args[1]);
                    return Map.of(3L, new BigDecimal("7.0000"));
                }
                case "obtenerPorId" -> { return insumo; }
                case "registrarMovimientoInventario" -> {
                    movimientos.add((String) args[2]);
                    return null;
                }
                case "insertarKardex" -> {
                    mermaKardex.set((KardexMovimiento) args[1]);
                    return 1L;
                }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
        handler(daoMerma, emptyComboDAO()).revertirOMermarInsumosPorAnulacion(null, 8, true, 2L,
                "motivo", Map.of(3L, BigDecimal.ONE));
        assertEquals(List.of(3L), bloqueados);
        assertEquals(List.of("MERMA"), movimientos);
        assertEquals("MERMA", mermaKardex.get().getTipoMovimiento());
    }

    @Test
    void revertirInsumosPorDevolucionParcialActualizaStockYRegistraKardex() throws Exception {
        List<Long> idsBloqueados = new ArrayList<>();
        AtomicReference<BigDecimal> stockActualizado = new AtomicReference<>();
        List<String> tipos = new ArrayList<>();
        AtomicReference<KardexMovimiento> movimiento = new AtomicReference<>();
        InsumoDAO dao = proxy(InsumoDAO.class, (method, args) -> {
            switch (method.getName()) {
                case "lockearPorIds" -> {
                    idsBloqueados.addAll((List<Long>) args[1]);
                    return Map.of(3L, new BigDecimal("5.0000"));
                }
                case "actualizarStock" -> {
                    stockActualizado.set((BigDecimal) args[2]);
                    return null;
                }
                case "obtenerPorId" -> {
                    return new Insumo(3L, "INS-3", "Papa", "KG", BigDecimal.TEN,
                            BigDecimal.ONE, new BigDecimal("4.50"));
                }
                case "registrarMovimientoInventario" -> {
                    tipos.add((String) args[2]);
                    return null;
                }
                case "insertarKardex" -> {
                    movimiento.set((KardexMovimiento) args[1]);
                    return 1L;
                }
                default -> { return defaultValue(method.getReturnType()); }
            }
        });
        handler(dao, emptyComboDAO()).revertirInsumosPorDevolucionParcial(null, 8, 2L,
                "devolución", Map.of(3L, new BigDecimal("1.2500")));
        assertEquals(List.of(3L), idsBloqueados);
        assertEquals(new BigDecimal("6.2500"), stockActualizado.get());
        assertEquals(List.of("ENTRADA"), tipos);
        assertEquals("DEVOLUCION", movimiento.get().getTipoMovimiento());
        assertEquals("DEVOLUCION", movimiento.get().getReferenciaTipo());
    }

    @Test
    void ventaServiceFacadeDipDelegaOperacionesPublicas() throws Exception {
        Venta venta = new Venta();
        ComprobantePago comprobante = new ComprobantePago();
        DetalleVenta detalle = new DetalleVenta();
        SesionUsuario sesion = null;
        AtomicReference<String> operacion = new AtomicReference<>();
        VentaDAO ventaDAO = proxy(VentaDAO.class, (method, args) -> defaultValue(method.getReturnType()));
        VentaCalculoValidator validator = new VentaCalculoValidator() {
            @Override
            public BigDecimal calcularDescuentoAutorizado(BigDecimal bruto, BigDecimal descuento, boolean porcentaje,
                                                            SesionUsuario usuario) {
                operacion.set("descuento");
                return new BigDecimal("7.00");
            }
            @Override
            public void validarYPrepararMontosVenta(Venta v, List<DetalleVenta> detalles,
                                                     List<PagoSplit> pagos) { }
            @Override
            public String normalizarMedioPagoMovimientoCaja(String medioPago) { return "CREDITO"; }
        };
        RegistrarVentaUseCase registrar = new RegistrarVentaUseCase() {
            @Override
            public Venta registrarVentaConPagos(Venta v, List<DetalleVenta> detalles, ComprobantePago c,
                                                 List<PagoSplit> pagos) {
                operacion.set("registrar"); return v;
            }
            @Override
            public Venta registrarVentaDividida(Venta v, List<DetalleVenta> detalles, ComprobantePago c,
                                                  List<PagoSplit> pagos) {
                operacion.set("dividida"); return v;
            }
            @Override
            public Venta registrarVentaConCreditoCorporativo(Venta v, List<DetalleVenta> detalles,
                    ComprobantePago c, String documento, String nombre) {
                operacion.set("credito"); return v;
            }
            @Override
            public List<Venta> dividirCuentaPorItems(List<SubcuentaItems> subcuentas, int idUsuario) {
                operacion.set("dividirItems"); return List.of(venta);
            }
            @Override
            public Venta registrarVentaEnConexion(Connection conn, Venta v, List<DetalleVenta> detalles,
                    ComprobantePago c, List<PagoSplit> pagos) { return v; }
        };
        AnularDevolverVentaUseCase anular = new AnularDevolverVentaUseCase() {
            @Override
            public void anularVenta(int id, boolean preparado, SesionUsuario usuario, String motivo) {
                operacion.set("anular");
            }
            @Override
            public Usuario anularVentaConSupervisor(int id, boolean preparado, SesionUsuario usuario,
                    String pin, String motivo) {
                operacion.set("supervisor"); return null;
            }
            @Override
            public ResultadoDevolucionParcial devolverItemsParcial(int id, List<Integer> ids,
                    SesionUsuario usuario, String motivo) {
                operacion.set("devolucion"); return null;
            }
        };
        VentaService service = new VentaServiceImpl(ventaDAO, validator, registrar, anular);

        assertSame(venta, service.procesarCobroVenta(venta, List.of(detalle), comprobante));
        assertEquals("registrar", operacion.get());
        assertEquals(new BigDecimal("7.00"), service.calcularDescuentoAutorizado(BigDecimal.TEN,
                BigDecimal.ONE, false, sesion));
        assertEquals("descuento", operacion.get());
        assertSame(venta, service.registrarVenta(venta, List.of(detalle), comprobante));
        assertEquals("registrar", operacion.get());
        assertSame(venta, service.registrarVentaConCreditoCorporativo(venta, List.of(detalle), comprobante,
                "doc", "cliente"));
        assertEquals("credito", operacion.get());
        assertSame(venta, service.registrarVentaDividida(venta, List.of(detalle), comprobante, List.of()));
        assertEquals("dividida", operacion.get());
        assertEquals(List.of(venta), service.dividirCuentaPorItems(List.of(), 2));
        assertEquals("dividirItems", operacion.get());
        service.anularVenta(1, false, sesion, "motivo");
        assertEquals("anular", operacion.get());
        service.anularVentaConSupervisor(1, false, sesion, "pin", "motivo");
        assertEquals("supervisor", operacion.get());
        service.devolverItemsParcial(1, List.of(2), sesion, "motivo");
        assertEquals("devolucion", operacion.get());
        assertEquals("CREDITO", VentaService.normalizarMedioPagoMovimientoCaja("CREDITO_CORPORATIVO"));
    }

    private static DefaultVentaInventarioKardexHandler handler(InsumoDAO insumoDAO, ComboDAO comboDAO) {
        return new DefaultVentaInventarioKardexHandler(insumoDAO, comboDAO);
    }

    private static ComboDAO emptyComboDAO() {
        return proxy(ComboDAO.class, (method, args) -> defaultValue(method.getReturnType()));
    }

    private static <T> T proxy(Class<T> type, Stub stub) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            return stub.invoke(method, args == null ? new Object[0] : args);
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, invocationHandler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    @FunctionalInterface
    private interface Stub {
        Object invoke(Method method, Object[] args) throws Throwable;
    }
}
