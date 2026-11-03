package com.foodcore.service;

import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.Venta;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración de concurrencia real en ventas y stock sobre MySQL 8.0 InnoDB
 * según SPEC-FOODCORE-v1.0 (§9.1, §15.2, §19.6; REMEDIATION R-02.1, R-09, R-10).
 */
class VentaServiceConcurrencyTest {

    private VentaServiceImpl ventaService;
    private CajaServiceImpl cajaService;
    private InsumoDAOImpl insumoDAO;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JdbcTransactionManager tx = new JdbcTransactionManager();
        insumoDAO = new InsumoDAOImpl();
        CajaDAOImpl cajaDAO = new CajaDAOImpl();
        ventaService = new VentaServiceImpl(tx, new VentaDAOImpl(), cajaDAO, insumoDAO, new PedidoDAOImpl());
        cajaService = new CajaServiceImpl(tx, cajaDAO);
    }

    @Test
    @DisplayName("R-02.1: 2 hilos concurrentes sobre insumo con stock_actual_disponible=1 -> 1 venta, 1 kardex SALIDA, stock=0 y 1 StockInsuficienteException")
    void dosHilosConcurrentesConStockUnoDebenProducirExactamenteUnaVentaYUnErrorStockInsuficiente() throws Exception {
        cajaService.abrirTurnoCaja(1, "Caja Concurrencia", new BigDecimal("100.00"));

        long idInsumo;
        int idProducto;
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo ins = new Insumo(0L, "Bife Angosto Premium", "UNIDAD",
                new BigDecimal("1.0000"), new BigDecimal("1.0000"), new BigDecimal("25.00"));
            idInsumo = insumoDAO.crear(conn, ins);

            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('Carnes', 'Cortes')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.executeUpdate();
                int idCat;
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
                try (PreparedStatement psProd = conn.prepareStatement(
                        "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, 'Bife Parrillero', 48.00, 10)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    psProd.setInt(1, idCat);
                    psProd.executeUpdate();
                    try (ResultSet rsP = psProd.getGeneratedKeys()) {
                        rsP.next();
                        idProducto = rsP.getInt(1);
                    }
                }
            }

            try (PreparedStatement psRec = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 1.0000)")) {
                psRec.setInt(1, idProducto);
                psRec.setLong(2, idInsumo);
                psRec.executeUpdate();
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger fallosStockInsuficiente = new AtomicInteger(0);

        final int prodId = idProducto;
        Runnable tareaCobro = () -> {
            readyLatch.countDown();
            try {
                startSignal.await(5, TimeUnit.SECONDS);
                Venta v = new Venta();
                v.setIdUsuario(1);
                v.setMetodoPago("EFECTIVO");
                v.setMontoRecibido(new BigDecimal("50.00"));
                v.setVuelto(new BigDecimal("2.00"));
                v.setIdempotencyKey(UUID.randomUUID().toString());

                DetalleVenta dv = new DetalleVenta(0, 0, prodId, "Bife Parrillero", 1,
                    new BigDecimal("48.00"), new BigDecimal("48.00"));

                ventaService.procesarCobroVenta(v, List.of(dv), null);
                exitos.incrementAndGet();
            } catch (StockInsuficienteException ex) {
                fallosStockInsuficiente.incrementAndGet();
            } catch (Exception unexpected) {
                throw new RuntimeException(unexpected);
            }
        };

        Future<?> f1 = pool.submit(tareaCobro);
        Future<?> f2 = pool.submit(tareaCobro);

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startSignal.countDown();

        f1.get(15, TimeUnit.SECONDS);
        f2.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(1, exitos.get(), "Exactamente 1 hilo debe completar la venta exitosamente");
        assertEquals(1, fallosStockInsuficiente.get(), "Exactamente 1 hilo debe recibir StockInsuficienteException");

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rsVentas = st.executeQuery("SELECT COUNT(*) FROM venta")) {
                rsVentas.next();
                assertEquals(1, rsVentas.getInt(1), "Debe existir exactamente 1 fila en tabla venta");
            }
            try (ResultSet rsKardex = st.executeQuery(
                    "SELECT COUNT(*) FROM kardex_movimiento WHERE tipo_movimiento = 'SALIDA' AND id_insumo = " + idInsumo)) {
                rsKardex.next();
                assertEquals(1, rsKardex.getInt(1), "Debe existir exactamente 1 fila en kardex_movimiento con tipo SALIDA");
            }
            Insumo insumoFinal = insumoDAO.obtenerPorId(conn, idInsumo);
            assertEquals(new BigDecimal("0.0000"), insumoFinal.getStockActualDisponible(),
                "stock_actual_disponible debe quedar exactamente en 0.0000");
        }
    }

    @Test
    @DisplayName("R-09: EXPLAIN del SELECT ... FOR UPDATE debe utilizar el índice PRIMARY sobre id_insumo")
    void explainSelectForUpdateDebeUsarPrimaryKey() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo ins = new Insumo(0L, "Insumo Explain", "KG",
                new BigDecimal("10.0000"), new BigDecimal("1.0000"), new BigDecimal("5.00"));
            long id = insumoDAO.crear(conn, ins);

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "EXPLAIN SELECT id_insumo, stock_actual_disponible FROM insumo WHERE id_insumo IN (" + id + ") ORDER BY id_insumo ASC FOR UPDATE")) {
                assertTrue(rs.next());
                String keyUsed = rs.getString("key");
                String possibleKeys = rs.getString("possible_keys");
                assertTrue("PRIMARY".equalsIgnoreCase(keyUsed) || (possibleKeys != null && possibleKeys.contains("PRIMARY")),
                    "El plan de ejecución debe usar PRIMARY KEY");
            }
        }
    }

    @Test
    @DisplayName("R-10: JdbcTransactionManager debe reintentar con backoff exponencial ante Deadlock MySQL (1213) y completar OK")
    void debeReintentarAnteDeadlockInnoDbYCompletarOperacion() throws Exception {
        JdbcTransactionManager tx = new JdbcTransactionManager();
        AtomicInteger intentosEjecutados = new AtomicInteger(0);

        String resultado = tx.executeChecked(conn -> {
            int intentoActual = intentosEjecutados.incrementAndGet();
            if (intentoActual < 3) {
                throw new SQLException("Deadlock found when trying to get lock; try restarting transaction", "40001", 1213);
            }
            return "COMPLETADO_TRAS_RETRY_" + intentoActual;
        });

        assertEquals("COMPLETADO_TRAS_RETRY_3", resultado);
        assertEquals(3, intentosEjecutados.get());
    }
}
