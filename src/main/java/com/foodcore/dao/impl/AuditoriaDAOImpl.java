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
import java.util.Locale;
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

    private static final String FILTRO_WHERE_SQL =
            "WHERE (? = '' OR UPPER(COALESCE(u.username, 'admin')) = UPPER(?)) "
            + "AND (? = '' "
            + "     OR (? = 'ANULACION' AND UPPER(a.accion) LIKE '%ANUL%') "
            + "     OR (? IN ('LOGIN', 'VENTA', 'DESCUENTO', 'DEVOLUCION') "
            + "         AND (UPPER(a.accion) = ? OR UPPER(a.accion) LIKE CONCAT('%', ?, '%'))) "
            + "     OR UPPER(a.accion) = ?) "
            + "AND (? = 0 OR a.fecha >= FROM_UNIXTIME(?)) "
            + "AND (? = 0 OR a.fecha < FROM_UNIXTIME(?)) ";

    private static final String SQL_LISTAR_FILTRADO =
            "SELECT a.id_auditoria, a.id_usuario, COALESCE(u.username, 'admin') AS username, "
            + "a.accion, a.entidad_afectada, a.datos_anteriores, a.datos_nuevos, a.fecha "
            + "FROM auditoria a LEFT JOIN usuario u ON a.id_usuario = u.id_usuario "
            + FILTRO_WHERE_SQL
            + "ORDER BY a.fecha DESC, a.id_auditoria DESC LIMIT ? OFFSET ?";

    private static final String SQL_CONTAR_FILTRADO =
            "SELECT COUNT(*) FROM auditoria a LEFT JOIN usuario u ON a.id_usuario = u.id_usuario "
            + FILTRO_WHERE_SQL;

    @Override
    public List<RegistroAuditoria> listarUltimosEventos(Connection conn, int limite) throws SQLException {
        return listarEventosFiltrados(conn, null, null, null, null, 0, limite > 0 ? limite : 100);
    }

    @Override
    public List<RegistroAuditoria> listarEventosFiltrados(Connection conn, String usuario, String accion,
                                                          LocalDate desde, LocalDate hasta,
                                                          int offset, int limit) throws SQLException {
        List<RegistroAuditoria> lista = new ArrayList<>();
        if (conn == null) {
            return lista;
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_FILTRADO)) {
            int idx = asignarParametrosFiltro(ps, usuario, accion, desde, hasta);
            ps.setInt(idx++, limit > 0 ? limit : 50);
            ps.setInt(idx, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearRegistro(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public int contarEventosFiltrados(Connection conn, String usuario, String accion,
                                      LocalDate desde, LocalDate hasta) throws SQLException {
        if (conn == null) {
            return 0;
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_CONTAR_FILTRADO)) {
            asignarParametrosFiltro(ps, usuario, accion, desde, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public List<String> listarUsuariosDistinct(Connection conn) throws SQLException {
        List<String> usuarios = new ArrayList<>();
        if (conn == null) {
            return usuarios;
        }
        String sql = "SELECT DISTINCT COALESCE(u.username, 'admin') AS username "
                + "FROM auditoria a LEFT JOIN usuario u ON a.id_usuario = u.id_usuario "
                + "ORDER BY username ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String u = rs.getString("username");
                if (u != null && !u.isBlank()) {
                    usuarios.add(u);
                }
            }
        }
        return usuarios;
    }

    @Override
    public List<String> listarAccionesDistinct(Connection conn) throws SQLException {
        List<String> acciones = new ArrayList<>();
        if (conn == null) {
            return acciones;
        }
        String sql = "SELECT DISTINCT a.accion FROM auditoria a WHERE a.accion IS NOT NULL ORDER BY a.accion ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String acc = rs.getString("accion");
                if (acc != null && !acc.isBlank()) {
                    acciones.add(acc);
                }
            }
        }
        return acciones;
    }

    private static RegistroAuditoria mapearRegistro(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha");
        return new RegistroAuditoria(
            rs.getLong("id_auditoria"),
            rs.getInt("id_usuario"),
            rs.getString("username"),
            rs.getString("accion"),
            rs.getString("entidad_afectada"),
            rs.getString("datos_anteriores"),
            rs.getString("datos_nuevos"),
            ts != null ? ts.toInstant().atZone(ZONA_LIMA).toLocalDateTime() : null
        );
    }

    private static int asignarParametrosFiltro(PreparedStatement ps, String usuario, String accion,
                                               LocalDate desde, LocalDate hasta) throws SQLException {
        String filtroUser = (usuario != null && !usuario.isBlank()
                && !usuario.trim().toLowerCase(Locale.ROOT).startsWith("todos"))
                ? usuario.trim() : "";
        String filtroAccion = (accion != null && !accion.isBlank()
                && !accion.trim().toLowerCase(Locale.ROOT).startsWith("todas"))
                ? accion.trim().toUpperCase(Locale.ROOT) : "";
        int usaDesde = desde != null ? 1 : 0;
        long epochDesde = desde != null ? desde.atStartOfDay(ZONA_LIMA).toEpochSecond() : 0L;
        int usaHasta = hasta != null ? 1 : 0;
        long epochHasta = hasta != null ? hasta.plusDays(1).atStartOfDay(ZONA_LIMA).toEpochSecond() : 0L;

        int i = 1;
        ps.setString(i++, filtroUser);
        ps.setString(i++, filtroUser);
        ps.setString(i++, filtroAccion);
        ps.setString(i++, filtroAccion);
        ps.setString(i++, filtroAccion);
        ps.setString(i++, filtroAccion);
        ps.setString(i++, filtroAccion);
        ps.setString(i++, filtroAccion);
        ps.setInt(i++, usaDesde);
        ps.setLong(i++, epochDesde);
        ps.setInt(i++, usaHasta);
        ps.setLong(i++, epochHasta);
        return i;
    }

    @Override
    public ResumenSeguridadHoy obtenerResumenSeguridadHoy(Connection conn) throws SQLException {
        if (conn == null) {
            return ResumenSeguridadHoy.normal();
        }
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        long inicioEpochSec = hoy.atStartOfDay(ZONA_LIMA).toEpochSecond();
        long finEpochSec = hoy.plusDays(1).atStartOfDay(ZONA_LIMA).toEpochSecond();

        int intentosFallidos = 0;
        int anulaciones = 0;

        String sqlConteo = "SELECT "
                + "COALESCE(SUM(CASE WHEN UPPER(accion) IN "
                + "('LOGIN_FALLIDO', '2FA_FALLIDO', 'USUARIO_BLOQUEADO') "
                + "THEN 1 ELSE 0 END), 0) AS fallidos, "
                + "COALESCE(SUM(CASE WHEN UPPER(accion) LIKE '%ANUL%' "
                + "THEN 1 ELSE 0 END), 0) AS anulaciones "
                + "FROM auditoria WHERE fecha >= FROM_UNIXTIME(?) AND fecha < FROM_UNIXTIME(?)";
        try (PreparedStatement ps = conn.prepareStatement(sqlConteo)) {
            ps.setLong(1, inicioEpochSec);
            ps.setLong(2, finEpochSec);
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
                String horaStr = ts != null
                        ? ts.toInstant().atZone(ZONA_LIMA).toLocalDateTime().format(FMT_SOSPECHOSO)
                        : "--/-- --:--";
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
    @Override
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
