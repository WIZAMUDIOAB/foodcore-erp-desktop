package com.foodcore.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.foodcore.dao.impl.ComboDAOImpl;
import com.foodcore.dao.impl.ProductoPersonalizacionDAOImpl;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.model.Combo;
import com.foodcore.model.Comanda;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.ProductoModificador;
import com.foodcore.util.FormatoPersonalizacionUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PedidoDAOImpl implements PedidoDAO {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private final ProductoPersonalizacionDAO personalizacionDAO = new ProductoPersonalizacionDAOImpl();
    private final ComboDAO comboDAO = new ComboDAOImpl();

    @Override
    public int crearPedido(Connection conn, Pedido pedido) throws SQLException {
        Integer idMesa = pedido.getIdMesa();
        if (idMesa == null && pedido.getNumeroMesa() != null && !pedido.getNumeroMesa().isBlank()) {
            idMesa = buscarIdMesaPorNumero(conn, pedido.getNumeroMesa().trim()).orElse(null);
            pedido.setIdMesa(idMesa);
        }
        String sql = "INSERT INTO pedido (id_usuario, id_cliente, id_mesa, numero_mesa, total, estado, idempotency_key, tipo_pedido, cliente_nombre, cliente_telefono, cliente_direccion, cliente_referencia, costo_envio, repartidor, id_usuario_creador) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pedido.getIdUsuario());
            if (pedido.getIdCliente() != null) {
                ps.setInt(2, pedido.getIdCliente());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            if (idMesa != null) {
                ps.setInt(3, idMesa);
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setString(4, pedido.getNumeroMesa());
            ps.setBigDecimal(5, pedido.getTotal());
            ps.setString(6, pedido.getEstado());
            ps.setString(7, pedido.getIdempotencyKey());
            ps.setString(8, pedido.getTipoPedido() != null ? pedido.getTipoPedido() : "MESA");
            ps.setString(9, pedido.getClienteNombre());
            ps.setString(10, pedido.getClienteTelefono());
            ps.setString(11, pedido.getClienteDireccion());
            ps.setString(12, pedido.getClienteReferencia());
            ps.setBigDecimal(13, pedido.getCostoEnvio() != null ? pedido.getCostoEnvio() : BigDecimal.ZERO);
            ps.setString(14, pedido.getRepartidor());
            if (pedido.getIdUsuarioCreador() != null) {
                ps.setInt(15, pedido.getIdUsuarioCreador());
            } else {
                ps.setInt(15, pedido.getIdUsuario());
            }

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    pedido.setIdPedido(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener el ID del pedido registrado.");
    }

    @Override
    public Optional<Pedido> buscarPorIdempotencyKey(Connection conn, String idempotencyKey) throws SQLException {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM pedido WHERE idempotency_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idempotencyKey.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Pedido> obtenerPorId(Connection conn, int idPedido) throws SQLException {
        String sql = "SELECT * FROM pedido WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }
        return Optional.empty();
    }

    private Pedido mapearPedido(ResultSet rs) throws SQLException {
        Pedido p = new Pedido();
        p.setIdPedido(rs.getInt("id_pedido"));
        p.setIdUsuario(rs.getInt("id_usuario"));
        int c = rs.getInt("id_cliente");
        p.setIdCliente(rs.wasNull() ? null : c);
        try {
            long idMesaVal = rs.getLong("id_mesa");
            p.setIdMesa(rs.wasNull() ? null : (int) idMesaVal);
        } catch (SQLException ignored) {
            // Compatibilidad con esquemas sin columna id_mesa
        }
        p.setNumeroMesa(rs.getString("numero_mesa"));
        Timestamp fa = rs.getTimestamp("fecha_hora");
        if (fa != null) {
            p.setFechaHora(fa.toLocalDateTime());
        }
        p.setEstado(rs.getString("estado"));
        p.setTotal(rs.getBigDecimal("total"));
        try {
            p.setIdempotencyKey(rs.getString("idempotency_key"));
        } catch (SQLException ignored) {
            // Compatibilidad
        }
        try {
            p.setTipoPedido(rs.getString("tipo_pedido"));
            p.setClienteNombre(rs.getString("cliente_nombre"));
            p.setClienteTelefono(rs.getString("cliente_telefono"));
            p.setClienteDireccion(rs.getString("cliente_direccion"));
            p.setClienteReferencia(rs.getString("cliente_referencia"));
            p.setCostoEnvio(rs.getBigDecimal("costo_envio"));
            p.setRepartidor(rs.getString("repartidor"));
            int idCreador = rs.getInt("id_usuario_creador");
            p.setIdUsuarioCreador(rs.wasNull() ? p.getIdUsuario() : idCreador);
        } catch (SQLException ignored) {}
        return p;
    }

    @Override
    public int insertarDetallePedido(Connection conn, DetallePedido detalle) throws SQLException {
        LocalDateTime enviadoEn = detalle.getEnviadoEn() != null ? detalle.getEnviadoEn() : LocalDateTime.now(ZONA_LIMA);
        String estadoCocina = detalle.getEstadoCocina() != null ? detalle.getEstadoCocina() : "PENDIENTE";
        String modsTexto = FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                detalle.getModificadoresSeleccionados(), detalle.getModificadoresTexto());
        if (!modsTexto.isEmpty()) {
            detalle.setModificadoresTexto(modsTexto);
        }
        completarComboDetallePedido(conn, detalle);
        String sql = "INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario, subtotal, " +
                     "notas_preparacion, estado_cocina, enviado_en, id_variante, nombre_variante, modificadores_texto, " +
                     "id_combo, combo_componentes_texto) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, detalle.getIdPedido());
            ps.setInt(2, detalle.getIdProducto());
            ps.setInt(3, detalle.getCantidad());
            ps.setBigDecimal(4, detalle.getPrecioUnitario());
            ps.setBigDecimal(5, detalle.getSubtotal());
            ps.setString(6, detalle.getNotasPreparacion() != null ? detalle.getNotasPreparacion() : "");
            ps.setString(7, estadoCocina);
            ps.setTimestamp(8, Timestamp.valueOf(enviadoEn));
            if (detalle.getIdVariante() != null && detalle.getIdVariante() > 0) {
                ps.setInt(9, detalle.getIdVariante());
            } else {
                ps.setNull(9, Types.INTEGER);
            }
            if (detalle.getNombreVariante() != null && !detalle.getNombreVariante().isBlank()) {
                ps.setString(10, detalle.getNombreVariante().trim());
            } else {
                ps.setNull(10, Types.VARCHAR);
            }
            if (!modsTexto.isEmpty()) {
                ps.setString(11, modsTexto);
            } else {
                ps.setNull(11, Types.VARCHAR);
            }
            if (detalle.getIdCombo() != null && detalle.getIdCombo() > 0) {
                ps.setInt(12, detalle.getIdCombo());
            } else {
                ps.setNull(12, Types.INTEGER);
            }
            if (detalle.getComboComponentesTexto() != null && !detalle.getComboComponentesTexto().isBlank()) {
                ps.setString(13, detalle.getComboComponentesTexto().trim());
            } else {
                ps.setNull(13, Types.VARCHAR);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    detalle.setIdDetallePedido(id);
                    personalizacionDAO.insertarDetallePedidoModificadores(
                            conn, id, detalle.getModificadoresSeleccionados());
                    return id;
                }
            }
        }
        return 0;
    }

    @Override
    public int crearComanda(Connection conn, Comanda comanda) throws SQLException {
        String sql = "INSERT INTO comanda (id_pedido, numero_comanda, estado, tiempo_estimado_min) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, comanda.getIdPedido());
            ps.setString(2, comanda.getNumeroComanda());
            ps.setString(3, comanda.getEstado());
            ps.setInt(4, comanda.getTiempoEstimadoMin());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    comanda.setIdComanda(id);
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo registrar la comanda para cocina.");
    }

    @Override
    public List<Pedido> listarPedidosActivos(Connection conn) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String sql = "SELECT * FROM pedido WHERE estado IN ('ABIERTO', 'PENDIENTE', 'REGISTRADO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'ITEMS_ADICIONALES', 'POR_COBRAR') ORDER BY id_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearPedido(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Pedido> listarPedidosPendientesCocina(Connection conn) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String sql = "SELECT * FROM pedido WHERE estado IN ('ABIERTO', 'PENDIENTE', 'REGISTRADO', 'EN_PREPARACION', 'LISTO', 'ITEMS_ADICIONALES') ORDER BY id_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearPedido(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Pedido> buscarPorEstado(Connection conn, String estado) throws SQLException {
        if (estado == null || estado.isBlank()) {
            return List.of();
        }
        List<Pedido> lista = new ArrayList<>();
        String sql = "SELECT * FROM pedido WHERE estado = ? ORDER BY id_pedido DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearPedido(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<Pedido> buscarPorEstadoYFechas(Connection conn, String estado, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        List<Pedido> lista = new ArrayList<>();
        String estadoNormalizado = (estado != null && !estado.isBlank()) ? estado.trim() : null;
        String sql = "SELECT * FROM pedido "
                + "WHERE (? IS NULL OR estado = ?) "
                + "  AND (? IS NULL OR fecha_hora >= ?) "
                + "  AND (? IS NULL OR fecha_hora <= ?) "
                + "ORDER BY fecha_hora DESC, id_pedido DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (estadoNormalizado != null) {
                ps.setString(1, estadoNormalizado);
                ps.setString(2, estadoNormalizado);
            } else {
                ps.setNull(1, Types.VARCHAR);
                ps.setNull(2, Types.VARCHAR);
            }
            if (desde != null) {
                Timestamp tsDesde = Timestamp.valueOf(desde);
                ps.setTimestamp(3, tsDesde);
                ps.setTimestamp(4, tsDesde);
            } else {
                ps.setNull(3, Types.TIMESTAMP);
                ps.setNull(4, Types.TIMESTAMP);
            }
            if (hasta != null) {
                Timestamp tsHasta = Timestamp.valueOf(hasta);
                ps.setTimestamp(5, tsHasta);
                ps.setTimestamp(6, tsHasta);
            } else {
                ps.setNull(5, Types.TIMESTAMP);
                ps.setNull(6, Types.TIMESTAMP);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearPedido(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<DetallePedido> listarDetallesPorPedido(Connection conn, int idPedido) throws SQLException {
        List<DetallePedido> lista = new ArrayList<>();
        String sql = "SELECT dp.*, p.nombre AS nombre_prod, ped.numero_mesa FROM detalle_pedido dp " +
                     "JOIN producto p ON dp.id_producto = p.id_producto " +
                     "JOIN pedido ped ON dp.id_pedido = ped.id_pedido " +
                     "WHERE dp.id_pedido = ? ORDER BY dp.id_detalle_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearDetallePedido(rs));
                }
            }
        }
        if (!lista.isEmpty()) {
            adjuntarModificadores(lista, personalizacionDAO.listarModificadoresPorPedido(conn, idPedido));
        }
        return lista;
    }

    /**
     * Lista los ítems activos de cocina ({@code PENDIENTE}, {@code EN_PREPARACION} o {@code LISTO})
     * con su marca temporal individual {@code enviado_en} (SPEC-FOODCORE-v1.0 §8.5; REMEDIATION R-08; v4.4.0).
     */
    @Override
    public List<DetallePedido> listarItemsPendientesCocina(Connection conn) throws SQLException {
        List<DetallePedido> lista = new ArrayList<>();
        String sql = "SELECT dp.*, p.nombre AS nombre_prod, ped.numero_mesa " +
                     "FROM detalle_pedido dp " +
                     "JOIN producto p ON dp.id_producto = p.id_producto " +
                     "JOIN pedido ped ON dp.id_pedido = ped.id_pedido " +
                     "WHERE dp.estado_cocina IN ('PENDIENTE', 'EN_PREPARACION', 'LISTO') " +
                     "AND ped.estado IN ('ABIERTO', 'PENDIENTE', 'REGISTRADO', 'EN_PREPARACION', 'LISTO', 'ITEMS_ADICIONALES') " +
                     "ORDER BY dp.prioridad DESC, dp.id_detalle_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearDetallePedido(rs));
            }
        }
        if (!lista.isEmpty()) {
            adjuntarModificadores(lista, personalizacionDAO.listarModificadoresDetallesActivosCocina(conn));
        }
        return lista;
    }

    private void adjuntarModificadores(List<DetallePedido> lista, Map<Integer, List<ProductoModificador>> modsPorDetalle) {
        for (DetallePedido dp : lista) {
            List<ProductoModificador> mods = modsPorDetalle.get(dp.getIdDetallePedido());
            if (mods != null && !mods.isEmpty()) {
                dp.setModificadoresSeleccionados(mods);
                if (dp.getModificadoresTexto() == null || dp.getModificadoresTexto().isBlank()) {
                    dp.setModificadoresTexto(FormatoPersonalizacionUtil.formatearTextoModificadores(mods));
                }
            }
        }
    }

    @Override
    public boolean tieneItemsPendientesCocina(Connection conn, int idPedido) throws SQLException {
        String sql = "SELECT COUNT(*) FROM detalle_pedido WHERE id_pedido = ? AND estado_cocina NOT IN ('ENTREGADO', 'CANCELADO')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private DetallePedido mapearDetallePedido(ResultSet rs) throws SQLException {
        DetallePedido dp = new DetallePedido();
        dp.setIdDetallePedido(rs.getInt("id_detalle_pedido"));
        dp.setIdPedido(rs.getInt("id_pedido"));
        dp.setIdProducto(rs.getInt("id_producto"));
        dp.setCantidad(rs.getInt("cantidad"));
        dp.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
        dp.setSubtotal(rs.getBigDecimal("subtotal"));
        dp.setNotasPreparacion(rs.getString("notas_preparacion"));
        dp.setNombreProducto(rs.getString("nombre_prod"));
        try {
            dp.setNumeroMesa(rs.getString("numero_mesa"));
            String estCocina = rs.getString("estado_cocina");
            if (estCocina != null) dp.setEstadoCocina(estCocina);
            Timestamp env = rs.getTimestamp("enviado_en");
            if (env != null) dp.setEnviadoEn(env.toLocalDateTime());
            Timestamp ini = rs.getTimestamp("iniciado_en");
            if (ini != null) dp.setIniciadoEn(ini.toLocalDateTime());
            Timestamp lst = rs.getTimestamp("listo_en");
            if (lst != null) dp.setListoEn(lst.toLocalDateTime());
            int idVar = rs.getInt("id_variante");
            if (!rs.wasNull() && idVar > 0) {
                dp.setIdVariante(idVar);
            }
            dp.setNombreVariante(rs.getString("nombre_variante"));
            dp.setModificadoresTexto(rs.getString("modificadores_texto"));
            int idCombo = rs.getInt("id_combo");
            if (!rs.wasNull() && idCombo > 0) {
                dp.setIdCombo(idCombo);
            }
            dp.setComboComponentesTexto(rs.getString("combo_componentes_texto"));
            dp.setPrioridad(rs.getInt("prioridad"));
        } catch (SQLException ignored) {
            // Columnas opcionales en esquemas previos
        }
        return dp;
    }

    private void completarComboDetallePedido(Connection conn, DetallePedido detalle) throws SQLException {
        if (detalle == null || detalle.getIdProducto() <= 0) {
            return;
        }
        if (detalle.getIdCombo() != null && detalle.getIdCombo() > 0
                && detalle.getComboComponentesTexto() != null && !detalle.getComboComponentesTexto().isBlank()) {
            return;
        }
        Optional<Combo> opt = (detalle.getIdCombo() != null && detalle.getIdCombo() > 0)
                ? comboDAO.obtenerPorId(conn, detalle.getIdCombo())
                : comboDAO.obtenerPorIdProducto(conn, detalle.getIdProducto());
        if (opt.isPresent()) {
            Combo combo = opt.get();
            if (detalle.getIdCombo() == null || detalle.getIdCombo() <= 0) {
                detalle.setIdCombo(combo.getIdCombo());
            }
            if (detalle.getComboComponentesTexto() == null || detalle.getComboComponentesTexto().isBlank()) {
                detalle.setComboComponentesTexto(
                        FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(combo.getDetalles()));
            }
        }
    }

    @Override
    public void actualizarEstadoPedido(Connection conn, int idPedido, String nuevoEstado) throws SQLException {
        String sql = "UPDATE pedido SET estado = ?, version = version + 1 WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idPedido);
            ps.executeUpdate();
        }

        String estadoComanda = "PENDIENTE";
        if ("EN_PREPARACION".equalsIgnoreCase(nuevoEstado)) {
            estadoComanda = "EN_PREPARACION";
        } else if ("LISTO".equalsIgnoreCase(nuevoEstado)) {
            estadoComanda = "LISTA";
        } else if ("COBRADO".equalsIgnoreCase(nuevoEstado) || "CERRADO".equalsIgnoreCase(nuevoEstado) || "ENTREGADO".equalsIgnoreCase(nuevoEstado)) {
            estadoComanda = "DESPACHADA";
        }
        String sqlComanda = "UPDATE comanda SET estado = ? WHERE id_pedido = ?";
        try (PreparedStatement psCom = conn.prepareStatement(sqlComanda)) {
            psCom.setString(1, estadoComanda);
            psCom.setInt(2, idPedido);
            psCom.executeUpdate();
        }
    }

    @Override
    public void actualizarEstadoDetalleCocina(Connection conn, int idDetallePedido, String nuevoEstado) throws SQLException {
        String sql;
        if ("EN_PREPARACION".equalsIgnoreCase(nuevoEstado)) {
            sql = "UPDATE detalle_pedido SET estado_cocina = ?, iniciado_en = ? WHERE id_detalle_pedido = ?";
        } else if ("LISTO".equalsIgnoreCase(nuevoEstado) || "ENTREGADO".equalsIgnoreCase(nuevoEstado)) {
            sql = "UPDATE detalle_pedido SET estado_cocina = ?, listo_en = ? WHERE id_detalle_pedido = ?";
        } else {
            sql = "UPDATE detalle_pedido SET estado_cocina = ?, enviado_en = ? WHERE id_detalle_pedido = ?";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado.toUpperCase());
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now(ZONA_LIMA)));
            ps.setInt(3, idDetallePedido);
            ps.executeUpdate();
        }
    }

    @Override
    public void actualizarPrioridadesCocina(Connection conn, List<Integer> idsDetalleOrdenDescendente) throws SQLException {
        if (idsDetalleOrdenDescendente == null || idsDetalleOrdenDescendente.isEmpty()) {
            return;
        }
        String sql = "UPDATE detalle_pedido SET prioridad = ? WHERE id_detalle_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int total = idsDetalleOrdenDescendente.size();
            for (int i = 0; i < total; i++) {
                int idDet = idsDetalleOrdenDescendente.get(i);
                if (idDet <= 0) {
                    continue;
                }
                int pesoPrioridad = (total - i) * 10;
                ps.setInt(1, pesoPrioridad);
                ps.setInt(2, idDet);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void actualizarEstadoComanda(Connection conn, int idComanda, String nuevoEstado) throws SQLException {
        String sql = "UPDATE comanda SET estado = ? WHERE id_comanda = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idComanda);
            ps.executeUpdate();
        }
    }

    /**
     * Bloqueo optimista sobre {@code mesa} según SPEC-FOODCORE-v1.0 (§8.4; REMEDIATION R-02.5):
     * {@code UPDATE mesa SET estado=?, version=version+1 WHERE id_mesa=? AND version=?}.
     */
    @Override
    public void actualizarEstadoMesaConVersion(Connection conn, long idMesa, String nuevoEstado, int versionEsperada) throws SQLException {
        String sql = "UPDATE mesa SET estado = ?, version = version + 1 WHERE id_mesa = ? AND version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setLong(2, idMesa);
            ps.setInt(3, versionEsperada);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException(new ConflictoEstadoException(
                    "Conflicto de concurrencia optimista en mesa #" + idMesa + " (versión esperada: " + versionEsperada + ")."
                ));
            }
        }
    }

    /**
     * Recalcula el total acumulado del pedido sumando todos sus {@code detalle_pedido}
     * (más {@code costo_envio} si aplica) y persiste el resultado en {@code pedido.total}.
     */
    @Override
    public BigDecimal calcularTotalPedido(Connection conn, int idPedido) throws SQLException {
        BigDecimal sumItems = BigDecimal.ZERO;
        String sqlItems = "SELECT COALESCE(SUM(subtotal), 0.00) FROM detalle_pedido WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlItems)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal val = rs.getBigDecimal(1);
                    if (val != null) {
                        sumItems = val;
                    }
                }
            }
        }

        BigDecimal costoEnvio = BigDecimal.ZERO;
        String sqlEnvio = "SELECT COALESCE(costo_envio, 0.00) FROM pedido WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlEnvio)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal val = rs.getBigDecimal(1);
                    if (val != null) {
                        costoEnvio = val;
                    }
                }
            }
        }

        BigDecimal total = sumItems.add(costoEnvio).setScale(2, RoundingMode.HALF_UP);
        String sqlUpd = "UPDATE pedido SET total = ? WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUpd)) {
            ps.setBigDecimal(1, total);
            ps.setInt(2, idPedido);
            ps.executeUpdate();
        }
        return total;
    }

    @Override
    public Optional<Pedido> buscarPedidoActivoPorMesa(Connection conn, long idMesa) throws SQLException {
        Optional<String> numeroMesa = obtenerNumeroMesaPorId(conn, idMesa);
        String sql = "SELECT * FROM pedido WHERE (id_mesa = ? OR (? IS NOT NULL AND numero_mesa = ?)) "
                + "AND estado NOT IN ('COBRADO', 'CERRADO', 'PAGADO', 'CANCELADO', 'ANULADO') "
                + "ORDER BY id_pedido DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMesa);
            if (numeroMesa.isPresent()) {
                ps.setString(2, numeroMesa.get());
                ps.setString(3, numeroMesa.get());
            } else {
                ps.setNull(2, Types.VARCHAR);
                ps.setNull(3, Types.VARCHAR);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Pedido> buscarPedidoActivoPorNumeroMesa(Connection conn, String numeroMesa) throws SQLException {
        if (numeroMesa == null || numeroMesa.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM pedido WHERE numero_mesa = ? "
                + "AND estado NOT IN ('COBRADO', 'CERRADO', 'PAGADO', 'CANCELADO', 'ANULADO') "
                + "ORDER BY id_pedido DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroMesa.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearPedido(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void moverPedidoAMesa(Connection conn, int idPedido, long idMesaDestino, String numeroMesaDestino) throws SQLException {
        String sql = "UPDATE pedido SET id_mesa = ?, numero_mesa = ? WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMesaDestino);
            ps.setString(2, numeroMesaDestino);
            ps.setInt(3, idPedido);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<String> obtenerNumeroMesaPorId(Connection conn, long idMesa) throws SQLException {
        String sql = "SELECT numero FROM mesa WHERE id_mesa = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idMesa);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("numero"));
                }
            }
        } catch (SQLException ignored) {
            // Compatibilidad si la tabla mesa aún no existe en esquema reducido
        }
        return Optional.empty();
    }

    @Override
    public void actualizarEstadoMesaDirecto(Connection conn, long idMesa, String estado, Integer idPedidoActual) throws SQLException {
        String sql = "UPDATE mesa SET estado = ?, id_pedido_actual = ?, version = version + 1 WHERE id_mesa = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            if (idPedidoActual != null) {
                ps.setInt(2, idPedidoActual);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setLong(3, idMesa);
            ps.executeUpdate();
        }
    }

    private Optional<Integer> buscarIdMesaPorNumero(Connection conn, String numeroMesa) {
        String sql = "SELECT id_mesa FROM mesa WHERE numero = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroMesa);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("id_mesa"));
                }
            }
        } catch (SQLException ignored) {
            // Si el número de mesa no corresponde a una fila en mesa (e.g. M01, DELIVERY), queda null
        }
        return Optional.empty();
    }
}
