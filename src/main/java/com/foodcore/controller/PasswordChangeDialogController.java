package com.foodcore.controller;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.service.AuthService;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.UiAsyncExecutor;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador del diálogo modal de cambio de contraseña obligatorio (§3 y §5 de UI-REDESIGN-SPEC-FOODCORE-v1.1).
 */
public class PasswordChangeDialogController {

    private static final Logger LOGGER = Logger.getLogger(PasswordChangeDialogController.class.getName());

    @FXML private StackPane passwordChangeDialogRoot;
    @FXML private VBox dialogCard;
    @FXML private Label lblTituloDialogo;
    @FXML private Label lblSubtituloDialogo;
    @FXML private PasswordField txtPasswordActual;
    @FXML private PasswordField txtPasswordNueva;
    @FXML private PasswordField txtPasswordConfirmar;
    @FXML private Label lblErrorPassword;
    @FXML private Button btnCancelar;
    @FXML private Button btnCambiarPassword;
    @FXML private ProgressIndicator spinnerCarga;

    private final AuthService authService = new AuthServiceImpl();
    private final EncriptadorPassword encriptador = new EncriptadorPassword();

    private Usuario usuarioObjetivo;
    private Runnable onCambioExitoso;
    private Runnable onCancelar;
    private boolean procesando = false;

    @FXML
    public void initialize() {
        ocultarError();
        AnimationUtils.attachTactileFeedback(btnCambiarPassword);
        AnimationUtils.attachTactileFeedback(btnCancelar);
        AnimationUtils.animateModalEnter(dialogCard);
    }

    public void initContext(Usuario usuario, Runnable onSuccess, Runnable onCancel) {
        this.usuarioObjetivo = usuario;
        this.onCambioExitoso = onSuccess;
        this.onCancelar = onCancel;
        ocultarError();
        if (txtPasswordActual != null) {
            txtPasswordActual.requestFocus();
        }
    }

    @FXML
    private void handleCambiarPassword() {
        if (procesando) {
            return;
        }
        ocultarError();

        String actual = txtPasswordActual.getText() != null ? txtPasswordActual.getText() : "";
        String nueva = txtPasswordNueva.getText() != null ? txtPasswordNueva.getText() : "";
        String confirmar = txtPasswordConfirmar.getText() != null ? txtPasswordConfirmar.getText() : "";

        // 1. Validaciones en cliente (§3.3)
        if (actual.isBlank() || nueva.isBlank() || confirmar.isBlank()) {
            mostrarError("Todos los campos son obligatorios. Complete su contraseña actual y la nueva clave.");
            return;
        }

        if (nueva.equals(actual)) {
            mostrarError("La nueva contraseña no puede ser idéntica a la contraseña actual.");
            return;
        }

        if (!nueva.equals(confirmar)) {
            mostrarError("La nueva contraseña y su confirmación no coinciden.");
            return;
        }

        if (!cumplePoliticaComplejidad(nueva)) {
            mostrarError("La nueva contraseña debe tener mínimo 8 caracteres, al menos 1 mayúscula, 1 minúscula y 1 número.");
            return;
        }

        Usuario usuario = usuarioObjetivo != null ? usuarioObjetivo : AuthService.getUsuarioSesionActual();
        if (usuario == null || !usuario.isActivo()) {
            mostrarError("No se encontró una sesión de usuario activa para procesar el cambio.");
            return;
        }

        // 2. Estado de carga: spinner + botón deshabilitado (§3.3) en segundo plano sin bloquear el hilo JavaFX
        setEstadoCarga(true);
        final String hashActual = usuario.getPasswordHash();
        final int idUsuarioObjetivo = usuario.getIdUsuario();
        final String usernameObjetivo = usuario.getUsername();

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                boolean actualValida = (hashActual != null && hashActual.startsWith("$2"))
                    ? encriptador.verificar(actual, hashActual)
                    : actual.equals(hashActual);
                if (!actualValida) {
                    throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS,
                        "La contraseña actual ingresada es incorrecta. Verifique e intente nuevamente.");
                }
                authService.cambiarPasswordObligatorio(idUsuarioObjetivo, nueva);
                authService.login(usernameObjetivo, nueva);
            },
            () -> {
                setEstadoCarga(false);
                if (onCambioExitoso != null) {
                    onCambioExitoso.run();
                }
            },
            error -> {
                setEstadoCarga(false);
                if (error instanceof NegocioException) {
                    mostrarError(error.getMessage());
                } else if (error instanceof SQLException) {
                    LOGGER.log(Level.SEVERE, "Error de base de datos al actualizar contraseña obligatoria", error);
                    mostrarError("Error de conexión con la base de datos al actualizar la contraseña.");
                } else {
                    LOGGER.log(Level.SEVERE, "Error inesperado al actualizar contraseña obligatoria", error);
                    mostrarError("Error inesperado al actualizar contraseña.");
                }
            }
        );
    }

    @FXML
    private void handleCancelar() {
        if (procesando) {
            return;
        }
        AuthService.logout();
        if (onCancelar != null) {
            onCancelar.run();
        }
    }

    public static boolean cumplePoliticaComplejidad(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean tieneMayuscula = false;
        boolean tieneMinuscula = false;
        boolean tieneNumero = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                tieneMayuscula = true;
            } else if (Character.isLowerCase(c)) {
                tieneMinuscula = true;
            } else if (Character.isDigit(c)) {
                tieneNumero = true;
            }
        }
        return tieneMayuscula && tieneMinuscula && tieneNumero;
    }

    private void setEstadoCarga(boolean cargando) {
        this.procesando = cargando;
        if (btnCambiarPassword != null) {
            btnCambiarPassword.setDisable(cargando);
        }
        if (btnCancelar != null) {
            btnCancelar.setDisable(cargando);
        }
        if (spinnerCarga != null) {
            spinnerCarga.setVisible(cargando);
            spinnerCarga.setManaged(cargando);
        }
    }

    private void mostrarError(String mensaje) {
        if (lblErrorPassword != null) {
            lblErrorPassword.setText(mensaje);
            lblErrorPassword.setVisible(true);
            lblErrorPassword.setManaged(true);
        }
    }

    private void ocultarError() {
        if (lblErrorPassword != null) {
            lblErrorPassword.setText("");
            lblErrorPassword.setVisible(false);
            lblErrorPassword.setManaged(false);
        }
    }

    public PasswordField getTxtPasswordActual() {
        return txtPasswordActual;
    }

    public PasswordField getTxtPasswordNueva() {
        return txtPasswordNueva;
    }

    public PasswordField getTxtPasswordConfirmar() {
        return txtPasswordConfirmar;
    }

    public Button getBtnCambiarPassword() {
        return btnCambiarPassword;
    }

    public Button getBtnCancelar() {
        return btnCancelar;
    }

    public Label getLblErrorPassword() {
        return lblErrorPassword;
    }
}
