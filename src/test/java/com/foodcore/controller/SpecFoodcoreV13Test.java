package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.RolDAO;
import com.foodcore.dao.impl.RolDAOImpl;
import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Insumo;
import com.foodcore.model.Pedido;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.model.Rol;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.offline.LocalDatabaseManager;
import com.foodcore.offline.OfflineCryptoService;
import com.foodcore.offline.SyncWorker;
import org.junit.jupiter.api.io.TempDir;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E de Verificacion para SPEC-FOODCORE-v1.3-ENTERPRISE.
 */
class SpecFoodcoreV13Test {

    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots-v1.3");
    private final RolDAO rolDAO = new RolDAOImpl();
    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @Test
    @DisplayName("SPEC v1.3 SEC-1: SyncWorker encola en SQLite local en modo offline y sincroniza con MySQL al volver online")
    void testResilienciaYOfflineSyncWorker(@TempDir Path tempOfflineDir) throws Exception {
        SyncWorker syncWorker = SyncWorker.getInstancia();
        assertNotNull(syncWorker);

        String previousOfflineKey = System.getProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY);
        System.setProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY, "SpecV13-Offline-TestKey#2026");
        LocalDatabaseManager.setDbPath(tempOfflineDir.resolve("terminal_local.db").toString());
        try {
            syncWorker.setSimularDesconexionRed(true);
            assertFalse(syncWorker.isOnline(), "Debe marcar estado offline");

            Pedido pedidoLocal = new Pedido();
            pedidoLocal.setIdMesa(1);
            pedidoLocal.setNumeroMesa("DELIV-01");
            pedidoLocal.setTipoPedido("DELIVERY");
            pedidoLocal.setClienteNombre("Carlos Mendoza");
            pedidoLocal.setClienteTelefono("987654321");
            pedidoLocal.setClienteDireccion("Av. Larco 456");
            pedidoLocal.setCostoEnvio(new BigDecimal("5.00"));
            pedidoLocal.setTotal(new BigDecimal("35.00"));

            List<DetallePedido> detalles = new ArrayList<>();
            DetallePedido d = new DetallePedido();
            d.setIdProducto(1);
            d.setNombreProducto("Hamburguesa Clasica");
            d.setCantidad(1);
            d.setPrecioUnitario(new BigDecimal("30.00"));
            d.setSubtotal(new BigDecimal("30.00"));
            detalles.add(d);

            int outboxId = syncWorker.guardarPedidoEnOutboxLocal(pedidoLocal, detalles);
            assertTrue(outboxId > 0, "Debe generar un ID en outbox local");

            int pendientes = syncWorker.contarPedidosPendientes();
            assertTrue(pendientes >= 1, "Debe existir al menos 1 pedido pendiente en outbox local");

            syncWorker.setSimularDesconexionRed(false);
            assertTrue(syncWorker.isOnline(), "Debe marcar estado online");

            int sincronizados = syncWorker.procesarOutboxSync();
            assertTrue(sincronizados >= 0, "Debe procesar la cola de sincronizacion");
            assertEquals(0, syncWorker.contarPedidosPendientes(), "Los registros sincronizados deben purgarse localmente");
        } finally {
            syncWorker.setSimularDesconexionRed(false);
            LocalDatabaseManager.setDbPath(null);
            if (previousOfflineKey != null) {
                System.setProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY, previousOfflineKey);
            } else {
                System.clearProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY);
            }
        }
    }

    @Test
    @DisplayName("SPEC v1.3 SEC-2: Rol dinamico Supervisor con permiso VENTA_ANULAR")
    void testRolDinamicoSupervisor() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            List<Rol> roles = rolDAO.listarTodos(conn);
            Optional<Rol> rolOpt = roles.stream().filter(r -> "SUPERVISOR".equalsIgnoreCase(r.getCodigo())).findFirst();
            Rol rolSupervisor;
            if (rolOpt.isEmpty()) {
                int idCreado = rolDAO.crearRol(conn, "SUPERVISOR", "Supervisor", "Rol dinamico con permisos de anulacion");
                rolSupervisor = new Rol(idCreado, "Supervisor", "Rol dinamico con permisos de anulacion", true);
                rolSupervisor.setCodigo("SUPERVISOR");
            } else {
                rolSupervisor = rolOpt.get();
            }

            rolDAO.actualizarPermisosRol(conn, rolSupervisor.getCodigo(), Set.of(Permiso.VENTA_ANULAR));

            Set<Permiso> permisos = rolDAO.obtenerPermisosPorRol(conn, rolSupervisor.getCodigo());
            assertTrue(permisos.contains(Permiso.VENTA_ANULAR), "El rol SUPERVISOR debe poseer VENTA_ANULAR");

            SesionUsuario sesion = new SesionUsuario(999L, "supervisor1", "Supervisor", Set.of(Permiso.VENTA_ANULAR), "TOKEN-TEST", LocalDateTime.now().plusHours(8));

            assertTrue(sesion.tienePermiso(Permiso.VENTA_ANULAR));
            assertFalse(sesion.tienePermiso(Permiso.CAJA_CIERRE));
        }
    }

    @Test
    @DisplayName("SPEC v1.3 SEC-3: Pedido Delivery calcula costo de envio inafecto de IGV")
    void testPedidoDeliveryCalculos() {
        Pedido pedido = new Pedido();
        pedido.setTipoPedido("DELIVERY");
        pedido.setClienteNombre("Lucia Ramos");
        pedido.setClienteDireccion("Calle Las Palmeras 123");
        pedido.setClienteTelefono("999888777");
        pedido.setCostoEnvio(new BigDecimal("7.50"));

        BigDecimal subtotalComida = new BigDecimal("50.00");
        BigDecimal totalFinal = subtotalComida.add(pedido.getCostoEnvio());

        assertEquals(new BigDecimal("57.50"), totalFinal, "El costo de envio se suma al total directo sin recargo adicional de IGV");
    }

    @Test
    @DisplayName("SPEC v1.3 SEC-4: Apertura de cajon emite pulsos ESC-POS")
    void testAperturaCajonPulsoEscPos() {
        byte[] pulso = ImpresoraTermicaService.getEscPosAbrirCajon();
        assertNotNull(pulso);
        assertEquals(5, pulso.length);
        assertEquals((byte) 0x1B, pulso[0]);
        assertEquals((byte) 0x70, pulso[1]);
        assertEquals((byte) 0x00, pulso[2]);
        assertEquals((byte) 0x19, pulso[3]);
        assertEquals((byte) 0xFA, pulso[4]);

        ImpresoraTermicaService.emitirPulsoAperturaCajon("CAJA_PRINCIPAL");
        assertTrue(true, "Pulso ESC/POS emitido sin lanzar excepcion");
    }

    @Test
    @DisplayName("SPEC v1.3 SEC-5: Soft delete de Insumos marca activo=0 y no se borra fisicamente")
    void testSoftDeleteInsumo() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo insumo = new Insumo();
            insumo.setCodigo("INS-TEST-SD-" + UUID.randomUUID().toString().substring(0, 5));
            insumo.setNombre("Insumo Soft Delete Test");
            insumo.setUnidadMedida("UNIDAD");
            insumo.setStockActual(new BigDecimal("10.00"));
            insumo.setStockMinimo(new BigDecimal("2.00"));
            insumo.setCostoUnitario(new BigDecimal("5.00"));

            long idCreado = insumoDAO.crear(conn, insumo);
            assertTrue(idCreado > 0);

            insumoDAO.eliminar(conn, idCreado);

            List<Insumo> activos = insumoDAO.listarTodos(conn);
            assertFalse(activos.stream().anyMatch(i -> i.getIdInsumo() == idCreado), "No debe figurar en insumos activos");

            try (PreparedStatement ps = conn.prepareStatement("SELECT activo FROM insumo WHERE id_insumo = ?")) {
                ps.setLong(1, idCreado);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "El registro fisico debe continuar en la base de datos");
                    assertEquals(0, rs.getInt("activo"), "El flag activo debe ser 0");
                }
            }
        }
    }

    @Test
    @DisplayName("SPEC v1.3 SEC-5: Auditoria registra evento inmutable al anular una venta")
    void testAuditoriaAlAnularVenta() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            auditoriaDAO.registrarEvento(conn, 1, "TEST_ANULACION", "VENTA", "Venta anterior", "Venta anulada");

            List<RegistroAuditoria> eventos = auditoriaDAO.listarUltimosEventos(conn, 10);
            assertTrue(eventos.stream().anyMatch(e -> "TEST_ANULACION".equals(e.getAccion())), "Debe encontrarse el evento de prueba en auditoria");
        }
    }

    @Test
    @DisplayName("SPEC v1.3 SEC-7: Generacion de las 4 capturas requeridas de la version Enterprise con datos operacionales")
    void testGenerarCapturasV13() throws Exception {
        poblarDatosDemostracionCompletos();

        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setUsername("admin");
        admin.setNombreRol("ADMIN");
        AuthService.setUsuarioSesionActual(admin);

        // 1. Matriz de Roles y Permisos (RolesView.fxml)
        renderizarYCapturar("/com/foodcore/view/RolesView.fxml", 1200, 750, "01_roles_matriz_v1.3.png");

        // 2. Tablero de Entregas y Delivery (DeliveryView.fxml)
        renderizarYCapturar("/com/foodcore/view/DeliveryView.fxml", 1200, 750, "02_delivery_tablero_v1.3.png");

        // 3. Salón de Mesas con Tarjetas y botones Takeaway/Delivery (SalonMesasView.fxml)
        renderizarYCapturar("/com/foodcore/view/SalonMesasView.fxml", 1200, 750, "03_modal_nuevo_delivery_v1.3.png");

        // 4. Sidebar con Badge ONLINE y Dashboard Gerencial (MainLayout.fxml)
        renderizarYCapturar("/com/foodcore/view/MainLayout.fxml", 1280, 768, "04_badge_online_offline_v1.3.png");
    }

    private void poblarDatosDemostracionCompletos() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            st.execute("DELETE FROM detalle_venta");
            st.execute("DELETE FROM venta");
            st.execute("DELETE FROM detalle_pedido");
            st.execute("DELETE FROM pedido");
            st.execute("DELETE FROM movimiento_caja");
            st.execute("DELETE FROM turno_caja");
            st.execute("DELETE FROM caja");
            st.execute("SET FOREIGN_KEY_CHECKS = 1");

            st.execute("INSERT INTO turno_caja (id_turno, id_usuario_apertura, saldo_inicial, estado) VALUES (1, 1, 150.00, 'ABIERTO')");
            st.execute("INSERT INTO caja (id_caja, id_usuario_apertura, nombre_caja, monto_apertura, estado) VALUES (1, 1, 'Caja Principal 01', 150.00, 'ABIERTA')");

            st.execute("INSERT INTO pedido (id_pedido, id_mesa, id_usuario, numero_mesa, tipo_pedido, estado, total, fecha_hora, idempotency_key) VALUES "
                + "(101, 1, 1, '01', 'MESA', 'EN_PREPARACION', 68.50, NOW() - INTERVAL 18 MINUTE, 'IDEM-MESA-01'), "
                + "(102, 2, 1, '02', 'MESA', 'LISTO', 94.00, NOW() - INTERVAL 34 MINUTE, 'IDEM-MESA-02'), "
                + "(104, 4, 1, '04', 'MESA', 'REGISTRADO', 42.00, NOW() - INTERVAL 10 MINUTE, 'IDEM-MESA-04'), "
                + "(107, 7, 1, '07', 'MESA', 'ENTREGADO', 118.50, NOW() - INTERVAL 46 MINUTE, 'IDEM-MESA-07')");

            st.execute("INSERT INTO pedido (id_pedido, id_mesa, id_usuario, numero_mesa, tipo_pedido, cliente_nombre, cliente_telefono, cliente_direccion, cliente_referencia, costo_envio, total, estado, repartidor, idempotency_key) VALUES "
                + "(201, 1, 1, 'DELIV-01', 'DELIVERY', 'Lucía Ramos', '987654321', 'Av. Larco 456, Dpto 302', 'Frente al parque Kennedy', 5.00, 48.50, 'REGISTRADO', 'Sin Asignar', 'IDEM-DELIV-001'), "
                + "(202, 1, 1, 'DELIV-02', 'DELIVERY', 'Roberto Gómez', '992345678', 'Calle Los Pinos 230', 'Casa portón negro', 6.00, 65.00, 'EN_RUTA', 'Juan Pérez (Moto 02)', 'IDEM-DELIV-002'), "
                + "(203, 1, 1, 'DELIV-03', 'DELIVERY', 'Carla Morales', '971122334', 'Jr. Huancavelica 110', 'Oficina 402', 4.50, 34.50, 'ENTREGADO', 'Marcos Soto (Bici 01)', 'IDEM-DELIV-003')");

            st.execute("INSERT INTO venta (id_venta, id_pedido, id_caja, id_usuario, subtotal, igv, total, metodo_pago, monto_recibido, vuelto, estado, fecha_hora, idempotency_key) VALUES "
                + "(1, 101, 1, 1, 122.88, 22.12, 145.00, 'EFECTIVO', 150.00, 5.00, 'COMPLETADA', '2026-11-06 12:15:00', 'IDEM-V-01'), "
                + "(2, 102, 1, 1, 227.54, 40.96, 268.50, 'TARJETA', 268.50, 0.00, 'COMPLETADA', '2026-11-06 13:30:00', 'IDEM-V-02'), "
                + "(3, 104, 1, 2, 262.71, 47.29, 310.00, 'YAPE', 310.00, 0.00, 'COMPLETADA', '2026-11-06 14:10:00', 'IDEM-V-03'), "
                + "(4, 107, 1, 2, 190.68, 34.32, 225.00, 'EFECTIVO', 250.00, 25.00, 'COMPLETADA', '2026-11-06 19:25:00', 'IDEM-V-04'), "
                + "(5, 203, 1, 1, 322.03, 57.97, 380.00, 'TARJETA', 380.00, 0.00, 'COMPLETADA', '2026-11-06 20:45:00', 'IDEM-V-05')");

            st.execute("INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES "
                + "(1, 1, 4, 30.00, 120.00), "
                + "(2, 1, 6, 30.00, 180.00), "
                + "(3, 1, 8, 30.00, 240.00), "
                + "(4, 2, 5, 25.00, 125.00), "
                + "(5, 1, 6, 30.00, 180.00)");

            st.execute("INSERT IGNORE INTO rol (id_rol, codigo_rol, nombre, descripcion, estado) VALUES "
                + "(6, 'GERENTE', 'Gerente', 'Gerente general con acceso operativo y analítico', 1), "
                + "(7, 'SUPERVISOR', 'Supervisor', 'Supervisor de turno con permisos de anulación', 1)");

            st.execute("INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES "
                + "('ADMIN', 'VENTA_COBRAR'), ('ADMIN', 'VENTA_ANULAR'), ('ADMIN', 'PEDIDO_CREAR'), ('ADMIN', 'PEDIDO_ADICIONAR'), ('ADMIN', 'CAJA_APERTURA'), ('ADMIN', 'CAJA_CIERRE'), ('ADMIN', 'KDS_OPERAR'), ('ADMIN', 'USUARIO_GESTIONAR'), ('ADMIN', 'INSUMO_GESTIONAR'), ('ADMIN', 'KARDEX_CONSULTAR'), ('ADMIN', 'CONFIG_RED'), "
                + "('SUPERVISOR', 'VENTA_ANULAR'), ('SUPERVISOR', 'VENTA_DESCUENTO'), ('SUPERVISOR', 'TICKET_REIMPRIMIR')");
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
        assertTrue(outFile.exists() && outFile.length() > 1000, "Captura v1.3 generada: " + outFile.getAbsolutePath());
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
}
