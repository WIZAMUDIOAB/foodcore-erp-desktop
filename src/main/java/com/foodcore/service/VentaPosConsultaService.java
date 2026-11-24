package com.foodcore.service;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.Cliente;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VentaPosConsultaService {
    private static final Logger LOGGER = Logger.getLogger(VentaPosConsultaService.class.getName());
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();
    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final PersonalizacionProductoService personalizacionService = new PersonalizacionProductoServiceImpl();

    public List<Pedido> listarPedidosActivosConDetalles() {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) return List.of();
            List<Pedido> pedidos = pedidoDAO.listarPedidosActivos(conn);
            for (Pedido pedido : pedidos) {
                if (pedido.getDetalles() == null || pedido.getDetalles().isEmpty()) {
                    pedido.setDetalles(pedidoDAO.listarDetallesPorPedido(conn, pedido.getIdPedido()));
                }
            }
            return pedidos;
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso consultando pedidos activos POS", ex);
            return List.of();
        }
    }

    public List<DetallePedido> listarDetallesPorPedido(int idPedido) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return conn == null ? List.of() : pedidoDAO.listarDetallesPorPedido(conn, idPedido);
        }
    }

    public List<Categoria> listarCategoriasProductos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return conn == null ? List.of() : productoDAO.listarCategorias(conn);
        }
    }

    public List<Producto> listarProductosDisponiblesConOpciones() {
        try (Connection conn = ConexionBD.getConnection()) {
            return conn == null ? List.of() : personalizacionService.listarProductosDisponiblesConOpciones(conn);
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso consultando catálogo POS", ex);
            return List.of();
        }
    }

    public Optional<Cliente> buscarClientePorDniOTelefono(String criterio) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return conn == null ? Optional.empty() : clienteDAO.buscarPorDniOTelefono(conn, criterio);
        }
    }

    public Cliente buscarORegistrarClienteMinoristaRapido(String dni, String nombreCompleto, String telefono,
            LocalDate fechaNacimiento) {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) return null;
            Optional<Cliente> existente = clienteDAO.buscarPorDocumento(conn, dni);
            if (existente.isPresent()) return existente.get();
            Cliente nuevo = new Cliente();
            nuevo.setTipoCliente("MINORISTA");
            nuevo.setTipoDocumento("DNI");
            nuevo.setNumeroDocumento(dni);
            nuevo.setRazonSocial(nombreCompleto);
            nuevo.setContacto(nombreCompleto);
            nuevo.setTelefono(telefono != null && !telefono.isBlank() ? telefono.trim() : null);
            nuevo.setFechaNacimiento(fechaNacimiento);
            nuevo.setLimiteCredito(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            nuevo.setDiasCredito(0);
            clienteDAO.insertar(conn, nuevo);
            return nuevo;
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error registrando cliente rápido POS", ex);
            return null;
        }
    }
}