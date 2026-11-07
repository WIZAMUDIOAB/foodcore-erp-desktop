package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CompraDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.CompraDAOImpl;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.dao.impl.CuentaPorPagarDAOImpl;
import com.foodcore.dao.impl.HistorialPrecioDAOImpl;
import com.foodcore.dao.impl.ProveedorDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.Cliente;
import com.foodcore.model.Compra;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.model.HistorialPrecio;
import com.foodcore.model.Insumo;
import com.foodcore.model.Proveedor;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.service.AuthService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de Pruebas Unitarias, Integrales y Visuales para Sub-Fase 4A — FINANZAS Y CONTABILIDAD (FoodCore v2.0).
 */
class SpecFoodcoreV20Subfase4ATest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots-v2.0-4a");

    private final ProveedorDAO proveedorDAO = new ProveedorDAOImpl();
    private final CuentaPorPagarDAO cuentaPorPagarDAO = new CuentaPorPagarDAOImpl();
    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final CuentaPorCobrarDAO cuentaPorCobrarDAO = new CuentaPorCobrarDAOImpl();
    private final HistorialPrecioDAO historialPrecioDAO = new HistorialPrecioDAOImpl();
    private final CompraDAO compraDAO = new CompraDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final CajaDAO cajaDAO = new CajaDAOImpl();
    private final VentaDAO ventaDAO = new VentaDAOImpl();

    private final FinanzasService finanzasService = new FinanzasService();
    private final ExportacionContableService exportacionService = new ExportacionContableService();

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        poblarDatosDemostracionFinanzas();
    }

    @Test
    @DisplayName("4A-1: Exportación PLE SUNAT 14.1 (Ventas Excel) y 8.1 (Compras Excel)")
    void testExportacionPleSunatExcel() throws Exception {
        LocalDate hoy = LocalDate.now();
        LocalDate ini = hoy.withDayOfMonth(1);

        File fVentas = new File("target/test-ventas-14.1.xlsx");
        exportacionService.exportarVentasSunat14_1_Excel(ini, hoy, fVentas);
        assertTrue(fVentas.exists() && fVentas.length() > 2000, "El Excel de ventas SUNAT 14.1 debe haberse generado correctamente");

        File fCompras = new File("target/test-compras-8.1.xlsx");
        exportacionService.exportarComprasSunat8_1_Excel(ini, hoy, fCompras);
        assertTrue(fCompras.exists() && fCompras.length() > 2000, "El Excel de compras SUNAT 8.1 debe haberse generado correctamente");

        File fCajaZ = new File("target/test-resumen-caja-z.pdf");
        exportacionService.exportarResumenCajaMensualPdf(ini, hoy, fCajaZ);
        assertTrue(fCajaZ.exists() && fCajaZ.length() > 1000, "El PDF de Resumen de Caja Z debe haberse generado correctamente");
    }

    @Test
    @DisplayName("4A-2: Proveedores y Cuentas por Pagar con registro de abono transaccional")
    void testGestionProveedoresYCuentasPorPagar() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Proveedor nuevo = new Proveedor();
            nuevo.setRuc("20778899001");
            nuevo.setRazonSocial("Distribuidora Bebidas y Licores S.A.C.");
            nuevo.setContacto("Renato Farfán");
            nuevo.setTelefono("911223344");
            nuevo.setEmail("ventas@bebidas.pe");
            nuevo.setDiasCredito(15);
            int idProv = proveedorDAO.insertar(conn, nuevo);
            assertTrue(idProv > 0, "Proveedor debe insertarse con ID generado");

            CuentaPorPagar cxp = new CuentaPorPagar();
            cxp.setIdProveedor(idProv);
            cxp.setNumeroDocumento("F001-0005521");
            cxp.setFechaEmision(LocalDate.now());
            cxp.setFechaVencimiento(LocalDate.now().plusDays(15));
            cxp.setMontoTotal(850.00);
            cxp.setMontoPagado(0.00);
            cxp.setEstado("PENDIENTE");
            cxp.setObservaciones("Lote de gaseosas y cervezas artesanales");
            int idCxp = cuentaPorPagarDAO.insertar(conn, cxp);
            assertTrue(idCxp > 0, "Cuenta por pagar debe insertarse");

            // Abonar S/ 350.00
            boolean pagoOk = finanzasService.registrarPagoProveedor(idCxp, 350.00, "TRANSFERENCIA", null, 1, "Primer abono");
            assertTrue(pagoOk, "El abono a proveedor debe procesarse con éxito");

            Optional<CuentaPorPagar> cxpActual = cuentaPorPagarDAO.buscarPorId(conn, idCxp);
            assertTrue(cxpActual.isPresent());
            assertEquals(350.00, cxpActual.get().getMontoPagado(), 0.01);
            assertEquals(500.00, cxpActual.get().getSaldo(), 0.01);
            assertEquals("PENDIENTE", cxpActual.get().getEstado());

            // Liquidar saldo restante S/ 500.00
            finanzasService.registrarPagoProveedor(idCxp, 500.00, "TRANSFERENCIA", null, 1, "Liquidación final");
            Optional<CuentaPorPagar> cxpLiquidada = cuentaPorPagarDAO.buscarPorId(conn, idCxp);
            assertTrue(cxpLiquidada.isPresent());
            assertEquals(850.00, cxpLiquidada.get().getMontoPagado(), 0.01);
            assertEquals("PAGADA", cxpLiquidada.get().getEstado());

            // Orden de Compra PDF
            List<Insumo> ins = insumoDAO.listarTodos(conn);
            File doc = new File("target/test-orden-compra.pdf");
            finanzasService.generarOrdenCompraPdf(idProv, ins, doc);
            assertTrue(doc.exists() && doc.length() > 1000, "El PDF de orden de compra debe generarse");
        }
    }

    @Test
    @DisplayName("4A-3: Clientes Corporativos, Venta a Crédito y Cuentas por Cobrar")
    void testClientesCorporativosYCuentasPorCobrar() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Cliente cli = new Cliente();
            cli.setTipoDocumento("RUC");
            cli.setNumeroDocumento("20998877665");
            cli.setRazonSocial("Agencia de Viajes & Turismo Los Andes S.A.");
            cli.setContacto("Mirella Paredes");
            cli.setTelefono("955667788");
            cli.setEmail("turismo@losandes.pe");
            cli.setLimiteCredito(4000.00);
            cli.setDiasCredito(30);
            int idCli = clienteDAO.insertar(conn, cli);
            assertTrue(idCli > 0, "Cliente corporativo registrado");

            // Registrar venta a crédito
            Venta vta = new Venta(
                    0, 101, 1, 1, idCli,
                    new BigDecimal("300.00"),
                    new BigDecimal("54.00"),
                    new BigDecimal("354.00"),
                    "CREDITO",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
            int idVta = finanzasService.registrarVentaCreditoCorporativo(vta, idCli, 30);
            assertTrue(idVta > 0, "Venta a crédito registrada con éxito");

            List<CuentaPorCobrar> facturas = cuentaPorCobrarDAO.listarPorCliente(conn, idCli);
            assertFalse(facturas.isEmpty(), "Debe haberse generado la cuenta por cobrar asociada");
            CuentaPorCobrar cxc = facturas.get(0);
            assertEquals(354.00, cxc.getMontoTotal(), 0.01);
            assertEquals(354.00, cxc.getSaldo(), 0.01);
            assertEquals("PENDIENTE", cxc.getEstado());

            // Cobro de abono S/ 200.00
            boolean cobroOk = finanzasService.registrarCobroCliente(cxc.getIdCuentaCobrar(), 200.00, "TRANSFERENCIA", null, 1, "Abono cliente");
            assertTrue(cobroOk, "Cobro a cuenta corporativa registrado");

            Optional<CuentaPorCobrar> cxcPost = cuentaPorCobrarDAO.buscarPorId(conn, cxc.getIdCuentaCobrar());
            assertTrue(cxcPost.isPresent());
            assertEquals(200.00, cxcPost.get().getMontoCobrado(), 0.01);
            assertEquals(154.00, cxcPost.get().getSaldo(), 0.01);

            // Estado de cuenta en PDF
            File doc = new File("target/test-estado-cuenta-cliente.pdf");
            finanzasService.generarEstadoCuentaClientePdf(idCli, doc);
            assertTrue(doc.exists() && doc.length() > 1000, "El PDF de estado de cuenta cliente debe generarse");
        }
    }

    @Test
    @DisplayName("4A-4: Historial de Precios con motivo obligatorio (§4.1)")
    void testHistorialPreciosConMotivoObligatorio() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            boolean cambioOk = finanzasService.registrarCambioPrecioConMotivo(
                    "PRODUCTO", 1, "Hamburguesa Clásica", 20.00, 22.00, 1, "admin", "Incremento de costo de insumo carne de res"
            );
            assertTrue(cambioOk, "El cambio de precio debe registrarse");

            List<HistorialPrecio> lista = historialPrecioDAO.listarTodos(conn);
            assertTrue(lista.stream().anyMatch(h -> "Hamburguesa Clásica".equals(h.getNombreEntidad())), "Debe registrarse en bitácora");

            // Validar que motivo corto lance excepción
            assertThrows(IllegalArgumentException.class, () -> {
                finanzasService.registrarCambioPrecioConMotivo(
                        "PRODUCTO", 1, "Hamburguesa Clásica", 22.00, 25.00, 1, "admin", "n/a"
                );
            }, "Motivo inferior a 5 caracteres debe ser rechazado");
        }
    }

    @Test
    @DisplayName("4A-5: Exportación universal de reportes avanzados a PDF, Excel y CSV (§5.2)")
    void testReportesAvanzadosExportacionMultiformato() throws Exception {
        List<String> headers = List.of("Concepto", "Transacciones", "Subtotal", "Total S/", "Participación");
        List<List<String>> filas = List.of(
                List.of("EFECTIVO", "15 ops", "S/ 1200.00", "S/ 1416.00", "55.0%"),
                List.of("TARJETA", "8 ops", "S/ 650.00", "S/ 767.00", "30.0%"),
                List.of("YAPE", "5 ops", "S/ 320.00", "S/ 377.60", "15.0%")
        );

        File fCsv = new File("target/test-rep-avanzado.csv");
        exportacionService.exportarReporteGenericoCsv(headers, filas, fCsv);
        assertTrue(fCsv.exists() && fCsv.length() > 50, "El reporte CSV debe generarse correctamente");

        File fExcel = new File("target/test-rep-avanzado.xlsx");
        exportacionService.exportarReporteGenericoExcel("PAGOS", headers, filas, fExcel);
        assertTrue(fExcel.exists() && fExcel.length() > 2000, "El reporte Excel debe generarse correctamente");

        File fPdf = new File("target/test-rep-avanzado.pdf");
        exportacionService.exportarReporteGenericoPdf("Reporte de Medios de Pago", headers, filas, fPdf);
        assertTrue(fPdf.exists() && fPdf.length() > 1000, "El reporte PDF debe generarse correctamente");
    }

    @Test
    @DisplayName("4A-6: Generación de las capturas demostrativas de la Sub-Fase 4A Finanzas")
    void testGenerarCapturasSubfase4A() throws Exception {
        poblarDatosDemostracionFinanzas();

        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setUsername("admin");
        admin.setNombreRol("ADMIN");
        AuthService.setUsuarioSesionActual(admin);

        // 1. Reportes Contables y PLE SUNAT
        renderizarYCapturar("/com/foodcore/view/ReportesContablesView.fxml", 1200, 750, "01_reportes_contables_ple_v2.0.png");

        // 2. Maestro de Proveedores y Ficha Comercial
        renderizarYCapturar("/com/foodcore/view/ProveedoresView.fxml", 1200, 750, "02_proveedores_ficha_v2.0.png");

        // 3. Cuentas por Pagar con Semáforo de Vencimiento
        renderizarYCapturar("/com/foodcore/view/CuentasPorPagarView.fxml", 1200, 750, "03_cuentas_por_pagar_v2.0.png");

        // 4. Cartera de Clientes Corporativos
        renderizarYCapturar("/com/foodcore/view/ClientesView.fxml", 1200, 750, "04_clientes_corporativos_v2.0.png");

        // 5. Cuentas por Cobrar al Crédito
        renderizarYCapturar("/com/foodcore/view/CuentasPorCobrarView.fxml", 1200, 750, "05_cuentas_por_cobrar_v2.0.png");

        // 6. Historial de Precios y Auditoría
        renderizarYCapturar("/com/foodcore/view/HistorialPreciosView.fxml", 1200, 750, "06_historial_precios_v2.0.png");

        // 7. Reportes Avanzados y Business Intelligence
        renderizarYCapturar("/com/foodcore/view/ReportesAvanzadosView.fxml", 1200, 750, "07_reportes_avanzados_bi_v2.0.png");

        // 8. Cockpit Sidebar con Sección Finanzas
        renderizarYCapturar("/com/foodcore/view/MainLayout.fxml", 1280, 768, "08_sidebar_cockpit_v2.0.png");
    }

    private void poblarDatosDemostracionFinanzas() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {

            // Asegurar ventas demostrativas para el período contable
            st.execute("INSERT IGNORE INTO turno_caja (id_turno, id_usuario_apertura, saldo_inicial, estado) VALUES (1, 1, 150.00, 'ABIERTO')");
            st.execute("INSERT IGNORE INTO caja (id_caja, id_usuario_apertura, nombre_caja, monto_apertura, monto_cierre, estado, fecha_apertura) "
                    + "VALUES (1, 1, 'Caja Principal 01', 150.00, 520.00, 'ABIERTA', NOW())");

            st.execute("INSERT IGNORE INTO pedido (id_pedido, id_mesa, id_usuario, numero_mesa, tipo_pedido, cliente_nombre, estado, total, idempotency_key) VALUES "
                    + "(101, 1, 1, '01', 'MESA', 'Cliente Crédito Demo', 'ENTREGADO', 354.00, 'DEMO-FIN-101'), "
                    + "(301, 1, 1, '01', 'MESA', 'Corporación Minera Andina S.A.C.', 'ENTREGADO', 245.00, 'DEMO-FIN-01'), "
                    + "(302, 2, 1, '02', 'MESA', 'Consultora IT Systems', 'ENTREGADO', 180.00, 'DEMO-FIN-02'), "
                    + "(303, 3, 1, '03', 'MESA', 'Roberto Mendoza Gómez', 'ENTREGADO', 125.00, 'DEMO-FIN-03')");

            st.execute("INSERT IGNORE INTO venta (id_venta, id_pedido, id_caja, id_usuario, subtotal, igv, total, metodo_pago, monto_recibido, vuelto, estado, fecha_hora, idempotency_key) VALUES "
                    + "(101, 301, 1, 1, 207.63, 37.37, 245.00, 'TRANSFERENCIA', 245.00, 0.00, 'COMPLETADA', NOW() - INTERVAL 2 DAY, 'IDEM-VF-01'), "
                    + "(102, 302, 1, 1, 152.54, 27.46, 180.00, 'CREDITO', 0.00, 0.00, 'COMPLETADA', NOW() - INTERVAL 1 DAY, 'IDEM-VF-02'), "
                    + "(103, 303, 1, 1, 105.93, 19.07, 125.00, 'EFECTIVO', 130.00, 5.00, 'COMPLETADA', NOW(), 'IDEM-VF-03')");

            st.execute("INSERT IGNORE INTO movimiento_caja (id_movimiento, id_caja, id_usuario, tipo_movimiento, medio_pago, monto, concepto, fecha_hora) VALUES "
                    + "(1, 1, 1, 'VENTA', 'EFECTIVO', 125.00, 'Venta demo #103', NOW()), "
                    + "(2, 1, 1, 'VENTA', 'TARJETA', 245.00, 'Venta demo #101', NOW() - INTERVAL 2 DAY), "
                    + "(3, 1, 1, 'EGRESO', 'EFECTIVO', -30.00, 'Compra insumos menores', NOW() - INTERVAL 1 DAY)");
        }
    }

    private void renderizarYCapturar(String fxmlPath, double width, double height, String filename) throws Exception {
        AtomicReference<BufferedImage> imageRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                Parent root = loader.load();
                Scene scene = new Scene(root, width, height);
                App.applyStylesheets(scene);
                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);

                WritableImage fxImage = root.snapshot(new SnapshotParameters(), null);
                int imgW = (int) fxImage.getWidth();
                int imgH = (int) fxImage.getHeight();
                BufferedImage bufferedImage = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
                PixelReader reader = fxImage.getPixelReader();
                for (int y = 0; y < imgH; y++) {
                    for (int x = 0; x < imgW; x++) {
                        bufferedImage.setRGB(x, y, reader.getArgb(x, y));
                    }
                }
                imageRef.set(bufferedImage);
            } catch (Exception e) {
                throw new RuntimeException("Error capturando " + fxmlPath, e);
            }
        });

        BufferedImage img = imageRef.get();
        assertNotNull(img, "La imagen generada no debe ser nula");
        File outFile = SCREENSHOT_DIR.resolve(filename).toFile();
        ImageIO.write(img, "png", outFile);
        assertTrue(outFile.exists() && outFile.length() > 1000, "Captura Sub-Fase 4A generada: " + outFile.getAbsolutePath());
    }

    private void normalizarAnimaciones(Node node) {
        if (node == null) return;
        if (!node.isDisabled() && !node.opacityProperty().isBound()) {
            node.setOpacity(1.0);
        }
        if (!node.scaleXProperty().isBound()) {
            node.setScaleX(1.0);
        }
        if (!node.scaleYProperty().isBound()) {
            node.setScaleY(1.0);
        }
        if (node instanceof ScrollPane sp) {
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                normalizarAnimaciones(child);
            }
        }
    }
}
