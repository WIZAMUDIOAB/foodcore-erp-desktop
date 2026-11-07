package com.foodcore.dao.impl;

import com.foodcore.dao.RolDAO;
import com.foodcore.model.Rol;
import com.foodcore.model.enums.Permiso;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementación JDBC de RolDAO para persistencia y consulta dinámica de roles y matriz de permisos (§2.1).
 */
public class RolDAOImpl implements RolDAO {

    @Override
    public List<Rol> listarTodos(Connection conn) throws SQLException {
        List<Rol> lista = new ArrayList<>();
        String sql = "SELECT id_rol, codigo_rol, nombre, descripcion, estado FROM rol ORDER BY id_rol ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Rol r = new Rol(
                    rs.getInt("id_rol"),
                    rs.getString("nombre"),
                    rs.getString("descripcion"),
                    rs.getBoolean("estado")
                );
                // codigo_rol guardado en codigo
                r.setCodigo(rs.getString("codigo_rol"));
                lista.add(r);
            }
        }
        return lista;
    }

    @Override
    public int crearRol(Connection conn, String codigoRol, String nombre, String descripcion) throws SQLException {
        String sql = "INSERT INTO rol (codigo_rol, nombre, descripcion, estado) VALUES (?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigoRol.trim().toUpperCase());
            ps.setString(2, nombre.trim());
            ps.setString(3, descripcion != null ? descripcion.trim() : "");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID del rol registrado.");
    }

    @Override
    public void actualizarPermisosRol(Connection conn, String codigoRol, Set<Permiso> permisos) throws SQLException {
        String normalizado = codigoRol.trim().toUpperCase();
        // 1. Limpiar permisos existentes del rol
        String sqlDelete = "DELETE FROM rol_permiso WHERE codigo_rol = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlDelete)) {
            ps.setString(1, normalizado);
            ps.executeUpdate();
        }

        // 2. Insertar nuevos permisos
        if (permisos != null && !permisos.isEmpty()) {
            String sqlInsert = "INSERT INTO rol_permiso (codigo_rol, codigo_permiso) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                for (Permiso p : permisos) {
                    ps.setString(1, normalizado);
                    ps.setString(2, p.name());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Override
    public Set<Permiso> obtenerPermisosPorRol(Connection conn, String codigoRol) throws SQLException {
        Set<Permiso> permisos = new HashSet<>();
        if (codigoRol == null || codigoRol.isBlank()) {
            return permisos;
        }
        String sql = "SELECT codigo_permiso FROM rol_permiso WHERE UPPER(codigo_rol) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigoRol.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cod = rs.getString("codigo_permiso");
                    try {
                        permisos.add(Permiso.valueOf(cod));
                    } catch (IllegalArgumentException ignored) {
                        // Permiso no mapeado en enum actual
                    }
                }
            }
        }
        return permisos.isEmpty() ? EnumSet.noneOf(Permiso.class) : EnumSet.copyOf(permisos);
    }

    @Override
    public void cambiarEstadoRol(Connection conn, int idRol, boolean activo) throws SQLException {
        String sql = "UPDATE rol SET estado = ? WHERE id_rol = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, activo ? 1 : 0);
            ps.setInt(2, idRol);
            ps.executeUpdate();
        }
    }
}
