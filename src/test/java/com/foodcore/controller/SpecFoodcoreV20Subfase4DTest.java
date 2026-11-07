package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.FidelizacionDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PromocionDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.FidelizacionDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.PromocionDAOImpl;
import com.foodcore.model.Cliente;
import com.foodcore.model.CuponCumpleanos;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Promocion;
import com.foodcore.service.DeliveryWebhookService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.MenuQrService;
import com.foodcore.service.PromocionService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de Pruebas de Integración y UI para SPEC-FOODCORE-v2.0 SUB-FASE 4D
 * Crecimiento Comercial: Promociones, Fidelización, Menú QR, Modo Kiosco y Delivery Webhook.
 */
public class SpecFoodcoreV20Subfase4DTest {

    private static final Path SCREENSHOT_DIR = Path.of("target/screenshots-v2.0-4d");

    private final PromocionService promocionService = new PromocionService();
    private final FidelizacionService fidelizacionService = new FidelizacionService();
    private final MenuQrService menuQrService = new MenuQrService();
    private final DeliveryWebhookService deliveryWebhookService = new DeliveryWebhookService();
    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final FidelizacionDAO fidelizacionDAO = new FidelizacionDAOImpl();

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        poblarDatosDemostracionComercial();
    }

    @Test
    @DisplayName("4D-1: Evaluación Automática de Promociones (2x1, 3x2, % y Monto Fijo)")
    void testPromocionesAutomaticasEnComanda() {
        // Caso 1: 2x1 en Alitas BBQ (Producto id 2, precio S/ 22.00)
        // 2 unidades compradas -> 1 gratis (Ahorro S/ 22.00)
        List<DetalleVenta> items2x1 = new ArrayList<>();
        DetalleVenta itemAlitas = new DetalleVenta(1, 0, 2, "Alitas BBQ Crujientes", 2, new BigDecimal("22.00"), new BigDecimal("44.00"));
        items2x1.add(itemAlitas);

        PromocionService.ResultadoEvaluacion res2x1 = promocionService.evaluarPromociones(
                items2x1, new BigDecimal("44.00"), DayOfWeek.FRIDAY, LocalTime.of(20, 0)
        );

        assertTrue(res2x1.tieneDescuento(), "Debe detectar la promoción 2x1");
        assertEquals(new BigDecimal("22.00"), res2x1.getDescuentoTotal(), "El descuento 2x1 debe ser el valor de 1 porción");
        assertEquals(new BigDecimal("22.00"), res2x1.getTotalFinal(), "El total a pagar debe ser S/ 22.00");

        // Caso 2: Promoción Porcentual 20% en Hamburguesas
        List<DetalleVenta> itemsBurger = new ArrayList<>();
        DetalleVenta itemBurger = new DetalleVenta(2, 0, 1, "Hamburguesa Clásica", 2, new BigDecimal("18.50"), new BigDecimal("37.00"));
        itemsBurger.add(itemBurger);

        PromocionService.ResultadoEvaluacion resBurger = promocionService.evaluarPromociones(
                itemsBurger, new BigDecimal("37.00"), DayOfWeek.SATURDAY, LocalTime.of(13, 0)
        );
        assertTrue(resBurger.tieneDescuento());
        // 20% de 37.00 = 7.40
        assertEquals(new BigDecimal("7.40"), resBurger.getDescuentoTotal());
        assertEquals(new BigDecimal("29.60"), resBurger.getTotalFinal());

        // Caso 3: Descuento Fijo S/ 5.00 en total orden > S/ 50.00
        List<DetalleVenta> itemsTotal = new ArrayList<>();
        itemsTotal.add(new DetalleVenta(3, 0, 10, "Parrilla Familiar", 1, new BigDecimal("65.00"), new BigDecimal("65.00")));
        PromocionService.ResultadoEvaluacion resFijo = promocionService.evaluarPromociones(
                itemsTotal, new BigDecimal("65.00"), DayOfWeek.SUNDAY, LocalTime.of(14, 0)
        );
        assertTrue(resFijo.tieneDescuento());
        assertEquals(new BigDecimal("5.00"), resFijo.getDescuentoTotal());
        assertEquals(new BigDecimal("60.00"), resFijo.getTotalFinal());
    }

    @Test
    @DisplayName("4D-2: Fidelización de Clientes: Acumulación y Canje de Puntos (S/ 1 = 1 Pto)")
    void testFidelizacionYCanjeDePuntos() throws Exception {
        int idCliente = 3; // Cliente de prueba
        int puntosIniciales = fidelizacionService.obtenerPuntosCliente(idCliente);

        // 1. Acumular puntos por una compra de S/ 75.00 -> debe ganar 75 puntos
        BigDecimal montoVenta = new BigDecimal("75.00");
        int puntosGanados = fidelizacionService.acumularPuntosPorVenta(idCliente, 501, montoVenta);
        assertEquals(75, puntosGanados);

        int puntosPostAcumulacion = fidelizacionService.obtenerPuntosCliente(idCliente);
        assertEquals(puntosIniciales + 75, puntosPostAcumulacion);

        // 2. Canjear 50 puntos por descuento (10 puntos = S/ 1.00 -> descuento = S/ 5.00)
        BigDecimal totalOrden = new BigDecimal("50.00");
        BigDecimal descuentoCanje = fidelizacionService.canjearPuntos(idCliente, 50, totalOrden);
        assertEquals(new BigDecimal("5.00"), descuentoCanje);

        int puntosFinales = fidelizacionService.obtenerPuntosCliente(idCliente);
        assertEquals(puntosPostAcumulacion - 50, puntosFinales);
    }

    @Test
    @DisplayName("4D-3: Cupón de Cumpleaños Automático (15% OFF en el Mes)")
    void testCuponCumpleanos15Off() throws Exception {
        int idClienteCumpleanero = 1; // Cliente 1 configurado con cumpleaños en el mes actual

        Optional<CuponCumpleanos> optCupon = fidelizacionService.verificarYGenerarCuponCumpleanos(idClienteCumpleanero);
        assertTrue(optCupon.isPresent(), "Debe existir o generarse un cupón de cumpleaños para el cliente del mes");

        CuponCumpleanos cupon = optCupon.get();
        assertEquals(new BigDecimal("15.00"), cupon.getPorcentajeDescuento());
        assertFalse(cupon.isUsado());

        // Marcar cupón como usado
        boolean marcado = fidelizacionService.marcarCuponUsado(cupon.getCodigoCupon());
        assertTrue(marcado);

        Optional<CuponCumpleanos> cuponUsado = fidelizacionService.buscarCuponPorCodigo(cupon.getCodigoCupon());
        assertTrue(cuponUsado.isPresent());
        assertTrue(cuponUsado.get().isUsado(), "El cupón debe quedar registrado como USADO");
    }

    @Test
    @DisplayName("4D-4: Generación de Menú Digital HTML y Código QR con ZXing")
    void testGeneracionMenuQrZXingYHtml() throws Exception {
        File dirQr = new File("target/qr_test");
        dirQr.mkdirs();

        File htmlDestino = new File(dirQr, "menu_digital.html");
        File qrDestino = new File(dirQr, "menu_qr.png");

        // 1. Generar archivo HTML
        menuQrService.generarMenuDigitalHtml(htmlDestino);
        assertTrue(htmlDestino.exists() && htmlDestino.length() > 500, "El HTML del menú digital debe generarse");
        String contenidoHtml = Files.readString(htmlDestino.toPath(), StandardCharsets.UTF_8);
        assertTrue(contenidoHtml.contains("FOODCORE RESTOBAR & GRILL"));
        assertTrue(contenidoHtml.contains("Carta Gastronómica Oficial"));

        // 2. Generar imagen QR con ZXing
        BufferedImage qrImg = menuQrService.generarCodigoQr(htmlDestino.toURI().toString(), 300, 300);
        assertNotNull(qrImg);
        assertEquals(300, qrImg.getWidth());
        assertEquals(300, qrImg.getHeight());

        menuQrService.exportarQrPng(htmlDestino.toURI().toString(), 300, 300, qrDestino);
        assertTrue(qrDestino.exists() && qrDestino.length() > 100, "El archivo PNG del código QR debe guardarse");
    }

    @Test
    @DisplayName("4D-5: Kiosco Autoservicio Autónomo Directo al KDS")
    void testKioscoAutoservicioAutonomo() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KioscoView.fxml"));
                Parent root = loader.load();
                assertNotNull(root);
                KioscoController kiosco = loader.getController();
                assertNotNull(kiosco);

                // Agregar 2 ítems al carrito
                Producto p1 = new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "", new BigDecimal("18.50"), BigDecimal.ZERO, true);
                Producto p2 = new Producto(3, 3, "BEB-01", "Gaseosa Personal", "", new BigDecimal("5.00"), BigDecimal.ZERO, true);

                kiosco.agregarAlCarrito(p1);
                kiosco.agregarAlCarrito(p2);

                assertEquals(2, kiosco.getCarritoItems().size());
                BigDecimal totalEsperado = new BigDecimal("23.50");
                BigDecimal totalCarrito = kiosco.getCarritoItems().stream()
                        .map(DetallePedido::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(totalEsperado, totalCarrito);

                int turnoAntes = kiosco.getTurnoActual();
                assertTrue(turnoAntes >= 100);
            } catch (Exception e) {
                throw new RuntimeException("Error en testKioscoAutoservicioAutonomo", e);
            }
        });
    }

    @Test
    @DisplayName("4D-6: Integración Delivery Webhook Rappi (JSON Mock y Servidor REST)")
    void testDeliveryWebhookRappiJson() throws Exception {
        String payloadMock = DeliveryWebhookService.generarPayloadMockRappi();

        // 1. Procesamiento directo del payload JSON a Pedido
        Pedido ordenRappi = deliveryWebhookService.procesarPayloadJson(payloadMock);
        assertNotNull(ordenRappi);
        assertEquals("DELIVERY", ordenRappi.getTipoPedido());
        assertEquals("RAPPI", ordenRappi.getNumeroMesa());
        assertEquals("Esteban Morales", ordenRappi.getClienteNombre());
        assertEquals("EN_PREPARACION", ordenRappi.getEstado());
        assertEquals(new BigDecimal("47.00"), ordenRappi.getTotal());
        assertFalse(ordenRappi.getDetalles().isEmpty());
        assertEquals(2, ordenRappi.getDetalles().size());

        // 2. Levantar servidor REST local y realizar POST HTTP
        int puertoTest = 8089;
        try {
            deliveryWebhookService.iniciarServidor(puertoTest);
            assertTrue(deliveryWebhookService.isServidorActivo());

            URL url = new URL("http://localhost:" + puertoTest + "/api/v1/delivery/webhook");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payloadMock.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            assertEquals(200, responseCode, "El webhook debe responder HTTP 200 OK");
        } finally {
            deliveryWebhookService.detenerServidor();
            assertFalse(deliveryWebhookService.isServidorActivo());
        }
    }

    @Test
    @DisplayName("4D-7: Capturas Visuales de la Sub-Fase 4D (SPEC-FOODCORE-v2.0)")
    void testCapturasVisualesSubfase4D() throws Exception {
        // 1. Promociones y Descuentos Automáticos con KPIs
        renderizarYCapturar("/com/foodcore/view/PromocionesView.fxml", 1200, 750, "01_promociones_automaticas_v2.0.png");

        // 2. Modo Kiosco de Autoservicio Táctil
        renderizarYCapturar("/com/foodcore/view/KioscoView.fxml", 1200, 750, "02_kiosco_autoservicio_tactil_v2.0.png");

        // 3. Diálogo de Menú Digital QR Dinámico
        renderizarYCapturar("/com/foodcore/view/MenuQrModalView.fxml", 520, 620, "03_menu_digital_qr_modal_v2.0.png");

        // 4. POS con Catálogo de Productos Poblado y Fidelización (Verificación Hotfix B1)
        renderizarYCapturar("/com/foodcore/view/VentaPOSView.fxml", 1240, 750, "04_pos_con_catalogo_y_fidelizacion_v2.0.png");

        // 5. Salón de Mesas con MenuButton de Operaciones (Verificación Hotfix B3)
        renderizarYCapturar("/com/foodcore/view/SalonMesasView.fxml", 1240, 750, "05_salon_operaciones_menu_v2.0.png");

        // 6. Sidebar Cockpit Completo con Sección Comercial y Marketing
        renderizarYCapturar("/com/foodcore/view/MainLayout.fxml", 1280, 768, "06_sidebar_cockpit_comercial_v2.0.png");
    }

    private void poblarDatosDemostracionComercial() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {

            // Asegurar categorías y productos base para POS y Kiosco
            st.execute("INSERT INTO categoria (id_categoria, nombre, descripcion, activo) VALUES "
                    + "(1, 'Cat Hamburguesas', 'Hamburguesas a la parrilla', 1), "
                    + "(2, 'Cat Alitas & Pollo', 'Alitas crujientes', 1), "
                    + "(3, 'Cat Bebidas', 'Bebidas heladas', 1) "
                    + "ON DUPLICATE KEY UPDATE nombre = VALUES(nombre)");

            st.execute("INSERT INTO producto (id_producto, id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible) VALUES "
                    + "(1, 1, 'HAM-01', 'Hamburguesa Clásica', 'Carne 180g, queso cheddar fundido', 18.50, 7.50, 1), "
                    + "(2, 2, 'ALI-01', 'Alitas BBQ x6', '6 alitas bañadas en salsa BBQ', 22.00, 9.00, 1), "
                    + "(3, 3, 'BEB-01', 'Gaseosa Personal', 'Botella 500ml helada', 5.00, 2.00, 1) "
                    + "ON DUPLICATE KEY UPDATE precio_venta = VALUES(precio_venta), disponible = 1");

            // Asegurar cliente con cumpleaños en el mes actual
            int mesActual = LocalDate.now().getMonthValue();
            st.execute("UPDATE cliente SET fecha_nacimiento = '" + LocalDate.now().getYear() + "-" + String.format("%02d", mesActual) + "-15', "
                    + "puntos_acumulados = 350, puntos_fidelidad = 350 WHERE id_cliente = 1");
            st.execute("UPDATE cliente SET puntos_acumulados = 180, puntos_fidelidad = 180 WHERE id_cliente = 2");
            st.execute("UPDATE cliente SET puntos_acumulados = 240, puntos_fidelidad = 240 WHERE id_cliente = 3");

            // Limpiar cupones previos para permitir prueba limpia
            st.execute("DELETE FROM cupon_cumpleanos WHERE id_cliente = 1");
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
        assertTrue(outFile.exists() && outFile.length() > 1000, "Captura Sub-Fase 4D generada: " + outFile.getAbsolutePath());
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
