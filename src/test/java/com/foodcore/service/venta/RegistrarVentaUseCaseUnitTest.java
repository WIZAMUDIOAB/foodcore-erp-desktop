package com.foodcore.service.venta;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.model.Caja;
import com.foodcore.model.Cliente;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.VentaService;
import com.foodcore.tx.TransactionManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrarVentaUseCaseUnitTest {
    private static final class Estado {
        final Map<String, Integer> llamadas = new HashMap<>();
        final AtomicInteger nextVenta = new AtomicInteger(100);
        boolean cajaPresente = true;
        int cajaId = 1;
        Optional<Venta> existente = Optional.empty();
        boolean periodoCerrado;
        boolean clientePresente = true;
        boolean clienteActivo = true;
        BigDecimal limite = new BigDecimal("1000.00");
        BigDecimal deuda = BigDecimal.ZERO;
    }

    private RegistrarVentaUseCase caso(Estado e) {
        Connection conn = proxy(Connection.class, (p, m, a) -> {
            if (m.getName().equals("prepareStatement")) {
                String sql = (String) a[0];
                return statement(sql, e);
            }
            return def(m.getReturnType());
        });
        TransactionManager tx = proxy(TransactionManager.class, (p, m, a) -> {
            if (m.getName().equals("executeChecked")) return ((TransactionManager.CheckedWork<?>) a[0]).apply(conn);
            if (m.getName().equals("executeVoidChecked")) { ((TransactionManager.CheckedVoidWork) a[0]).accept(conn); return null; }
            return def(m.getReturnType());
        });
        VentaDAO venta = proxy(VentaDAO.class, (p, m, a) -> {
            inc(e, m.getName());
            return switch (m.getName()) {
                case "buscarPorIdempotencyKey" -> e.existente;
                case "insertarVenta" -> e.nextVenta.incrementAndGet();
                default -> def(m.getReturnType());
            };
        });
        CajaDAO caja = proxy(CajaDAO.class, (p, m, a) -> {
            inc(e, m.getName());
            if (m.getName().equals("obtenerCajaAbierta")) return e.cajaPresente
                    ? Optional.of(new Caja(e.cajaId, 2, "Caja Principal", new BigDecimal("200.00"), "ABIERTA"))
                    : Optional.empty();
            return def(m.getReturnType());
        });
        PedidoDAO pedido = proxy(PedidoDAO.class, (p, m, a) -> { inc(e, m.getName()); return def(m.getReturnType()); });
        ClienteDAO cliente = proxy(ClienteDAO.class, (p, m, a) -> {
            if (m.getName().equals("buscarPorId") && e.clientePresente) return Optional.of(new Cliente());
            return Optional.empty();
        });
        CuentaPorCobrarDAO cxc = proxy(CuentaPorCobrarDAO.class, (p, m, a) -> { inc(e, m.getName()); return def(m.getReturnType()); });
        CierreContableService cierre = new CierreContableService(null, null, null) {
            @Override public void validarPeriodoAbierto(Connection c, LocalDate f) throws PeriodoCerradoException {
                if (e.periodoCerrado) throw new PeriodoCerradoException(2026, 11);
            }
        };
        FidelizacionService fidelizacion = new FidelizacionService(null, null) {
            @Override public int acumularPuntosPorVenta(Connection c, int clienteId, Integer ventaId, BigDecimal total) {
                inc(e, "acumularPuntosPorVentaEnTx");
                return 0;
            }
        };
        VentaCalculoValidator calculo = proxy(VentaCalculoValidator.class, (p, m, a) -> {
            inc(e, m.getName());
            if (m.getName().equals("normalizarMedioPagoMovimientoCaja")) return a[0];
            return def(m.getReturnType());
        });
        VentaInventarioKardexHandler kardex = proxy(VentaInventarioKardexHandler.class, (p, m, a) -> {
            inc(e, m.getName());
            if (m.getName().equals("prepararYLockearInsumosVenta")) return null;
            return def(m.getReturnType());
        });
        return new DefaultRegistrarVentaUseCase(tx, venta, caja, pedido, cliente, cxc, cierre, fidelizacion, calculo, kardex);
    }

    private Object statement(String sql, Estado e) {
        return proxy(PreparedStatement.class, (p, m, a) -> {
            if (m.getName().equals("executeQuery")) return proxy(ResultSet.class, (rp, rm, ra) -> {
                if (rm.getName().equals("next")) return sql.contains("FROM cliente WHERE id_cliente") ? e.clientePresente : true;
                if (rm.getName().equals("getBoolean")) return e.clienteActivo;
                if (rm.getName().equals("getBigDecimal")) return sql.contains("FROM cuenta_por_cobrar") ? e.deuda : e.limite;
                if (rm.getName().equals("getInt")) return 30;
                return def(rm.getReturnType());
            });
            return def(m.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }
    private static Object def(Class<?> t) {
        if (!t.isPrimitive() || t == void.class) return null;
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == double.class) return 0D;
        if (t == float.class) return 0F;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return '\0';
        return null;
    }
    private static void inc(Estado e, String k) { e.llamadas.merge(k, 1, Integer::sum); }
    private Venta venta() { return new Venta(0, 10, 1, 2, null, new BigDecimal("84.75"), new BigDecimal("15.25"), new BigDecimal("100.00"), "EFECTIVO", new BigDecimal("100.00"), BigDecimal.ZERO); }
    private List<DetalleVenta> detalles() { return new ArrayList<>(List.of(new DetalleVenta(0, 0, 5, 2, new BigDecimal("50.00"), new BigDecimal("100.00")))); }

    @Test void registrarVentaIdempotenteRetornaVentaExistenteSinReinsertar() throws Exception {
        Estado e = new Estado(); Venta v = venta(); v.setIdempotencyKey("idem-1"); Venta existente = venta(); existente.setIdVenta(9); e.existente = Optional.of(existente);
        assertSame(existente, caso(e).registrarVentaEnConexion(null, v, detalles(), null, null));
        assertEquals(0, e.llamadas.getOrDefault("insertarVenta", 0));
    }

    @Test void registrarVentaFallaPorPeriodoCerradoOCajaCerrada() {
        Estado cerrado = new Estado(); cerrado.periodoCerrado = true;
        assertThrows(PeriodoCerradoException.class, () -> caso(cerrado).registrarVentaEnConexion(null, venta(), detalles(), null, null));
        Estado cajaCerrada = new Estado(); cajaCerrada.cajaPresente = false;
        assertThrows(CajaCerradaException.class, () -> caso(cajaCerrada).registrarVentaEnConexion(null, venta(), detalles(), null, null));
    }

    @Test void registrarVentaFlujoFelizConPedidoComprobanteYPuntos() throws Exception {
        Estado e = new Estado(); Venta v = venta(); v.setIdCliente(5); v.setPuntosACanjear(0);
        Venta r = caso(e).registrarVentaConPagos(v, detalles(), new ComprobantePago(0, 0, "BOLETA", "B001", "0000123", new BigDecimal("100.00")), null);
        assertNotNull(r);
        for (String s : List.of("validarYPrepararMontosVenta", "prepararYLockearInsumosVenta", "insertarVenta", "insertarDetalleVenta", "insertarComprobante", "registrarMovimiento", "actualizarEstadoPedido", "acumularPuntosPorVentaEnTx", "descontarStockYRegistrarKardex")) assertTrue(e.llamadas.getOrDefault(s, 0) > 0, s);
    }

    @Test void registrarVentaDivididaYDividirCuentaPorItemsValidacionesYExito() throws Exception {
        Estado e = new Estado(); RegistrarVentaUseCase u = caso(e); Venta v = venta(); List<DetalleVenta> d = detalles();
        assertThrows(NegocioException.class, () -> u.registrarVentaDividida(v, d, null, List.of()));
        u.registrarVentaDividida(venta(), detalles(), null, List.of(new VentaService.PagoSplit("EFECTIVO", new BigDecimal("50.00")), new VentaService.PagoSplit("YAPE", new BigDecimal("50.00"))));
        assertEquals(1, e.llamadas.get("insertarVenta"));
        assertEquals(2, e.llamadas.get("registrarMovimiento"));
        assertThrows(NegocioException.class, () -> u.dividirCuentaPorItems(List.of(), 2));
        List<Venta> ventas = u.dividirCuentaPorItems(List.of(new VentaService.SubcuentaItems(detalles(), "EFECTIVO"), new VentaService.SubcuentaItems(detalles(), "YAPE")), 2);
        assertEquals(2, ventas.size());
    }

    @Test void registrarVentaCreditoCorporativoValidacionesYExito() throws Exception {
        Estado e = new Estado(); RegistrarVentaUseCase u = caso(e); Venta sinCliente = venta(); sinCliente.setIdCliente(0);
        assertThrows(NegocioException.class, () -> u.registrarVentaConCreditoCorporativo(sinCliente, detalles(), null, null, null));
        e.clientePresente = false; Venta ausente = venta(); ausente.setIdCliente(5);
        assertThrows(NegocioException.class, () -> u.registrarVentaConCreditoCorporativo(ausente, detalles(), null, null, null));
        e.clientePresente = true; e.deuda = new BigDecimal("950.00"); Venta excedida = venta(); excedida.setIdCliente(5);
        assertThrows(NegocioException.class, () -> u.registrarVentaConCreditoCorporativo(excedida, detalles(), null, null, null));
        e.deuda = BigDecimal.ZERO; Venta ok = venta(); ok.setIdCliente(5);
        Venta result = u.registrarVentaConCreditoCorporativo(ok, detalles(), null, null, "Empresa");
        assertNotNull(result);
        assertEquals(1, e.llamadas.getOrDefault("insertar", 0));
        assertTrue(e.llamadas.getOrDefault("insertarVenta", 0) > 0);
    }
}
