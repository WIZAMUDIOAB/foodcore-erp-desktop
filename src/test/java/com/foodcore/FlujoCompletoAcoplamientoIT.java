package com.foodcore;

import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.EstadoResultadosReporte;
import com.foodcore.model.Insumo;
import com.foodcore.model.Pedido;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.FinanzasBiService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
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
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke Test de Acoplamiento End-to-End (Sub-Fase G - Ítem #55):
 * Mozo/POS -> KDS -> Caja -> Kárdex -> P&L sobre MySQL 8.0 InnoDB real.
 */
class FlujoCompletoAcoplamientoIT {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private PedidoService pedidoService;
    private PedidoDAOImpl pedidoDAO;
    private VentaServiceImpl ventaService;
    private CajaServiceImpl cajaService;
    private InsumoDAOImpl insumoDAO;
    private FinanzasBiService finanzasBiService;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        JdbcTransactionManager tx = new JdbcTransactionManager();
        pedidoDAO = new PedidoDAOImpl();
        insumoDAO = new InsumoDAOImpl();
        CajaDAOImpl cajaDAO = new CajaDAOImpl();
        pedidoService = new PedidoService(tx, pedidoDAO, new AutorizacionService());
        ventaService = new VentaServiceImpl(tx, new VentaDAOImpl(), cajaDAO, insumoDAO, pedidoDAO);
        cajaService = new CajaServiceImpl(tx, cajaDAO);
        finanzasBiService = new FinanzasBiService();
    }

    @Test
    @DisplayName("G1: Flujo acoplado Mozo -> KDS -> Caja -> Kárdex -> Estado de Resultados (P&L)")
    void smokeTestFlujoCompletoMozoKdsCajaKardexYPnl() throws Exception {
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja Smoke G1", new BigDecimal("200.00"));
        assertTrue(idCaja > 0, "El turno de caja debe abrirse correctamente");

        long idInsumoPan;
        long idInsumoCarne;
        int idProducto;

        try (Connection conn = ConexionBD.getConnection()) {
            idInsumoPan = insumoDAO.crear(conn, new Insumo(
                    0L, "Pan Brioche G1", "UNIDAD",
                    new BigDecimal("25.0000"), new BigDecimal("5.0000"), new BigDecimal("1.50")));
            idInsumoCarne = insumoDAO.crear(conn, new Insumo(
                    0L, "Medallón Res G1", "UNIDAD",
                    new BigDecimal("25.0000"), new BigDecimal("5.0000"), new BigDecimal("6.50")));

            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('Gourmet G1', 'Smoke E2E')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.executeUpdate();
                int idCategoria;
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCategoria = rs.getInt(1);
                }

                try (PreparedStatement psProd = conn.prepareStatement(
                        "INSERT INTO producto (id_categoria, codigo, nombre, precio_venta, stock_disponible, disponible) "
                                + "VALUES (?, 'SMK-G1', 'Burger Smoke G1', 35.40, 50, 1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    psProd.setInt(1, idCategoria);
                    psProd.executeUpdate();
                    try (ResultSet rsProd = psProd.getGeneratedKeys()) {
                        rsProd.next();
                        idProducto = rsProd.getInt(1);
                    }
                }
            }

            try (PreparedStatement psReceta = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 1.0000)")) {
                psReceta.setInt(1, idProducto);
                psReceta.setLong(2, idInsumoPan);
                psReceta.executeUpdate();

                psReceta.setInt(1, idProducto);
                psReceta.setLong(2, idInsumoCarne);
                psReceta.executeUpdate();
            }
        }

        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        EstadoResultadosReporte pnlAntes = finanzasBiService.generarEstadoResultados(
                hoy.minusDays(1), hoy.plusDays(1));

        // 1. Mozo / POS crea el pedido para Mesa 05 (2 unidades = S/ 70.80)
        Pedido nuevoPedido = new Pedido(0, 1, null, "05", "REGISTRADO", new BigDecimal("70.80"));
        DetallePedido detallePedido = new DetallePedido(
                0, 0, idProducto, "Burger Smoke G1", 2,
                new BigDecimal("35.40"), new BigDecimal("70.80"), "Sin cebolla");
        Pedido pedidoRegistrado = pedidoService.registrarPedidoYEnviarComanda(
                nuevoPedido, List.of(detallePedido));
        assertTrue(pedidoRegistrado.getIdPedido() > 0, "El pedido debe persistirse con ID válido");
        assertEquals("REGISTRADO", pedidoRegistrado.getEstado());
        int idDetallePedido = pedidoRegistrado.getDetalles().get(0).getIdDetallePedido();
        assertTrue(idDetallePedido > 0);

        // 2. KDS recibe comanda en estado PENDIENTE y la despacha a EN_PREPARACION -> LISTO -> ENTREGADO
        try (Connection conn = ConexionBD.getConnection()) {
            List<DetallePedido> itemsKds = pedidoDAO.listarItemsPendientesCocina(conn);
            DetallePedido itemKds = itemsKds.stream()
                    .filter(d -> d.getIdDetallePedido() == idDetallePedido)
                    .findFirst()
                    .orElse(null);
            assertTrue(itemKds != null && "PENDIENTE".equalsIgnoreCase(itemKds.getEstadoCocina()),
                    "KDS debe recibir el ítem en estado PENDIENTE");

            pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "EN_PREPARACION");
            pedidoDAO.actualizarEstadoPedido(conn, pedidoRegistrado.getIdPedido(), "EN_PREPARACION");

            pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "LISTO");
            pedidoDAO.actualizarEstadoPedido(conn, pedidoRegistrado.getIdPedido(), "LISTO");

            Pedido pedidoListo = pedidoDAO.obtenerPorId(conn, pedidoRegistrado.getIdPedido()).orElseThrow();
            assertEquals("LISTO", pedidoListo.getEstado(), "El pedido debe figurar LISTO en KDS");

            pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetallePedido, "ENTREGADO");
            assertFalse(pedidoDAO.tieneItemsPendientesCocina(conn, pedidoRegistrado.getIdPedido()),
                    "Al entregar el plato no deben quedar ítems pendientes en cocina");
        }

        // 3. Caja cobra el pedido y genera la venta
        Venta venta = new Venta(
                0, pedidoRegistrado.getIdPedido(), idCaja, 1, null,
                new BigDecimal("60.00"), new BigDecimal("10.80"), new BigDecimal("70.80"),
                "EFECTIVO", new BigDecimal("80.00"), new BigDecimal("9.20"));
        venta.setIdempotencyKey(UUID.randomUUID().toString());

        DetalleVenta detVenta = new DetalleVenta(
                0, 0, idProducto, "Burger Smoke G1", 2,
                new BigDecimal("35.40"), new BigDecimal("70.80"));
        ComprobantePago comprobante = new ComprobantePago(
                0, 0, "BOLETA", "B001", "000101", new BigDecimal("70.80"));

        Venta ventaProcesada = ventaService.procesarCobroVenta(venta, List.of(detVenta), comprobante);
        assertTrue(ventaProcesada.getIdVenta() > 0, "La venta debe generarse en Caja");

        // 4. Kárdex descontó 2.0000 unidades de cada insumo y registró movimientos SALIDA
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo panDespues = insumoDAO.obtenerPorId(conn, idInsumoPan);
            Insumo carneDespues = insumoDAO.obtenerPorId(conn, idInsumoCarne);
            assertEquals(0, new BigDecimal("23.0000").compareTo(panDespues.getStockActualDisponible()),
                    "Kárdex debe descontar 2 unidades de Pan Brioche G1 (25 -> 23)");
            assertEquals(0, new BigDecimal("23.0000").compareTo(carneDespues.getStockActualDisponible()),
                    "Kárdex debe descontar 2 unidades de Medallón Res G1 (25 -> 23)");

            try (PreparedStatement psKardex = conn.prepareStatement(
                    "SELECT COUNT(*) FROM kardex_movimiento "
                            + "WHERE id_insumo IN (?, ?) AND tipo_movimiento = 'SALIDA' "
                            + "AND referencia_tipo = 'VENTA' AND referencia_id = ? AND cantidad = -2.0000")) {
                psKardex.setLong(1, idInsumoPan);
                psKardex.setLong(2, idInsumoCarne);
                psKardex.setInt(3, ventaProcesada.getIdVenta());
                try (ResultSet rsK = psKardex.executeQuery()) {
                    assertTrue(rsK.next());
                    assertEquals(2, rsK.getInt(1),
                            "Kárdex debe registrar movimiento SALIDA firmado (-2.0000) por cada insumo de la receta");
                }
            }
        }

        // 5. P&L (Estado de Resultados) suma exactamente los S/ 70.80 de la venta
        EstadoResultadosReporte pnlDespues = finanzasBiService.generarEstadoResultados(
                hoy.minusDays(1), hoy.plusDays(1));
        assertEquals(pnlAntes.cantidadVentas() + 1, pnlDespues.cantidadVentas(),
                "El P&L debe reflejar +1 venta completada");
        BigDecimal deltaIngresos = pnlDespues.ingresosBrutos().subtract(pnlAntes.ingresosBrutos());
        assertEquals(0, new BigDecimal("70.80").compareTo(deltaIngresos),
                "El P&L debe sumar exactamente +70.80 en ingresos brutos");
    }
}
