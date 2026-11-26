package com.foodcore.security;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.util.ServidorSockets;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor de ciclo de vida de la sesión autenticada (SPEC-FOODCORE-v1.0 §10.2; REMEDIATION R-18).
 * Mantiene de forma atómica y thread-safe la sesión activa, el usuario autenticado
 * y el estado de cambio obligatorio de contraseña.
 */
public final class SesionManager {

    private static final Logger LOGGER = Logger.getLogger(SesionManager.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final AtomicReference<EstadoSesion> ESTADO_ACTUAL = new AtomicReference<>(EstadoSesion.VACIO);

    private static final class EstadoSesion {
        private static final EstadoSesion VACIO = new EstadoSesion(null, null, false);

        private final SesionUsuario sesion;
        private final Usuario usuario;
        private final boolean cambioPasswordPendiente;

        private EstadoSesion(SesionUsuario sesion, Usuario usuario, boolean cambioPasswordPendiente) {
            this.sesion = sesion;
            this.usuario = usuario;
            this.cambioPasswordPendiente = cambioPasswordPendiente;
        }
    }

    private SesionManager() {
    }

    public static void iniciarSesion(SesionUsuario sesion, boolean cambioPasswordRequerido) {
        iniciarSesion(sesion, null, cambioPasswordRequerido);
    }

    public static void iniciarSesion(SesionUsuario sesion, Usuario usuario, boolean cambioPasswordRequerido) {
        EstadoSesion nuevoEstado = (sesion == null && usuario == null && !cambioPasswordRequerido)
                ? EstadoSesion.VACIO
                : new EstadoSesion(sesion, usuario, cambioPasswordRequerido);
        ESTADO_ACTUAL.set(nuevoEstado);
    }

    public static void setUsuarioActual(Usuario usuario) {
        ESTADO_ACTUAL.updateAndGet(prev -> (usuario == null && prev.sesion == null && !prev.cambioPasswordPendiente)
                ? EstadoSesion.VACIO
                : new EstadoSesion(prev.sesion, usuario, prev.cambioPasswordPendiente));
    }

    public static Usuario getUsuarioActual() {
        return ESTADO_ACTUAL.get().usuario;
    }

    public static SesionUsuario getSesionActiva() {
        return ESTADO_ACTUAL.get().sesion;
    }

    public static boolean isCambioPasswordPendiente() {
        return ESTADO_ACTUAL.get().cambioPasswordPendiente;
    }

    public static void completarCambioPassword() {
        ESTADO_ACTUAL.updateAndGet(prev -> new EstadoSesion(prev.sesion, prev.usuario, false));
    }

    public static void completarCambioPassword(int idUsuario, String nuevoHash) {
        ESTADO_ACTUAL.updateAndGet(prev -> {
            Usuario actual = prev.usuario;
            if (actual != null && actual.getIdUsuario() == idUsuario) {
                actual.setCambioPasswordRequerido(false);
                actual.setPasswordHash(nuevoHash);
            }
            return new EstadoSesion(prev.sesion, actual, false);
        });
    }

    /**
     * Verifica que exista una sesión activa, no haya expirado su TTL de 30 minutos
     * y no tenga cambio de contraseña obligatorio pendiente.
     */
    public static SesionUsuario verificarSesionActiva() throws NegocioException {
        return verificarSesionActiva(LocalDateTime.now(ZONA_LIMA));
    }

    public static SesionUsuario verificarSesionActiva(LocalDateTime ahora) throws NegocioException {
        EstadoSesion estado = ESTADO_ACTUAL.get();
        SesionUsuario sesion = estado.sesion;
        if (sesion == null) {
            throw new NegocioException(CodigoError.ACCESO_DENEGADO, "No existe una sesión autenticada activa.");
        }
        if (sesion.haExpirado(ahora)) {
            cerrarSesion();
            throw new NegocioException(
                CodigoError.SESION_EXPIRADA,
                "La sesión ha expirado tras 30 minutos. Inicie sesión nuevamente."
            );
        }
        if (estado.cambioPasswordPendiente) {
            throw new NegocioException(
                CodigoError.CAMBIO_PASSWORD_REQUERIDO,
                "Debe cambiar su contraseña inicial antes de ejecutar operaciones."
            );
        }
        return sesion;
    }

    public static void cerrarSesion() {
        ESTADO_ACTUAL.set(EstadoSesion.VACIO);
        try {
            ServidorSockets.getInstance().cerrarSesionesActivas();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Cierre defensivo de sesiones socket", e);
        }
    }
}
