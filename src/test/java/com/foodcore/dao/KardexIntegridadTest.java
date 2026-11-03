package com.foodcore.dao;

import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración de integridad contable y de inventario en {@code kardex_movimiento}
 * según SPEC-FOODCORE-v1.0 (§8.2, §15.2; REMEDIATION R-02.3).
 */
class KardexIntegridadTest {

    private InsumoDAOImpl insumoDAO;
    private VentaServiceImpl ventaService;
    private CajaServiceImpl cajaService;
    private JdbcTransactionManager tx;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        tx = new JdbcTransactionManager();
        insumoDAO = new InsumoDAOImpl();
        CajaDAOImpl cajaDAO = new CajaDAOImpl();
        ventaService = new VentaServiceImpl(tx, new VentaDAOImpl(), cajaDAO, insumoDAO, new PedidoDAOImpl());
        cajaService = new CajaServiceImpl(tx, cajaDAO);
    }

    @Test
    @DisplayName("R-02.3: Invariante saldo_posterior == stock_actual_disponible y cantidad firmada tras venta, entrada, merma, ajuste positivo y ajuste negativo")
    void debeMantenerInvarianteKardexYCantidadesFirmadasEnLos5Escenarios() throws Exception {
        cajaService.abrirTurnoCaja(1, "Caja Kardex", new BigDecimal("200.00"));

        long idInsumo;
        int idProducto;
        try (Connection conn = ConexionBD.getConnection()) {
            idInsumo = insumoDAO.crear(conn, new Insumo(
                0L, "Lomo Fino Seleccionado", "KG",
                new BigDecimal("10.0000"), new BigDecimal("2.0000"), new BigDecimal("42.00")));

            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('Fondos', 'Platos')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.executeUpdate();
                int idCat;
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
                try (PreparedStatement psProd = conn.prepareStatement(
                        "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, 'Lomo Saltado', 45.00, 30)",
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
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 0.2500)")) {
                psRec.setInt(1, idProducto);
                psRec.setLong(2, idInsumo);
                psRec.executeUpdate();
            }
        }

        // 1. Escenario VENTA (SALIDA de 2 unidades * 0.2500 = -0.5000 -> stock 9.5000)
        Venta v = new Venta();
        v.setIdUsuario(1);
        v.setMetodoPago("EFECTIVO");
        v.setMontoRecibido(new BigDecimal("100.00"));
        v.setVuelto(new BigDecimal("10.00"));
        v.setIdempotencyKey(UUID.randomUUID().toString());
        DetalleVenta dv = new DetalleVenta(0, 0, idProducto, "Lomo Saltado", 2,
            new BigDecimal("45.00"), new BigDecimal("90.00"));
        ventaService.procesarCobroVenta(v, List.of(dv), null);
        verificarInvarianteUltimoKardex(idInsumo, "SALIDA", new BigDecimal("-0.5000"), new BigDecimal("9.5000"));

        // 2. Escenario ENTRADA (+5.0000 -> stock 14.5000)
        tx.executeChecked(conn -> insumoDAO.registrarMovimientoKardexAtomico(
            conn, idInsumo, "ENTRADA", new BigDecimal("5.0000"), 1L, "Compra Factura F001-120"));
        verificarInvarianteUltimoKardex(idInsumo, "ENTRADA", new BigDecimal("5.0000"), new BigDecimal("14.5000"));

        // 3. Escenario MERMA (-1.2500 -> stock 13.2500)
        tx.executeChecked(conn -> insumoDAO.registrarMovimientoKardexAtomico(
            conn, idInsumo, "MERMA", new BigDecimal("1.2500"), 1L, "Merma por corte de limpieza"));
        verificarInvarianteUltimoKardex(idInsumo, "MERMA", new BigDecimal("-1.2500"), new BigDecimal("13.2500"));

        // 4. Escenario AJUSTE POSITIVO (+0.7500 -> stock 14.0000)
        tx.executeChecked(conn -> insumoDAO.registrarMovimientoKardexAtomico(
            conn, idInsumo, "AJUSTE_POS", new BigDecimal("0.7500"), 1L, "Ajuste positivo por conteo físico"));
        verificarInvarianteUltimoKardex(idInsumo, "AJUSTE_POS", new BigDecimal("0.7500"), new BigDecimal("14.0000"));

        // 5. Escenario AJUSTE NEGATIVO (-2.0000 -> stock 12.0000)
        tx.executeChecked(conn -> insumoDAO.registrarMovimientoKardexAtomico(
            conn, idInsumo, "AJUSTE_NEG", new BigDecimal("2.0000"), 1L, "Ajuste negativo auditoría"));
        verificarInvarianteUltimoKardex(idInsumo, "AJUSTE_NEG", new BigDecimal("-2.0000"), new BigDecimal("12.0000"));
    }

    private void verificarInvarianteUltimoKardex(long idInsumo,
                                                 String tipoEsperado,
                                                 BigDecimal cantidadFirmadaEsperada,
                                                 BigDecimal saldoPosteriorEsperado) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo insumo = insumoDAO.obtenerPorId(conn, idInsumo);
            List<KardexMovimiento> historial = insumoDAO.listarKardexPorInsumo(conn, idInsumo);
            assertTrue(!historial.isEmpty(), "El historial kardex no debe estar vacío");

            KardexMovimiento ultimo = historial.get(historial.size() - 1);
            assertEquals(tipoEsperado, ultimo.getTipoMovimiento());
            assertEquals(cantidadFirmadaEsperada, ultimo.getCantidad(),
                "La cantidad debe estar firmada según el tipo de movimiento (§8.2)");
            assertEquals(saldoPosteriorEsperado, ultimo.getSaldoPosterior(),
                "saldo_posterior debe coincidir con el cálculo exacto");
            assertEquals(ultimo.getSaldoPosterior(), insumo.getStockActualDisponible(),
                "Invariante R-02.3: kardex_movimiento.saldo_posterior == insumo.stock_actual_disponible");
            assertEquals(ultimo.getSaldoAnterior().add(ultimo.getCantidad()), ultimo.getSaldoPosterior(),
                "Invariante §8.2: saldo_posterior == saldo_anterior + cantidad");
        }
    }
}
