package com.foodcore.service;

import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.HistorialPrecioDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.model.HistorialPrecio;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class HistorialPreciosService {
    private final HistorialPrecioDAO historialDAO = new HistorialPrecioDAOImpl();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();

    public record CatalogoPreciosData(List<Producto> productos, List<Insumo> insumos) { }

    public List<HistorialPrecio> listarHistorial() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return historialDAO.listarTodos(conn);
        }
    }

    public CatalogoPreciosData listarCatalogosPrecios() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return new CatalogoPreciosData(productoDAO.listarTodos(conn), insumoDAO.listarTodos(conn));
        }
    }
}
