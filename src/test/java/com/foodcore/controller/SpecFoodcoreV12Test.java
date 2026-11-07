package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.VentaService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UnidadMedidaFormatter;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite integral E2E para SPEC-FOODCORE-v1.2 (§1–§7):
 * Valida los 11 criterios de aceptación (§6) y genera las 5 capturas de pantalla de §7.
 */
class SpecFoodcoreV12Test {

    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots-v1.2");

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @Test
    @DisplayName("SPEC-FOODCORE-v1.2 §1.1 & §6.1: UnidadMedidaFormatter formatea 120 Unds, 38.2 kg, 150 gr, 500 ml y 1.25 L sin .0000")
    void testUnidadMedidaFormatterSinCerosDecimales() {
        assertEquals("120 Unds", UnidadMedidaFormatter.formatear(new BigDecimal("120.0000"), "UNIDAD"));
        assertEquals("+50 Unds", UnidadMedidaFormatter.formatear(new BigDecimal("50.0000"), "UND", true));
        assertEquals("38.2 kg", UnidadMedidaFormatter.formatear(new BigDecimal("38.2000"), "KG"));
        assertEquals("1.5 kg", UnidadMedidaFormatter.formatear(new BigDecimal("1.5000"), "KG"));
        assertEquals("150 gr", UnidadMedidaFormatter.formatear(new BigDecimal("150.0000"), "GR"));
        assertEquals("500 ml", UnidadMedidaFormatter.formatear(new BigDecimal("500.0000"), "ML"));
        assertEquals("1.25 L", UnidadMedidaFormatter.formatear(new BigDecimal("1.2500"), "LT"));
    }

    @Test
    @DisplayName("SPEC-FOODCORE-v1.2 §2, §3, §4, §5, §6 & §7: Flujo E2E Backoffice, Recetas, Split, Descuento RBAC, Merma, Gasto Caja y 5 Capturas")
    void testFlujoCompletoBackofficeOperacionesYCapturasV12() throws Exception {
        InsumoDAO insumoDAO = new InsumoDAOImpl();
        CajaService cajaService = new CajaService();
        VentaService ventaService = new VentaService();
        AutorizacionService authz = new AutorizacionService();

        // Abrir turno de caja con S/ 100.00
        int idCaja = cajaService.abrirTurnoCaja(1, "Caja Principal 01", new BigDecimal("100.00"));

        // 1. Crear insumo en GR (Carne Molida Angus, stock inicial 1000 gr) y registrar compra (+500 gr -> 1500 gr) (§2.1, §6.2)
        AtomicReference<InventarioController> invCtrlRef = new AtomicReference<>();
        AtomicReference<Parent> invRootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/InventarioView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1180, 720);
                App.applyStylesheets(sc);
                invRootRef.set(root);
                invCtrlRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        InventarioController invCtrl = invCtrlRef.get();
        Insumo carneGr = invCtrl.crearNuevoInsumoTransaccional(
            "INS-CARNE-GR", "Carne Molida Angus", "GR",
            new BigDecimal("1000"), new BigDecimal("200"), new BigDecimal("0.04")
        );
        assertNotNull(carneGr);
        KardexMovimiento kmCompra = invCtrl.registrarCompraTransaccional(
            carneGr.getIdInsumo(), new BigDecimal("500"), new BigDecimal("0.04"), "Factura Compra F001-889"
        );
        assertEquals("ENTRADA_COMPRA", kmCompra.getTipoMovimiento());
        assertEquals(new BigDecimal("1500.0000"), kmCompra.getSaldoPosterior());

        // Crear insumo con stock bajo para evidenciar semáforo REABASTECER (§2.1)
        invCtrl.crearNuevoInsumoTransaccional(
            "INS-PAPA-KG", "Papa Amarilla Bastón", "KG",
            new BigDecimal("38.200"), new BigDecimal("10.000"), new BigDecimal("4.80")
        );
        invCtrl.crearNuevoInsumoTransaccional(
            "INS-ACEITE-LT", "Aceite Vegetal Gourmet", "LT",
            new BigDecimal("3.50"), new BigDecimal("10.00"), new BigDecimal("9.50")
        );

        // 2. Crear platillo con fotografía real y verificar copia a uploads/platillos/ y visualización en POS (§2.2, §6.3)
        File tempImg = generarImagenDemoPlatillo();
        AtomicReference<MenuController> menuCtrlRef = new AtomicReference<>();
        AtomicReference<Parent> menuRootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MenuView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1180, 720);
                App.applyStylesheets(sc);
                menuRootRef.set(root);
                menuCtrlRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        MenuController menuCtrl = menuCtrlRef.get();
        String rutaRelativaFoto = menuCtrl.copiarImagenAUploads(tempImg);
        assertTrue(rutaRelativaFoto.startsWith("uploads/platillos/"));
        assertTrue(new File(rutaRelativaFoto).exists());

        Producto burgerFoto = menuCtrl.guardarPlatilloEnBD(
            1, "HAM-FOTO-01", "Burger Angus Trufada", new BigDecimal("28.00"), true, rutaRelativaFoto
        );
        assertTrue(burgerFoto.getIdProducto() > 0);
        assertEquals(rutaRelativaFoto, burgerFoto.getImagenUrl());

        // 3. Asociar receta (150 gr de Carne Molida Angus + 1 Pan Brioche) y verificar descuento automático al vender (§2.3, §6.4)
        AtomicReference<RecetasController> recCtrlRef = new AtomicReference<>();
        AtomicReference<Parent> recRootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/RecetasView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1180, 720);
                App.applyStylesheets(sc);
                recRootRef.set(root);
                recCtrlRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        RecetasController recCtrl = recCtrlRef.get();
        recCtrl.asociarInsumoARecetaEnBD(burgerFoto.getIdProducto(), carneGr.getIdInsumo(), new BigDecimal("150"));
        recCtrl.asociarInsumoARecetaEnBD(burgerFoto.getIdProducto(), 2L, new BigDecimal("1"));

        // 4. Validar Descuento del 10%: denegado a CAJERO/MOZO y permitido a ADMIN (§3.2, §6.6)
        SesionUsuario sesionCajero = authz.crearSesion(2L, "Maria Cajera", "CAJERO", "POS-01");
        SesionUsuario sesionAdmin = authz.crearSesion(1L, "Administrador", "ADMIN", "POS-01");

        NegocioException exRbacDesc = assertThrows(NegocioException.class, () ->
            ventaService.calcularDescuentoAutorizado(new BigDecimal("100.00"), new BigDecimal("10"), true, sesionCajero)
        );
        assertEquals(CodigoError.ACCESO_DENEGADO, exRbacDesc.getCodigoError());

        BigDecimal descDiezPct = ventaService.calcularDescuentoAutorizado(
            new BigDecimal("28.00"), new BigDecimal("10"), true, sesionAdmin
        );
        assertEquals(new BigDecimal("2.80"), descDiezPct);

        // 5. Vender 2 Burger Angus Trufada dividiendo la cuenta en 2 medios de pago (EFECTIVO y TARJETA) (§3.1, §6.4, §6.5)
        DetalleVenta detBurger = new DetalleVenta(0, 0, burgerFoto.getIdProducto(), 2, new BigDecimal("28.00"), new BigDecimal("56.00"));
        Venta ventaSplit = new Venta(0, null, idCaja, 1, 1,
            new BigDecimal("47.46"), new BigDecimal("8.54"), new BigDecimal("56.00"),
            "MIXTO", new BigDecimal("56.00"), BigDecimal.ZERO);
        ventaSplit.setIdempotencyKey(UUID.randomUUID().toString());
        ComprobantePago compSplit = new ComprobantePago(0, 0, "TICKET", "TCK", "000101", new BigDecimal("56.00"));

        List<VentaService.PagoSplit> pagos = List.of(
            new VentaService.PagoSplit("EFECTIVO", new BigDecimal("28.00")),
            new VentaService.PagoSplit("TARJETA", new BigDecimal("28.00"))
        );
        Venta ventaRegistrada = ventaService.registrarVentaDividida(ventaSplit, List.of(detBurger), compSplit, pagos);
        assertTrue(ventaRegistrada.getIdVenta() > 0);

        // Verificar que la receta descontó automáticamente 2 * 150 gr = 300 gr de Carne Molida Angus (1500 -> 1200 gr)
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo carnePostVenta = insumoDAO.obtenerPorId(conn, carneGr.getIdInsumo());
            assertEquals(new BigDecimal("1200.0000"), carnePostVenta.getStockActualDisponible());
        }

        // 6. Registrar una Merma de 100 gr de Carne Molida Angus y verla reflejada en el Kardex (1200 -> 1100 gr) (§3.3, §6.7)
        MermasController mermasCtrl = new MermasController();
        KardexMovimiento kmMerma = mermasCtrl.registrarMermaTransaccional(
            carneGr.getIdInsumo(), new BigDecimal("100"), "Carne deteriorada en cámara"
        );
        assertEquals("MERMA", kmMerma.getTipoMovimiento());
        assertEquals(new BigDecimal("1100.0000"), kmMerma.getSaldoPosterior());

        // 7. Registrar un Gasto Operativo de Caja Chica (S/ 15.00) y verificar su impacto en el arqueo / Cierre Z (§3.4, §6.8)
        // Saldo inicial: S/ 100.00 + Ventas Efectivo: S/ 28.00 - Gasto Efectivo: S/ 15.00 = S/ 113.00 esperado
        cajaService.registrarGastoOperativo(idCaja, 1, new BigDecimal("15.00"), "Compra urgente de hielo");
        BigDecimal gastosTurno = cajaService.calcularGastosTurno(idCaja);
        assertEquals(new BigDecimal("15.00"), gastosTurno);

        // 8. Verificar RBAC del Sidebar y generar las 5 capturas de pantalla de §7 (§5, §6.9, §6.10, §7)
        AtomicReference<Parent> dashRootRef = new AtomicReference<>();
        AtomicReference<Parent> mainLayoutRootRef = new AtomicReference<>();
        AtomicReference<MainDashboardController> mainLayoutCtrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                // Inicializar DashboardView.fxml (§4)
                FXMLLoader dashLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                Parent dashRoot = dashLoader.load();
                Scene dashScene = new Scene(dashRoot, 1180, 760);
                App.applyStylesheets(dashScene);
                dashRootRef.set(dashRoot);

                // Inicializar MainLayout.fxml (§5)
                Usuario adminUser = new Usuario();
                adminUser.setIdUsuario(1);
                adminUser.setUsername("admin");
                adminUser.setNombreRol("ADMIN");

                FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
                Parent mainRoot = mainLoader.load();
                Scene mainScene = new Scene(mainRoot, 1280, 768);
                App.applyStylesheets(mainScene);
                MainDashboardController mainCtrlLoaded = mainLoader.getController();
                mainCtrlLoaded.aplicarControlAcceso(adminUser);
                mainLayoutRootRef.set(mainRoot);
                mainLayoutCtrlRef.set(mainCtrlLoaded);

                // Refrescar Inventario, Menú y Recetas para capturas
                invCtrl.cargarInventarioDesdeBD();
                menuCtrl.cargarDatosMenu();
                recCtrl.cargarDatosIniciales();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(700);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                // Captura 1: Dashboard Gerencial (01_dashboard_gerencial_v1.2.png)
                Parent dashRoot = dashRootRef.get();
                dashRoot.applyCss();
                dashRoot.layout();
                normalizarAnimaciones(dashRoot);
                dashRoot.applyCss();
                dashRoot.layout();
                guardarSnapshot(dashRoot, "01_dashboard_gerencial_v1.2.png");

                // Captura 2: Inventario de Insumos con semáforo REABASTECER y UnidadMedidaFormatter (02_inventario_insumos_v1.2.png)
                Parent invRoot = invRootRef.get();
                invRoot.applyCss();
                invRoot.layout();
                normalizarAnimaciones(invRoot);
                invRoot.applyCss();
                invRoot.layout();
                guardarSnapshot(invRoot, "02_inventario_insumos_v1.2.png");

                // Captura 3: Gestión de Menú con Fotos (03_menu_fotos_v1.2.png)
                Parent menuRoot = menuRootRef.get();
                menuCtrl.cargarEnFormulario(burgerFoto);
                menuRoot.applyCss();
                menuRoot.layout();
                normalizarAnimaciones(menuRoot);
                menuRoot.applyCss();
                menuRoot.layout();
                guardarSnapshot(menuRoot, "03_menu_fotos_v1.2.png");

                // Captura 4: Recetas y Escandallo (04_recetas_escandallo_v1.2.png)
                Parent recRoot = recRootRef.get();
                recCtrl.cargarRecetaDePlatillo(burgerFoto);
                recRoot.applyCss();
                recRoot.layout();
                normalizarAnimaciones(recRoot);
                recRoot.applyCss();
                recRoot.layout();
                guardarSnapshot(recRoot, "04_recetas_escandallo_v1.2.png");

                // Captura 5: MainLayout con Sidebar y secciones Backoffice (05_sidebar_backoffice_v1.2.png)
                Parent mainRoot = mainLayoutRootRef.get();
                MainDashboardController mainCtrl = mainLayoutCtrlRef.get();
                assertTrue(mainCtrl.getBtnNavInventario().isVisible(), "Admin debe ver Inventario en el Sidebar");
                assertTrue(mainCtrl.getBtnNavRecetas().isVisible(), "Admin debe ver Recetas en el Sidebar");
                assertTrue(mainCtrl.getBtnNavUsuarios().isVisible(), "Admin debe ver Usuarios en el Sidebar");

                mainRoot.applyCss();
                mainRoot.layout();
                normalizarAnimaciones(mainRoot);
                mainRoot.applyCss();
                mainRoot.layout();
                guardarSnapshot(mainRoot, "05_sidebar_backoffice_v1.2.png");

                // Validar Criterio §6.10: El Sidebar oculta opciones de administración a los roles que no son Admin
                Usuario cajeroUser = new Usuario();
                cajeroUser.setIdUsuario(2);
                cajeroUser.setUsername("cajero");
                cajeroUser.setNombreRol("CAJERO");
                mainCtrl.aplicarControlAcceso(cajeroUser);
                assertFalse(mainCtrl.getBtnNavInventario().isVisible(), "Cajero NO debe ver Inventario en el Sidebar");
                assertFalse(mainCtrl.getBtnNavRecetas().isVisible(), "Cajero NO debe ver Recetas en el Sidebar");
                assertFalse(mainCtrl.getBtnNavUsuarios().isVisible(), "Cajero NO debe ver Usuarios en el Sidebar");

                // Validar colapso del Sidebar (§5)
                mainCtrl.handleToggleSidebar(null);
                assertTrue(mainCtrl.isSidebarColapsado(), "El Sidebar debe poder colapsarse");
                mainCtrl.handleToggleSidebar(null);
                assertFalse(mainCtrl.isSidebarColapsado(), "El Sidebar debe poder expandirse nuevamente");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Verificar cierre de turno reflejando el gasto operativo de S/ 15.00 (§3.4, §6.8)
        cajaService.cerrarTurnoCaja(idCaja, 1, new BigDecimal("113.00"));
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT saldo_final_teorico, diferencia FROM turno_caja WHERE id_turno = " + idCaja)) {
            assertTrue(rs.next());
            assertEquals(new BigDecimal("113.00"), rs.getBigDecimal("saldo_final_teorico"));
            assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("diferencia"));
        }
    }

    private File generarImagenDemoPlatillo() throws Exception {
        BufferedImage img = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(240, 90, 34), 160, 120, new Color(217, 119, 6)));
        g.fillRoundRect(0, 0, 160, 120, 20, 20);
        g.setColor(new Color(255, 243, 199));
        g.fillOval(42, 24, 76, 72);
        g.dispose();

        Path temp = Files.createTempFile("burger_angus_trufada_", ".png");
        ImageIO.write(img, "png", temp.toFile());
        return temp.toFile();
    }

    private void normalizarAnimaciones(Node node) {
        if (node == null) {
            return;
        }
        if (!node.isDisabled() && !node.opacityProperty().isBound()) {
            node.setOpacity(1.0);
        }
        if (!node.scaleXProperty().isBound()) {
            node.setScaleX(1.0);
        }
        if (!node.scaleYProperty().isBound()) {
            node.setScaleY(1.0);
        }
        if (!node.translateXProperty().isBound()) {
            node.setTranslateX(0.0);
        }
        if (!node.translateYProperty().isBound()) {
            node.setTranslateY(0.0);
        }
        if (node instanceof ScrollPane sp && sp.getContent() != null) {
            normalizarAnimaciones(sp.getContent());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                normalizarAnimaciones(child);
            }
        }
    }

    private void guardarSnapshot(Parent root, String filename) throws Exception {
        WritableImage fxImage = root.snapshot(new SnapshotParameters(), null);
        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        File outFile = SCREENSHOT_DIR.resolve(filename).toFile();
        ImageIO.write(bufferedImage, "png", outFile);
        assertTrue(outFile.exists() && outFile.length() > 1000, "Captura v1.2 generada: " + outFile.getAbsolutePath());
    }
}
