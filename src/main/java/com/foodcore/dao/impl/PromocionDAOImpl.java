package com.foodcore.dao.impl;

import com.foodcore.dao.PromocionDAO;
import com.foodcore.model.Promocion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PromocionDAOImpl implements PromocionDAO {

    private static final String SELECT_BASE =
            "SELECT p.*, prod.nombre AS nombre_producto, cat.nombre AS nombre_categoria "
            + "FROM promocion p "
            + "LEFT JOIN producto prod ON p.id_producto_aplica = prod.id_producto "
            + "LEFT JOIN categoria cat ON p.id_categoria_aplica = cat.id_categoria ";

    @Override
    public List<Promocion> listarTodas(Connection conn) throws SQLException {
        String sql = SELECT_BASE + "ORDER BY p.activo DESC, p.id_promocion DESC";
        List<Promocion> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Promocion> listarActivas(Connection conn) throws SQLException {
        String sql = SELECT_BASE + "WHERE p.activo = 1 ORDER BY p.id_promocion ASC";
        List<Promocion> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public Optional<Promocion> buscarPorId(Connection conn, int idPromocion) throws SQLException {
        String sql = SELECT_BASE + "WHERE p.id_promocion = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPromocion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Promocion> buscarPorCodigo(Connection conn, String codigo) throws SQLException {
        String sql = SELECT_BASE + "WHERE p.codigo = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int crear(Connection conn, Promocion promo) throws SQLException {
        String sql = "INSERT INTO promocion (codigo, nombre, descripcion, tipo_promocion, valor_descuento, "
                + "aplica_a, id_producto_aplica, id_categoria_aplica, monto_minimo, dias_semana, "
                + "hora_inicio, hora_fin, activo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, promo.getCodigo());
            ps.setString(2, promo.getNombre());
            ps.setString(3, promo.getDescripcion());
            ps.setString(4, promo.getTipoPromocion());
            ps.setBigDecimal(5, promo.getValorDescuento());
            ps.setString(6, promo.getAplicaA());
            if (promo.getIdProductoAplica() != null && promo.getIdProductoAplica() > 0) {
                ps.setInt(7, promo.getIdProductoAplica());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            if (promo.getIdCategoriaAplica() != null && promo.getIdCategoriaAplica() > 0) {
                ps.setInt(8, promo.getIdCategoriaAplica());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
            ps.setBigDecimal(9, promo.getMontoMinimo());
            ps.setString(10, promo.getDiasSemana() != null ? promo.getDiasSemana() : "LUN,MAR,MIE,JUE,VIE,SAB,DOM");
            ps.setTime(11, promo.getHoraInicio() != null ? Time.valueOf(promo.getHoraInicio()) : null);
            ps.setTime(12, promo.getHoraFin() != null ? Time.valueOf(promo.getHoraFin()) : null);
            ps.setBoolean(13, promo.isActivo());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    promo.setIdPromocion(id);
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public boolean actualizar(Connection conn, Promocion promo) throws SQLException {
        String sql = "UPDATE promocion SET nombre = ?, descripcion = ?, tipo_promocion = ?, valor_descuento = ?, "
                + "aplica_a = ?, id_producto_aplica = ?, id_categoria_aplica = ?, monto_minimo = ?, "
                + "dias_semana = ?, hora_inicio = ?, hora_fin = ?, activo = ? "
                + "WHERE id_promocion = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, promo.getNombre());
            ps.setString(2, promo.getDescripcion());
            ps.setString(3, promo.getTipoPromocion());
            ps.setBigDecimal(4, promo.getValorDescuento());
            ps.setString(5, promo.getAplicaA());
            if (promo.getIdProductoAplica() != null && promo.getIdProductoAplica() > 0) {
                ps.setInt(6, promo.getIdProductoAplica());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            if (promo.getIdCategoriaAplica() != null && promo.getIdCategoriaAplica() > 0) {
                ps.setInt(7, promo.getIdCategoriaAplica());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            ps.setBigDecimal(8, promo.getMontoMinimo());
            ps.setString(9, promo.getDiasSemana());
            ps.setTime(10, promo.getHoraInicio() != null ? Time.valueOf(promo.getHoraInicio()) : null);
            ps.setTime(11, promo.getHoraFin() != null ? Time.valueOf(promo.getHoraFin()) : null);
            ps.setBoolean(12, promo.isActivo());
            ps.setInt(13, promo.getIdPromocion());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean cambiarEstado(Connection conn, int idPromocion, boolean activo) throws SQLException {
        String sql = "UPDATE promocion SET activo = ? WHERE id_promocion = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, activo);
            ps.setInt(2, idPromocion);
            return ps.executeUpdate() > 0;
        }
    }

    private Promocion mapear(ResultSet rs) throws SQLException {
        Promocion p = new Promocion();
        p.setIdPromocion(rs.getInt("id_promocion"));
        p.setCodigo(rs.getString("codigo"));
        p.setNombre(rs.getString("nombre"));
        p.setDescripcion(rs.getString("descripcion"));
        p.setTipoPromocion(rs.getString("tipo_promocion"));
        p.setValorDescuento(rs.getBigDecimal("valor_descuento"));
        p.setAplicaA(rs.getString("aplica_a"));

        int idProd = rs.getInt("id_producto_aplica");
        p.setIdProductoAplica(rs.wasNull() ? null : idProd);
        p.setNombreProductoAplica(rs.getString("nombre_producto"));

        int idCat = rs.getInt("id_categoria_aplica");
        p.setIdCategoriaAplica(rs.wasNull() ? null : idCat);
        p.setNombreCategoriaAplica(rs.getString("nombre_categoria"));

        p.setMontoMinimo(rs.getBigDecimal("monto_minimo"));
        p.setDiasSemana(rs.getString("dias_semana"));

        Time hi = rs.getTime("hora_inicio");
        if (hi != null) {
            p.setHoraInicio(hi.toLocalTime());
        }
        Time hf = rs.getTime("hora_fin");
        if (hf != null) {
            p.setHoraFin(hf.toLocalTime());
        }

        p.setActivo(rs.getBoolean("activo"));
        if (rs.getTimestamp("fecha_creacion") != null) {
            p.setFechaCreacion(rs.getTimestamp("fecha_creacion").toLocalDateTime());
        }
        return p;
    }
}
