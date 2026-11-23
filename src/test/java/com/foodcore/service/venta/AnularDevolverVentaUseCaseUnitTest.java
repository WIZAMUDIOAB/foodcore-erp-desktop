package com.foodcore.service.venta;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.SupervisorAutorizacionService;
import com.foodcore.service.VentaService;
import com.foodcore.tx.TransactionManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnularDevolverVentaUseCaseUnitTest {
    private static final String SQL_LOCK = "SELECT id_caja, id_cliente, total, metodo_pago, estado, fecha_hora FROM venta WHERE id_venta = ? FOR UPDATE";
    private static final String SQL_UPDATE = "UPDATE venta SET estado = 'ANULADA' WHERE id_venta = ?";
    private static final String SQL_DETALLES = "SELECT id_producto, id_combo, cantidad FROM detalle_venta WHERE id_venta = ? AND COALESCE(estado, 'COMPLETADO') <> 'DEVUELTO'";

    @Test
    void calcularMontoReembolsoProrrateadoCubreTotalProrrateoYRestanteCero() throws Exception {
        assertEquals(new BigDecimal("80.00"), DefaultAnularDevolverVentaUseCase.calcularMontoReembolsoProrrateado(
                new BigDecimal("80"), new BigDecimal("25"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("40.00"), DefaultAnularDevolverVentaUseCase.calcularMontoReembolsoProrrateado(
                new BigDecimal("80.00"), new BigDecimal("50.00"), new BigDecimal("50.00")));
        assertEquals(new BigDecimal("30.00"), DefaultAnularDevolverVentaUseCase.calcularMontoReembolsoProrrateado(
                new BigDecimal("100.00"), new BigDecimal("30.00"), new BigDecimal("70.00")));
    }

    @Test
    void anularVentaValidaIdEstadoAnuladaYFlujoFeliz() throws Exception {
        Harness invalidId = new Harness("COMPLETADA", 8, List.of());
        assertThrows(NegocioException.class, () -> invalidId.useCase.anularVenta(0, false, sesionVigente(), "motivo"));

        Harness alreadyVoided = new Harness("ANULADA", 8, List.of());
        assertThrows(NegocioException.class, () -> alreadyVoided.useCase.anularVenta(10, false, sesionVigente(), "motivo"));

        Harness happy = new Harness("COMPLETADA", 8, List.of());
        happy.useCase.anularVenta(10, false, sesionVigente(), "operativo");
        assertEquals("ANULADA", happy.db.estado);
        assertEquals("ANULACION", happy.movimientos.get(0).getTipoMovimiento());
        assertEquals(new BigDecimal("-80.00"), happy.movimientos.get(0).getMonto());
        assertEquals(1, happy.anulacionReversiones);
        assertFalse(happy.platoPreparado);
    }

    @Test
    void anularVentaConSupervisorValidaSesionYRegistraAuditoriaYMerma() throws Exception {
        Harness h = new Harness("COMPLETADA", 8, List.of());
        assertThrows(NegocioException.class, () -> h.useCase.anularVentaConSupervisor(
                10, true, null, "1234", "motivo"));
        SesionUsuario expirada = new SesionUsuario(2L, "Cajero Uno", "ADMIN", EnumSet.of(Permiso.VENTA_ANULAR),
                "TERM-1", LocalDateTime.now(ZoneId.of("America/Lima")).minusMinutes(60));
        NegocioException expiradaEx = assertThrows(NegocioException.class, () -> h.useCase.anularVentaConSupervisor(
                10, true, expirada, "1234", "motivo"));
        assertEquals(CodigoError.ACCESO_DENEGADO, expiradaEx.getCodigoError());

        Usuario supervisor = h.useCase.anularVentaConSupervisor(10, true, sesionVigente(), "1234", "motivo");
        assertSame(h.supervisor, supervisor);
        assertTrue(h.auditoriaAcciones.contains("ANULACION_VENTA_SUPERVISOR"));
        assertTrue(h.platoPreparado);
    }

    @Test
    void devolverItemsParcialValidacionesProrrateoYDevolucionTotalAnulaVenta() throws Exception {
        Harness h = new Harness("COMPLETADA", 8, List.of(detalle(1, "50.00"), detalle(2, "50.00")));
        assertThrows(NegocioException.class, () -> h.useCase.devolverItemsParcial(10, List.of(), sesionVigente(), "motivo"));
        h.db.estado = "ANULADA";
        assertThrows(NegocioException.class, () -> h.useCase.devolverItemsParcial(10, List.of(1), sesionVigente(), "motivo"));
        h.db.estado = "COMPLETADA";
        h.detalles.get(0).setEstado("DEVUELTO");
        assertThrows(NegocioException.class, () -> h.useCase.devolverItemsParcial(10, List.of(1), sesionVigente(), "motivo"));
        h.detalles.get(0).setEstado("COMPLETADO");

        VentaService.ResultadoDevolucionParcial parcial = h.useCase.devolverItemsParcial(
                10, List.of(1), sesionVigente(), "motivo parcial");
        assertEquals("DEVUELTO", h.detalles.get(0).getEstado());
        assertEquals("COMPLETADA", parcial.nuevoEstadoVenta());
        assertEquals(new BigDecimal("40.00"), parcial.montoReembolso());
        assertEquals(new BigDecimal("40.00"), h.movimientos.get(0).getMonto());
        assertEquals("EGRESO", h.movimientos.get(0).getTipoMovimiento());
        assertEquals(1, h.puntosDevolucionReversiones);
        assertEquals(1, h.insumosDevolucionReversiones);
        assertTrue(h.auditoriaAcciones.contains("DEVOLUCION_PARCIAL_VENTA"));

        Harness total = new Harness("COMPLETADA", 8, List.of(detalle(3, "50.00")));
        VentaService.ResultadoDevolucionParcial devolucionTotal = total.useCase.devolverItemsParcial(
                10, List.of(3), sesionVigente(), "último ítem");
        assertEquals("ANULADA", devolucionTotal.nuevoEstadoVenta());
        assertEquals(new BigDecimal("0.00"), devolucionTotal.nuevoTotalVenta());
    }

    private static DetalleVenta detalle(int id, String subtotal) {
        return new DetalleVenta(id, 10, 7, 1, new BigDecimal(subtotal), new BigDecimal(subtotal));
    }

    private static SesionUsuario sesionVigente() {
        return new SesionUsuario(2L, "Cajero Uno", "ADMIN", EnumSet.of(Permiso.VENTA_ANULAR), "TERM-1",
                LocalDateTime.now(ZoneId.of("America/Lima")));
    }

    private static final class Harness {
        private final DbState db;
        private final List<MovimientoCaja> movimientos = new ArrayList<>();
        private final List<String> auditoriaAcciones = new ArrayList<>();
        private final List<DetalleVenta> detalles;
        private final Usuario supervisor = new Usuario(99, 99, 2, "sup1", "h", true);
        private boolean platoPreparado;
        private int anulacionReversiones;
        private int puntosDevolucionReversiones;
        private int insumosDevolucionReversiones;
        private final DefaultAnularDevolverVentaUseCase useCase;

        private Harness(String estado, int idCliente, List<DetalleVenta> detalles) {
            this.db = new DbState(estado, idCliente);
            this.detalles = new ArrayList<>(detalles);
            supervisor.setNombreRol("SUPERVISOR");
            Connection conn = connection(db);
            TransactionManager tx = new TransactionManager() {
                @Override public <T> T execute(java.util.function.Function<Connection, T> work) { return work.apply(conn); }
                @Override public void executeVoid(java.util.function.Consumer<Connection> work) { work.accept(conn); }
                @Override public <T> T executeChecked(CheckedWork<T> work) throws NegocioException, java.sql.SQLException { return work.apply(conn); }
                @Override public void executeVoidChecked(CheckedVoidWork work) throws NegocioException, java.sql.SQLException { work.accept(conn); }
            };
            VentaDAO ventaDAO = proxy(VentaDAO.class, (p, m, a) -> switch (m.getName()) {
                case "listarDetallesPorVenta" -> this.detalles;
                case "marcarDetalleDevuelto" -> { this.detalles.stream().filter(d -> d.getIdDetalleVenta() == (int) a[1]).findFirst().ifPresent(d -> d.setEstado("DEVUELTO")); yield 1; }
                case "actualizarTotalesVentaPorDevolucion" -> null;
                default -> defaultValue(m.getReturnType());
            });
            CajaDAO cajaDAO = proxy(CajaDAO.class, (p, m, a) -> {
                if (m.getName().equals("registrarMovimiento")) { movimientos.add((MovimientoCaja) a[1]); return null; }
                if (m.getName().equals("obtenerCajaAbierta")) return Optional.empty();
                return defaultValue(m.getReturnType());
            });
            AutorizacionService autorizacion = new AutorizacionService(null, () -> null) {
                @Override public void exigir(SesionUsuario s, Permiso p) throws NegocioException {
                    if (s == null || !s.tienePermiso(p)) throw new NegocioException("Permiso denegado");
                }
            };
            SupervisorAutorizacionService supervisorService = new SupervisorAutorizacionService(null, null) {
                @Override public Usuario autenticarSupervisorPorPin(String pin) { return supervisor; }
            };
            CierreContableService cierre = new CierreContableService(null, null, null) {
                @Override public void validarPeriodoAbierto(Connection c, LocalDateTime fecha) { }
            };
            FidelizacionService fidelizacion = new FidelizacionService(null, null) {
                @Override public int revertirPuntosPorAnulacionVenta(Connection c, int cliente, int venta, String motivo) { anulacionReversiones++; return 0; }
                @Override public int revertirPuntosPorDevolucionParcial(Connection c, int cliente, int venta, BigDecimal monto, boolean anulada, String motivo) { puntosDevolucionReversiones++; return 0; }
            };
            AuditoriaDAOImpl auditoria = new AuditoriaDAOImpl() {
                @Override public void registrarEventoSeguro(Connection c, int usuario, String accion, String entidad, String anterior, String nuevo) { auditoriaAcciones.add(accion); }
            };
            VentaCalculoValidator validator = new DefaultVentaCalculoValidator(autorizacion, fidelizacion);
            VentaInventarioKardexHandler kardex = new VentaInventarioKardexHandler() {
                @Override public void completarTrazabilidadComboDetalle(Connection c, DetalleVenta d) { }
                @Override public void acumularInsumosParaLinea(Connection c, Map<Long, BigDecimal> destino, Integer combo, int producto, int cantidad) { }
                @Override public PlanConsumoInsumos prepararYLockearInsumosVenta(Connection c, List<DetalleVenta> ds) { return new PlanConsumoInsumos(Map.of(), List.of(), Map.of()); }
                @Override public void descontarStockYRegistrarKardex(Connection c, int venta, long usuario, PlanConsumoInsumos plan) { }
                @Override public void revertirOMermarInsumosPorAnulacion(Connection c, int venta, boolean preparado, long usuario, String motivo, Map<Long, BigDecimal> insumos) { platoPreparado = preparado; }
                @Override public void revertirInsumosPorDevolucionParcial(Connection c, int venta, long usuario, String motivo, Map<Long, BigDecimal> insumos) { insumosDevolucionReversiones++; }
            };
            this.useCase = new DefaultAnularDevolverVentaUseCase(tx, ventaDAO, cajaDAO, autorizacion,
                    supervisorService, cierre, fidelizacion, auditoria, validator, kardex);
        }
    }

    private static final class DbState {
        private String estado;
        private final int idCliente;
        private DbState(String estado, int idCliente) { this.estado = estado; this.idCliente = idCliente; }
    }

    private static Connection connection(DbState db) {
        return proxy(Connection.class, (p, m, a) -> {
            if (m.getName().equals("prepareStatement")) {
                String sql = (String) a[0];
                if (SQL_LOCK.equals(sql)) return prepared(sql, db);
                if (SQL_UPDATE.equals(sql)) return prepared(sql, db);
                if (SQL_DETALLES.equals(sql)) return prepared(sql, db);
                throw new AssertionError("SQL inesperado: " + sql);
            }
            return defaultValue(m.getReturnType());
        });
    }

    private static PreparedStatement prepared(String sql, DbState db) {
        return proxy(PreparedStatement.class, (p, m, a) -> {
            if (m.getName().equals("executeQuery")) {
                if (SQL_DETALLES.equals(sql)) return resultSet(List.of());
                Map<String, Object> row = new HashMap<>();
                row.put("id_caja", 3); row.put("id_cliente", db.idCliente); row.put("total", new BigDecimal("80.00"));
                row.put("metodo_pago", "EFECTIVO"); row.put("estado", db.estado);
                row.put("fecha_hora", Timestamp.valueOf(LocalDateTime.now()));
                return resultSet(List.of(row));
            }
            if (m.getName().equals("executeUpdate")) { db.estado = "ANULADA"; return 1; }
            return defaultValue(m.getReturnType());
        });
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] index = {-1};
        boolean[] wasNull = {false};
        InvocationHandler handler = (p, m, a) -> {
            if (m.getName().equals("next")) return ++index[0] < rows.size();
            if (m.getName().equals("wasNull")) return wasNull[0];
            if (m.getName().equals("close")) return null;
            if (index[0] < 0 || index[0] >= rows.size()) return defaultValue(m.getReturnType());
            Object value = rows.get(index[0]).get(a[0]);
            wasNull[0] = value == null;
            return value;
        };
        return proxy(ResultSet.class, handler);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
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
}
