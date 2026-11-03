package com.foodcore.events;

import com.foodcore.model.OutboxEvento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio transaccional de eventos de salida {@code outbox_evento}
 * según SPEC-FOODCORE-v1.0 (§11; REMEDIATION R-13, R-02.6).
 */
public class OutboxRepository {

    public long insertar(Connection conn, String tipoEvento, String payloadJson) throws SQLException {
        OutboxEvento ev = new OutboxEvento();
        ev.setTipoEvento(tipoEvento);
        ev.setPayloadJson(payloadJson);
        ev.setEstado("PENDIENTE");
        ev.setIntentos(0);
        return insertar(conn, ev);
    }

    public long insertar(Connection conn, OutboxEvento evento) throws SQLException {
        String sql = "INSERT INTO outbox_evento (tipo_evento, payload_json, estado, intentos, proximo_intento, creado_en) "
            + "VALUES (?, ?, 'PENDIENTE', 0, NOW(), NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, evento.getTipoEvento());
            ps.setString(2, evento.getPayloadJson());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    evento.setIdEvento(id);
                    evento.setEstado("PENDIENTE");
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo insertar el evento en outbox_evento.");
    }

    public List<OutboxEvento> listarPendientesParaDespacho(Connection conn) throws SQLException {
        String sql = "SELECT * FROM outbox_evento WHERE estado = 'PENDIENTE' AND proximo_intento <= NOW() ORDER BY id_evento ASC";
        List<OutboxEvento> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public List<OutboxEvento> listarTodosPendientes(Connection conn) throws SQLException {
        String sql = "SELECT * FROM outbox_evento WHERE estado = 'PENDIENTE' ORDER BY id_evento ASC";
        List<OutboxEvento> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public Optional<OutboxEvento> obtenerPorId(Connection conn, long idEvento) throws SQLException {
        String sql = "SELECT * FROM outbox_evento WHERE id_evento = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEvento);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void marcarEnviado(Connection conn, long idEvento) throws SQLException {
        String sql = "UPDATE outbox_evento SET estado = 'ENVIADO', enviado_en = NOW() WHERE id_evento = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idEvento);
            ps.executeUpdate();
        }
    }

    public void registrarIntentoFallido(Connection conn,
                                        long idEvento,
                                        int nuevoIntento,
                                        long segundosBackoff,
                                        boolean marcarFallido) throws SQLException {
        String sql = marcarFallido
            ? "UPDATE outbox_evento SET intentos = ?, estado = 'FALLIDO', proximo_intento = DATE_ADD(NOW(), INTERVAL ? SECOND) WHERE id_evento = ?"
            : "UPDATE outbox_evento SET intentos = ?, proximo_intento = DATE_ADD(NOW(), INTERVAL ? SECOND) WHERE id_evento = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nuevoIntento);
            ps.setLong(2, segundosBackoff);
            ps.setLong(3, idEvento);
            ps.executeUpdate();
        }
    }

    private OutboxEvento mapear(ResultSet rs) throws SQLException {
        Timestamp prox = rs.getTimestamp("proximo_intento");
        Timestamp creado = rs.getTimestamp("creado_en");
        Timestamp enviado = rs.getTimestamp("enviado_en");
        return new OutboxEvento(
            rs.getLong("id_evento"),
            rs.getString("tipo_evento"),
            rs.getString("payload_json"),
            rs.getString("estado"),
            rs.getInt("intentos"),
            prox != null ? prox.toLocalDateTime() : null,
            creado != null ? creado.toLocalDateTime() : null,
            enviado != null ? enviado.toLocalDateTime() : null
        );
    }
}
