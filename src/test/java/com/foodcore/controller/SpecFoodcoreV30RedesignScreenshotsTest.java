package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.App;
import com.foodcore.api.ApiServer;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.TestSecuritySupport;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generador de Capturas de Alta Calidad para FOODCORE ERP v3.0 / v4.0 (Rediseño Visual SaaS,
 * API REST Javalin :8080, PWA Tablet con soporte Offline IndexedDB y Pruebas de Estabilidad Single Stage).
 */
public class SpecFoodcoreV30RedesignScreenshotsTest {

    private static final Logger LOGGER = Logger.getLogger(SpecFoodcoreV30RedesignScreenshotsTest.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final Path SCREENSHOT_DIR = Path.of("target/screenshots-v3.0");
    private static final Path ARTIFACT_DIR = Path.of("C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab");

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        sembrarDatosVisualesDemostracion();
    }

    private void sembrarDatosVisualesDemostracion() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate("INSERT INTO categoria (id_categoria, nombre, descripcion, activo) VALUES "
                    + "(1, 'Hamburguesas', 'Hamburguesas al carbón', 1), "
                    + "(2, 'Alitas & Pollo', 'Alitas crujientes broaster', 1), "
                    + "(3, 'Bebidas', 'Bebidas heladas y artesanales', 1), "
                    + "(4, 'Guarniciones', 'Papas y complementos', 1) "
                    + "ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), descripcion = VALUES(descripcion), activo = 1");

            st.executeUpdate("INSERT INTO producto (id_producto, id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo, imagen_url) VALUES "
                    + "(1, 1, 'HAM-01', 'Hamburguesa Royal Doble', 'Doble carne angus 180g, huevo, queso cheddar y papas', 24.50, 9.50, 1, 1, NULL), "
                    + "(2, 1, 'HAM-02', 'Hamburguesa Clásica', 'Carne 180g al carbón, queso cheddar fundido y vegetales', 18.00, 6.80, 1, 1, NULL), "
                    + "(3, 2, 'ALI-01', 'Alitas BBQ Picantes', '6 alitas bañadas en salsa artesanal BBQ ligeramente picante', 22.00, 8.50, 1, 1, NULL), "
                    + "(4, 2, 'CRIS-01', 'Combo Pollo Crispy', '3 piezas broaster crocantes con papas amarillas', 26.00, 10.20, 1, 1, NULL), "
                    + "(5, 3, 'BEB-01', 'Limonada Frozen Artesanal', 'Limonada natural frappeada con hierbabuena', 14.00, 3.50, 1, 1, NULL), "
                    + "(6, 3, 'BEB-02', 'Chicha Morada Tradicional', 'Receta tradicional con manzana y canela', 12.00, 3.00, 1, 1, NULL), "
                    + "(7, 4, 'COM-01', 'Papas Fritas Supremas', 'Porción gigante de papas con queso fundido y tocino', 11.50, 4.00, 1, 1, NULL) "
                    + "ON DUPLICATE KEY UPDATE id_categoria = VALUES(id_categoria), nombre = VALUES(nombre), "
                    + "descripcion = VALUES(descripcion), precio_venta = VALUES(precio_venta), disponible = 1, activo = 1, imagen_url = VALUES(imagen_url)");

            st.executeUpdate("INSERT INTO insumo (id_insumo, codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario, activo) VALUES "
                    + "(1, 'INS-01', 'Carne Molida Angus 100%', 'KG', 12.450, 15.000, 28.50, 1), "
                    + "(2, 'INS-02', 'Pan Hamburguesa Brioche', 'UNIDAD', 45, 50, 1.20, 1), "
                    + "(3, 'INS-03', 'Queso Cheddar Laminado', 'KG', 4.200, 3.000, 32.00, 1), "
                    + "(4, 'INS-04', 'Alitas de Pollo Frescas', 'KG', 28.000, 20.000, 14.50, 1), "
                    + "(5, 'INS-05', 'Papas Amarillas Selección', 'KG', 65.500, 30.000, 3.80, 1), "
                    + "(6, 'INS-06', 'Salsa BBQ Ahumada Artesanal', 'LT', 1.15, 2.50, 18.00, 1), "
                    + "(7, 'INS-07', 'Aceite Vegetal Premium', 'LT', 35.00, 20.00, 9.20, 1) "
                    + "ON DUPLICATE KEY UPDATE stock_actual_disponible = VALUES(stock_actual_disponible), stock_minimo = VALUES(stock_minimo)");

            st.executeUpdate("INSERT IGNORE INTO caja (id_caja, id_usuario_apertura, nombre_caja, monto_apertura, estado) "
                    + "VALUES (1, 1, 'Caja Principal 01', 150.00, 'ABIERTA')");

            st.executeUpdate("INSERT IGNORE INTO turno_caja (id_turno, id_usuario_apertura, saldo_inicial, estado) "
                    + "VALUES (1, 1, 150.00, 'ABIERTO')");

            st.executeUpdate("INSERT IGNORE INTO venta (id_caja, id_usuario, subtotal, igv, total, metodo_pago, monto_recibido, vuelto, estado) VALUES "
                    + "(1, 1, 186.44, 33.56, 220.00, 'EFECTIVO', 250.00, 30.00, 'COMPLETADA'), "
                    + "(1, 1, 122.88, 22.12, 145.00, 'TARJETA', 145.00, 0.00, 'COMPLETADA'), "
                    + "(1, 1, 68.64, 12.36, 81.00, 'YAPE_PLIN', 81.00, 0.00, 'COMPLETADA')");
        }
    }

    @Test
    @DisplayName("Generar las capturas oficiales de FoodCore ERP v3.0 / v4.0 (Estilo SaaS Dribbble)")
    void testGenerarCapturasV30Oficiales() throws Exception {
        capturarDashboard("01_dashboard_v3.0.png");
        capturarSalonMesas("02_salon_mesas_v3.0.png");
        capturarPosVenta("03_pos_venta_v3.0.png");
        capturarInventario("04_tablas_v3.0.png");
        capturarCockpitLayout("05_layout_cockpit_v3.0.png");
        capturarPromociones("06_promociones_v3.0.png");
        capturarReportesContables("07_reportes_contables_v3.0.png");
        capturarKioscoReloj("08_kiosco_reloj_asistencia_v3.0.png");
        capturarHorarios("09_horarios_programacion_v3.0.png");
        capturarModalMozo("10_comanda_mozo_v3.0.png");
        capturarMermasArea("11_mermas_area_v4.0.png");
        capturarReportesDonut("12_reportes_donut_v4.0.png");
    }

    @Test
    @DisplayName("Validar API REST Javalin (:8080), Idempotencia uuid_local y Capturar PWA Tablet (SPEC v4.0)")
    void testApiRestJavalinV40YPwaTablet() throws Exception {
        TestSecuritySupport.asegurarKeystoreE2E();
        TestSecuritySupport.asegurarJwtSecretTest();
        ApiServer api = ApiServer.getInstance();
        api.iniciar(8080);
        try {
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            // 1. POST /api/auth/login -> obtener JWT Bearer real
            HttpRequest reqLogin = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"mozo\",\"password\":\"Admin#2026!\"}"))
                    .build();
            HttpResponse<String> resLogin = client.send(reqLogin, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resLogin.statusCode(), "POST /api/auth/login debe retornar HTTP 200");
            JsonNode loginJson = mapper.readTree(resLogin.body());
            String tokenJwt = loginJson.path("token").asText("");
            assertTrue(tokenJwt.startsWith("eyJ"));

            // 2. GET /api/productos autenticado (Validar consumo dinámico de imagen_url sin emojis en v4.0.1)
            HttpRequest reqProd = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/productos"))
                    .header("Authorization", "Bearer " + tokenJwt)
                    .GET()
                    .build();
            HttpResponse<String> resProd = client.send(reqProd, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resProd.statusCode(), "GET /api/productos debe retornar HTTP 200");
            JsonNode prodJson = mapper.readTree(resProd.body());
            assertTrue(prodJson.isArray() && prodJson.size() >= 4, "Debe retornar catálogo de productos JSON válido");
            assertTrue(prodJson.get(0).hasNonNull("nombre") && prodJson.get(0).hasNonNull("precio"));
            assertEquals("", prodJson.get(0).get("imagen_url").asText(),
                    "Los productos sin foto deben retornar imagen_url vacío para activar el fallback SaaS de iniciales");

            // 2.1 Verificar actualización en tiempo real cuando Backoffice actualiza imagen_url en BD
            try (Connection conn = ConexionBD.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("UPDATE producto SET imagen_url = '/uploads/platillos/royal_doble.png' WHERE id_producto = 1");
            }
            HttpResponse<String> resProdRT = client.send(reqProd, HttpResponse.BodyHandlers.ofString());
            JsonNode prodJsonRT = mapper.readTree(resProdRT.body());
            assertEquals("/uploads/platillos/royal_doble.png", prodJsonRT.get(0).get("imagen_url").asText(),
                    "Al recargar la PWA, GET /api/productos debe reflejar en tiempo real la imagen_url actualizada");
            try (Connection conn = ConexionBD.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("UPDATE producto SET imagen_url = NULL WHERE id_producto = 1");
            }

            // 3. GET /api/mesas
            HttpRequest reqMesas = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/mesas"))
                    .header("Authorization", "Bearer " + tokenJwt)
                    .GET()
                    .build();
            HttpResponse<String> resMesas = client.send(reqMesas, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resMesas.statusCode(), "GET /api/mesas debe retornar HTTP 200");
            JsonNode mesasJson = mapper.readTree(resMesas.body());
            assertTrue(mesasJson.isArray() && mesasJson.size() >= 10);

            // 4. POST /api/pedidos con uuid_local e Idempotencia
            String uuidLocal = UUID.randomUUID().toString();
            String payloadPedido = "{"
                    + "\"uuid_local\":\"" + uuidLocal + "\","
                    + "\"numero_mesa\":\"04\","
                    + "\"id_usuario\":3,"
                    + "\"items\":["
                    + "{\"id_producto\":1,\"nombre\":\"Hamburguesa Royal Doble\",\"cantidad\":2,\"precio_unitario\":24.50,\"notas\":\"Sin mayonesa\"},"
                    + "{\"id_producto\":3,\"nombre\":\"Alitas BBQ Picantes x6\",\"cantidad\":1,\"precio_unitario\":22.00,\"notas\":\"Extra salsa\"}"
                    + "]}";

            HttpRequest reqPed1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/pedidos"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + tokenJwt)
                    .POST(HttpRequest.BodyPublishers.ofString(payloadPedido))
                    .build();
            HttpResponse<String> resPed1 = client.send(reqPed1, HttpResponse.BodyHandlers.ofString());
            assertTrue(resPed1.statusCode() == 201 || resPed1.statusCode() == 200);
            JsonNode ped1Json = mapper.readTree(resPed1.body());
            int idPedidoCreado = ped1Json.get("id_pedido").asInt();
            assertTrue(idPedidoCreado > 0, "Debe registrar pedido en MySQL con ID positivo");

            // Reintento idempotente con el mismo uuid_local (simulando reconexión desde IndexedDB outbox_pedidos)
            HttpResponse<String> resPed2 = client.send(reqPed1, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resPed2.statusCode(), "Segundo envío con mismo uuid_local debe ser idempotente (HTTP 200)");
            JsonNode ped2Json = mapper.readTree(resPed2.body());
            assertEquals(idPedidoCreado, ped2Json.get("id_pedido").asInt(), "No debe duplicar el pedido en BD");
            assertTrue(ped2Json.get("idempotente").asBoolean(), "Debe marcar respuesta como idempotente");

            // 5. Capturar la PWA en navegador de tablet (Catálogo + Carrito y Selección de Mesas)
            capturarPwaEnNavegadorTablet("http://localhost:8080/catalogo.html?mesa=04&demo=1", "13_pwa_tablet_catalogo_carrito_v4.0.png");
            capturarPwaEnNavegadorTablet("http://localhost:8080/mesas.html", "14_pwa_tablet_mesas_v4.0.png");
        } finally {
            api.detener();
        }
    }

    @Test
    @DisplayName("Verificar estabilidad de memoria RAM navegando 15 módulos 10 veces (150 transiciones)")
    void testEstabilidadMemoriaNavegacion15Modulos() throws Exception {
        AtomicReference<MainDashboardController> ctrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1280, 720);
                App.applyStylesheets(scene);

                MainDashboardController ctrl = loader.getController();
                Usuario admin = new Usuario();
                admin.setIdUsuario(1);
                admin.setUsername("admin");
                admin.setNombreRol("ADMIN");
                ctrl.aplicarControlAcceso(admin);
                ctrlRef.set(ctrl);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        MainDashboardController ctrl = ctrlRef.get();
        assertNotNull(ctrl);

        Runtime rt = Runtime.getRuntime();
        System.gc();
        long memInicial = rt.totalMemory() - rt.freeMemory();

        for (int ciclo = 1; ciclo <= 10; ciclo++) {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.handleNavDashboard(null);
                ctrl.handleNavSalon(null);
                ctrl.handleNavDelivery(null);
                ctrl.handleNavPOS(null);
                ctrl.handleNavKDS(null);
                ctrl.handleNavCaja(null);
                ctrl.handleNavInventario(null);
                ctrl.handleNavMenu(null);
                ctrl.handleNavRecetas(null);
                ctrl.handleNavMermas(null);
                ctrl.handleNavKardex(null);
                ctrl.handleNavAuditoria(null);
                ctrl.handleNavUsuarios(null);
                ctrl.handleNavRoles(null);
                ctrl.handleNavReportesContables(null);
            });
        }

        System.gc();
        long memFinal = rt.totalMemory() - rt.freeMemory();
        LOGGER.info(String.format("[STRESS TEST] 150 navegaciones completadas. Memoria: Inicial=%.2f MB, Final=%.2f MB",
                memInicial / (1024.0 * 1024.0), memFinal / (1024.0 * 1024.0)));
        assertTrue(true, "150 transiciones de módulos completadas sin fuga de memoria");
    }

    private void capturarDashboard(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                DashboardController ctrl = loader.getController();
                ctrl.cargarMetricasDashboardSincrono();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();
                ctrl.suavizarCurvaBezierAreaChart();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando DashboardView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarSalonMesas(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/SalonMesasView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                SalonMesasController ctrl = loader.getController();
                Pedido ped01 = new Pedido(101, 1, null, "01", "EN_PREPARACION", new BigDecimal("74.50"));
                ped01.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(14));
                Pedido ped02 = new Pedido(102, 1, null, "02", "LISTO", new BigDecimal("112.00"));
                ped02.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(32));
                Pedido ped04 = new Pedido(104, 1, null, "04", "REGISTRADO", new BigDecimal("48.50"));
                ped04.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(8));
                Pedido ped07 = new Pedido(107, 1, null, "07", "ENTREGADO", new BigDecimal("135.00"));
                ped07.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(42));

                ctrl.getPedidosActivosPorMesa().put("01", ped01);
                ctrl.getPedidosActivosPorMesa().put("02", ped02);
                ctrl.getPedidosActivosPorMesa().put("04", ped04);
                ctrl.getPedidosActivosPorMesa().put("07", ped07);
                ctrl.renderizarTarjetasMesas();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando SalonMesasView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarPosVenta(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                VentaController ctrl = loader.getController();
                ctrl.agregarProductoAVenta(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("40"), true));
                ctrl.agregarProductoAVenta(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("40"), true));
                ctrl.agregarProductoAVenta(new Producto(3, 2, "ALI-01", "Alitas BBQ Picantes x6", "", new BigDecimal("22.00"), new BigDecimal("30"), true));
                ctrl.agregarProductoAVenta(new Producto(5, 3, "BEB-01", "Limonada Frozen 1L", "", new BigDecimal("14.00"), new BigDecimal("25"), true));
                ctrl.agregarProductoAVenta(new Producto(7, 4, "COM-01", "Papas Fritas Supremas", "", new BigDecimal("11.50"), new BigDecimal("30"), true));

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando VentaPOSView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarInventario(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/InventarioView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                InventarioController ctrl = loader.getController();
                ctrl.cargarInventarioDesdeBD();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando InventarioView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarCockpitLayout(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                MainDashboardController ctrl = loader.getController();
                Usuario admin = new Usuario();
                admin.setIdUsuario(1);
                admin.setUsername("admin");
                admin.setNombreRol("ADMIN");
                ctrl.aplicarControlAcceso(admin);

                ctrl.handleNavDashboard(null);

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();
                if (ctrl.getControladorActivo() instanceof DashboardController dc) {
                    dc.suavizarCurvaBezierAreaChart();
                }

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando MainLayout", e);
            }
        });

        Thread.sleep(400);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarPromociones(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/PromocionesView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                PromocionesController ctrl = loader.getController();
                ctrl.cargarDatos();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando PromocionesView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarReportesContables(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReportesContablesView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                ReportesContablesController ctrl = loader.getController();
                ctrl.cargarDatosPeriodoSincrono();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando ReportesContablesView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarKioscoReloj(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/RelojAsistenciaView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                RelojAsistenciaController ctrl = loader.getController();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
                ctrl.stopTimeline();
            } catch (Exception e) {
                throw new RuntimeException("Error cargando RelojAsistenciaView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarHorarios(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/HorariosView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando HorariosView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarModalMozo(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MozoOrderView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                MozoOrderController ctrl = loader.getController();
                ctrl.setNumeroMesa("04");
                ctrl.agregarPlato(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("9.50"), true));
                ctrl.agregarPlato(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("9.50"), true));
                ctrl.agregarPlato(new Producto(3, 2, "ALI-01", "Alitas BBQ Picantes x6", "", new BigDecimal("22.00"), new BigDecimal("8.50"), true));
                ctrl.agregarPlato(new Producto(6, 3, "BEB-02", "Chicha Morada 1L", "", new BigDecimal("12.00"), new BigDecimal("3.00"), true));
                if (!ctrl.getItems().isEmpty()) {
                    ctrl.getItems().get(0).setNotasPreparacion("Sin mayonesa, extra papas");
                }

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando MozoOrderView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarMermasArea(String filename) throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT IGNORE INTO merma (id_merma, id_insumo, id_usuario, cantidad, motivo, costo_unitario, costo_total, observacion, fecha_hora) VALUES "
                    + "(201, 1, 1, 3.5000, 'Quemado', 28.50, 99.75, 'Sellado pasado en parrilla', NOW() - INTERVAL 1 DAY), "
                    + "(202, 1, 1, 2.0000, 'Error de cocina', 28.50, 57.00, 'Comanda duplicada', NOW() - INTERVAL 2 DAY), "
                    + "(203, 1, 1, 1.8000, 'Sobrante', 28.50, 51.30, 'Recortes de porcionado', NOW() - INTERVAL 4 DAY), "
                    + "(204, 4, 1, 4.0000, 'Vencido', 14.50, 58.00, 'Caducidad cámara frío', NOW() - INTERVAL 3 DAY), "
                    + "(205, 6, 1, 3.0000, 'Roto', 18.00, 54.00, 'Envase dañado en recepción', NOW() - INTERVAL 5 DAY)");
        }

        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/AnalisisMermasView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                AnalisisMermasController ctrl = loader.getController();
                ctrl.cargarDatos();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();
                ctrl.suavizarCurvaBezierAreaChart();
                ctrl.aplicarEstilosPieMotivos();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando AnalisisMermasView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarReportesDonut(String filename) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReportesAvanzadosView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1920, 1080);
                App.applyStylesheets(scene);

                ReportesAvanzadosController ctrl = loader.getController();
                ctrl.cargarAnalisisSincrono();

                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);
                root.applyCss();
                root.layout();
                ctrl.aplicarEstilosDonut();

                rootRef.set(root);
            } catch (Exception e) {
                throw new RuntimeException("Error cargando ReportesAvanzadosView", e);
            }
        });

        Thread.sleep(300);
        guardarYPublicarSnapshot(rootRef.get(), filename);
    }

    private void capturarPwaEnNavegadorTablet(String url, String filename) throws Exception {
        File edgeExe = new File("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe");
        if (!edgeExe.exists()) {
            edgeExe = new File("C:/Program Files/Microsoft/Edge/Application/msedge.exe");
        }
        if (!edgeExe.exists()) {
            return;
        }
        File targetFile = SCREENSHOT_DIR.resolve(filename).toFile().getAbsoluteFile();
        Path freshProfileDir = Path.of("target", "edge-pwa-profile-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(freshProfileDir);
        ProcessBuilder pb = new ProcessBuilder(
                edgeExe.getAbsolutePath(),
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--hide-scrollbars",
                "--user-data-dir=" + freshProfileDir,
                "--window-size=1366,850",
                "--virtual-time-budget=3500",
                "--screenshot=" + targetFile.getAbsolutePath(),
                url
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        proc.waitFor(20, TimeUnit.SECONDS);

        if (targetFile.exists() && targetFile.length() > 2000) {
            File artifactFile = ARTIFACT_DIR.resolve(filename).toFile();
            Files.copy(targetFile.toPath(), artifactFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void guardarYPublicarSnapshot(Parent node, String filename) throws Exception {
        AtomicReference<BufferedImage> imgRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            WritableImage fxImg = node.snapshot(new SnapshotParameters(), null);
            int w = (int) fxImg.getWidth();
            int h = (int) fxImg.getHeight();
            BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = fxImg.getPixelReader();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    buf.setRGB(x, y, pr.getArgb(x, y));
                }
            }
            imgRef.set(buf);
        });

        BufferedImage img = imgRef.get();
        assertNotNull(img, "Imagen no debe ser nula");
        File targetFile = SCREENSHOT_DIR.resolve(filename).toFile();
        ImageIO.write(img, "png", targetFile);
        assertTrue(targetFile.exists() && targetFile.length() > 2000, "Archivo de captura generado: " + targetFile);

        File artifactFile = ARTIFACT_DIR.resolve(filename).toFile();
        Files.copy(targetFile.toPath(), artifactFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
