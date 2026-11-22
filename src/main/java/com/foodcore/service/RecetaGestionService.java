package com.foodcore.service;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.RecetaDAO;
import com.foodcore.dao.RecetaDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.model.Receta;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Servicio de negocio para la gestión de recetas y escandallos (§2.3):
 * catálogo de platillos e insumos, componentes por producto y asociación o
 * eliminación transaccional de insumos. Encapsula el acceso a
 * {@link ProductoDAO}, {@link InsumoDAO} y {@link RecetaDAO} junto con la
 * apertura y cierre de la conexión.
 */
public class RecetaGestionService {

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final RecetaDAO recetaDAO = new RecetaDAOImpl();

    /**
     * Catálogo necesario para la pantalla de recetas: platillos e insumos.
     */
    public record CatalogoRecetaData(List<Producto> productos, List<Insumo> insumos) {
    }

    public CatalogoRecetaData listarCatalogosReceta() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            List<Producto> productos = productoDAO.listarTodos(conn);
            List<Insumo> insumos = insumoDAO.listarTodos(conn);
            return new CatalogoRecetaData(productos, insumos);
        }
    }

    public List<Receta> listarPorProducto(int idProducto) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return recetaDAO.listarPorProducto(conn, idProducto);
        }
    }

    public void asociarInsumo(int idProducto, long idInsumo, BigDecimal cantidad) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            recetaDAO.asociarInsumo(conn, idProducto, idInsumo, cantidad);
        }
    }

    public void eliminarComponente(int idReceta) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            recetaDAO.eliminarComponente(conn, idReceta);
        }
    }
}
