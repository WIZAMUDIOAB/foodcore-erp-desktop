package com.foodcore.service;

import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.Caja;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de integración de ciclo completo de Caja, Arqueo Físico y Cierre Z
 * según SPEC-FOODCORE-v1.0 (§6.4, §8.3, §15.2; REMEDIATION R-02.4, B4).
 */
class CajaServiceTest {

    private CajaServiceImpl cajaService;
    private VentaServiceImpl ventaService;
    private InsumoDAOImpl insumoDAO;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JdbcTransactionManager tx = new JdbcTransactionManager();
        CajaDAOImpl cajaDAO = new CajaDAOImpl();
        insumoDAO = new InsumoDAOImpl();
        cajaService = new CajaServiceImpl(tx, cajaDAO);
        ventaService = new VentaServiceImpl(tx, new VentaDAOImpl(), cajaDAO, insumoDAO, new PedidoDAOImpl());
    }

    private int crearProductoConInsumo(String nombreProd, BigDecimal precioVenta) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            long idInsumo = insumoDAO.crear(conn, new Insumo(
                0L, "Insumo Test " + UUID.randomUUID().toString().substring(0, 8), "UND",
                new BigDecimal("500.0000"), new BigDecimal("5.0000"), new BigDecimal("10.00")));

            int idCat;
            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.setString(1, "Cat-" + UUID.randomUUID().toString().substring(0, 6));
                psCat.setString(2, "Categoria Test");
                psCat.executeUpdate();
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
            }

            int idProducto;
            try (PreparedStatement psProd = conn.prepareStatement(
                    "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, ?, ?, 100)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psProd.setInt(1, idCat);
                psProd.setString(2, nombreProd);
                psProd.setBigDecimal(3, precioVenta);
                psProd.executeUpdate();
                try (ResultSet rsP = psProd.getGeneratedKeys()) {
                    rsP.next();
                    idProducto = rsP.getInt(1);
                }
            }

            try (PreparedStatement psRec = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 1.0000)")) {
                psRec.setInt(1, idProducto);
                psRec.setLong(2, idInsumo);
                psRec.executeUpdate();
            }

            return idProducto;
        }
    }

    @Test
    @DisplayName("B4.1: testAperturaTurno_CreaEstadoABIERTO - Apertura de turno registra estado ABIERTO y saldo inicial")
    void testAperturaTurno_CreaEstadoABIERTO() throws Exception {
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja Principal POS-01", new BigDecimal("150.00"));
        assertTrue(idCaja > 0, "Debe retornar un id_turno válido autoincremental");

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rsTurno = st.executeQuery("SELECT estado, saldo_inicial, abierto_en, cerrado_en FROM turno_caja WHERE id_turno = " + idCaja)) {
            assertTrue(rsTurno.next(), "Debe existir fila en turno_caja");
            assertEquals("ABIERTO", rsTurno.getString("estado"));
            assertEquals(new BigDecimal("150.00"), rsTurno.getBigDecimal("saldo_inicial"));
            assertNotNull(rsTurno.getTimestamp("abierto_en"));
            assertNull(rsTurno.getTimestamp("cerrado_en"));
        }

        Optional<Caja> activa = cajaService.obtenerCajaActiva();
        assertTrue(activa.isPresent(), "La caja debe figurar como activa");
        assertTrue("ABIERTA".equalsIgnoreCase(activa.get().getEstado()) || "ABIERTO".equalsIgnoreCase(activa.get().getEstado()));
    }

    @Test
    @DisplayName("B4.2: testCobroVenta_GeneraMovimientoCajaTipoVENTA - Procesar venta genera movimiento de caja")
    void testCobroVenta_GeneraMovimientoCajaTipoVENTA() throws Exception {
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja POS-02", new BigDecimal("100.00"));
        int idProd = crearProductoConInsumo("Hamburguesa Royal", new BigDecimal("25.00"));

        Venta v = new Venta();
        v.setIdUsuario(1);
        v.setMetodoPago("EFECTIVO");
        v.setMontoRecibido(new BigDecimal("30.00"));
        v.setVuelto(new BigDecimal("5.00"));
        v.setIdempotencyKey(UUID.randomUUID().toString());

        ventaService.procesarCobroVenta(v, List.of(
            new DetalleVenta(0, 0, idProd, "Hamburguesa Royal", 1, new BigDecimal("25.00"), new BigDecimal("25.00"))
        ), null);

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT tipo_movimiento, monto FROM movimiento_caja WHERE id_caja = ? AND medio_pago = 'EFECTIVO' AND concepto LIKE '%Venta%' ORDER BY id_movimiento DESC")) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Debe registrarse movimiento de cobro en movimiento_caja");
                assertEquals(new BigDecimal("25.00"), rs.getBigDecimal("monto"));
            }
        }
    }

    @Test
    @DisplayName("B4.3: testCierreZ_TotalesPorMedioPagoCoinciden - Cierre Z consolida totales por EFECTIVO, TARJETA y YAPE")
    void testCierreZ_TotalesPorMedioPagoCoinciden() throws Exception {
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja POS-03", new BigDecimal("200.00"));
        int idProd = crearProductoConInsumo("Pizza Artesanal", new BigDecimal("30.00"));

        // Venta 1: EFECTIVO (30.00)
        Venta vEf = new Venta();
        vEf.setIdUsuario(1);
        vEf.setMetodoPago("EFECTIVO");
        vEf.setMontoRecibido(new BigDecimal("50.00"));
        vEf.setVuelto(new BigDecimal("20.00"));
        vEf.setIdempotencyKey(UUID.randomUUID().toString());
        ventaService.procesarCobroVenta(vEf, List.of(
            new DetalleVenta(0, 0, idProd, "Pizza Artesanal", 1, new BigDecimal("30.00"), new BigDecimal("30.00"))
        ), null);

        // Venta 2: YAPE (60.00)
        Venta vYape = new Venta();
        vYape.setIdUsuario(1);
        vYape.setMetodoPago("YAPE");
        vYape.setMontoRecibido(new BigDecimal("60.00"));
        vYape.setVuelto(BigDecimal.ZERO);
        vYape.setIdempotencyKey(UUID.randomUUID().toString());
        ventaService.procesarCobroVenta(vYape, List.of(
            new DetalleVenta(0, 0, idProd, "Pizza Artesanal", 2, new BigDecimal("30.00"), new BigDecimal("60.00"))
        ), null);

        Map<String, BigDecimal> totalesZ = cajaService.obtenerTotalesCierreZ(idCaja);
        assertEquals(new BigDecimal("30.00"), totalesZ.get("EFECTIVO"));
        assertEquals(new BigDecimal("60.00"), totalesZ.get("YAPE"));
    }

    @Test
    @DisplayName("B4.4: testCierreTurno_CalculaDiferenciaCorrectamente - Arqueo físico compara saldo teórico vs contado")
    void testCierreTurno_CalculaDiferenciaCorrectamente() throws Exception {
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja POS-04", new BigDecimal("100.00"));
        int idProd = crearProductoConInsumo("Combo Familiar", new BigDecimal("45.00"));

        Venta v = new Venta();
        v.setIdUsuario(1);
        v.setMetodoPago("EFECTIVO");
        v.setMontoRecibido(new BigDecimal("50.00"));
        v.setVuelto(new BigDecimal("5.00"));
        v.setIdempotencyKey(UUID.randomUUID().toString());
        ventaService.procesarCobroVenta(v, List.of(
            new DetalleVenta(0, 0, idProd, "Combo Familiar", 1, new BigDecimal("45.00"), new BigDecimal("45.00"))
        ), null);

        // Teórico en efectivo = 100.00 + 45.00 = 145.00; contado = 142.00 -> diferencia = -3.00
        cajaService.cerrarTurnoCaja(idCaja, 1, new BigDecimal("142.00"));

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT estado, saldo_final_teorico, saldo_final_contado, diferencia, cerrado_en FROM turno_caja WHERE id_turno = " + idCaja)) {
            assertTrue(rs.next());
            assertEquals("CERRADO", rs.getString("estado"));
            assertEquals(new BigDecimal("145.00"), rs.getBigDecimal("saldo_final_teorico"));
            assertEquals(new BigDecimal("142.00"), rs.getBigDecimal("saldo_final_contado"));
            assertEquals(new BigDecimal("-3.00"), rs.getBigDecimal("diferencia"));
            assertNotNull(rs.getTimestamp("cerrado_en"));
        }
    }
}
