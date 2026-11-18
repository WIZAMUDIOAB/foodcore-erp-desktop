package com.foodcore.ops;

import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.dao.impl.AsistenciaDAOImpl;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.dao.impl.CierreContableDAOImpl;
import com.foodcore.dao.impl.ConfiguracionImpresoraDAOImpl;
import com.foodcore.dao.impl.TurnoProgramadoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.model.Asistencia;
import com.foodcore.model.Caja;
import com.foodcore.model.Categoria;
import com.foodcore.model.CierreContable;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.ItemInventarioFisico;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.model.ReporteCumplimientoTurno;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.InventarioFisicoService;
import com.foodcore.service.InventarioFisicoService.ResultadoAjusteInventarioFisico;
import com.foodcore.service.PedidoService;
import com.foodcore.service.SupervisorAutorizacionService;
import com.foodcore.service.VentaService;
import com.foodcore.service.VentaService.ResultadoDevolucionParcial;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.ImpresoraTermicaService.DespachoImpresionGrupo;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración MySQL 8.0 real para Sub-Fase B: Operaciones Avanzadas (v5.2.0):
 * valida persistencia Flyway V23, DAOs, transacciones ACID y servicios para los 8 ítems.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubFaseBOperacionesIT {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private static CajaService cajaService;
    private static VentaService ventaService;
    private static InventarioFisicoService inventarioFisicoService;
    private static PedidoService pedidoService;
    private static CierreContableService cierreContableService;
    private static AsistenciaService asistenciaService;
    private static SupervisorAutorizacionService supervisorService;
    private static AutorizacionService autorizacionService;
    private static InsumoDAOImpl insumoDAO;
    private static ProductoDAOImpl productoDAO;
    private static PedidoDAOImpl pedidoDAO;
    private static VentaDAOImpl ventaDAO;
    private static AuditoriaDAOImpl auditoriaDAO;
    private static UsuarioDAOImpl usuarioDAO;
    private static ConfiguracionImpresoraDAOImpl configImpresoraDAO;
    private static CierreContableDAOImpl cierreContableDAO;

    @BeforeAll
    static void setUpSuite() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();

        cajaService = new CajaService();
        ventaService = new VentaService();
        inventarioFisicoService = new InventarioFisicoService();
        pedidoService = new PedidoService();
        cierreContableService = new CierreContableService();
        asistenciaService = new AsistenciaService();
        supervisorService = new SupervisorAutorizacionService();
        autorizacionService = new AutorizacionService();
        insumoDAO = new InsumoDAOImpl();
        productoDAO = new ProductoDAOImpl();
        pedidoDAO = new PedidoDAOImpl();
        ventaDAO = new VentaDAOImpl();
        auditoriaDAO = new AuditoriaDAOImpl();
        usuarioDAO = new UsuarioDAOImpl();
        configImpresoraDAO = new ConfiguracionImpresoraDAOImpl();
        cierreContableDAO = new CierreContableDAOImpl();

        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setIdRol(1);
        admin.setUsername("admin");
        admin.setNombreRol("ADMIN");
        admin.setNombreEmpleado("Administrador General");
        AuthService.setUsuarioSesionActual(admin);
    }

    @AfterAll
    static void tearDownSuite() {
        AuthService.setUsuarioSesionActual(null);
        MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
    }

    @Test
    @Order(1)
    @DisplayName("Ítem 1 IT: CRUD configuracion_impresora y despacho simultáneo por categoría")
    void item1MultiplesImpresorasCrudYDespachoBd(@TempDir Path tempDir) throws Exception {
        int catCocina = asegurarCategoria("Cocina Caliente IT");
        int catBarra = asegurarCategoria("Bar Bebidas IT");
        long ins1 = asegurarInsumoConCosto("INS-IT1-C", "Insumo Cocina IT", "UND",
                new BigDecimal("100.0000"), new BigDecimal("8.00"));
        long ins2 = asegurarInsumoConCosto("INS-IT1-B", "Insumo Barra IT", "UND",
                new BigDecimal("100.0000"), new BigDecimal("4.00"));

        Producto prodCocina = asegurarProductoConReceta(catCocina, "IT1-COC", "Lomo Saltado IT",
                new BigDecimal("35.00"), new BigDecimal("8.00"), ins1, BigDecimal.ONE);
        Producto prodBarra = asegurarProductoConReceta(catBarra, "IT1-BAR", "Chicha Morada Jarra IT",
                new BigDecimal("18.00"), new BigDecimal("4.00"), ins2, BigDecimal.ONE);

        try (Connection conn = ConexionBD.getConnection()) {
            configImpresoraDAO.guardarOActualizar(conn, catCocina, "EPSON-COCINA-80MM");
            configImpresoraDAO.guardarOActualizar(conn, catBarra, "STAR-BARRA-TSP100");
            configImpresoraDAO.guardarOActualizar(conn, catBarra, "STAR-BARRA-V2");

            Map<Integer, String> catNombres = configImpresoraDAO.obtenerMapaNombreCategorias(conn);
            assertEquals("Cocina Caliente IT", catNombres.get(catCocina));
            assertEquals("Bar Bebidas IT", catNombres.get(catBarra));

            List<ConfiguracionImpresora> todas = configImpresoraDAO.listarTodas(conn);
            assertTrue(todas.stream().anyMatch(c -> c.getIdCategoriaProducto() == catCocina
                    && "EPSON-COCINA-80MM".equals(c.getNombreImpresoraWindows())));
            assertTrue(todas.stream().anyMatch(c -> c.getIdCategoriaProducto() == catBarra
                    && "STAR-BARRA-V2".equals(c.getNombreImpresoraWindows())));

            Path logFile = tempDir.resolve("comanda_multi_it.log");
            ImpresoraTermicaService impresora = new ImpresoraTermicaService(autorizacionService, logFile);

            Pedido ped = new Pedido(701, 1, null, "Mesa 09", "REGISTRADO", new BigDecimal("53.00"));
            DetallePedido dCocina = new DetallePedido(1, 701, prodCocina.getIdProducto(), prodCocina.getNombre(), 1,
                    new BigDecimal("35.00"), new BigDecimal("35.00"), "Sin cebolla");
            DetallePedido dBarra = new DetallePedido(2, 701, prodBarra.getIdProducto(), prodBarra.getNombre(), 1,
                    new BigDecimal("18.00"), new BigDecimal("18.00"), "Helada");

            List<DespachoImpresionGrupo> grupos = impresora.imprimirComandaPorCategorias(
                    conn, ped, List.of(dCocina, dBarra));
            assertEquals(2, grupos.size());
            String logTxt = Files.readString(logFile, StandardCharsets.UTF_8);
            assertTrue(logTxt.contains("[IMPRESORA: EPSON-COCINA-80MM]"));
            assertTrue(logTxt.contains("[IMPRESORA: STAR-BARRA-V2]"));

            assertTrue(configImpresoraDAO.eliminarPorCategoria(conn, catBarra));
            assertFalse(configImpresoraDAO.eliminarPorCategoria(conn, 999999));
        }
    }

    @Test
    @Order(2)
    @DisplayName("Ítem 2 IT: Toma de inventario físico ajusta stock y registra AJUSTE_INV en Kardex")
    void item2InventarioFisicoAjusteKardexYAuditoria() throws Exception {
        long idInsA = asegurarInsumoConCosto("INS-IT2-A", "Carne Molida Fina IT", "KG",
                new BigDecimal("20.0000"), new BigDecimal("16.00"));
        long idInsB = asegurarInsumoConCosto("INS-IT2-B", "Papa Amarilla Tumbay IT", "KG",
                new BigDecimal("15.0000"), new BigDecimal("4.50"));

        List<ItemInventarioFisico> items = inventarioFisicoService.listarInsumosParaConteo();
        ItemInventarioFisico itemA = items.stream().filter(i -> i.getIdInsumo() == idInsA).findFirst().orElseThrow();
        ItemInventarioFisico itemB = items.stream().filter(i -> i.getIdInsumo() == idInsB).findFirst().orElseThrow();

        itemA.setStockContado(new BigDecimal("17.500"));
        itemB.setStockContado(new BigDecimal("18.000"));

        ResultadoAjusteInventarioFisico res = inventarioFisicoService.aplicarAjustesInventarioFisico(
                List.of(itemA, itemB), 1, "Conteo físico turno IT");
        assertEquals(2, res.insumosAjustados());
        assertEquals(0, new BigDecimal("0.500").compareTo(res.sumaDiferenciaNeta()));

        try (Connection conn = ConexionBD.getConnection()) {
            Insumo insAAct = insumoDAO.obtenerPorId(conn, idInsA);
            Insumo insBAct = insumoDAO.obtenerPorId(conn, idInsB);
            assertEquals(0, new BigDecimal("17.500").compareTo(insAAct.getStockActual().setScale(3)));
            assertEquals(0, new BigDecimal("18.000").compareTo(insBAct.getStockActual().setScale(3)));

            int cntKardex = contarKardexTipo(conn, idInsA, "AJUSTE_INV");
            assertTrue(cntKardex >= 1, "Debe existir asiento AJUSTE_INV en kardex_movimiento");

            List<RegistroAuditoria> regs = auditoriaDAO.listarUltimosEventos(conn, 10);
            assertTrue(regs.stream().anyMatch(r -> "AJUSTE_INV_FISICO".equals(r.getAccion())));
        }

        // Segunda ejecución con stock idéntico debe rechazar con VALIDACION_ENTRADA
        List<ItemInventarioFisico> sinCambios = inventarioFisicoService.listarInsumosParaConteo();
        NegocioException ne = assertThrows(NegocioException.class,
                () -> inventarioFisicoService.aplicarAjustesInventarioFisico(sinCambios, 1, "Sin dif"));
        assertEquals(CodigoError.VALIDACION_ENTRADA, ne.getCodigoError());
    }

    @Test
    @Order(3)
    @DisplayName("Ítem 3 IT: Modo Supervisor valida PIN ADMIN/SUPERVISOR, rechaza otros roles y registra auditoría")
    void item3ModoSupervisorPinAutorizaYRegistraAuditoria() throws Exception {
        int idCaja = asegurarCajaAbierta();
        assertTrue(idCaja > 0);

        long idIns = asegurarInsumoConCosto("INS-IT3", "Pollo Crispi IT", "UND",
                new BigDecimal("80.0000"), new BigDecimal("6.00"));
        int idCat = asegurarCategoria("Pollo IT");
        Producto prod = asegurarProductoConReceta(idCat, "IT3-POL", "Pollo Broaster 1/4 IT",
                new BigDecimal("25.00"), new BigDecimal("6.00"), idIns, BigDecimal.ONE);

        // Configurar PIN válido para ADMIN (id=1) y MOZO (sin privilegio de anulación)
        int idMozo = buscarIdUsuarioPorUsername("mozo").orElse(3);
        try (Connection conn = ConexionBD.getConnection()) {
            usuarioDAO.actualizarPin(conn, 1, "9876", EncriptadorPassword.generarHash("9876"));
            usuarioDAO.actualizarPin(conn, idMozo, "1234", EncriptadorPassword.generarHash("1234"));
        }

        // Confirmar rechazo directo de PIN vacío y de PIN perteneciente a rol MOZO
        assertThrows(NegocioException.class, () -> supervisorService.autenticarSupervisorPorPin(" "));
        assertThrows(NegocioException.class, () -> supervisorService.autenticarSupervisorPorPin("0000"));
        assertThrows(NegocioException.class, () -> supervisorService.autenticarSupervisorPorPin("1234"));

        int idCajero = buscarIdUsuarioPorUsername("cajero").orElse(2);
        SesionUsuario sesionCajero = autorizacionService.crearSesion(idCajero, "cajero", "CAJERO", "POS-IT3");

        Venta venta = new Venta(0, null, 1, idCajero, null,
                new BigDecimal("21.19"), new BigDecimal("3.81"), new BigDecimal("25.00"),
                "EFECTIVO", new BigDecimal("25.00"), BigDecimal.ZERO);
        venta.setIdempotencyKey(UUID.randomUUID().toString());
        DetalleVenta dv = new DetalleVenta(0, 0, prod.getIdProducto(), 1,
                new BigDecimal("25.00"), new BigDecimal("25.00"));
        ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "T001", "IT000003", new BigDecimal("25.00"));
        Venta registrada = ventaService.registrarVenta(venta, List.of(dv), comp);

        // Un PIN de MOZO no debe autorizar la anulación desde caja
        assertThrows(NegocioException.class, () -> ventaService.anularVentaConSupervisor(
                registrada.getIdVenta(), false, sesionCajero, "1234", "Intento sin rol supervisor"));

        Usuario autorizador = ventaService.anularVentaConSupervisor(
                registrada.getIdVenta(), false, sesionCajero, "9876", "Cliente desistió antes de entrega");
        assertEquals(1, autorizador.getIdUsuario());

        try (Connection conn = ConexionBD.getConnection()) {
            Venta anulada = ventaDAO.buscarVentaPorId(conn, registrada.getIdVenta()).orElseThrow();
            assertEquals("ANULADA", anulada.getEstado());

            List<RegistroAuditoria> regs = auditoriaDAO.listarUltimosEventos(conn, 10);
            assertTrue(regs.stream().anyMatch(r -> r.getAccion() != null
                    && r.getAccion().equals("ANULACION_VENTA_SUPERVISOR")
                    && r.getDatosNuevos() != null
                    && r.getDatosNuevos().contains("Autorizante=" + autorizador.getUsername())));
        }
    }

    @Test
    @Order(4)
    @DisplayName("Ítem 4 IT: Devolución parcial de 1 de 3 ítems actualiza estado, Caja EGRESO y Kardex DEVOLUCION")
    void item4DevolucionParcialRevierteStockRegistraEgresoYBloqueaDobleDevolucion() throws Exception {
        asegurarCajaAbierta();
        int idCat = asegurarCategoria("Devolucion IT");
        long insA = asegurarInsumoConCosto("INS-IT4-A", "Carne Burger IT4", "UND",
                new BigDecimal("50.0000"), new BigDecimal("7.00"));
        long insB = asegurarInsumoConCosto("INS-IT4-B", "Papa Nativa IT4", "UND",
                new BigDecimal("50.0000"), new BigDecimal("2.50"));
        long insC = asegurarInsumoConCosto("INS-IT4-C", "Limón Jarra IT4", "UND",
                new BigDecimal("50.0000"), new BigDecimal("1.50"));

        Producto pBurger = asegurarProductoConReceta(idCat, "IT4-BUG", "Burger Clásica IT4",
                new BigDecimal("30.00"), new BigDecimal("7.00"), insA, BigDecimal.ONE);
        Producto pPapas = asegurarProductoConReceta(idCat, "IT4-PAP", "Papas Nativas IT4",
                new BigDecimal("15.00"), new BigDecimal("2.50"), insB, BigDecimal.ONE);
        Producto pLim = asegurarProductoConReceta(idCat, "IT4-LIM", "Limonada IT4",
                new BigDecimal("12.00"), new BigDecimal("1.50"), insC, BigDecimal.ONE);

        Venta venta = new Venta(0, null, 1, 1, null,
                new BigDecimal("48.31"), new BigDecimal("8.69"), new BigDecimal("57.00"),
                "EFECTIVO", new BigDecimal("60.00"), new BigDecimal("3.00"));
        venta.setIdempotencyKey(UUID.randomUUID().toString());
        DetalleVenta d1 = new DetalleVenta(0, 0, pBurger.getIdProducto(), 1, new BigDecimal("30.00"), new BigDecimal("30.00"));
        DetalleVenta d2 = new DetalleVenta(0, 0, pPapas.getIdProducto(), 1, new BigDecimal("15.00"), new BigDecimal("15.00"));
        DetalleVenta d3 = new DetalleVenta(0, 0, pLim.getIdProducto(), 1, new BigDecimal("12.00"), new BigDecimal("12.00"));
        ComprobantePago comp = new ComprobantePago(0, 0, "BOLETA", "B001", "IT000004", new BigDecimal("57.00"));

        Venta reg = ventaService.registrarVenta(venta, List.of(d1, d2, d3), comp);
        List<DetalleVenta> detallesReg = ventaService.listarDetallesVenta(reg.getIdVenta());
        assertEquals(3, detallesReg.size());

        DetalleVenta detPapas = detallesReg.stream()
                .filter(d -> d.getIdProducto() == pPapas.getIdProducto())
                .findFirst()
                .orElseThrow();

        try (Connection conn = ConexionBD.getConnection()) {
            assertEquals(0, new BigDecimal("49.000").compareTo(insumoDAO.obtenerPorId(conn, insB).getStockActual().setScale(3)));
        }

        SesionUsuario adminSes = autorizacionService.crearSesion(1L, "admin", "ADMIN", "POS-IT4");
        ResultadoDevolucionParcial dev = ventaService.devolverItemsParcial(
                reg.getIdVenta(), List.of(detPapas.getIdDetalleVenta()), adminSes, "Papas crudas IT4");

        assertEquals(0, new BigDecimal("15.00").compareTo(dev.montoReembolso()));
        assertEquals(0, new BigDecimal("42.00").compareTo(dev.nuevoTotalVenta()));
        assertEquals("COMPLETADA", dev.nuevoEstadoVenta());
        assertEquals(1, dev.cantidadInsumosRevertidos());

        try (Connection conn = ConexionBD.getConnection()) {
            assertEquals(0, new BigDecimal("50.000").compareTo(insumoDAO.obtenerPorId(conn, insB).getStockActual().setScale(3)));
            assertTrue(contarKardexTipo(conn, insB, "DEVOLUCION") >= 1);
            assertTrue(contarEgresoDevolucionCaja(conn, reg.getIdVenta()) >= 1);
        }

        List<DetalleVenta> trasDev = ventaService.listarDetallesVenta(reg.getIdVenta());
        assertTrue(trasDev.stream().filter(d -> d.getIdDetalleVenta() == detPapas.getIdDetalleVenta())
                .findFirst().orElseThrow().isDevuelto());

        // Doble devolución del mismo ítem debe rechazarse
        assertThrows(NegocioException.class, () -> ventaService.devolverItemsParcial(
                reg.getIdVenta(), List.of(detPapas.getIdDetalleVenta()), adminSes, "Reintento doble"));

        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        List<Venta> hist = ventaService.listarVentasHistorial(hoy.minusDays(1), hoy, String.valueOf(reg.getIdVenta()));
        assertTrue(hist.stream().anyMatch(v -> v.getIdVenta() == reg.getIdVenta()));
    }

    @Test
    @Order(5)
    @DisplayName("Ítem 5 IT: Priorización manual KDS persiste detalle_pedido.prioridad y ordena DESC")
    void item5KdsPrioridadManualPersisteOrdenYConsultaDesc() throws Exception {
        int idCat = asegurarCategoria("KDS Priority IT");
        long idIns = asegurarInsumoConCosto("INS-IT5", "Masa Base IT5", "UND",
                new BigDecimal("100.0000"), new BigDecimal("3.00"));
        Producto p1 = asegurarProductoConReceta(idCat, "IT5-P1", "Entrada IT5",
                new BigDecimal("12.00"), new BigDecimal("3.00"), idIns, BigDecimal.ONE);
        Producto p2 = asegurarProductoConReceta(idCat, "IT5-P2", "Fondo IT5",
                new BigDecimal("24.00"), new BigDecimal("3.00"), idIns, BigDecimal.ONE);
        Producto p3 = asegurarProductoConReceta(idCat, "IT5-P3", "Especial VIP IT5",
                new BigDecimal("36.00"), new BigDecimal("3.00"), idIns, BigDecimal.ONE);

        Pedido ped = new Pedido(0, 1, null, "M-IT5", "REGISTRADO", new BigDecimal("72.00"));
        DetallePedido dp1 = new DetallePedido(0, 0, p1.getIdProducto(), p1.getNombre(), 1, new BigDecimal("12.00"), new BigDecimal("12.00"), "1");
        DetallePedido dp2 = new DetallePedido(0, 0, p2.getIdProducto(), p2.getNombre(), 1, new BigDecimal("24.00"), new BigDecimal("24.00"), "2");
        DetallePedido dp3 = new DetallePedido(0, 0, p3.getIdProducto(), p3.getNombre(), 1, new BigDecimal("36.00"), new BigDecimal("36.00"), "3");

        Pedido creado = pedidoService.registrarPedidoYEnviarComanda(ped, List.of(dp1, dp2, dp3));
        List<DetallePedido> iniciales;
        try (Connection conn = ConexionBD.getConnection()) {
            iniciales = pedidoDAO.listarItemsPendientesCocina(conn).stream()
                    .filter(d -> d.getIdPedido() == creado.getIdPedido())
                    .toList();
        }
        assertEquals(3, iniciales.size());
        int id1 = iniciales.get(0).getIdDetallePedido();
        int id2 = iniciales.get(1).getIdDetallePedido();
        int id3 = iniciales.get(2).getIdDetallePedido();

        // Priorizar manualmente: id3 al tope, luego id1, luego id2
        pedidoService.reordenarPrioridadKds(List.of(id3, id1, id2));

        List<DetallePedido> reordenados;
        try (Connection conn = ConexionBD.getConnection()) {
            reordenados = pedidoDAO.listarItemsPendientesCocina(conn).stream()
                    .filter(d -> d.getIdPedido() == creado.getIdPedido())
                    .toList();
        }
        assertEquals(List.of(id3, id1, id2),
                reordenados.stream().map(DetallePedido::getIdDetallePedido).toList());
        assertTrue(reordenados.get(0).getPrioridad() > reordenados.get(1).getPrioridad());
        assertTrue(reordenados.get(1).getPrioridad() > reordenados.get(2).getPrioridad());
    }

    @Test
    @Order(6)
    @DisplayName("Ítem 6 IT: Cierre contable mensual bloquea ventas y gastos y luego libera período en finally")
    void item6CierreContableMensualBloqueaVentasYGastos() throws Exception {
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        int anio = hoy.getYear();
        int mes = hoy.getMonthValue();
        int idCaja = asegurarCajaAbierta();

        long idIns = asegurarInsumoConCosto("INS-IT6", "Insumo Cierre IT6", "UND",
                new BigDecimal("40.0000"), new BigDecimal("5.00"));
        int idCat = asegurarCategoria("Cierre IT");
        Producto prod = asegurarProductoConReceta(idCat, "IT6-PRD", "Plato Cierre IT6",
                new BigDecimal("20.00"), new BigDecimal("5.00"), idIns, BigDecimal.ONE);
        int catGastoId = cajaService.listarCategoriasGastoActivas().get(0).getIdCategoriaGasto();

        try {
            CierreContable cierre = cierreContableService.cerrarMes(anio, mes, 1, "Cierre mensual IT6");
            assertTrue(cierre.estaCerrado());
            assertTrue(cierreContableService.estaPeriodoCerrado(anio, mes));
            assertTrue(cierreContableService.obtenerCierre(anio, mes).isPresent());
            assertFalse(cierreContableService.listarCierres().isEmpty());

            // Segundo intento sobre período ya cerrado -> PeriodoCerradoException
            PeriodoCerradoException dupEx = assertThrows(PeriodoCerradoException.class,
                    () -> cierreContableService.cerrarMes(anio, mes, 1, "Duplicado"));
            assertEquals(CodigoError.PERIODO_CERRADO, dupEx.getCodigoError());

            // Bloqueo de nueva venta en mes cerrado
            Venta vBloq = new Venta(0, null, 1, 1, null,
                    new BigDecimal("16.95"), new BigDecimal("3.05"), new BigDecimal("20.00"),
                    "EFECTIVO", new BigDecimal("20.00"), BigDecimal.ZERO);
            vBloq.setIdempotencyKey(UUID.randomUUID().toString());
            DetalleVenta dv = new DetalleVenta(0, 0, prod.getIdProducto(), 1, new BigDecimal("20.00"), new BigDecimal("20.00"));
            ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "T001", "IT000006", new BigDecimal("20.00"));
            assertThrows(PeriodoCerradoException.class,
                    () -> ventaService.registrarVenta(vBloq, List.of(dv), comp));

            // Bloqueo de gasto operativo en mes cerrado
            assertThrows(PeriodoCerradoException.class,
                    () -> cajaService.registrarGastoOperativo(idCaja, 1, new BigDecimal("25.00"), "Gasto bloqueado", catGastoId));
        } finally {
            eliminarCierrePeriodo(anio, mes);
            assertFalse(cierreContableService.estaPeriodoCerrado(anio, mes));
        }
    }

    @Test
    @Order(7)
    @DisplayName("Ítem 7 IT: Cruce turnos programados vs asistencia calcula Atrasos/Faltas y exporta Excel")
    void item7TurnosProgramadosYAsistenciaCalculanAtrasosYFaltasExcel(@TempDir Path tempDir) throws Exception {
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        String diaEs = AsistenciaService.diaSemanaEspanol(hoy.getDayOfWeek());
        int idUserAtraso = 1;
        int idUserFalta = buscarIdUsuarioPorUsername("cajero").orElse(2);

        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement delTp = conn.prepareStatement("DELETE FROM turno_programado WHERE dia_semana = ?")) {
                delTp.setString(1, diaEs);
                delTp.executeUpdate();
            }
            try (PreparedStatement delAs = conn.prepareStatement("DELETE FROM asistencia WHERE fecha = ?")) {
                delAs.setDate(1, java.sql.Date.valueOf(hoy));
                delAs.executeUpdate();
            }

            TurnoProgramadoDAOImpl tpDao = new TurnoProgramadoDAOImpl();
            TurnoProgramado t1 = new TurnoProgramado();
            t1.setIdUsuario(idUserAtraso);
            t1.setDiaSemana(diaEs);
            t1.setTurno("MAÑANA");
            t1.setHoraInicio(LocalTime.of(8, 0));
            t1.setHoraFin(LocalTime.of(16, 0));
            t1.setPublicado(true);
            tpDao.asignarTurno(conn, t1);

            TurnoProgramado t2 = new TurnoProgramado();
            t2.setIdUsuario(idUserFalta);
            t2.setDiaSemana(diaEs);
            t2.setTurno("TARDE");
            t2.setHoraInicio(LocalTime.of(14, 0));
            t2.setHoraFin(LocalTime.of(22, 0));
            t2.setPublicado(true);
            tpDao.asignarTurno(conn, t2);
            tpDao.publicarHorariosSemana(conn);

            AsistenciaDAOImpl asDao = new AsistenciaDAOImpl();
            Asistencia a = new Asistencia();
            a.setIdUsuario(idUserAtraso);
            a.setIdEmpleado(idUserAtraso);
            a.setFecha(hoy);
            a.setHoraEntrada(LocalDateTime.of(hoy, LocalTime.of(8, 25)));
            a.setHoraIngreso(LocalTime.of(8, 25));
            a.setTardanzaMinutos(25);
            a.setEstado("TARDE");
            a.setTipoMarcaje("DNI");
            asDao.registrarEntrada(conn, a);
        }

        List<Asistencia> conCumplimiento = asistenciaService.listarAsistenciaConCumplimiento(hoy);
        Asistencia marcada = conCumplimiento.stream()
                .filter(x -> x.getIdUsuario() == idUserAtraso)
                .findFirst()
                .orElseThrow();
        assertNotNull(marcada.getTurnoProgramadoTexto());
        assertTrue(marcada.getCumplimientoTurno().contains("ATRASO (25 min)"),
                "Debe detectar atraso exacto de 25 min, encontrado: " + marcada.getCumplimientoTurno());

        ReporteCumplimientoTurno reporte = asistenciaService.generarReporteAtrasosYFaltas(hoy, hoy);
        assertTrue(reporte.totalAtrasos() >= 1);
        assertTrue(reporte.totalFaltas() >= 1);
        assertTrue(reporte.filasAtrasos().stream().anyMatch(f -> f.idUsuario() == idUserAtraso));
        assertTrue(reporte.filasFaltas().stream().anyMatch(f -> f.idUsuario() == idUserFalta));

        File xlsx = tempDir.resolve("reporte_cumplimiento_it.xlsx").toFile();
        asistenciaService.exportarReporteCumplimientoExcel(reporte, xlsx);
        assertTrue(xlsx.exists() && xlsx.length() > 1200);
        try (FileInputStream fis = new FileInputStream(xlsx);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            assertEquals("Atrasos y Faltas", wb.getSheetName(0));
            assertTrue(wb.getSheetAt(0).getPhysicalNumberOfRows() >= 4);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Ítem 8 IT: Cierre Z detecta insumos agotados/mínimos y emite Acta de Entrega PDF")
    void item8CierreCajaStockCriticoYActaEntregaPdf(@TempDir Path tempDir) throws Exception {
        long idAgotado = asegurarInsumoConCosto("INS-IT8-AGO", "Aceite Oliva Relevo IT8", "LT",
                BigDecimal.ZERO, new BigDecimal("22.00"));
        long idMinimo = asegurarInsumoConCosto("INS-IT8-MIN", "Sal Marina Relevo IT8", "KG",
                new BigDecimal("3.0000"), new BigDecimal("2.50"));
        assertTrue(idAgotado > 0 && idMinimo > 0);

        List<InsumoCriticoTurno> criticos = cajaService.obtenerInsumosCriticosTurno();
        assertTrue(criticos.stream().anyMatch(c -> "INS-IT8-AGO".equals(c.codigo()) && c.isAgotado()));
        assertTrue(criticos.stream().anyMatch(c -> "INS-IT8-MIN".equals(c.codigo()) && !c.isAgotado()));

        int idCaja = asegurarCajaAbierta();
        File pdfFile = tempDir.resolve("Acta_Entrega_Relevo_IT8.pdf").toFile();
        CajaService.ResultadoCierreConActa res = cajaService.cerrarTurnoConActaPdf(
                idCaja, 1, new BigDecimal("300.00"),
                "Administrador Saliente", "Supervisor Entrante", pdfFile);

        assertEquals("CERRADA", res.cajaCerrada().getEstado());
        assertTrue(res.archivoActaPdf().exists() && res.archivoActaPdf().length() > 1500);
        assertTrue(res.insumosCriticos().stream().anyMatch(c -> "INS-IT8-AGO".equals(c.codigo())));

        try (PdfReader reader = new PdfReader(res.archivoActaPdf().getAbsolutePath())) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            String textoPdf = extractor.getTextFromPage(1);
            assertTrue(textoPdf.contains("ACTA DE ENTREGA DE TURNO"));
            assertTrue(textoPdf.contains("Aceite Oliva Relevo IT8"));
            assertTrue(textoPdf.contains("AGOTADO"));
        }
    }

    private static int asegurarCajaAbierta() throws Exception {
        Optional<Caja> abierta = cajaService.obtenerCajaActiva();
        if (abierta.isPresent()) {
            return abierta.get().getIdCaja();
        }
        return cajaService.abrirTurnoCaja(1, "Caja Operativa IT v5.2", new BigDecimal("300.00"));
    }

    private static Optional<Integer> buscarIdUsuarioPorUsername(String username) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            return usuarioDAO.buscarPorUsername(conn, username).map(Usuario::getIdUsuario);
        }
    }

    private static long asegurarInsumoConCosto(
            String codigo, String nombre, String um, BigDecimal stockInicial, BigDecimal costoUnit) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo ex = insumoDAO.buscarPorCodigo(conn, codigo);
            if (ex != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE insumo SET stock_actual_disponible = ?, stock_minimo = 5.0000, costo_unitario = ?, costo_unitario_actual = ?, activo = 1 WHERE id_insumo = ?")) {
                    ps.setBigDecimal(1, stockInicial);
                    ps.setBigDecimal(2, costoUnit);
                    ps.setBigDecimal(3, costoUnit);
                    ps.setLong(4, ex.getIdInsumo());
                    ps.executeUpdate();
                }
                return ex.getIdInsumo();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO insumo (codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario, costo_unitario_actual, activo) "
                            + "VALUES (?, ?, ?, ?, 5.0000, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, codigo);
                ps.setString(2, nombre);
                ps.setString(3, um);
                ps.setBigDecimal(4, stockInicial);
                ps.setBigDecimal(5, costoUnit);
                ps.setBigDecimal(6, costoUnit);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    return rs.getLong(1);
                }
            }
        }
    }

    private static int asegurarCategoria(String nombreCategoria) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            List<Categoria> existentes = productoDAO.listarCategorias(conn);
            for (Categoria c : existentes) {
                if (nombreCategoria.equalsIgnoreCase(c.getNombre())) {
                    return c.getIdCategoria();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion, activo) VALUES (?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nombreCategoria);
                ps.setString(2, "Categoría Ops IT " + nombreCategoria);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    return rs.getInt(1);
                }
            }
        }
    }

    private static Producto asegurarProductoConReceta(
            int idCategoria, String codigo, String nombre, BigDecimal precioVenta,
            BigDecimal costoEstimado, long idInsumo, BigDecimal qtyReceta) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Producto ex = productoDAO.buscarPorCodigo(conn, codigo);
            int idProd;
            if (ex == null) {
                Producto nuevo = new Producto(0, idCategoria, codigo, nombre, "Producto Ops IT " + nombre,
                        precioVenta, costoEstimado, true);
                productoDAO.crear(conn, nuevo);
                idProd = nuevo.getIdProducto();
            } else {
                idProd = ex.getIdProducto();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE producto SET id_categoria = ?, precio_venta = ?, costo_estimado = ?, disponible = 1 WHERE id_producto = ?")) {
                    ps.setInt(1, idCategoria);
                    ps.setBigDecimal(2, precioVenta);
                    ps.setBigDecimal(3, costoEstimado);
                    ps.setInt(4, idProd);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE cantidad_necesaria = VALUES(cantidad_necesaria)")) {
                ps.setInt(1, idProd);
                ps.setLong(2, idInsumo);
                ps.setBigDecimal(3, qtyReceta);
                ps.executeUpdate();
            }
            return productoDAO.buscarPorCodigo(conn, codigo);
        }
    }

    private static int contarKardexTipo(Connection conn, long idInsumo, String tipo) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM kardex_movimiento WHERE id_insumo = ? AND tipo_movimiento = ?")) {
            ps.setLong(1, idInsumo);
            ps.setString(2, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int contarEgresoDevolucionCaja(Connection conn, int idVenta) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM movimiento_caja WHERE tipo_movimiento = 'EGRESO' AND concepto LIKE ?")) {
            ps.setString(1, "%Venta #" + idVenta + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void eliminarCierrePeriodo(int anio, int mes) throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM cierre_contable WHERE anio = ? AND mes = ?")) {
            ps.setInt(1, anio);
            ps.setInt(2, mes);
            ps.executeUpdate();
        }
    }
}
