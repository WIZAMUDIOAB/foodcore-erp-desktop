package com.foodcore.dao.impl;

import com.foodcore.dao.ProductoPersonalizacionDAO;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementación JDBC de {@link ProductoPersonalizacionDAO} (v4.8.0).
 */
public class ProductoPersonalizacionDAOImpl implements ProductoPersonalizacionDAO {

    @Override
    public List<ProductoVariante> listarVariantesPorProducto(Connection conn, int idProducto) throws SQLException {
        List<ProductoVariante> lista = new ArrayList<>();
        String sql = "SELECT id_variante, id_producto, nombre_variante, precio_fijo, precio_adicional, orden, activo "
                + "FROM producto_variante WHERE id_producto = ? AND activo = 1 ORDER BY orden ASC, id_variante ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearVariante(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Map<Integer, List<ProductoVariante>> listarVariantesPorProductos(Connection conn) throws SQLException {
        Map<Integer, List<ProductoVariante>> mapa = new LinkedHashMap<>();
        String sql = "SELECT id_variante, id_producto, nombre_variante, precio_fijo, precio_adicional, orden, activo "
                + "FROM producto_variante WHERE activo = 1 ORDER BY id_producto ASC, orden ASC, id_variante ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ProductoVariante pv = mapearVariante(rs);
                mapa.computeIfAbsent(pv.getIdProducto(), k -> new ArrayList<>()).add(pv);
            }
        }
        return mapa;
    }

    @Override
    public int crearVariante(Connection conn, ProductoVariante variante) throws SQLException {
        String sql = "INSERT INTO producto_variante (id_producto, nombre_variante, precio_fijo, precio_adicional, orden, activo) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, variante.getIdProducto());
            ps.setString(2, variante.getNombreVariante().trim());
            if (variante.getPrecioFijo() != null) {
                ps.setBigDecimal(3, variante.getPrecioFijo().setScale(2, RoundingMode.HALF_UP));
            } else {
                ps.setNull(3, Types.DECIMAL);
            }
            BigDecimal adic = variante.getPrecioAdicional() != null
                    ? variante.getPrecioAdicional().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            ps.setBigDecimal(4, adic);
            ps.setInt(5, variante.getOrden());
            ps.setBoolean(6, variante.isActivo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    variante.setIdVariante(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo generar el ID de la variante de producto.");
    }

    @Override
    public void reemplazarVariantesProducto(Connection conn, int idProducto, List<ProductoVariante> variantes) throws SQLException {
        try (PreparedStatement psDeact = conn.prepareStatement(
                "UPDATE producto_variante SET activo = 0 WHERE id_producto = ?")) {
            psDeact.setInt(1, idProducto);
            psDeact.executeUpdate();
        }
        if (variantes == null || variantes.isEmpty()) {
            return;
        }
        String sqlUpdate = "UPDATE producto_variante SET nombre_variante = ?, precio_fijo = ?, precio_adicional = ?, orden = ?, activo = 1 "
                + "WHERE id_variante = ? AND id_producto = ?";
        int orden = 1;
        for (ProductoVariante v : variantes) {
            if (v == null || v.getNombreVariante() == null || v.getNombreVariante().isBlank()) {
                continue;
            }
            v.setIdProducto(idProducto);
            v.setOrden(orden++);
            v.setActivo(true);
            int actualizados = 0;
            if (v.getIdVariante() > 0) {
                try (PreparedStatement psUpd = conn.prepareStatement(sqlUpdate)) {
                    psUpd.setString(1, v.getNombreVariante().trim());
                    if (v.getPrecioFijo() != null) {
                        psUpd.setBigDecimal(2, v.getPrecioFijo().setScale(2, RoundingMode.HALF_UP));
                    } else {
                        psUpd.setNull(2, Types.DECIMAL);
                    }
                    BigDecimal adic = v.getPrecioAdicional() != null
                            ? v.getPrecioAdicional().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    psUpd.setBigDecimal(3, adic);
                    psUpd.setInt(4, v.getOrden());
                    psUpd.setInt(5, v.getIdVariante());
                    psUpd.setInt(6, idProducto);
                    actualizados = psUpd.executeUpdate();
                }
            }
            if (actualizados == 0) {
                crearVariante(conn, v);
            }
        }
    }

    @Override
    public void eliminarVariante(Connection conn, int idVariante) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE producto_variante SET activo = 0 WHERE id_variante = ?")) {
            ps.setInt(1, idVariante);
            ps.executeUpdate();
        }
    }

    @Override
    public List<ProductoModificador> listarModificadoresGlobales(Connection conn) throws SQLException {
        List<ProductoModificador> lista = new ArrayList<>();
        String sql = "SELECT id_modificador, nombre, precio, activo FROM producto_modificador WHERE activo = 1 ORDER BY nombre ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearModificador(rs));
            }
        }
        return lista;
    }

    @Override
    public ProductoModificador guardarModificadorGlobal(Connection conn, ProductoModificador modificador) throws SQLException {
        if (modificador == null || modificador.getNombre() == null || modificador.getNombre().isBlank()) {
            throw new SQLException("El nombre del modificador es obligatorio.");
        }
        String nombreLimpio = modificador.getNombre().trim();
        BigDecimal precioNorm = modificador.getPrecioNormalizado();
        if (modificador.getIdModificador() > 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE producto_modificador SET nombre = ?, precio = ?, activo = 1 WHERE id_modificador = ?")) {
                ps.setString(1, nombreLimpio);
                ps.setBigDecimal(2, precioNorm);
                ps.setInt(3, modificador.getIdModificador());
                if (ps.executeUpdate() > 0) {
                    modificador.setNombre(nombreLimpio);
                    modificador.setPrecio(precioNorm);
                    modificador.setActivo(true);
                    return modificador;
                }
            }
        }

        try (PreparedStatement psFind = conn.prepareStatement(
                "SELECT id_modificador FROM producto_modificador WHERE LOWER(nombre) = LOWER(?) LIMIT 1")) {
            psFind.setString(1, nombreLimpio);
            try (ResultSet rs = psFind.executeQuery()) {
                if (rs.next()) {
                    int idExistente = rs.getInt("id_modificador");
                    try (PreparedStatement psReact = conn.prepareStatement(
                            "UPDATE producto_modificador SET nombre = ?, precio = ?, activo = 1 WHERE id_modificador = ?")) {
                        psReact.setString(1, nombreLimpio);
                        psReact.setBigDecimal(2, precioNorm);
                        psReact.setInt(3, idExistente);
                        psReact.executeUpdate();
                    }
                    modificador.setIdModificador(idExistente);
                    modificador.setNombre(nombreLimpio);
                    modificador.setPrecio(precioNorm);
                    modificador.setActivo(true);
                    return modificador;
                }
            }
        }

        String sqlIns = "INSERT INTO producto_modificador (nombre, precio, activo) VALUES (?, ?, 1)";
        try (PreparedStatement psIns = conn.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
            psIns.setString(1, nombreLimpio);
            psIns.setBigDecimal(2, precioNorm);
            psIns.executeUpdate();
            try (ResultSet rs = psIns.getGeneratedKeys()) {
                if (rs.next()) {
                    modificador.setIdModificador(rs.getInt(1));
                }
            }
        }
        modificador.setNombre(nombreLimpio);
        modificador.setPrecio(precioNorm);
        modificador.setActivo(true);
        return modificador;
    }

    @Override
    public void desactivarModificadorGlobal(Connection conn, int idModificador) throws SQLException {
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM producto_modificador_asociado WHERE id_modificador = ?")) {
            psDel.setInt(1, idModificador);
            psDel.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE producto_modificador SET activo = 0 WHERE id_modificador = ?")) {
            ps.setInt(1, idModificador);
            ps.executeUpdate();
        }
    }

    @Override
    public List<ProductoModificador> listarModificadoresPorProducto(Connection conn, int idProducto) throws SQLException {
        List<ProductoModificador> lista = new ArrayList<>();
        String sql = "SELECT m.id_modificador, m.nombre, m.precio, m.activo "
                + "FROM producto_modificador m "
                + "JOIN producto_modificador_asociado pma ON m.id_modificador = pma.id_modificador "
                + "WHERE pma.id_producto = ? AND m.activo = 1 "
                + "ORDER BY pma.orden ASC, m.nombre ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearModificador(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public Map<Integer, List<ProductoModificador>> listarModificadoresPorProductos(Connection conn) throws SQLException {
        Map<Integer, List<ProductoModificador>> mapa = new LinkedHashMap<>();
        String sql = "SELECT pma.id_producto, m.id_modificador, m.nombre, m.precio, m.activo "
                + "FROM producto_modificador_asociado pma "
                + "JOIN producto_modificador m ON m.id_modificador = pma.id_modificador "
                + "WHERE m.activo = 1 "
                + "ORDER BY pma.id_producto ASC, pma.orden ASC, m.nombre ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idProd = rs.getInt("id_producto");
                mapa.computeIfAbsent(idProd, k -> new ArrayList<>()).add(mapearModificador(rs));
            }
        }
        return mapa;
    }

    @Override
    public void reemplazarModificadoresAsociados(Connection conn, int idProducto, List<Integer> idsModificadores) throws SQLException {
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM producto_modificador_asociado WHERE id_producto = ?")) {
            psDel.setInt(1, idProducto);
            psDel.executeUpdate();
        }
        if (idsModificadores == null || idsModificadores.isEmpty()) {
            return;
        }
        Set<Integer> unicos = new LinkedHashSet<>();
        for (Integer id : idsModificadores) {
            if (id != null && id > 0) {
                unicos.add(id);
            }
        }
        if (unicos.isEmpty()) {
            return;
        }
        String sqlIns = "INSERT INTO producto_modificador_asociado (id_producto, id_modificador, orden) VALUES (?, ?, ?)";
        try (PreparedStatement psIns = conn.prepareStatement(sqlIns)) {
            int orden = 1;
            for (Integer idMod : unicos) {
                psIns.setInt(1, idProducto);
                psIns.setInt(2, idMod);
                psIns.setInt(3, orden++);
                psIns.addBatch();
            }
            psIns.executeBatch();
        }
    }

    @Override
    public void insertarDetallePedidoModificadores(Connection conn,
                                                   int idDetallePedido,
                                                   List<ProductoModificador> modificadores) throws SQLException {
        if (idDetallePedido <= 0 || modificadores == null || modificadores.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO detalle_pedido_modificador (id_detalle_pedido, id_modificador, nombre_modificador, precio_aplicado) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ProductoModificador mod : modificadores) {
                if (mod == null || mod.getIdModificador() <= 0) {
                    continue;
                }
                ps.setInt(1, idDetallePedido);
                ps.setInt(2, mod.getIdModificador());
                ps.setString(3, mod.getNombre() != null ? mod.getNombre().trim() : "Extra");
                ps.setBigDecimal(4, mod.getPrecioNormalizado());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public Map<Integer, List<ProductoModificador>> listarModificadoresPorPedido(Connection conn, int idPedido) throws SQLException {
        Map<Integer, List<ProductoModificador>> mapa = new LinkedHashMap<>();
        String sql = "SELECT dpm.id_detalle_pedido, dpm.id_modificador, dpm.nombre_modificador, dpm.precio_aplicado "
                + "FROM detalle_pedido_modificador dpm "
                + "JOIN detalle_pedido dp ON dp.id_detalle_pedido = dpm.id_detalle_pedido "
                + "WHERE dp.id_pedido = ? ORDER BY dpm.id_detalle_modificador ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    agregarModificadorDetalleAMapa(mapa, rs);
                }
            }
        }
        return mapa;
    }

    @Override
    public Map<Integer, List<ProductoModificador>> listarModificadoresDetallesActivosCocina(Connection conn) throws SQLException {
        Map<Integer, List<ProductoModificador>> mapa = new LinkedHashMap<>();
        String sql = "SELECT dpm.id_detalle_pedido, dpm.id_modificador, dpm.nombre_modificador, dpm.precio_aplicado "
                + "FROM detalle_pedido_modificador dpm "
                + "JOIN detalle_pedido dp ON dp.id_detalle_pedido = dpm.id_detalle_pedido "
                + "JOIN pedido ped ON dp.id_pedido = ped.id_pedido "
                + "WHERE dp.estado_cocina IN ('PENDIENTE', 'EN_PREPARACION', 'LISTO') "
                + "AND ped.estado IN ('ABIERTO', 'PENDIENTE', 'REGISTRADO', 'EN_PREPARACION', 'LISTO', 'ITEMS_ADICIONALES') "
                + "ORDER BY dpm.id_detalle_modificador ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                agregarModificadorDetalleAMapa(mapa, rs);
            }
        }
        return mapa;
    }

    private void agregarModificadorDetalleAMapa(Map<Integer, List<ProductoModificador>> mapa, ResultSet rs) throws SQLException {
        int idDetalle = rs.getInt("id_detalle_pedido");
        ProductoModificador mod = new ProductoModificador(
                rs.getInt("id_modificador"),
                rs.getString("nombre_modificador"),
                rs.getBigDecimal("precio_aplicado"),
                true
        );
        mapa.computeIfAbsent(idDetalle, k -> new ArrayList<>()).add(mod);
    }

    private ProductoVariante mapearVariante(ResultSet rs) throws SQLException {
        return new ProductoVariante(
                rs.getInt("id_variante"),
                rs.getInt("id_producto"),
                rs.getString("nombre_variante"),
                rs.getBigDecimal("precio_fijo"),
                rs.getBigDecimal("precio_adicional"),
                rs.getInt("orden"),
                rs.getBoolean("activo")
        );
    }

    private ProductoModificador mapearModificador(ResultSet rs) throws SQLException {
        return new ProductoModificador(
                rs.getInt("id_modificador"),
                rs.getString("nombre"),
                rs.getBigDecimal("precio"),
                rs.getBoolean("activo")
        );
    }
}
