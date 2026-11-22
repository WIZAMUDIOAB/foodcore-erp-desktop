package com.foodcore.controller.config;

import com.foodcore.service.AlertaService.AlertaEmitida;
import com.foodcore.service.AlertaService.ConfiguracionAlertaEmail;

/**
 * Contrato para la configuración de alertas de fallo por correo SMTP,
 * aislado del controlador de configuración de la interfaz gráfica.
 */
public interface ConfigAlertasSmtpCoordinator {

    /**
     * Resultado de validar los campos de configuración SMTP.
     *
     * @param valido        true si los campos son válidos.
     * @param config        configuración construida, o null si la validación falló.
     * @param mensajeError  mensaje de error para el usuario, o null si fue válido.
     */
    record ValidacionSmtp(boolean valido, ConfiguracionAlertaEmail config,
                          String mensajeError) {
    }

    /**
     * Obtiene la configuración SMTP actualmente persistida.
     *
     * @return la configuración global de alertas por email.
     */
    ConfiguracionAlertaEmail obtenerConfiguracionActual();

    /**
     * Valida los campos de la configuración SMTP y, de ser válidos, construye
     * la configuración resultante.
     *
     * @param emailDest  correo destinatario de las alertas.
     * @param host       servidor SMTP.
     * @param portRaw    puerto SMTP en formato textual.
     * @param user       usuario SMTP.
     * @param pass       contraseña SMTP.
     * @param habilitado indica si las alertas están habilitadas.
     * @param tls        indica si se usa TLS.
     * @return validación con la configuración construida o el mensaje de error.
     */
    ValidacionSmtp validarYConstruirConfiguracion(String emailDest, String host, String portRaw,
                                                   String user, String pass, boolean habilitado,
                                                   boolean tls);

    /**
     * Guarda la configuración SMTP, persistiéndola si se indica.
     *
     * @param config    configuración a aplicar.
     * @param persistir true para persistir la configuración en el sistema.
     */
    void guardarConfiguracion(ConfiguracionAlertaEmail config, boolean persistir);

    /**
     * Envía un correo de prueba con la configuración actual.
     *
     * @return el resultado del envío, o null si no se pudo emitir.
     */
    AlertaEmitida enviarAlertaPrueba();
}
