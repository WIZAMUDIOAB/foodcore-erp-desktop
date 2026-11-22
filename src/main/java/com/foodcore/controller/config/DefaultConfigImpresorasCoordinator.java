package com.foodcore.controller.config;

import com.foodcore.service.ConfiguracionSistemaService;
import com.foodcore.service.ConfiguracionSistemaService.DatosRestauranteConfig;
import com.foodcore.service.ConfiguracionSistemaService.RuteoImpresorasData;
import com.foodcore.util.ImpresoraTermicaService;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Implementación por defecto del {@link ConfigImpresorasCoordinator} que delega el
 * ruteo de impresoras por categoría y la configuración del restaurante en
 * {@link ConfiguracionSistemaService}.
 */
public class DefaultConfigImpresorasCoordinator implements ConfigImpresorasCoordinator {

    private static final String ALIAS_COCINA = "Cocina";

    private static final String ALIAS_BARRA = "Barra";

    private static final String ALIAS_PARRILLA = "Parrilla";

    private static final String ALIAS_CAJA = "Caja";

    private final ConfiguracionSistemaService configuracionService;

    /**
     * Constructor público sin argumentos.
     */
    public DefaultConfigImpresorasCoordinator() {
        this(new ConfiguracionSistemaService());
    }

    /**
     * Constructor con inyección del servicio de configuración del sistema.
     *
     * @param configuracionService servicio de configuración a utilizar.
     */
    public DefaultConfigImpresorasCoordinator(ConfiguracionSistemaService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @Override
    public Set<String> obtenerImpresorasDisponibles() {
        Set<String> opciones = new LinkedHashSet<>();
        opciones.addAll(ImpresoraTermicaService.listarImpresorasWindows());
        opciones.add(ALIAS_COCINA);
        opciones.add(ALIAS_BARRA);
        opciones.add(ALIAS_PARRILLA);
        opciones.add(ALIAS_CAJA);
        return opciones;
    }

    @Override
    public RuteoImpresorasData cargarRuteoImpresoras() throws SQLException {
        return configuracionService.cargarRuteoImpresoras();
    }

    @Override
    public void asignarImpresoraCategoria(int idCategoria, String nombreImpresora) throws SQLException {
        configuracionService.asignarImpresoraCategoria(idCategoria, nombreImpresora);
    }

    @Override
    public void eliminarImpresoraCategoria(int idCategoria) throws SQLException {
        configuracionService.eliminarImpresoraCategoria(idCategoria);
    }

    @Override
    public Optional<DatosRestauranteConfig> cargarConfiguracionRestaurante() throws SQLException {
        return configuracionService.cargarConfiguracionRestaurante();
    }

    @Override
    public void guardarConfiguracionRestaurante(DatosRestauranteConfig datos) throws SQLException {
        configuracionService.guardarConfiguracionRestaurante(datos);
    }
}
