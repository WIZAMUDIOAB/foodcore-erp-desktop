package com.foodcore.controller;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite TestFX / JavaFX de la vista de Login, bloqueo por 5 intentos fallidos (15 min),
 * cambio obligatorio de contraseña inicial y expiración de sesión de 30 minutos
 * según SPEC-FOODCORE-v1.0 (§10.1, §10.2, §15.3, §19.4; REMEDIATION R-03, R-18, B6).
 */
class LoginViewTest {

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        authService = new AuthServiceImpl();
        AuthService.logout();
    }

    @Test
    @DisplayName("B6.1: testLoginExitoso_EstableceSesionUsuario - Login válido establece sesión activa en SesionManager")
    void testLoginExitoso_EstableceSesionUsuario() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1024, 680);

                TextField txtUsername = (TextField) root.lookup("#txtUsername");
                PasswordField txtPassword = (PasswordField) root.lookup("#txtPassword");
                Button btnLogin = (Button) root.lookup("#btnLogin");

                txtUsername.setText("cajero");
                txtPassword.setText("Admin#2026!");
                btnLogin.fire();

                SesionUsuario sesion = SesionManager.getSesionActiva();
                assertNotNull(sesion, "Debe existir sesión activa tras login válido");
                assertEquals("CAJERO", sesion.getCodigoRol());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.2: testLoginFallido_IncrementaContadorVisible - Intentos 1 a 4 muestran advertencia visible i/5")
    void testLoginFallido_IncrementaContadorVisible() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1024, 680);

                TextField txtUsername = (TextField) root.lookup("#txtUsername");
                PasswordField txtPassword = (PasswordField) root.lookup("#txtPassword");
                Button btnLogin = (Button) root.lookup("#btnLogin");
                Label lblMensaje = (Label) root.lookup("#lblMensaje");

                txtUsername.setText("cajero");

                for (int i = 1; i <= 3; i++) {
                    txtPassword.setText("ClaveErronea#" + i);
                    btnLogin.fire();
                    assertTrue(lblMensaje.isVisible());
                    assertTrue(lblMensaje.getText().contains(i + "/5"));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.3: testBloqueoCuenta_TrasCincoIntentosFallidos - 5to intento fallido bloquea cuenta por 15 min")
    void testBloqueoCuenta_TrasCincoIntentosFallidos() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1024, 680);

                TextField txtUsername = (TextField) root.lookup("#txtUsername");
                PasswordField txtPassword = (PasswordField) root.lookup("#txtPassword");
                Button btnLogin = (Button) root.lookup("#btnLogin");
                Label lblMensaje = (Label) root.lookup("#lblMensaje");

                txtUsername.setText("cajero");

                for (int i = 1; i <= 5; i++) {
                    txtPassword.setText("ClaveBloqueo#" + i);
                    btnLogin.fire();
                }

                assertTrue(lblMensaje.isVisible());
                assertTrue(lblMensaje.getText().toLowerCase().contains("bloqueada"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        NegocioException exBloqueo = assertThrows(
            NegocioException.class,
            () -> authService.login("cajero", "Admin#2026!")
        );
        assertEquals(CodigoError.USUARIO_BLOQUEADO, exBloqueo.getCodigoError());
    }

    @Test
    @DisplayName("B6.4: testCambioPasswordObligatorio_BloqueaOperacionesHastaActualizar - Enforce cambio_password_requerido=1")
    void testCambioPasswordObligatorio_BloqueaOperacionesHastaActualizar() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1024, 680);

                TextField txtUsername = (TextField) root.lookup("#txtUsername");
                PasswordField txtPassword = (PasswordField) root.lookup("#txtPassword");
                Button btnLogin = (Button) root.lookup("#btnLogin");
                Label lblMensaje = (Label) root.lookup("#lblMensaje");

                txtUsername.setText("admin");
                txtPassword.setText("Admin#2026!");
                btnLogin.fire();

                assertTrue(lblMensaje.isVisible());
                assertTrue(lblMensaje.getText().contains("CAMBIO DE CONTRASEÑA OBLIGATORIO"));
                assertTrue(SesionManager.isCambioPasswordPendiente());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        NegocioException exPendiente = assertThrows(
            NegocioException.class,
            SesionManager::verificarSesionActiva
        );
        assertEquals(CodigoError.CAMBIO_PASSWORD_REQUERIDO, exPendiente.getCodigoError());

        int idAdmin = AuthService.getUsuarioSesionActual().getIdUsuario();
        authService.cambiarPasswordObligatorio(idAdmin, "NuevaClaveSegura#2026!");

        assertFalse(SesionManager.isCambioPasswordPendiente());
        assertNotNull(SesionManager.verificarSesionActiva());
    }

    @Test
    @DisplayName("B6.5: testExpiracionSesion_TreintaMinutosTTL - Invalida sesión activa transcurridos 30 minutos")
    void testExpiracionSesion_TreintaMinutosTTL() throws Exception {
        authService.login("cajero", "Admin#2026!");
        SesionUsuario sesion = SesionManager.verificarSesionActiva();
        assertNotNull(sesion);
        assertEquals(sesion.getIniciadaEn().plusMinutes(30), sesion.getExpiraEn());

        LocalDateTime treintaMinutosDespues = sesion.getIniciadaEn().plusMinutes(30);
        NegocioException exExpirada = assertThrows(
            NegocioException.class,
            () -> SesionManager.verificarSesionActiva(treintaMinutosDespues)
        );
        assertEquals(CodigoError.SESION_EXPIRADA, exExpirada.getCodigoError());
        assertNull(SesionManager.getSesionActiva());
    }
}
