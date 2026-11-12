package com.foodcore.dao.impl;

import com.foodcore.dao.MesaDAO;
import com.foodcore.model.MesaUnion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MesaDAOImpl implements MesaDAO {

    @Override
    public Map<String, String> obtenerEstadoTodasMesas(Connection conn) throws SQLException {
        Map<String, String> mapa = new HashMap<>();
        String sql = "SELECT numero, estado FROM mesa";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getString("numero"), rs.getString("estado"));
            }
        }
        return mapa;
    }

    @Override
    public boolean transferirPedidoMesa(Connection conn, String numeroOrigen, String numeroDestino, int idUsuario) throws SQLException {
        if (numeroOrigen == null || numeroDestino == null || numeroOrigen.equalsIgnoreCase(numeroDestino)) {
            throw new IllegalArgumentException("Las mesas de origen y destino deben ser distintas");
        }

        // 1. Obtener pedido activo en mesa origen (por id_mesa o numero_mesa)
        long idMesaOrigen = 0;
        try (PreparedStatement psOri = conn.prepareStatement("SELECT id_mesa FROM mesa WHERE numero = ?")) {
            psOri.setString(1, numeroOrigen);
            try (ResultSet rsOri = psOri.executeQuery()) {
                if (rsOri.next()) {
                    idMesaOrigen = rsOri.getLong("id_mesa");
                }
            }
        }

        int idPedido = 0;
        String sqlPed = "SELECT id_pedido FROM pedido WHERE (numero_mesa = ? OR (? > 0 AND id_mesa = ?)) "
                + "AND estado NOT IN ('COBRADO', 'CERRADO', 'PAGADO', 'CANCELADO', 'ANULADO') ORDER BY id_pedido DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlPed)) {
            ps.setString(1, numeroOrigen);
            ps.setLong(2, idMesaOrigen);
            ps.setLong(3, idMesaOrigen);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idPedido = rs.getInt("id_pedido");
                }
            }
        }

        if (idPedido <= 0) {
            throw new IllegalStateException("No existe comanda activa en la mesa " + numeroOrigen);
        }

        // 2. Obtener id_mesa de mesa destino
        long idMesaDestino = 0;
        String sqlMesaDest = "SELECT id_mesa, estado FROM mesa WHERE numero = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlMesaDest)) {
            ps.setString(1, numeroDestino);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idMesaDestino = rs.getLong("id_mesa");
                }
            }
        }

        if (idMesaDestino <= 0) {
            idMesaDestino = Long.parseLong(numeroDestino.replaceAll("[^0-9]", ""));
        }

        // 3. Actualizar pedido con nueva mesa
        String sqlUpdPed = "UPDATE pedido SET numero_mesa = ?, id_mesa = ? WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUpdPed)) {
            ps.setString(1, numeroDestino);
            ps.setLong(2, idMesaDestino);
            ps.setInt(3, idPedido);
            ps.executeUpdate();
        }

        // 4. Liberar mesa origen y ocupar mesa destino
        try (PreparedStatement ps = conn.prepareStatement("UPDATE mesa SET estado = 'LIBRE', id_pedido_actual = NULL WHERE numero = ?")) {
            ps.setString(1, numeroOrigen);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement("UPDATE mesa SET estado = 'OCUPADA', id_pedido_actual = ? WHERE numero = ?")) {
            ps.setInt(1, idPedido);
            ps.setString(2, numeroDestino);
            ps.executeUpdate();
        }

        // 5. Registrar en auditoría
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO auditoria (id_usuario, accion, tabla_afectada, registro_id, detalle) VALUES (?, 'TRANSFERIR_MESA', 'pedido', ?, ?)")) {
            ps.setInt(1, idUsuario > 0 ? idUsuario : 1);
            ps.setInt(2, idPedido);
            ps.setString(3, "Comanda #" + idPedido + " transferida de Mesa " + numeroOrigen + " a Mesa " + numeroDestino);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Si auditoria no existe o falla, no abortar la transferencia operativa
        }

        return true;
    }

    @Override
    public boolean unirMesas(Connection conn, String numeroPrincipal, List<String> numerosSecundarios, int idUsuario) throws SQLException {
        if (numeroPrincipal == null || numerosSecundarios == null || numerosSecundarios.isEmpty()) {
            throw new IllegalArgumentException("Debe especificar la mesa principal y al menos una secundaria");
        }

        // 1. Obtener pedido activo en mesa principal
        int idPedido = 0;
        long idMesaPrincipal = 0;
        String sqlPed = "SELECT id_pedido, id_mesa FROM pedido WHERE numero_mesa = ? AND estado NOT IN ('PAGADO', 'CANCELADO', 'ANULADO') ORDER BY id_pedido DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlPed)) {
            ps.setString(1, numeroPrincipal);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idPedido = rs.getInt("id_pedido");
                    idMesaPrincipal = rs.getLong("id_mesa");
                }
            }
        }

        if (idMesaPrincipal <= 0) {
            idMesaPrincipal = Long.parseLong(numeroPrincipal.replaceAll("[^0-9]", ""));
        }

        // Asegurar mesa principal con id_pedido_actual y estado OCUPADA
        try (PreparedStatement ps = conn.prepareStatement("UPDATE mesa SET estado = 'OCUPADA', id_pedido_actual = ? WHERE id_mesa = ? OR numero = ?")) {
            ps.setInt(1, idPedido);
            ps.setLong(2, idMesaPrincipal);
            ps.setString(3, numeroPrincipal);
            ps.executeUpdate();
        }

        // 2. Asociar cada mesa secundaria
        for (String sec : numerosSecundarios) {
            if (sec.equalsIgnoreCase(numeroPrincipal)) continue;

            long idMesaSec = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id_mesa FROM mesa WHERE numero = ?")) {
                ps.setString(1, sec);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idMesaSec = rs.getLong("id_mesa");
                }
            }
            if (idMesaSec <= 0) {
                idMesaSec = Long.parseLong(sec.replaceAll("[^0-9]", ""));
            }

            // Ocupar mesa secundaria marcando id_mesa_principal
            try (PreparedStatement ps = conn.prepareStatement("UPDATE mesa SET estado = 'OCUPADA', id_mesa_principal = ?, id_pedido_actual = ? WHERE numero = ?")) {
                ps.setLong(1, idMesaPrincipal);
                ps.setInt(2, idPedido);
                ps.setString(3, sec);
                ps.executeUpdate();
            }

            // Registrar en mesa_union
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO mesa_union (id_mesa_principal, id_mesa_secundaria, id_pedido, activo) VALUES (?, ?, ?, 1)")) {
                ps.setLong(1, idMesaPrincipal);
                ps.setLong(2, idMesaSec);
                ps.setInt(3, idPedido);
                ps.executeUpdate();
            }
        }

        // 3. Auditoría
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO auditoria (id_usuario, accion, tabla_afectada, registro_id, detalle) VALUES (?, 'UNIR_MESAS', 'mesa', ?, ?)")) {
            ps.setInt(1, idUsuario > 0 ? idUsuario : 1);
            ps.setLong(2, idMesaPrincipal);
            ps.setString(3, "Mesa " + numeroPrincipal + " unida con mesas: " + String.join(", ", numerosSecundarios));
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }

        return true;
    }

    @Override
    public boolean liberarMesasUnidasPorPedido(Connection conn, int idPedido) throws SQLException {
        // 1. Liberar secundarias vinculadas en mesa_union
        String sqlSec = "UPDATE mesa m JOIN mesa_union u ON m.id_mesa = u.id_mesa_secundaria "
                + "SET m.estado = 'LIBRE', m.id_mesa_principal = NULL, m.id_pedido_actual = NULL "
                + "WHERE u.id_pedido = ? AND u.activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlSec)) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }

        // 2. Liberar principal vinculada en mesa_union
        String sqlPrincUnion = "UPDATE mesa m JOIN mesa_union u ON m.id_mesa = u.id_mesa_principal "
                + "SET m.estado = 'LIBRE', m.id_mesa_principal = NULL, m.id_pedido_actual = NULL "
                + "WHERE u.id_pedido = ? AND u.activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlPrincUnion)) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }

        // 3. Liberar mesa referenciada directamente en pedido
        String sqlPrincPed = "UPDATE mesa m JOIN pedido p ON (m.id_mesa = p.id_mesa OR m.numero = p.numero_mesa) "
                + "SET m.estado = 'LIBRE', m.id_mesa_principal = NULL, m.id_pedido_actual = NULL "
                + "WHERE p.id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlPrincPed)) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }

        // 4. Fallback directo por id_pedido_actual
        String sqlFallback = "UPDATE mesa SET estado = 'LIBRE', id_mesa_principal = NULL, id_pedido_actual = NULL WHERE id_pedido_actual = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlFallback)) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }

        // 5. Desactivar uniones
        try (PreparedStatement ps = conn.prepareStatement("UPDATE mesa_union SET activo = 0 WHERE id_pedido = ?")) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }
        return true;
    }

    @Override
    public List<MesaUnion> listarUnionesActivas(Connection conn) throws SQLException {
        String sql = "SELECT u.*, mp.numero AS num_p, ms.numero AS num_s "
                + "FROM mesa_union u "
                + "JOIN mesa mp ON u.id_mesa_principal = mp.id_mesa "
                + "JOIN mesa ms ON u.id_mesa_secundaria = ms.id_mesa "
                + "WHERE u.activo = 1";
        List<MesaUnion> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                MesaUnion mu = new MesaUnion();
                mu.setIdUnion(rs.getInt("id_union"));
                mu.setIdMesaPrincipal(rs.getLong("id_mesa_principal"));
                mu.setIdMesaSecundaria(rs.getLong("id_mesa_secundaria"));
                mu.setNumeroMesaPrincipal(rs.getString("num_p"));
                mu.setNumeroMesaSecundaria(rs.getString("num_s"));
                mu.setIdPedido(rs.getInt("id_pedido"));
                mu.setActivo(rs.getBoolean("activo"));
                lista.add(mu);
            }
        }
        return lista;
    }

    @Override
    public List<String> listarMesasSecundariasDePedido(Connection conn, int idPedido) throws SQLException {
        List<String> res = new ArrayList<>();
        String sql = "SELECT ms.numero FROM mesa_union u JOIN mesa ms ON u.id_mesa_secundaria = ms.id_mesa WHERE u.id_pedido = ? AND u.activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    res.add(rs.getString("numero"));
                }
            }
        }
        return res;
    }
}
