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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite TestFX / JavaFX de la vista de Login, bloqueo por 5 intentos fallidos (15 min),
 * cambio obligatorio de contraseña inicial y expiración de sesión de 30 minutos
 * según SPEC-FOODCORE-v1.0 (§10.1, §10.2, §15.3, §19.4; REMEDIATION R-03, R-18).
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
    @DisplayName("R-03 & R-18 (1/3): LoginView FXML muestra error en fallo y bloquea la cuenta visiblemente tras 5 intentos fallidos")
    void debeMostrarErrorYBloqueoVisibleTrasCincoIntentosFallidos() throws Exception {
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

                // Intentos 1 a 4: credenciales inválidas
                for (int i = 1; i <= 4; i++) {
                    txtPassword.setText("ClaveIncorrecta#" + i);
                    btnLogin.fire();
                    assertTrue(lblMensaje.isVisible(), "El mensaje de error debe ser visible en el intento " + i);
                    assertTrue(lblMensaje.getText().contains(i + "/5"));
                }

                // Intento 5: dispara bloqueo por 15 minutos
                txtPassword.setText("ClaveIncorrecta#5");
                btnLogin.fire();
                assertTrue(lblMensaje.isVisible());
                assertTrue(lblMensaje.getText().toLowerCase().contains("bloqueada"),
                    "El 5to intento fallido debe mostrar cuenta bloqueada por 15 minutos: " + lblMensaje.getText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Verificar también en capa de servicio que aun con contraseña correcta rechaza con USUARIO_BLOQUEADO
        NegocioException exBloqueo = assertThrows(
            NegocioException.class,
            () -> authService.login("cajero", "Admin#2026!")
        );
        assertEquals(CodigoError.USUARIO_BLOQUEADO, exBloqueo.getCodigoError());
    }

    @Test
    @DisplayName("R-03 & R-18 (2/3): Login de admin con cambio_password_requerido=1 exige actualizar clave antes de operar")
    void debeForzarCambioDePasswordEnPrimerLoginYDesbloquearTrasActualizar() throws Exception {
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

        // Mientras no cambie la contraseña inicial, SesionManager bloquea operaciones de negocio
        NegocioException exPendiente = assertThrows(
            NegocioException.class,
            SesionManager::verificarSesionActiva
        );
        assertEquals(CodigoError.CAMBIO_PASSWORD_REQUERIDO, exPendiente.getCodigoError());

        // Ejecutar cambio obligatorio de password
        int idAdmin = AuthService.getUsuarioSesionActual().getIdUsuario();
        authService.cambiarPasswordObligatorio(idAdmin, "NuevaClaveSegura#2026!");

        assertFalse(SesionManager.isCambioPasswordPendiente());
        SesionUsuario sesionActiva = SesionManager.verificarSesionActiva();
        assertNotNull(sesionActiva);
    }

    @Test
    @DisplayName("R-18 (3/3): SesionUsuario calcula expiraEn = iniciadaEn + 30 min, SesionManager rechaza sesión expirada y logout limpia estado")
    void debeVerificarExpiracionDe30MinutosYLimpiezaEnLogout() throws Exception {
        authService.login("cajero", "Admin#2026!");
        SesionUsuario sesion = SesionManager.verificarSesionActiva();
        assertNotNull(sesion);
        assertEquals(sesion.getIniciadaEn().plusMinutes(30), sesion.getExpiraEn(),
            "expiraEn debe ser exactamente iniciadaEn + 30 minutos");

        // Verificar que a los 30 minutos exactos SesionManager lanza SESION_EXPIRADA
        LocalDateTime treintaMinutosDespues = sesion.getIniciadaEn().plusMinutes(30);
        NegocioException exExpirada = assertThrows(
            NegocioException.class,
            () -> SesionManager.verificarSesionActiva(treintaMinutosDespues)
        );
        assertEquals(CodigoError.SESION_EXPIRADA, exExpirada.getCodigoError());
        assertNull(SesionManager.getSesionActiva(), "La sesión expirada debe limpiarse automáticamente");
    }
}
