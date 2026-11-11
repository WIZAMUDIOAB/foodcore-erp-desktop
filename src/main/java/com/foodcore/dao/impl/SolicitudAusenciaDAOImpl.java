package com.foodcore.dao.impl;

import com.foodcore.dao.SolicitudAusenciaDAO;
import com.foodcore.model.SolicitudAusencia;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class SolicitudAusenciaDAOImpl implements SolicitudAusenciaDAO {

    @Override
    public int crear(Connection conn, SolicitudAusencia sol) throws SQLException {
        String sql = "INSERT INTO solicitud_ausencia (id_usuario, tipo, fecha_inicio, fecha_fin, motivo, estado) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sol.getIdUsuario());
            ps.setString(2, sol.getTipo());
            ps.setDate(3, Date.valueOf(sol.getFechaInicio()));
            ps.setDate(4, Date.valueOf(sol.getFechaFin()));
            ps.setString(5, sol.getMotivo());
            ps.setString(6, sol.getEstado() != null ? sol.getEstado() : "PENDIENTE");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    sol.setIdSolicitud(id);
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public List<SolicitudAusencia> listarTodas(Connection conn) throws SQLException {
        List<SolicitudAusencia> lista = new ArrayList<>();
        String sql = "SELECT s.*, COALESCE(CONCAT(e.nombres, ' ', e.apellidos), u.username) AS nombre_emp " +
                     "FROM solicitud_ausencia s " +
                     "JOIN usuario u ON s.id_usuario = u.id_usuario " +
                     "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado " +
                     "ORDER BY s.fecha_solicitud DESC, s.id_solicitud DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public List<SolicitudAusencia> listarPorUsuario(Connection conn, int idUsuario) throws SQLException {
        List<SolicitudAusencia> lista = new ArrayList<>();
        String sql = "SELECT s.*, COALESCE(CONCAT(e.nombres, ' ', e.apellidos), u.username) AS nombre_emp " +
                     "FROM solicitud_ausencia s " +
                     "JOIN usuario u ON s.id_usuario = u.id_usuario " +
                     "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado " +
                     "WHERE s.id_usuario = ? " +
                     "ORDER BY s.fecha_solicitud DESC, s.id_solicitud DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public void actualizarEstado(Connection conn, int idSolicitud, String nuevoEstado, String observacion) throws SQLException {
        String sql = "UPDATE solicitud_ausencia SET estado = ?, observacion_gerente = ? WHERE id_solicitud = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setString(2, observacion);
            ps.setInt(3, idSolicitud);
            ps.executeUpdate();
        }
    }

    private SolicitudAusencia mapear(ResultSet rs) throws SQLException {
        SolicitudAusencia s = new SolicitudAusencia();
        s.setIdSolicitud(rs.getInt("id_solicitud"));
        s.setIdUsuario(rs.getInt("id_usuario"));
        s.setNombreUsuario(rs.getString("nombre_emp"));
        s.setTipo(rs.getString("tipo"));
        Date fIni = rs.getDate("fecha_inicio");
        if (fIni != null) s.setFechaInicio(fIni.toLocalDate());
        Date fFin = rs.getDate("fecha_fin");
        if (fFin != null) s.setFechaFin(fFin.toLocalDate());
        s.setMotivo(rs.getString("motivo"));
        s.setEstado(rs.getString("estado"));
        s.setObservacionGerente(rs.getString("observacion_gerente"));
        Timestamp ts = rs.getTimestamp("fecha_solicitud");
        if (ts != null) s.setFechaSolicitud(ts.toLocalDateTime());
        return s;
    }
}
