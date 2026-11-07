package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.MermaDAO;
import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ReservaDAO;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.MermaDAOImpl;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.ReservaDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Merma;
import com.foodcore.model.MesaUnion;
import com.foodcore.model.Pedido;
import com.foodcore.model.ReservaMesa;
import com.foodcore.service.AnalisisMermasService;
import com.foodcore.service.ReservasService;
import com.foodcore.service.SalonService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ImpresoraTermicaService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de Pruebas Unitarias, Integrales y Visuales para Sub-Fase 4C — OPERACIONES DE SALÓN (FoodCore v2.0).
 * Cubre:
 * - Módulo 8: Gestión de Mesas (Transferir comanda, Unir mesas, Liberación consolidada, Pre-cuenta térmica, Notas por plato).
 * - Módulo 9: Reservas de Mesas (Calendario, Bloqueo preventivo en salón, WhatsApp API).
 * - Módulo 10: Control de Mermas y Desperdicio con Análisis (Gráficos, KPIs de pérdida, Top 5 crítico, Alertas).
 * - Capturas visuales de Salón, Reservas, Análisis de Mermas y POS.
 */
class SpecFoodcoreV20Subfase4CTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots-v2.0-4c");

    private final MesaDAO mesaDAO = new MesaDAOImpl();
    private final ReservaDAO reservaDAO = new ReservaDAOImpl();
    private final MermaDAO mermaDAO = new MermaDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    private final SalonService salonService = new SalonService(mesaDAO);
    private final ReservasService reservasService = new ReservasService(reservaDAO);
    private final AnalisisMermasService mermasService = new AnalisisMermasService(mermaDAO, insumoDAO);
    private final ImpresoraTermicaService impresoraService = new ImpresoraTermicaService();

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        poblarDatosDemostracionSalon();
    }

    @Test
    @DisplayName("4C-1: Transferencia de Mesa - Mover pedido activo de Mesa 02 a Mesa 04 y auditar")
    void testTransferenciaMesa() throws Exception {
        // Asegurar comanda en Mesa 02
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("UPDATE mesa SET estado = 'OCUPADA' WHERE numero = '02'");
            st.execute("UPDATE mesa SET estado = 'LIBRE', id_pedido_actual = NULL WHERE numero = '04'");
            st.execute("INSERT INTO pedido (id_pedido, id_mesa, id_usuario, numero_mesa, total, estado, idempotency_key) VALUES "
                    + "(501, 2, 1, '02', 145.00, 'ENTREGADO', 'TEST-TRANSFER-01') "
                    + "ON DUPLICATE KEY UPDATE numero_mesa = '02', id_mesa = 2, estado = 'ENTREGADO'");
        }

        boolean transferido = salonService.transferirMesa("02", "04", 1);
        assertTrue(transferido, "La transferencia de comanda debe ser exitosa");

        // Validar que el pedido ahora pertenece a Mesa 04
        try (Connection conn = ConexionBD.getConnection()) {
            Pedido p = pedidoDAO.obtenerPorId(conn, 501).orElse(null);
            assertNotNull(p);
            assertEquals("04", p.getNumeroMesa(), "El pedido debe haber cambiado a Mesa 04");

            Map<String, String> estados = mesaDAO.obtenerEstadoTodasMesas(conn);
            assertEquals("LIBRE", estados.get("02"), "Mesa origen debe quedar LIBRE");
            assertEquals("OCUPADA", estados.get("04"), "Mesa destino debe quedar OCUPADA");
        }
    }

    @Test
    @DisplayName("4C-2: Unión de Mesas - Consolidar Mesa 01 con Mesas 06 y 07, y liberación atómica")
    void testUnionMesasYLiberacion() throws Exception {
        int idPedidoUnido = 601;
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("UPDATE mesa SET estado = 'OCUPADA' WHERE numero = '01'");
            st.execute("UPDATE mesa SET estado = 'LIBRE', id_mesa_principal = NULL WHERE numero IN ('06', '07')");
            st.execute("INSERT INTO pedido (id_pedido, id_mesa, id_usuario, numero_mesa, total, estado, idempotency_key) VALUES "
                    + "(601, 1, 1, '01', 320.00, 'REGISTRADO', 'TEST-UNION-01') "
                    + "ON DUPLICATE KEY UPDATE numero_mesa = '01', id_mesa = 1, estado = 'REGISTRADO'");
        }

        boolean unidas = salonService.unirMesas("01", List.of("06", "07"), 1);
        assertTrue(unidas, "La unión de mesas debe ser exitosa");

        try (Connection conn = ConexionBD.getConnection()) {
            List<String> secundarias = salonService.listarMesasSecundarias(idPedidoUnido);
            assertTrue(secundarias.contains("06"));
            assertTrue(secundarias.contains("07"));

            Map<String, String> estados = mesaDAO.obtenerEstadoTodasMesas(conn);
            assertEquals("OCUPADA", estados.get("06"), "Mesa secundaria 06 debe estar OCUPADA");
            assertEquals("OCUPADA", estados.get("07"), "Mesa secundaria 07 debe estar OCUPADA");
        }

        // Liberar todas las mesas unidas al pagar
        boolean liberadas = salonService.liberarMesas(idPedidoUnido);
        assertTrue(liberadas, "Todas las mesas del pedido consolidado deben liberarse");

        try (Connection conn = ConexionBD.getConnection()) {
            Map<String, String> estados = mesaDAO.obtenerEstadoTodasMesas(conn);
            assertEquals("LIBRE", estados.get("01"), "Mesa principal debe quedar LIBRE");
            assertEquals("LIBRE", estados.get("06"), "Mesa secundaria 06 debe quedar LIBRE");
            assertEquals("LIBRE", estados.get("07"), "Mesa secundaria 07 debe quedar LIBRE");
        }
    }

    @Test
    @DisplayName("4C-3: Pre-Cuenta Térmica y Notas por Plato")
    void testPreCuentaYNotasPorPlato() throws Exception {
        Pedido p = new Pedido();
        p.setIdPedido(777);
        p.setNumeroMesa("05");
        p.setTotal(new BigDecimal("98.00"));

        DetallePedido d1 = new DetallePedido();
        d1.setIdProducto(1);
        d1.setNombreProducto("Lomo Saltado Clásico");
        d1.setCantidad(2);
        d1.setPrecioUnitario(new BigDecimal("38.00"));
        d1.setSubtotal(new BigDecimal("76.00"));
        d1.setNotasPreparacion("Término 3/4, sin cebolla");

        DetallePedido d2 = new DetallePedido();
        d2.setIdProducto(2);
        d2.setNombreProducto("Pisco Sour Catedral");
        d2.setCantidad(1);
        d2.setPrecioUnitario(new BigDecimal("22.00"));
        d2.setSubtotal(new BigDecimal("22.00"));
        d2.setNotasPreparacion("Poco jarabe de goma");

        List<String> lineas = impresoraService.formatearLineasPreCuenta(p, List.of(d1, d2));
        assertNotNull(lineas);
        assertFalse(lineas.isEmpty());

        String ticketTexto = String.join("\n", lineas);
        assertTrue(ticketTexto.contains("PRE-CUENTA DE CONSUMO"), "Debe tener encabezado de PRE-CUENTA");
        assertTrue(ticketTexto.contains("*** NO ES COMPROBANTE DE PAGO ***"), "Debe incluir leyenda no fiscal");
        assertTrue(ticketTexto.contains("Término 3/4, sin cebolla"), "Debe incluir las notas de preparación");
        assertTrue(ticketTexto.contains("TOTAL ESTIMADO"), "Debe incluir el total estimado");
    }

    @Test
    @DisplayName("4C-4: Libro de Reservas, Bloqueo Preventivo y Enlace WhatsApp")
    void testReservasYBloqueoSalon() throws Exception {
        ReservaMesa r = new ReservaMesa();
        r.setIdMesa(4);
        r.setNumeroMesa("04");
        r.setClienteNombre("Lorena Benavides");
        r.setClienteTelefono("991234567");
        r.setFecha(LocalDate.now());
        r.setHora(LocalTime.now().plusMinutes(25)); // Reserva dentro de 25 minutos
        r.setPersonas(4);
        r.setNotas("Celebración de cumpleaños");

        int idReserva = reservasService.registrarReserva(r);
        assertTrue(idReserva > 0, "La reserva debe crearse con ID positivo");

        // Validar búsqueda preventiva de bloqueo en salón (margen 45 min)
        Optional<ReservaMesa> resOpt = reservasService.buscarReservaProximaParaMesa("04", 45);
        assertTrue(resOpt.isPresent(), "Debe detectar que Mesa 04 tiene reserva próxima");
        assertEquals("Lorena Benavides", resOpt.get().getClienteNombre());

        // Validar generación de enlace WhatsApp
        String urlWa = r.generarUrlWhatsApp();
        assertNotNull(urlWa);
        assertTrue(urlWa.startsWith("https://wa.me/51991234567?text="));
        assertTrue(urlWa.contains("Lorena"), "Debe personalizar el mensaje con el nombre del cliente");

        // Cambiar estado a SENTADA
        boolean sentada = reservasService.actualizarEstado(idReserva, "SENTADA");
        assertTrue(sentada);
    }

    @Test
    @DisplayName("4C-5: Control de Mermas, Top 5 Insumos y Alertas de Reincidencia")
    void testAnalisisMermasYAlertas() throws Exception {
        int anio = LocalDate.now().getYear();
        int mes = LocalDate.now().getMonthValue();

        // Registrar merma de insumo 1 (Carne lomo)
        int idMerma = mermasService.registrarMerma(1, 1, new BigDecimal("2.5000"), "Quemado", "Exceso de sellado");
        assertTrue(idMerma > 0);

        List<Merma> todas = mermasService.listarTodas();
        assertFalse(todas.isEmpty());

        Map<String, BigDecimal> porMotivo = mermasService.obtenerMermasPorMotivo(anio, mes);
        assertNotNull(porMotivo);
        assertTrue(porMotivo.containsKey("Quemado"));

        List<Merma> topPerdida = mermasService.obtenerTopInsumosPerdida(anio, mes, 5);
        assertFalse(topPerdida.isEmpty());
        assertTrue(topPerdida.get(0).getCostoTotal().compareTo(BigDecimal.ZERO) > 0);

        // Validar evaluación de alertas (insumo 1 tiene más de 3 mermas)
        List<String> alertas = mermasService.obtenerAlertasInsumosCriticos(anio, mes);
        assertNotNull(alertas);
        assertFalse(alertas.isEmpty(), "Debe detectar al menos una alerta para insumos reincidentes");
    }

    @Test
    @DisplayName("4C-6: Capturas Visuales de la Sub-Fase 4C (SPEC-FOODCORE-v2.0)")
    void testCapturasVisualesSubfase4C() throws Exception {
        // 1. Salón de Mesas con Opciones de Transferir y Unir Mesas
        renderizarYCapturar("/com/foodcore/view/SalonMesasView.fxml", 1240, 750, "01_transferir_unir_mesas_v2.0.png");

        // 2. Libro de Reservas de Mesas y WhatsApp API
        renderizarYCapturar("/com/foodcore/view/ReservasView.fxml", 1200, 750, "02_reservas_mesas_v2.0.png");

        // 3. Control de Mermas y Desperdicio con Análisis Gráfico y KPIs
        renderizarYCapturar("/com/foodcore/view/AnalisisMermasView.fxml", 1200, 750, "03_analisis_mermas_kpis_v2.0.png");

        // 4. Salón de Mesas con Mesas Reservadas y Bloqueo Visual
        renderizarYCapturar("/com/foodcore/view/SalonMesasView.fxml", 1240, 750, "04_salon_mesas_reservadas_v2.0.png");

        // 5. Punto de Venta POS con Pre-Cuenta y Notas de Plato
        renderizarYCapturar("/com/foodcore/view/VentaPOSView.fxml", 1200, 750, "05_pos_pre_cuenta_notas_v2.0.png");

        // 6. Sidebar Cockpit con Sección de Operaciones y Mermas Integradas
        renderizarYCapturar("/com/foodcore/view/MainLayout.fxml", 1280, 768, "06_sidebar_cockpit_salon_v2.0.png");
    }

    private void poblarDatosDemostracionSalon() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {

            // Asegurar mesas 01 a 12
            for (int i = 1; i <= 12; i++) {
                st.execute("INSERT IGNORE INTO mesa (id_mesa, numero, estado, version) VALUES (" + i + ", '" + String.format("%02d", i) + "', 'LIBRE', 0)");
            }

            // Ocupar Mesa 01 y Mesa 02 para demostración de transferencias
            st.execute("UPDATE mesa SET estado = 'OCUPADA' WHERE numero IN ('01', '02')");
            st.execute("INSERT INTO pedido (id_pedido, id_mesa, id_usuario, numero_mesa, total, estado, idempotency_key) VALUES "
                    + "(201, 1, 1, '01', 184.00, 'ENTREGADO', 'DEMO-SALON-01'), "
                    + "(202, 2, 1, '02', 125.00, 'LISTO', 'DEMO-SALON-02') "
                    + "ON DUPLICATE KEY UPDATE total = VALUES(total), estado = VALUES(estado)");

            // Insumos y mermas de demostración
            st.execute("INSERT IGNORE INTO insumo (id_insumo, codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario) VALUES "
                    + "(1, 'INS-001', 'Lomo Fino de Res', 'KG', 18.5000, 5.0000, 28.50), "
                    + "(2, 'INS-002', 'Pechuga de Pollo', 'KG', 24.0000, 6.0000, 12.00), "
                    + "(3, 'INS-003', 'Cerveza Cusqueña 330ml', 'UNIDAD', 48.0000, 12.0000, 8.50), "
                    + "(4, 'INS-004', 'Queso Mozzarella', 'KG', 9.5000, 3.0000, 15.00), "
                    + "(5, 'INS-005', 'Papa Amarilla Seleccionada', 'KG', 45.0000, 10.0000, 3.20)");

            // Mermas previas
            st.execute("INSERT IGNORE INTO merma (id_merma, id_insumo, id_usuario, cantidad, motivo, costo_unitario, costo_total, observacion, fecha_hora) VALUES "
                    + "(101, 1, 1, 3.5000, 'Quemado', 28.50, 99.75, 'Sellado pasado en parrilla', NOW() - INTERVAL 1 DAY), "
                    + "(102, 1, 1, 2.0000, 'Error de cocina', 28.50, 57.00, 'Comanda duplicada', NOW() - INTERVAL 2 DAY), "
                    + "(103, 1, 1, 1.8000, 'Sobrante', 28.50, 51.30, 'Recortes no aprovechables', NOW() - INTERVAL 4 DAY), "
                    + "(104, 2, 1, 5.0000, 'Vencido', 12.00, 60.00, 'Caducidad de frío', NOW() - INTERVAL 3 DAY), "
                    + "(105, 3, 1, 12.0000, 'Roto', 8.50, 102.00, 'Botellas rotas en almacén', NOW() - INTERVAL 5 DAY)");

            // Reservas
            st.execute("DELETE FROM reserva_mesa WHERE id_reserva >= 100 OR cliente_nombre LIKE '%Zambrano%'");
            st.execute("INSERT INTO reserva_mesa (id_reserva, id_mesa, cliente_nombre, cliente_telefono, fecha, hora, personas, estado, notas, id_usuario_registro) VALUES "
                    + "(101, 3, 'Mariana Valdivia', '987654321', CURRENT_DATE, '20:00:00', 4, 'CONFIRMADA', 'Mesa ventana aniversario', 1), "
                    + "(102, 5, 'Carlos Zambrano', '998877665', CURRENT_DATE, '13:30:00', 6, 'CONFIRMADA', 'Almuerzo corporativo', 1)");
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
        assertTrue(outFile.exists() && outFile.length() > 1000, "Captura Sub-Fase 4C generada: " + outFile.getAbsolutePath());
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
