package com.foodcore.controller;

import com.foodcore.security.EncriptadorPassword;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
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
import javafx.scene.layout.StackPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    public record FilaUsuario(int idUsuario, String username, String nombreCompleto, String rol, boolean cambioRequerido, boolean activo) {
    }

    @FXML private StackPane rootUsuariosStack;
    @FXML private TableView<FilaUsuario> tblUsuarios;
    @FXML private TableColumn<FilaUsuario, String> colUsername;
    @FXML private TableColumn<FilaUsuario, String> colNombre;
    @FXML private TableColumn<FilaUsuario, String> colRol;
    @FXML private TableColumn<FilaUsuario, String> colCambioClave;
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

    private final EncriptadorPassword encriptador = new EncriptadorPassword();
    private final ObservableList<FilaUsuario> listaUsuarios = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnCrearUsuario);
        AnimationUtils.attachTactileFeedback(btnResetearPassword);

        cmbRol.setItems(FXCollections.observableArrayList("ADMIN", "CAJERO", "MOZO", "COCINA", "ALMACEN"));
        cmbRol.getSelectionModel().select("CAJERO");

        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombreCompleto()));
        colRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().rol()));
        colCambioClave.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().cambioRequerido() ? "REQUERIDO (1)" : "ACTUALIZADA"
        ));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().activo() ? "ACTIVO" : "INACTIVO"
        ));

        tblUsuarios.setItems(listaUsuarios);
        cargarUsuarios();
    }

    public void cargarUsuarios() {
        new Thread(() -> {
            List<FilaUsuario> filas = new ArrayList<>();
            String sql = "SELECT id_usuario, username, COALESCE(nombre_completo, username) AS nom, codigo_rol, "
                + "cambio_password_requerido, activo FROM usuario ORDER BY id_usuario ASC";
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
                        rs.getBoolean("activo")
                    ));
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error cargando usuarios", e);
            }
            Platform.runLater(() -> listaUsuarios.setAll(filas));
        }).start();
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
        try {
            String user = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
            String nom = txtNombreCompleto.getText() != null ? txtNombreCompleto.getText().trim() : user;
            String rol = cmbRol.getValue() != null ? cmbRol.getValue() : "CAJERO";
            String pass = txtPasswordTemporal.getText() != null && !txtPasswordTemporal.getText().isBlank()
                ? txtPasswordTemporal.getText() : "Temporal#2026!";
            if (user.isEmpty()) {
                AnimationUtils.mostrarToast(rootUsuariosStack, "Ingrese el username del usuario.", true);
                return;
            }
            String hash = encriptador.hashear(pass);
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
                ps.setBoolean(8, chkUsuarioActivo.isSelected());
                ps.setString(9, dni);
                ps.setString(10, dni != null && dni.length() >= 4 ? dni.substring(dni.length() - 4) : "1234");
                ps.setBigDecimal(11, java.math.BigDecimal.valueOf(sueldo));
                ps.setString(12, horario);
                ps.executeUpdate();
            }
            AnimationUtils.mostrarToast(rootUsuariosStack, "Usuario '" + user + "' guardado con cambio de clave obligatorio.", false);
            cargarUsuarios();
        } catch (Exception e) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Error: " + e.getMessage(), true);
        }
    }

    @FXML
    public void handleResetearPassword(ActionEvent event) {
        FilaUsuario sel = tblUsuarios.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Seleccione un usuario de la tabla.", true);
            return;
        }
        try {
            String temp = txtPasswordTemporal.getText();
            resetearPasswordForzandoCambio(sel.idUsuario(), temp);
            AnimationUtils.mostrarToast(rootUsuariosStack, "Contraseña reseteada para " + sel.username() + " (cambio_password_requerido = 1).", false);
            cargarUsuarios();
        } catch (Exception e) {
            AnimationUtils.mostrarToast(rootUsuariosStack, "Error al resetear clave: " + e.getMessage(), true);
        }
    }
}
