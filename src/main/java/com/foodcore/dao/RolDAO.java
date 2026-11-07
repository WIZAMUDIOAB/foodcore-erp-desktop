package com.foodcore.dao;

import com.foodcore.model.Rol;
import com.foodcore.model.enums.Permiso;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Acceso a datos para Roles y Permisos Dinámicos (§2.1 SPEC-FOODCORE-v1.3-ENTERPRISE).
 */
public interface RolDAO {

    List<Rol> listarTodos(Connection conn) throws SQLException;

    int crearRol(Connection conn, String codigoRol, String nombre, String descripcion) throws SQLException;

    void actualizarPermisosRol(Connection conn, String codigoRol, Set<Permiso> permisos) throws SQLException;

    Set<Permiso> obtenerPermisosPorRol(Connection conn, String codigoRol) throws SQLException;

    void cambiarEstadoRol(Connection conn, int idRol, boolean activo) throws SQLException;
}
