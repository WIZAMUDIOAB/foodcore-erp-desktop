package com.foodcore.service;

import com.foodcore.dao.RolDAO;
import com.foodcore.dao.impl.RolDAOImpl;
import com.foodcore.model.Rol;
import com.foodcore.model.enums.Permiso;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RolGestionService {

    private final RolDAO rolDAO;

    public RolGestionService() {
        this(new RolDAOImpl());
    }

    public RolGestionService(RolDAO rolDAO) {
        this.rolDAO = rolDAO;
    }

    public List<Rol> listarTodos() {
        try (Connection conn = ConexionBD.getConnection()) {
            return conn != null ? rolDAO.listarTodos(conn) : Collections.emptyList();
        } catch (SQLException | RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    public Set<Permiso> obtenerPermisosPorRol(String codigoRol) {
        try (Connection conn = ConexionBD.getConnection()) {
            return conn != null ? rolDAO.obtenerPermisosPorRol(conn, codigoRol) : Collections.emptySet();
        } catch (SQLException | RuntimeException ignored) {
            return Collections.emptySet();
        }
    }

    public void actualizarPermisosRol(String codigoRol, Set<Permiso> permisos) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("No se pudo establecer conexión con la base de datos.");
            }
            rolDAO.actualizarPermisosRol(conn, codigoRol, permisos);
        }
    }

    public void crearRol(String codigo, String nombre, String descripcion) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("No se pudo establecer conexión con la base de datos.");
            }
            rolDAO.crearRol(conn, codigo, nombre, descripcion);
        }
    }
}
