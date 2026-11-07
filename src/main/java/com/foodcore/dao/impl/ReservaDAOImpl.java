package com.foodcore.dao.impl;

import com.foodcore.dao.ReservaDAO;
import com.foodcore.model.ReservaMesa;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservaDAOImpl implements ReservaDAO {

    @Override
    public int crearReserva(Connection conn, ReservaMesa r) throws SQLException {
        String sql = "INSERT INTO reserva_mesa (id_mesa, cliente_nombre, cliente_telefono, fecha, hora, personas, estado, notas, id_usuario_registro) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.getIdMesa());
            ps.setString(2, r.getClienteNombre());
            ps.setString(3, r.getClienteTelefono());
            ps.setDate(4, Date.valueOf(r.getFecha()));
            ps.setTime(5, Time.valueOf(r.getHora()));
            ps.setInt(6, r.getPersonas() > 0 ? r.getPersonas() : 2);
            ps.setString(7, r.getEstado() != null ? r.getEstado() : "CONFIRMADA");
            ps.setString(8, r.getNotas());
            if (r.getIdUsuarioRegistro() != null && r.getIdUsuarioRegistro() > 0) {
                ps.setInt(9, r.getIdUsuarioRegistro());
            } else {
                ps.setNull(9, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    r.setIdReserva(id);
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public List<ReservaMesa> listarTodas(Connection conn) throws SQLException {
        String sql = "SELECT DISTINCT r.*, m.numero AS numero_mesa FROM reserva_mesa r "
                + "JOIN mesa m ON r.id_mesa = m.id_mesa "
                + "ORDER BY r.fecha DESC, r.hora ASC";
        List<ReservaMesa> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearReserva(rs));
            }
        }
        return lista;
    }

    @Override
    public List<ReservaMesa> listarPorFecha(Connection conn, LocalDate fecha) throws SQLException {
        String sql = "SELECT r.*, m.numero AS numero_mesa FROM reserva_mesa r "
                + "JOIN mesa m ON r.id_mesa = m.id_mesa "
                + "WHERE r.fecha = ? "
                + "ORDER BY r.hora ASC";
        List<ReservaMesa> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearReserva(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Optional<ReservaMesa> buscarReservaProximaParaMesa(Connection conn, String numeroMesa, LocalDate fecha, LocalTime horaActual, int minutosMargen) throws SQLException {
        LocalTime inicio = horaActual.minusMinutes(15);
        LocalTime fin = horaActual.plusMinutes(minutosMargen);

        String condHora = inicio.isAfter(fin) ? "(r.hora >= ? OR r.hora <= ?)" : "(r.hora BETWEEN ? AND ?)";
        String sql = "SELECT r.*, m.numero AS numero_mesa FROM reserva_mesa r "
                + "JOIN mesa m ON r.id_mesa = m.id_mesa "
                + "WHERE m.numero = ? AND r.fecha = ? AND r.estado IN ('CONFIRMADA', 'PENDIENTE') "
                + "AND " + condHora + " "
                + "ORDER BY r.hora ASC LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroMesa);
            ps.setDate(2, Date.valueOf(fecha));
            ps.setTime(3, Time.valueOf(inicio));
            ps.setTime(4, Time.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearReserva(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean actualizarEstado(Connection conn, int idReserva, String nuevoEstado) throws SQLException {
        String sql = "UPDATE reserva_mesa SET estado = ? WHERE id_reserva = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idReserva);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean eliminarReserva(Connection conn, int idReserva) throws SQLException {
        String sql = "DELETE FROM reserva_mesa WHERE id_reserva = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            return ps.executeUpdate() > 0;
        }
    }

    private ReservaMesa mapearReserva(ResultSet rs) throws SQLException {
        ReservaMesa r = new ReservaMesa();
        r.setIdReserva(rs.getInt("id_reserva"));
        r.setIdMesa(rs.getLong("id_mesa"));
        r.setNumeroMesa(rs.getString("numero_mesa"));
        r.setClienteNombre(rs.getString("cliente_nombre"));
        r.setClienteTelefono(rs.getString("cliente_telefono"));
        Date f = rs.getDate("fecha");
        if (f != null) r.setFecha(f.toLocalDate());
        Time h = rs.getTime("hora");
        if (h != null) r.setHora(h.toLocalTime());
        r.setPersonas(rs.getInt("personas"));
        r.setEstado(rs.getString("estado"));
        r.setNotas(rs.getString("notas"));
        int u = rs.getInt("id_usuario_registro");
        if (!rs.wasNull()) r.setIdUsuarioRegistro(u);
        return r;
    }
}
