package com.foodcore.security;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.util.ServidorSockets;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor de ciclo de vida de la sesión autenticada (SPEC-FOODCORE-v1.0 §10.2; REMEDIATION R-18).
 * Verifica expiración de 30 minutos y bloqueo por cambio de contraseña obligatorio.
 */
public final class SesionManager {

    private static final Logger LOGGER = Logger.getLogger(SesionManager.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final AtomicReference<SesionUsuario> SESION_ACTIVA = new AtomicReference<>();
    private static final AtomicBoolean CAMBIO_PASSWORD_PENDIENTE = new AtomicBoolean(false);

    private SesionManager() {
    }

    public static void iniciarSesion(SesionUsuario sesion, boolean cambioPasswordRequerido) {
        SESION_ACTIVA.set(sesion);
        CAMBIO_PASSWORD_PENDIENTE.set(cambioPasswordRequerido);
    }

    public static SesionUsuario getSesionActiva() {
        return SESION_ACTIVA.get();
    }

    public static boolean isCambioPasswordPendiente() {
        return CAMBIO_PASSWORD_PENDIENTE.get();
    }

    public static void completarCambioPassword() {
        CAMBIO_PASSWORD_PENDIENTE.set(false);
    }

    /**
     * Verifica que exista una sesión activa, no haya expirado su TTL de 30 minutos
     * y no tenga cambio de contraseña obligatorio pendiente.
     */
    public static SesionUsuario verificarSesionActiva() throws NegocioException {
        return verificarSesionActiva(LocalDateTime.now(ZONA_LIMA));
    }

    public static SesionUsuario verificarSesionActiva(LocalDateTime ahora) throws NegocioException {
        SesionUsuario sesion = SESION_ACTIVA.get();
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
        if (CAMBIO_PASSWORD_PENDIENTE.get()) {
            throw new NegocioException(
                CodigoError.CAMBIO_PASSWORD_REQUERIDO,
                "Debe cambiar su contraseña inicial antes de ejecutar operaciones."
            );
        }
        return sesion;
    }

    public static void cerrarSesion() {
        SESION_ACTIVA.set(null);
        CAMBIO_PASSWORD_PENDIENTE.set(false);
        try {
            ServidorSockets.getInstance().cerrarSesionesActivas();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Cierre defensivo de sesiones socket", e);
        }
    }
}
