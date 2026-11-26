package com.foodcore.controller;

import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.model.Usuario;
import com.foodcore.service.AlertaService;
import com.foodcore.service.AlertaService.AlertaEmitida;
import com.foodcore.service.AlertaService.ConfiguracionAlertaEmail;
import com.foodcore.service.AlertaService.TipoAlerta;
import com.foodcore.service.AuthService;
import com.foodcore.service.SistemaMonitorService.EstadoSistemaSnapshot;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.UiScreenshotTestSupport;
import com.foodcore.support.UiScreenshotTestSupport.VistaCargada;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias, de scripts e interfaz JavaFX para Sub-Fase C: Infraestructura y Deploy (v5.3.0),
 * validando los 6 ítems y generando evidencias PNG en {@code target/v530_evidence}.
 */
class SubFaseCInfraestructuraTest {

    private static final Path EVIDENCE_DIR = Path.of("target", "v530_evidence");

    @BeforeAll
    static void prepararToolkit() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @AfterEach
    void limpiarEstado() {
        AuthService.logout();
        AlertaService.reiniciarEstadoGlobal();
    }

    @Test
    @DisplayName("Ítem 1: Empaquetador jpackage 5.8.0 dinámico y recursos pwa/keystore/scripts incluidos")
    void item1EmpaquetadoInstallerYRecursosVersion530() throws Exception {
        String pomXml = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue(pomXml.contains("<app.package.version>5.8.0</app.package.version>")
                        && pomXml.contains("<appVersion>${app.package.version}</appVersion>"),
                "pom.xml perfil release debe declarar appVersion dinámico 5.8.0");

        String pkgPs1 = Files.readString(Path.of("scripts", "package-release.ps1"), StandardCharsets.UTF_8);
        assertTrue(pkgPs1.contains("pom.xml") && pkgPs1.contains("$PomXml.project.version"),
                "package-release.ps1 debe resolver la versión dinámicamente desde pom.xml");
        assertTrue(pkgPs1.contains("FoodCoreERP-$AppVersion.msi"));
        assertTrue(pkgPs1.contains("pwa"));
        assertTrue(pkgPs1.contains("keystore.jks.example"));
        assertTrue(pkgPs1.contains("scripts"));

        String debSh = Files.readString(Path.of("scripts", "build-deb.sh"), StandardCharsets.UTF_8);
        assertTrue(debSh.contains("APP_VERSION=") && debSh.contains("pom.xml"),
                "build-deb.sh debe resolver APP_VERSION dinámicamente desde pom.xml");
        assertTrue(debSh.contains("pwa"));
        assertTrue(debSh.contains("keystore.jks.example"));

        assertTrue(Files.isRegularFile(Path.of("keystore.jks.example")));
        assertTrue(Files.isRegularFile(Path.of("scripts", "init-client-keystore.ps1")));
        assertTrue(Files.isRegularFile(Path.of("pwa", "index.html")));
        assertTrue(Files.isRegularFile(Path.of("scripts", "backup-auto.ps1")));
        assertTrue(Files.isRegularFile(Path.of("scripts", "backup-auto.sh")));
        assertTrue(Files.isRegularFile(Path.of("scripts", "restore-auto.ps1")));
        assertTrue(Files.isRegularFile(Path.of("scripts", "restore-auto.sh")));

        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
        assertTrue(readme.contains("FoodCoreERP-5.8.0"));
        assertTrue(readme.contains("backup-auto.ps1"));
        assertTrue(readme.contains("restore-auto.ps1"));
    }

    @Test
    @DisplayName("Ítem 2: Scripts backup-auto (.ps1/.sh) cumplen compresión .gz, permisos, 03:00 AM y purga > 30 días")
    void item2BackupAutoRetencionYScripts(@TempDir Path tempDir) throws Exception {
        String ps1 = Files.readString(Path.of("scripts", "backup-auto.ps1"), StandardCharsets.UTF_8);
        assertTrue(ps1.contains("--single-transaction"));
        assertTrue(ps1.contains("GzipStream"));
        assertTrue(ps1.contains("icacls"));
        assertTrue(ps1.contains("03:00"));
        assertTrue(ps1.contains("RetentionDays = 30"));

        String sh = Files.readString(Path.of("scripts", "backup-auto.sh"), StandardCharsets.UTF_8);
        assertTrue(sh.contains("--single-transaction"));
        assertTrue(sh.contains("gzip -9"));
        assertTrue(sh.contains("chmod 700"));
        assertTrue(sh.contains("chmod 600"));
        assertTrue(sh.contains("0 3 * * *"));
        assertTrue(sh.contains("-mtime"));

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            Path antiguo = tempDir.resolve("foodcore_20250101_030000.sql.gz");
            Path reciente = tempDir.resolve("foodcore_reciente.sql.gz");
            Files.write(antiguo, comprimirGzip("-- old backup"));
            Files.write(reciente, comprimirGzip("-- recent backup"));
            Files.setLastModifiedTime(antiguo, FileTime.from(Instant.now().minus(35, ChronoUnit.DAYS)));

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", Path.of("scripts", "backup-auto.ps1").toAbsolutePath().toString(),
                    "-BackupDir", tempDir.toAbsolutePath().toString(),
                    "-RetentionDays", "30",
                    "-SkipDump"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(proc.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, proc.exitValue(), "Salida backup-auto.ps1: " + output);
            assertFalse(Files.exists(antiguo), "Backup > 30 días debe haber sido eliminado");
            assertTrue(Files.exists(reciente), "Backup reciente debe conservarse");
        } else {
            ProcessBuilder pb = new ProcessBuilder("bash", "-n", Path.of("scripts", "backup-auto.sh").toString());
            Process proc = pb.start();
            assertTrue(proc.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, proc.exitValue());
        }
    }

    @Test
    @DisplayName("Ítem 3 y 6 UI: ConfiguracionView restaura desde backup con confirmación previa y guarda SMTP")
    void item3Y6ConfiguracionRestoreYAlertasEmailUi(@TempDir Path tempDir) throws Exception {
        String restorePs1 = Files.readString(Path.of("scripts", "restore-auto.ps1"), StandardCharsets.UTF_8);
        assertTrue(restorePs1.contains("Stop-Service"));
        assertTrue(restorePs1.contains("Start-Service"));
        assertTrue(restorePs1.contains("GzipStream"));

        String restoreSh = Files.readString(Path.of("scripts", "restore-auto.sh"), StandardCharsets.UTF_8);
        assertTrue(restoreSh.contains("gzip -t"));
        assertTrue(restoreSh.contains("gunzip -c"));

        Path fakeGz = tempDir.resolve("foodcore_v530_test.sql.gz");
        Files.write(fakeGz, comprimirGzip("-- MySQL dump\nCREATE TABLE IF NOT EXISTS demo(id INT);"));

        CountDownLatch ejecutorLatch = new CountDownLatch(1);
        AtomicReference<Path> rutaRestaurada = new AtomicReference<>();

        Usuario admin = new Usuario(1, 1, 1, "admin", "hash", true);
        admin.setNombreRol("ADMIN");
        admin.setNombreEmpleado("Administrador General");
        AuthService.setUsuarioSesionActual(admin);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ConfiguracionController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ConfiguracionView.fxml", 1180, 820);
                ConfiguracionController ctrl = vista.controller();

                ctrl.getTxtEmailAlertas().setText("gerencia@foodcore.pe");
                ctrl.getTxtSmtpHost().setText("smtp.foodcore.pe");
                ctrl.getTxtSmtpPort().setText("587");
                ctrl.handleGuardarConfiguracionAlertas(null);

                ConfiguracionAlertaEmail cfgGuardada = AlertaService.getConfiguracionGlobal();
                assertEquals("gerencia@foodcore.pe", cfgGuardada.destinatario());
                assertEquals("smtp.foodcore.pe", cfgGuardada.smtpHost());
                assertEquals(587, cfgGuardada.smtpPort());

                ctrl.getTxtRutaBackupRestaurar().setText(fakeGz.toAbsolutePath().toString());
                ctrl.setEjecutorRestauracion(path -> {
                    rutaRestaurada.set(path);
                    ejecutorLatch.countDown();
                    return true;
                });

                // Caso 1: Usuario cancela confirmación previa
                ctrl.setConfirmacionRestauracionSupplier(() -> false);
                ctrl.handleRestaurarDesdeBackup(null);
                assertEquals(1L, ejecutorLatch.getCount(), "Si cancela confirmación no debe restaurar");
                assertTrue(ctrl.getLblEstadoRestauracion().getText().contains("cancelada"));

                // Caso 2: Usuario confirma restauración
                ctrl.setConfirmacionRestauracionSupplier(() -> true);
                ctrl.handleRestaurarDesdeBackup(null);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        assertTrue(ejecutorLatch.await(5, TimeUnit.SECONDS), "Al confirmar debe invocar la restauración del backup");
        assertEquals(fakeGz.toAbsolutePath().normalize(), rutaRestaurada.get().toAbsolutePath().normalize());

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ConfiguracionController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ConfiguracionView.fxml", 1180, 820);
                ConfiguracionController ctrl = vista.controller();
                ctrl.getAsignaciones().setAll(
                        new ConfiguracionImpresora(1, 1, "Cocina Caliente", "EPSON-COCINA-80MM", true),
                        new ConfiguracionImpresora(2, 2, "Bar y Bebidas", "STAR-BARRA-TSP100", true)
                );
                ctrl.getTxtEmailAlertas().setText("dueno@foodcore.pe");
                ctrl.getTxtSmtpHost().setText("smtp.gmail.com");
                ctrl.getTxtSmtpPort().setText("587");
                ctrl.getTxtRutaBackupRestaurar().setText("backups/foodcore_20261115_030000.sql.gz");
                ctrl.getLblEstadoRestauracion().setText("BD restaurada desde foodcore_20261115_030000.sql.gz");
                ctrl.getLblEstadoAlertasEmail().setText("Alertas activas → dueno@foodcore.pe");
                if (vista.root().lookup(".scroll-pane") instanceof ScrollPane sp) {
                    sp.setVvalue(1.0);
                }
                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "02_configuracion_restore_y_alertas_v530.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítems 4 y 5: EstadoSistemaView exclusivo ADMIN, muestra servicios, CPU/RAM/Disco y captura PNG")
    void item4Y5EstadoSistemaRbacServiciosYPerformanceUi() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Usuario admin = new Usuario(1, 1, 1, "admin", "hash", true);
                admin.setNombreRol("ADMIN");
                admin.setNombreEmpleado("Administrador General");
                AuthService.setUsuarioSesionActual(admin);
                VistaCargada<MainDashboardController> dashAdmin = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/MainLayout.fxml", 1280, 800);
                MainDashboardController mainCtrl = dashAdmin.controller();
                assertNotNull(mainCtrl.getBtnNavEstadoSistema());
                assertTrue(mainCtrl.getBtnNavEstadoSistema().isVisible(),
                        "ADMIN debe visualizar Estado del Sistema en el sidebar");
                assertTrue(mainCtrl.getBtnNavEstadoSistema().isManaged());

                Usuario cajero = new Usuario(2, 2, 2, "cajero1", "hash", true);
                cajero.setNombreRol("CAJERO");
                cajero.setNombreEmpleado("Caja Principal");
                AuthService.setUsuarioSesionActual(cajero);
                mainCtrl.aplicarControlAcceso(cajero);
                assertFalse(mainCtrl.getBtnNavEstadoSistema().isVisible(),
                        "CAJERO no debe tener acceso a Estado del Sistema");

                Usuario supervisor = new Usuario(3, 3, 5, "sup1", "hash", true);
                supervisor.setNombreRol("SUPERVISOR");
                supervisor.setNombreEmpleado("Supervisor");
                AuthService.setUsuarioSesionActual(supervisor);
                mainCtrl.aplicarControlAcceso(supervisor);
                assertFalse(mainCtrl.getBtnNavEstadoSistema().isVisible(),
                        "SUPERVISOR no debe ver Estado del Sistema (exclusivo ADMIN)");

                VistaCargada<EstadoSistemaController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/EstadoSistemaView.fxml", 1160, 760);
                EstadoSistemaController ctrl = vista.controller();
                assertTrue(ctrl.isTimelineActivo(), "Auto-refresco cada 5 segundos debe iniciar activo");

                EstadoSistemaSnapshot snapSaludable = new EstadoSistemaSnapshot(
                        true, "8.0.40", 3L,
                        true, 8080,
                        true, 8443,
                        true, 9090, 4,
                        true, "https://foodcore-pos-live.trycloudflare.com",
                        "V23 (operaciones avanzadas v5 2)",
                        ((2L * 3600L) + (18L * 60L) + 45L) * 1000L,
                        27.8,
                        6L * 1024 * 1024 * 1024L, 16L * 1024 * 1024 * 1024L, 37.5,
                        148L * 1024 * 1024 * 1024L, 256L * 1024 * 1024 * 1024L, 42.2
                );
                ctrl.aplicarSnapshotEnUi(snapSaludable);

                assertEquals("UP", ctrl.getLblMysqlEstado().getText());
                assertEquals("UP", ctrl.getLblHttpEstado().getText());
                assertEquals("UP", ctrl.getLblHttpsEstado().getText());
                assertEquals("UP", ctrl.getLblKdsSocketEstado().getText());
                assertTrue(ctrl.getLblKdsSocketClientes().getText().contains("4"));
                assertEquals("https://foodcore-pos-live.trycloudflare.com", ctrl.getLblTunnelUrl().getText());
                assertTrue(ctrl.getLblFlywayVersion().getText().contains("V23"));
                assertTrue(ctrl.getLblSistemaUptime().getText().contains("02h 18m 45s"));
                assertTrue(ctrl.getLblCpuPorcentaje().getText().contains("27.8"));
                assertTrue(ctrl.getLblRamDetalle().getText().contains("6.00 GB"));
                assertTrue(ctrl.getLblDiscoDetalle().getText().contains("148.00 GB"));

                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "01_estado_sistema_monitoreo_v530.png");

                // Captura adicional con MySQL caído y disco bajo (< 5 GB)
                EstadoSistemaSnapshot snapCritico = new EstadoSistemaSnapshot(
                        false, "N/D", -1L,
                        true, 8080,
                        true, 8443,
                        true, 9090, 2,
                        false, "Sin túnel activo",
                        "V23 (operaciones avanzadas v5 2)",
                        ((4L * 3600L) + (5L * 60L) + 12L) * 1000L,
                        88.4,
                        15L * 1024 * 1024 * 1024L, 16L * 1024 * 1024 * 1024L, 93.8,
                        3L * 1024 * 1024 * 1024L, 256L * 1024 * 1024 * 1024L, 98.8
                );
                ctrl.aplicarSnapshotEnUi(snapCritico);
                assertEquals("DOWN", ctrl.getLblMysqlEstado().getText());

                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "03_estado_sistema_alerta_critica_v530.png");

                ctrl.stopTimeline();
                assertFalse(ctrl.isTimelineActivo(), "Timeline debe detenerse limpiamente al salir de la vista");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 6: AlertaService dispara emails ante MySQL caído >1m, disco <5GB, RAM >=90% >5m y SMTP real TCP")
    void item6AlertaServiceUmbralesYSmtpReal() throws Exception {
        AtomicLong reloj = new AtomicLong(1_000_000L);
        List<String> asuntosEnviados = new ArrayList<>();
        AlertaService service = new AlertaService(reloj::get, (cfg, dest, asunto, cuerpo) -> {
            asuntosEnviados.add(asunto);
            return true;
        });
        service.actualizarConfiguracion(new ConfiguracionAlertaEmail(
                "127.0.0.1", 587, "alertas@foodcore.pe", "secreto", false, "dueno@foodcore.pe", true
        ), false);

        long discoOk = 20L * 1024L * 1024L * 1024L;
        long discoBajo = 3L * 1024L * 1024L * 1024L;

        // 1. MySQL caído > 1 minuto
        assertTrue(service.evaluar(false, 45.0, discoOk).isEmpty(), "En t=0 inicia conteo sin alertar");
        reloj.addAndGet(45_000L);
        assertTrue(service.evaluar(false, 45.0, discoOk).isEmpty(), "A los 45s aún no supera 1 minuto");
        reloj.addAndGet(16_000L); // 61s total caído
        List<AlertaEmitida> a1 = service.evaluar(false, 45.0, discoOk);
        assertEquals(1, a1.size());
        assertEquals(TipoAlerta.MYSQL_CAIDO, a1.get(0).tipo());
        assertTrue(a1.get(0).enviadoPorRed());

        // Recupera MySQL
        assertTrue(service.evaluar(true, 45.0, discoOk).isEmpty());

        // 2. Disco libre < 5 GB
        List<AlertaEmitida> a2 = service.evaluar(true, 50.0, discoBajo);
        assertEquals(1, a2.size());
        assertEquals(TipoAlerta.DISCO_BAJO, a2.get(0).tipo());
        assertTrue(service.evaluar(true, 50.0, discoBajo).isEmpty(), "No repite alerta mientras persista");

        // 3. RAM >= 90% por > 5 minutos
        assertTrue(service.evaluar(true, 92.5, discoOk).isEmpty(), "En t=0 RAM alta inicia ventana de 5 min");
        reloj.addAndGet(290_000L); // 4m50s
        assertTrue(service.evaluar(true, 93.0, discoOk).isEmpty(), "A los 4m50s no dispara aún");
        reloj.addAndGet(15_000L); // 5m05s
        List<AlertaEmitida> a3 = service.evaluar(true, 94.0, discoOk);
        assertEquals(1, a3.size());
        assertEquals(TipoAlerta.RAM_ALTA, a3.get(0).tipo());
        assertEquals(3, asuntosEnviados.size());

        // 4. Transmisión SMTP TCP real contra ServerSocket local efímero
        StringBuffer smtpLog = new StringBuffer();
        try (ServerSocket smtpMock = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int puertoMock = smtpMock.getLocalPort();
            Thread serverWorker = new Thread(() -> {
                try (Socket client = smtpMock.accept();
                     BufferedReader r = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter w = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
                    w.write("220 foodcore-smtp ESMTP\r\n");
                    w.flush();
                    boolean inData = false;
                    String line;
                    while ((line = r.readLine()) != null) {
                        smtpLog.append(line).append('\n');
                        if (inData) {
                            if (".".equals(line)) {
                                inData = false;
                                w.write("250 OK\r\n");
                                w.flush();
                            }
                            continue;
                        }
                        if (line.startsWith("EHLO") || line.startsWith("MAIL") || line.startsWith("RCPT")) {
                            w.write("250 OK\r\n");
                            w.flush();
                        } else if (line.startsWith("DATA")) {
                            inData = true;
                            w.write("354 End data with <CR><LF>.<CR><LF>\r\n");
                            w.flush();
                        } else if (line.startsWith("QUIT")) {
                            w.write("221 Bye\r\n");
                            w.flush();
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    // Cierre controlado tras completar recepción
                }
            }, "mock-smtp-listener");
            serverWorker.setDaemon(true);
            serverWorker.start();

            AlertaService realSmtpService = new AlertaService();
            realSmtpService.actualizarConfiguracion(new ConfiguracionAlertaEmail(
                    "127.0.0.1", puertoMock, "alertas@foodcore.pe", "", false, "dueno@foodcore.pe", true
            ), false);
            AlertaEmitida smtpOk = realSmtpService.enviarAlertaPrueba();
            serverWorker.join(3000L);

            assertNotNull(smtpOk);
            assertTrue(smtpOk.enviadoPorRed(), "SMTP nativo debe completar envío TCP 250 OK");
            String transcript = smtpLog.toString();
            assertTrue(transcript.contains("RCPT TO:<dueno@foodcore.pe>"));
            assertTrue(transcript.contains("Prueba de Alertas SMTP v5.3.0"));
        }
    }

    private static byte[] comprimirGzip(String contenido) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(contenido.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
}
