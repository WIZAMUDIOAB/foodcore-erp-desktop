package com.foodcore.controller.config;

import com.foodcore.service.ConfiguracionSistemaService;
import com.foodcore.service.ConfiguracionSistemaService.DatosRestauranteConfig;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;

/**
 * Contrato para la coordinación del ruteo de impresoras por categoría y de la
 * configuración general del restaurante, aislado del controlador de la
 * interfaz gráfica.
 */
public interface ConfigImpresorasCoordinator {

    /**
     * Lista las impresoras disponibles para el ruteo por categoría, combinando
     * las impresoras térmicas detectadas en Windows con los alias internos.
     *
     * @return conjunto ordenado con los nombres de impresora disponibles.
     */
    Set<String> obtenerImpresorasDisponibles();

    /**
     * Carga el ruteo de impresoras por categoría de producto.
     *
     * @return mapa de categorías y asignaciones persistentes.
     */
    ConfiguracionSistemaService.RuteoImpresorasData cargarRuteoImpresoras() throws SQLException;

    /**
     * Asigna una impresora Windows a una categoría de producto.
     *
     * @param idCategoria    identificador de la categoría.
     * @param nombreImpresora nombre de la impresora Windows.
     */
    void asignarImpresoraCategoria(int idCategoria, String nombreImpresora) throws SQLException;

    /**
     * Elimina la asignación de impresora de una categoría de producto.
     *
     * @param idCategoria identificador de la categoría.
     */
    void eliminarImpresoraCategoria(int idCategoria) throws SQLException;

    /**
     * Carga la configuración general del restaurante.
     *
     * @return la configuración persistida, o {@link Optional#empty()} si no existe.
     */
    Optional<DatosRestauranteConfig> cargarConfiguracionRestaurante() throws SQLException;

    /**
     * Persiste la configuración general del restaurante.
     *
     * @param datos configuración a guardar.
     */
    void guardarConfiguracionRestaurante(DatosRestauranteConfig datos) throws SQLException;
}
