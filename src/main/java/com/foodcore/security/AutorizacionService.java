package com.foodcore.security;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.enums.Permiso;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Servicio de control de acceso basado en roles y permisos (RBAC) según SPEC-FOODCORE-v1.0 (§10.2, §10.3).
 * La validación se ejecuta obligatoriamente en la capa de servicios (ocultar botones en UI no constituye autorización).
 */
public class AutorizacionService {

    private static final Map<String, Set<Permiso>> MATRIZ_RBAC;

    static {
        Set<Permiso> permisosAdmin = Collections.unmodifiableSet(EnumSet.allOf(Permiso.class));

        Set<Permiso> permisosCajero = Collections.unmodifiableSet(EnumSet.of(
            Permiso.VENTA_COBRAR,
            Permiso.VENTA_ANULAR,
            Permiso.CAJA_APERTURA,
            Permiso.CAJA_CIERRE,
            Permiso.CAJA_MOVIMIENTO_MANUAL,
            Permiso.TICKET_REIMPRIMIR,
            Permiso.PEDIDO_CREAR,
            Permiso.PEDIDO_ADICIONAR
        ));

        Set<Permiso> permisosMozo = Collections.unmodifiableSet(EnumSet.of(
            Permiso.PEDIDO_CREAR,
            Permiso.PEDIDO_ADICIONAR
        ));

        Set<Permiso> permisosCocina = Collections.unmodifiableSet(EnumSet.of(
            Permiso.KDS_OPERAR
        ));

        Set<Permiso> permisosAlmacen = Collections.unmodifiableSet(EnumSet.of(
            Permiso.INSUMO_GESTIONAR,
            Permiso.KARDEX_CONSULTAR
        ));

        MATRIZ_RBAC = Map.of(
            "ADMIN", permisosAdmin,
            "ADMINISTRADOR", permisosAdmin,
            "CAJERO", permisosCajero,
            "MOZO", permisosMozo,
            "MESERO", permisosMozo,
            "COCINA", permisosCocina,
            "COCINERO", permisosCocina,
            "ALMACEN", permisosAlmacen
        );
    }

    /**
     * Obtiene el conjunto inmutable de permisos asignados a un rol oficial.
     * Si el rol es nulo o desconocido, devuelve un conjunto vacío (Principio de Mínimo Privilegio).
     */
    public Set<Permiso> obtenerPermisosPorRol(String codigoRol) {
        if (codigoRol == null || codigoRol.isBlank()) {
            return Collections.emptySet();
        }
        return MATRIZ_RBAC.getOrDefault(codigoRol.trim().toUpperCase(), Collections.emptySet());
    }

    /**
     * Construye una {@link SesionUsuario} inmutable poblando sus permisos según la matriz RBAC.
     */
    public SesionUsuario crearSesion(long idUsuario, String nombreCompleto, String codigoRol, String idTerminal) {
        Set<Permiso> permisos = obtenerPermisosPorRol(codigoRol);
        return new SesionUsuario(idUsuario, nombreCompleto, codigoRol, permisos, idTerminal, LocalDateTime.now());
    }

    /**
     * Exige que la sesión activa sea válida y posea el permiso especificado (§10.2).
     *
     * @throws NegocioException con {@link CodigoError#ACCESO_DENEGADO} si la sesión es nula o carece del permiso.
     */
    public void exigir(SesionUsuario sesion, Permiso permisoRequerido) throws NegocioException {
        if (permisoRequerido == null) {
            throw new IllegalArgumentException("El permiso requerido a verificar no puede ser nulo.");
        }
        if (sesion == null) {
            throw new NegocioException(
                CodigoError.ACCESO_DENEGADO,
                "Acceso denegado: No existe una sesión de usuario autenticada."
            );
        }
        if (!sesion.tienePermiso(permisoRequerido)) {
            throw new NegocioException(
                CodigoError.ACCESO_DENEGADO,
                String.format(
                    "Acceso denegado (RBAC): El usuario '%s' (Rol: %s) no posee el permiso '%s'.",
                    sesion.getNombreCompleto(),
                    sesion.getCodigoRol(),
                    permisoRequerido.name()
                )
            );
        }
    }
}
