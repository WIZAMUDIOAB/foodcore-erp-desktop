package com.foodcore.service.caja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.Insumo;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.dto.ResultadoCierreConActa;
import com.foodcore.service.ActaEntregaTurnoService;
import com.foodcore.service.CajaService;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.tx.TransactionManager;
import java.io.File;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CajaUseCasesUnitTest {
    private static final BigDecimal UNO = BigDecimal.ONE;

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> defaultValue(method.getReturnType()));
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
        return 0D;
    }

    private static TransactionManager transactionManager() {
        return new TransactionManager() {
            public <T> T execute(Function<Connection, T> work) { return work.apply(connection()); }
            public void executeVoid(Consumer<Connection> work) { work.accept(connection()); }
            public <T> T executeChecked(CheckedWork<T> work) throws NegocioException, SQLException {
                return work.apply(connection());
            }
            public void executeVoidChecked(CheckedVoidWork work) throws NegocioException, SQLException {
                work.accept(connection());
            }
        };
    }

    private static CajaDAO cajaDao(java.lang.reflect.InvocationHandler handler) {
        return (CajaDAO) Proxy.newProxyInstance(CajaDAO.class.getClassLoader(), new Class<?>[]{CajaDAO.class}, handler);
    }

    private static InsumoDAO insumoDao() {
        return (InsumoDAO) Proxy.newProxyInstance(InsumoDAO.class.getClassLoader(), new Class<?>[]{InsumoDAO.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static CierreContableService cierreContable() {
        return new CierreContableService(null, null, null) {
            @Override
            public void validarPeriodoAbierto(Connection c, LocalDate f) { }
        };
    }

    private static DefaultCajaTurnoOperacionesUseCase operaciones(CajaDAO dao) {
        return new DefaultCajaTurnoOperacionesUseCase(transactionManager(), dao, cierreContable(), () -> connection());
    }

    private static Caja caja(int id, String estado) {
        return new Caja(id, 1, "Caja", UNO, estado);
    }

    @Test
    void abrirTurnoCajaValidaNegativoCajaExistenteYAperturaExitosa() throws Exception {
        AtomicReference<Optional<Caja>> abierta = new AtomicReference<>(Optional.empty());
        AtomicReference<MovimientoCaja> movimiento = new AtomicReference<>();
        CajaDAO dao = cajaDao((proxy, method, args) -> switch (method.getName()) {
            case "obtenerCajaAbierta" -> abierta.get();
            case "abrirCaja" -> 7;
            case "registrarMovimiento" -> { movimiento.set((MovimientoCaja) args[1]); yield null; }
            default -> defaultValue(method.getReturnType());
        });
        DefaultCajaTurnoOperacionesUseCase useCase = operaciones(dao);
        assertThrows(NegocioException.class, () -> useCase.abrirTurnoCaja(1, "Caja", BigDecimal.valueOf(-1)));
        abierta.set(Optional.of(caja(4, "ABIERTA")));
        assertThrows(NegocioException.class, () -> useCase.abrirTurnoCaja(1, "Caja", UNO));
        abierta.set(Optional.empty());
        assertEquals(7, useCase.abrirTurnoCaja(1, "Caja", UNO));
        assertEquals("INGRESO", movimiento.get().getTipoMovimiento());
        assertEquals("EFECTIVO", movimiento.get().getMedioPago());
    }

    @Test
    void registrarGastoOperativoValidaEntradasYCicloExitoso() throws Exception {
        AtomicReference<Optional<Caja>> abierta = new AtomicReference<>(Optional.of(caja(7, "ABIERTA")));
        AtomicReference<Optional<CategoriaGasto>> categoria = new AtomicReference<>(Optional.empty());
        AtomicReference<MovimientoCaja> movimiento = new AtomicReference<>();
        CajaDAO dao = cajaDao((proxy, method, args) -> switch (method.getName()) {
            case "obtenerCajaAbierta" -> abierta.get();
            case "obtenerCategoriaGastoPorId" -> categoria.get();
            case "registrarMovimiento" -> { movimiento.set((MovimientoCaja) args[1]); yield null; }
            default -> defaultValue(method.getReturnType());
        });
        DefaultCajaTurnoOperacionesUseCase useCase = operaciones(dao);
        assertThrows(NegocioException.class, () -> useCase.registrarGastoOperativo(7, 1, BigDecimal.ZERO, "gasto", 2));
        assertThrows(NegocioException.class, () -> useCase.registrarGastoOperativo(7, 1, UNO, " ", 2));
        abierta.set(Optional.empty());
        assertThrows(CajaCerradaException.class, () -> useCase.registrarGastoOperativo(7, 1, UNO, "gasto", 2));
        abierta.set(Optional.of(caja(8, "ABIERTA")));
        assertThrows(CajaCerradaException.class, () -> useCase.registrarGastoOperativo(7, 1, UNO, "gasto", 2));
        abierta.set(Optional.of(caja(7, "ABIERTA")));
        assertThrows(NegocioException.class, () -> useCase.registrarGastoOperativo(7, 1, UNO, "gasto", 2));
        categoria.set(Optional.of(new CategoriaGasto(2, "C2", "Gas", "", false)));
        assertThrows(NegocioException.class, () -> useCase.registrarGastoOperativo(7, 1, UNO, "gasto", 2));
        categoria.set(Optional.of(new CategoriaGasto(2, "C2", "Gas", "", true)));
        useCase.registrarGastoOperativo(7, 1, UNO, "gasto", 2);
        assertEquals("EGRESO", movimiento.get().getTipoMovimiento());
        assertEquals(Integer.valueOf(2), movimiento.get().getIdCategoriaGasto());
    }

    @Test
    void consultasDeTurnoDeleganEnCajaDaoConConnectionSupplier() throws Exception {
        AtomicInteger conexiones = new AtomicInteger();
        List<CategoriaGasto> categorias = List.of(new CategoriaGasto(2, "C2", "Gas", "", true));
        List<MovimientoCaja> gastos = List.of(new MovimientoCaja());
        Map<String, Object[]> resumen = Map.of("VENTA", new Object[]{UNO});
        Map<String, BigDecimal> totales = Map.of("EFECTIVO", UNO);
        CajaDAO dao = cajaDao((proxy, method, args) -> switch (method.getName()) {
            case "obtenerCajaAbierta" -> Optional.of(caja(7, "ABIERTA"));
            case "listarCategoriasGastoActivas" -> categorias;
            case "listarGastosTurno" -> gastos;
            case "calcularTotalVentasTurno" -> UNO;
            case "calcularTotalGastosTurno" -> BigDecimal.TEN;
            case "obtenerResumenVentasCierreZ" -> resumen;
            case "obtenerTotalesPorMedioPago" -> totales;
            default -> defaultValue(method.getReturnType());
        });
        DefaultCajaTurnoOperacionesUseCase useCase = new DefaultCajaTurnoOperacionesUseCase(
                transactionManager(), dao, cierreContable(), () -> { conexiones.incrementAndGet(); return connection(); });
        assertEquals(7, useCase.obtenerCajaActiva().orElseThrow().getIdCaja());
        assertSame(categorias, useCase.listarCategoriasGastoActivas());
        assertSame(gastos, useCase.listarGastosTurno(7));
        assertEquals(UNO, useCase.calcularVentasTurno(7));
        assertEquals(BigDecimal.TEN, useCase.calcularGastosTurno(7));
        assertSame(resumen, useCase.obtenerResumenVentasCierreZ(7));
        assertSame(totales, useCase.obtenerTotalesCierreZ(7));
        assertEquals(7, conexiones.get());
    }

    @Test
    void facadeCajaServiceDelegaOperacionesYArchivoResuelto() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();
        AtomicReference<File> destino = new AtomicReference<>();
        List<CategoriaGasto> categorias = List.of(new CategoriaGasto());
        List<MovimientoCaja> gastos = List.of(new MovimientoCaja());
        List<InsumoCriticoTurno> criticos = new ArrayList<>();
        Map<String, Object[]> resumen = Map.of("V", new Object[]{UNO});
        Map<String, BigDecimal> totales = Map.of("EFECTIVO", UNO);
        Caja caja = caja(7, "ABIERTA");
        ResultadoCierreConActa resultado = new ResultadoCierreConActa(
                caja, UNO, UNO, BigDecimal.ZERO, criticos, null);
        CajaTurnoOperacionesUseCase operaciones = new CajaTurnoOperacionesUseCase() {
            public Optional<Caja> obtenerCajaActiva() { llamadas.incrementAndGet(); return Optional.of(caja); }
            public int abrirTurnoCaja(int u, String n, BigDecimal m) { llamadas.incrementAndGet(); return 7; }
            public void registrarGastoOperativo(int c, int u, BigDecimal m, String co, Integer ca) { llamadas.incrementAndGet(); }
            public List<CategoriaGasto> listarCategoriasGastoActivas() { llamadas.incrementAndGet(); return categorias; }
            public List<MovimientoCaja> listarGastosTurno(int c) { llamadas.incrementAndGet(); return gastos; }
            public BigDecimal calcularVentasTurno(int c) { llamadas.incrementAndGet(); return UNO; }
            public BigDecimal calcularGastosTurno(int c) { llamadas.incrementAndGet(); return BigDecimal.TEN; }
            public Map<String, Object[]> obtenerResumenVentasCierreZ(int c) { llamadas.incrementAndGet(); return resumen; }
            public Map<String, BigDecimal> obtenerTotalesCierreZ(int c) { llamadas.incrementAndGet(); return totales; }
        };
        ArqueoCierreZUseCase arqueo = new ArqueoCierreZUseCase() {
            public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno() { llamadas.incrementAndGet(); return criticos; }
            public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno(Connection c) { llamadas.incrementAndGet(); return criticos; }
            public List<InsumoCriticoTurno> clasificarInsumosCriticos(List<Insumo> i) { llamadas.incrementAndGet(); return criticos; }
            public ResultadoCierreConActa cerrarTurnoCaja(int c, int u, BigDecimal f) { llamadas.incrementAndGet(); return resultado; }
            public ResultadoCierreConActa cerrarTurnoConActaPdf(int c, int u, BigDecimal f, String s, String e, File d) {
                llamadas.incrementAndGet(); destino.set(d); return resultado;
            }
        };
        CajaService service = new CajaServiceImpl(operaciones, arqueo);
        assertEquals(Optional.of(caja), service.obtenerCajaActiva());
        assertEquals(7, service.abrirTurnoCaja(1, "Caja", UNO));
        service.registrarGastoOperativo(7, 1, UNO, "gasto");
        service.registrarGastoOperativo(7, 1, UNO, "gasto", 2);
        assertSame(categorias, service.listarCategoriasGastoActivas());
        assertSame(gastos, service.listarGastosTurno(7));
        assertEquals(UNO, service.calcularVentasTurno(7));
        assertEquals(BigDecimal.TEN, service.calcularGastosTurno(7));
        assertSame(resumen, service.obtenerResumenVentasCierreZ(7));
        assertSame(totales, service.obtenerTotalesCierreZ(7));
        assertSame(criticos, service.obtenerInsumosCriticosTurno());
        Connection conn = connection();
        assertSame(criticos, service.obtenerInsumosCriticosTurno(conn));
        service.cerrarTurnoCaja(7, 1, UNO);
        assertSame(resultado, service.cerrarTurnoConActaPdf(7, 1, UNO, "sale", "entra", null));
        assertNotNull(destino.get());
        assertTrue(destino.get().getName().matches("Acta_Entrega_Turno_7_\\d{8}_\\d{6}\\.pdf"));
        File explicito = new File("acta-explicita.pdf");
        assertSame(resultado, service.cerrarTurnoConActaPdf(7, 1, UNO, "sale", "entra", explicito));
        assertSame(explicito, destino.get());
        assertEquals(15, llamadas.get());
    }
}
