package com.foodcore.dao;

import com.foodcore.model.Asistencia;
import com.foodcore.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UsuarioDAOImpl implements UsuarioDAO {

    @Override
    public Optional<Usuario> autenticar(Connection conn, String username, String password) throws SQLException {
        String sql = "SELECT u.*, r.nombre AS rol_nombre, CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre " +
                     "FROM usuario u " +
                     "JOIN rol r ON u.id_rol = r.id_rol " +
                     "JOIN empleado e ON u.id_empleado = e.id_empleado " +
                     "WHERE u.username = ? AND u.password_hash = ? AND u.activo = TRUE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u = new Usuario();
                    u.setIdUsuario(rs.getInt("id_usuario"));
                    u.setIdEmpleado(rs.getInt("id_empleado"));
                    u.setIdRol(rs.getInt("id_rol"));
                    u.setUsername(rs.getString("username"));
                    u.setActivo(rs.getBoolean("activo"));
                    u.setNombreRol(rs.getString("rol_nombre"));
                    u.setNombreEmpleado(rs.getString("empleado_nombre"));
                    return Optional.of(u);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Integer> buscarEmpleadoPorDni(Connection conn, String dni) throws SQLException {
        String sql = "SELECT id_empleado FROM empleado WHERE dni = ? AND estado = TRUE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dni);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("id_empleado"));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void registrarMarcacionAsistencia(Connection conn, Asistencia asistencia) throws SQLException {
        String sql = "INSERT INTO asistencia (id_empleado, fecha, hora_ingreso, hora_salida, estado) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, asistencia.getIdEmpleado());
            ps.setDate(2, java.sql.Date.valueOf(asistencia.getFecha()));
            ps.setTime(3, java.sql.Time.valueOf(asistencia.getHoraIngreso()));
            if (asistencia.getHoraSalida() != null) {
                ps.setTime(4, java.sql.Time.valueOf(asistencia.getHoraSalida()));
            } else {
                ps.setNull(4, java.sql.Types.TIME);
            }
            ps.setString(5, asistencia.getEstado());
            ps.executeUpdate();
        }
    }
}
