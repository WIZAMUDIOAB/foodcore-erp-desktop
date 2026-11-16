package com.foodcore.util;

import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;

/**
 * Servicio unificado de notificaciones Toast y alertas modales estilizadas (§2.2).
 */
public final class ToastService {

    private ToastService() {
    }

    /**
     * Muestra un mensaje toast informativo sobre el contenedor raíz.
     */
    public static void mostrarInfo(Pane hostPane, String mensaje) {
        AnimationUtils.mostrarToast(hostPane, mensaje, false);
    }

    /**
     * Muestra un mensaje toast de error sobre el contenedor raíz.
     */
    public static void mostrarError(Pane hostPane, String mensaje) {
        AnimationUtils.mostrarToast(hostPane, mensaje, true);
    }

    /**
     * Despliega una alerta modal estilizada sin usar ventanas blancas nativas.
     */
    public static void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        DialogService.mostrarAlerta(tipo, titulo, contenido);
    }
}
