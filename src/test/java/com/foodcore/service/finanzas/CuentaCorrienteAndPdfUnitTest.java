package com.foodcore.service.finanzas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.model.Caja;
import com.foodcore.model.Cliente;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.model.Insumo;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.Proveedor;
import com.foodcore.model.Venta;
import com.foodcore.service.CierreContableService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CuentaCorrienteAndPdfUnitTest {
    @TempDir
    Path tempDir;

    @Test
    void registrarPagoProveedorCubreErroresAbortYMovCajaEfectivo() throws Exception {
        CuentaPorPagarDAO ausente = cuentaPagar(Optional.empty(), true);
        assertThrows(IllegalArgumentException.class, () -> cuentas(ausente, null, null, null, servicioCierre(false))
                .registrarPagoProveedor(1, new BigDecimal("10.00"), "EFECTIVO", 1, 2, null));

        CuentaPorPagar saldo = cuentaPagar("100.00", "20.00");
        assertThrows(IllegalArgumentException.class, () -> cuentas(cuentaPagar(Optional.of(saldo), true), null, null, null, servicioCierre(false))
                .registrarPagoProveedor(1, new BigDecimal("81.00"), "EFECTIVO", 1, 2, null));
        assertThrows(IllegalStateException.class, () -> cuentas(null, null, null, null, servicioCierre(true))
                .registrarPagoProveedor(1, new BigDecimal("10.00"), "EFECTIVO", 1, 2, null));

        assertFalse(cuentas(cuentaPagar(Optional.of(saldo), false), null, null, null, servicioCierre(false))
                .registrarPagoProveedor(1, new BigDecimal("10.00"), "EFECTIVO", 1, 2, null));
        AtomicReference<MovimientoCaja> movimiento = new AtomicReference<>();
        DefaultCuentaCorrienteUseCase efectivo = cuentas(cuentaPagar(Optional.of(saldo), true), null, cajaDAO(movimiento), null, servicioCierre(false));
        assertTrue(efectivo.registrarPagoProveedor(1, new BigDecimal("10.00"), "EFECTIVO", 1, 2, null));
        assertEquals("EGRESO", movimiento.get().getTipoMovimiento());

        movimiento.set(null);
        assertTrue(cuentas(cuentaPagar(Optional.of(saldo), true), null, cajaDAO(movimiento), null, servicioCierre(false))
                .registrarPagoProveedor(1, new BigDecimal("10.00"), "TRANSFERENCIA", 1, 2, null));
        assertEquals(null, movimiento.get());
    }

    @Test
    void registrarCobroClienteCubreErroresAbortYMovCajaEfectivo() throws Exception {
        CuentaPorCobrar cxc = cuentaCobrar("100.00", "20.00");
        assertThrows(IllegalArgumentException.class, () -> cuentas(null, cuentaCobrarDAO(Optional.empty(), true), null, null, servicioCierre(false))
                .registrarCobroCliente(1, new BigDecimal("10.00"), "EFECTIVO", 1, 2, null));
        assertThrows(IllegalArgumentException.class, () -> cuentas(null, cuentaCobrarDAO(Optional.of(cxc), true), null, null, servicioCierre(false))
                .registrarCobroCliente(1, new BigDecimal("81.00"), "EFECTIVO", 1, 2, null));
        assertFalse(cuentas(null, cuentaCobrarDAO(Optional.of(cxc), false), null, null, servicioCierre(false))
                .registrarCobroCliente(1, new BigDecimal("10.00"), "EFECTIVO", 1, 2, null));

        AtomicReference<MovimientoCaja> movimiento = new AtomicReference<>();
        assertTrue(cuentas(null, cuentaCobrarDAO(Optional.of(cxc), true), cajaDAO(movimiento), null, servicioCierre(false))
                .registrarCobroCliente(1, new BigDecimal("10.00"), "EFECTIVO", 1, 2, null));
        assertEquals("INGRESO", movimiento.get().getTipoMovimiento());
    }

    @Test
    void registrarVentaCreditoCorporativoValidaLimiteYRegistraCuentaPorCobrar() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Cliente corporativo");
        cliente.setLimiteCredito(new BigDecimal("100.00"));
        cliente.setSaldoDeudor(new BigDecimal("80.00"));
        assertThrows(IllegalArgumentException.class, () -> ventaCredito(clienteDAO(Optional.of(cliente)), ventaDAO(), cuentaCobrarInsercion(new AtomicReference<>()), resultSet(false))
                .registrarVentaCreditoCorporativo(venta("20.00"), 7, 30));
        assertThrows(IllegalStateException.class, () -> ventaCredito(clienteDAO(Optional.of(cliente)), ventaDAO(), cuentaCobrarInsercion(new AtomicReference<>()), resultSet(true))
                .registrarVentaCreditoCorporativo(venta("21.00"), 7, 30));

        AtomicReference<CuentaPorCobrar> insertada = new AtomicReference<>();
        VentaDAO ventas = ventaDAO();
        Venta venta = venta("20.00");
        int id = ventaCredito(clienteDAO(Optional.of(cliente)), ventas, cuentaCobrarInsercion(insertada), resultSet(true))
                .registrarVentaCreditoCorporativo(venta, 7, 30);
        assertEquals(41, id);
        assertEquals("CREDITO", venta.getMetodoPago());
        assertNotNull(insertada.get());
        assertEquals("CR-000041", insertada.get().getNumeroComprobante());
        assertEquals("PENDIENTE", insertada.get().getEstado());
    }

    @Test
    void registrarCambioPrecioConMotivoActualizaProductoEInsumo() throws Exception {
        List<String> sqls = new ArrayList<>();
        AtomicInteger historial = new AtomicInteger();
        HistorialPrecioDAO dao = historialPrecioDAO(historial);
        DefaultCambioPrecioUseCase useCase = new DefaultCambioPrecioUseCase(transacciones(conexion(sqls, null)), dao);
        assertTrue(useCase.registrarCambioPrecioConMotivo("PRODUCTO", 3, "Producto", new BigDecimal("2.0000"), new BigDecimal("4.0000"), 1, "Usuario", "Ajuste costo"));
        assertEquals(1, historial.get());
        assertTrue(sqls.contains("UPDATE producto SET precio_venta = ? WHERE id_producto = ?"));
        assertTrue(useCase.registrarCambioPrecioConMotivo("INSUMO", 5, "Insumo", new BigDecimal("2.0000"), new BigDecimal("4.0000"), 1, "Usuario", "Ajuste costo"));
        assertEquals(2, historial.get());
        assertTrue(sqls.contains("UPDATE insumo SET costo_unitario = ?, costo_unitario_actual = ? WHERE id_insumo = ?"));
    }

    @Test
    void finanzasDocumentoPdfGeneratorGeneraAmbosPdfsRealesEnTempDir() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Cliente PDF");
        CuentaPorCobrar cxc = new CuentaPorCobrar();
        cxc.setNumeroComprobante("CR-000001");
        cxc.setMontoTotal(new BigDecimal("100.00"));
        cxc.setMontoCobrado(new BigDecimal("25.00"));
        DefaultFinanzasDocumentoPdfGenerator generator = new DefaultFinanzasDocumentoPdfGenerator(
                proveedorDAO(), clienteDAO(Optional.of(cliente)), cuentaCobrarListado(List.of(cxc)), () -> conexion(new ArrayList<>(), null));
        Insumo insumo = new Insumo(1, "INS-1", "Harina", "KG", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE);
        Path orden = tempDir.resolve("orden.pdf");
        Path estado = tempDir.resolve("estado.pdf");
        generator.generarOrdenCompraPdf(1, List.of(insumo), orden.toFile());
        generator.generarEstadoCuentaClientePdf(1, estado.toFile());
        assertPdf(orden);
        assertPdf(estado);
    }

    private static void assertPdf(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        assertTrue(bytes.length > 0);
        assertTrue(new String(bytes, 0, Math.min(bytes.length, 5), java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
    }

    private static Venta venta(String total) {
        Venta venta = new Venta();
        venta.setTotal(new BigDecimal(total));
        return venta;
    }

    private static CuentaPorPagar cuentaPagar(String total, String pagado) {
        CuentaPorPagar cuenta = new CuentaPorPagar();
        cuenta.setMontoTotal(new BigDecimal(total));
        cuenta.setMontoPagado(new BigDecimal(pagado));
        cuenta.setRazonSocialProveedor("Proveedor");
        cuenta.setNumeroDocumento("F001-1");
        return cuenta;
    }

    private static CuentaPorCobrar cuentaCobrar(String total, String cobrado) {
        CuentaPorCobrar cuenta = new CuentaPorCobrar();
        cuenta.setMontoTotal(new BigDecimal(total));
        cuenta.setMontoCobrado(new BigDecimal(cobrado));
        cuenta.setRazonSocialCliente("Cliente");
        cuenta.setNumeroComprobante("CR-1");
        return cuenta;
    }

    private static DefaultCuentaCorrienteUseCase cuentas(CuentaPorPagarDAO pagar, CuentaPorCobrarDAO cobrar,
            CajaDAO caja, VentaDAO venta, CierreContableService cierre) {
        return new DefaultCuentaCorrienteUseCase(transacciones(conexion(new ArrayList<>(), null)), pagar, cobrar,
                null, caja, venta, cierre);
    }

    private static DefaultCuentaCorrienteUseCase ventaCredito(ClienteDAO cliente, VentaDAO venta,
            CuentaPorCobrarDAO cobrar, ResultSet rs) {
        return new DefaultCuentaCorrienteUseCase(transacciones(conexion(new ArrayList<>(), rs)), null, cobrar,
                cliente, null, venta, servicioCierre(false));
    }

    private static CierreContableService servicioCierre(boolean cerrado) {
        return new CierreContableService(null, null, null) {
            @Override
            public void validarPeriodoAbierto(Connection c, LocalDate f) throws PeriodoCerradoException {
                if (cerrado) {
                    throw new PeriodoCerradoException(f.getYear(), f.getMonthValue());
                }
            }
        };
    }

    private static TransactionManager transacciones(Connection conn) {
        return new TransactionManager() {
            @Override public <T> T execute(Function<Connection, T> work) { return work.apply(conn); }
            @Override public void executeVoid(Consumer<Connection> work) { work.accept(conn); }
            @Override public <T> T executeChecked(CheckedWork<T> work) throws NegocioException, SQLException { return work.apply(conn); }
            @Override public void executeVoidChecked(CheckedVoidWork work) throws NegocioException, SQLException { work.accept(conn); }
        };
    }

    private static Connection conexion(List<String> sqls, ResultSet rs) {
        return proxy(Connection.class, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                String sql = (String) args[0];
                sqls.add(sql);
                return proxy(PreparedStatement.class, (p, m, a) -> {
                    if (m.getName().equals("executeQuery")) return rs;
                    if (m.getName().equals("executeUpdate")) return 1;
                    return defaultValue(m.getReturnType());
                });
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(boolean next) {
        AtomicBoolean first = new AtomicBoolean(true);
        return proxy(ResultSet.class, (p, method, args) -> method.getName().equals("next") && first.getAndSet(false) && next);
    }

    private static CajaDAO cajaDAO(AtomicReference<MovimientoCaja> movimiento) {
        return proxy(CajaDAO.class, (p, method, args) -> {
            if (method.getName().equals("obtenerCajaAbierta")) {
                Caja caja = new Caja();
                caja.setIdCaja(1);
                return Optional.of(caja);
            }
            if (method.getName().equals("registrarMovimiento")) {
                movimiento.set((MovimientoCaja) args[1]);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static CuentaPorPagarDAO cuentaPagar(Optional<CuentaPorPagar> cuenta, boolean resultado) {
        return proxy(CuentaPorPagarDAO.class, (p, method, args) -> {
            if (method.getName().equals("buscarPorIdParaActualizar")) return cuenta;
            if (method.getName().equals("registrarPago")) return resultado;
            return defaultValue(method.getReturnType());
        });
    }

    private static CuentaPorCobrarDAO cuentaCobrarDAO(Optional<CuentaPorCobrar> cuenta, boolean resultado) {
        return proxy(CuentaPorCobrarDAO.class, (p, method, args) -> {
            if (method.getName().equals("buscarPorIdParaActualizar")) return cuenta;
            if (method.getName().equals("registrarCobro")) return resultado;
            return defaultValue(method.getReturnType());
        });
    }

    private static CuentaPorCobrarDAO cuentaCobrarInsercion(AtomicReference<CuentaPorCobrar> guardada) {
        return proxy(CuentaPorCobrarDAO.class, (p, method, args) -> {
            if (method.getName().equals("insertar")) guardada.set((CuentaPorCobrar) args[1]);
            return defaultValue(method.getReturnType());
        });
    }

    private static CuentaPorCobrarDAO cuentaCobrarListado(List<CuentaPorCobrar> cuentas) {
        return proxy(CuentaPorCobrarDAO.class, (p, method, args) -> method.getName().equals("listarPorCliente") ? cuentas : defaultValue(method.getReturnType()));
    }

    private static ClienteDAO clienteDAO(Optional<Cliente> cliente) {
        return proxy(ClienteDAO.class, (p, method, args) -> method.getName().equals("buscarPorId") ? cliente : defaultValue(method.getReturnType()));
    }

    private static VentaDAO ventaDAO() {
        return proxy(VentaDAO.class, (p, method, args) -> method.getName().equals("insertarVenta") ? 41 : defaultValue(method.getReturnType()));
    }

    private static HistorialPrecioDAO historialPrecioDAO(AtomicInteger calls) {
        return proxy(HistorialPrecioDAO.class, (p, method, args) -> {
            if (method.getName().equals("insertar")) calls.incrementAndGet();
            return defaultValue(method.getReturnType());
        });
    }

    private static ProveedorDAO proveedorDAO() {
        return proxy(ProveedorDAO.class, (p, method, args) -> method.getName().equals("buscarPorId") ? Optional.of(new Proveedor()) : defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
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
}
