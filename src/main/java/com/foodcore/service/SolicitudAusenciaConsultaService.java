package com.foodcore.service;

import com.foodcore.dao.SolicitudAusenciaDAO;
import com.foodcore.dao.impl.SolicitudAusenciaDAOImpl;
import com.foodcore.model.SolicitudAusencia;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class SolicitudAusenciaConsultaService {

    private final SolicitudAusenciaDAO solicitudDAO = new SolicitudAusenciaDAOImpl();

    public List<SolicitudAusencia> listarSolicitudes(boolean esGerente, int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return esGerente ? solicitudDAO.listarTodas(conn) : solicitudDAO.listarPorUsuario(conn, idUsuario);
        }
    }
}