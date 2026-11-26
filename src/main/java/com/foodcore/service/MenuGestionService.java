package com.foodcore.service;

import com.foodcore.dao.CategoriaDAO;
import com.foodcore.dao.CategoriaDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Servicio de negocio para la gestión del menú (§2.2): consulta de categorías,
 * alta/actualización de productos y resolución (o creación) de la categoría
 * asociada a un platillo. Encapsula el acceso a {@link ProductoDAO} y
 * {@link CategoriaDAO} junto con la apertura y cierre de la conexión.
 */
public class MenuGestionService {

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final CategoriaDAO categoriaDAO = new CategoriaDAOImpl();

    /**
     * Resultado de resolver una categoría por nombre: su identificador y, cuando
     * fue necesario crearla, la nueva instancia generada.
     */
    public record CategoriaResultado(int idCategoria, Categoria nuevaCategoriaCreada) {
    }

    public List<Categoria> listarCategoriasProductos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return productoDAO.listarCategorias(conn);
        }
    }

    public void actualizarProducto(Producto p) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            productoDAO.actualizar(conn, p);
        }
    }

    public void crearProducto(Producto p) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            productoDAO.crear(conn, p);
        }
    }

    /**
     * Busca una categoría existente por nombre (ignorando mayúsculas y espacios);
     * si no existe la crea y devuelve tanto su ID como la instancia creada.
     */
    public CategoriaResultado resolverOCrearCategoria(String nombreCategoria) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            Categoria existente = null;
            List<Categoria> enBD = categoriaDAO.listarTodas(conn);
            for (Categoria c : enBD) {
                if (c.getNombre() != null && c.getNombre().trim().equalsIgnoreCase(nombreCategoria)) {
                    existente = c;
                    break;
                }
            }
            if (existente != null && existente.getIdCategoria() > 0) {
                return new CategoriaResultado(existente.getIdCategoria(), null);
            }
            Categoria nuevaCat = new Categoria(0, nombreCategoria, "Categoría creada dinámicamente", true);
            int idCat = categoriaDAO.crear(conn, nuevaCat);
            return new CategoriaResultado(idCat, nuevaCat);
        }
    }
}
