package com.foodcore.service;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Valida credenciales PIN de un usuario con rol SUPERVISOR o ADMIN para autorizar
 * operaciones sensibles iniciadas por un CAJERO (Modo Supervisor v5.2.0).
 */
public class SupervisorAutorizacionService {

    private final UsuarioDAO usuarioDAO;
    private final EncriptadorPassword encriptador;

    public SupervisorAutorizacionService() {
        this(new UsuarioDAOImpl(), new EncriptadorPassword());
    }

    public SupervisorAutorizacionService(UsuarioDAO usuarioDAO, EncriptadorPassword encriptador) {
        this.usuarioDAO = usuarioDAO;
        this.encriptador = encriptador;
    }

    public Usuario autenticarSupervisorPorPin(String pinPlano) throws NegocioException, SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return autenticarSupervisorPorPin(conn, pinPlano);
        }
    }

    public Usuario autenticarSupervisorPorPin(Connection conn, String pinPlano)
            throws NegocioException, SQLException {
        if (pinPlano == null || pinPlano.isBlank()) {
            throw new NegocioException(CodigoError.ACCESO_DENEGADO,
                    "Debe ingresar el PIN de un Supervisor o Administrador autorizado.");
        }
        String pinLimpio = pinPlano.trim();
        List<Usuario> activos = usuarioDAO.listarTodosActivos(conn);

        for (Usuario u : activos) {
            if (u == null || !u.isActivo()) {
                continue;
            }
            if (!pinCoincide(pinLimpio, u)) {
                continue;
            }
            if (esSupervisorOAdminAutorizado(u)) {
                return u;
            }
        }

        throw new NegocioException(CodigoError.ACCESO_DENEGADO,
                "PIN inválido o el usuario ingresado no cuenta con privilegios de SUPERVISOR/ADMIN.");
    }

    private boolean pinCoincide(String pinPlano, Usuario u) {
        String hash = u.getPinHash();
        if (hash != null && hash.startsWith("$2") && encriptador.verificar(pinPlano, hash)) {
            return true;
        }
        return u.getPin() != null && pinPlano.equals(u.getPin());
    }

    private boolean esSupervisorOAdminAutorizado(Usuario u) {
        String rol = u.getNombreRol() != null ? u.getNombreRol().trim().toUpperCase() : "";
        boolean rolElevado = rol.contains("ADMIN") || rol.contains("SUPERVISOR");
        Set<Permiso> permisos = AutorizacionService.permisosPorDefectoParaRol(u.getNombreRol());
        return rolElevado || permisos.contains(Permiso.VENTA_ANULAR);
    }
}
