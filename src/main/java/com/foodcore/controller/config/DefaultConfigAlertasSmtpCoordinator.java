package com.foodcore.controller.config;

import com.foodcore.service.AlertaService;
import com.foodcore.service.AlertaService.AlertaEmitida;
import com.foodcore.service.AlertaService.ConfiguracionAlertaEmail;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación por defecto del {@link ConfigAlertasSmtpCoordinator} que valida los
 * campos de configuración SMTP y delega el envío de alertas de prueba en
 * {@link AlertaService}.
 */
public class DefaultConfigAlertasSmtpCoordinator implements ConfigAlertasSmtpCoordinator {

    private static final Logger LOGGER =
            Logger.getLogger(DefaultConfigAlertasSmtpCoordinator.class.getName());

    private static final String HOST_SMTP_POR_DEFECTO = "smtp.gmail.com";

    private static final int PUERTO_MINIMO = 1;

    private static final int PUERTO_MAXIMO = 65535;

    /**
     * Constructor público sin argumentos.
     */
    public DefaultConfigAlertasSmtpCoordinator() {
        // Sin estado inicial; la persistencia se delega en AlertaService.
    }

    @Override
    public ConfiguracionAlertaEmail obtenerConfiguracionActual() {
        return AlertaService.getConfiguracionGlobal();
    }

    @Override
    public ValidacionSmtp validarYConstruirConfiguracion(String emailDest, String host,
                                                          String portRaw, String user,
                                                          String pass, boolean habilitado,
                                                          boolean tls) {
        String correo = emailDest == null ? "" : emailDest.trim();
        if (correo.isBlank() || !correo.contains("@")) {
            return new ValidacionSmtp(
                    false,
                    null,
                    "Ingrese un correo destinatario válido para alertas."
            );
        }
        String puertoTexto = portRaw == null || portRaw.isBlank() ? "587" : portRaw.trim();
        int puerto;
        try {
            puerto = Integer.parseInt(puertoTexto);
            if (puerto < PUERTO_MINIMO || puerto > PUERTO_MAXIMO) {
                throw new NumberFormatException("Puerto fuera de rango");
            }
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.FINE, "Puerto SMTP inválido: " + puertoTexto, ex);
            return new ValidacionSmtp(
                    false,
                    null,
                    "Puerto SMTP inválido (ej. 587 o 465)."
            );
        }
        String hostNormalizado = host == null || host.isBlank()
                ? HOST_SMTP_POR_DEFECTO : host.trim();
        ConfiguracionAlertaEmail config = new ConfiguracionAlertaEmail(
                hostNormalizado,
                puerto,
                user == null ? "" : user.trim(),
                pass == null ? "" : pass,
                tls,
                correo,
                habilitado
        );
        return new ValidacionSmtp(true, config, null);
    }

    @Override
    public void guardarConfiguracion(ConfiguracionAlertaEmail config, boolean persistir) {
        if (config == null) {
            return;
        }
        AlertaService.actualizarConfiguracionGlobal(config, persistir);
    }

    @Override
    public AlertaEmitida enviarAlertaPrueba() {
        return AlertaService.enviarAlertaPruebaGlobal();
    }
}
