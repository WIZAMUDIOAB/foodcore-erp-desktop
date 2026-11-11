package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.UiAsyncExecutor;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Controlador de la pantalla de inicio de sesión (§3 y §4 de UI-REDESIGN-SPEC-FOODCORE-v1.1).
 */
public class LoginController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginController.class);
    static final String MENSAJE_ERROR_BD_DESACTUALIZADA =
        "Error: la base de datos no está actualizada. Reinicie la aplicación o contacte al administrador.";
    static final String MENSAJE_ERROR_INESPERADO =
        "Error inesperado al iniciar sesión. Reinicie la aplicación o contacte al administrador.";

    @FXML private StackPane rootStackPane;
    @FXML private GridPane loginCardContainer;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private ProgressIndicator spinnerLogin;
    @FXML private Label lblMensaje;
    @FXML private Label lblVersionBadge;

    private final AuthService authService;
    private Parent dialogoPasswordActivo;
    private PasswordChangeDialogController dialogoPasswordController;
    private boolean autenticating = false;

    public LoginController() {
        this(new AuthServiceImpl());
    }

    LoginController(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        ocultarError();
        AnimationUtils.enterWithFadeSlide(loginCardContainer, 12.0, 0);
        AnimationUtils.attachTactileFeedback(btnLogin);
    }

    @FXML
    private void handleLogin() {
        if (autenticating) {
            return;
        }
        ocultarError();
        String user = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
        String pass = txtPassword.getText() != null ? txtPassword.getText().trim() : "";

        setEstadoCarga(true);

        UiAsyncExecutor.ejecutarAsync(
            () -> authService.login(user, pass),
            usuario -> {
                setEstadoCarga(false);
                if (usuario.isCambioPasswordRequerido()) {
                    // Regla §3.2 y §4.3: Abrir diálogo modal automáticamente, NUNCA mostrar label rojo en el login
                    abrirDialogoCambioPassword(usuario);
                    return;
                }
                irAlDashboard();
            },
            error -> {
                setEstadoCarga(false);
                mostrarError(resolverMensajeErrorLogin(error));
            }
        );
    }

    static String resolverMensajeErrorLogin(Throwable error) {
        if (error instanceof NegocioException) {
            return error.getMessage();
        }
        SQLException sqlEx = extraerSqlException(error);
        if (sqlEx != null || esErrorBdOEsquema(error)) {
            LOGGER.error(
                "Error de conexión MySQL o esquema desactualizado durante el inicio de sesión (SQLState={}, ErrorCode={})",
                sqlEx != null ? sqlEx.getSQLState() : "N/A",
                sqlEx != null ? sqlEx.getErrorCode() : -1,
                error
            );
            return MENSAJE_ERROR_BD_DESACTUALIZADA;
        }
        LOGGER.error("Error inesperado en inicio de sesión", error);
        return MENSAJE_ERROR_INESPERADO;
    }

    private static SQLException extraerSqlException(Throwable error) {
        Map<Throwable, Boolean> visitados = new IdentityHashMap<>();
        Throwable actual = error;
        while (actual != null && !visitados.containsKey(actual)) {
            if (actual instanceof SQLException sqlEx) {
                return sqlEx;
            }
            visitados.put(actual, Boolean.TRUE);
            actual = actual.getCause();
        }
        return null;
    }

    private static boolean esErrorBdOEsquema(Throwable error) {
        Map<Throwable, Boolean> visitados = new IdentityHashMap<>();
        Throwable actual = error;
        while (actual != null && !visitados.containsKey(actual)) {
            String msg = actual.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("unknown column")
                        || lower.contains("doesn't exist")
                        || lower.contains("communications link failure")
                        || lower.contains("connection refused")
                        || lower.contains("hikaripool")
                        || lower.contains("access denied for user")) {
                    return true;
                }
            }
            visitados.put(actual, Boolean.TRUE);
            actual = actual.getCause();
        }
        return false;
    }

    /**
     * Abre el diálogo modal de cambio de contraseña obligatorio (§3.2, §3.3, §5).
     */
    public void abrirDialogoCambioPassword(Usuario usuario) {
        try {
            cerrarDialogoCambioPassword();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/PasswordChangeDialog.fxml"));
            Parent dialogRoot = loader.load();
            PasswordChangeDialogController ctrl = loader.getController();

            this.dialogoPasswordActivo = dialogRoot;
            this.dialogoPasswordController = ctrl;

            if (loginCardContainer != null) {
                loginCardContainer.setDisable(true);
            }

            ctrl.initContext(
                usuario,
                () -> {
                    cerrarDialogoCambioPassword();
                    irAlDashboard();
                },
                () -> {
                    cerrarDialogoCambioPassword();
                    if (txtPassword != null) {
                        txtPassword.clear();
                        txtPassword.requestFocus();
                    }
                }
            );

            if (rootStackPane != null) {
                rootStackPane.getChildren().add(dialogRoot);
            }
        } catch (IOException e) {
            LOGGER.error("No se pudo cargar PasswordChangeDialog.fxml", e);
            mostrarError("No se pudo abrir el diálogo de cambio de contraseña.");
        }
    }

    private void cerrarDialogoCambioPassword() {
        if (dialogoPasswordActivo != null && rootStackPane != null) {
            rootStackPane.getChildren().remove(dialogoPasswordActivo);
        }
        dialogoPasswordActivo = null;
        dialogoPasswordController = null;
        if (loginCardContainer != null) {
            loginCardContainer.setDisable(false);
        }
    }

    private void irAlDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
            Parent root = loader.load();

            Scene currentScene = rootStackPane != null ? rootStackPane.getScene() : null;
            if (currentScene != null && currentScene.getWindow() instanceof Stage stage) {
                Scene dashboardScene = new Scene(root, 1920, 1080);
                App.applyStylesheets(dashboardScene);
                stage.setScene(dashboardScene);
                stage.setTitle("FoodCore ERP v3.0 - Panel Principal ["
                    + AuthService.getUsuarioSesionActual().getNombreRol() + "]");
                stage.setMinWidth(1280);
                stage.setMinHeight(720);
                stage.setMaximized(true);
            } else if (currentScene != null) {
                App.applyStylesheets(currentScene);
                currentScene.setRoot(root);
                if (currentScene.getWindow() instanceof Stage stage) {
                    stage.setMaximized(true);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error cargando MainLayout.fxml", e);
            mostrarError("Error cargando vista principal: " + e.getMessage());
        }
    }

    private void setEstadoCarga(boolean cargando) {
        this.autenticating = cargando;
        if (btnLogin != null) {
            btnLogin.setDisable(cargando);
        }
        if (spinnerLogin != null) {
            spinnerLogin.setVisible(cargando);
            spinnerLogin.setManaged(cargando);
        }
    }

    private void mostrarError(String msg) {
        if (lblMensaje != null) {
            lblMensaje.setText(msg);
            lblMensaje.setVisible(true);
            lblMensaje.setManaged(true);
        }
    }

    private void ocultarError() {
        if (lblMensaje != null) {
            lblMensaje.setText("");
            lblMensaje.setVisible(false);
            lblMensaje.setManaged(false);
        }
    }

    public PasswordChangeDialogController getDialogoPasswordController() {
        return dialogoPasswordController;
    }

    public boolean isDialogoPasswordAbierto() {
        return dialogoPasswordActivo != null
            && rootStackPane != null
            && rootStackPane.getChildren().contains(dialogoPasswordActivo);
    }
}
