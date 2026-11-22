package com.foodcore.service;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.dao.impl.ProveedorDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.Proveedor;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de proveedores: consulta, alta,
 * actualización, cambio de estado y obtención de insumos con stock bajo para
 * la generación de órdenes de compra. Encapsula el acceso a
 * {@link ProveedorDAO} e {@link InsumoDAO} junto con la apertura y cierre de
 * la conexión.
 */
public class ProveedorGestionService {

    private final ProveedorDAO proveedorDAO = new ProveedorDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();

    public List<Proveedor> listarTodos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return proveedorDAO.listarTodos(conn);
        }
    }

    public void actualizar(Proveedor p) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            proveedorDAO.actualizar(conn, p);
        }
    }

    public void insertar(Proveedor p) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            proveedorDAO.insertar(conn, p);
        }
    }

    public void cambiarEstado(int idProveedor, boolean nuevoEstado) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            proveedorDAO.cambiarEstado(conn, idProveedor, nuevoEstado);
        }
    }

    /**
     * Devuelve los insumos cuyo stock disponible es menor o igual al mínimo.
     * Si no existe ninguno en esa condición, retorna hasta {@code limiteFallback}
     * insumos como respaldo para la orden de compra.
     */
    public List<Insumo> listarInsumosStockBajo(int limiteFallback) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            List<Insumo> todos = insumoDAO.listarTodos(conn);
            List<Insumo> stockBajo = todos.stream()
                    .filter(i -> i.getStockActualDisponible() != null && i.getStockMinimo() != null
                            && i.getStockActualDisponible().compareTo(i.getStockMinimo()) <= 0)
                    .collect(Collectors.toList());
            if (stockBajo.isEmpty()) {
                return todos.stream()
                        .limit(Math.max(0, limiteFallback))
                        .collect(Collectors.toList());
            }
            return stockBajo;
        }
    }
}
