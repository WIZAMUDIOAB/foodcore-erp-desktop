package com.foodcore.model;

/**
 * Resumen diario de alertas de seguridad operacional para el Dashboard gerencial (ÍTEM 9 v5.1.0).
 *
 * @param intentosFallidosHoy    Cantidad de intentos fallidos de login/2FA o bloqueos hoy.
 * @param anulacionesHoy         Cantidad de eventos de anulación registrados hoy.
 * @param ultimoAccesoSospechoso Descripción legible del último evento sospechoso detectado.
 * @param requiereAtencion       Indica si existen eventos de seguridad que requieren revisión.
 */
public record ResumenSeguridadHoy(
        int intentosFallidosHoy,
        int anulacionesHoy,
        String ultimoAccesoSospechoso,
        boolean requiereAtencion) {

    /**
     * Instancia vacía cuando no existen alertas registradas ni incidentes pendientes.
     *
     * @return Resumen sin incidentes.
     */
    public static ResumenSeguridadHoy normal() {
        return new ResumenSeguridadHoy(0, 0, "Sin eventos sospechosos", false);
    }
}
