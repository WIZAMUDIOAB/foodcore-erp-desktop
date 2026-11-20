package com.foodcore.service;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de negocio para Administración de Usuarios (SPEC-FOODCORE-v1.2 §2.4):
 * listado, alta/actualización, reseteo de contraseña forzando
 * {@code cambio_password_requerido = 1}, configuración de PIN de tablet (solo hash BCrypt)
 * y activación/desactivación de 2FA TOTP con auditoría inmutable.
 *
 * <p>Toda operación exige el permiso {@link Permiso#USUARIO_GESTIONAR} en modo fail-closed
 * mediante {@link #exigirPermisoGestionUsuarios()}.</p>
 */
public class UsuarioGestionService {

    private static final Logger LOGGER = Logger.getLogger(UsuarioGestionService.class.getName());

    /**
     * Resumen plano de un usuario para la tabla de administración.
     */
    public record UsuarioResumen(int idUsuario, String username, String nombreCompleto, String rol,
            boolean cambioRequerido, boolean activo, boolean pinConfigurado, boolean totpHabilitado) {
    }

    private final EncriptadorPassword encriptador = new EncriptadorPassword();
    private final UsuarioDAO usuarioDAO = new com.foodcore.dao.impl.UsuarioDAOImpl();
    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();

    private static final String SQL_LISTAR_USUARIOS =
            "SELECT id_usuario, username, COALESCE(nombre_completo, username) AS nom, codigo_rol, "
            + "cambio_password_requerido, activo, pin_hash, COALESCE(totp_habilitado, 0) AS totp_habilitado "
            + "FROM usuario ORDER BY id_usuario ASC";

    /**
     * Evalúa en modo fail-closed si el operador autenticado posee {@link Permiso#USUARIO_GESTIONAR}.
     */
    public boolean tienePermisoGestionUsuarios() {
        SesionUsuario sesion = SesionManager.getSesionActiva();
        Usuario actual = AuthService.getUsuarioSesionActual();
        if (actual != null) {
            if (sesion != null && sesion.getIdUsuario() == actual.getIdUsuario()
                    && actual.getNombreRol() != null
                    && actual.getNombreRol().equalsIgnoreCase(sesion.getCodigoRol())) {
                return sesion.tienePermiso(Permiso.USUARIO_GESTIONAR);
            }
            if (actual.getNombreRol() == null || actual.getNombreRol().isBlank()) {
                return false;
            }
            return AutorizacionService.permisosPorDefectoParaRol(actual.getNombreRol())
                    .contains(Permiso.USUARIO_GESTIONAR);
        }
        return sesion != null && sesion.tienePermiso(Permiso.USUARIO_GESTIONAR);
    }

    private void exigirPermisoGestionUsuarios() throws NegocioException {
        if (!tienePermisoGestionUsuarios()) {
            throw new NegocioException(
                    CodigoError.ACCESO_DENEGADO,
                    "Acceso denegado: Se requiere el permiso USUARIO_GESTIONAR para administrar usuarios.");
        }
    }

    private static void validarPasswordTemporal(String passwordTemporal) throws NegocioException {
        if (passwordTemporal == null || passwordTemporal.isBlank()
                || !EncriptadorPassword.cumplePoliticaComplejidad(passwordTemporal)) {
            throw new NegocioException(
                    CodigoError.VALIDACION_ENTRADA,
                    "Debe ingresar una contraseña temporal válida (mínimo 8 caracteres, mayúscula, minúscula y dígito).");
        }
    }

    /**
     * Lista todos los usuarios con su estado de cambio de clave, PIN y 2FA.
     */
    public List<UsuarioResumen> listarUsuarios() {
        List<UsuarioResumen> filas = new ArrayList<>();
        String sql = SQL_LISTAR_USUARIOS;
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String pinHash = rs.getString("pin_hash");
                filas.add(new UsuarioResumen(
                        rs.getInt("id_usuario"),
                        rs.getString("username"),
                        rs.getString("nom"),
                        rs.getString("codigo_rol"),
                        rs.getBoolean("cambio_password_requerido"),
                        rs.getBoolean("activo"),
                        pinHash != null && !pinHash.isBlank(),
                        rs.getBoolean("totp_habilitado")
                ));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Error cargando usuarios", e);
        }
        return filas;
    }

    /**
     * Resetea la contraseña de un usuario forzando {@code cambio_password_requerido = 1} (§2.4).
     */
    public void resetearPasswordForzandoCambio(int idUsuario, String passwordTemporal)
            throws NegocioException, SQLException {
        exigirPermisoGestionUsuarios();
        validarPasswordTemporal(passwordTemporal);
        String hash = encriptador.hashear(passwordTemporal);
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

    /**
     * Crea o actualiza un usuario con contraseña temporal y {@code cambio_password_requerido = 1}.
     * El PIN nunca se persiste en texto plano: la columna {@code pin} queda en NULL.
     */
    public void crearOActualizarUsuario(int idRol, String user, String nombreCompleto, String rol,
            String passwordTemporal, boolean activo, String dni, double sueldo, String horario)
            throws NegocioException, SQLException {
        exigirPermisoGestionUsuarios();
        validarPasswordTemporal(passwordTemporal);
        String hash = encriptador.hashear(passwordTemporal);
        String sql = "INSERT INTO usuario (id_empleado, id_rol, username, nombre_usuario, nombre_completo, "
            + "password_hash, hash_password, codigo_rol, activo, cambio_password_requerido, dni, pin, pin_hash, sueldo_base, horario_laboral) "
            + "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, NULL, NULL, ?, ?) "
            + "ON DUPLICATE KEY UPDATE nombre_completo = VALUES(nombre_completo), codigo_rol = VALUES(codigo_rol), activo = VALUES(activo), "
            + "dni = VALUES(dni), sueldo_base = VALUES(sueldo_base), horario_laboral = VALUES(horario_laboral)";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idRol);
            ps.setString(2, user);
            ps.setString(3, user);
            ps.setString(4, nombreCompleto);
            ps.setString(5, hash);
            ps.setString(6, hash);
            ps.setString(7, rol);
            ps.setBoolean(8, activo);
            ps.setString(9, dni);
            ps.setBigDecimal(10, BigDecimal.valueOf(sueldo));
            ps.setString(11, horario);
            ps.executeUpdate();
        }
    }

    /**
     * Actualiza el PIN de tablet almacenando únicamente su hash BCrypt y limpiando la columna
     * {@code pin} en texto plano.
     */
    public void actualizarPinUsuario(int idUsuario, String pin) throws NegocioException, SQLException {
        exigirPermisoGestionUsuarios();
        if (pin == null || !pin.trim().matches("^\\d{4,6}$")) {
            throw new NegocioException(
                    CodigoError.VALIDACION_ENTRADA,
                    "El PIN debe contener entre 4 y 6 dígitos numéricos.");
        }
        String hash = EncriptadorPassword.generarHash(pin.trim());
        String sql = "UPDATE usuario SET pin = NULL, pin_hash = ? WHERE id_usuario = ?";
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
    }

    /**
     * Habilita o deshabilita 2FA TOTP de un usuario y registra el evento en la bitácora inmutable.
     */
    public void actualizarConfiguracion2FA(int idUsuario, String username, String secretoBase32,
            boolean habilitar, int actorId) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            usuarioDAO.actualizarConfiguracion2FA(conn, idUsuario, secretoBase32, habilitar);
            auditoriaDAO.registrarEventoSeguro(
                    conn, actorId, habilitar ? "ACTIVAR_2FA" : "DESACTIVAR_2FA", "USUARIO", null,
                    "2FA TOTP " + (habilitar ? "activado" : "desactivado") + " para usuario " + username);
        }
    }
}
