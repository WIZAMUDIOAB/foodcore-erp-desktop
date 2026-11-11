package com.foodcore.dao;

import com.foodcore.model.SolicitudAusencia;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface SolicitudAusenciaDAO {
    int crear(Connection conn, SolicitudAusencia sol) throws SQLException;
    List<SolicitudAusencia> listarTodas(Connection conn) throws SQLException;
    List<SolicitudAusencia> listarPorUsuario(Connection conn, int idUsuario) throws SQLException;
    void actualizarEstado(Connection conn, int idSolicitud, String nuevoEstado, String observacion) throws SQLException;
}
