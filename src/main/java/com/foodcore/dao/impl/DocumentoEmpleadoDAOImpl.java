package com.foodcore.dao.impl;

import com.foodcore.dao.DocumentoEmpleadoDAO;
import com.foodcore.model.DocumentoEmpleado;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC de {@link DocumentoEmpleadoDAO}.
 */
public class DocumentoEmpleadoDAOImpl implements DocumentoEmpleadoDAO {

    private static final String BASE_SELECT =
            "SELECT d.id_documento, d.id_usuario, d.tipo_documento, d.nombre_archivo, d.ruta_archivo, "
                    + "d.fecha_emision, d.fecha_vencimiento, d.observaciones, d.subido_en, "
                    + "COALESCE(NULLIF(TRIM(u.nombre_completo), ''), "
                    + "NULLIF(TRIM(CONCAT(COALESCE(e.nombres, ''), ' ', COALESCE(e.apellidos, ''))), ''), "
                    + "u.username) AS nombre_empleado, "
                    + "COALESCE(NULLIF(TRIM(u.cargo), ''), r.nombre, 'Personal') AS cargo_empleado "
                    + "FROM documento_empleado d "
                    + "JOIN usuario u ON d.id_usuario = u.id_usuario "
                    + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                    + "LEFT JOIN empleado e ON u.id_empleado = e.id_empleado ";

    @Override
    public long insertar(Connection conn, DocumentoEmpleado doc) throws SQLException {
        String sql = "INSERT INTO documento_empleado "
                + "(id_usuario, tipo_documento, nombre_archivo, ruta_archivo, "
                + "fecha_emision, fecha_vencimiento, observaciones) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, doc.getIdUsuario());
            ps.setString(2, doc.getTipoDocumento());
            ps.setString(3, doc.getNombreArchivo());
            ps.setString(4, doc.getRutaArchivo());
            if (doc.getFechaEmision() != null) {
                ps.setDate(5, Date.valueOf(doc.getFechaEmision()));
            } else {
                ps.setNull(5, Types.DATE);
            }
            if (doc.getFechaVencimiento() != null) {
                ps.setDate(6, Date.valueOf(doc.getFechaVencimiento()));
            } else {
                ps.setNull(6, Types.DATE);
            }
            ps.setString(7, doc.getObservaciones());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    doc.setIdDocumento(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se generó ID para documento_empleado.");
    }

    @Override
    public List<DocumentoEmpleado> listarPorUsuario(Connection conn, int idUsuario) throws SQLException {
        String sql = BASE_SELECT + "WHERE d.id_usuario = ? ORDER BY d.subido_en DESC, d.id_documento DESC";
        List<DocumentoEmpleado> lista = new ArrayList<>();
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
    public Optional<DocumentoEmpleado> buscarPorId(Connection conn, long idDocumento) throws SQLException {
        String sql = BASE_SELECT + "WHERE d.id_documento = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idDocumento);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean eliminar(Connection conn, long idDocumento) throws SQLException {
        String sql = "DELETE FROM documento_empleado WHERE id_documento = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idDocumento);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<DocumentoEmpleado> listarProximosAVencer(Connection conn, int dias) throws SQLException {
        int ventanaDias = Math.max(0, dias);
        String sql = BASE_SELECT
                + "WHERE u.activo = 1 "
                + "AND d.fecha_vencimiento IS NOT NULL "
                + "AND d.fecha_vencimiento BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL ? DAY) "
                + "ORDER BY d.fecha_vencimiento ASC, nombre_empleado ASC";
        List<DocumentoEmpleado> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ventanaDias);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    private DocumentoEmpleado mapear(ResultSet rs) throws SQLException {
        DocumentoEmpleado doc = new DocumentoEmpleado();
        doc.setIdDocumento(rs.getLong("id_documento"));
        doc.setIdUsuario(rs.getInt("id_usuario"));
        doc.setTipoDocumento(rs.getString("tipo_documento"));
        doc.setNombreArchivo(rs.getString("nombre_archivo"));
        doc.setRutaArchivo(rs.getString("ruta_archivo"));
        Date fEmision = rs.getDate("fecha_emision");
        if (fEmision != null) {
            doc.setFechaEmision(fEmision.toLocalDate());
        }
        Date fVenc = rs.getDate("fecha_vencimiento");
        if (fVenc != null) {
            doc.setFechaVencimiento(fVenc.toLocalDate());
        }
        doc.setObservaciones(rs.getString("observaciones"));
        Timestamp subido = rs.getTimestamp("subido_en");
        if (subido != null) {
            doc.setSubidoEn(subido.toLocalDateTime());
        }
        doc.setNombreEmpleado(rs.getString("nombre_empleado"));
        doc.setCargoEmpleado(rs.getString("cargo_empleado"));
        return doc;
    }
}
