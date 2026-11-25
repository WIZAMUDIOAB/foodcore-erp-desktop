package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.config.AppContainer;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.SesionManager;
import com.foodcore.security.TotpService;
import com.foodcore.service.AuditoriaConsultaService;
import com.foodcore.service.AuthService;
import com.foodcore.service.OnboardingService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.UiAsyncExecutor;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    @FXML private Canvas auroraCanvas;
    @FXML private GridPane loginCardContainer;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private ProgressIndicator spinnerLogin;
    @FXML private Label lblMensaje;
    @FXML private Label lblVersionBadge;
    @FXML private VBox overlayTotp;
    @FXML private Label lblTotpSubtitulo;
    @FXML private TextField txtTotpCodigo;
    @FXML private Label lblTotpError;
    @FXML private Button btnVerificarTotp;
    @FXML private Button btnCancelarTotp;

    private final AuthService authService;
    private final OnboardingService onboardingService;
    private final TotpService totpService;
    private final AuditoriaConsultaService auditoriaService;
    private Parent dialogoPasswordActivo;
    private PasswordChangeDialogController dialogoPasswordController;
    private Usuario usuarioPendiente2FA;
    private Timeline auroraTimeline;
    private double auroraPhase = 0.0;
    private boolean autenticating = false;

    public LoginController() {
        this(AppContainer.getDefault());
    }

    private LoginController(AppContainer container) {
        this(container.authService(),
                container.resolve(OnboardingService.class),
                container.resolve(TotpService.class),
                container.auditoriaConsultaService());
    }

    LoginController(AuthService authService) {
        this(authService,
                AppContainer.getDefault().resolve(OnboardingService.class),
                AppContainer.getDefault().resolve(TotpService.class),
                AppContainer.getDefault().auditoriaConsultaService());
    }

    public LoginController(AuthService authService,
                           OnboardingService onboardingService,
                           TotpService totpService,
                           AuditoriaConsultaService auditoriaService) {
        this.authService = Objects.requireNonNull(authService, "authService no puede ser nulo");
        this.onboardingService = Objects.requireNonNull(onboardingService, "onboardingService no puede ser nulo");
        this.totpService = Objects.requireNonNull(totpService, "totpService no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        ocultarError();
        iniciarAnimacionAurora();
        AnimationUtils.enterWithFadeSlide(loginCardContainer, 12.0, 0);
        AnimationUtils.attachTactileFeedback(btnLogin);
    }

    /**
     * Inicializa y anima el fondo Aurora en bucle lento con orbes Terracota, Ámbar y Gris Oscuro (F1).
     */
    private void iniciarAnimacionAurora() {
        if (auroraCanvas == null) {
            return;
        }
        if (rootStackPane != null) {
            auroraCanvas.widthProperty().bind(rootStackPane.widthProperty());
            auroraCanvas.heightProperty().bind(rootStackPane.heightProperty());
        }
        auroraCanvas.setEffect(new GaussianBlur(16.0));
        auroraCanvas.widthProperty().addListener((obs, o, n) -> dibujarFrameAurora(auroraPhase));
        auroraCanvas.heightProperty().addListener((obs, o, n) -> dibujarFrameAurora(auroraPhase));
        dibujarFrameAurora(0.0);

        auroraTimeline = new Timeline(new KeyFrame(Duration.millis(40), e -> {
            auroraPhase += 0.025;
            dibujarFrameAurora(auroraPhase);
        }));
        auroraTimeline.setCycleCount(Animation.INDEFINITE);
        auroraTimeline.play();
    }

    private void dibujarFrameAurora(double phase) {
        if (auroraCanvas == null) {
            return;
        }
        double w = Math.max(1040.0, auroraCanvas.getWidth());
        double h = Math.max(680.0, auroraCanvas.getHeight());
        GraphicsContext gc = auroraCanvas.getGraphicsContext2D();

        gc.setFill(Color.rgb(24, 24, 27));
        gc.fillRect(0, 0, w, h);

        double radiusPrincipal = Math.max(w, h) * 0.58;
        // Orbe 1: Terracota (#EA580C)
        double x1 = w * (0.28 + 0.16 * Math.sin(phase));
        double y1 = h * (0.32 + 0.14 * Math.cos(phase * 0.8));
        gc.setFill(new RadialGradient(
                0, 0, x1, y1, radiusPrincipal, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(234, 88, 12, 0.42)),
                new Stop(1.0, Color.TRANSPARENT)));
        gc.fillRect(0, 0, w, h);

        // Orbe 2: Ámbar cálido (#D97706)
        double x2 = w * (0.74 + 0.15 * Math.cos(phase * 0.7));
        double y2 = h * (0.68 + 0.14 * Math.sin(phase * 0.9));
        gc.setFill(new RadialGradient(
                0, 0, x2, y2, radiusPrincipal * 0.90, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(217, 119, 6, 0.32)),
                new Stop(1.0, Color.TRANSPARENT)));
        gc.fillRect(0, 0, w, h);

        // Orbe 3: Gris Oscuro / Zinc (#27272A)
        double x3 = w * (0.52 + 0.18 * Math.sin(phase * 0.5 + 1.2));
        double y3 = h * (0.20 + 0.16 * Math.cos(phase * 0.6));
        gc.setFill(new RadialGradient(
                0, 0, x3, y3, radiusPrincipal * 0.85, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(63, 63, 70, 0.55)),
                new Stop(1.0, Color.TRANSPARENT)));
        gc.fillRect(0, 0, w, h);
    }

    /**
     * Detiene la línea de tiempo del fondo Aurora al abandonar la vista de Login.
     */
    public void detenerAnimacionAurora() {
        if (auroraTimeline != null) {
            auroraTimeline.stop();
        }
    }

    public boolean isAuroraActiva() {
        return auroraTimeline != null && auroraTimeline.getStatus() == Animation.Status.RUNNING;
    }

    public Canvas getAuroraCanvas() {
        return auroraCanvas;
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
            () -> autenticarCredencialesSinSesion(user, pass),
            usuario -> {
                setEstadoCarga(false);
                procesarUsuarioAutenticado(usuario);
            },
            error -> {
                setEstadoCarga(false);
                mostrarError(resolverMensajeErrorLogin(error));
            }
        );
    }

    /**
     * Valida credenciales sin establecer sesión activa en {@link SesionManager} cuando 2FA TOTP está habilitado.
     * Utiliza {@code AuthService.autenticarSoloCredenciales(String, String)} si está disponible.
     */
    private Usuario autenticarCredencialesSinSesion(String user, String pass) throws Exception {
        Method stateless = buscarMetodoStatelessSiAplica(authService);
        if (stateless != null) {
            try {
                return (Usuario) stateless.invoke(authService, user, pass);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof Exception e) {
                    throw e;
                }
                if (cause instanceof Error err) {
                    throw err;
                }
                throw new IllegalStateException("Error invocando autenticarSoloCredenciales", cause);
            }
        }
        Usuario usuario = authService.login(user, pass);
        if (usuario != null && usuario.isTotpHabilitado()) {
            SesionManager.cerrarSesion();
        }
        return usuario;
    }

    private static Method buscarMetodoStatelessSiAplica(AuthService service) {
        Class<?> clazz = service.getClass();
        Method stateless;
        try {
            stateless = clazz.getMethod("autenticarSoloCredenciales", String.class, String.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
        // Si una subclase de prueba sobrescribe únicamente login(String, String), respetarla sin saltar el mock.
        boolean declaraLoginEnSubclase = false;
        boolean declaraStatelessEnSubclase = false;
        if (clazz.getSuperclass() != Object.class) {
            try {
                clazz.getDeclaredMethod("login", String.class, String.class);
                declaraLoginEnSubclase = true;
            } catch (NoSuchMethodException ignored) {
                declaraLoginEnSubclase = false;
            }
            try {
                clazz.getDeclaredMethod("autenticarSoloCredenciales", String.class, String.class);
                declaraStatelessEnSubclase = true;
            } catch (NoSuchMethodException ignored) {
                declaraStatelessEnSubclase = false;
            }
        }
        if (declaraLoginEnSubclase && !declaraStatelessEnSubclase) {
            return null;
        }
        return stateless;
    }

    private void establecerSesionActiva(Usuario usuario) {
        if (usuario == null) {
            return;
        }
        authService.establecerSesionActiva(usuario);
    }

    /**
     * Evalúa requisitos post-contraseña (2FA TOTP y cambio de clave obligatorio).
     * Nunca establece {@link SesionManager} antes de superar la verificación 2FA TOTP.
     *
     * @param usuario Usuario autenticado por credenciales.
     */
    public void procesarUsuarioAutenticado(Usuario usuario) {
        if (usuario == null) {
            mostrarError("Credenciales inválidas.");
            return;
        }
        if (usuario.isTotpHabilitado()) {
            SesionManager.cerrarSesion();
            abrirModalTotp(usuario);
            return;
        }
        completarPostLogin(usuario);
    }

    private void abrirModalTotp(Usuario usuario) {
        this.usuarioPendiente2FA = usuario;
        if (loginCardContainer != null) {
            loginCardContainer.setDisable(true);
        }
        if (lblTotpSubtitulo != null) {
            lblTotpSubtitulo.setText("Cuenta protegida (" + usuario.getUsername()
                    + "). Ingrese el código TOTP de 6 dígitos de su autenticador.");
        }
        if (txtTotpCodigo != null) {
            txtTotpCodigo.clear();
        }
        ocultarErrorTotp();
        if (overlayTotp != null) {
            overlayTotp.setVisible(true);
            overlayTotp.setManaged(true);
        }
        if (txtTotpCodigo != null) {
            txtTotpCodigo.requestFocus();
        }
    }

    /**
     * Valida el código TOTP ingresado en la ventana de 2FA antes de habilitar el Dashboard.
     */
    @FXML
    public void handleVerificarTotp() {
        if (usuarioPendiente2FA == null) {
            return;
        }
        String codigo = txtTotpCodigo != null ? txtTotpCodigo.getText() : "";
        if (!totpService.verificarCodigo(usuarioPendiente2FA.getTotpSecret(), codigo)) {
            auditoriaService.registrarEventoBestEffort(
                    usuarioPendiente2FA.getIdUsuario(),
                    "2FA_FALLIDO",
                    "USUARIO",
                    null,
                    "Código TOTP desktop inválido para " + usuarioPendiente2FA.getUsername());
            mostrarErrorTotp("Código 2FA inválido o expirado. Verifique los 6 dígitos e intente de nuevo.");
            return;
        }
        Usuario verificado = usuarioPendiente2FA;
        cerrarModalTotp();
        completarPostLogin(verificado);
    }

    /**
     * Cancela la verificación 2FA y limpia la sesión temporal.
     */
    @FXML
    public void handleCancelarTotp() {
        cerrarModalTotp();
        SesionManager.cerrarSesion();
        if (txtPassword != null) {
            txtPassword.clear();
            txtPassword.requestFocus();
        }
    }

    public boolean isModalTotpAbierto() {
        return overlayTotp != null && overlayTotp.isVisible();
    }

    private void cerrarModalTotp() {
        this.usuarioPendiente2FA = null;
        ocultarErrorTotp();
        if (overlayTotp != null) {
            overlayTotp.setVisible(false);
            overlayTotp.setManaged(false);
        }
        if (loginCardContainer != null) {
            loginCardContainer.setDisable(false);
        }
    }

    private void completarPostLogin(Usuario usuario) {
        establecerSesionActiva(usuario);
        if (usuario.isCambioPasswordRequerido()) {
            // Regla §3.2 y §4.3: Abrir diálogo modal automáticamente, NUNCA mostrar label rojo en el login
            abrirDialogoCambioPassword(usuario);
            return;
        }
        irAlDashboard();
    }

    private void mostrarErrorTotp(String msg) {
        if (lblTotpError != null) {
            lblTotpError.setText(msg);
            lblTotpError.setVisible(true);
            lblTotpError.setManaged(true);
        }
    }

    private void ocultarErrorTotp() {
        if (lblTotpError != null) {
            lblTotpError.setText("");
            lblTotpError.setVisible(false);
            lblTotpError.setManaged(false);
        }
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
            FXMLLoader loader = AppContainer.getDefault().createLoader(
                    getClass().getResource("/com/foodcore/view/PasswordChangeDialog.fxml"));
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
        detenerAnimacionAurora();
        boolean requiereOnboarding = onboardingService.necesitaOnboarding();
        String vistaObjetivo = requiereOnboarding
                ? "/com/foodcore/view/OnboardingWizardView.fxml"
                : "/com/foodcore/view/MainLayout.fxml";
        String rol = SesionManager.getUsuarioActual() != null
                ? SesionManager.getUsuarioActual().getNombreRol()
                : "ADMIN";
        String titulo = requiereOnboarding
                ? "FoodCore ERP v5.5 - Configuración Inicial (Onboarding)"
                : "FoodCore ERP v3.0 - Panel Principal [" + rol + "]";
        try {
            FXMLLoader loader = AppContainer.getDefault().createLoader(getClass().getResource(vistaObjetivo));
            Parent root = loader.load();

            Scene currentScene = rootStackPane != null ? rootStackPane.getScene() : null;
            if (currentScene != null && currentScene.getWindow() instanceof Stage stage) {
                Scene nextScene = requiereOnboarding
                        ? new Scene(root, 1080, 768)
                        : new Scene(root, 1920, 1080);
                App.applyStylesheets(nextScene);
                stage.setScene(nextScene);
                stage.setTitle(titulo);
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
            LOGGER.error("Error cargando " + vistaObjetivo, e);
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
