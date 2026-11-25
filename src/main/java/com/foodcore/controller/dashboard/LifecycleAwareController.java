package com.foodcore.controller.dashboard;

/**
 * Contrato OCP para controladores JavaFX que administran recursos de ciclo de vida
 * ({@code Timeline}, animaciones, temporizadores o suscripciones a eventos).
 */
public interface LifecycleAwareController {

    /**
     * Detiene y libera de manera idempotente animaciones, temporizadores y listeners activos
     * cuando la vista se oculta, recarga o cierra.
     */
    void detenerRecursos();
}
