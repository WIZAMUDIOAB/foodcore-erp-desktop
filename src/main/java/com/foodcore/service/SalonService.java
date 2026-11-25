package com.foodcore.service;

import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.MesaUnion;
import com.foodcore.model.Pedido;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class SalonService {

    private final MesaDAO mesaDAO;
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    public SalonService() {
        this(new MesaDAOImpl());
    }

    public SalonService(MesaDAO mesaDAO) {
        this.mesaDAO = mesaDAO;
    }

    public List<Pedido> listarPedidosActivosConConexion() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return List.of();
            }
            return pedidoDAO.listarPedidosActivos(conn);
        }
    }

    public List<DetallePedido> listarDetallesPedido(int idPedido) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return List.of();
            }
            return pedidoDAO.listarDetallesPorPedido(conn, idPedido);
        }
    }

    public boolean transferirMesa(String origen, String destino, int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mesaDAO.transferirPedidoMesa(conn, origen, destino, idUsuario);
        }
    }

    public boolean unirMesas(String principal, List<String> secundarias, int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mesaDAO.unirMesas(conn, principal, secundarias, idUsuario);
        }
    }

    public boolean liberarMesas(int idPedido) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mesaDAO.liberarMesasUnidasPorPedido(conn, idPedido);
        }
    }

    public List<MesaUnion> listarUnionesActivas() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mesaDAO.listarUnionesActivas(conn);
        }
    }

    public List<String> listarMesasSecundarias(int idPedido) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mesaDAO.listarMesasSecundariasDePedido(conn, idPedido);
        }
    }
}
