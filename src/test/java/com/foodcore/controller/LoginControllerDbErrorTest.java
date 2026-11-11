package com.foodcore.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.foodcore.App;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.service.AuthService;
import com.foodcore.support.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que {@link LoginController} oculte errores técnicos de MySQL o esquema desactualizado
 * mostrando el mensaje amigable esperado y registrando el error real con SLF4J en nivel ERROR.
 */
class LoginControllerDbErrorTest {

    @BeforeAll
    static void initFx() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @Test
    @DisplayName("Simulación UI Login con Unknown column -> mensaje amigable sin detalles SQL, log SLF4J ERROR y captura PNG")
    void loginUiMuestraMensajeAmigableAnteEsquemaDesactualizadoYLogueaErrorRealSlf4j() throws Exception {
        AuthService failingAuthService = new AuthService(new UsuarioDAOImpl(), new EncriptadorPassword()) {
            @Override
            public Usuario login(String username, String password) throws SQLException {
                throw new SQLException("Unknown column 'intentos_fallidos' in 'field list'", "42S22", 1054);
            }
        };

        Logger logger = (Logger) LoggerFactory.getLogger(LoginController.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Button> btnRef = new AtomicReference<>();
        AtomicReference<Label> lblRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                    loader.setControllerFactory(type -> type == LoginController.class
                            ? new LoginController(failingAuthService)
                            : crearInstanciaPorDefecto(type));
                    Parent root = loader.load();
                    Scene scene = new Scene(root, 1080, 700);
                    App.applyStylesheets(scene);

                    TextField txtUsername = (TextField) root.lookup("#txtUsername");
                    PasswordField txtPassword = (PasswordField) root.lookup("#txtPassword");
                    Button btnLogin = (Button) root.lookup("#btnLogin");
                    Label lblMensaje = (Label) root.lookup("#lblMensaje");

                    txtUsername.setText("admin");
                    txtPassword.setText("Admin#2026!");
                    rootRef.set(root);
                    btnRef.set(btnLogin);
                    lblRef.set(lblMensaje);
                    btnLogin.fire();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            long deadline = System.currentTimeMillis() + Duration.ofSeconds(6).toMillis();
            while (System.currentTimeMillis() < deadline) {
                AtomicBoolean done = new AtomicBoolean(false);
                JavaFxTestSupport.runOnFxThreadAndWait(() -> done.set(
                        !btnRef.get().isDisable() && lblRef.get().isVisible()
                ));
                if (done.get()) {
                    break;
                }
                Thread.sleep(25);
            }

            AtomicReference<String> uiMsgRef = new AtomicReference<>();
            AtomicBoolean visibleRef = new AtomicBoolean(false);
            Path evidenceDir = Path.of("target", "v435-evidence");
            Files.createDirectories(evidenceDir);
            File pngFile = evidenceDir.resolve("login_friendly_db_error.png").toFile();

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                visibleRef.set(lblRef.get().isVisible());
                uiMsgRef.set(lblRef.get().getText());
                rootRef.get().applyCss();
                rootRef.get().layout();
                WritableImage image = rootRef.get().snapshot(new SnapshotParameters(), null);
                BufferedImage buffered = new BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        BufferedImage.TYPE_INT_ARGB
                );
                PixelReader reader = image.getPixelReader();
                for (int y = 0; y < buffered.getHeight(); y++) {
                    for (int x = 0; x < buffered.getWidth(); x++) {
                        buffered.setRGB(x, y, reader.getArgb(x, y));
                    }
                }
                try {
                    ImageIO.write(buffered, "png", pngFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(visibleRef.get(), "El label de error debe mostrarse en la UI de login");
            assertEquals(LoginController.MENSAJE_ERROR_BD_DESACTUALIZADA, uiMsgRef.get());
            assertFalse(uiMsgRef.get().contains("Unknown column"));
            assertFalse(uiMsgRef.get().contains("intentos_fallidos"));
            assertFalse(uiMsgRef.get().contains("MySQL"));
            assertTrue(pngFile.exists() && pngFile.length() > 1000, "Debe generarse captura PNG de evidencia");

            ILoggingEvent errorEvent = listAppender.list.stream()
                    .filter(ev -> ev.getLevel() == Level.ERROR)
                    .findFirst()
                    .orElse(null);
            assertNotNull(errorEvent, "Debe registrarse un evento SLF4J ERROR con el detalle técnico");
            assertNotNull(errorEvent.getThrowableProxy());
            assertTrue(errorEvent.getThrowableProxy().getMessage().contains("Unknown column 'intentos_fallidos'"));
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("Errores envueltos de tabla inexistente o conexión MySQL usan el mensaje amigable; NegocioException se conserva")
    void resolverMensajeCubreTablaInexistenteConexionYNegocio() {
        String msgTabla = LoginController.resolverMensajeErrorLogin(
                new RuntimeException("Wrapper", new SQLException("Table 'foodcore.usuario' doesn't exist", "42S02", 1146))
        );
        assertEquals(LoginController.MENSAJE_ERROR_BD_DESACTUALIZADA, msgTabla);

        String msgConexion = LoginController.resolverMensajeErrorLogin(
                new SQLException("Communications link failure", "08S01", 0)
        );
        assertEquals(LoginController.MENSAJE_ERROR_BD_DESACTUALIZADA, msgConexion);

        String msgNegocio = LoginController.resolverMensajeErrorLogin(
                new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Credenciales incorrectas (1/5).")
        );
        assertEquals("Credenciales incorrectas (1/5).", msgNegocio);
    }

    private static Object crearInstanciaPorDefecto(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo instanciar controlador " + type.getName(), e);
        }
    }
}
