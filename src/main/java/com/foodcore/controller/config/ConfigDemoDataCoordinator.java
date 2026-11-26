package com.foodcore.controller.config;

import com.foodcore.service.DemoDataService.ResumenDemo;

/**
 * Contrato para la coordinación de los datos de demostración, aislado del
 * controlador de configuración de la interfaz gráfica.
 */
public interface ConfigDemoDataCoordinator {

    /**
     * Estado del badge de datos demo en la interfaz.
     *
     * @param texto      texto visible del badge.
     * @param badgeClass clase css del badge.
     */
    record EstadoBadgeUi(String texto, String badgeClass) {
    }

    /**
     * Cuenta los registros de demostración actualmente persistidos.
     *
     * @return resumen de los datos demo.
     */
    ResumenDemo contarDatosDemo();

    /**
     * Carga el script de datos de demostración.
     *
     * @return resumen de los datos demo cargados.
     */
    ResumenDemo cargarDatosDemo();

    /**
     * Elimina los registros marcados como demostración.
     *
     * @return cantidad de filas eliminadas.
     */
    int borrarDatosDemo();

    /**
     * Formatea el resumen de datos demo como estado de badge para la interfaz.
     *
     * @param resumen resumen a formatear.
     * @return estado del badge con texto y clase css.
     */
    EstadoBadgeUi formatearResumenDemo(ResumenDemo resumen);
}
