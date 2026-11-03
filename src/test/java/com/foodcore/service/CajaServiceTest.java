package com.foodcore.service;

import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración de ciclo completo de Caja, Arqueo Físico y Cierre Z
 * según SPEC-FOODCORE-v1.0 (§6.4, §8.3, §15.2; REMEDIATION R-02.4).
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

    @Test
    @DisplayName("R-02.4: Ciclo completo Apertura (turno_caja ABIERTO) -> Cobros VENTA -> Cierre Z (saldo_final_teorico, contado, diferencia, CERRADO)")
    void debeEjecutarCicloCompletoAperturaCobroArqueoYCierreZ() throws Exception {
        // 1. Apertura: crea turno_caja con estado='ABIERTO' y saldo_inicial = 150.00
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja Principal POS-01", new BigDecimal("150.00"));

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rsTurno = st.executeQuery("SELECT estado, saldo_inicial FROM turno_caja WHERE id_turno = " + idCaja)) {
            assertTrue(rsTurno.next(), "Debe existir fila en turno_caja");
            assertEquals("ABIERTO", rsTurno.getString("estado"));
            assertEquals(new BigDecimal("150.00"), rsTurno.getBigDecimal("saldo_inicial"));
        }

        // Preparar producto con receta para registrar cobros reales
        int idProducto;
        try (Connection conn = ConexionBD.getConnection()) {
            long idInsumo = insumoDAO.crear(conn, new Insumo(
                0L, "Café en Grano Orgánico", "KG",
                new BigDecimal("50.0000"), new BigDecimal("5.0000"), new BigDecimal("30.00")));
            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('Bebidas', 'Cafetería')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.executeUpdate();
                int idCat;
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
                try (PreparedStatement psProd = conn.prepareStatement(
                        "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, 'Espresso Doble', 12.00, 100)",
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
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 0.0200)")) {
                psRec.setInt(1, idProducto);
                psRec.setLong(2, idInsumo);
                psRec.executeUpdate();
            }
        }

        // 2. Cobro 1 en EFECTIVO (24.00) y Cobro 2 en YAPE (36.00)
        Venta vEfectivo = new Venta();
        vEfectivo.setIdUsuario(1);
        vEfectivo.setMetodoPago("EFECTIVO");
        vEfectivo.setMontoRecibido(new BigDecimal("30.00"));
        vEfectivo.setVuelto(new BigDecimal("6.00"));
        vEfectivo.setIdempotencyKey(UUID.randomUUID().toString());
        ventaService.procesarCobroVenta(vEfectivo, List.of(
            new DetalleVenta(0, 0, idProducto, "Espresso Doble", 2, new BigDecimal("12.00"), new BigDecimal("24.00"))
        ), null);

        Venta vYape = new Venta();
        vYape.setIdUsuario(1);
        vYape.setMetodoPago("YAPE");
        vYape.setMontoRecibido(new BigDecimal("36.00"));
        vYape.setVuelto(BigDecimal.ZERO);
        vYape.setIdempotencyKey(UUID.randomUUID().toString());
        ventaService.procesarCobroVenta(vYape, List.of(
            new DetalleVenta(0, 0, idProducto, "Espresso Doble", 3, new BigDecimal("12.00"), new BigDecimal("36.00"))
        ), null);

        // 3. Cierre Z: verificar totales por medio de pago vs suma de movimiento_caja
        Map<String, BigDecimal> totalesPorMedio = cajaService.obtenerTotalesCierreZ(idCaja);
        assertEquals(new BigDecimal("24.00"), totalesPorMedio.get("EFECTIVO"));
        assertEquals(new BigDecimal("36.00"), totalesPorMedio.get("YAPE"));

        // 4. Cierre de turno: saldo_final_teorico en gaveta = 150.00 + 24.00 = 174.00; contado = 172.50 -> diferencia = -1.50
        cajaService.cerrarTurnoCaja(idCaja, 1, new BigDecimal("172.50"));

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rsCerrado = st.executeQuery(
                 "SELECT estado, saldo_final_teorico, saldo_final_contado, diferencia FROM turno_caja WHERE id_turno = " + idCaja)) {
            assertTrue(rsCerrado.next());
            assertEquals("CERRADO", rsCerrado.getString("estado"));
            assertEquals(new BigDecimal("174.00"), rsCerrado.getBigDecimal("saldo_final_teorico"));
            assertEquals(new BigDecimal("172.50"), rsCerrado.getBigDecimal("saldo_final_contado"));
            assertEquals(new BigDecimal("-1.50"), rsCerrado.getBigDecimal("diferencia"));
        }
    }
}
