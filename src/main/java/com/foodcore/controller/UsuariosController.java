package com.foodcore.controller;

import com.foodcore.api.security.TokenBlacklistService;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.security.TotpService;
import com.foodcore.service.AuthService;
import com.foodcore.exception.CodigoError;
import com.foodcore.service.UsuarioGestionService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.DialogService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Administración de Usuarios según SPEC-FOODCORE-v1.2 (§2.4):
 * CRUD de usuarios (exclusivo Admin) y reseteo de contraseña forzando {@code cambio_password_requerido = 1}.
 */
public class UsuariosController {

    private static final Logger LOGGER = Logger.getLogger(UsuariosController.class.getName());

    public record FilaUsuario(int idUsuario, String username, String nombreCompleto, String rol,
            boolean cambioRequerido, boolean activo, boolean pinConfigurado, boolean totpHabilitado) {
        public FilaUsuario(int idUsuario, String username, String nombreCompleto, String rol,
                boolean cambioRequerido, boolean activo, boolean pinConfigurado) {
            this(idUsuario, username, nombreCompleto, rol, cambioRequerido, activo, pinConfigurado, false);
        }
    }

    @FXML private StackPane rootUsuariosStack;
    @FXML private TableView<FilaUsuario> tblUsuarios;
    @FXML private TableColumn<FilaUsuario, String> colUsername;
    @FXML private TableColumn<FilaUsuario, String> colNombre;
    @FXML private TableColumn<FilaUsuario, String> colRol;
    @FXML private TableColumn<FilaUsuario, String> colCambioClave;
    @FXML private TableColumn<FilaUsuario, String> colPinConfigurado;
    @FXML private TableColumn<FilaUsuario, String> col2FA;
    @FXML private TableColumn<FilaUsuario, String> colEstado;

    @FXML private TextField txtUsername;
    @FXML private TextField txtNombreCompleto;
    @FXML private TextField txtDni;
    @FXML private TextField txtSueldoBase;
    @FXML private TextField txtHorarioLaboral;
    @FXML private ComboBox<String> cmbRol;
    @FXML private PasswordField txtPasswordTemporal;
    @FXML private CheckBox chkUsuarioActivo;
    @FXML private Button btnCrearUsuario;
    @FXML private Button btnResetearPassword;
    @FXML private Button btnConfigurarPin;
    @FXML private Button btnConfigurar2FA;
    @FXML private Button btnCerrarSesionRemota;

    @FXML private VBox modalConfigurar2FA;
    @FXML private Label lblModal2faUsuario;
    @FXML private ImageView imgQr2fa;
    @FXML private Label lblSecreto2fa;
    @FXML private TextField txtCodigoVerificacion2fa;
    @FXML private Label lblError2fa;
    @FXML private Button btnCancelar2FA;
    @FXML private Button btnDesactivar2FA;
    @FXML private Button btnConfirmar2FA;


    private final TotpService totpService = new TotpService();
    private final UsuarioGestionService usuarioGestionService = new UsuarioGestionService();
    private final TokenBlacklistService tokenBlacklistService = TokenBlacklistService.getInstance();
    private final ObservableList<FilaUsuario> listaUsuarios = FXCollections.observableArrayList();

    private FilaUsuario usuario2faEnEdicion;
    private String secretoTemporal2fa;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnCrearUsuario);
        AnimationUtils.attachTactileFeedback(btnResetearPassword);
        AnimationUtils.attachTactileFeedback(btnConfigurarPin);
        if (btnConfigurar2FA != null) {
            AnimationUtils.attachTactileFeedback(btnConfigurar2FA);
        }
        if (btnCerrarSesionRemota != null) {
            AnimationUtils.attachTactileFeedback(btnCerrarSesionRemota);
        }

        cmbRol.setItems(FXCollections.observableArrayList("ADMIN", "CAJERO", "MOZO", "COCINA", "ALMACEN"));
        cmbRol.getSelectionModel().select("CAJERO");

        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombreCompleto()));
        colRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().rol()));
        colPinConfigurado.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().pinConfigurado() ? "CONFIGURADO" : "SIN PIN"
        ));
        colPinConfigurado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(item);
                if ("CONFIGURADO".equals(item)) {
                    badge.getStyleClass().add("badge-pill-accent");
                } else {
                    badge.getStyleClass().add("badge-pill-danger");
                }
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        colCambioClave.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().cambioRequerido() ? "REQUERIDO (1)" : "ACTUALIZADA"
        ));
        if (col2FA != null) {
            col2FA.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().totpHabilitado() ? "ACTIVO" : "INACTIVO"
            ));
        }
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().activo() ? "ACTIVO" : "INACTIVO"
        ));

        tblUsuarios.setItems(listaUsuarios);
        cargarUsuarios();
    }

    public void cargarUsuarios() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                List<UsuarioGestionService.UsuarioResumen> resumenes = usuarioGestionService.listarUsuarios();
                List<FilaUsuario> filas = new ArrayList<>();
                for (UsuarioGestionService.UsuarioResumen r : resumenes) {
                    filas.add(new FilaUsuario(
                        r.idUsuario(),
                        r.username(),
                        r.nombreCompleto(),
                        r.rol(),
                        r.cambioRequerido(),
                        r.activo(),
                        r.pinConfigurado(),
                        r.totpHabilitado()
                    ));
                }
                return filas;
            },
            listaUsuarios::setAll,
            e -> LOGGER.log(Level.FINE, "Error cargando usuarios", e)
        );
    }

    /**
     * Evalúa en modo fail-closed si el operador autenticado posee {@link Permiso#USUARIO_GESTIONAR}.
     */
    public static boolean tienePermisoGestionUsuarios() {
        return new UsuarioGestionService().tienePermisoGestionUsuarios();
    }

    private void exigirPermisoGestionUsuarios() throws NegocioException {
        usuarioGestionService.exigirPermisoGestionUsuarios();
    }

    /**
     * Resetea la contraseña de un usuario forzando {@code cambio_password_requerido = 1} (§2.4).
     */
    public void resetearPasswordForzandoCambio(int idUsuario, String passwordTemporal) throws Exception {
        usuarioGestionService.resetearPasswordForzandoCambio(idUsuario, passwordTemporal);
    }

    @FXML
    public void handleCrearUsuario(ActionEvent event) {
        if (!tienePermisoGestionUsuarios()) {
            AnimationUtils.mostrarToast(rootUsuariosStack,
                    "Acceso denegado: requiere permiso USUARIO_GESTIONAR.", true);
            return;
        }
        String user = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
        String nom = txtNombreCompleto.getText() != null ? txtNombreCompleto.getText().trim() : user;
        String rol = cmbRol.getValue() != null ? cmbRol.getValue() : "CAJERO";
        String pass = txtPasswordTemporal != null ? txtPasswordTemporal.getText() : null;
        if (user.isEmpty()) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Ingrese el username del usuario.", true);
            return;
        }
        if (pass == null || pass.isBlank() || !EncriptadorPassword.cumplePoliticaComplejidad(pass)) {
            AnimationUtils.mostrarToast(rootUsuariosStack,
                    "Ingrese una contraseña temporal válida (mínimo 8 caracteres, mayúscula, minúscula y dígito).",
                    true);
            return;
        }
        int idRol = switch (rol) {
            case "ADMIN" -> 1;
            case "CAJERO" -> 2;
            case "MOZO" -> 3;
            case "COCINA" -> 4;
            default -> 5;
        };

        String dni = txtDni != null && txtDni.getText() != null ? txtDni.getText().trim() : null;
        double sueldo = 1200.0;
        if (txtSueldoBase != null && txtSueldoBase.getText() != null && !txtSueldoBase.getText().isBlank()) {
            try {
                sueldo = Double.parseDouble(txtSueldoBase.getText().trim());
            } catch (Exception ignored) {
                sueldo = 1200.0;
            }
        }
        String horario = txtHorarioLaboral != null && txtHorarioLaboral.getText() != null && !txtHorarioLaboral.getText().isBlank()
            ? txtHorarioLaboral.getText().trim() : "08:00-16:00";
        boolean activoSel = chkUsuarioActivo.isSelected();
        final double sueldoFinal = sueldo;

        if (btnCrearUsuario != null) {
            btnCrearUsuario.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                usuarioGestionService.crearOActualizarUsuario(idRol, user, nom, rol, pass,
                        activoSel, dni, sueldoFinal, horario);
            },
            () -> {
                if (btnCrearUsuario != null) {
                    btnCrearUsuario.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootUsuariosStack, "Usuario '" + user + "' guardado con cambio de clave obligatorio.", false);
                cargarUsuarios();
            },
            e -> {
                if (btnCrearUsuario != null) {
                    btnCrearUsuario.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootUsuariosStack, "Error: " + (e != null ? e.getMessage() : "desconocido"), true);
            }
        );
    }

    @FXML
    public void handleResetearPassword(ActionEvent event) {
        if (!tienePermisoGestionUsuarios()) {
            AnimationUtils.mostrarToast(rootUsuariosStack,
                    "Acceso denegado: requiere permiso USUARIO_GESTIONAR.", true);
            return;
        }
        FilaUsuario sel = tblUsuarios.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Seleccione un usuario de la tabla.", true);
            return;
        }
        String temp = txtPasswordTemporal != null ? txtPasswordTemporal.getText() : null;
        if (temp == null || temp.isBlank() || !EncriptadorPassword.cumplePoliticaComplejidad(temp)) {
            AnimationUtils.mostrarToast(rootUsuariosStack,
                    "Ingrese una contraseña temporal válida (mínimo 8 caracteres, mayúscula, minúscula y dígito).",
                    true);
            return;
        }
        if (btnResetearPassword != null) {
            btnResetearPassword.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> resetearPasswordForzandoCambio(sel.idUsuario(), temp),
            () -> {
                if (btnResetearPassword != null) {
                    btnResetearPassword.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootUsuariosStack, "Contraseña reseteada para " + sel.username() + " (cambio_password_requerido = 1).", false);
                cargarUsuarios();
            },
            e -> {
                if (btnResetearPassword != null) {
                    btnResetearPassword.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootUsuariosStack, "Error al resetear clave: " + (e != null ? e.getMessage() : "desconocido"), true);
            }
        );
    }

    @FXML
    public void handleConfigurarPin(ActionEvent event) {
        if (!tienePermisoGestionUsuarios()) {
            AnimationUtils.mostrarToast(rootUsuariosStack,
                    "Acceso denegado: requiere permiso USUARIO_GESTIONAR.", true);
            return;
        }
        FilaUsuario sel = tblUsuarios.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Seleccione un usuario para configurar su PIN.", true);
            return;
        }

        Optional<String> res = DialogService.crearTextInputDialog(
                "",
                "Configurar PIN Tablet",
                "Configurar PIN de 4 a 6 dígitos para: " + sel.username(),
                "Nuevo PIN numérico:").showAndWait();
        if (res.isPresent()) {
            String pin = res.get().trim();
            if (!pin.matches("^\\d{4,6}$")) {
                AnimationUtils.mostrarToast(rootUsuariosStack, "El PIN debe tener entre 4 y 6 dígitos numéricos.", true);
                return;
            }
            if (btnConfigurarPin != null) {
                btnConfigurarPin.setDisable(true);
            }
            UiAsyncExecutor.ejecutarVoidAsync(
                () -> actualizarPinUsuario(sel.idUsuario(), pin),
                () -> {
                    if (btnConfigurarPin != null) {
                        btnConfigurarPin.setDisable(false);
                    }
                    AnimationUtils.mostrarToast(rootUsuariosStack, "PIN actualizado exitosamente para " + sel.username() + ".", false);
                    cargarUsuarios();
                },
                e -> {
                    if (btnConfigurarPin != null) {
                        btnConfigurarPin.setDisable(false);
                    }
                    LOGGER.log(Level.WARNING, "Error al configurar PIN", e);
                    AnimationUtils.mostrarToast(rootUsuariosStack, "Error actualizando PIN: " + (e != null ? e.getMessage() : "desconocido"), true);
                }
            );
        }
    }

    public void actualizarPinUsuario(int idUsuario, String pin) throws Exception {
        exigirPermisoGestionUsuarios();
        usuarioGestionService.actualizarPinUsuario(idUsuario, pin);
    }

    /**
     * Abre el modal QR TOTP para configurar 2FA del usuario seleccionado (priorizando rol ADMIN).
     */
    @FXML
    public void handleConfigurar2FA(ActionEvent event) {
        if (!tienePermisoGestionUsuarios()) {
            AnimationUtils.mostrarToast(rootUsuariosStack,
                    "Acceso denegado: requiere permiso USUARIO_GESTIONAR.", true);
            return;
        }
        FilaUsuario sel = resolverUsuarioSeleccionadoOAdmin();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Seleccione un usuario administrador para configurar 2FA.", true);
            return;
        }
        abrirModal2FAParaUsuario(sel);
    }

    /**
     * Abre el modal 2FA generando secreto Base32 y QR scannable para pruebas o uso directo.
     *
     * @param usuario Usuario objetivo a configurar.
     */
    public void abrirModal2FAParaUsuario(FilaUsuario usuario) {
        if (usuario == null) {
            return;
        }
        this.usuario2faEnEdicion = usuario;
        this.secretoTemporal2fa = totpService.generarSecretoBase32();
        if (lblModal2faUsuario != null) {
            lblModal2faUsuario.setText("Usuario: " + usuario.username() + " (" + usuario.rol()
                    + "). Escanee el QR con su app autenticadora e ingrese el código.");
        }
        if (lblSecreto2fa != null) {
            lblSecreto2fa.setText(secretoTemporal2fa);
        }
        if (txtCodigoVerificacion2fa != null) {
            txtCodigoVerificacion2fa.clear();
        }
        ocultarError2FA();
        try {
            if (imgQr2fa != null) {
                imgQr2fa.setImage(totpService.generarQrImageFx(usuario.username(), secretoTemporal2fa));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo generar QR 2FA", ex);
            mostrarError2FA("Error generando QR: " + ex.getMessage());
        }
        if (modalConfigurar2FA != null) {
            modalConfigurar2FA.setVisible(true);
            modalConfigurar2FA.setManaged(true);
        }
    }

    /**
     * Valida el código TOTP ingresado contra el secreto generado y habilita 2FA en BD.
     */
    @FXML
    public void handleConfirmarActivar2FA(ActionEvent event) {
        if (usuario2faEnEdicion == null || secretoTemporal2fa == null) {
            return;
        }
        if (!tienePermisoGestionUsuarios()) {
            mostrarError2FA("Acceso denegado: requiere permiso USUARIO_GESTIONAR.");
            return;
        }
        String codigo = txtCodigoVerificacion2fa != null ? txtCodigoVerificacion2fa.getText() : "";
        if (!totpService.verificarCodigo(secretoTemporal2fa, codigo)) {
            mostrarError2FA("Código TOTP inválido. Verifique los 6 dígitos de su aplicación.");
            return;
        }
        ocultarError2FA();
        if (btnConfirmar2FA != null) {
            btnConfirmar2FA.setDisable(true);
        }
        final int idObjetivo = usuario2faEnEdicion.idUsuario();
        final String usernameObj = usuario2faEnEdicion.username();
        final String secretoFinal = secretoTemporal2fa;
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                int actor = AuthService.getUsuarioSesionActual() != null
                        ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
                usuarioGestionService.actualizarConfiguracion2FA(idObjetivo, usernameObj,
                        secretoFinal, true, actor);
            },
            () -> {
                if (btnConfirmar2FA != null) {
                    btnConfirmar2FA.setDisable(false);
                }
                handleCerrarModal2FA(null);
                AnimationUtils.mostrarToast(rootUsuariosStack, "2FA TOTP activado para " + usernameObj + ".", false);
                cargarUsuarios();
            },
            ex -> {
                if (btnConfirmar2FA != null) {
                    btnConfirmar2FA.setDisable(false);
                }
                mostrarError2FA("Error guardando 2FA en BD: " + (ex != null ? ex.getMessage() : "desconocido"));
            }
        );
    }

    /**
     * Desactiva la verificación 2FA del usuario seleccionado.
     */
    @FXML
    public void handleDesactivar2FA(ActionEvent event) {
        if (usuario2faEnEdicion == null) {
            return;
        }
        if (!tienePermisoGestionUsuarios()) {
            mostrarError2FA("Acceso denegado: requiere permiso USUARIO_GESTIONAR.");
            return;
        }
        final int idObjetivo = usuario2faEnEdicion.idUsuario();
        final String usernameObj = usuario2faEnEdicion.username();
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                int actor = AuthService.getUsuarioSesionActual() != null
                        ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
                usuarioGestionService.actualizarConfiguracion2FA(idObjetivo, usernameObj,
                        null, false, actor);
            },
            () -> {
                handleCerrarModal2FA(null);
                AnimationUtils.mostrarToast(rootUsuariosStack, "2FA desactivado para " + usernameObj + ".", false);
                cargarUsuarios();
            },
            ex -> mostrarError2FA("Error desactivando 2FA: " + (ex != null ? ex.getMessage() : "desconocido"))
        );
    }

    @FXML
    public void handleCerrarModal2FA(ActionEvent event) {
        if (modalConfigurar2FA != null) {
            modalConfigurar2FA.setVisible(false);
            modalConfigurar2FA.setManaged(false);
        }
        ocultarError2FA();
        usuario2faEnEdicion = null;
        secretoTemporal2fa = null;
    }

    /**
     * Invalida remotamente todos los JWT activos del usuario seleccionado (ÍTEM 8 v5.1.0).
     */
    @FXML
    public void handleCerrarSesionRemota(ActionEvent event) {
        if (!tienePermisoGestionUsuarios()) {
            AnimationUtils.mostrarToast(rootUsuariosStack,
                    "Acceso denegado: requiere permiso USUARIO_GESTIONAR.", true);
            return;
        }
        FilaUsuario sel = tblUsuarios != null ? tblUsuarios.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Seleccione un usuario para cerrar sus sesiones remotas.", true);
            return;
        }
        if (btnCerrarSesionRemota != null) {
            btnCerrarSesionRemota.setDisable(true);
        }
        int adminId = AuthService.getUsuarioSesionActual() != null
                ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                exigirPermisoGestionUsuarios();
                tokenBlacklistService.forzarCierreSesionUsuario(
                        sel.idUsuario(), adminId, "Cierre remoto desde Administración de Usuarios");
            },
            () -> {
                if (btnCerrarSesionRemota != null) {
                    btnCerrarSesionRemota.setDisable(false);
                }
                AnimationUtils.mostrarToast(
                        rootUsuariosStack,
                        "Sesiones JWT cerradas remotamente para " + sel.username() + ".",
                        false);
            },
            ex -> {
                if (btnCerrarSesionRemota != null) {
                    btnCerrarSesionRemota.setDisable(false);
                }
                AnimationUtils.mostrarToast(
                        rootUsuariosStack,
                        "Error cerrando sesiones: " + (ex != null ? ex.getMessage() : "desconocido"),
                        true);
            }
        );
    }

    public String getSecretoTemporal2fa() {
        return secretoTemporal2fa;
    }

    public boolean isModal2faVisible() {
        return modalConfigurar2FA != null && modalConfigurar2FA.isVisible();
    }

    private FilaUsuario resolverUsuarioSeleccionadoOAdmin() {
        FilaUsuario sel = tblUsuarios != null ? tblUsuarios.getSelectionModel().getSelectedItem() : null;
        if (sel != null) {
            return sel;
        }
        return listaUsuarios.stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.rol()) || "admin".equalsIgnoreCase(u.username()))
                .findFirst()
                .orElse(null);
    }

    private void mostrarError2FA(String mensaje) {
        if (lblError2fa != null) {
            lblError2fa.setText(mensaje);
            lblError2fa.setVisible(true);
            lblError2fa.setManaged(true);
        }
    }

    private void ocultarError2FA() {
        if (lblError2fa != null) {
            lblError2fa.setText("");
            lblError2fa.setVisible(false);
            lblError2fa.setManaged(false);
        }
    }
}
