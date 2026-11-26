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
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.model.Caja;
import com.foodcore.model.Cliente;
import com.foodcore.model.CobroCliente;
import com.foodcore.model.Compra;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.model.FilaPlanilla;
import com.foodcore.model.HistorialPrecio;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.PagoProveedor;
import com.foodcore.model.Proveedor;
import com.foodcore.model.ResultadoReporteAvanzado;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasService;
import com.foodcore.service.ReporteService;
import com.foodcore.service.UsuarioGestionService;
import com.foodcore.tx.TransactionManager;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrecisionMonetariaBigDecimalTest {
    @TempDir
    Path tempDir;

    @Test
    void modelosCuentasCorrientesClientesProveedoresSinDerivaIeee754() {
        BigDecimal total = new BigDecimal("0.10").add(new BigDecimal("0.20"));
        assertEquals(0, new BigDecimal("0.30").compareTo(total));
        assertEquals("0.30", total.toPlainString());

        CuentaPorCobrar cxc = new CuentaPorCobrar();
        assertEquals(new BigDecimal("0.00"), cxc.getMontoTotal());
        assertEquals(new BigDecimal("0.00"), cxc.getMontoCobrado());
        cxc.setMontoTotal(total);
        cxc.setMontoCobrado(new BigDecimal("0.10"));
        assertEquals(new BigDecimal("0.20"), cxc.getSaldo());
        cxc.setMontoCobrado(new BigDecimal("0.30"));
        assertEquals(new BigDecimal("0.00"), cxc.getSaldo());

        CuentaPorPagar cxp = new CuentaPorPagar();
        cxp.setMontoTotal(new BigDecimal("100.10"));
        cxp.setMontoPagado(new BigDecimal("33.35").add(new BigDecimal("66.75")));
        assertEquals(new BigDecimal("0.00"), cxp.getSaldo());

        CobroCliente cobro = new CobroCliente();
        cobro.setMonto(new BigDecimal("12.345"));
        assertEquals(new BigDecimal("12.35"), cobro.getMonto());

        PagoProveedor pago = new PagoProveedor();
        pago.setMonto(new BigDecimal("12.344"));
        assertEquals(new BigDecimal("12.34"), pago.getMonto());

        Cliente cliente = new Cliente();
        cliente.setLimiteCredito(new BigDecimal("500.005"));
        cliente.setSaldoDeudor(new BigDecimal("0.10").add(new BigDecimal("0.20")));
        assertEquals(new BigDecimal("500.01"), cliente.getLimiteCredito());
        assertEquals(new BigDecimal("0.30"), cliente.getSaldoDeudor());

        Proveedor proveedor = new Proveedor();
        proveedor.setDeudaActual(new BigDecimal("199.995"));
        assertEquals(new BigDecimal("200.00"), proveedor.getDeudaActual());
    }

    @Test
    void cuentaCorrienteUseCaseValidaCentavoACentavoYPropagaBigDecimal() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Corporativo Exacto SAC");
        cliente.setLimiteCredito(new BigDecimal("100.30"));
        cliente.setSaldoDeudor(new BigDecimal("99.70")
                .add(new BigDecimal("0.10"))
                .add(new BigDecimal("0.20")));

        Venta ventaLimite = new Venta();
        ventaLimite.setTotal(new BigDecimal("0.30"));
        AtomicReference<CuentaPorCobrar> cxcInsertada = new AtomicReference<>();
        DefaultCuentaCorrienteUseCase useCaseCredito = new DefaultCuentaCorrienteUseCase(
                txManager(conexionCapturandoSetBigDecimal(new ArrayList<>(), resultSet(true))),
                null,
                cuentaCobrarInsercion(cxcInsertada),
                clienteDao(Optional.of(cliente)),
                null,
                ventaDao(),
                cierreAbierto());

        int idVenta = useCaseCredito.registrarVentaCreditoCorporativo(ventaLimite, 10, 30);
        assertEquals(41, idVenta);
        assertNotNull(cxcInsertada.get());
        assertEquals(new BigDecimal("0.30"), cxcInsertada.get().getMontoTotal());

        Venta ventaExcesoCentavo = new Venta();
        ventaExcesoCentavo.setTotal(new BigDecimal("0.31"));
        DefaultCuentaCorrienteUseCase useCaseCreditoExceso = new DefaultCuentaCorrienteUseCase(
                txManager(conexionCapturandoSetBigDecimal(new ArrayList<>(), resultSet(true))),
                null,
                cuentaCobrarInsercion(new AtomicReference<>()),
                clienteDao(Optional.of(cliente)),
                null,
                ventaDao(),
                cierreAbierto());
        assertThrows(IllegalStateException.class,
                () -> useCaseCreditoExceso.registrarVentaCreditoCorporativo(ventaExcesoCentavo, 10, 30));

        CuentaPorPagar cxp = new CuentaPorPagar();
        cxp.setMontoTotal(new BigDecimal("100.30"));
        cxp.setMontoPagado(new BigDecimal("100.00"));
        cxp.setRazonSocialProveedor("Proveedor Exacto");
        cxp.setNumeroDocumento("F001-99");
        AtomicReference<BigDecimal> pagoRegistrado = new AtomicReference<>();
        AtomicReference<MovimientoCaja> movPago = new AtomicReference<>();
        CuentaCorrienteUseCase ccPagar = new DefaultCuentaCorrienteUseCase(
                txManager(conexionCapturandoSetBigDecimal(new ArrayList<>(), null)),
                cuentaPagarDao(Optional.of(cxp), pagoRegistrado),
                null,
                null,
                cajaDao(movPago),
                null,
                cierreAbierto());

        FinanzasService servicePagar = new FinanzasService(ccPagar, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> servicePagar.registrarPagoProveedor(1, new BigDecimal("0.31"), "EFECTIVO", 1, 1, "Exceso"));
        assertTrue(servicePagar.registrarPagoProveedor(
                1, new BigDecimal("0.10").add(new BigDecimal("0.20")), "EFECTIVO", 1, 1, "Exacto"));
        assertEquals(new BigDecimal("0.30"), pagoRegistrado.get());
        assertEquals(new BigDecimal("0.30"), movPago.get().getMonto());

        CuentaPorCobrar cxc = new CuentaPorCobrar();
        cxc.setMontoTotal(new BigDecimal("100.30"));
        cxc.setMontoCobrado(new BigDecimal("100.00"));
        cxc.setRazonSocialCliente("Cliente Exacto");
        cxc.setNumeroComprobante("CR-99");
        AtomicReference<BigDecimal> cobroRegistrado = new AtomicReference<>();
        AtomicReference<MovimientoCaja> movCobro = new AtomicReference<>();
        DefaultCuentaCorrienteUseCase ccCobrar = new DefaultCuentaCorrienteUseCase(
                txManager(conexionCapturandoSetBigDecimal(new ArrayList<>(), null)),
                null,
                cuentaCobrarDao(Optional.of(cxc), cobroRegistrado),
                null,
                cajaDao(movCobro),
                null,
                cierreAbierto());

        assertThrows(IllegalArgumentException.class,
                () -> ccCobrar.registrarCobroCliente(1, new BigDecimal("0.31"), "EFECTIVO", 1, 1, "Exceso"));
        assertTrue(ccCobrar.registrarCobroCliente(
                1, new BigDecimal("0.10").add(new BigDecimal("0.20")), "EFECTIVO", 1, 1, "Exacto"));
        assertEquals(new BigDecimal("0.30"), cobroRegistrado.get());
        assertEquals(new BigDecimal("0.30"), movCobro.get().getMonto());
    }

    @Test
    void cambioPrecioMantieneEscala4InsumoYEscala2Producto() throws Exception {
        HistorialPrecio hp = new HistorialPrecio();
        hp.setPrecioAnterior(new BigDecimal("10.0000"));
        hp.setPrecioNuevo(new BigDecimal("12.34565"));
        assertEquals(4, hp.getPrecioNuevo().scale());
        assertEquals(new BigDecimal("12.3457"), hp.getPrecioNuevo());

        HistorialPrecio variacion = new HistorialPrecio();
        variacion.setPrecioAnterior(new BigDecimal("10.0000"));
        variacion.setPrecioNuevo(new BigDecimal("12.5000"));
        assertEquals(new BigDecimal("25.00"), variacion.getVariacionPorcentual());

        List<BigDecimal> paramsJdbc = new ArrayList<>();
        AtomicReference<HistorialPrecio> guardado = new AtomicReference<>();
        HistorialPrecioDAO dao = proxy(HistorialPrecioDAO.class, (p, m, args) -> {
            if ("insertar".equals(m.getName())) {
                guardado.set((HistorialPrecio) args[1]);
                return 1;
            }
            return defaultValue(m.getReturnType());
        });

        CambioPrecioUseCase useCase = new DefaultCambioPrecioUseCase(
                txManager(conexionCapturandoSetBigDecimal(paramsJdbc, null)), dao);
        FinanzasService finanzas = new FinanzasService(null, useCase, null);

        assertTrue(finanzas.registrarCambioPrecioConMotivo("INSUMO", 7, "Aceite",
                new BigDecimal("4.1000"), new BigDecimal("4.12345"), 1, "Admin", "Ajuste proveedor"));
        assertEquals(new BigDecimal("4.1235"), guardado.get().getPrecioNuevo());
        assertEquals(2, paramsJdbc.size());
        assertEquals(new BigDecimal("4.1235"), paramsJdbc.get(0));
        assertEquals(4, paramsJdbc.get(0).scale());
        assertEquals(new BigDecimal("4.1235"), paramsJdbc.get(1));

        paramsJdbc.clear();
        assertTrue(finanzas.registrarCambioPrecioConMotivo("PRODUCTO", 9, "Combo",
                new BigDecimal("15.0000"), new BigDecimal("15.5560"), 1, "Admin", "Ajuste carta"));
        assertEquals(1, paramsJdbc.size());
        assertEquals(new BigDecimal("15.56"), paramsJdbc.get(0));
        assertEquals(2, paramsJdbc.get(0).scale());
    }

    @Test
    void compraYExportacionContableAcumulanIgvSinDerivaDecimal() {
        BigDecimal tasaIgv = new BigDecimal("0.18");
        BigDecimal divisorIgv = new BigDecimal("1.18");
        BigDecimal sumBase = BigDecimal.ZERO;
        BigDecimal sumIgv = BigDecimal.ZERO;
        BigDecimal sumTotal = BigDecimal.ZERO;

        List<ExportacionContableService.VentaRegistroSunat> ventas = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            BigDecimal precioUnitario = new BigDecimal("0.10");
            BigDecimal totalLinea = precioUnitario.multiply(new BigDecimal("1.18"))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal base = totalLinea.divide(divisorIgv, 2, RoundingMode.HALF_UP);
            BigDecimal igv = totalLinea.subtract(base).setScale(2, RoundingMode.HALF_UP);

            Compra compra = new Compra();
            compra.setBaseImponible(base);
            compra.setIgv(igv);
            compra.setTotal(base.add(igv));
            assertEquals(totalLinea, compra.getTotal());

            ExportacionContableService.VentaRegistroSunat reg = new ExportacionContableService.VentaRegistroSunat();
            reg.subtotal = base;
            reg.igv = igv;
            reg.total = totalLinea;
            ventas.add(reg);

            sumBase = sumBase.add(base);
            sumIgv = sumIgv.add(igv);
            sumTotal = sumTotal.add(totalLinea);
        }

        assertEquals(new BigDecimal("10.00"), sumBase);
        assertEquals(new BigDecimal("2.00"), sumIgv);
        assertEquals(new BigDecimal("12.00"), sumTotal);

        BigDecimal baseExacta = new BigDecimal("100.00");
        BigDecimal igvExacto = baseExacta.multiply(tasaIgv).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("18.00"), igvExacto);

        ExportacionContableService.PeriodoContableResumen resumen =
                new ExportacionContableService.PeriodoContableResumen(
                        ventas, sumTotal, sumBase, sumIgv, new BigDecimal("0.10").add(new BigDecimal("0.20")));
        assertEquals(0, new BigDecimal("12.00").compareTo(resumen.sumTotal()));
        assertEquals(0, new BigDecimal("10.00").compareTo(resumen.sumBase()));
        assertEquals(0, new BigDecimal("2.00").compareTo(resumen.sumIgv()));
        assertEquals(0, new BigDecimal("0.30").compareTo(resumen.sumCompras()));
    }

    @Test
    void usuarioFilaPlanillaYAsistenciaExcelCalculanTotalesExactos() throws Exception {
        Usuario u = new Usuario();
        assertEquals(new BigDecimal("1200.00"), u.getSueldoBase());
        u.setSueldoBase(new BigDecimal("1850.555"));
        assertEquals(new BigDecimal("1850.56"), u.getSueldoBase());
        u.setSueldoBase(new BigDecimal("-10.00"));
        assertEquals(new BigDecimal("-10.00"), u.getSueldoBase());

        FilaPlanilla f1 = new FilaPlanilla();
        f1.setSueldoBase(new BigDecimal("0.10"));
        f1.setDescuentoTardanza(new BigDecimal("0.03"));
        f1.setBonoHorasExtra(new BigDecimal("0.05"));
        f1.setTotalNetoPagar(f1.getSueldoBase().subtract(f1.getDescuentoTardanza()).add(f1.getBonoHorasExtra()));
        assertEquals(new BigDecimal("0.12"), f1.getTotalNetoPagar());

        FilaPlanilla f2 = new FilaPlanilla();
        f2.setSueldoBase(new BigDecimal("0.20"));
        f2.setDescuentoTardanza(new BigDecimal("0.07"));
        f2.setBonoHorasExtra(new BigDecimal("0.05"));
        f2.setTotalNetoPagar(f2.getSueldoBase().subtract(f2.getDescuentoTardanza()).add(f2.getBonoHorasExtra()));
        assertEquals(new BigDecimal("0.18"), f2.getTotalNetoPagar());

        FilaPlanilla f3 = new FilaPlanilla();
        f3.setSueldoBase(new BigDecimal("1200.00"));
        f3.setDescuentoTardanza(BigDecimal.ZERO);
        f3.setBonoHorasExtra(BigDecimal.ZERO);
        f3.setTotalNetoPagar(new BigDecimal("1200.00"));

        List<FilaPlanilla> filas = List.of(f1, f2, f3);
        AsistenciaService asistenciaService = new AsistenciaService() {
            @Override
            public List<FilaPlanilla> calcularPlanillaMensual(int anio, int mes) {
                return filas;
            }
        };

        File xlsx = tempDir.resolve("planilla_precision.xlsx").toFile();
        asistenciaService.exportarPlanillaMensualExcel(2026, 11, xlsx);
        assertTrue(xlsx.exists() && xlsx.length() > 0);

        try (InputStream is = Files.newInputStream(xlsx.toPath());
                XSSFWorkbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row totalRow = sheet.getRow(sheet.getLastRowNum());
            assertNotNull(totalRow);
            assertEquals("TOTALES GENERALES:", totalRow.getCell(1).getStringCellValue());
            BigDecimal excelSueldoTotal = BigDecimal.valueOf(totalRow.getCell(8).getNumericCellValue())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal excelDescTotal = BigDecimal.valueOf(totalRow.getCell(9).getNumericCellValue())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal excelBonoTotal = BigDecimal.valueOf(totalRow.getCell(10).getNumericCellValue())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal excelNetoTotal = BigDecimal.valueOf(totalRow.getCell(11).getNumericCellValue())
                    .setScale(2, RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("1200.30"), excelSueldoTotal);
            assertEquals(new BigDecimal("0.10"), excelDescTotal);
            assertEquals(new BigDecimal("0.10"), excelBonoTotal);
            assertEquals(new BigDecimal("1200.30"), excelNetoTotal);
        }
    }

    @Test
    void resultadoReporteAvanzadoYAuditoriaReflexionSinDoubleEnLas20Clases() {
        ResultadoReporteAvanzado.RebanadaPie rebanada = new ResultadoReporteAvanzado.RebanadaPie(
                "Delivery", new BigDecimal("0.10").add(new BigDecimal("0.20")));
        assertEquals(new BigDecimal("0.30"), rebanada.valor());

        ResultadoReporteAvanzado r = new ResultadoReporteAvanzado(
                null, List.<String[]>of(), List.of(rebanada),
                new BigDecimal("0.10").add(new BigDecimal("0.20")),
                3, "Delivery", new BigDecimal("0.30"),
                null, null, "", "", "", "");
        assertEquals(new BigDecimal("0.30"), r.montoTotal());
        assertEquals(new BigDecimal("0.30"), r.topValor());

        Class<?>[] migratedClasses = new Class<?>[] {
            CuentaPorCobrar.class,
            CuentaPorPagar.class,
            CobroCliente.class,
            PagoProveedor.class,
            Cliente.class,
            Proveedor.class,
            FinanzasService.class,
            CuentaCorrienteUseCase.class,
            DefaultCuentaCorrienteUseCase.class,
            HistorialPrecio.class,
            Compra.class,
            CambioPrecioUseCase.class,
            DefaultCambioPrecioUseCase.class,
            FilaPlanilla.class,
            Usuario.class,
            AsistenciaService.class,
            UsuarioGestionService.class,
            ResultadoReporteAvanzado.class,
            ReporteService.class,
            ExportacionContableService.class
        };

        for (Class<?> clazz : migratedClasses) {
            for (Field field : clazz.getDeclaredFields()) {
                assertFalse(isDoubleType(field.getType()),
                        "Campo aún usa double/Double: " + clazz.getSimpleName() + "." + field.getName());
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                assertFalse(isDoubleType(method.getReturnType()),
                        "Retorno aún usa double/Double: " + clazz.getSimpleName() + "." + method.getName());
                for (Class<?> paramType : method.getParameterTypes()) {
                    assertFalse(isDoubleType(paramType),
                            "Parámetro aún usa double/Double: " + clazz.getSimpleName() + "." + method.getName());
                }
            }
        }
    }

    private static boolean isDoubleType(Class<?> type) {
        return type == double.class || type == Double.class;
    }

    private static Connection conexionCapturandoSetBigDecimal(List<BigDecimal> captured, ResultSet rs) {
        return proxy(Connection.class, (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                return proxy(PreparedStatement.class, (p, m, a) -> {
                    if ("setBigDecimal".equals(m.getName())) {
                        captured.add((BigDecimal) a[1]);
                    }
                    if ("executeQuery".equals(m.getName())) {
                        return rs;
                    }
                    if ("executeUpdate".equals(m.getName())) {
                        return 1;
                    }
                    return defaultValue(m.getReturnType());
                });
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(boolean next) {
        AtomicBoolean first = new AtomicBoolean(true);
        return proxy(ResultSet.class, (p, method, args) ->
                "next".equals(method.getName()) && first.getAndSet(false) && next);
    }

    private static CuentaPorPagarDAO cuentaPagarDao(Optional<CuentaPorPagar> cuenta, AtomicReference<BigDecimal> pago) {
        return proxy(CuentaPorPagarDAO.class, (p, method, args) -> {
            if ("buscarPorIdParaActualizar".equals(method.getName())) {
                return cuenta;
            }
            if ("registrarPago".equals(method.getName())) {
                pago.set(((PagoProveedor) args[1]).getMonto());
                return true;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static CuentaPorCobrarDAO cuentaCobrarDao(Optional<CuentaPorCobrar> cuenta,
            AtomicReference<BigDecimal> cobro) {
        return proxy(CuentaPorCobrarDAO.class, (p, method, args) -> {
            if ("buscarPorIdParaActualizar".equals(method.getName())) {
                return cuenta;
            }
            if ("registrarCobro".equals(method.getName())) {
                cobro.set(((CobroCliente) args[1]).getMonto());
                return true;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static CuentaPorCobrarDAO cuentaCobrarInsercion(AtomicReference<CuentaPorCobrar> guardada) {
        return proxy(CuentaPorCobrarDAO.class, (p, method, args) -> {
            if ("insertar".equals(method.getName())) {
                guardada.set((CuentaPorCobrar) args[1]);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ClienteDAO clienteDao(Optional<Cliente> cliente) {
        return proxy(ClienteDAO.class, (p, method, args) ->
                "buscarPorId".equals(method.getName()) ? cliente : defaultValue(method.getReturnType()));
    }

    private static VentaDAO ventaDao() {
        return proxy(VentaDAO.class, (p, method, args) ->
                "insertarVenta".equals(method.getName()) ? 41 : defaultValue(method.getReturnType()));
    }

    private static CajaDAO cajaDao(AtomicReference<MovimientoCaja> movimiento) {
        return proxy(CajaDAO.class, (p, method, args) -> {
            if ("obtenerCajaAbierta".equals(method.getName())) {
                Caja caja = new Caja();
                caja.setIdCaja(1);
                return Optional.of(caja);
            }
            if ("registrarMovimiento".equals(method.getName())) {
                movimiento.set((MovimientoCaja) args[1]);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static CierreContableService cierreAbierto() {
        return new CierreContableService(null, null, null) {
            @Override
            public void validarPeriodoAbierto(Connection c, LocalDate f) throws PeriodoCerradoException {
            }
        };
    }

    private static TransactionManager txManager(Connection conn) {
        return new TransactionManager() {
            @Override
            public <T> T execute(Function<Connection, T> work) {
                return work.apply(conn);
            }

            @Override
            public void executeVoid(Consumer<Connection> work) {
                work.accept(conn);
            }

            @Override
            public <T> T executeChecked(CheckedWork<T> work) throws NegocioException, SQLException {
                return work.apply(conn);
            }

            @Override
            public void executeVoidChecked(CheckedVoidWork work) throws NegocioException, SQLException {
                work.accept(conn);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
