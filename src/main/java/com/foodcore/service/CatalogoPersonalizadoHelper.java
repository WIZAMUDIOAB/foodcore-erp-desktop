package com.foodcore.service;

import com.foodcore.dao.ComboDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoPersonalizacionDAO;
import com.foodcore.model.Combo;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helper compartido en el paquete {@code com.foodcore.service} para cargar y enriquecer
 * productos del catálogo con variantes, modificadores y componentes de combo sin depender
 * de implementaciones concretas en {@code com.foodcore.service.impl}.
 */
public final class CatalogoPersonalizadoHelper {

    private CatalogoPersonalizadoHelper() {
    }

    public static List<Producto> listarProductosDisponiblesConOpciones(
            Connection conn,
            ProductoDAO productoDAO,
            ProductoPersonalizacionDAO personalizacionDAO,
            ComboDAO comboDAO) throws SQLException {
        Objects.requireNonNull(productoDAO, "productoDAO");
        List<Producto> productos = productoDAO.listarProductosDisponibles(conn);
        enriquecerListaProductos(conn, productos, personalizacionDAO, comboDAO);
        return productos;
    }

    public static void enriquecerListaProductos(
            Connection conn,
            List<Producto> productos,
            ProductoPersonalizacionDAO personalizacionDAO,
            ComboDAO comboDAO) throws SQLException {
        if (productos == null || productos.isEmpty()) {
            return;
        }
        Objects.requireNonNull(personalizacionDAO, "personalizacionDAO");
        Objects.requireNonNull(comboDAO, "comboDAO");
        Map<Integer, List<ProductoVariante>> mapaVariantes = personalizacionDAO.listarVariantesPorProductos(conn);
        Map<Integer, List<ProductoModificador>> mapaMods = personalizacionDAO.listarModificadoresPorProductos(conn);
        Map<Integer, Combo> mapaCombos = comboDAO.listarMapaCombosActivosPorProducto(conn);
        for (Producto p : productos) {
            p.setVariantes(mapaVariantes.getOrDefault(p.getIdProducto(), List.of()));
            p.setModificadores(mapaMods.getOrDefault(p.getIdProducto(), List.of()));
            Combo combo = mapaCombos.get(p.getIdProducto());
            if (combo != null) {
                p.setEsCombo(true);
                p.setIdCombo(combo.getIdCombo());
                p.setComboComponentes(combo.getDetalles());
            }
        }
    }
}
