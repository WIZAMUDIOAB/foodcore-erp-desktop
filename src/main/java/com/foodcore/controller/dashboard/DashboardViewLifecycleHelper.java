package com.foodcore.controller.dashboard;

import com.foodcore.controller.EstadoSistemaController;
import com.foodcore.controller.KitchenDisplayController;
import com.foodcore.controller.RelojAsistenciaController;
import com.foodcore.controller.VentaController;

public class DashboardViewLifecycleHelper {
    public void detenerControladorActivo(Object controladorActivo) {
        if (controladorActivo instanceof RelojAsistenciaController controlador) {
            controlador.stopTimeline();
        } else if (controladorActivo instanceof VentaController controlador) {
            controlador.stopTimeline();
        } else if (controladorActivo instanceof KitchenDisplayController controlador) {
            controlador.stopTimers();
        } else if (controladorActivo instanceof EstadoSistemaController controlador) {
            controlador.stopTimeline();
        }
    }
}