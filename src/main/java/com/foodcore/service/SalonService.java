package com.foodcore.service;

import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.model.MesaUnion;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class SalonService {

    private final MesaDAO mesaDAO;

    public SalonService() {
        this(new MesaDAOImpl());
    }

    public SalonService(MesaDAO mesaDAO) {
        this.mesaDAO = mesaDAO;
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
