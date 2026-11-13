package com.foodcore.controller;

import com.foodcore.api.security.TokenBlacklistService;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.TotpService;
import com.foodcore.service.AuthService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.Optional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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

    private final EncriptadorPassword encriptador = new EncriptadorPassword();
    private final TotpService totpService = new TotpService();
    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();
    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();
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
                List<FilaUsuario> filas = new ArrayList<>();
                String sql = "SELECT id_usuario, username, COALESCE(nombre_completo, username) AS nom, codigo_rol, "
                    + "cambio_password_requerido, activo, pin, pin_hash, COALESCE(totp_habilitado, 0) AS totp_habilitado "
                    + "FROM usuario ORDER BY id_usuario ASC";
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        filas.add(new FilaUsuario(
                            rs.getInt("id_usuario"),
                            rs.getString("username"),
                            rs.getString("nom"),
                            rs.getString("codigo_rol"),
                            rs.getBoolean("cambio_password_requerido"),
                            rs.getBoolean("activo"),
                            (rs.getString("pin_hash") != null && !rs.getString("pin_hash").isBlank())
                                    || (rs.getString("pin") != null && !rs.getString("pin").isBlank()),
                            rs.getBoolean("totp_habilitado")
                        ));
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.FINE, "Error cargando usuarios", e);
                }
                return filas;
            },
            listaUsuarios::setAll,
            e -> LOGGER.log(Level.FINE, "Error cargando usuarios", e)
        );
    }

    /**
     * Resetea la contraseña de un usuario forzando {@code cambio_password_requerido = 1} (§2.4).
     */
    public void resetearPasswordForzandoCambio(int idUsuario, String passwordTemporal) throws Exception {
        String hash = encriptador.hashear(passwordTemporal != null && !passwordTemporal.isBlank() ? passwordTemporal : "Temporal#2026!");
        String sql = "UPDATE usuario SET password_hash = ?, hash_password = ?, cambio_password_requerido = 1, "
            + "intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = ?";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, hash);
            ps.setInt(3, idUsuario);
            ps.executeUpdate();
        }
    }

    @FXML
    public void handleCrearUsuario(ActionEvent event) {
        String user = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
        String nom = txtNombreCompleto.getText() != null ? txtNombreCompleto.getText().trim() : user;
        String rol = cmbRol.getValue() != null ? cmbRol.getValue() : "CAJERO";
        String pass = txtPasswordTemporal.getText() != null && !txtPasswordTemporal.getText().isBlank()
            ? txtPasswordTemporal.getText() : "Temporal#2026!";
        if (user.isEmpty()) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Ingrese el username del usuario.", true);
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
                String hash = encriptador.hashear(pass);
                String sql = "INSERT INTO usuario (id_empleado, id_rol, username, nombre_usuario, nombre_completo, "
                    + "password_hash, hash_password, codigo_rol, activo, cambio_password_requerido, dni, pin, sueldo_base, horario_laboral) "
                    + "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE nombre_completo = VALUES(nombre_completo), codigo_rol = VALUES(codigo_rol), activo = VALUES(activo), "
                    + "dni = VALUES(dni), sueldo_base = VALUES(sueldo_base), horario_laboral = VALUES(horario_laboral)";
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, idRol);
                    ps.setString(2, user);
                    ps.setString(3, user);
                    ps.setString(4, nom);
                    ps.setString(5, hash);
                    ps.setString(6, hash);
                    ps.setString(7, rol);
                    ps.setBoolean(8, activoSel);
                    ps.setString(9, dni);
                    ps.setString(10, dni != null && dni.length() >= 4 ? dni.substring(dni.length() - 4) : "1234");
                    ps.setBigDecimal(11, java.math.BigDecimal.valueOf(sueldoFinal));
                    ps.setString(12, horario);
                    ps.executeUpdate();
                }
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
        FilaUsuario sel = tblUsuarios.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Seleccione un usuario de la tabla.", true);
            return;
        }
        String temp = txtPasswordTemporal.getText();
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
        FilaUsuario sel = tblUsuarios.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Seleccione un usuario para configurar su PIN.", true);
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1234");
        dialog.setTitle("Configurar PIN Tablet");
        dialog.setHeaderText("Configurar PIN de 4 a 6 dígitos para: " + sel.username());
        dialog.setContentText("Nuevo PIN numérico:");

        Optional<String> res = dialog.showAndWait();
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
        String hash = EncriptadorPassword.generarHash(pin);
        String sql = "UPDATE usuario SET pin = ?, pin_hash = ? WHERE id_usuario = ?";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pin);
            ps.setString(2, hash);
            ps.setInt(3, idUsuario);
            ps.executeUpdate();
        }
    }

    /**
     * Abre el modal QR TOTP para configurar 2FA del usuario seleccionado (priorizando rol ADMIN).
     */
    @FXML
    public void handleConfigurar2FA(ActionEvent event) {
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
                try (Connection conn = ConexionBD.getConnection()) {
                    usuarioDAO.actualizarConfiguracion2FA(conn, idObjetivo, secretoFinal, true);
                    int actor = AuthService.getUsuarioSesionActual() != null
                            ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
                    auditoriaDAO.registrarEventoSeguro(
                            conn, actor, "ACTIVAR_2FA", "USUARIO", null,
                            "2FA TOTP activado para usuario " + usernameObj);
                }
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
        final int idObjetivo = usuario2faEnEdicion.idUsuario();
        final String usernameObj = usuario2faEnEdicion.username();
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    usuarioDAO.actualizarConfiguracion2FA(conn, idObjetivo, null, false);
                    int actor = AuthService.getUsuarioSesionActual() != null
                            ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
                    auditoriaDAO.registrarEventoSeguro(
                            conn, actor, "DESACTIVAR_2FA", "USUARIO", null,
                            "2FA TOTP desactivado para usuario " + usernameObj);
                }
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
            () -> tokenBlacklistService.forzarCierreSesionUsuario(
                    sel.idUsuario(), adminId, "Cierre remoto desde Administración de Usuarios"),
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
