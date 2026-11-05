package com.foodcore.dao;

import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de pruebas de integración para {@link VentaDAOImpl} (B3-bis).
 */
class VentaDAOImplTest {

    private VentaDAOImpl ventaDAO;
    private int idCajaActiva;

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        ventaDAO = new VentaDAOImpl();
        CajaDAOImpl cajaDAO = new CajaDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            Caja caja = new Caja();
            caja.setNombreCaja("CAJA-TEST-DAO");
            caja.setIdUsuarioApertura(1);
            caja.setMontoApertura(new BigDecimal("100.00"));
            idCajaActiva = cajaDAO.abrirCaja(conn, caja);
        }
    }

    @Test
    @DisplayName("B3-bis (1/4): insertarVenta OK persiste cabecera, detalle, comprobante y lista ventas del día")
    void testInsertarVenta_OK() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Venta v = new Venta();
            v.setIdCaja(idCajaActiva);
            v.setIdUsuario(1);
            v.setSubtotal(new BigDecimal("42.37"));
            v.setIgv(new BigDecimal("7.63"));
            v.setTotal(new BigDecimal("50.00"));
            v.setMetodoPago("EFECTIVO");
            v.setMontoRecibido(new BigDecimal("50.00"));
            v.setVuelto(BigDecimal.ZERO);
            v.setEstado("COMPLETADA");
            v.setIdempotencyKey("IDEM-DAO-OK-001");

            int idVenta = ventaDAO.insertarVenta(conn, v);
            assertTrue(idVenta > 0);
            assertEquals(idVenta, v.getIdVenta());

            DetalleVenta det = new DetalleVenta();
            det.setIdVenta(idVenta);
            det.setIdProducto(1);
            det.setCantidad(2);
            det.setPrecioUnitario(new BigDecimal("25.00"));
            det.setSubtotal(new BigDecimal("50.00"));
            ventaDAO.insertarDetalleVenta(conn, det);

            ComprobantePago comp = new ComprobantePago();
            comp.setIdVenta(idVenta);
            comp.setTipoComprobante("BOLETA");
            comp.setSerie("B001");
            comp.setNumero("000001");
            comp.setTotal(new BigDecimal("50.00"));
            ventaDAO.insertarComprobante(conn, comp);
            assertTrue(comp.getIdComprobante() > 0);

            List<Venta> ventasHoy = ventaDAO.listarVentasDelDia(conn);
            assertFalse(ventasHoy.isEmpty());
            assertEquals(idVenta, ventasHoy.get(0).getIdVenta());
            assertEquals(0, new BigDecimal("50.00").compareTo(ventasHoy.get(0).getTotal()));
        }
    }

    @Test
    @DisplayName("B3-bis (2/4): insertarVenta con idempotency_key duplicada devuelve el ID existente sin duplicar")
    void testInsertarVenta_IdempotencyDuplicada_DevuelveExistente() throws Exception {
        String idemKey = "IDEM-DAO-DUP-002";
        try (Connection conn = ConexionBD.getConnection()) {
            Venta v1 = new Venta();
            v1.setIdCaja(idCajaActiva);
            v1.setIdUsuario(1);
            v1.setSubtotal(new BigDecimal("25.42"));
            v1.setIgv(new BigDecimal("4.58"));
            v1.setTotal(new BigDecimal("30.00"));
            v1.setIdempotencyKey(idemKey);
            int idPrimero = ventaDAO.insertarVenta(conn, v1);

            Venta v2 = new Venta();
            v2.setIdCaja(idCajaActiva);
            v2.setIdUsuario(1);
            v2.setSubtotal(new BigDecimal("25.42"));
            v2.setIgv(new BigDecimal("4.58"));
            v2.setTotal(new BigDecimal("30.00"));
            v2.setIdempotencyKey(idemKey);
            int idSegundo = ventaDAO.insertarVenta(conn, v2);

            assertEquals(idPrimero, idSegundo, "Debe retornar el id_venta original ante clave idempotente repetida");

            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM venta WHERE idempotency_key = ?")) {
                ps.setString(1, idemKey);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    @DisplayName("B3-bis (3/4): insertarVenta con total = 0 falla arrojando SQLException")
    void testInsertarVenta_TotalCero_Falla() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Venta vCero = new Venta();
            vCero.setIdCaja(idCajaActiva);
            vCero.setIdUsuario(1);
            vCero.setSubtotal(BigDecimal.ZERO);
            vCero.setIgv(BigDecimal.ZERO);
            vCero.setTotal(BigDecimal.ZERO);

            SQLException ex = assertThrows(SQLException.class, () -> ventaDAO.insertarVenta(conn, vCero));
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    @DisplayName("B3-bis (4/4): insertarVenta con excepción en detalle ejecuta rollback sin dejar cabecera huérfana")
    void testInsertarVenta_ExcepcionEnDetalle_Rollback() throws Exception {
        JdbcTransactionManager tx = new JdbcTransactionManager();

        assertThrows(SQLException.class, () -> tx.executeChecked(conn -> {
            Venta v = new Venta();
            v.setIdCaja(idCajaActiva);
            v.setIdUsuario(1);
            v.setSubtotal(new BigDecimal("33.90"));
            v.setIgv(new BigDecimal("6.10"));
            v.setTotal(new BigDecimal("40.00"));
            v.setIdempotencyKey("IDEM-DAO-ROLLBACK-004");

            int idVenta = ventaDAO.insertarVenta(conn, v);

            DetalleVenta detInvalido = new DetalleVenta();
            detInvalido.setIdVenta(idVenta);
            detInvalido.setIdProducto(1);
            detInvalido.setCantidad(0); // Inválido -> dispara SQLException y rollback ACID
            detInvalido.setPrecioUnitario(new BigDecimal("40.00"));
            detInvalido.setSubtotal(BigDecimal.ZERO);

            ventaDAO.insertarDetalleVenta(conn, detInvalido);
            return idVenta;
        }));

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM venta WHERE idempotency_key = 'IDEM-DAO-ROLLBACK-004'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "El rollback transaccional no debe dejar cabeceras de venta huérfanas");
        }
    }
}
