package com.foodcore.dao;

import com.foodcore.model.Asistencia;
import com.foodcore.model.Empleado;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuarioDAOImpl implements UsuarioDAO {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private final EncriptadorPassword encriptador = new EncriptadorPassword();

    @Override
    public Optional<Usuario> autenticar(Connection conn, String username, String password) throws SQLException {
        Optional<Usuario> userOpt = buscarPorUsername(conn, username);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        Usuario u = userOpt.get();
        if (!u.isActivo()) {
            return Optional.empty();
        }
        String hash = u.getPasswordHash();
        boolean coincide = (hash != null && hash.startsWith("$2"))
            ? encriptador.verificar(password, hash)
            : password.equals(hash);
        return coincide ? Optional.of(u) : Optional.empty();
    }

    @Override
    public Optional<Usuario> buscarPorUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT u.*, r.nombre AS rol_nombre, CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre "
            + "FROM usuario u "
            + "JOIN rol r ON u.id_rol = r.id_rol "
            + "JOIN empleado e ON u.id_empleado = e.id_empleado "
            + "WHERE u.username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u = new Usuario();
                    u.setIdUsuario(rs.getInt("id_usuario"));
                    u.setIdEmpleado(rs.getInt("id_empleado"));
                    u.setIdRol(rs.getInt("id_rol"));
                    u.setUsername(rs.getString("username"));
                    u.setPasswordHash(rs.getString("password_hash"));
                    u.setActivo(rs.getBoolean("activo"));
                    u.setNombreRol(rs.getString("rol_nombre"));
                    u.setNombreEmpleado(rs.getString("empleado_nombre"));
                    try {
                        u.setIntentosFallidos(rs.getInt("intentos_fallidos"));
                        u.setBloqueadoHasta(leerFechaLima(rs, "bloqueado_hasta"));
                        u.setCambioPasswordRequerido(rs.getBoolean("cambio_password_requerido"));
                        u.setUltimoLogin(leerFechaLima(rs, "ultimo_login"));
                        try {
                            u.setDni(rs.getString("dni"));
                            u.setPin(rs.getString("pin"));
                            u.setPinHash(rs.getString("pin_hash"));
                        } catch (SQLException ignored) {}
                    } catch (SQLException ignoredCol) {
                        u.setIntentosFallidos(0);
                    }
                    return Optional.of(u);
                }
            }
        }
        return Optional.empty();
    }

    private static LocalDateTime leerFechaLima(ResultSet rs, String columna) throws SQLException {
        Timestamp ts = rs.getTimestamp(columna);
        return ts != null ? ts.toInstant().atZone(ZONA_LIMA).toLocalDateTime() : null;
    }

    @Override
    public void registrarIntentoFallido(Connection conn, int idUsuario, int nuevosIntentos, boolean bloquear15Minutos) throws SQLException {
        if (bloquear15Minutos) {
            String sql = "UPDATE usuario SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id_usuario = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, nuevosIntentos);
                ps.setObject(2, LocalDateTime.now(ZONA_LIMA).plusMinutes(15));
                ps.setInt(3, idUsuario);
                ps.executeUpdate();
            }
            return;
        }
        String sql = "UPDATE usuario SET intentos_fallidos = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nuevosIntentos);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
    }

    @Override
    public void resetearIntentosLogin(Connection conn, int idUsuario) throws SQLException {
        String sql = "UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, ultimo_login = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, LocalDateTime.now(ZONA_LIMA));
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
    }

    @Override
    public void actualizarPasswordObligatorio(Connection conn, int idUsuario, String nuevoPasswordHash) throws SQLException {
        String sql = "UPDATE usuario SET password_hash = ?, cambio_password_requerido = FALSE WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoPasswordHash);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
    }

    @Override
    public void actualizarPasswordHash(Connection conn, int idUsuario, String nuevoPasswordHash) throws SQLException {
        String sql = "UPDATE usuario SET password_hash = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoPasswordHash);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
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

    @Override
    public Optional<Usuario> buscarPorDniOPin(Connection conn, String credencial) throws SQLException {
        if (credencial == null || credencial.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT u.*, r.nombre AS rol_nombre, CONCAT(COALESCE(e.nombres, u.username), ' ', COALESCE(e.apellidos, '')) AS empleado_nombre "
                + "FROM usuario u "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado "
                + "WHERE u.activo = 1 AND (u.dni = ? OR u.pin = ? OR e.dni = ?) "
                + "LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = credencial.trim();
            ps.setString(1, q);
            ps.setString(2, q);
            ps.setString(3, q);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearUsuarioCompleto(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Usuario> listarTodosActivos(Connection conn) throws SQLException {
        String sql = "SELECT u.*, r.nombre AS rol_nombre, CONCAT(COALESCE(e.nombres, u.username), ' ', COALESCE(e.apellidos, '')) AS empleado_nombre "
                + "FROM usuario u "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado "
                + "WHERE u.activo = 1 "
                + "ORDER BY u.id_usuario ASC";

        List<Usuario> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearUsuarioCompleto(rs));
            }
        }
        return lista;
    }

    @Override
    public boolean actualizarDatosLaborales(Connection conn, int idUsuario, String dni, String pin,
                                            double sueldoBase, String horarioLaboral,
                                            LocalTime horaEntrada, LocalTime horaSalida) throws SQLException {
        String sql = "UPDATE usuario SET dni = ?, pin = ?, sueldo_base = ?, horario_laboral = ?, "
                + "hora_entrada_programada = ?, hora_salida_programada = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dni);
            ps.setString(2, pin);
            ps.setDouble(3, sueldoBase);
            ps.setString(4, horarioLaboral);
            ps.setTime(5, horaEntrada != null ? Time.valueOf(horaEntrada) : Time.valueOf("08:00:00"));
            ps.setTime(6, horaSalida != null ? Time.valueOf(horaSalida) : Time.valueOf("16:00:00"));
            ps.setInt(7, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean actualizarPin(Connection conn, int idUsuario, String pinPlano, String pinHash) throws SQLException {
        String sql = "UPDATE usuario SET pin = ?, pin_hash = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pinPlano);
            ps.setString(2, pinHash);
            ps.setInt(3, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<Usuario> autenticarPorPin(Connection conn, int idUsuario, String pinPlano) throws SQLException {
        String sql = "SELECT u.*, r.nombre AS rol_nombre, CONCAT(COALESCE(e.nombres, u.username), ' ', COALESCE(e.apellidos, '')) AS empleado_nombre "
                + "FROM usuario u "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado "
                + "WHERE u.id_usuario = ? AND u.activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u = mapearUsuarioCompleto(rs);
                    if (verificarPin(pinPlano, u)) {
                        return Optional.of(u);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Usuario> autenticarPorPinGeneral(Connection conn, String pinPlano) throws SQLException {
        List<Usuario> activos = listarTodosActivos(conn);
        for (Usuario u : activos) {
            if (verificarPin(pinPlano, u)) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    private boolean verificarPin(String pinPlano, Usuario u) {
        if (pinPlano == null || pinPlano.isBlank()) {
            return false;
        }
        String hash = u.getPinHash();
        if (hash != null && hash.startsWith("$2")) {
            if (encriptador.verificar(pinPlano, hash)) {
                return true;
            }
        }
        // Fallback a pin en texto plano si existe
        return pinPlano.equals(u.getPin());
    }

    private Usuario mapearUsuarioCompleto(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setIdUsuario(rs.getInt("id_usuario"));
        u.setIdEmpleado(rs.getInt("id_empleado"));
        u.setIdRol(rs.getInt("id_rol"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setActivo(rs.getBoolean("activo"));
        u.setNombreRol(rs.getString("rol_nombre"));
        u.setNombreEmpleado(rs.getString("empleado_nombre"));

        try {
            u.setDni(rs.getString("dni"));
            u.setPin(rs.getString("pin"));
            u.setPinHash(rs.getString("pin_hash"));
            u.setSueldoBase(rs.getDouble("sueldo_base"));
            u.setHorarioLaboral(rs.getString("horario_laboral"));
            Time te = rs.getTime("hora_entrada_programada");
            if (te != null) u.setHoraEntradaProgramada(te.toLocalTime());
            Time ts = rs.getTime("hora_salida_programada");
            if (ts != null) u.setHoraSalidaProgramada(ts.toLocalTime());
        } catch (SQLException ignored) {
        }
        return u;
    }
}
