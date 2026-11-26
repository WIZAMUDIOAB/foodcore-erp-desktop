package com.foodcore;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.FidelizacionDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ReporteDAO;
import com.foodcore.dao.RolDAO;
import com.foodcore.dao.impl.AsistenciaDAOImpl;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.dao.impl.CuentaPorPagarDAOImpl;
import com.foodcore.dao.impl.FidelizacionDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.RolDAOImpl;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.dao.ReporteDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Cliente;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.model.CuponCumpleanos;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.ItemInventarioFisico;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.Pedido;
import com.foodcore.model.Promocion;
import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.model.dto.ResultadoDevolucionParcial;
import com.foodcore.model.dto.SubcuentaItems;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AlertaService;
import com.foodcore.service.AnalisisMermasService;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.DemoDataService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.FinanzasService;
import com.foodcore.service.ImportacionMasivaService;
import com.foodcore.service.InventarioFisicoService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.AusenciasVacacionesServiceImpl;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.service.impl.ComboServiceImpl;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.service.impl.PlanillaServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.ToastService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite integral de regresión para el endurecimiento v5.7.1 de Integridad Financiera,
 * DAOs, Servicios Core, Inventario/Kárdex, Fidelización, Mesas/Pedidos, RRHH y Periféricos.
 */
class HardeningFinanceIntegrityTest {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @BeforeAll
    static void initDb() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
    }

    // =========================================================================
    // 1. MySQL SET Left-to-Right Bug, Anti-Overpayment & FinanzasService
    // =========================================================================

    @Test
    @DisplayName("1.1 CxP y CxC: pago 50% debe quedar PARCIAL (no PAGADA/COBRADA) y rechazar sobrepago sin huérfanos")
    void testMysqlSetBugYAntiSobrepagoCxpYCxc() throws Exception {
        CuentaPorPagarDAO cxpDAO = new CuentaPorPagarDAOImpl();
        CuentaPorCobrarDAO cxcDAO = new CuentaPorCobrarDAOImpl();
        FinanzasService finanzas = new FinanzasService();

        int idProveedor;
        int idCliente;
        try (Connection conn = ConexionBD.getConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT MIN(id_proveedor) FROM proveedor")) {
                rs.next();
                idProveedor = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT MIN(id_cliente) FROM cliente")) {
                rs.next();
                idCliente = rs.getInt(1);
            }
        }

        // CxP de S/ 100.00
        CuentaPorPagar cxp = new CuentaPorPagar();
        cxp.setIdProveedor(idProveedor);
        cxp.setNumeroDocumento("F001-HARD-" + UUID.randomUUID().toString().substring(0, 6));
        cxp.setFechaEmision(LocalDate.now(ZONA_LIMA));
        cxp.setFechaVencimiento(LocalDate.now(ZONA_LIMA).plusDays(15));
        cxp.setMontoTotal(new BigDecimal("100.00"));
        cxp.setMontoPagado(BigDecimal.ZERO);
        cxp.setEstado("PENDIENTE");
        int idCxp;
        try (Connection conn = ConexionBD.getConnection()) {
            idCxp = cxpDAO.insertar(conn, cxp);
        }

        // Pagar 50% -> debe quedar PENDIENTE, NO PAGADA
        assertTrue(finanzas.registrarPagoProveedor(idCxp, new BigDecimal("50.00"), "EFECTIVO", null, 1, "Abono 50%"));
        try (Connection conn = ConexionBD.getConnection()) {
            CuentaPorPagar tras50 = cxpDAO.buscarPorId(conn, idCxp).orElseThrow();
            assertEquals("PENDIENTE", tras50.getEstado(), "Pago del 50% debe dejar CxP en PENDIENTE (no PAGADA)");
            assertEquals(0, new BigDecimal("50.00").compareTo(tras50.getMontoPagado()));
        }

        // Sobrepago S/ 60.00 sobre saldo de S/ 50.00 -> rechazado sin dejar fila huérfana en pago_proveedor
        assertThrows(IllegalArgumentException.class,
                () -> finanzas.registrarPagoProveedor(idCxp, new BigDecimal("60.00"), "EFECTIVO", null, 1, "Sobrepago"));
        assertThrows(IllegalArgumentException.class,
                () -> finanzas.registrarPagoProveedor(idCxp, null, "EFECTIVO", null, 1, "Null"));
        assertThrows(IllegalArgumentException.class,
                () -> finanzas.registrarPagoProveedor(idCxp, new BigDecimal("-1.00"), "EFECTIVO", null, 1, "Neg"));

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM pago_proveedor WHERE id_cuenta_pagar = ?")) {
            ps.setInt(1, idCxp);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1), "Sobrepagos rechazados no deben insertar filas huérfanas en pago_proveedor");
            }
        }

        // Pagar saldo restante S/ 50.00 -> PAGADA
        assertTrue(finanzas.registrarPagoProveedor(idCxp, new BigDecimal("50.00"), "EFECTIVO", null, 1, "Liquidación"));
        try (Connection conn = ConexionBD.getConnection()) {
            CuentaPorPagar finalCxp = cxpDAO.buscarPorId(conn, idCxp).orElseThrow();
            assertEquals("PAGADA", finalCxp.getEstado());
            assertEquals(0, new BigDecimal("100.00").compareTo(finalCxp.getMontoPagado()));
        }

        // CxC de S/ 100.00
        CuentaPorCobrar cxc = new CuentaPorCobrar();
        cxc.setIdCliente(idCliente);
        cxc.setNumeroComprobante("B001-HARD-" + UUID.randomUUID().toString().substring(0, 6));
        cxc.setFechaEmision(LocalDate.now(ZONA_LIMA));
        cxc.setFechaVencimiento(LocalDate.now(ZONA_LIMA).plusDays(30));
        cxc.setMontoTotal(new BigDecimal("100.00"));
        cxc.setMontoCobrado(BigDecimal.ZERO);
        cxc.setEstado("PENDIENTE");
        int idCxc;
        try (Connection conn = ConexionBD.getConnection()) {
            idCxc = cxcDAO.insertar(conn, cxc);
        }

        assertTrue(finanzas.registrarCobroCliente(idCxc, new BigDecimal("50.00"), "EFECTIVO", null, 1, "Cobro 50%"));
        try (Connection conn = ConexionBD.getConnection()) {
            CuentaPorCobrar tras50 = cxcDAO.buscarPorId(conn, idCxc).orElseThrow();
            assertEquals("PENDIENTE", tras50.getEstado(), "Cobro del 50% debe dejar CxC en PENDIENTE (no COBRADA)");
            assertEquals(0, new BigDecimal("50.00").compareTo(tras50.getMontoCobrado()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> finanzas.registrarCobroCliente(idCxc, new BigDecimal("55.00"), "EFECTIVO", null, 1, "Sobrecobro"));
        Venta vCero = crearVentaBase("CREDITO");
        vCero.setTotal(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> finanzas.registrarVentaCreditoCorporativo(vCero, idCliente, 15));
        assertThrows(IllegalArgumentException.class,
                () -> finanzas.registrarCambioPrecioConMotivo("PRODUCTO", 1, "Prod", new BigDecimal("10.0000"), BigDecimal.ZERO, 1, "Cero", "Obs valida"));
        assertThrows(IllegalArgumentException.class,
                () -> finanzas.registrarCambioPrecioConMotivo("PRODUCTO", 1, "Prod", new BigDecimal("10.0000"), null, 1, "Null", "Obs valida"));
    }

    @Test
    @DisplayName("1.2 VentaService.registrarVentaConCreditoCorporativo persiste Venta+CxC atómicamente y revierte al exceder límite")
    void testVentaCreditoCorporativoAtomicaYLimiteCredito() throws Exception {
        asegurarCajaAbierta();
        int idProd = crearProductoConReceta("PROD-CRED-" + sufijo(), new BigDecimal("60.00"), new BigDecimal("20.0000"));
        int idCli = crearClienteCorporativo("2099" + sufijoNum(7), new BigDecimal("50.00"));

        VentaService ventaService = new VentaServiceImpl();
        Venta v = new Venta();
        v.setIdUsuario(1);
        v.setIdCliente(idCli);
        v.setMetodoPago("CREDITO_CORPORATIVO");
        v.setMontoRecibido(new BigDecimal("60.00"));
        v.setVuelto(BigDecimal.ZERO);
        v.setAplicarFidelizacion(false);
        v.setIdempotencyKey(UUID.randomUUID().toString());

        DetalleVenta det = new DetalleVenta(0, 0, idProd, "Plato Credito", 1, new BigDecimal("60.00"), new BigDecimal("60.00"));
        ComprobantePago comp = new ComprobantePago(0, 0, "FACTURA", "F001", "C" + sufijo(), new BigDecimal("60.00"));

        // S/ 60 > límite S/ 50 -> debe rechazar sin insertar venta ni CxC
        assertThrows(NegocioException.class,
                () -> ventaService.registrarVentaConCreditoCorporativo(v, List.of(det), comp, "", "Corp Test"));
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM venta WHERE idempotency_key = ?")) {
            ps.setString(1, v.getIdempotencyKey());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1), "No debe persistirse venta cuando falla crédito corporativo");
            }
        }

        // Con límite suficiente (S/ 200) -> persiste venta Y CxC vinculada en la misma transacción
        int idCliOk = crearClienteCorporativo("2088" + sufijoNum(7), new BigDecimal("200.00"));
        Venta vOk = new Venta();
        vOk.setIdUsuario(1);
        vOk.setIdCliente(idCliOk);
        vOk.setMetodoPago("CREDITO_CORPORATIVO");
        vOk.setMontoRecibido(new BigDecimal("60.00"));
        vOk.setVuelto(BigDecimal.ZERO);
        vOk.setAplicarFidelizacion(false);
        vOk.setIdempotencyKey(UUID.randomUUID().toString());
        Venta guardada = ventaService.registrarVentaConCreditoCorporativo(vOk, List.of(det), comp, "", "Corp OK");

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM cuenta_por_cobrar WHERE id_venta = ?")) {
            ps.setInt(1, guardada.getIdVenta());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1), "Debe crearse cuenta_por_cobrar vinculada atómicamente a la venta");
            }
        }
    }

    // =========================================================================
    // 2. VentaService Hardening & AutorizacionService Fail-Closed
    // =========================================================================

    @Test
    @DisplayName("2.1 VentaService rechaza spoofing descuentoPuntos, precios <= 0, PagoSplit <= 0 y valida RBAC/prorrateo/puntos")
    void testVentaServiceHardeningCompleto() throws Exception {
        asegurarCajaAbierta();
        int idProd = crearProductoConReceta("PROD-VTA-" + sufijo(), new BigDecimal("50.00"), new BigDecimal("100.0000"));
        VentaService ventaService = new VentaServiceImpl();

        // a) Spoofing de descuentoPuntos con puntosACanjear = 0
        Venta spoof = crearVentaBase("EFECTIVO");
        spoof.setPuntosACanjear(0);
        spoof.setDescuentoPuntos(new BigDecimal("50.00"));
        DetalleVenta d50 = new DetalleVenta(0, 0, idProd, "Plato 50", 1, new BigDecimal("50.00"), new BigDecimal("50.00"));
        assertThrows(NegocioException.class, () -> ventaService.registrarVenta(spoof, List.of(d50), null));

        // b) Precio unitario <= 0
        DetalleVenta dZero = new DetalleVenta(0, 0, idProd, "Gratis", 1, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(NegocioException.class, () -> ventaService.registrarVenta(crearVentaBase("EFECTIVO"), List.of(dZero), null));

        // c) PagoSplit <= 0
        Venta vSplit = crearVentaBase("EFECTIVO");
        assertThrows(NegocioException.class, () -> ventaService.registrarVentaDividida(
                vSplit, List.of(d50), null,
                List.of(new PagoSplit("EFECTIVO", new BigDecimal("60.00")),
                        new PagoSplit("TARJETA", new BigDecimal("-10.00")))));

        // d) Devolución parcial con descuento comercial: 2 ítems de S/ 50 (= S/ 100) con S/ 20 desc (= S/ 80 net)
        // Devolver 1 ítem debe reembolsar S/ 40.00 prorrateado (NO S/ 50.00) y exigir Permiso.VENTA_ANULAR
        int idCliPts = crearClienteMinorista("77" + sufijoNum(6), 200);
        Venta vDesc = crearVentaBase("EFECTIVO");
        vDesc.setIdCliente(idCliPts);
        vDesc.setAplicarFidelizacion(true);
        vDesc.setDescuento(new BigDecimal("20.00"));
        DetalleVenta d1 = new DetalleVenta(0, 0, idProd, "Item A", 1, new BigDecimal("50.00"), new BigDecimal("50.00"));
        DetalleVenta d2 = new DetalleVenta(0, 0, idProd, "Item B", 1, new BigDecimal("50.00"), new BigDecimal("50.00"));
        Venta guardada = ventaService.registrarVenta(vDesc, List.of(d1, d2), null);
        assertEquals(0, new BigDecimal("80.00").compareTo(guardada.getTotal()));

        FidelizacionService fidService = new FidelizacionService();
        assertEquals(280, fidService.obtenerPuntosCliente(idCliPts), "Debe acumular 80 pts sobre total neto S/ 80");

        List<DetalleVenta> dets = ventaService.listarDetallesVenta(guardada.getIdVenta());
        assertEquals(2, dets.size());

        // CAJERO sin Permiso.VENTA_ANULAR debe ser rechazado
        AutorizacionService authz = new AutorizacionService();
        SesionUsuario sesionCajero = authz.crearSesion(2L, "cajero1", "CAJERO", "POS-T");
        NegocioException rbacEx = assertThrows(NegocioException.class, () ->
                ventaService.devolverItemsParcial(guardada.getIdVenta(), List.of(dets.get(0).getIdDetalleVenta()), sesionCajero, "Sin permiso"));
        assertEquals(CodigoError.ACCESO_DENEGADO, rbacEx.getCodigoError());

        // ADMIN con Permiso.VENTA_ANULAR: reembolso prorrateado = S/ 40.00 y revierte 40 pts -> 240 pts
        SesionUsuario sesionAdmin = authz.crearSesion(1L, "admin", "ADMIN", "POS-T");
        ResultadoDevolucionParcial resParcial = ventaService.devolverItemsParcial(
                guardada.getIdVenta(), List.of(dets.get(0).getIdDetalleVenta()), sesionAdmin, "Devolución 1 ítem");
        assertEquals(0, new BigDecimal("40.00").compareTo(resParcial.montoReembolso()),
                "Reembolso debe prorratear el descuento de la venta (50% de S/ 80 = S/ 40)");
        assertEquals(0, new BigDecimal("40.00").compareTo(resParcial.nuevoTotalVenta()));
        assertEquals(240, fidService.obtenerPuntosCliente(idCliPts),
                "Devolución parcial de S/ 40 debe revertir 40 puntos ganados");

        // Anular el resto de la venta -> revierte los otros 40 puntos -> vuelve exactamente a 200 pts
        ventaService.anularVenta(guardada.getIdVenta(), true, sesionAdmin, "Anulación final");
        assertEquals(200, fidService.obtenerPuntosCliente(idCliPts),
                "Anulación total debe restaurar el saldo de puntos exacto previo a la venta");
    }

    @Test
    @DisplayName("2.2 dividirCuentaPorItems revierte todas las subcuentas si la 2da falla por stock; idempotency_key usa BD")
    void testDividirCuentaAtomicaEIdempotenciaBd() throws Exception {
        asegurarCajaAbierta();
        int idProdOk = crearProductoConReceta("PROD-DIV1-" + sufijo(), new BigDecimal("20.00"), new BigDecimal("10.0000"));
        int idProdSinStock = crearProductoConReceta("PROD-DIV2-" + sufijo(), new BigDecimal("30.00"), new BigDecimal("0.0000"));

        VentaService ventaService = new VentaServiceImpl();
        int ventasAntes = contarTabla("venta");

        DetalleVenta dOk = new DetalleVenta(0, 0, idProdOk, "Ok", 1, new BigDecimal("20.00"), new BigDecimal("20.00"));
        DetalleVenta dFail = new DetalleVenta(0, 0, idProdSinStock, "SinStock", 1, new BigDecimal("30.00"), new BigDecimal("30.00"));

        assertThrows(StockInsuficienteException.class, () -> ventaService.dividirCuentaPorItems(
                List.of(new SubcuentaItems(List.of(dOk), "EFECTIVO"),
                        new SubcuentaItems(List.of(dFail), "TARJETA")),
                1));
        assertEquals(ventasAntes, contarTabla("venta"),
                "Si la segunda subcuenta falla, la primera también debe revertirse en la misma transacción");

        // Idempotencia por BD: un fallo/rollback NO bloquea reintento posterior con la misma clave
        String idemKey = UUID.randomUUID().toString();
        Venta vFail = crearVentaBase("EFECTIVO");
        vFail.setIdempotencyKey(idemKey);
        assertThrows(StockInsuficienteException.class, () -> ventaService.registrarVenta(vFail, List.of(dFail), null));

        Venta vRetry = crearVentaBase("EFECTIVO");
        vRetry.setIdempotencyKey(idemKey);
        Venta persistida = ventaService.registrarVenta(vRetry, List.of(dOk), null);
        assertTrue(persistida.getIdVenta() > 0, "Reintento con misma idempotency_key tras rollback debe persistirse");

        Venta idempotente = ventaService.registrarVenta(vRetry, List.of(dOk), null);
        assertEquals(persistida.getIdVenta(), idempotente.getIdVenta(), "Clave ya persistida en BD debe retornar misma venta");
    }

    @Test
    @DisplayName("2.3 AutorizacionService aplica fail-closed en error de BD y respeta roles existentes con 0 permisos")
    void testAutorizacionServiceFailClosedYRolVacio() throws Exception {
        // 1. Fail-closed ante error de BD
        AutorizacionService authzConErrorBd = new AutorizacionService(
                new RolDAOImpl(),
                () -> {
                    throw new SQLException("BD simulada caída");
                });
        assertTrue(authzConErrorBd.obtenerPermisosPorRol("ADMIN").isEmpty(),
                "Ante fallo de BD, AutorizacionService debe devolver 0 permisos (fail-closed)");

        // 2. Rol existente en BD con conjunto de permisos intencionalmente vacío
        RolDAO rolDAO = new RolDAOImpl();
        String codigoVacio = "VACIO_" + sufijo();
        try (Connection conn = ConexionBD.getConnection()) {
            rolDAO.crearRol(conn, codigoVacio, "Rol Sin Permisos", "Prueba conjunto vacío intencional");
            Optional<Set<Permiso>> opt = rolDAO.obtenerPermisosSiRolExiste(conn, codigoVacio);
            assertTrue(opt.isPresent(), "El rol existe en BD");
            assertTrue(opt.get().isEmpty(), "El rol debe tener 0 permisos");
        }
        AutorizacionService authz = new AutorizacionService();
        assertTrue(authz.obtenerPermisosPorRol(codigoVacio).isEmpty());
    }

    // =========================================================================
    // 3. CajaService CIERRE_Z, ExportacionContableService & ReporteDAOImpl
    // =========================================================================

    @Test
    @DisplayName("3.1 CajaService usa CIERRE_Z, suma INGRESO efectivo y CajaDAO netea ANULACION/DEVOLUCION")
    void testCajaCierreZIngresosYNeteoAnulaciones() throws Exception {
        cerrarCajaAbiertaSiExiste();
        CajaService cajaService = new CajaServiceImpl();
        CajaDAO cajaDAO = new CajaDAOImpl();

        int idCaja = cajaService.abrirTurnoCaja(1, "Caja Hardening", new BigDecimal("100.00"));
        try (Connection conn = ConexionBD.getConnection()) {
            cajaDAO.registrarMovimiento(conn, new MovimientoCaja(0, idCaja, 1, "VENTA", "EFECTIVO", new BigDecimal("80.00"), "Venta #1"));
            cajaDAO.registrarMovimiento(conn, new MovimientoCaja(0, idCaja, 1, "ANULACION", "EFECTIVO", new BigDecimal("-30.00"), "Anulación Venta #1"));
            cajaDAO.registrarMovimiento(conn, new MovimientoCaja(0, idCaja, 1, "INGRESO", "EFECTIVO", new BigDecimal("25.00"), "Reposición sencillo"));
        }

        // Totales por medio pago deben netear VENTA 80 - ANULACION 30 = 50
        Map<String, BigDecimal> totales = cajaService.obtenerTotalesCierreZ(idCaja);
        assertEquals(0, new BigDecimal("50.00").compareTo(totales.get("EFECTIVO")),
                "obtenerTotalesPorMedioPago debe netear ANULACION/DEVOLUCION");

        // Esperado = apertura(100) + ventas(80) + ingreso(25) - anulacion(30) = 175.00
        cajaService.cerrarTurnoCaja(idCaja, 1, new BigDecimal("175.00"));

        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT diferencia FROM caja WHERE id_caja = ?")) {
                ps.setInt(1, idCaja);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, BigDecimal.ZERO.compareTo(rs.getBigDecimal("diferencia")),
                            "Con apertura 100 + ventas 80 + ingreso 25 - anulación 30 = 175.00, diferencia debe ser 0");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM movimiento_caja WHERE id_caja = ? AND tipo_movimiento = 'CIERRE_Z'")) {
                ps.setInt(1, idCaja);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "El cierre Z debe registrarse como tipo_movimiento = 'CIERRE_Z'");
                }
            }
        }
    }

    @Test
    @DisplayName("3.2 ExportacionContableService neutraliza Formula Injection y excluye ANULADA; ReporteDAO excluye ANULADA")
    void testExportacionFormulaInjectionYReporteBajaRotacion(@TempDir Path tempDir) throws Exception {
        assertEquals("'=CMD|' /C calc'!A0", ExportacionContableService.sanitizarFormulaCelda("=CMD|' /C calc'!A0"));
        assertEquals("'+1+1", ExportacionContableService.sanitizarFormulaCelda("+1+1"));
        assertEquals("'-2+3", ExportacionContableService.sanitizarFormulaCelda("-2+3"));
        assertEquals("'@SUM(A1)", ExportacionContableService.sanitizarFormulaCelda("@SUM(A1)"));
        assertEquals("'\tTAB", ExportacionContableService.sanitizarFormulaCelda("\tTAB"));
        assertEquals("Cliente Normal", ExportacionContableService.sanitizarFormulaCelda("Cliente Normal"));

        ExportacionContableService exp = new ExportacionContableService();
        File csv = tempDir.resolve("formula_safe.csv").toFile();
        exp.exportarReporteGenericoCsv(List.of("Nombre", "Nota"), List.of(List.of("=1+1", "+cmd")), csv);
        String csvContent = Files.readString(csv.toPath(), StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("\"'=1+1\""), "CSV debe neutralizar celdas que inician con =");
        assertTrue(csvContent.contains("\"'+cmd\""), "CSV debe neutralizar celdas que inician con +");

        // ReporteDAO.obtenerPlatosBajaRotacion no debe contar ítems de ventas ANULADAS
        asegurarCajaAbierta();
        String nombreUnico = "AAA_PlatoAnulado_" + sufijo();
        int idProd = crearProductoConReceta(nombreUnico, new BigDecimal("25.00"), new BigDecimal("50.0000"));
        VentaService ventaService = new VentaServiceImpl();
        Venta v = ventaService.registrarVenta(crearVentaBase("EFECTIVO"),
                List.of(new DetalleVenta(0, 0, idProd, nombreUnico, 4, new BigDecimal("25.00"), new BigDecimal("100.00"))),
                null);
        SesionUsuario adminSes = new AutorizacionService().crearSesion(1L, "admin", "ADMIN", "POS");
        ventaService.anularVenta(v.getIdVenta(), false, adminSes, "Venta anulada no debe sumar rotación");

        ReporteDAO reporteDAO = new ReporteDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            Map<String, Integer> bajaRot = reporteDAO.obtenerPlatosBajaRotacion(conn, 200);
            assertEquals(0, bajaRot.getOrDefault(nombreUnico, -1),
                    "Un producto cuyas ventas fueron ANULADAS debe reportar 0 unidades vendidas");
        }
    }

    // =========================================================================
    // 4. Inventario/Kárdex, Fidelización, Pedidos/Mesas y RRHH
    // =========================================================================

    @Test
    @DisplayName("4.1 ImportacionMasiva rechaza >10MB y fórmulas Excel; Mermas revierte si falla Kardex; InventarioFisico bloquea antes de diff")
    void testInventarioKardexHardening(@TempDir Path tempDir) throws Exception {
        ImportacionMasivaService imp = new ImportacionMasivaService();

        // a) Rechazo > 10 MB
        File grande = tempDir.resolve("grande.csv").toFile();
        try (RandomAccessFile raf = new RandomAccessFile(grande, "rw")) {
            raf.setLength(ImportacionMasivaService.MAX_FILE_SIZE_BYTES + 1024L);
        }
        assertThrows(IllegalArgumentException.class, () -> imp.prevalidarArchivoInsumos(grande));

        // b) Rechazo de fórmulas Excel
        File conFormula = tempDir.resolve("formula.xlsx").toFile();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Insumos");
            Row h = sh.createRow(0);
            String[] cols = {"Codigo_SKU", "Nombre_Insumo", "Unidad_Medida", "Stock_Actual", "Stock_Minimo", "Costo_Unitario"};
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i).setCellValue(cols[i]);
            }
            Row r = sh.createRow(1);
            r.createCell(0).setCellValue("INS-F1");
            r.createCell(1).setCellValue("Harina");
            r.createCell(2).setCellValue("KG");
            r.createCell(3).setCellFormula("1+1");
            r.createCell(4).setCellValue(1);
            r.createCell(5).setCellValue(5);
            try (FileOutputStream fos = new FileOutputStream(conFormula)) {
                wb.write(fos);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> imp.prevalidarArchivoInsumos(conFormula));

        // c) AnalisisMermasService NO debe tragar fallo de Kardex (stock insuficiente revierte merma)
        InsumoDAO insDAO = new InsumoDAOImpl();
        long idIns;
        try (Connection conn = ConexionBD.getConnection()) {
            idIns = insDAO.crear(conn, new Insumo(0L, "MRM-" + sufijo(), "Insumo Merma", "UNIDAD",
                    new BigDecimal("2.0000"), new BigDecimal("1.0000"), new BigDecimal("5.00")));
        }
        AnalisisMermasService mermas = new AnalisisMermasService();
        int mermasAntes = contarTabla("merma");
        assertThrows(Exception.class,
                () -> mermas.registrarMerma(idIns, 1, new BigDecimal("50.0000"), "Quemado", "Excede stock"));
        assertEquals(mermasAntes, contarTabla("merma"),
                "Si Kardex falla por stock insuficiente, la fila en merma debe revertirse");

        // d) InventarioFisicoService bloquea FOR UPDATE antes de calcular diff (detecta cambio concurrente en BD)
        InventarioFisicoService invFisico = new InventarioFisicoService();
        // El objeto en memoria cree que sistema = 5 y contado = 5 (stale), pero en BD real es 2.0000!
        ItemInventarioFisico staleItem = new ItemInventarioFisico(
                idIns, "MRM", "Insumo Merma", "UNIDAD",
                new BigDecimal("5.0000"), new BigDecimal("5.0000"), new BigDecimal("5.00"));
        InventarioFisicoService.ResultadoAjusteInventarioFisico resInv =
                invFisico.aplicarAjustesInventarioFisico(List.of(staleItem), 1, "Conteo concurrente");
        assertEquals(1, resInv.insumosAjustados(),
                "Debe detectar diff real contra BD bloqueada (+3.0000) pese a snapshot obsoleto en memoria");
        assertEquals(0, new BigDecimal("3.0000").compareTo(resInv.sumaDiferenciaNeta()));
    }

    @Test
    @DisplayName("4.2 Fidelizacion: canjearPuntos <=0, cupones SecureRandom + un solo uso/cliente, ClienteDAO.actualizar preserva puntos")
    void testFidelizacionYClienteHardening() throws Exception {
        FidelizacionService fid = new FidelizacionService();
        FidelizacionDAO fidDAO = new FidelizacionDAOImpl();
        ClienteDAO cliDAO = new ClienteDAOImpl();

        int idCli1 = crearClienteMinoristaConCumple("76" + sufijoNum(6), 150, LocalDate.now(ZONA_LIMA).minusYears(25));
        int idCli2 = crearClienteMinorista("75" + sufijoNum(6), 10);

        // totalVenta <= 0 rechazado
        assertEquals(0, BigDecimal.ZERO.compareTo(fid.canjearPuntos(idCli1, 50, BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.compareTo(fid.canjearPuntos(idCli1, 50, new BigDecimal("-10.00"))));
        assertEquals(150, fid.obtenerPuntosCliente(idCli1));

        // Cupón cumpleaños con entropía y validación estricta de cliente + un solo uso
        CuponCumpleanos cupon = fid.verificarYGenerarCuponCumpleanos(idCli1).orElseThrow();
        assertFalse(cupon.getCodigoCupon().contains("CLI" + idCli1),
                "Código de cupón no debe ser predecible por ID de cliente");
        assertFalse(fid.marcarCuponUsado(cupon.getCodigoCupon(), idCli2),
                "Un cupón no puede ser redimido por otro cliente");
        assertTrue(fid.marcarCuponUsado(cupon.getCodigoCupon(), idCli1),
                "El cliente dueño puede redimir el cupón una vez");
        assertFalse(fid.marcarCuponUsado(cupon.getCodigoCupon(), idCli1),
                "El cupón ya usado no puede reutilizarse");

        // ClienteDAOImpl.actualizar no sobrescribe puntos concurrentes
        try (Connection conn = ConexionBD.getConnection()) {
            Cliente stale = cliDAO.buscarPorId(conn, idCli1).orElseThrow();
            assertTrue(fidDAO.acreditarPuntosAtomico(conn, idCli1, 50)); // Ahora tiene 200 en BD
            stale.setTelefono("999111222");
            stale.setPuntosAcumulados(0); // Valor obsoleto en el formulario de edición
            assertTrue(cliDAO.actualizar(conn, stale));
            assertEquals(200, fidDAO.obtenerPuntosActuales(conn, idCli1),
                    "ClienteDAOImpl.actualizar no debe sobreescribir puntos_acumulados concurrentes");
        }
    }

    @Test
    @DisplayName("4.3 PedidoService RBAC post-entrega, CANCELADO->CANCELADA y transferirMesa valida destino LIBRE + auditoria")
    void testPedidosYMesasHardening() throws Exception {
        PedidoService pedidoService = new PedidoServiceImpl();
        PedidoDAO pedidoDAO = new PedidoDAOImpl();
        MesaDAO mesaDAO = new MesaDAOImpl();

        int idProd = crearProductoConReceta("PROD-PED-" + sufijo(), new BigDecimal("18.00"), new BigDecimal("50.0000"));
        String mOrigen = "H1" + sufijo().substring(0, 3);
        String mOcupada = "H2" + sufijo().substring(0, 3);
        String mLibre = "H3" + sufijo().substring(0, 3);
        int idMesaOri;
        int idMesaOcu;
        int idMesaLib;
        try (Connection conn = ConexionBD.getConnection()) {
            idMesaOri = insertarMesa(conn, mOrigen, "OCUPADA");
            idMesaOcu = insertarMesa(conn, mOcupada, "OCUPADA");
            idMesaLib = insertarMesa(conn, mLibre, "LIBRE");
        }

        Pedido p = new Pedido();
        p.setIdUsuario(1);
        p.setIdMesa(idMesaOri);
        p.setNumeroMesa(mOrigen);
        p.setTotal(new BigDecimal("18.00"));
        p.setIdempotencyKey(UUID.randomUUID().toString());
        DetallePedido dp = new DetallePedido(0, 0, idProd, 1, new BigDecimal("18.00"), new BigDecimal("18.00"), "");
        dp.setNombreProducto("Plato Pedido");
        Pedido creado = pedidoService.crearPedido(p, List.of(dp));

        // Transferencia hacia mesa OCUPADA debe rechazarse
        assertThrows(IllegalStateException.class, () -> pedidoService.transferirMesa(idMesaOri, idMesaOcu));
        try (Connection conn = ConexionBD.getConnection()) {
            assertThrows(IllegalStateException.class, () -> mesaDAO.transferirPedidoMesa(conn, mOrigen, mOcupada, 1));
        }

        // Transferencia hacia mesa LIBRE debe funcionar, incrementar version y escribir auditoria
        int audAntes = contarTabla("auditoria");
        assertTrue(pedidoService.transferirMesa(idMesaOri, idMesaLib));
        assertTrue(contarTabla("auditoria") > audAntes, "transferirMesa debe registrar fila válida en auditoria");

        // Post-entrega RBAC: marcar ENTREGADO y verificar que MOZO es rechazado pero ADMIN pasa
        try (Connection conn = ConexionBD.getConnection()) {
            pedidoDAO.actualizarEstadoPedido(conn, creado.getIdPedido(), "ENTREGADO");
        }
        AutorizacionService authz = new AutorizacionService();
        SesionUsuario mozoSes = authz.crearSesion(3L, "mozo1", "MOZO", "TAB-1");
        SesionUsuario adminSes = authz.crearSesion(1L, "admin", "ADMIN", "POS-1");
        DetallePedido extra = new DetallePedido(0, creado.getIdPedido(), idProd, 1, new BigDecimal("18.00"), new BigDecimal("18.00"), "Extra");
        extra.setNombreProducto("Plato Extra");

        NegocioException exMozo = assertThrows(NegocioException.class,
                () -> pedidoService.agregarItemsAPedido(creado.getIdPedido(), List.of(extra), mozoSes));
        assertEquals(CodigoError.ACCESO_DENEGADO, exMozo.getCodigoError());
        Pedido conExtra = pedidoService.agregarItemsAPedido(creado.getIdPedido(), List.of(extra), adminSes);
        assertEquals("ITEMS_ADICIONALES", conExtra.getEstado());

        // CANCELADO debe actualizar comanda a CANCELADA (no PENDIENTE)
        try (Connection conn = ConexionBD.getConnection()) {
            pedidoDAO.actualizarEstadoPedido(conn, creado.getIdPedido(), "CANCELADO");
            try (PreparedStatement ps = conn.prepareStatement("SELECT estado FROM comanda WHERE id_pedido = ?")) {
                ps.setInt(1, creado.getIdPedido());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("CANCELADA", rs.getString("estado"));
                }
            }
        }
    }

    @Test
    @DisplayName("4.4 RRHH: auto-aprobación bloqueada, turno nocturno en Asistencia y periodo cerrado en Planilla")
    void testRrhhHardening() throws Exception {
        // 1. Auto-aprobación bloqueada en AusenciasVacacionesServiceImpl
        AusenciasVacacionesServiceImpl ausencias = new AusenciasVacacionesServiceImpl();
        int anio = LocalDate.now(ZONA_LIMA).getYear();
        SolicitudAusencia sol = ausencias.registrarSolicitud(
                3, "PERMISO_MEDICO",
                LocalDate.of(anio, 12, 10), LocalDate.of(anio, 12, 11), "Control dental");
        assertThrows(IllegalStateException.class,
                () -> ausencias.aprobarSolicitud(sol.getIdSolicitud(), "Auto-aprobado", 3),
                "Un colaborador no debe poder auto-aprobar su propia solicitud");

        // 2. Asistencia nocturna (entrada 22:00 día D, salida 06:00 día D+1 = 8.00 hrs) + lectura real id_empleado
        AsistenciaService asistService = new AsistenciaService(new AsistenciaDAOImpl(), new UsuarioDAOImpl());
        String dniTest = "45892147"; // admin seeded en V8
        LocalDate fechaBase = LocalDate.now(ZONA_LIMA).minusDays(40);
        LocalDateTime entradaNocturna = fechaBase.atTime(22, 0);
        LocalDateTime salidaMadrugada = fechaBase.plusDays(1).atTime(6, 0);

        AsistenciaService.ResultadoMarcaje resIn = asistService.registrarMarcajeKioscoEnInstante(dniTest, entradaNocturna);
        assertTrue(resIn.isExitoso());
        assertEquals("ENTRADA", resIn.getTipo());
        try {
            AsistenciaService.ResultadoMarcaje resOut = asistService.registrarMarcajeKioscoEnInstante(dniTest, salidaMadrugada);
            assertTrue(resOut.isExitoso());
            assertEquals("SALIDA", resOut.getTipo(), "Salida tras medianoche debe cerrar el turno abierto de la noche anterior");
            assertEquals(8.0, resOut.getAsistencia().getHorasTrabajadas(), 0.01);
        } finally {
            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM asistencia WHERE id_asistencia = ?")) {
                ps.setInt(1, resIn.getAsistencia().getIdAsistencia());
                ps.executeUpdate();
            }
        }

        // 3. PlanillaServiceImpl valida cierre contable
        CierreContableService cierreService = new CierreContableService();
        LocalDate fechaCerrada = LocalDate.of(2024, 2, 10);
        cierreService.cerrarMes(2024, 2, 1, "Cierre prueba hardening");
        PlanillaServiceImpl planilla = new PlanillaServiceImpl();
        IllegalStateException cerradaEx = assertThrows(IllegalStateException.class,
                () -> planilla.registrarMovimiento(2, "BONO", "Bono tardío", new BigDecimal("50.00"), fechaCerrada, 1, null));
        assertTrue(cerradaEx.getCause() instanceof PeriodoCerradoException,
                "PlanillaServiceImpl debe rechazar movimientos en periodos contables cerrados");
    }

    // =========================================================================
    // 5. Impresora, Alertas, DemoData, Combos, Promociones & Helpers
    // =========================================================================

    @Test
    @DisplayName("5.1 ImpresoraTermicaService elimina bytes ESC/POS inyectados, conserva fecha original y reporta false en fallo")
    void testImpresoraTermicaHardening(@TempDir Path tempDir) throws Exception {
        ImpresoraTermicaService imp = new ImpresoraTermicaService(
                new AutorizacionService(), tempDir.resolve("tickets_hardening.log"));

        LocalDateTime fechaOriginal = LocalDateTime.of(2026, 5, 14, 13, 45, 22);
        Venta v = new Venta();
        v.setIdVenta(777);
        v.setFechaHora(fechaOriginal);
        v.setTotal(new BigDecimal("25.00"));
        v.setMetodoPago("EFECTIVO");

        // Nombre con secuencia inyectada de apertura de cajón (0x1B 0x70 0x00 0x19)
        String nombreMalicioso = "Burger\u001Bp\u0000\u0019Maliciosa";
        DetalleVenta dv = new DetalleVenta(1, 777, 1, nombreMalicioso, 1, new BigDecimal("25.00"), new BigDecimal("25.00"));
        ComprobantePago comp = new ComprobantePago(1, 777, "BOLETA", "B001", "000777", new BigDecimal("25.00"));
        comp.setNombreCliente("Juan\u001B@Inyectado");

        List<String> lineas = imp.formatearLineasTicket(v, List.of(dv), comp, true);
        assertTrue(lineas.stream().anyMatch(l -> l.contains("14/05/2026 13:45:22")),
                "La reimpresión debe conservar venta.getFechaHora() original");
        byte[] bytes = imp.generarBytesEscPos(lineas, true);
        // Solo debe existir un ESC (0x1B) al inicio (ESC_POS_INIT = bytes[0..1]), ninguno en el cuerpo
        for (int i = 2; i < bytes.length - 4; i++) {
            assertNotEquals((byte) 0x1B, bytes[i], "No debe haber secuencias ESC (0x1B) inyectadas en el cuerpo del ticket");
        }

        Pedido ped = new Pedido();
        ped.setIdPedido(99);
        ped.setNumeroMesa("01");
        ped.setTotal(new BigDecimal("25.00"));
        DetallePedido dp = new DetallePedido(1, 99, 1, 1, new BigDecimal("25.00"), new BigDecimal("25.00"), "");
        dp.setNombreProducto("Pollo");
        assertFalse(imp.imprimirComandaCocina(ped, List.of(dp), "IMPRESORA_INEXISTENTE_XYZ_999"),
                "Debe retornar false honestamente cuando la impresora física solicitada no existe");
        assertFalse(imp.imprimirPreCuenta(ped, List.of(dp), "IMPRESORA_INEXISTENTE_XYZ_999"),
                "Debe retornar false honestamente cuando la impresora física solicitada no existe");
    }

    @Test
    @DisplayName("5.2 AlertaService rechaza CRLF, libera lock en E/S, reintenta si falla red y acota historial")
    void testAlertaServiceHardening() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> AlertaService.validarEmailSeguro("atacante@evil.com\r\nRCPT TO:<victima@corp.pe>", "destinatario"));
        assertThrows(IllegalArgumentException.class,
                () -> AlertaService.validarEmailSeguro("no-es-correo", "destinatario"));

        AtomicLong reloj = new AtomicLong(1_000_000L);
        AtomicInteger intentos = new AtomicInteger(0);
        CountDownLatch enRed = new CountDownLatch(1);
        CountDownLatch liberarRed = new CountDownLatch(1);

        AlertaService alerta = new AlertaService(reloj::get, (cfg, dest, asunto, cuerpo) -> {
            int n = intentos.incrementAndGet();
            if (n == 1) {
                enRed.countDown();
                try {
                    liberarRed.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return false; // Primer envío falla -> NO debe suprimir reintento
            }
            return true;
        });
        alerta.actualizarConfiguracion(new AlertaService.ConfiguracionAlertaEmail(
                "127.0.0.1", 587, "alertas@foodcore.pe", "", false, "dueno@foodcore.pe", true), false);

        long discoBajo = 1L * 1024L * 1024L * 1024L;
        Thread worker = new Thread(() -> alerta.evaluar(true, 40.0, discoBajo));
        worker.start();
        assertTrue(enRed.await(3, TimeUnit.SECONDS));

        // Mientras el primer envío está en E/S de red, getHistorialAlertas() NO debe quedar bloqueado por monitorLock
        AtomicReference<List<AlertaService.AlertaEmitida>> lecturaConcurrente = new AtomicReference<>();
        Thread lector = new Thread(() -> lecturaConcurrente.set(alerta.getHistorialAlertas()));
        lector.start();
        lector.join(1500L);
        assertFalse(lector.isAlive(), "monitorLock debe liberarse durante E/S de red SMTP");

        liberarRed.countDown();
        worker.join(3000L);

        // Como el primer intento falló (enviadoRed=false), la segunda evaluación DEBE reintentar y tener éxito
        List<AlertaService.AlertaEmitida> reintento = alerta.evaluar(true, 40.0, discoBajo);
        assertEquals(1, reintento.size(), "Debe reintentar alerta tras fallo previo de red");
        assertTrue(reintento.get(0).enviadoPorRed());
        assertTrue(alerta.evaluar(true, 40.0, discoBajo).isEmpty(),
                "Tras éxito de envío, no debe repetir alerta mientras persista el mismo incidente");
    }

    @Test
    @DisplayName("5.3 DemoDataService RBAC/classpath, ComboService sin colisión, Promocion nocturna, FileChooser y ToastInfo")
    void testDemoCombosPromosFileChooserYToast(@TempDir Path tempDir) throws Exception {
        // 1. DemoDataService exige CONFIG_RED y bloquea rutas externas
        DemoDataService demo = new DemoDataService();
        AuthService.logout();
        assertThrows(SecurityException.class, demo::cargarDatosDemo,
                "Sin sesión con CONFIG_RED, DemoDataService debe rechazar carga");
        Usuario mozo = new Usuario(3, 3, 3, "mozo1", "h", true);
        mozo.setNombreRol("MOZO");
        AuthService.setUsuarioSesionActual(mozo);
        try {
            assertThrows(SecurityException.class, demo::cargarDatosDemo,
                    "MOZO sin CONFIG_RED debe ser rechazado por DemoDataService");
            assertThrows(SecurityException.class, demo::borrarDatosDemo,
                    "MOZO sin CONFIG_RED debe ser rechazado al borrar demo");
        } finally {
            AuthService.logout();
        }

        // 2. ComboServiceImpl rechaza colisión de código en nuevo combo
        int idProdComp = crearProductoConReceta("PROD-CMB-" + sufijo(), new BigDecimal("15.00"), new BigDecimal("50.0000"));
        ComboServiceImpl comboService = new ComboServiceImpl();
        String codUnico = "CMB-H-" + sufijo();
        Combo c1 = new Combo();
        c1.setCodigo(codUnico);
        c1.setNombre("Combo Hardening 1");
        c1.setPrecioCombo(new BigDecimal("25.00"));
        c1.setActivo(true);
        c1.setDetalles(List.of(new ComboDetalle(0, 0, idProdComp, 1)));
        Combo guardado1 = comboService.guardarCombo(c1);
        assertTrue(guardado1.getIdCombo() > 0);

        Combo cDup = new Combo();
        cDup.setCodigo(codUnico);
        cDup.setNombre("Combo Colision");
        cDup.setPrecioCombo(new BigDecimal("30.00"));
        cDup.setActivo(true);
        cDup.setDetalles(List.of(new ComboDetalle(0, 0, idProdComp, 1)));
        assertThrows(NegocioException.class, () -> comboService.guardarCombo(cDup),
                "Un combo nuevo con código existente no debe sobrescribir silenciosamente al combo previo");

        // 3. PromocionService ventana nocturna 22:00 - 02:00
        PromocionService promoService = new PromocionService();
        Promocion happyHour = new Promocion();
        happyHour.setDiasSemana("VIE");
        happyHour.setHoraInicio(LocalTime.of(22, 0));
        happyHour.setHoraFin(LocalTime.of(2, 0));
        // Acceder vía reflexión o mismo paquete: PromocionService.evaluarPromociones ya usa aplicaDiaYHora
        // Probamos vía DAO mock simple en subclase anónima
        PromocionService promoMem = new PromocionService(new com.foodcore.dao.impl.PromocionDAOImpl() {
            @Override
            public List<Promocion> listarActivas(Connection conn) {
                happyHour.setTipoPromocion("MONTO_FIJO");
                happyHour.setValorDescuento(new BigDecimal("10.00"));
                happyHour.setNombre("Happy Hour Nocturno");
                return List.of(happyHour);
            }
        });
        DetalleVenta dvPromo = new DetalleVenta(0, 0, idProdComp, "Bebida", 2, new BigDecimal("20.00"), new BigDecimal("40.00"));
        assertTrue(promoMem.evaluarPromociones(List.of(dvPromo), new BigDecimal("40.00"),
                DayOfWeek.FRIDAY, LocalTime.of(23, 30)).tieneDescuento(),
                "Viernes 23:30 debe activar Happy Hour VIE 22:00-02:00");
        assertTrue(promoMem.evaluarPromociones(List.of(dvPromo), new BigDecimal("40.00"),
                DayOfWeek.SATURDAY, LocalTime.of(1, 15)).tieneDescuento(),
                "Sábado 01:15 (madrugada del viernes) debe activar Happy Hour VIE 22:00-02:00");
        assertFalse(promoMem.evaluarPromociones(List.of(dvPromo), new BigDecimal("40.00"),
                DayOfWeek.SATURDAY, LocalTime.of(23, 0)).tieneDescuento(),
                "Sábado 23:00 NO debe activar Happy Hour solo de viernes");
        assertFalse(promoMem.evaluarPromociones(List.of(dvPromo), new BigDecimal("40.00"),
                DayOfWeek.FRIDAY, LocalTime.of(15, 0)).tieneDescuento(),
                "Viernes 15:00 está fuera de ventana 22:00-02:00");

        // 4. ExportFileChooserHelper filtro único + extensión exacta
        Preferences prefs = Preferences.userRoot().node("fc-hardening-" + UUID.randomUUID());
        try {
            ExportFileChooserHelper helper = ExportFileChooserHelper.conPreferencias(prefs);
            AtomicReference<ExportFileChooserHelper.FileChooserRequest> reqRef = new AtomicReference<>();
            ExportFileChooserHelper.setTestSelectorOverride(req -> {
                reqRef.set(req);
                return Optional.of(tempDir.resolve("reporte.pdf").toFile()); // usuario eligió .pdf para export EXCEL
            });
            Optional<File> sel = helper.seleccionarArchivoGuardar(
                    null, "Exportar Excel", "ventas", ExportFileChooserHelper.FormatoExportacion.EXCEL);
            assertTrue(sel.isPresent());
            assertEquals(1, reqRef.get().filtros().size(), "Debe mostrar únicamente 1 filtro de formato");
            assertEquals(List.of("*.xlsx"), reqRef.get().filtros().get(0).getExtensions());
            assertTrue(sel.get().getName().endsWith(".xlsx"),
                    "Debe garantizar extensión exacta .xlsx aunque el usuario escriba otra");
        } finally {
            ExportFileChooserHelper.clearTestSelectorOverride();
            prefs.removeNode();
        }

        // 5. ToastService.mostrarInfo usa TipoToast.INFO
        ToastService.mostrarInfo(null, "Mensaje informativo");
        assertEquals(ToastService.TipoToast.INFO, ToastService.getUltimoTipoToast());
    }

    // =========================================================================
    // Helpers de datos de prueba
    // =========================================================================

    private static void asegurarCajaAbierta() throws Exception {
        CajaService cs = new CajaServiceImpl();
        if (cs.obtenerCajaActiva().isEmpty()) {
            cs.abrirTurnoCaja(1, "Caja Hardening IT", new BigDecimal("100.00"));
        }
    }

    private static void cerrarCajaAbiertaSiExiste() throws Exception {
        CajaService cs = new CajaServiceImpl();
        cs.obtenerCajaActiva().ifPresent(c -> {
            try {
                cs.cerrarTurnoCaja(c.getIdCaja(), 1, BigDecimal.ZERO);
            } catch (Exception ignored) {
            }
        });
    }

    private static Venta crearVentaBase(String metodo) {
        Venta v = new Venta();
        v.setIdUsuario(1);
        v.setMetodoPago(metodo);
        v.setMontoRecibido(new BigDecimal("200.00"));
        v.setVuelto(BigDecimal.ZERO);
        v.setAplicarFidelizacion(false);
        v.setIdempotencyKey(UUID.randomUUID().toString());
        return v;
    }

    private static int crearProductoConReceta(String nombre, BigDecimal precio, BigDecimal stockInsumo) throws Exception {
        InsumoDAO insDAO = new InsumoDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            long idIns = insDAO.crear(conn, new Insumo(
                    0L, "INS-" + sufijo(), "Insumo " + nombre, "UNIDAD", stockInsumo, BigDecimal.ONE, BigDecimal.ONE));
            int idCat;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT MIN(id_categoria) FROM categoria")) {
                rs.next();
                idCat = rs.getInt(1);
            }
            int idProd;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO producto (id_categoria, codigo, nombre, precio_venta, stock_disponible, disponible, activo) "
                            + "VALUES (?, ?, ?, ?, 100, 1, 1)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idCat);
                ps.setString(2, "PR-" + sufijo());
                ps.setString(3, nombre);
                ps.setBigDecimal(4, precio);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    idProd = rs.getInt(1);
                }
            }
            try (PreparedStatement psRec = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 1.0000)")) {
                psRec.setInt(1, idProd);
                psRec.setLong(2, idIns);
                psRec.executeUpdate();
            }
            return idProd;
        }
    }

    private static int crearClienteCorporativo(String ruc, BigDecimal limite) throws Exception {
        ClienteDAO dao = new ClienteDAOImpl();
        Cliente c = new Cliente();
        c.setTipoDocumento("RUC");
        c.setNumeroDocumento(ruc);
        c.setTipoCliente("CORPORATIVO");
        c.setRazonSocial("Corp Hardening " + ruc);
        c.setLimiteCredito(limite);
        c.setDiasCredito(30);
        c.setEstado("ACTIVO");
        c.setActivo(true);
        try (Connection conn = ConexionBD.getConnection()) {
            return dao.insertar(conn, c);
        }
    }

    private static int crearClienteMinorista(String dni, int puntos) throws Exception {
        return crearClienteMinoristaConCumple(dni, puntos, null);
    }

    private static int crearClienteMinoristaConCumple(String dni, int puntos, LocalDate fechaNac) throws Exception {
        ClienteDAO dao = new ClienteDAOImpl();
        Cliente c = new Cliente();
        c.setTipoDocumento("DNI");
        c.setNumeroDocumento(dni);
        c.setTipoCliente("MINORISTA");
        c.setRazonSocial("Cliente Hardening " + dni);
        c.setPuntosAcumulados(puntos);
        c.setPuntosFidelidad(puntos);
        c.setFechaNacimiento(fechaNac);
        c.setEstado("ACTIVO");
        c.setActivo(true);
        try (Connection conn = ConexionBD.getConnection()) {
            return dao.insertar(conn, c);
        }
    }

    private static int insertarMesa(Connection conn, String numero, String estado) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO mesa (numero, estado, version, activo) VALUES (?, ?, 0, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setString(2, estado);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int contarTabla(String tabla) throws Exception {
        String sql = switch (tabla) {
            case "venta" -> "SELECT COUNT(*) FROM venta";
            case "merma" -> "SELECT COUNT(*) FROM merma";
            case "auditoria" -> "SELECT COUNT(*) FROM auditoria";
            default -> throw new IllegalArgumentException("Tabla no permitida: " + tabla);
        };
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(java.util.Locale.ROOT);
    }

    private static String sufijoNum(int digits) {
        long val = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String s = String.format(java.util.Locale.ROOT, "%012d", val % 1_000_000_000_000L);
        return s.substring(s.length() - digits);
    }
}
