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
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Suite de integración de rollback transaccional ACID en ventas con múltiples insumos
 * según SPEC-FOODCORE-v1.0 (§7.1, §9.1, §15.2; REMEDIATION R-02.2, R-11).
 */
class VentaServiceRollbackTest {

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
    @DisplayName("R-02.2 & R-11: Venta con 2 insumos donde el segundo carece de stock -> propaga StockInsuficienteException directa y revierte 100% de la transacción")
    void debeRevertirTodoCuandoElSegundoInsumoFallaPorStockInsuficiente() throws Exception {
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja Rollback", new BigDecimal("100.00"));

        long idInsumo1;
        long idInsumo2;
        int idProducto;

        try (Connection conn = ConexionBD.getConnection()) {
            // Primer insumo con stock suficiente (10.0000)
            idInsumo1 = insumoDAO.crear(conn, new Insumo(
                0L, "Pan Brioche Artesanal", "UNIDAD",
                new BigDecimal("10.0000"), new BigDecimal("2.0000"), new BigDecimal("1.50")));

            // Segundo insumo con stock insuficiente (0.5000 < 2.0000 requerido)
            idInsumo2 = insumoDAO.crear(conn, new Insumo(
                0L, "Queso Cheddar Fundido", "UNIDAD",
                new BigDecimal("0.5000"), new BigDecimal("2.0000"), new BigDecimal("2.00")));

            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('Hamburguesas', 'Gourmet')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.executeUpdate();
                int idCat;
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
                try (PreparedStatement psProd = conn.prepareStatement(
                        "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, 'Cheese Burger Doble', 32.00, 20)",
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
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, ?)")) {
                psRec.setInt(1, idProducto);
                psRec.setLong(2, idInsumo1);
                psRec.setBigDecimal(3, new BigDecimal("1.0000"));
                psRec.executeUpdate();

                psRec.setInt(1, idProducto);
                psRec.setLong(2, idInsumo2);
                psRec.setBigDecimal(3, new BigDecimal("2.0000"));
                psRec.executeUpdate();
            }
        }

        Venta venta = new Venta();
        venta.setIdUsuario(1);
        venta.setMetodoPago("EFECTIVO");
        venta.setMontoRecibido(new BigDecimal("40.00"));
        venta.setVuelto(new BigDecimal("8.00"));
        venta.setIdempotencyKey(UUID.randomUUID().toString());

        DetalleVenta det = new DetalleVenta(0, 0, idProducto, "Cheese Burger Doble", 1,
            new BigDecimal("32.00"), new BigDecimal("32.00"));

        // R-11: Debe propagar directamente StockInsuficienteException (NO SQLException)
        StockInsuficienteException ex = assertThrows(
            StockInsuficienteException.class,
            () -> ventaService.procesarCobroVenta(venta, List.of(det), null)
        );
        assertEquals(idInsumo2, ex.getIdInsumo());

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            // 1. 0 ventas en venta
            try (ResultSet rsV = st.executeQuery("SELECT COUNT(*) FROM venta")) {
                rsV.next();
                assertEquals(0, rsV.getInt(1), "No debe quedar ninguna venta registrada tras el rollback");
            }
            // 2. 0 filas en kardex_movimiento
            try (ResultSet rsK = st.executeQuery("SELECT COUNT(*) FROM kardex_movimiento")) {
                rsK.next();
                assertEquals(0, rsK.getInt(1), "No debe quedar ningún registro en kardex_movimiento tras el rollback");
            }
            // 3. Ambos insumos con stock intacto (especialmente el primero)
            Insumo ins1Despues = insumoDAO.obtenerPorId(conn, idInsumo1);
            Insumo ins2Despues = insumoDAO.obtenerPorId(conn, idInsumo2);
            assertEquals(new BigDecimal("10.0000"), ins1Despues.getStockActualDisponible(),
                "El stock del primer insumo debe permanecer intacto (10.0000)");
            assertEquals(new BigDecimal("0.5000"), ins2Despues.getStockActualDisponible(),
                "El stock del segundo insumo debe permanecer intacto (0.5000)");

            // 4. movimiento_caja tipo VENTA no registrado
            try (ResultSet rsMc = st.executeQuery(
                    "SELECT COUNT(*) FROM movimiento_caja WHERE id_caja = " + idCaja + " AND tipo_movimiento = 'VENTA'")) {
                rsMc.next();
                assertEquals(0, rsMc.getInt(1), "No debe registrarse ningún movimiento_caja de tipo VENTA");
            }
        }
    }
}
