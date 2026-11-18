package com.foodcore.dao.impl;

import com.foodcore.dao.VacacionesDAO;
import com.foodcore.model.Vacaciones;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Implementación JDBC de {@link VacacionesDAO}.
 */
public class VacacionesDAOImpl implements VacacionesDAO {

    @Override
    public Optional<Vacaciones> buscarPorUsuarioYAnio(Connection conn, int idUsuario, int anio) throws SQLException {
        String sql = "SELECT v.id_vacacion, v.id_usuario, v.periodo_anio, v.dias_asignados, "
                + "v.dias_gozados, v.fecha_corte, v.observaciones, "
                + "COALESCE((SELECT SUM(s.dias_solicitados) FROM solicitud_ausencia s "
                + "WHERE s.id_usuario = v.id_usuario AND s.tipo = 'VACACIONES' "
                + "AND s.estado = 'PENDIENTE' AND YEAR(s.fecha_inicio) = v.periodo_anio), 0) AS dias_pendientes "
                + "FROM vacaciones v "
                + "WHERE v.id_usuario = ? AND v.periodo_anio = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, anio);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Vacaciones v = new Vacaciones();
                    v.setIdVacacion(rs.getLong("id_vacacion"));
                    v.setIdUsuario(rs.getInt("id_usuario"));
                    v.setPeriodoAnio(rs.getInt("periodo_anio"));
                    v.setDiasAsignados(rs.getInt("dias_asignados"));
                    v.setDiasGozados(rs.getInt("dias_gozados"));
                    v.setDiasPendientesAprobacion(rs.getInt("dias_pendientes"));
                    Date corte = rs.getDate("fecha_corte");
                    if (corte != null) {
                        v.setFechaCorte(corte.toLocalDate());
                    }
                    v.setObservaciones(rs.getString("observaciones"));
                    return Optional.of(v);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void guardarOActualizar(Connection conn, Vacaciones vac) throws SQLException {
        String sql = "INSERT INTO vacaciones "
                + "(id_usuario, periodo_anio, dias_asignados, dias_gozados, fecha_corte, observaciones) "
                + "VALUES (?, ?, ?, ?, ?, ?) AS new_val "
                + "ON DUPLICATE KEY UPDATE "
                + "dias_asignados = new_val.dias_asignados, "
                + "dias_gozados = new_val.dias_gozados, "
                + "fecha_corte = new_val.fecha_corte, "
                + "observaciones = new_val.observaciones";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vac.getIdUsuario());
            ps.setInt(2, vac.getPeriodoAnio());
            ps.setInt(3, vac.getDiasAsignados());
            ps.setInt(4, vac.getDiasGozados());
            ps.setDate(5, Date.valueOf(vac.getFechaCorte() != null ? vac.getFechaCorte() : LocalDate.now()));
            ps.setString(6, vac.getObservaciones());
            ps.executeUpdate();
        }
    }

    @Override
    public void incrementarDiasGozados(Connection conn, int idUsuario, int anio, int dias) throws SQLException {
        String sql = "UPDATE vacaciones SET dias_gozados = dias_gozados + ?, fecha_corte = CURRENT_DATE "
                + "WHERE id_usuario = ? AND periodo_anio = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, dias));
            ps.setInt(2, idUsuario);
            ps.setInt(3, anio);
            ps.executeUpdate();
        }
    }
}
