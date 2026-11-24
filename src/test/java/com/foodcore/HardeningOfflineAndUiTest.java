package com.foodcore;

import com.foodcore.controller.ConfiguracionController;
import com.foodcore.controller.KitchenDisplayController;
import com.foodcore.controller.LoginController;
import com.foodcore.controller.MainDashboardController;
import com.foodcore.controller.MenuController;
import com.foodcore.controller.RelojAsistenciaController;
import com.foodcore.controller.UsuariosController;
import com.foodcore.controller.VentaController;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Usuario;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.offline.LocalDatabaseManager;
import com.foodcore.offline.OfflineCryptoService;
import com.foodcore.offline.SyncWorker;
import com.foodcore.security.SesionManager;
import com.foodcore.service.AuthService;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.util.JsonMapperProvider;
import com.foodcore.util.ServidorSockets;
import com.foodcore.util.UiAsyncExecutor;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.HeadlessException;
import java.io.File;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de regresión v5.7.1 para seguridad PWA, SQLite Offline / SyncWorker,
 * ciclo de vida JavaFX, fugas de memoria y RBAC fail-closed en controladores.
 */
class HardeningOfflineAndUiTest {

    private String previousOfflineKey;

    @BeforeAll
    static void iniciarJavaFx() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @AfterEach
    void limpiarEstadoGlobal() {
        AuthService.logout();
        SyncWorker.getInstancia().setSimularDesconexionRed(false);
        LocalDatabaseManager.setDbPath(null);
        ServidorSockets.getInstance().setMensajeListener(null);
        if (previousOfflineKey != null) {
            System.setProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY, previousOfflineKey);
        } else {
            System.clearProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY);
        }
        previousOfflineKey = null;
    }

    // -------------------------------------------------------------------------
    // 1. Offline SQLite & SyncWorker
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SyncWorker: listeners thread-safe, bandera offline respetada, ACL en dir existente y Dead Letter")
    void syncWorkerListenersOfflineFlagYDeadLetter(@TempDir Path tempDir) throws Exception {
        previousOfflineKey = System.getProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY);
        System.setProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY, "FoodCore-Hardening-Test-Key#2026!");

        Path preExistingDir = tempDir.resolve("offline_dir");
        Files.createDirectories(preExistingDir);
        Path dbFile = preExistingDir.resolve("hardening_test.db");
        LocalDatabaseManager.setDbPath(dbFile.toString());
        LocalDatabaseManager.inicializarEsquemaLocal();
        assertTrue(Files.isRegularFile(dbFile), "Debe crear BD SQLite en directorio preexistente");

        // Verificar que el directorio padre preexistente recibió permisos restrictivos solo para el propietario
        PosixFileAttributeView posix = Files.getFileAttributeView(preExistingDir, PosixFileAttributeView.class);
        if (posix != null) {
            assertEquals("rwx------", PosixFilePermissions.toString(posix.readAttributes().permissions()));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(preExistingDir, AclFileAttributeView.class);
            if (acl != null) {
                assertEquals(1, acl.getAcl().size(),
                        "El directorio padre preexistente debe restringirse exclusivamente al propietario");
                assertEquals(acl.getOwner(), acl.getAcl().get(0).principal());
            }
        }

        SyncWorker worker = SyncWorker.getInstancia();
        int listenersAntes = worker.getCantidadListeners();
        AtomicBoolean notificado = new AtomicBoolean(false);
        SyncWorker.SyncStatusListener[] ref = new SyncWorker.SyncStatusListener[1];
        ref[0] = (online, pend, alerta) -> {
            if (!online) {
                notificado.set(true);
                // Mutación durante iteración debe ser segura gracias a CopyOnWriteArrayList
                worker.removeListener(ref[0]);
            }
        };
        worker.addListener(ref[0]);
        assertEquals(listenersAntes + 1, worker.getCantidadListeners());

        worker.setSimularDesconexionRed(true);
        assertFalse(worker.verificarConectividadServidor(),
                "verificarConectividadServidor() debe retornar false cuando simularDesconexion está activa");
        assertFalse(worker.isOnline(), "isOnline() debe reflejar la simulación offline");
        assertTrue(notificado.get(), "El listener de SyncWorker debe haberse notificado");
        assertEquals(listenersAntes, worker.getCantidadListeners(),
                "Remover listener dentro del callback debe ejecutarse sin ConcurrentModificationException");

        // Verificar Dead Letter (RECHAZADO) cuando un registro outbox alcanza MAX_INTENTOS_OUTBOX
        Pedido pedido = new Pedido();
        pedido.setIdMesa(1);
        pedido.setIdUsuario(1);
        pedido.setNumeroMesa("07");
        pedido.setTipoPedido("MESA");
        pedido.setTotal(new BigDecimal("42.00"));
        pedido.setIdempotencyKey("DLQ-" + UUID.randomUUID());
        DetallePedido det = new DetallePedido(0, 0, 1, 1, new BigDecimal("42.00"), new BigDecimal("42.00"), "");
        int idLocal = worker.guardarPedidoEnOutboxLocal(pedido, List.of(det));
        assertTrue(idLocal > 0);
        assertEquals(5, SyncWorker.MAX_INTENTOS_OUTBOX, "MAX_INTENTOS_OUTBOX debe ser 5");

        // Simular payload corrupto con 4 intentos previos (el siguiente intento será el #5 -> RECHAZADO)
        try (Connection conn = LocalDatabaseManager.getLocalConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE outbox_sync SET payload_json = '{json_corrupto', intentos = ? WHERE id_pedido_local = ?")) {
            ps.setInt(1, SyncWorker.MAX_INTENTOS_OUTBOX - 1);
            ps.setInt(2, idLocal);
            ps.executeUpdate();
        }

        // Si MySQL está accesible, procesarOutboxSync() debe marcar el payload corrupto como RECHAZADO
        worker.setSimularDesconexionRed(false);
        if (worker.verificarConectividadServidor()) {
            int synced = worker.procesarOutboxSync();
            assertEquals(0, synced, "Payload corrupto no debe sincronizarse");
            try (Connection conn = LocalDatabaseManager.getLocalConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT estado, intentos FROM outbox_sync WHERE id_pedido_local = ?")) {
                ps.setInt(1, idLocal);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("RECHAZADO", rs.getString("estado"),
                            "Al alcanzar MAX_INTENTOS_OUTBOX debe pasar a Dead Letter RECHAZADO");
                    assertEquals(SyncWorker.MAX_INTENTOS_OUTBOX, rs.getInt("intentos"));
                }
            }
            assertEquals(0, worker.contarPedidosPendientes(),
                    "Un ítem RECHAZADO ya no debe bloquear la cola pendiente");
        }
    }

    @Test
    @DisplayName("SyncWorker.rollbackSinOcultar: preserva la excepción raíz y adjunta fallo de rollback como suppressed")
    void rollbackSinOcultarPreservaCausaRaiz() {
        SQLException rootCause = new SQLException("Error SQL original");
        SQLException rollbackError = new SQLException("Fallo secundario en rollback");
        Connection fakeConn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("rollback".equals(method.getName())) {
                        throw rollbackError;
                    }
                    if ("getAutoCommit".equals(method.getName())) {
                        return false;
                    }
                    return null;
                });

        SyncWorker.rollbackSinOcultar(fakeConn, rootCause);
        assertEquals(1, rootCause.getSuppressed().length,
                "El fallo de rollback debe adjuntarse como suppressed sin enmascarar la causa original");
        assertSame(rollbackError, rootCause.getSuppressed()[0]);
    }

    // -------------------------------------------------------------------------
    // 2. Ciclo de Vida JavaFX, UiAsyncExecutor, App & Launcher
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("UiAsyncExecutor, App y Launcher: shutdown limpio, re-uso seguro, uncaught handler y catch acotado")
    void cicloDeVidaUiAsyncExecutorAppYLauncher() throws Exception {
        App.registrarManejadorExcepcionesNoCapturadas();
        assertNotNull(Thread.getDefaultUncaughtExceptionHandler(),
                "App debe registrar un DefaultUncaughtExceptionHandler global");

        UiAsyncExecutor.shutdown();
        assertTrue(UiAsyncExecutor.isShutdown(), "UiAsyncExecutor.shutdown() debe apagar el pool");

        // Debe recrearse de forma segura si un flujo/test posterior envía una tarea
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultado = new AtomicReference<>();
        UiAsyncExecutor.ejecutarAsync(
                () -> "ok-post-shutdown",
                val -> {
                    resultado.set(val);
                    latch.countDown();
                },
                ex -> latch.countDown()
        );
        assertTrue(latch.await(5, TimeUnit.SECONDS), "UiAsyncExecutor debe recrearse tras shutdown");
        assertEquals("ok-post-shutdown", resultado.get());

        // Launcher solo debe tratar errores reales de entorno headless/JavaFX como fallback a servidor
        assertTrue(Launcher.esErrorInicializacionHeadlessJavaFx(new HeadlessException("No X11 DISPLAY")));
        assertTrue(Launcher.esErrorInicializacionHeadlessJavaFx(
                new UnsupportedOperationException("Unable to open DISPLAY")));
        assertFalse(Launcher.esErrorInicializacionHeadlessJavaFx(
                new IllegalStateException("Error real de configuración de negocio")),
                "Launcher no debe tragarse excepciones de negocio/programación");
        assertFalse(Launcher.esErrorInicializacionHeadlessJavaFx(
                new NullPointerException("NPE inesperado")),
                "Launcher no debe tragarse NullPointerException");
    }

    // -------------------------------------------------------------------------
    // 3. MainDashboardController: RBAC Fail-Closed, EstadoSistema y Listener Leak
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MainDashboardController: RBAC fail-closed (null/desconocido/MOZO/CAJERO/SUPERVISOR/ADMIN) y desregistro SyncWorker")
    void mainDashboardRbacFailClosedYListenerCleanup() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                AuthService.logout();
                int listenersAntes = SyncWorker.getInstancia().getCantidadListeners();

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
                Parent root = loader.load();
                new Scene(root, 1280, 800);
                MainDashboardController ctrl = loader.getController();

                assertNotNull(ctrl.getSyncStatusListener(), "MainDashboardController debe guardar su listener");
                assertEquals(listenersAntes + 1, SyncWorker.getInstancia().getCantidadListeners());

                // Sin sesión (null) -> FAIL-CLOSED: todos los botones críticos ocultos
                assertFalse(ctrl.getBtnNavDashboard().isVisible(), "Sin sesión debe ocultar Dashboard");
                assertFalse(ctrl.getBtnNavPromociones().isVisible(), "Sin sesión debe ocultar Promociones");
                assertFalse(ctrl.getBtnNavKiosco().isVisible(), "Sin sesión debe ocultar Kiosco");
                assertFalse(ctrl.getBtnNavMenuQr().isVisible(), "Sin sesión debe ocultar MenuQr");
                assertFalse(ctrl.getBtnNavReservas().isVisible(), "Sin sesión debe ocultar Reservas");
                assertFalse(ctrl.getBtnNavUsuarios().isVisible(), "Sin sesión debe ocultar Usuarios");
                assertFalse(ctrl.getBtnNavEstadoSistema().isVisible(), "Sin sesión debe ocultar EstadoSistema");

                // handleNavEstadoSistema con act == null debe denegar (no cargar EstadoSistemaView)
                ctrl.handleNavEstadoSistema(null);
                assertNull(ctrl.getControladorActivo(),
                        "handleNavEstadoSistema con usuario null no debe cargar EstadoSistemaView");

                // Rol desconocido -> FAIL-CLOSED
                Usuario desconocido = crearUsuario(99, "invitado", "ROL_DESCONOCIDO_XYZ");
                ctrl.aplicarControlAcceso(desconocido);
                assertFalse(ctrl.getBtnNavDashboard().isVisible(), "Rol desconocido no debe ver Dashboard");
                assertFalse(ctrl.getBtnNavPromociones().isVisible(), "Rol desconocido no debe ver Promociones");
                assertFalse(ctrl.getBtnNavKiosco().isVisible(), "Rol desconocido no debe ver Kiosco");
                assertFalse(ctrl.getBtnNavMenuQr().isVisible(), "Rol desconocido no debe ver MenuQr");
                assertFalse(ctrl.getBtnNavPOS().isVisible(), "Rol desconocido no debe ver POS");

                // MOZO -> solo Operaciones de pedido (Salón/Reservas/POS) y RRHH propio; sin Comercial/Caja/Sistema
                Usuario mozo = crearUsuario(3, "mozo1", "MOZO");
                ctrl.aplicarControlAcceso(mozo);
                assertTrue(ctrl.getBtnNavSalon().isVisible());
                assertTrue(ctrl.getBtnNavReservas().isVisible());
                assertTrue(ctrl.getBtnNavPOS().isVisible());
                assertFalse(ctrl.getBtnNavCaja().isVisible(), "MOZO no debe ver Caja");
                assertFalse(ctrl.getBtnNavPromociones().isVisible(), "MOZO no debe ver Promociones");
                assertFalse(ctrl.getBtnNavKiosco().isVisible(), "MOZO no debe ver Kiosco");
                assertFalse(ctrl.getBtnNavMenuQr().isVisible(), "MOZO no debe ver MenuQr");
                assertFalse(ctrl.getBtnNavAuditoria().isVisible(), "MOZO no debe ver Auditoría");

                // COCINA -> solo KDS y RRHH propio; sin Reservas/Comercial/POS
                Usuario cocina = crearUsuario(4, "cocinero1", "COCINA");
                ctrl.aplicarControlAcceso(cocina);
                assertTrue(ctrl.getBtnNavKDS().isVisible());
                assertFalse(ctrl.getBtnNavReservas().isVisible(), "COCINA no debe ver Reservas");
                assertFalse(ctrl.getBtnNavPromociones().isVisible(), "COCINA no debe ver Promociones");
                assertFalse(ctrl.getBtnNavPOS().isVisible(), "COCINA no debe ver POS");

                // SUPERVISOR -> Comercial visible, Sistema (Usuarios/Config/EstadoSistema) y Auditoría ocultos
                Usuario supervisor = crearUsuario(5, "sup1", "SUPERVISOR");
                ctrl.aplicarControlAcceso(supervisor);
                assertTrue(ctrl.getBtnNavPromociones().isVisible(), "SUPERVISOR debe ver Promociones");
                assertTrue(ctrl.getBtnNavKiosco().isVisible(), "SUPERVISOR debe ver Kiosco");
                assertTrue(ctrl.getBtnNavMenuQr().isVisible(), "SUPERVISOR debe ver MenuQr");
                assertFalse(ctrl.getBtnNavEstadoSistema().isVisible(), "SUPERVISOR no debe ver EstadoSistema");
                assertFalse(ctrl.getBtnNavAuditoria().isVisible(), "SUPERVISOR no debe ver Auditoría");
                assertFalse(ctrl.getBtnNavUsuarios().isVisible(), "SUPERVISOR no debe ver Usuarios");

                // ADMIN -> todo habilitado
                Usuario admin = crearUsuario(1, "admin", "ADMIN");
                AuthService.setUsuarioSesionActual(admin);
                ctrl.aplicarControlAcceso(admin);
                assertTrue(ctrl.getBtnNavPromociones().isVisible());
                assertTrue(ctrl.getBtnNavKiosco().isVisible());
                assertTrue(ctrl.getBtnNavMenuQr().isVisible());
                assertTrue(ctrl.getBtnNavEstadoSistema().isVisible());
                assertTrue(ctrl.getBtnNavAuditoria().isVisible());
                assertTrue(ctrl.getBtnNavConfiguracion().isVisible());

                // handleCerrarSesion debe desregistrar listener de SyncWorker (sin fuga de memoria)
                ctrl.handleCerrarSesion(null);
                assertNull(ctrl.getSyncStatusListener(), "El listener de SyncWorker debe limpiarse al cerrar sesión");
                assertEquals(listenersAntes, SyncWorker.getInstancia().getCantidadListeners(),
                        "SyncWorker debe desregistrar el listener de MainDashboardController");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    // -------------------------------------------------------------------------
    // 4. KitchenDisplayController, VentaController & MenuController
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("KitchenDisplayController.stopTimers(): limpia el listener de ServidorSockets")
    void kitchenDisplayLimpiaSocketListenerEnStopTimers() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                AtomicInteger recibidos = new AtomicInteger(0);
                ServidorSockets.getInstance().setMensajeListener(msg -> recibidos.incrementAndGet());

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KitchenDisplayView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1200, 760);
                KitchenDisplayController ctrl = loader.getController();

                ctrl.stopTimers();

                // Emitir un mensaje tras stopTimers(): no debe llegar a ningún listener retenido
                ObjectNode msg = JsonMapperProvider.getMapper().createObjectNode();
                msg.put("evento", "TEST_CLEANUP");
                ServidorSockets.getInstance().emitirMensaje(msg);
                assertEquals(0, recibidos.get(), "Tras stopTimers() no debe quedar listener activo en ServidorSockets");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("VentaController: limpia KeyEvent filter, respeta las 3 modalidades Split y reporta error al fallar comanda")
    void ventaControllerKeyFilterSplitYComandaError() throws Exception {
        // 1. Verificar construirPagosPorModalidad para las 3 opciones
        DetalleVenta d1 = new DetalleVenta(0, 0, 1, 1, new BigDecimal("60.00"), new BigDecimal("60.00"));
        DetalleVenta d2 = new DetalleVenta(0, 0, 2, 1, new BigDecimal("40.00"), new BigDecimal("40.00"));
        List<DetalleVenta> items = List.of(d1, d2);
        BigDecimal total = new BigDecimal("100.00");

        List<PagoSplit> split2 = VentaController.construirPagosPorModalidad(
                VentaController.MODALIDAD_SPLIT_2, total, items);
        assertEquals(2, split2.size());
        assertEquals(new BigDecimal("50.00"), split2.get(0).monto());
        assertEquals(new BigDecimal("50.00"), split2.get(1).monto());

        List<PagoSplit> split4 = VentaController.construirPagosPorModalidad(
                VentaController.MODALIDAD_SPLIT_4, new BigDecimal("100.01"), items);
        assertEquals(4, split4.size(), "Modalidad 4 partes debe producir 4 pagos");
        BigDecimal suma4 = split4.stream().map(PagoSplit::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.01"), suma4, "La suma del split de 4 partes debe ser exacta al centavo");

        List<PagoSplit> splitItems = VentaController.construirPagosPorModalidad(
                VentaController.MODALIDAD_SPLIT_ITEMS, total, items);
        assertEquals(2, splitItems.size());
        assertEquals(new BigDecimal("60.00"), splitItems.get(0).monto(), "Subcuenta A debe reflejar ítem 1 (60.00)");
        assertEquals(new BigDecimal("40.00"), splitItems.get(1).monto(), "Subcuenta B debe reflejar ítem 2 (40.00)");

        // 2. Verificar KeyEvent filter cleanup y manejo de error en handleEnviarComanda
        AtomicReference<VentaController> ctrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1280, 800);
                VentaController ctrl = loader.getController();
                ctrl.registrarAtajosTeclado();
                assertTrue(ctrl.isAtajoTecladoRegistrado(), "Debe registrar filtro KeyEvent en la Scene");

                ctrl.stopTimeline();
                assertFalse(ctrl.isAtajoTecladoRegistrado(), "stopTimeline() debe remover el filtro KeyEvent");

                // Inyectar PedidoService que falla siempre -> NO debe mostrar "Comanda Enviada"
                ctrl.setPedidoService(new PedidoServiceImpl() {
                    @Override
                    public Pedido registrarPedidoYEnviarComanda(Pedido pedido, List<DetallePedido> detalles)
                            throws SQLException {
                        throw new SQLException("Base de datos caída simulada");
                    }
                });
                DetalleVenta itemPrueba = new DetalleVenta(0, 0, 1, 1, new BigDecimal("25.00"), new BigDecimal("25.00"));
                itemPrueba.setNombreProducto("Lomo Saltado");
                ctrl.getItemsVenta().setAll(itemPrueba);
                com.foodcore.model.Usuario sesionPrev = com.foodcore.service.AuthService.getUsuarioSesionActual();
                com.foodcore.model.Usuario usrTest = new com.foodcore.model.Usuario();
                usrTest.setIdUsuario(1);
                com.foodcore.service.AuthService.setUsuarioSesionActual(usrTest);
                try {
                    ctrl.handleEnviarComanda(null);
                } finally {
                    com.foodcore.service.AuthService.setUsuarioSesionActual(sesionPrev);
                }
                ctrlRef.set(ctrl);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        long deadline = System.currentTimeMillis() + 5000L;
        while (ctrlRef.get().getUltimaAlertaTitulo() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals("Error al Enviar Comanda", ctrlRef.get().getUltimaAlertaTitulo(),
                "Si registrarPedidoYEnviarComanda falla, debe mostrar error y nunca 'Comanda Enviada'");
        assertNull(ctrlRef.get().getPedidoActivoId(), "No debe asignar pedidoActivoId cuando falla el envío");
    }

    @Test
    @DisplayName("MenuController.resolverImagenPlatilloSegura: bloquea path traversal y extensiones peligrosas")
    void menuControllerBloqueaPathTraversalEnImagenes(@TempDir Path tempDir) throws Exception {
        assertNull(MenuController.resolverImagenPlatilloSegura("../../etc/passwd"));
        assertNull(MenuController.resolverImagenPlatilloSegura("..\\..\\Windows\\win.ini"));
        assertNull(MenuController.resolverImagenPlatilloSegura("uploads/platillos/../secret.png"));
        assertNull(MenuController.resolverImagenPlatilloSegura("uploads/platillos/evil.svg"));
        assertNull(MenuController.resolverImagenPlatilloSegura("C:/Windows/system32/cmd.exe"));

        Files.createDirectories(MenuController.DIRECTORIO_UPLOADS);
        Path archivoValido = MenuController.DIRECTORIO_UPLOADS.resolve("test_hardening_" + UUID.randomUUID() + ".png");
        try {
            Files.write(archivoValido, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            Path resuelta = MenuController.resolverImagenPlatilloSegura("uploads/platillos/" + archivoValido.getFileName());
            assertNotNull(resuelta, "Una imagen válida dentro de uploads/platillos debe resolverse");
            assertEquals(archivoValido.toRealPath(), resuelta);
        } finally {
            Files.deleteIfExists(archivoValido);
        }

        // Verificar que copiarImagenAUploads rechaza extensiones no permitidas
        File archivoSvg = tempDir.resolve("payload.svg").toFile();
        Files.writeString(archivoSvg.toPath(), "<svg/>", StandardCharsets.UTF_8);
        MenuController menuCtrl = new MenuController();
        assertThrows(java.io.IOException.class, () -> menuCtrl.copiarImagenAUploads(archivoSvg));
    }

    // -------------------------------------------------------------------------
    // 5. LoginController (2FA sin sesión previa), Reloj, Usuarios, Configuración, PasswordChange
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("LoginController 2FA: SesionManager y AuthService permanecen vacíos antes de verificar TOTP")
    void loginControllerNoEstableceSesionAntesDe2FA() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                AuthService.logout();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1080, 700);
                LoginController ctrl = loader.getController();

                Usuario admin2fa = crearUsuario(1, "admin", "ADMIN");
                admin2fa.setTotpHabilitado(true);
                admin2fa.setTotpSecret("JBSWY3DPEHPK3PXP");

                // Incluso si un mock/legacy llenó AuthService, abrir el desafío 2FA debe limpiar cualquier sesión
                AuthService.setUsuarioSesionActual(admin2fa);
                ctrl.procesarUsuarioAutenticado(admin2fa);

                assertTrue(ctrl.isModalTotpAbierto(), "Debe abrirse el modal TOTP 2FA");
                assertNull(SesionManager.getSesionActiva(),
                        "SesionManager NO debe tener sesión activa antes de completar 2FA");
                assertNull(AuthService.getUsuarioSesionActual(),
                        "AuthService NO debe exponer usuario en sesión antes de completar 2FA");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("RelojAsistenciaController: solo ADMIN puede salir del Kiosco directo al Dashboard")
    void relojKioscoSoloAdminAccedeADashboard() {
        assertFalse(RelojAsistenciaController.puedeAccederADashboardDesdeKiosco(null));
        assertFalse(RelojAsistenciaController.puedeAccederADashboardDesdeKiosco(crearUsuario(3, "mozo", "MOZO")));
        assertFalse(RelojAsistenciaController.puedeAccederADashboardDesdeKiosco(crearUsuario(2, "cajero", "CAJERO")));
        assertFalse(RelojAsistenciaController.puedeAccederADashboardDesdeKiosco(crearUsuario(4, "cocina", "COCINA")));
        assertTrue(RelojAsistenciaController.puedeAccederADashboardDesdeKiosco(crearUsuario(1, "admin", "ADMIN")));
        assertTrue(RelojAsistenciaController.puedeAccederADashboardDesdeKiosco(
                crearUsuario(1, "admin", "ADMINISTRADOR")));
    }

    @Test
    @DisplayName("UsuariosController: exige USUARIO_GESTIONAR, elimina password fallback hardcoded y nunca escribe PIN plano")
    void usuariosControllerRbacPasswordYPinSinTextoPlano() throws Exception {
        UsuariosController ctrl = new UsuariosController();

        // 1. Sin sesión -> ACCESO_DENEGADO
        AuthService.logout();
        assertFalse(UsuariosController.tienePermisoGestionUsuarios());
        NegocioException exNoSession = assertThrows(NegocioException.class,
                () -> ctrl.resetearPasswordForzandoCambio(2, "ClaveSegura2026"));
        assertEquals(CodigoError.ACCESO_DENEGADO, exNoSession.getCodigoError());

        // 2. Con rol MOZO / CAJERO -> ACCESO_DENEGADO
        AuthService.setUsuarioSesionActual(crearUsuario(2, "cajero", "CAJERO"));
        assertFalse(UsuariosController.tienePermisoGestionUsuarios());
        NegocioException exCajero = assertThrows(NegocioException.class,
                () -> ctrl.actualizarPinUsuario(3, "4567"));
        assertEquals(CodigoError.ACCESO_DENEGADO, exCajero.getCodigoError());

        // 3. Con rol ADMIN pero clave vacía o débil -> VALIDACION_ENTRADA (sin fallback "Temporal#2026!")
        AuthService.setUsuarioSesionActual(crearUsuario(1, "admin", "ADMIN"));
        assertTrue(UsuariosController.tienePermisoGestionUsuarios());
        NegocioException exBlankPass = assertThrows(NegocioException.class,
                () -> ctrl.resetearPasswordForzandoCambio(2, ""));
        assertEquals(CodigoError.VALIDACION_ENTRADA, exBlankPass.getCodigoError());
        NegocioException exWeakPass = assertThrows(NegocioException.class,
                () -> ctrl.resetearPasswordForzandoCambio(2, "12345"));
        assertEquals(CodigoError.VALIDACION_ENTRADA, exWeakPass.getCodigoError());

        // 4. Con rol ADMIN pero PIN inválido -> VALIDACION_ENTRADA
        NegocioException exBadPin = assertThrows(NegocioException.class,
                () -> ctrl.actualizarPinUsuario(3, "12a"));
        assertEquals(CodigoError.VALIDACION_ENTRADA, exBadPin.getCodigoError());

        // 5. Verificación estática: cero fallback "Temporal#2026!" ni escritura de PIN en texto plano
        String srcUsuarios = Files.readString(
                Path.of("src", "main", "java", "com", "foodcore", "controller", "UsuariosController.java"),
                StandardCharsets.UTF_8);
        String srcUsuarioService = Files.readString(
                Path.of("src", "main", "java", "com", "foodcore", "service", "UsuarioGestionService.java"),
                StandardCharsets.UTF_8);
        assertFalse(srcUsuarios.contains("Temporal#2026!") || srcUsuarioService.contains("Temporal#2026!"),
                "UsuariosController y UsuarioGestionService no deben contener contraseña fallback hardcodeada");
        assertTrue(srcUsuarioService.contains("SET pin = NULL, pin_hash = ?"),
                "actualizarPinUsuario en UsuarioGestionService debe limpiar la columna pin y guardar solo pin_hash");

        // 6. PasswordChangeDialogController: sin comparación directa en texto plano
        String srcPassDialog = Files.readString(
                Path.of("src", "main", "java", "com", "foodcore", "controller", "PasswordChangeDialogController.java"),
                StandardCharsets.UTF_8);
        assertFalse(srcPassDialog.contains("actual.equals(hashActual)"),
                "PasswordChangeDialogController no debe permitir fallback de igualdad en texto plano");
    }

    @Test
    @DisplayName("ConfiguracionController: exige Permiso.CONFIG_RED antes de ejecutar backup o restauración")
    void configuracionControllerExigeConfigRedParaBackupYRestauracion(@TempDir Path tempDir) throws Exception {
        Path fakeSql = tempDir.resolve("backup_prueba.sql");
        Files.writeString(fakeSql, "-- test dump", StandardCharsets.UTF_8);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ConfiguracionView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1180, 820);
                ConfiguracionController ctrl = loader.getController();

                AtomicBoolean backupEjecutado = new AtomicBoolean(false);
                AtomicBoolean restoreEjecutado = new AtomicBoolean(false);
                ctrl.setEjecutorBackup(() -> {
                    backupEjecutado.set(true);
                    return fakeSql;
                });
                ctrl.setEjecutorRestauracion(p -> {
                    restoreEjecutado.set(true);
                    return true;
                });
                ctrl.setConfirmacionRestauracionSupplier(() -> true);
                ctrl.getTxtRutaBackupRestaurar().setText(fakeSql.toAbsolutePath().toString());

                // Sin sesión -> denegado
                AuthService.logout();
                assertFalse(ConfiguracionController.tienePermisoBackupRestauracion());
                ctrl.handleEjecutarBackupAhora(null);
                ctrl.handleRestaurarDesdeBackup(null);
                assertFalse(backupEjecutado.get(), "Sin sesión no debe ejecutarse backup");
                assertFalse(restoreEjecutado.get(), "Sin sesión no debe ejecutarse restauración");
                assertTrue(ctrl.getLblEstadoRestauracion().getText().contains("Acceso denegado"));

                // Con CAJERO -> denegado
                AuthService.setUsuarioSesionActual(crearUsuario(2, "cajero", "CAJERO"));
                assertFalse(ConfiguracionController.tienePermisoBackupRestauracion());
                ctrl.handleEjecutarBackupAhora(null);
                ctrl.handleRestaurarDesdeBackup(null);
                assertFalse(backupEjecutado.get(), "CAJERO no debe ejecutar backup");
                assertFalse(restoreEjecutado.get(), "CAJERO no debe ejecutar restauración");

                // Con ADMIN -> permitido
                AuthService.setUsuarioSesionActual(crearUsuario(1, "admin", "ADMIN"));
                assertTrue(ConfiguracionController.tienePermisoBackupRestauracion());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    // -------------------------------------------------------------------------
    // 6. Seguridad PWA, XSS, Logout, Service Workers & Contratos API
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PWA Security Audit: sin innerHTML/onclick/onerror/alert/Math.random, aislamiento SW y contratos API")
    void auditoriaSeguridadPwaYServiceWorkers() throws Exception {
        Path pwaRoot = Path.of("pwa");
        try (Stream<Path> walk = Files.walk(pwaRoot)) {
            List<Path> archivos = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().replace('\\', '/').contains("/vendor/"))
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".js") || n.endsWith(".html");
                    })
                    .toList();
            assertFalse(archivos.isEmpty());
            for (Path archivo : archivos) {
                String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
                String rel = pwaRoot.relativize(archivo).toString().replace('\\', '/');
                assertFalse(contenido.contains("innerHTML"), "XSS: innerHTML prohibido en " + rel);
                assertFalse(contenido.contains("onclick="), "XSS: onclick inline prohibido en " + rel);
                assertFalse(contenido.contains("onerror="), "XSS: onerror inline prohibido en " + rel);
                assertFalse(contenido.contains("alert("), "Stub alert() prohibido en " + rel);
                assertFalse(contenido.contains("Math.random"), "PRNG inseguro Math.random prohibido en " + rel);
            }
        }

        // Verificar aislamiento de cachés y same-origin en los 3 Service Workers
        String swMain = Files.readString(pwaRoot.resolve("sw.js"), StandardCharsets.UTF_8);
        String swDash = Files.readString(pwaRoot.resolve("dashboard/sw.js"), StandardCharsets.UTF_8);
        String swKds = Files.readString(pwaRoot.resolve("kds/sw.js"), StandardCharsets.UTF_8);
        assertTrue(swMain.contains("foodcore-pwa-cache-") && swMain.contains("url.origin !== self.location.origin"));
        assertTrue(swDash.contains("foodcore-dashboard-") && swDash.contains("url.origin !== self.location.origin"));
        assertTrue(swKds.contains("foodcore-kds-") && swKds.contains("url.origin !== self.location.origin"));
        assertNotEquals(swMain, swDash);

        // Verificar db.js limpia cachés antes de guardar y soporta limpiarBaseDatos()
        String dbJs = Files.readString(pwaRoot.resolve("js/db.js"), StandardCharsets.UTF_8);
        assertTrue(dbJs.contains("store.clear()"), "db.js debe limpiar el store antes de sobrescribir caché");
        assertTrue(dbJs.contains("limpiarBaseDatos"), "db.js debe exponer limpieza de IndexedDB para logout");

        // Verificar app.js: mutex syncEnProgreso, logout, validación URL, getRandomValues, no encolar 400/401/403
        String appJs = Files.readString(pwaRoot.resolve("js/app.js"), StandardCharsets.UTF_8);
        assertTrue(appJs.contains("syncEnProgreso"), "app.js debe proteger sincronizarOutbox con mutex");
        assertTrue(appJs.contains("cerrarSesionPwa"), "app.js debe implementar cerrarSesionPwa()");
        assertTrue(appJs.contains("esUrlImagenSegura"), "app.js debe validar esquemas de imagen_url");
        assertTrue(appJs.contains("getRandomValues"), "app.js debe usar crypto.getRandomValues como fallback UUID");
        assertTrue(appJs.contains("400") && appJs.contains("401") && appJs.contains("403"),
                "app.js debe distinguir errores cliente 400/401/403 y no reintentarlos como caídas offline");

        // Verificar pin-login.html sin usuarios pre-auth hardcodeados y catalogo.html sin Simular Caída API
        String pinHtml = Files.readString(pwaRoot.resolve("pin-login.html"), StandardCharsets.UTF_8);
        assertFalse(pinHtml.contains("Mateo Quispe"), "pin-login.html no debe exponer nombres pre-auth");
        assertFalse(pinHtml.contains("Administrador General"), "pin-login.html no debe exponer admin pre-auth");

        String catalogoHtml = Files.readString(pwaRoot.resolve("catalogo.html"), StandardCharsets.UTF_8);
        assertFalse(catalogoHtml.contains("Simular Caída"),
                "catalogo.html no debe exponer botón 'Simular Caída API' en producción");

        // Verificar dashboard.js: mapeo JSON correcto y logout real
        String dashJs = Files.readString(pwaRoot.resolve("dashboard/js/dashboard.js"), StandardCharsets.UTF_8);
        assertTrue(dashJs.contains("unidades_vendidas"), "dashboard.js debe mapear unidades_vendidas");
        assertTrue(dashJs.contains("numero_comprobante"), "dashboard.js debe mapear numero_comprobante");
        assertTrue(dashJs.contains("fecha_hora"), "dashboard.js debe mapear fecha_hora");
        assertTrue(dashJs.contains("cerrarSesionPwa"), "dashboard.js debe implementar logout PWA real");
        assertFalse(dashJs.contains("Ceviche Clásico"), "dashboard.js no debe inyectar datos demo falsos");

        // Verificar kds.js: sin tickets demo falsos
        String kdsJs = Files.readString(pwaRoot.resolve("kds/js/kds.js"), StandardCharsets.UTF_8);
        assertFalse(kdsJs.contains("Lomo Saltado Clásico"), "kds.js no debe renderizar comandas demo falsas");

        // Verificar menu-qr.js: validación de URL de imagen
        String menuQrJs = Files.readString(pwaRoot.resolve("menu-digital/js/menu-qr.js"), StandardCharsets.UTF_8);
        assertTrue(menuQrJs.contains("isSafeImageUrl"), "menu-qr.js debe validar el esquema de imagen_url");
    }

    private static Usuario crearUsuario(int id, String username, String rol) {
        Usuario u = new Usuario(id, id, 1, username, "hash", true);
        u.setNombreRol(rol);
        u.setNombreEmpleado(username);
        return u;
    }
}
