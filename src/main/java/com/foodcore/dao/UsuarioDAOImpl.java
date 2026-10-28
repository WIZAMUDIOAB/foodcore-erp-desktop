package com.foodcore.dao;

import com.foodcore.model.Asistencia;
import com.foodcore.model.Empleado;
import com.foodcore.model.Usuario;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
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
    public Optional<Empleado> buscarEmpleadoPorDni(Connection conn, String dni) throws SQLException {
        String sql = "SELECT * FROM empleado WHERE dni = ? AND estado = TRUE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dni);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Empleado e = new Empleado(
                        rs.getInt("id_empleado"),
                        rs.getString("dni"),
                        rs.getString("nombres"),
                        rs.getString("apellidos"),
                        rs.getString("telefono"),
                        rs.getString("cargo"),
                        rs.getBigDecimal("sueldo_base"),
                        rs.getDate("fecha_ingreso").toLocalDate(),
                        rs.getBoolean("estado")
                    );
                    return Optional.of(e);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Asistencia> obtenerAsistenciaHoy(Connection conn, int idEmpleado) throws SQLException {
        String sql = "SELECT * FROM asistencia WHERE id_empleado = ? AND fecha = CURRENT_DATE() ORDER BY id_asistencia DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idEmpleado);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Asistencia a = new Asistencia();
                    a.setIdAsistencia(rs.getInt("id_asistencia"));
                    a.setIdEmpleado(rs.getInt("id_empleado"));
                    a.setFecha(rs.getDate("fecha").toLocalDate());
                    a.setHoraIngreso(rs.getTime("hora_ingreso").toLocalTime());
                    Time salida = rs.getTime("hora_salida");
                    if (salida != null) {
                        a.setHoraSalida(salida.toLocalTime());
                    }
                    a.setEstado(rs.getString("estado"));
                    return Optional.of(a);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void registrarIngresoAsistencia(Connection conn, Asistencia asistencia) throws SQLException {
        String sql = "INSERT INTO asistencia (id_empleado, fecha, hora_ingreso, estado) VALUES (?, CURRENT_DATE(), CURRENT_TIME(), ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, asistencia.getIdEmpleado());
            ps.setString(2, asistencia.getEstado() != null ? asistencia.getEstado() : "ASISTIO");
            ps.executeUpdate();
        }
    }

    @Override
    public void registrarSalidaAsistencia(Connection conn, int idAsistencia, LocalTime horaSalida) throws SQLException {
        String sql = "UPDATE asistencia SET hora_salida = ? WHERE id_asistencia = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTime(1, Time.valueOf(horaSalida));
            ps.setInt(2, idAsistencia);
            ps.executeUpdate();
        }
    }
}
