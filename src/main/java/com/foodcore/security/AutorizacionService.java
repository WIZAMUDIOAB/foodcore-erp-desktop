package com.foodcore.security;

import com.foodcore.dao.RolDAO;
import com.foodcore.dao.impl.RolDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.enums.Permiso;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de control de acceso basado en roles y permisos (RBAC) según SPEC-FOODCORE-v1.0 (§10.2, §10.3).
 * La validación se ejecuta obligatoriamente en la capa de servicios (ocultar botones en UI no constituye autorización).
 * En caso de fallo de base de datos, aplica política estricta fail-closed (cero permisos).
 */
public class AutorizacionService {

    private static final Logger LOGGER = Logger.getLogger(AutorizacionService.class.getName());
    private static final Map<String, Set<Permiso>> MATRIZ_RBAC;
    private static final Map<String, String> ALIAS_ROLES = Map.of(
        "ADMINISTRADOR", "ADMIN",
        "MESERO", "MOZO",
        "COCINERO", "COCINA",
        "ALMACENERO", "ALMACEN"
    );

    @FunctionalInterface
    public interface ProveedorConexion {
        Connection obtener() throws SQLException;
    }

    static {
        Set<Permiso> permisosAdmin = Collections.unmodifiableSet(EnumSet.allOf(Permiso.class));

        Set<Permiso> permisosSupervisor = Collections.unmodifiableSet(EnumSet.of(
            Permiso.VENTA_COBRAR,
            Permiso.VENTA_ANULAR,
            Permiso.VENTA_DESCUENTO,
            Permiso.CAJA_APERTURA,
            Permiso.CAJA_CIERRE,
            Permiso.CAJA_MOVIMIENTO_MANUAL,
            Permiso.TICKET_REIMPRIMIR,
            Permiso.PEDIDO_CREAR,
            Permiso.PEDIDO_ADICIONAR
        ));

        Set<Permiso> permisosCajero = Collections.unmodifiableSet(EnumSet.of(
            Permiso.VENTA_COBRAR,
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
            "SUPERVISOR", permisosSupervisor,
            "CAJERO", permisosCajero,
            "MOZO", permisosMozo,
            "MESERO", permisosMozo,
            "COCINA", permisosCocina,
            "COCINERO", permisosCocina,
            "ALMACEN", permisosAlmacen
        );
    }

    private static final RolDAO ROL_DAO_DEFAULT = new RolDAOImpl();

    private final RolDAO rolDAO;
    private final ProveedorConexion proveedorConexion;

    public AutorizacionService() {
        this(ROL_DAO_DEFAULT, ConexionBD::getConnection);
    }

    public AutorizacionService(RolDAO rolDAO, ProveedorConexion proveedorConexion) {
        this.rolDAO = rolDAO != null ? rolDAO : ROL_DAO_DEFAULT;
        this.proveedorConexion = proveedorConexion != null ? proveedorConexion : ConexionBD::getConnection;
    }

    public static Set<Permiso> permisosPorDefectoParaRol(String codigoRol) {
        return resolverPermisosEnBd(codigoRol, ROL_DAO_DEFAULT, ConexionBD::getConnection);
    }

    private static Set<Permiso> resolverPermisosEnBd(String codigoRol, RolDAO dao, ProveedorConexion proveedor) {
        if (codigoRol == null || codigoRol.isBlank()) {
            return Collections.emptySet();
        }
        String codNorm = codigoRol.trim().toUpperCase(Locale.ROOT);
        String codCanonico = ALIAS_ROLES.getOrDefault(codNorm, codNorm);
        try (Connection conn = proveedor.obtener()) {
            Optional<Set<Permiso>> opt = dao.obtenerPermisosSiRolExiste(conn, codNorm);
            if (opt.isEmpty() && !codCanonico.equals(codNorm)) {
                opt = dao.obtenerPermisosSiRolExiste(conn, codCanonico);
            }
            return opt.orElse(Collections.emptySet());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE,
                "Fallo consultando permisos RBAC en BD para rol '" + codNorm + "'; aplicando fail-closed (0 permisos).",
                ex);
            return Collections.emptySet();
        }
    }

    public static Set<Permiso> permisosPorDefectoParaRolLegacy(String codigoRol) {
        if (codigoRol == null || codigoRol.isBlank()) {
            return Collections.emptySet();
        }
        return MATRIZ_RBAC.getOrDefault(codigoRol.trim().toUpperCase(Locale.ROOT), Collections.emptySet());
    }

    /**
     * Obtiene el conjunto inmutable de permisos asignados a un rol oficial en base de datos.
     * Si el rol es nulo, desconocido, inactivo, sin permisos configurados o ocurre un error de BD,
     * devuelve un conjunto vacío (Principio de Mínimo Privilegio / Fail-Closed).
     */
    public Set<Permiso> obtenerPermisosPorRol(String codigoRol) {
        return resolverPermisosEnBd(codigoRol, this.rolDAO, this.proveedorConexion);
    }

    /**
     * Construye una {@link SesionUsuario} inmutable poblando sus permisos según la matriz RBAC.
     */
    public SesionUsuario crearSesion(long idUsuario, String nombreCompleto, String codigoRol, String idTerminal) {
        Set<Permiso> permisos = obtenerPermisosPorRol(codigoRol);
        return new SesionUsuario(idUsuario, nombreCompleto, codigoRol, permisos, idTerminal, LocalDateTime.now());
    }

    public boolean tienePermiso(SesionUsuario sesion, Permiso permiso) {
        return sesion != null && permiso != null && sesion.tienePermiso(permiso);
    }

    public void exigirPermiso(SesionUsuario sesion, Permiso permisoRequerido) throws NegocioException {
        exigir(sesion, permisoRequerido);
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
