package com.foodcore.dao.impl;

import com.foodcore.dao.TurnoProgramadoDAO;
import com.foodcore.model.TurnoProgramado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

public class TurnoProgramadoDAOImpl implements TurnoProgramadoDAO {

    @Override
    public List<TurnoProgramado> listarSemana(Connection conn) throws SQLException {
        String sql = "SELECT tp.*, u.username, COALESCE(u.nombre_completo, u.username) AS nombre_empleado, r.nombre AS rol_nombre "
                + "FROM turno_programado tp "
                + "JOIN usuario u ON tp.id_usuario = u.id_usuario "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "ORDER BY FIELD(tp.dia_semana, 'LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO'), "
                + "FIELD(tp.turno, 'MANANA', 'TARDE', 'NOCHE', 'PERSONALIZADO'), tp.hora_inicio ASC";

        List<TurnoProgramado> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearTurno(rs));
            }
        }
        return lista;
    }

    @Override
    public List<TurnoProgramado> listarPorUsuario(Connection conn, int idUsuario) throws SQLException {
        String sql = "SELECT tp.*, u.username, COALESCE(u.nombre_completo, u.username) AS nombre_empleado, r.nombre AS rol_nombre "
                + "FROM turno_programado tp "
                + "JOIN usuario u ON tp.id_usuario = u.id_usuario "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "WHERE tp.id_usuario = ? AND tp.publicado = 1 "
                + "ORDER BY FIELD(tp.dia_semana, 'LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO'), tp.hora_inicio ASC";

        List<TurnoProgramado> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearTurno(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public int asignarTurno(Connection conn, TurnoProgramado tp) throws SQLException {
        String sql = "INSERT INTO turno_programado (id_usuario, dia_semana, turno, hora_inicio, hora_fin, publicado) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, tp.getIdUsuario());
            ps.setString(2, tp.getDiaSemana().toUpperCase());
            ps.setString(3, tp.getTurno().toUpperCase());
            ps.setTime(4, Time.valueOf(tp.getHoraInicio()));
            ps.setTime(5, Time.valueOf(tp.getHoraFin()));
            ps.setBoolean(6, tp.isPublicado());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    tp.setIdTurnoProg(rs.getInt(1));
                    return tp.getIdTurnoProg();
                }
            }
        }
        return 0;
    }

    @Override
    public boolean eliminarTurno(Connection conn, int idTurnoProg) throws SQLException {
        String sql = "DELETE FROM turno_programado WHERE id_turno_prog = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTurnoProg);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public int contarCobertura(Connection conn, String diaSemana, String turno) throws SQLException {
        String sql = "SELECT COUNT(*) FROM turno_programado WHERE dia_semana = ? AND turno = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, diaSemana.toUpperCase());
            ps.setString(2, turno.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public boolean publicarHorariosSemana(Connection conn) throws SQLException {
        String sql = "UPDATE turno_programado SET publicado = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate() >= 0;
        }
    }

    private TurnoProgramado mapearTurno(ResultSet rs) throws SQLException {
        TurnoProgramado tp = new TurnoProgramado();
        tp.setIdTurnoProg(rs.getInt("id_turno_prog"));
        tp.setIdUsuario(rs.getInt("id_usuario"));
        tp.setDiaSemana(rs.getString("dia_semana"));
        tp.setTurno(rs.getString("turno"));
        tp.setHoraInicio(rs.getTime("hora_inicio").toLocalTime());
        tp.setHoraFin(rs.getTime("hora_fin").toLocalTime());
        tp.setPublicado(rs.getBoolean("publicado"));

        String nom = rs.getString("nombre_empleado");
        tp.setNombreUsuario(nom != null && !nom.isBlank() ? nom : rs.getString("username"));
        tp.setRol(rs.getString("rol_nombre"));
        return tp;
    }
}
