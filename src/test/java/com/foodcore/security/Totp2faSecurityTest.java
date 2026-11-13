package com.foodcore.security;

import com.foodcore.api.ApiServer;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.controller.LoginController;
import com.foodcore.controller.UsuariosController;
import com.foodcore.controller.UsuariosController.FilaUsuario;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.model.Usuario;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.TestSecuritySupport;
import com.foodcore.support.UiScreenshotTestSupport;
import com.foodcore.support.UiScreenshotTestSupport.VistaCargada;
import com.foodcore.util.ConexionBD;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas integrales de autenticación 2FA TOTP (ÍTEM 7 v5.1.0):
 * servicio TOTP + QR ZXing, login API REST y flujo UI Desktop en UsuariosView/LoginView.
 */
class Totp2faSecurityTest {

    private final TotpService totpService = new TotpService();
    private final UsuarioDAOImpl usuarioDAO = new UsuarioDAOImpl();

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
        TestSecuritySupport.asegurarJwtSecretTest();
    }

    @Test
    @DisplayName("TotpService genera secretos Base32, códigos válidos de 6 dígitos e imágenes QR PNG válidas")
    void servicioTotpGeneraQrYValidaCodigos() throws Exception {
        String secret = totpService.generarSecretoBase32();
        assertNotNull(secret);
        assertFalse(secret.isBlank());

        String uri = totpService.construirOtpAuthUri("admin", secret);
        assertTrue(uri.startsWith("otpauth://totp/FoodCoreERP%3Aadmin"));
        assertTrue(uri.contains("secret=" + secret));
        assertTrue(uri.contains("issuer=FoodCoreERP"));

        String codigoValido = totpService.obtenerCodigoActualFormateado(secret);
        assertTrue(codigoValido.matches("\\d{6}"));
        assertTrue(totpService.verificarCodigo(secret, codigoValido));
        assertFalse(totpService.verificarCodigo(secret, "000000".equals(codigoValido) ? "111111" : "000000"));
        assertFalse(totpService.verificarCodigo(secret, "abc"));

        byte[] pngBytes = totpService.generarQrPngBytes(uri, 200);
        assertTrue(pngBytes.length > 200);
        assertEquals((byte) 0x89, pngBytes[0]);
        assertEquals((byte) 'P', pngBytes[1]);
        assertEquals((byte) 'N', pngBytes[2]);
        assertEquals((byte) 'G', pngBytes[3]);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Image fxImage = totpService.generarQrImageFx("admin", secret);
                assertNotNull(fxImage);
                assertFalse(fxImage.isError());
                assertTrue(fxImage.getWidth() > 100);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("API POST /api/auth/login exige código 2FA válido para ADMIN cuando totp_habilitado=1 y audita fallos")
    void apiRestLoginValida2faYAuditaFallos() throws Exception {
        String secret = totpService.generarSecretoBase32();
        try (Connection conn = ConexionBD.getConnection()) {
            assertTrue(usuarioDAO.actualizarConfiguracion2FA(conn, 1, secret, true));
        }

        int port = puertoLibre();
        ApiServer server = ApiServer.crearServidorAislado(
                new JwtService(),
                new CorsPolicy(Set.of("http://localhost")),
                new AuthRateLimiter(30, 60_000L, System::currentTimeMillis));
        server.iniciar(port, -1);
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> sinCodigo = postLogin(
                    client, port, "{\"username\":\"admin\",\"password\":\"Admin#2026!\"}");
            assertEquals(401, sinCodigo.statusCode());
            assertTrue(sinCodigo.body().contains("\"requires2fa\":true"));

            String codigoInvalido = "123456".equals(totpService.obtenerCodigoActualFormateado(secret))
                    ? "654321" : "123456";
            HttpResponse<String> conCodigoInvalido = postLogin(
                    client, port,
                    "{\"username\":\"admin\",\"password\":\"Admin#2026!\",\"totpCode\":\"" + codigoInvalido + "\"}");
            assertEquals(401, conCodigoInvalido.statusCode());
            assertTrue(conCodigoInvalido.body().contains("\"requires2fa\":true"));

            String codigoValido = totpService.obtenerCodigoActualFormateado(secret);
            HttpResponse<String> conCodigoValido = postLogin(
                    client, port,
                    "{\"username\":\"admin\",\"password\":\"Admin#2026!\",\"totpCode\":\"" + codigoValido + "\"}");
            assertEquals(200, conCodigoValido.statusCode());
            assertTrue(conCodigoValido.body().contains("\"token\":\"eyJ"));
            assertTrue(conCodigoValido.body().contains("\"totpHabilitado\":true"));

            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) FROM auditoria WHERE accion = '2FA_FALLIDO' AND id_usuario = 1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 2, "Los intentos fallidos de 2FA deben auditarse en auditoria");
            }
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("UI UsuariosView configura QR 2FA y LoginView solicita verificación TOTP antes de ingresar")
    void uiConfigura2faQrYSolicitaCodigoEnLogin() throws Exception {
        final String[] secretoCapturado = new String[1];

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<UsuariosController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/UsuariosView.fxml", 1180, 760);
                UsuariosController ctrl = vista.controller();
                FilaUsuario adminFila = new FilaUsuario(1, "admin", "Administrador General", "ADMIN", false, true, false, false);
                ctrl.abrirModal2FAParaUsuario(adminFila);

                assertTrue(ctrl.isModal2faVisible());
                secretoCapturado[0] = ctrl.getSecretoTemporal2fa();
                assertNotNull(secretoCapturado[0]);

                Label lblSec = (Label) vista.root().lookup("#lblSecreto2fa");
                assertEquals(secretoCapturado[0], lblSec.getText());

                UiScreenshotTestSupport.capturarEvidencia(vista.root(), "02_usuarios_2fa_qr_v510.png");

                TextField txtCodigo = (TextField) vista.root().lookup("#txtCodigoVerificacion2fa");
                Label lblErr = (Label) vista.root().lookup("#lblError2fa");
                txtCodigo.setText("000000".equals(totpService.obtenerCodigoActualFormateado(secretoCapturado[0]))
                        ? "999999" : "000000");
                ctrl.handleConfirmarActivar2FA(null);
                assertTrue(lblErr.isVisible(), "Un código TOTP erróneo debe mantener abierto el modal con error");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // Persistir secret activado como en confirmación BD y probar flujo LoginView
        try (Connection conn = ConexionBD.getConnection()) {
            assertTrue(usuarioDAO.actualizarConfiguracion2FA(conn, 1, secretoCapturado[0], true));
        }

        Usuario adminBd;
        try (Connection conn = ConexionBD.getConnection()) {
            adminBd = usuarioDAO.buscarPorUsername(conn, "admin").orElseThrow();
            adminBd.setCambioPasswordRequerido(false);
        }
        assertTrue(adminBd.isTotpHabilitado());

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<LoginController> loginVista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/LoginView.fxml", 1040, 680);
                LoginController loginCtrl = loginVista.controller();
                loginCtrl.procesarUsuarioAutenticado(adminBd);

                assertTrue(loginCtrl.isModalTotpAbierto(),
                        "LoginView debe mostrar el modal 2FA cuando el usuario tiene totp_habilitado=1");

                TextField txtOtp = (TextField) loginVista.root().lookup("#txtTotpCodigo");
                Label lblErr = (Label) loginVista.root().lookup("#lblTotpError");
                String malo = "111111".equals(totpService.obtenerCodigoActualFormateado(secretoCapturado[0]))
                        ? "222222" : "111111";
                txtOtp.setText(malo);
                loginCtrl.handleVerificarTotp();

                assertTrue(loginCtrl.isModalTotpAbierto(), "Código inválido debe bloquear acceso al Dashboard");
                assertTrue(lblErr.isVisible());

                loginCtrl.handleCancelarTotp();
                assertFalse(loginCtrl.isModalTotpAbierto());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static HttpResponse<String> postLogin(HttpClient client, int port, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int puertoLibre() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
