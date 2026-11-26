package com.foodcore.perf;

import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.Pedido;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simulación rápida de Soak Test (Sub-Fase G - Ítem #58):
 * ejecuta de forma continua el ciclo Pedido -> KDS -> Caja -> Kárdex durante hasta 5 minutos
 * (simulando una jornada operativa de 8 horas) para descartar fugas de memoria o conexiones JDBC.
 */
public final class SoakTestSimulado {

    private static final Logger LOGGER = Logger.getLogger(SoakTestSimulado.class.getName());
    private static final Duration DURACION_DEFAULT_5_MIN = Duration.ofMinutes(5);

    /** Métricas resultantes de la ejecución continua de Soak Test. */
    public record ResultadoSoak(
            int iteracionesCompletadas,
            int errores,
            long duracionMs,
            double latenciaPromedioMs,
            long heapInicialMb,
            long heapFinalMb,
            long deltaHeapMb) {
    }

    private SoakTestSimulado() {
    }

    /**
     * Punto de entrada CLI para ejecutar localmente el Soak Test completo de 5 minutos.
     */
    public static void main(String[] args) throws Exception {
        int minutos = args != null && args.length > 0 ? Math.max(1, Integer.parseInt(args[0])) : 5;
        ResultadoSoak res = ejecutarSimulacionSoak(Duration.ofMinutes(minutos), Integer.MAX_VALUE);
        LOGGER.info(() -> String.format(
                Locale.ROOT,
                "Soak Test finalizado: iteraciones=%d, errores=%d, duracionMs=%d, latenciaPromMs=%.2f, "
                        + "heapInicialMB=%d, heapFinalMB=%d, deltaHeapMB=%d",
                res.iteracionesCompletadas(), res.errores(), res.duracionMs(), res.latenciaPromedioMs(),
                res.heapInicialMb(), res.heapFinalMb(), res.deltaHeapMb()));
    }

    /**
     * Ejecuta el bucle continuo de ventas por un máximo de {@code duracionMaxima} o {@code maxIteraciones}.
     */
    public static ResultadoSoak ejecutarSimulacionSoak(Duration duracionMaxima, int maxIteraciones) throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();

        JdbcTransactionManager tx = new JdbcTransactionManager();
        PedidoDAOImpl pedidoDAO = new PedidoDAOImpl();
        InsumoDAOImpl insumoDAO = new InsumoDAOImpl();
        CajaDAOImpl cajaDAO = new CajaDAOImpl();
        PedidoService pedidoService = new PedidoServiceImpl(tx, pedidoDAO, new AutorizacionService());
        VentaServiceImpl ventaService = new VentaServiceImpl(tx, new VentaDAOImpl(), cajaDAO, insumoDAO, pedidoDAO);
        CajaServiceImpl cajaService = new CajaServiceImpl(tx, cajaDAO);

        int idCaja = cajaService.abrirTurnoCaja(1, "Caja Soak Test", new BigDecimal("500.00"));
        int idProducto = sembrarProductoConStockAlto(insumoDAO);

        Duration limiteDuracion = duracionMaxima != null && !duracionMaxima.isNegative()
                ? duracionMaxima
                : DURACION_DEFAULT_5_MIN;
        int limiteIter = maxIteraciones > 0 ? maxIteraciones : Integer.MAX_VALUE;

        long heapInicialMb = medirHeapUsadoMb();
        long inicioNs = System.nanoTime();
        long finNs = inicioNs + limiteDuracion.toNanos();

        int completadas = 0;
        int errores = 0;
        long sumaLatenciaNs = 0L;

        for (int i = 1; i <= limiteIter && System.nanoTime() < finNs; i++) {
            long iterInicio = System.nanoTime();
            try {
                String mesa = String.format("%02d", ((i - 1) % 12) + 1);
                Pedido pedido = new Pedido(0, 1, null, mesa, "REGISTRADO", new BigDecimal("29.50"));
                DetallePedido detPed = new DetallePedido(
                        0, 0, idProducto, "Combo Soak G3", 1,
                        new BigDecimal("29.50"), new BigDecimal("29.50"), "Soak #" + i);
                Pedido reg = pedidoService.registrarPedidoYEnviarComanda(pedido, List.of(detPed));
                int idDetPed = reg.getDetalles().get(0).getIdDetallePedido();

                try (Connection conn = ConexionBD.getConnection()) {
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetPed, "EN_PREPARACION");
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetPed, "LISTO");
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetPed, "ENTREGADO");
                    pedidoDAO.actualizarEstadoPedido(conn, reg.getIdPedido(), "LISTO");
                }

                Venta venta = new Venta(
                        0, reg.getIdPedido(), idCaja, 1, null,
                        new BigDecimal("25.00"), new BigDecimal("4.50"), new BigDecimal("29.50"),
                        "EFECTIVO", new BigDecimal("30.00"), new BigDecimal("0.50"));
                venta.setIdempotencyKey(UUID.randomUUID().toString());
                DetalleVenta detVenta = new DetalleVenta(
                        0, 0, idProducto, "Combo Soak G3", 1,
                        new BigDecimal("29.50"), new BigDecimal("29.50"));
                ComprobantePago comp = new ComprobantePago(
                        0, 0, "TICKET", "SOAK", String.format("%06d", i), new BigDecimal("29.50"));

                ventaService.procesarCobroVenta(venta, List.of(detVenta), comp);
                completadas++;
            } catch (Exception ex) {
                errores++;
                LOGGER.log(Level.WARNING, "Error en iteración de Soak #" + i, ex);
            } finally {
                sumaLatenciaNs += (System.nanoTime() - iterInicio);
            }
        }

        long duracionMs = Math.max(1L, (System.nanoTime() - inicioNs) / 1_000_000L);
        long heapFinalMb = medirHeapUsadoMb();
        long totalOps = Math.max(1L, (long) completadas + errores);
        double latenciaPromMs = (sumaLatenciaNs / 1_000_000.0) / totalOps;

        return new ResultadoSoak(
                completadas,
                errores,
                duracionMs,
                latenciaPromMs,
                heapInicialMb,
                heapFinalMb,
                heapFinalMb - heapInicialMb);
    }

    private static int sembrarProductoConStockAlto(InsumoDAOImpl insumoDAO) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            long idInsumo = insumoDAO.crear(conn, new Insumo(
                    0L, "Insumo Soak " + UUID.randomUUID().toString().substring(0, 6), "UNIDAD",
                    new BigDecimal("50000.0000"), new BigDecimal("10.0000"), new BigDecimal("4.00")));

            int idCat;
            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES (?, 'Soak G3')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.setString(1, "Cat Soak " + UUID.randomUUID().toString().substring(0, 5));
                psCat.executeUpdate();
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
            }

            int idProd;
            try (PreparedStatement psProd = conn.prepareStatement(
                    "INSERT INTO producto (id_categoria, codigo, nombre, precio_venta, stock_disponible, disponible) "
                            + "VALUES (?, ?, 'Combo Soak G3', 29.50, 50000, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psProd.setInt(1, idCat);
                psProd.setString(2, "SOAK-" + UUID.randomUUID().toString().substring(0, 5));
                psProd.executeUpdate();
                try (ResultSet rsP = psProd.getGeneratedKeys()) {
                    rsP.next();
                    idProd = rsP.getInt(1);
                }
            }

            try (PreparedStatement psRec = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 1.0000)")) {
                psRec.setInt(1, idProd);
                psRec.setLong(2, idInsumo);
                psRec.executeUpdate();
            }
            return idProd;
        }
    }

    private static long medirHeapUsadoMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
    }
}
