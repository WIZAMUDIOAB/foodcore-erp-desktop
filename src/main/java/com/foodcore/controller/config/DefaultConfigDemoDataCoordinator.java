package com.foodcore.controller.config;

import com.foodcore.service.DemoDataService;
import com.foodcore.service.DemoDataService.ResumenDemo;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Implementación por defecto del {@link ConfigDemoDataCoordinator} que delega los
 * datos de demostración en {@link DemoDataService} y formatea el badge de estado.
 */
public class DefaultConfigDemoDataCoordinator implements ConfigDemoDataCoordinator {

    private static final String BADGE_NEUTRAL = "badge-pill-neutral";

    private static final String BADGE_SUCCESS = "badge-pill-success";

    private static final String TEXTO_SIN_DATOS = "Sin datos de demo cargados";

    private final DemoDataService demoDataService;

    /**
     * Constructor público sin argumentos.
     */
    public DefaultConfigDemoDataCoordinator() {
        this(new DemoDataService());
    }

    /**
     * Constructor con inyección del servicio de datos demo.
     *
     * @param demoDataService servicio de datos demo a utilizar.
     */
    public DefaultConfigDemoDataCoordinator(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @Override
    public ResumenDemo contarDatosDemo() {
        return ejecutarConEstado(demoDataService::contarDatosDemo);
    }

    @Override
    public ResumenDemo cargarDatosDemo() {
        return ejecutarConEstado(demoDataService::cargarDatosDemo);
    }

    @Override
    public int borrarDatosDemo() {
        return ejecutarConEstado(demoDataService::borrarDatosDemo);
    }

    private <T> T ejecutarConEstado(DemoOperacion<T> operacion) {
        try {
            return operacion.ejecutar();
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    private interface DemoOperacion<T> {
        T ejecutar() throws SQLException, IOException;
    }

    @Override
    public EstadoBadgeUi formatearResumenDemo(ResumenDemo resumen) {
        if (resumen == null || resumen.total() == 0) {
            return new EstadoBadgeUi(TEXTO_SIN_DATOS, BADGE_NEUTRAL);
        }
        return new EstadoBadgeUi(
                String.format("Demo activos: %d clientes, %d productos, %d ventas",
                        resumen.clientes(), resumen.productos(), resumen.ventas()),
                BADGE_SUCCESS);
    }
}
