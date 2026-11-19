package com.foodcore.dao;

import com.foodcore.model.Rol;
import com.foodcore.model.enums.Permiso;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Acceso a datos para Roles y Permisos Dinámicos (§2.1 SPEC-FOODCORE-v1.3-ENTERPRISE).
 */
public interface RolDAO {

    List<Rol> listarTodos(Connection conn) throws SQLException;

    int crearRol(Connection conn, String codigoRol, String nombre, String descripcion) throws SQLException;

    void actualizarPermisosRol(Connection conn, String codigoRol, Set<Permiso> permisos) throws SQLException;

    Set<Permiso> obtenerPermisosPorRol(Connection conn, String codigoRol) throws SQLException;

    /**
     * Consulta los permisos de un rol verificando primero su existencia y estado activo en la tabla {@code rol}.
     * <ul>
     *   <li>{@link Optional#empty()} si el código de rol no existe en la tabla {@code rol}.</li>
     *   <li>{@link Optional#of(Object)} con conjunto vacío si el rol existe pero está inactivo o tiene 0 permisos configurados.</li>
     *   <li>{@link Optional#of(Object)} con los permisos activos si el rol está vigente.</li>
     * </ul>
     */
    Optional<Set<Permiso>> obtenerPermisosSiRolExiste(Connection conn, String codigoRol) throws SQLException;

    void cambiarEstadoRol(Connection conn, int idRol, boolean activo) throws SQLException;
}
