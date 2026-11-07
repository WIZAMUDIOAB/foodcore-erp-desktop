package com.foodcore.dao.impl;

import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.model.HistorialPrecio;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HistorialPrecioDAOImpl implements HistorialPrecioDAO {

    @Override
    public List<HistorialPrecio> listarTodos(Connection conn) throws SQLException {
        String sql = "SELECT * FROM historial_precios ORDER BY fecha_cambio DESC";
        List<HistorialPrecio> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearHistorial(rs));
            }
        }
        return lista;
    }

    @Override
    public List<HistorialPrecio> listarPorEntidad(Connection conn, String tipoEntidad, int idEntidad) throws SQLException {
        String sql = "SELECT * FROM historial_precios WHERE tipo_entidad = ? AND id_entidad = ? ORDER BY fecha_cambio DESC";
        List<HistorialPrecio> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tipoEntidad);
            ps.setInt(2, idEntidad);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearHistorial(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<HistorialPrecio> listarPorRangoFecha(Connection conn, LocalDate inicio, LocalDate fin) throws SQLException {
        String sql = "SELECT * FROM historial_precios WHERE DATE(fecha_cambio) BETWEEN ? AND ? ORDER BY fecha_cambio DESC";
        List<HistorialPrecio> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(inicio));
            ps.setDate(2, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearHistorial(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public int insertar(Connection conn, HistorialPrecio historial) throws SQLException {
        String sql = "INSERT INTO historial_precios (tipo_entidad, id_entidad, nombre_entidad, precio_anterior, "
                + "precio_nuevo, id_usuario, nombre_usuario, motivo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, historial.getTipoEntidad());
            ps.setInt(2, historial.getIdEntidad());
            ps.setString(3, historial.getNombreEntidad());
            ps.setDouble(4, historial.getPrecioAnterior());
            ps.setDouble(5, historial.getPrecioNuevo());
            ps.setInt(6, historial.getIdUsuario());
            ps.setString(7, historial.getNombreUsuario());
            ps.setString(8, historial.getMotivo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    historial.setIdHistorial(rs.getInt(1));
                    return historial.getIdHistorial();
                }
            }
        }
        return 0;
    }

    @Override
    public double obtenerPrecioPromedioInsumo(Connection conn, int idInsumo) throws SQLException {
        String sql = "SELECT AVG(precio_nuevo) FROM historial_precios WHERE tipo_entidad = 'INSUMO' AND id_entidad = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idInsumo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double prom = rs.getDouble(1);
                    if (!rs.wasNull()) {
                        return prom;
                    }
                }
            }
        }
        return 0.0;
    }

    private HistorialPrecio mapearHistorial(ResultSet rs) throws SQLException {
        HistorialPrecio h = new HistorialPrecio();
        h.setIdHistorial(rs.getInt("id_historial"));
        h.setTipoEntidad(rs.getString("tipo_entidad"));
        h.setIdEntidad(rs.getInt("id_entidad"));
        h.setNombreEntidad(rs.getString("nombre_entidad"));
        h.setPrecioAnterior(rs.getDouble("precio_anterior"));
        h.setPrecioNuevo(rs.getDouble("precio_nuevo"));
        h.setIdUsuario(rs.getInt("id_usuario"));
        h.setNombreUsuario(rs.getString("nombre_usuario"));
        h.setMotivo(rs.getString("motivo"));
        if (rs.getTimestamp("fecha_cambio") != null) {
            h.setFechaCambio(rs.getTimestamp("fecha_cambio").toLocalDateTime());
        }
        return h;
    }
}
