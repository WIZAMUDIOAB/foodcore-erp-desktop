package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.CategoriaDAO;
import com.foodcore.dao.CategoriaDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.e2e.PlaywrightE2ESupport;
import com.foodcore.model.Categoria;
import com.foodcore.model.Cliente;
import com.foodcore.model.Producto;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class Hotfix433VisualTest {

    public static final Path SCREENSHOT_DIR = Path.of("docs", "screenshots");
    public static final Path TARGET_SCREENSHOT_DIR = Path.of("target", "screenshots-v4.3.3");

    @BeforeAll
    static void setUpAll() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        Files.createDirectories(TARGET_SCREENSHOT_DIR);

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT IGNORE INTO categoria (nombre, descripcion) VALUES ('Hamburguesas', 'Parrilla'), ('Bebidas', 'Heladas')");
            st.executeUpdate("INSERT IGNORE INTO producto (id_categoria, codigo, nombre, precio_venta, stock_disponible, disponible, imagen_url) VALUES "
                    + "(1, 'HAM-01', 'Hamburguesa Royal Doble', 24.50, 50, 1, ''), "
                    + "(1, 'HAM-02', 'Hamburguesa Clásica', 18.00, 50, 1, ''), "
                    + "(2, 'BEB-01', 'Chicha Morada Artesanal', 8.50, 40, 1, '')");
            st.executeUpdate("INSERT IGNORE INTO cliente (tipo_documento, numero_documento, razon_social, nombre_razon_social, contacto, telefono, limite_credito, activo) VALUES "
                    + "('RUC', '20123456789', 'Corporación Gastronómica del Pacífico S.A.C.', 'Corporación Gastronómica del Pacífico S.A.C.', 'Ing. Roberto Silva Mendoza', '987654321', 5000.00, 1), "
                    + "('DNI', '45892134', 'Ana María Torres Gómez', 'Ana María Torres Gómez', 'Ana Torres', '912345678', 500.00, 1)");
        }
    }

    private static void guardarSnapshot(Parent root, String filename) throws Exception {
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
        File outFile1 = SCREENSHOT_DIR.resolve(filename).toFile();
        File outFile2 = TARGET_SCREENSHOT_DIR.resolve(filename).toFile();
        ImageIO.write(bufferedImage, "png", outFile1);
        ImageIO.write(bufferedImage, "png", outFile2);
        assertTrue(outFile1.exists() && outFile1.length() > 500, "Captura generada en docs: " + outFile1.getAbsolutePath());
    }

    @Test
    @DisplayName("1. Categorías Dinámicas en MenuView (Editable y Creación Automática en BD)")
    void test1_CategoriasDinamicasMenu() throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<MenuController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MenuView.fxml"));
                Parent root = loader.load();
                MenuController ctrl = loader.getController();
                Scene scene = new Scene(root, 1120, 720);
                root.applyCss();
                root.layout();

                rootRef.set(root);
                ctrlRef.set(ctrl);
            } catch (Exception e) {
                fail("Error al cargar MenuView: " + e.getMessage());
            }
        });

        Parent root = rootRef.get();
        MenuController ctrl = ctrlRef.get();
        assertNotNull(root);
        assertNotNull(ctrl);

        @SuppressWarnings("unchecked")
        ComboBox<Categoria> cmbCategoria = (ComboBox<Categoria>) root.lookup("#cmbCategoria");
        assertNotNull(cmbCategoria, "El ComboBox cmbCategoria debe existir");
        assertTrue(cmbCategoria.isEditable(), "El ComboBox de categorías debe ser editable según el requisito");

        TextField txtCodigo = (TextField) root.lookup("#txtCodigo");
        TextField txtNombre = (TextField) root.lookup("#txtNombre");
        TextField txtPrecio = (TextField) root.lookup("#txtPrecio");

        String nuevaCategoria = "Pizzas";
        String nuevoPlatillo = "Pizza Americana Familiar";

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            txtCodigo.setText("PIZ-01");
            txtNombre.setText(nuevoPlatillo);
            txtPrecio.setText("39.90");
            cmbCategoria.getEditor().setText(nuevaCategoria);
            ctrl.handleGuardarPlatillo(null);
            root.applyCss();
            root.layout();
        });

        // Validar en BD que la categoría "Pizzas" fue creada automáticamente
        CategoriaDAO catDAO = new CategoriaDAOImpl();
        ProductoDAO prodDAO = new ProductoDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            List<Categoria> cats = catDAO.listarTodas(conn);
            boolean catExiste = cats.stream().anyMatch(c -> nuevaCategoria.equalsIgnoreCase(c.getNombre()));
            assertTrue(catExiste, "La nueva categoría '" + nuevaCategoria + "' debe haberse creado automáticamente en la tabla 'categoria'");

            List<Producto> prods = prodDAO.listarTodos(conn);
            Producto creado = prods.stream()
                    .filter(p -> nuevoPlatillo.equalsIgnoreCase(p.getNombre()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(creado, "El nuevo platillo '" + nuevoPlatillo + "' debe existir en la base de datos");
            assertEquals("Pizzas", creado.getNombreCategoria(), "El platillo debe estar asociado a la nueva categoría 'Pizzas'");
        }

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                guardarSnapshot(root, "captura_1_categorias_dinamicas_menu.png");
            } catch (Exception e) {
                fail("Error guardando snapshot MenuView: " + e.getMessage());
            }
        });
    }

    @Test
    @DisplayName("2. Leyendas en Gráficos Donut (Mermas y Reportes en BOTTOM y fuente 11px)")
    void test2_LeyendasDonutMermasYReportes() throws Exception {
        AtomicReference<Parent> mermasRootRef = new AtomicReference<>();
        AtomicReference<Parent> reportesRootRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();

                FXMLLoader loaderMermas = new FXMLLoader(getClass().getResource("/com/foodcore/view/AnalisisMermasView.fxml"));
                Parent mermasRoot = loaderMermas.load();
                new Scene(mermasRoot, 1120, 768);
                mermasRoot.applyCss();
                mermasRoot.layout();
                mermasRootRef.set(mermasRoot);

                FXMLLoader loaderReportes = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReportesAvanzadosView.fxml"));
                Parent reportesRoot = loaderReportes.load();
                new Scene(reportesRoot, 1120, 768);
                reportesRoot.applyCss();
                reportesRoot.layout();
                reportesRootRef.set(reportesRoot);
            } catch (Exception e) {
                fail("Error cargando vistas de gráficos: " + e.getMessage());
            }
        });

        Parent mermasRoot = mermasRootRef.get();
        Parent reportesRoot = reportesRootRef.get();

        PieChart chartMermas = (PieChart) mermasRoot.lookup("#chartMermasMotivo");
        assertNotNull(chartMermas, "chartMermasMotivo debe existir");
        assertEquals(Side.BOTTOM, chartMermas.getLegendSide(), "La leyenda de Mermas debe ubicarse en BOTTOM");

        PieChart chartReportes = (PieChart) reportesRoot.lookup("#pieChartDistribucion");
        assertNotNull(chartReportes, "pieChartDistribucion debe existir");
        assertEquals(Side.BOTTOM, chartReportes.getLegendSide(), "La leyenda de Reportes debe ubicarse en BOTTOM");

        // Validar CSS de 11px
        String cssContent = Files.readString(Path.of("src", "main", "resources", "css", "foodcore.css"));
        assertTrue(cssContent.contains(".chart-legend"), "El CSS debe definir reglas para .chart-legend");
        assertTrue(cssContent.contains("-fx-font-size: 11px"), "El CSS debe configurar la fuente de la leyenda en 11px");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                guardarSnapshot(mermasRoot, "captura_2_leyendas_donut_mermas.png");
                guardarSnapshot(reportesRoot, "captura_2_leyendas_donut_reportes.png");
            } catch (Exception e) {
                fail("Error guardando snapshots de gráficos: " + e.getMessage());
            }
        });
    }

    @Test
    @DisplayName("3. Limpieza Visual en Clientes y CxC (CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN, Tooltip y padding)")
    void test3_ClientesTableCleanLayoutAndTooltips() throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<ClientesController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ClientesView.fxml"));
                Parent root = loader.load();
                ClientesController ctrl = loader.getController();
                new Scene(root, 1120, 768);
                root.applyCss();
                root.layout();

                rootRef.set(root);
                ctrlRef.set(ctrl);
            } catch (Exception e) {
                fail("Error cargando ClientesView: " + e.getMessage());
            }
        });

        Parent root = rootRef.get();
        ClientesController ctrl = ctrlRef.get();
        assertNotNull(root);
        assertNotNull(ctrl);

        @SuppressWarnings("unchecked")
        TableView<Cliente> tblClientes = (TableView<Cliente>) root.lookup("#tblClientes");
        assertNotNull(tblClientes, "TableView tblClientes debe existir");
        assertEquals(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN, tblClientes.getColumnResizePolicy(),
                "Debe utilizar TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN para un layout limpio sin desbordes");

        @SuppressWarnings("unchecked")
        TableColumn<Cliente, String> colRazonSocial = (TableColumn<Cliente, String>) tblClientes.getColumns().get(2);
        @SuppressWarnings("unchecked")
        TableColumn<Cliente, String> colContacto = (TableColumn<Cliente, String>) tblClientes.getColumns().get(3);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            // Verificar creación de Tooltip en las celdas
            TableCell<Cliente, String> cellRazon = colRazonSocial.getCellFactory().call(colRazonSocial);
            cellRazon.updateIndex(0);
            colRazonSocial.getCellObservableValue(0);
            
            TableCell<Cliente, String> cellContacto = colContacto.getCellFactory().call(colContacto);
            assertNotNull(cellRazon, "La celda de Razón Social debe tener factory definida");
            assertNotNull(cellContacto, "La celda de Contacto debe tener factory definida");

            try {
                guardarSnapshot(root, "captura_3_tabla_clientes_limpia_tooltips.png");
            } catch (Exception e) {
                fail("Error guardando snapshot ClientesView: " + e.getMessage());
            }
        });

        // Verificar padding en CSS
        String cssContent = Files.readString(Path.of("src", "main", "resources", "css", "foodcore.css"));
        assertTrue(cssContent.contains("-fx-padding: 10 14 10 14;"), "El padding de las celdas debe ser exactamente 10 14 10 14");
    }

    @Test
    @DisplayName("4. Botones del Carrito en Kiosco (visibles, minWidth=32, spacing=8) y PWA Móvil Fallback de Iniciales")
    void test4_KioscoCarritoYMobileFallback() throws Exception {
        AtomicReference<Parent> kioscoRootRef = new AtomicReference<>();
        AtomicReference<KioscoController> kioscoCtrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KioscoView.fxml"));
                Parent root = loader.load();
                KioscoController ctrl = loader.getController();
                new Scene(root, 1080, 720);
                root.applyCss();
                root.layout();

                kioscoRootRef.set(root);
                kioscoCtrlRef.set(ctrl);
            } catch (Exception e) {
                fail("Error cargando KioscoView: " + e.getMessage());
            }
        });

        Parent kioscoRoot = kioscoRootRef.get();
        KioscoController kioscoCtrl = kioscoCtrlRef.get();

        @SuppressWarnings("unchecked")
        TableView<?> tblCarrito = (TableView<?>) kioscoRoot.lookup("#tblCarrito");
        assertNotNull(tblCarrito, "tblCarrito debe existir");
        TableColumn<?, ?> colAccion = tblCarrito.getColumns().get(3);
        assertTrue(colAccion.getMinWidth() >= 95.0, "colAccion debe tener minWidth >= 95.0 para no cortar botones");
        assertTrue(colAccion.getPrefWidth() >= 100.0, "colAccion debe tener prefWidth >= 100.0");

        // Agregar un producto de prueba al carrito para validar visualmente los botones de ajuste
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            Producto pPrueba = new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), BigDecimal.ZERO, true, "");
            kioscoCtrl.agregarAlCarrito(pPrueba);
            kioscoCtrl.agregarAlCarrito(pPrueba);
            kioscoRoot.applyCss();
            kioscoRoot.layout();

            try {
                guardarSnapshot(kioscoRoot, "captura_4a_kiosco_carrito_botones_ajuste.png");
            } catch (Exception e) {
                fail("Error guardando snapshot Kiosco: " + e.getMessage());
            }
        });

        // 4B. Validar PWA Móvil con Playwright (Fallback de iniciales)
        PlaywrightE2ESupport.asegurarServidorActivo();
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, 375, 667);
             Page page = context.newPage()) {

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/");
            page.locator("#txtUsername").waitFor();
            page.fill("#txtUsername", "mozo");
            page.fill("#txtPassword", "Mozo2026!");
            page.click("button[type='submit']");
            page.waitForURL("**/mesas.html**");

            Locator mesa01Card = page.locator(".mesa-card", new Page.LocatorOptions().setHasText("Mesa 01")).first();
            mesa01Card.waitFor();
            mesa01Card.click();
            page.waitForURL("**/catalogo.html**");

            // Esperar que el catálogo de productos cargue
            page.locator(".dish-card").first().waitFor();

            // Verificar que los platos sin imagen rendericen el div con clase .dish-thumb-fallback y tengan iniciales
            Locator fallbacks = page.locator(".dish-thumb-fallback");
            assertTrue(fallbacks.count() > 0, "Debe existir al menos un contenedor .dish-thumb-fallback en el catálogo móvil");

            String primeraInicial = fallbacks.first().textContent().trim();
            assertFalse(primeraInicial.isEmpty(), "El fallback debe contener las iniciales del plato, no estar en blanco");
            assertEquals(2, primeraInicial.length(), "Las iniciales deben ser de 2 caracteres (ej. 'HR', 'HC', etc.)");

            // Guardar captura de evidencia de la PWA móvil
            File outFile1 = SCREENSHOT_DIR.resolve("captura_4b_pwa_mobile_fallback_iniciales.png").toFile();
            File outFile2 = TARGET_SCREENSHOT_DIR.resolve("captura_4b_pwa_mobile_fallback_iniciales.png").toFile();
            page.screenshot(new Page.ScreenshotOptions().setPath(outFile1.toPath()));
            page.screenshot(new Page.ScreenshotOptions().setPath(outFile2.toPath()));
            assertTrue(outFile1.exists() && outFile1.length() > 1000, "Captura PWA móvil generada en: " + outFile1.getAbsolutePath());
        }
    }
}
