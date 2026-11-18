package com.foodcore.dao.impl;

import com.foodcore.dao.FidelizacionDAO;
import com.foodcore.model.CompraClienteResumen;
import com.foodcore.model.CuponCumpleanos;
import com.foodcore.model.FidelizacionMovimiento;
import com.foodcore.model.HistorialPuntos;

import java.math.BigDecimal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FidelizacionDAOImpl implements FidelizacionDAO {

    @Override
    public int registrarMovimiento(Connection conn, FidelizacionMovimiento mov) throws SQLException {
        String sql = "INSERT INTO fidelizacion_movimiento (id_cliente, id_venta, tipo, puntos, motivo, fecha_hora) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, mov.getIdCliente());
            if (mov.getIdVenta() != null && mov.getIdVenta() > 0) {
                boolean ventaExiste = false;
                try (PreparedStatement psV = conn.prepareStatement("SELECT 1 FROM venta WHERE id_venta = ?")) {
                    psV.setInt(1, mov.getIdVenta());
                    try (ResultSet rsV = psV.executeQuery()) {
                        ventaExiste = rsV.next();
                    }
                }
                if (ventaExiste) {
                    ps.setInt(2, mov.getIdVenta());
                } else {
                    ps.setNull(2, java.sql.Types.INTEGER);
                }
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, mov.getTipo());
            ps.setInt(4, mov.getPuntos());
            ps.setString(5, mov.getMotivo());
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(mov.getFechaHora()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    mov.setIdMovimiento(id);
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public long registrarHistorialPuntos(Connection conn, HistorialPuntos entry) throws SQLException {
        String sql = "INSERT INTO historial_puntos (id_cliente, id_venta, puntos_ganados, tipo_movimiento, descripcion, fecha) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, entry.getIdCliente());
            if (entry.getIdVenta() != null && entry.getIdVenta() > 0 && existeVenta(conn, entry.getIdVenta())) {
                ps.setInt(2, entry.getIdVenta());
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setInt(3, entry.getPuntosGanados());
            ps.setString(4, entry.getTipoMovimiento() != null ? entry.getTipoMovimiento() : "ACUMULACION");
            ps.setString(5, entry.getDescripcion());
            java.time.LocalDateTime fecha = entry.getFecha() != null ? entry.getFecha() : java.time.LocalDateTime.now();
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(fecha));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    entry.setIdHistorial(id);
                    return id;
                }
            }
        }
        return 0L;
    }

    private boolean existeVenta(Connection conn, int idVenta) throws SQLException {
        try (PreparedStatement psV = conn.prepareStatement("SELECT 1 FROM venta WHERE id_venta = ?")) {
            psV.setInt(1, idVenta);
            try (ResultSet rsV = psV.executeQuery()) {
                return rsV.next();
            }
        }
    }

    @Override
    public List<FidelizacionMovimiento> listarPorCliente(Connection conn, int idCliente) throws SQLException {
        String sql = "SELECT fm.*, c.razon_social, c.nombre_razon_social "
                + "FROM fidelizacion_movimiento fm "
                + "JOIN cliente c ON fm.id_cliente = c.id_cliente "
                + "WHERE fm.id_cliente = ? "
                + "ORDER BY fm.fecha_hora DESC";
        List<FidelizacionMovimiento> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FidelizacionMovimiento m = new FidelizacionMovimiento();
                    m.setIdMovimiento(rs.getInt("id_movimiento"));
                    m.setIdCliente(rs.getInt("id_cliente"));
                    String nom = rs.getString("razon_social");
                    m.setNombreCliente(nom != null ? nom : rs.getString("nombre_razon_social"));
                    int idV = rs.getInt("id_venta");
                    m.setIdVenta(rs.wasNull() ? null : idV);
                    m.setTipo(rs.getString("tipo"));
                    m.setPuntos(rs.getInt("puntos"));
                    m.setMotivo(rs.getString("motivo"));
                    if (rs.getTimestamp("fecha_hora") != null) {
                        m.setFechaHora(rs.getTimestamp("fecha_hora").toLocalDateTime());
                    }
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    @Override
    public List<HistorialPuntos> listarHistorialPuntos(Connection conn, int idCliente) throws SQLException {
        String sql = "SELECT id_historial, id_cliente, id_venta, puntos_ganados, tipo_movimiento, descripcion, fecha "
                + "FROM historial_puntos WHERE id_cliente = ? ORDER BY fecha DESC, id_historial DESC";
        List<HistorialPuntos> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HistorialPuntos hp = new HistorialPuntos();
                    hp.setIdHistorial(rs.getLong("id_historial"));
                    hp.setIdCliente(rs.getInt("id_cliente"));
                    int idV = rs.getInt("id_venta");
                    hp.setIdVenta(rs.wasNull() ? null : idV);
                    hp.setPuntosGanados(rs.getInt("puntos_ganados"));
                    hp.setTipoMovimiento(rs.getString("tipo_movimiento"));
                    hp.setDescripcion(rs.getString("descripcion"));
                    java.sql.Timestamp ts = rs.getTimestamp("fecha");
                    if (ts != null) {
                        hp.setFecha(ts.toLocalDateTime());
                    }
                    lista.add(hp);
                }
            }
        }
        return lista;
    }

    @Override
    public List<CompraClienteResumen> listarUltimasComprasCliente(Connection conn, int idCliente, int limite) throws SQLException {
        int lim = Math.max(1, Math.min(limite, 50));
        String sql = "SELECT v.id_venta, v.fecha_hora, v.total, v.metodo_pago, "
                + "COALESCE(GROUP_CONCAT(CONCAT(dv.cantidad, 'x ', COALESCE(p.nombre, 'Producto'), "
                + "CASE WHEN dv.nombre_variante IS NOT NULL AND dv.nombre_variante <> '' "
                + "THEN CONCAT(' (', dv.nombre_variante, ')') ELSE '' END) "
                + "ORDER BY dv.id_detalle_venta SEPARATOR ', '), 'Consumo POS') AS resumen_productos, "
                + "COALESCE((SELECT SUM(hp.puntos_ganados) FROM historial_puntos hp "
                + "          WHERE hp.id_venta = v.id_venta AND hp.id_cliente = ? AND hp.puntos_ganados > 0), 0) AS puntos_ganados "
                + "FROM venta v "
                + "LEFT JOIN pedido ped ON v.id_pedido = ped.id_pedido "
                + "LEFT JOIN detalle_venta dv ON v.id_venta = dv.id_venta "
                + "LEFT JOIN producto p ON dv.id_producto = p.id_producto "
                + "WHERE v.estado = 'COMPLETADA' AND COALESCE(v.id_cliente, ped.id_cliente) = ? "
                + "GROUP BY v.id_venta, v.fecha_hora, v.total, v.metodo_pago "
                + "ORDER BY v.fecha_hora DESC, v.id_venta DESC "
                + "LIMIT ?";
        List<CompraClienteResumen> compras = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.setInt(2, idCliente);
            ps.setInt(3, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CompraClienteResumen item = new CompraClienteResumen();
                    item.setIdVenta(rs.getInt("id_venta"));
                    java.sql.Timestamp ts = rs.getTimestamp("fecha_hora");
                    if (ts != null) {
                        item.setFechaHora(ts.toLocalDateTime());
                    }
                    BigDecimal total = rs.getBigDecimal("total");
                    item.setMontoTotal(total != null ? total : BigDecimal.ZERO);
                    item.setMetodoPago(rs.getString("metodo_pago"));
                    item.setResumenProductos(rs.getString("resumen_productos"));
                    item.setPuntosGanados(rs.getInt("puntos_ganados"));
                    compras.add(item);
                }
            }
        }
        return compras;
    }

    @Override
    public int obtenerPuntosActuales(Connection conn, int idCliente) throws SQLException {
        String sql = "SELECT puntos_acumulados, puntos_fidelidad FROM cliente WHERE id_cliente = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int pa = rs.getInt("puntos_acumulados");
                    return pa > 0 ? pa : rs.getInt("puntos_fidelidad");
                }
            }
        }
        return 0;
    }

    @Override
    public boolean actualizarPuntosCliente(Connection conn, int idCliente, int nuevosPuntos) throws SQLException {
        String sql = "UPDATE cliente SET puntos_acumulados = ?, puntos_fidelidad = ? WHERE id_cliente = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, nuevosPuntos);
            ps.setInt(2, nuevosPuntos);
            ps.setInt(3, idCliente);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<CuponCumpleanos> buscarCuponCumpleanosActivo(Connection conn, int idCliente, int mesActual) throws SQLException {
        String sql = "SELECT c.*, cli.razon_social, cli.nombre_razon_social "
                + "FROM cupon_cumpleanos c "
                + "JOIN cliente cli ON c.id_cliente = cli.id_cliente "
                + "WHERE c.id_cliente = ? AND c.mes_validez = ? AND c.usado = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.setInt(2, mesActual);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearCupon(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int generarCuponCumpleanos(Connection conn, CuponCumpleanos cupon) throws SQLException {
        String sql = "INSERT INTO cupon_cumpleanos (id_cliente, codigo_cupon, porcentaje_descuento, mes_validez, usado, fecha_generacion) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cupon.getIdCliente());
            ps.setString(2, cupon.getCodigoCupon());
            ps.setBigDecimal(3, cupon.getPorcentajeDescuento());
            ps.setInt(4, cupon.getMesValidez());
            ps.setBoolean(5, cupon.isUsado());
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(cupon.getFechaGeneracion()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    cupon.setIdCupon(id);
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public boolean marcarCuponUsado(Connection conn, String codigoCupon) throws SQLException {
        String sql = "UPDATE cupon_cumpleanos SET usado = 1 WHERE codigo_cupon = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigoCupon);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<CuponCumpleanos> buscarCuponPorCodigo(Connection conn, String codigoCupon) throws SQLException {
        String sql = "SELECT c.*, cli.razon_social, cli.nombre_razon_social "
                + "FROM cupon_cumpleanos c "
                + "JOIN cliente cli ON c.id_cliente = cli.id_cliente "
                + "WHERE c.codigo_cupon = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigoCupon);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearCupon(rs));
                }
            }
        }
        return Optional.empty();
    }

    private CuponCumpleanos mapearCupon(ResultSet rs) throws SQLException {
        CuponCumpleanos c = new CuponCumpleanos();
        c.setIdCupon(rs.getInt("id_cupon"));
        c.setIdCliente(rs.getInt("id_cliente"));
        String nom = rs.getString("razon_social");
        c.setNombreCliente(nom != null ? nom : rs.getString("nombre_razon_social"));
        c.setCodigoCupon(rs.getString("codigo_cupon"));
        c.setPorcentajeDescuento(rs.getBigDecimal("porcentaje_descuento"));
        c.setMesValidez(rs.getInt("mes_validez"));
        c.setUsado(rs.getBoolean("usado"));
        if (rs.getTimestamp("fecha_generacion") != null) {
            c.setFechaGeneracion(rs.getTimestamp("fecha_generacion").toLocalDateTime());
        }
        return c;
    }
}
