package com.foodcore.dao.impl;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.model.RegistroAuditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de bitácora inmutable de auditoría (Audit Trail) (§5.2).
 */
public class AuditoriaDAOImpl implements AuditoriaDAO {

    @Override
    public void registrarEvento(Connection conn, int idUsuario, String accion, String entidadAfectada,
                                String datosAnteriores, String datosNuevos) throws SQLException {
        String sql = "INSERT INTO auditoria (id_usuario, accion, entidad_afectada, datos_anteriores, datos_nuevos) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario > 0 ? idUsuario : 1);
            ps.setString(2, accion);
            ps.setString(3, entidadAfectada);
            ps.setString(4, datosAnteriores);
            ps.setString(5, datosNuevos);
            ps.executeUpdate();
        }
    }

    @Override
    public List<RegistroAuditoria> listarUltimosEventos(Connection conn, int limite) throws SQLException {
        List<RegistroAuditoria> lista = new ArrayList<>();
        String sql = "SELECT a.id_auditoria, a.id_usuario, COALESCE(u.username, 'admin') as username, " +
                     "a.accion, a.entidad_afectada, a.datos_anteriores, a.datos_nuevos, a.fecha " +
                     "FROM auditoria a LEFT JOIN usuario u ON a.id_usuario = u.id_usuario " +
                     "ORDER BY a.fecha DESC, a.id_auditoria DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite > 0 ? limite : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("fecha");
                    RegistroAuditoria reg = new RegistroAuditoria(
                        rs.getLong("id_auditoria"),
                        rs.getInt("id_usuario"),
                        rs.getString("username"),
                        rs.getString("accion"),
                        rs.getString("entidad_afectada"),
                        rs.getString("datos_anteriores"),
                        rs.getString("datos_nuevos"),
                        ts != null ? ts.toLocalDateTime() : null
                    );
                    lista.add(reg);
                }
            }
        }
        return lista;
    }
}
