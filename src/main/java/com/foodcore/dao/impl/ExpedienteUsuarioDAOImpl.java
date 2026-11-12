package com.foodcore.dao.impl;

import com.foodcore.dao.ExpedienteUsuarioDAO;
import com.foodcore.model.Usuario;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC de {@link ExpedienteUsuarioDAO}.
 */
public class ExpedienteUsuarioDAOImpl implements ExpedienteUsuarioDAO {

    private static final String SELECT_BASE =
            "SELECT u.id_usuario, u.id_empleado, u.id_rol, u.username, u.activo, "
                    + "u.dni, u.tipo_documento, u.direccion, u.telefono, u.telefono_emergencia, "
                    + "u.contacto_emergencia, u.fecha_nacimiento, u.cargo, u.tipo_contrato, "
                    + "u.fecha_ingreso, u.sueldo_base, u.horario_laboral, "
                    + "u.hora_entrada_programada, u.hora_salida_programada, "
                    + "u.fecha_vencimiento_contrato, u.fecha_vencimiento_carnet_sanidad, "
                    + "r.nombre AS rol_nombre, "
                    + "COALESCE(NULLIF(TRIM(u.nombre_completo), ''), "
                    + "NULLIF(TRIM(CONCAT(COALESCE(e.nombres, ''), ' ', COALESCE(e.apellidos, ''))), ''), "
                    + "u.username) AS empleado_nombre "
                    + "FROM usuario u "
                    + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                    + "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado ";

    @Override
    public List<Usuario> listarTodos(Connection conn, boolean soloActivos) throws SQLException {
        String sql = SELECT_BASE
                + (soloActivos ? "WHERE u.activo = 1 " : "")
                + "ORDER BY empleado_nombre ASC, u.id_usuario ASC";
        List<Usuario> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public Optional<Usuario> buscarPorId(Connection conn, int idUsuario) throws SQLException {
        String sql = SELECT_BASE + "WHERE u.id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean actualizarDatosPersonales(Connection conn, Usuario usuario) throws SQLException {
        String sql = "UPDATE usuario SET "
                + "tipo_documento = ?, "
                + "dni = ?, "
                + "nombre_completo = COALESCE(NULLIF(?, ''), nombre_completo), "
                + "direccion = ?, "
                + "telefono = ?, "
                + "telefono_emergencia = ?, "
                + "contacto_emergencia = ?, "
                + "fecha_nacimiento = ? "
                + "WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario.getTipoDocumento());
            ps.setString(2, usuario.getDni());
            ps.setString(3, usuario.getNombreEmpleado());
            ps.setString(4, usuario.getDireccion());
            ps.setString(5, usuario.getTelefono());
            ps.setString(6, usuario.getTelefonoEmergencia());
            ps.setString(7, usuario.getContactoEmergencia());
            if (usuario.getFechaNacimiento() != null) {
                ps.setDate(8, Date.valueOf(usuario.getFechaNacimiento()));
            } else {
                ps.setNull(8, Types.DATE);
            }
            ps.setInt(9, usuario.getIdUsuario());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean actualizarDatosLaborales(Connection conn, Usuario usuario) throws SQLException {
        String sql = "UPDATE usuario SET "
                + "cargo = ?, "
                + "tipo_contrato = ?, "
                + "fecha_ingreso = ?, "
                + "sueldo_base = ?, "
                + "horario_laboral = ?, "
                + "hora_entrada_programada = ?, "
                + "hora_salida_programada = ?, "
                + "fecha_vencimiento_contrato = ?, "
                + "fecha_vencimiento_carnet_sanidad = ? "
                + "WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario.getCargo());
            ps.setString(2, usuario.getTipoContrato());
            if (usuario.getFechaIngreso() != null) {
                ps.setDate(3, Date.valueOf(usuario.getFechaIngreso()));
            } else {
                ps.setNull(3, Types.DATE);
            }
            ps.setDouble(4, usuario.getSueldoBase());
            ps.setString(5, usuario.getHorarioLaboral());
            if (usuario.getHoraEntradaProgramada() != null) {
                ps.setTime(6, Time.valueOf(usuario.getHoraEntradaProgramada()));
            } else {
                ps.setNull(6, Types.TIME);
            }
            if (usuario.getHoraSalidaProgramada() != null) {
                ps.setTime(7, Time.valueOf(usuario.getHoraSalidaProgramada()));
            } else {
                ps.setNull(7, Types.TIME);
            }
            if (usuario.getFechaVencimientoContrato() != null) {
                ps.setDate(8, Date.valueOf(usuario.getFechaVencimientoContrato()));
            } else {
                ps.setNull(8, Types.DATE);
            }
            if (usuario.getFechaVencimientoCarnetSanidad() != null) {
                ps.setDate(9, Date.valueOf(usuario.getFechaVencimientoCarnetSanidad()));
            } else {
                ps.setNull(9, Types.DATE);
            }
            ps.setInt(10, usuario.getIdUsuario());
            return ps.executeUpdate() > 0;
        }
    }

    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setIdUsuario(rs.getInt("id_usuario"));
        u.setIdEmpleado(rs.getInt("id_empleado"));
        u.setIdRol(rs.getInt("id_rol"));
        u.setUsername(rs.getString("username"));
        u.setActivo(rs.getBoolean("activo"));
        u.setNombreRol(rs.getString("rol_nombre"));
        u.setNombreEmpleado(rs.getString("empleado_nombre"));

        u.setTipoDocumento(rs.getString("tipo_documento"));
        u.setDni(rs.getString("dni"));
        u.setDireccion(rs.getString("direccion"));
        u.setTelefono(rs.getString("telefono"));
        u.setTelefonoEmergencia(rs.getString("telefono_emergencia"));
        u.setContactoEmergencia(rs.getString("contacto_emergencia"));
        Date fNac = rs.getDate("fecha_nacimiento");
        if (fNac != null) {
            u.setFechaNacimiento(fNac.toLocalDate());
        }

        u.setCargo(rs.getString("cargo"));
        u.setTipoContrato(rs.getString("tipo_contrato"));
        Date fIng = rs.getDate("fecha_ingreso");
        if (fIng != null) {
            u.setFechaIngreso(fIng.toLocalDate());
        }
        u.setSueldoBase(rs.getDouble("sueldo_base"));
        u.setHorarioLaboral(rs.getString("horario_laboral"));
        Time tEnt = rs.getTime("hora_entrada_programada");
        if (tEnt != null) {
            u.setHoraEntradaProgramada(tEnt.toLocalTime());
        }
        Time tSal = rs.getTime("hora_salida_programada");
        if (tSal != null) {
            u.setHoraSalidaProgramada(tSal.toLocalTime());
        }
        Date fCont = rs.getDate("fecha_vencimiento_contrato");
        if (fCont != null) {
            u.setFechaVencimientoContrato(fCont.toLocalDate());
        }
        Date fCarn = rs.getDate("fecha_vencimiento_carnet_sanidad");
        if (fCarn != null) {
            u.setFechaVencimientoCarnetSanidad(fCarn.toLocalDate());
        }
        return u;
    }
}
