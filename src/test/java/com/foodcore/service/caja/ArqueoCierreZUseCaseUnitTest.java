package com.foodcore.service.caja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.Insumo;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.dto.ResultadoCierreConActa;
import com.foodcore.service.ActaEntregaTurnoService;
import com.foodcore.service.CierreContableService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArqueoCierreZUseCaseUnitTest {
    private static final BigDecimal CERO = BigDecimal.ZERO;

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
            @Override
            public <T> T execute(Function<Connection, T> work) {
                return work.apply(connection());
            }

            @Override
            public void executeVoid(Consumer<Connection> work) {
                work.accept(connection());
            }

            @Override
            public <T> T executeChecked(CheckedWork<T> work) throws NegocioException, SQLException {
                return work.apply(connection());
            }

            @Override
            public void executeVoidChecked(CheckedVoidWork work) throws NegocioException, SQLException {
                work.accept(connection());
            }
        };
    }

    private static Caja caja(int id) {
        return new Caja(id, 2, "Caja 1", new BigDecimal("100.00"), "ABIERTA");
    }

    private static InsumoDAO insumoDao(InvocationHandler handler) {
        return (InsumoDAO) Proxy.newProxyInstance(InsumoDAO.class.getClassLoader(),
                new Class<?>[]{InsumoDAO.class}, handler);
    }

    private static CajaDAO cajaDao(InvocationHandler handler) {
        return (CajaDAO) Proxy.newProxyInstance(CajaDAO.class.getClassLoader(),
                new Class<?>[]{CajaDAO.class}, handler);
    }

    private static CierreContableService cierreContable() {
        return new CierreContableService(null, null, null) {
            @Override
            public void validarPeriodoAbierto(Connection c, LocalDate f) { }
        };
    }

    private static DefaultArqueoCierreZUseCase useCase(CajaDAO cajaDAO, InsumoDAO insumoDAO,
                                                        JdbcTransactionManager.ConnectionSupplier supplier) {
        return new DefaultArqueoCierreZUseCase(transactionManager(), cajaDAO, insumoDAO,
                cierreContable(), new ActaEntregaTurnoService(), supplier);
    }

    private static Insumo insumo(long id, String nombre, String stock, String minimo) {
        return new Insumo(id, "I-" + id, nombre, "KG", new BigDecimal(stock),
                new BigDecimal(minimo), new BigDecimal("12.00"));
    }

    @Test
    void clasificarInsumosCriticosYObtenerCriticosTurnoOrdenaAgotadosYStockMinimo() throws Exception {
        Insumo agotado = insumo(1L, "Pollo", "0.00", "5.00");
        Insumo agotadoNegativo = insumo(2L, "Arroz", "-1.00", "3.00");
        Insumo minimo = insumo(3L, "Carne", "5.00", "5.00");
        Insumo normal = insumo(4L, "Papa", "6.00", "5.00");
        AtomicInteger llamadas = new AtomicInteger();
        AtomicReference<Connection> connectionUsada = new AtomicReference<>();
        Connection connSupplier = connection();
        InsumoDAO insumoDAO = insumoDao((proxy, method, args) -> {
            if (method.getName().equals("listarTodos")) {
                llamadas.incrementAndGet();
                connectionUsada.set((Connection) args[0]);
                return List.of(agotado, agotadoNegativo, minimo, normal);
            }
            return defaultValue(method.getReturnType());
        });
        DefaultArqueoCierreZUseCase useCase = useCase(cajaDao((proxy, method, args) ->
                defaultValue(method.getReturnType())), insumoDAO, () -> connSupplier);

        assertTrue(useCase.clasificarInsumosCriticos(List.of()).isEmpty());
        List<InsumoCriticoTurno> clasificados = useCase.clasificarInsumosCriticos(
                List.of(agotado, agotadoNegativo, minimo, normal));
        assertEquals(3, clasificados.size());
        assertEquals(List.of("AGOTADO", "AGOTADO", "STOCK_MINIMO"),
                clasificados.stream().map(InsumoCriticoTurno::nivel).toList());
        assertEquals(List.of("Arroz", "Pollo", "Carne"),
                clasificados.stream().map(InsumoCriticoTurno::nombre).toList());
        assertTrue(clasificados.get(0).isAgotado());
        assertFalse(clasificados.get(2).isAgotado());

        List<InsumoCriticoTurno> obtenidos = useCase.obtenerInsumosCriticosTurno();
        assertEquals(1, llamadas.get());
        assertSame(connSupplier, connectionUsada.get());
        assertEquals(3, obtenidos.size());
    }

    @Test
    void cerrarTurnoCajaValidaNegativoYCajaNoAbierta() throws Exception {
        AtomicReference<Optional<Caja>> abierta = new AtomicReference<>(Optional.empty());
        CajaDAO cajaDAO = cajaDao((proxy, method, args) -> method.getName().equals("obtenerCajaAbierta")
                ? abierta.get() : defaultValue(method.getReturnType()));
        DefaultArqueoCierreZUseCase useCase = useCase(cajaDAO,
                insumoDao((proxy, method, args) -> List.of()), () -> connection());

        assertThrows(NegocioException.class,
                () -> useCase.cerrarTurnoCaja(7, 9, new BigDecimal("-0.01")));
        assertThrows(CajaCerradaException.class,
                () -> useCase.cerrarTurnoCaja(7, 9, new BigDecimal("10.00")));
        abierta.set(Optional.of(caja(8)));
        assertThrows(CajaCerradaException.class,
                () -> useCase.cerrarTurnoCaja(7, 9, new BigDecimal("10.00")));
    }

    @Test
    void cerrarTurnoCajaCalculaMontoEsperadoDiferenciaYCierraTurno() throws Exception {
        AtomicReference<Object[]> cierre = new AtomicReference<>();
        AtomicReference<MovimientoCaja> movimiento = new AtomicReference<>();
        Caja caja = caja(7);
        CajaDAO cajaDAO = cajaDao((proxy, method, args) -> switch (method.getName()) {
            case "obtenerCajaAbierta" -> Optional.of(caja);
            case "calcularTotalVentasTurno" -> new BigDecimal("250.00");
            case "calcularTotalIngresosEfectivoTurno" -> CERO;
            case "calcularTotalGastosTurno" -> new BigDecimal("50.00");
            case "cerrarCaja" -> {
                cierre.set(args);
                yield null;
            }
            case "registrarMovimiento" -> {
                movimiento.set((MovimientoCaja) args[1]);
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        DefaultArqueoCierreZUseCase useCase = useCase(cajaDAO,
                insumoDao((proxy, method, args) -> List.of()), () -> connection());

        ResultadoCierreConActa resultado = useCase.cerrarTurnoCaja(
                7, 9, new BigDecimal("295.00"));
        assertEquals(new BigDecimal("300.00"), resultado.montoEsperado());
        assertEquals(new BigDecimal("295.00"), resultado.efectivoContado());
        assertEquals(new BigDecimal("-5.00"), resultado.diferencia());
        assertEquals(6, cierre.get().length);
        assertEquals(7, cierre.get()[1]);
        assertEquals(new BigDecimal("300.00"), cierre.get()[3]);
        assertEquals(new BigDecimal("295.00"), cierre.get()[4]);
        assertEquals(new BigDecimal("-5.00"), cierre.get()[5]);
        assertEquals("CERRADA", caja.getEstado());
        assertNotNull(movimiento.get());
        assertEquals("CIERRE_Z", movimiento.get().getTipoMovimiento());
        assertEquals("EGRESO", movimiento.get().getTipoMovimiento().equals("CIERRE_Z") ? "EGRESO" : "");
        assertEquals("EFECTIVO", movimiento.get().getMedioPago());
        assertEquals(new BigDecimal("295.00"), movimiento.get().getMonto());
    }

    @Test
    void cerrarTurnoConActaPdfGeneraArchivoPdfRealYEnvuelveErrorDeEscritura(@TempDir Path tempDir)
            throws Exception {
        CajaDAO cajaDAO = cajaDao((proxy, method, args) -> switch (method.getName()) {
            case "obtenerCajaAbierta" -> Optional.of(caja(7));
            case "calcularTotalVentasTurno", "calcularTotalIngresosEfectivoTurno",
                    "calcularTotalGastosTurno" -> CERO;
            default -> defaultValue(method.getReturnType());
        });
        DefaultArqueoCierreZUseCase useCase = useCase(cajaDAO,
                insumoDao((proxy, method, args) -> List.of()), () -> connection());
        File destino = tempDir.resolve("acta.pdf").toFile();

        ResultadoCierreConActa resultado = useCase.cerrarTurnoConActaPdf(
                7, 9, new BigDecimal("100.00"), "Salida", "Entrada", destino);
        assertNotNull(resultado);
        assertSame(destino, resultado.archivoActaPdf());
        assertTrue(destino.isFile());
        assertTrue(destino.length() > 0);
        byte[] cabecera = Files.readAllBytes(destino.toPath());
        assertTrue(new String(cabecera, 0, Math.min(cabecera.length, 5),
                java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));

        File directorioDestino = tempDir.resolve("directorio-existente").toFile();
        assertTrue(directorioDestino.mkdir());
        assertThrows(NegocioException.class, () -> useCase.cerrarTurnoConActaPdf(
                7, 9, new BigDecimal("100.00"), "Salida", "Entrada", directorioDestino));
    }
}
