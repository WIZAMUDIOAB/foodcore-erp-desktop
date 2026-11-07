package com.foodcore.controller;

import com.foodcore.App;
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
 * Suite TestFX / JavaFX de la vista de Login v1.1, bloqueo por 5 intentos fallidos (15 min),
 * flujo end-to-end del diálogo modal de cambio de contraseña obligatorio y expiración de sesión
 * según SPEC-FOODCORE-v1.0 y UI-REDESIGN-SPEC-FOODCORE-v1.1 (§3, §4, §5, §13, §14).
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
                Scene scene = new Scene(root, 1080, 700);
                App.applyStylesheets(scene);

                Label lblMensaje = (Label) root.lookup("#lblMensaje");
                assertFalse(lblMensaje.isVisible(), "El login no debe mostrar ningún label rojo permanente");

                Label lblBadge = (Label) root.lookup("#lblVersionBadge");
                assertNotNull(lblBadge);
                assertEquals("v1.1.0", lblBadge.getText());

                TextField txtUsername = (TextField) root.lookup("#txtUsername");
                PasswordField txtPassword = (PasswordField) root.lookup("#txtPassword");
                Button btnLogin = (Button) root.lookup("#btnLogin");

                txtUsername.setText("cajero");
                txtPassword.setText("Admin#2026!");
                btnLogin.fire();

                SesionUsuario sesion = SesionManager.getSesionActiva();
                assertNotNull(sesion, "Debe existir sesión activa tras login válido");
                assertEquals("CAJERO", sesion.getCodigoRol());
                assertEquals("rootDashboardStack", scene.getRoot().getId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.2: testLoginFallido_IncrementaContadorVisible - Intentos 1 a 3 muestran advertencia visible i/5")
    void testLoginFallido_IncrementaContadorVisible() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1080, 700);

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
                new Scene(root, 1080, 700);

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
    @DisplayName("B6.4 & E2E v1.1: Login admin -> diálogo -> cancelar -> reintentar -> cambio -> dashboard -> cerrar -> reabrir -> sin diálogo")
    void testCambioPasswordObligatorio_BloqueaOperacionesHastaActualizar() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1080, 700);
                App.applyStylesheets(scene);
                LoginController loginCtrl = loader.getController();

                TextField txtUsername = (TextField) root.lookup("#txtUsername");
                PasswordField txtPassword = (PasswordField) root.lookup("#txtPassword");
                Button btnLogin = (Button) root.lookup("#btnLogin");
                Label lblMensaje = (Label) root.lookup("#lblMensaje");

                // 1. Login inicial con admin / Admin#2026! (cambio_password_requerido = 1)
                txtUsername.setText("admin");
                txtPassword.setText("Admin#2026!");
                btnLogin.fire();

                // Criterio 4 y 5: No muestra label rojo en el login; se abre el diálogo automáticamente
                assertFalse(lblMensaje.isVisible(), "No debe mostrarse ningún label rojo en LoginView");
                assertTrue(loginCtrl.isDialogoPasswordAbierto(), "El diálogo modal debe abrirse automáticamente");
                assertTrue(SesionManager.isCambioPasswordPendiente());

                NegocioException exPendiente = assertThrows(
                    NegocioException.class,
                    SesionManager::verificarSesionActiva
                );
                assertEquals(CodigoError.CAMBIO_PASSWORD_REQUERIDO, exPendiente.getCodigoError());

                // 2. Criterio 8: Si el usuario cancela el diálogo, vuelve al login
                PasswordChangeDialogController dialogCtrl1 = loginCtrl.getDialogoPasswordController();
                assertNotNull(dialogCtrl1);
                dialogCtrl1.getBtnCancelar().fire();

                assertFalse(loginCtrl.isDialogoPasswordAbierto(), "El diálogo debe cerrarse al cancelar");
                assertNull(SesionManager.getSesionActiva(), "La sesión debe cerrarse si cancela el cambio obligatorio");
                assertEquals(root, scene.getRoot(), "Debe permanecer en LoginView tras cancelar");

                // 3. Criterio 8: Al reintentar login con admin, el diálogo aparece otra vez
                txtPassword.setText("Admin#2026!");
                btnLogin.fire();
                assertTrue(loginCtrl.isDialogoPasswordAbierto(), "El diálogo debe volver a abrirse al reintentar");

                PasswordChangeDialogController dialogCtrl2 = loginCtrl.getDialogoPasswordController();
                assertNotNull(dialogCtrl2);

                // Validación: no permitir cambiar a una contraseña idéntica a la actual (§3.4)
                dialogCtrl2.getTxtPasswordActual().setText("Admin#2026!");
                dialogCtrl2.getTxtPasswordNueva().setText("Admin#2026!");
                dialogCtrl2.getTxtPasswordConfirmar().setText("Admin#2026!");
                dialogCtrl2.getBtnCambiarPassword().fire();
                assertTrue(dialogCtrl2.getLblErrorPassword().isVisible(), "Debe mostrar error si nueva == actual");
                assertTrue(loginCtrl.isDialogoPasswordAbierto(), "No debe cerrar el diálogo si hay error");

                // Validación: contraseña actual incorrecta (§3.2)
                dialogCtrl2.getTxtPasswordActual().setText("ClaveActualIncorrecta1");
                dialogCtrl2.getTxtPasswordNueva().setText("NuevaClaveSegura#2026!");
                dialogCtrl2.getTxtPasswordConfirmar().setText("NuevaClaveSegura#2026!");
                dialogCtrl2.getBtnCambiarPassword().fire();
                assertTrue(dialogCtrl2.getLblErrorPassword().isVisible(), "Debe mostrar error si clave actual no coincide");
                assertTrue(loginCtrl.isDialogoPasswordAbierto());

                // 4. Criterio 6: Completar el diálogo con datos válidos -> entra directo al dashboard
                dialogCtrl2.getTxtPasswordActual().setText("Admin#2026!");
                dialogCtrl2.getTxtPasswordNueva().setText("NuevaClaveSegura#2026!");
                dialogCtrl2.getTxtPasswordConfirmar().setText("NuevaClaveSegura#2026!");
                dialogCtrl2.getBtnCambiarPassword().fire();

                assertFalse(SesionManager.isCambioPasswordPendiente(), "cambio_password_requerido debe quedar en 0");
                assertNotNull(SesionManager.verificarSesionActiva());
                assertEquals("rootDashboardStack", scene.getRoot().getId(), "Debe entrar directo al dashboard sin pasar por login");

                // 5. Criterio 7: Cerrar sesión / reabrir y loguearse con la nueva contraseña -> el diálogo ya no aparece
                Button btnCerrarSesion = (Button) scene.getRoot().lookup("#btnCerrarSesion");
                assertNotNull(btnCerrarSesion);
                btnCerrarSesion.fire();

                Parent reloginRoot = scene.getRoot();
                assertEquals("rootStackPane", reloginRoot.getId(), "Debe haber vuelto a LoginView");

                TextField txtUser2 = (TextField) reloginRoot.lookup("#txtUsername");
                PasswordField txtPass2 = (PasswordField) reloginRoot.lookup("#txtPassword");
                Button btnLogin2 = (Button) reloginRoot.lookup("#btnLogin");

                txtUser2.setText("admin");
                txtPass2.setText("NuevaClaveSegura#2026!");
                btnLogin2.fire();

                assertEquals("rootDashboardStack", scene.getRoot().getId(), "En el segundo login debe ir directo al dashboard sin abrir diálogo");
                assertFalse(SesionManager.isCambioPasswordPendiente());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
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
