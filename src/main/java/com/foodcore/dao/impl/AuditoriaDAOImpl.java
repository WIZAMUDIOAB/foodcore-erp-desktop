package com.foodcore.dao.impl;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.model.ResumenSeguridadHoy;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación de bitácora inmutable de auditoría (Audit Trail) (§5.2).
 */
public class AuditoriaDAOImpl implements AuditoriaDAO {

    private static final Logger LOGGER = Logger.getLogger(AuditoriaDAOImpl.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FMT_SOSPECHOSO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

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

    @Override
    public ResumenSeguridadHoy obtenerResumenSeguridadHoy(Connection conn) throws SQLException {
        if (conn == null) {
            return ResumenSeguridadHoy.normal();
        }
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        Timestamp inicio = Timestamp.valueOf(hoy.atStartOfDay());
        Timestamp fin = Timestamp.valueOf(hoy.plusDays(1).atStartOfDay());

        int intentosFallidos = 0;
        int anulaciones = 0;

        String sqlConteo = "SELECT "
                + "COALESCE(SUM(CASE WHEN UPPER(accion) IN "
                + "('LOGIN_FALLIDO', '2FA_FALLIDO', 'USUARIO_BLOQUEADO') "
                + "THEN 1 ELSE 0 END), 0) AS fallidos, "
                + "COALESCE(SUM(CASE WHEN UPPER(accion) LIKE '%ANUL%' "
                + "THEN 1 ELSE 0 END), 0) AS anulaciones "
                + "FROM auditoria WHERE fecha >= ? AND fecha < ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlConteo)) {
            ps.setTimestamp(1, inicio);
            ps.setTimestamp(2, fin);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    intentosFallidos = rs.getInt("fallidos");
                    anulaciones = rs.getInt("anulaciones");
                }
            }
        }

        String ultimoSospechoso = "Sin eventos sospechosos";
        boolean haySospechoso = false;
        String sqlUltimo = "SELECT COALESCE(u.username, 'sistema') AS username, a.accion, a.datos_nuevos, a.fecha "
                + "FROM auditoria a LEFT JOIN usuario u ON a.id_usuario = u.id_usuario "
                + "WHERE UPPER(a.accion) IN ('LOGIN_FALLIDO', '2FA_FALLIDO', 'USUARIO_BLOQUEADO', "
                + "'ACCESO_SOSPECHOSO', 'FORCE_LOGOUT', 'TOKEN_REVOCADO') "
                + "ORDER BY a.fecha DESC, a.id_auditoria DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlUltimo);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp("fecha");
                String horaStr = ts != null ? ts.toLocalDateTime().format(FMT_SOSPECHOSO) : "--/-- --:--";
                String user = rs.getString("username");
                String acc = rs.getString("accion");
                String detalle = rs.getString("datos_nuevos");
                String resumenDetalle = detalle != null && !detalle.isBlank() ? " — " + truncar(detalle, 52) : "";
                ultimoSospechoso = horaStr + " · " + user + " (" + acc + ")" + resumenDetalle;
                haySospechoso = true;
            }
        }

        boolean atencion = intentosFallidos > 0 || anulaciones > 0 || haySospechoso;
        return new ResumenSeguridadHoy(intentosFallidos, anulaciones, ultimoSospechoso, atencion);
    }

    /**
     * Registra un evento de auditoría usando una conexión existente sin propagar errores
     * que puedan interrumpir flujos de autenticación o respuestas HTTP.
     */
    public void registrarEventoSeguro(Connection conn, int idUsuario, String accion, String entidadAfectada,
            String datosAnteriores, String datosNuevos) {
        if (conn == null) {
            return;
        }
        try {
            registrarEvento(conn, idUsuario, accion, entidadAfectada, datosAnteriores, datosNuevos);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se pudo registrar auditoría segura (" + accion + ")", ex);
        }
    }

    /**
     * Abre una conexión del pool y registra un evento de auditoría de forma best-effort.
     */
    public void registrarEventoBestEffort(int idUsuario, String accion, String entidadAfectada,
            String datosAnteriores, String datosNuevos) {
        try (Connection conn = ConexionBD.getConnection()) {
            registrarEventoSeguro(conn, idUsuario, accion, entidadAfectada, datosAnteriores, datosNuevos);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "Sin conexión para registrar evento de auditoría (" + accion + ")", ex);
        }
    }

    private static String truncar(String texto, int max) {
        if (texto == null || texto.length() <= max) {
            return texto == null ? "" : texto;
        }
        return texto.substring(0, Math.max(0, max - 3)) + "...";
    }
}
