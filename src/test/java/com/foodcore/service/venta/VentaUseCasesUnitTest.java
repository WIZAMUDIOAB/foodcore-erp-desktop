package com.foodcore.service.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.FidelizacionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VentaUseCasesUnitTest {
    private static final SesionUsuario SESION = new SesionUsuario(1L, "Cajero", "ADMIN",
            Set.of(Permiso.VENTA_DESCUENTO, Permiso.VENTA_ANULAR), "TEST", LocalDateTime.now());

    @Test
    void calcularDescuentoAutorizadoPorcentajeYFijo() throws NegocioException {
        DefaultVentaCalculoValidator validator = validator();
        assertEquals(new BigDecimal("10.00"), validator.calcularDescuentoAutorizado(
                new BigDecimal("100"), new BigDecimal("10"), true, SESION));
        assertEquals(new BigDecimal("5.00"), validator.calcularDescuentoAutorizado(
                new BigDecimal("100"), new BigDecimal("5"), false, SESION));
    }

    @Test
    void calcularDescuentoAutorizadoRechazaEntradasInvalidas() {
        DefaultVentaCalculoValidator validator = validator();
        assertThrows(NegocioException.class, () -> validator.calcularDescuentoAutorizado(
                BigDecimal.ZERO, BigDecimal.ONE, true, SESION));
        assertThrows(NegocioException.class, () -> validator.calcularDescuentoAutorizado(
                BigDecimal.TEN, BigDecimal.ZERO, true, SESION));
        assertThrows(NegocioException.class, () -> validator.calcularDescuentoAutorizado(
                BigDecimal.TEN, new BigDecimal("100"), true, SESION));
        assertThrows(NegocioException.class, () -> validator.calcularDescuentoAutorizado(
                BigDecimal.TEN, BigDecimal.TEN, false, SESION));
        assertThrows(NegocioException.class, () -> validator.calcularDescuentoAutorizado(
                BigDecimal.TEN, BigDecimal.ONE, true,
                new SesionUsuario(2, "Sin permiso", "MOZO", Set.of(), "TEST", LocalDateTime.now())));
    }

    @Test
    void validarMontosVentaYNormalizarMedioPago() throws NegocioException {
        DefaultVentaCalculoValidator validator = validator();
        Venta venta = new Venta(0, null, 1, 1, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "EFECTIVO", BigDecimal.ZERO, BigDecimal.ZERO);
        DetalleVenta detalle = new DetalleVenta(0, 0, 1, 2, new BigDecimal("10"), null);
        validator.validarYPrepararMontosVenta(venta, List.of(detalle),
                List.of(new PagoSplit("EFECTIVO", new BigDecimal("20.00"))));
        assertEquals(new BigDecimal("20.00"), venta.getTotal());
        assertEquals(new BigDecimal("20.00"), detalle.getSubtotal());
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(null, List.of(detalle), null));
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(venta, List.of(), null));
        assertEquals("EFECTIVO", validator.normalizarMedioPagoMovimientoCaja(null));
        assertEquals("EFECTIVO", validator.normalizarMedioPagoMovimientoCaja("  "));
        assertEquals("CREDITO", validator.normalizarMedioPagoMovimientoCaja("CREDITO_CORPORATIVO"));
        assertEquals("123456789012345", validator.normalizarMedioPagoMovimientoCaja("123456789012345678"));
    }

    @Test
    void validarMontosVentaRechazaValoresInvalidos() {
        DefaultVentaCalculoValidator validator = validator();
        Venta base = venta();
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(base,
                List.of(new DetalleVenta(0, 0, 1, 0, BigDecimal.TEN, null)), null));
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(venta(),
                List.of(new DetalleVenta(0, 0, 1, 1, BigDecimal.ZERO, null)), null));
        assertThrows(NegocioException.class, () -> {
            Venta v = venta(); v.setDescuento(new BigDecimal("-1"));
            validator.validarYPrepararMontosVenta(v, List.of(detalle()), null);
        });
        assertThrows(NegocioException.class, () -> {
            Venta v = venta(); v.setDescuento(new BigDecimal("20"));
            validator.validarYPrepararMontosVenta(v, List.of(detalle()), null);
        });
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(null, List.of(detalle()), null));
        Venta clampNegativo = venta(); clampNegativo.setPuntosACanjear(-1);
        assertEquals(0, clampNegativo.getPuntosACanjear());
        assertThrows(NegocioException.class, () -> {
            Venta v = venta(); v.setDescuentoPuntos(BigDecimal.ONE);
            validator.validarYPrepararMontosVenta(v, List.of(detalle()), null);
        });
        assertThrows(NegocioException.class, () -> {
            Venta v = venta(); v.setPuntosACanjear(20);
            validator.validarYPrepararMontosVenta(v, List.of(detalle()), null);
        });
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(venta(),
                List.of(detalle()), List.of(new PagoSplit("EFECTIVO", BigDecimal.ZERO))));
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(venta(),
                List.of(detalle()), List.of(new PagoSplit("EFECTIVO", BigDecimal.ONE))));
    }

    @Test
    void validarMontosCanjeValidoYTotalFinalNoPositivo() throws NegocioException {
        DefaultVentaCalculoValidator validator = validator();
        Venta canje = venta();
        canje.setIdCliente(5);
        canje.setAplicarFidelizacion(true);
        canje.setPuntosACanjear(20);
        validator.validarYPrepararMontosVenta(canje, List.of(detalle()), null);
        assertEquals(new BigDecimal("1.00"), canje.getDescuentoPuntos());
        Venta anuladoPorDescuento = venta();
        anuladoPorDescuento.setDescuento(new BigDecimal("9.00"));
        anuladoPorDescuento.setPuntosACanjear(20);
        anuladoPorDescuento.setIdCliente(5);
        anuladoPorDescuento.setAplicarFidelizacion(true);
        assertThrows(NegocioException.class, () -> validator.validarYPrepararMontosVenta(
                anuladoPorDescuento, List.of(detalle()), null));
    }

    @Test
    void handlerTrazabilidadToleranteAEntradaVacia() throws Exception {
        DefaultVentaInventarioKardexHandler handler = new DefaultVentaInventarioKardexHandler(
                stubProxy(com.foodcore.dao.InsumoDAO.class, (proxy, method, args) -> defaultValue(method.getReturnType())),
                stubProxy(com.foodcore.dao.ComboDAO.class, (proxy, method, args) -> defaultValue(method.getReturnType())));
        handler.completarTrazabilidadComboDetalle(null, null);
        handler.completarTrazabilidadComboDetalle(null, new DetalleVenta());
    }

    private static <T> T stubProxy(Class<T> iface, InvocationHandler handler) {
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler));
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

    private static DefaultVentaCalculoValidator validator() {
        return new DefaultVentaCalculoValidator(new AutorizacionService() {
            @Override
            public void exigir(SesionUsuario sesion, Permiso permiso) throws NegocioException {
                if (sesion == null || !sesion.tienePermiso(permiso)) {
                    throw new NegocioException("Permiso denegado");
                }
            }
        }, new FidelizacionService(null, null) {
            @Override
            public BigDecimal calcularDescuentoExactoParaCanjeVenta(int puntos, BigDecimal total) {
                return new BigDecimal("1.00");
            }
        });
    }

    private static Venta venta() {
        return new Venta(0, null, 1, 1, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "EFECTIVO", BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static DetalleVenta detalle() {
        return new DetalleVenta(0, 0, 1, 1, BigDecimal.TEN, null);
    }
}
