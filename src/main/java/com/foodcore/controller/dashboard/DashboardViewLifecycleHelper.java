package com.foodcore.controller.dashboard;

public class DashboardViewLifecycleHelper {

    public void detenerControladorActivo(Object controladorActivo) {
        if (controladorActivo instanceof LifecycleAwareController lifecycleAware) {
            lifecycleAware.detenerRecursos();
        }
    }
}