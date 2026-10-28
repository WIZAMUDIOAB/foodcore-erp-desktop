package com.foodcore.service;

import com.foodcore.dao.*;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.model.Receta;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Servicio de Mantenimiento de Catálogo, Insumos, Recetas y Diagnóstico de BD.
 * Diseñado y administrado por el Integrante 1 (Líder Técnico de Persistencia).
 */
public class MantenimientoService {

    private final CategoriaDAO categoriaDAO = new CategoriaDAOImpl();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final RecetaDAO recetaDAO = new RecetaDAOImpl();

    // ==========================================
    // 1. Gestión de Categorías
    // ==========================================
    public List<Categoria> listarCategorias() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return categoriaDAO.listarTodas(conn);
        }
    }

    public void guardarCategoria(Categoria c) throws NegocioException, SQLException {
        if (c.getNombre() == null || c.getNombre().trim().isEmpty()) {
            throw new NegocioException("El nombre de la categoría es obligatorio.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            if (c.getIdCategoria() == 0) {
                categoriaDAO.crear(conn, c);
            } else {
                categoriaDAO.actualizar(conn, c);
            }
        }
    }

    // ==========================================
    // 2. Gestión de Productos / Platos
    // ==========================================
    public List<Producto> listarProductos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return productoDAO.listarTodos(conn);
        }
    }

    public void guardarProducto(Producto p) throws NegocioException, SQLException {
        if (p.getNombre() == null || p.getNombre().trim().isEmpty()) {
            throw new NegocioException("El nombre del plato/producto es obligatorio.");
        }
        if (p.getCodigo() == null || p.getCodigo().trim().isEmpty()) {
            throw new NegocioException("El código del producto es obligatorio (ej: HAM-01).");
        }
        if (p.getPrecioVenta() == null || p.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El precio de venta debe ser mayor a 0.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            if (p.getIdProducto() == 0) {
                productoDAO.crear(conn, p);
            } else {
                productoDAO.actualizar(conn, p);
            }
        }
    }

    public void alternarDisponibilidadProducto(int idProducto, boolean disponible) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            productoDAO.cambiarDisponibilidad(conn, idProducto, disponible);
        }
    }

    public void eliminarProducto(int idProducto) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            productoDAO.eliminar(conn, idProducto);
        }
    }

    // ==========================================
    // 3. Gestión de Insumos y Almacén
    // ==========================================
    public List<Insumo> listarInsumos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return insumoDAO.listarTodos(conn);
        }
    }

    public void guardarInsumo(Insumo i) throws NegocioException, SQLException {
        if (i.getNombre() == null || i.getNombre().trim().isEmpty()) {
            throw new NegocioException("El nombre del insumo es obligatorio.");
        }
        if (i.getCodigo() == null || i.getCodigo().trim().isEmpty()) {
            throw new NegocioException("El código del insumo es obligatorio (ej: INS-001).");
        }
        if (i.getUnidadMedida() == null || i.getUnidadMedida().trim().isEmpty()) {
            throw new NegocioException("Seleccione una unidad de medida (GR, UND, ML, KG, LT).");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            if (i.getIdInsumo() == 0) {
                insumoDAO.crear(conn, i);
            } else {
                insumoDAO.actualizar(conn, i);
            }
        }
    }

    public void registrarAjusteStock(int idInsumo, BigDecimal nuevoStock, String motivo) throws NegocioException, SQLException {
        if (nuevoStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El stock no puede ser negativo.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                insumoDAO.actualizarStock(conn, idInsumo, nuevoStock);
                insumoDAO.registrarMovimientoInventario(conn, idInsumo, "AJUSTE", nuevoStock, motivo, null);
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ==========================================
    // 4. Gestión de Recetas de Platos
    // ==========================================
    public List<Receta> listarRecetaPorProducto(int idProducto) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return recetaDAO.listarPorProducto(conn, idProducto);
        }
    }

    public void asociarInsumoAReceta(int idProducto, int idInsumo, BigDecimal cantidad) throws NegocioException, SQLException {
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("La cantidad necesaria del insumo debe ser mayor a 0.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            recetaDAO.asociarInsumo(conn, idProducto, idInsumo, cantidad);
        }
    }

    public void eliminarInsumoDeReceta(int idReceta) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            recetaDAO.eliminarComponente(conn, idReceta);
        }
    }

    // ==========================================
    // 5. Diagnóstico de Salud de la BD (Health Check)
    // ==========================================
    public boolean verificarSaludBD() {
        return ConexionBD.testConexion();
    }

    public String obtenerMetricasPool() {
        return ConexionBD.getEstadoPool();
    }
}
