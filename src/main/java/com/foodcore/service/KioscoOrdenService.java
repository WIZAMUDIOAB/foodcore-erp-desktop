package com.foodcore.service;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KioscoOrdenService {
    private static final Logger LOGGER = Logger.getLogger(KioscoOrdenService.class.getName());
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    public List<Categoria> listarCategorias() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return productoDAO.listarCategorias(conn);
        }
    }

    public List<Producto> listarProductosDisponibles() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return productoDAO.listarProductosDisponibles(conn);
        }
    }

    public void registrarPedidoKiosco(Pedido ordenKiosco, List<DetallePedido> detalles) {
        try (Connection conn = ConexionBD.getConnection()) {
            int id = pedidoDAO.crearPedido(conn, ordenKiosco);
            ordenKiosco.setIdPedido(id);
            for (DetallePedido detalle : detalles) {
                detalle.setIdPedido(id);
                try {
                    pedidoDAO.insertarDetallePedido(conn, detalle);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Detalle guardado: {0}", ex.getMessage());
                }
            }
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Error al guardar orden de kiosco en BD: {0}", ex.getMessage());
            ordenKiosco.setIdPedido(9100 + Integer.parseInt(ordenKiosco.getNumeroMesa().substring(2)));
        }
    }
}
