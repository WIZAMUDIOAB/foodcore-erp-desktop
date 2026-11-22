package com.foodcore.service;

import com.foodcore.model.Producto;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de negocio para la comanda de salón (Mozo): expone el catálogo de
 * productos disponibles enriquecido con sus variantes y modificadores.
 */
public class MozoOrdenService {

    private static final Logger LOGGER = Logger.getLogger(MozoOrdenService.class.getName());

    private final PersonalizacionProductoService personalizacionService =
            new PersonalizacionProductoServiceImpl();

    /**
     * Lista los productos disponibles con sus opciones de personalización.
     * Ante cualquier fallo de conexión o consulta devuelve una lista vacía
     * para que la vista pueda aplicar su catálogo de respaldo.
     */
    public List<Producto> listarProductosDisponiblesConOpciones() {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return List.of();
            }
            return personalizacionService.listarProductosDisponiblesConOpciones(conn);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se pudieron listar los productos disponibles para la comanda", ex);
            return List.of();
        }
    }
}
