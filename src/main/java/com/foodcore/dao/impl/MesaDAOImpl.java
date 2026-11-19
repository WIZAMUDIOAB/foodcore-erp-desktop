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

    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();

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
        if (numeroOrigen == null || numeroDestino == null || numeroOrigen.isBlank()
                || numeroDestino.isBlank() || numeroOrigen.trim().equalsIgnoreCase(numeroDestino.trim())) {
            throw new IllegalArgumentException("Las mesas de origen y destino deben ser distintas");
        }

        String numOrigen = numeroOrigen.trim();
        String numDestino = numeroDestino.trim();

        // 1. Bloqueo pesimista ordenado por id_mesa sobre ambas mesas (previene deadlocks y sobreescritura)
        long idMesaOrigen = 0;
        long idMesaDestino = 0;
        String estadoDestino = null;
        String sqlLockMesas = "SELECT id_mesa, numero, estado FROM mesa WHERE numero IN (?, ?) ORDER BY id_mesa ASC FOR UPDATE";
        try (PreparedStatement psLock = conn.prepareStatement(sqlLockMesas)) {
            psLock.setString(1, numOrigen);
            psLock.setString(2, numDestino);
            try (ResultSet rs = psLock.executeQuery()) {
                while (rs.next()) {
                    String num = rs.getString("numero");
                    if (numOrigen.equalsIgnoreCase(num)) {
                        idMesaOrigen = rs.getLong("id_mesa");
                    } else if (numDestino.equalsIgnoreCase(num)) {
                        idMesaDestino = rs.getLong("id_mesa");
                        estadoDestino = rs.getString("estado");
                    }
                }
            }
        }

        if (idMesaOrigen <= 0) {
            throw new IllegalStateException("La mesa origen " + numOrigen + " no existe.");
        }
        if (idMesaDestino <= 0) {
            throw new IllegalStateException("La mesa destino " + numDestino + " no existe.");
        }
        if (!"LIBRE".equalsIgnoreCase(estadoDestino)) {
            throw new IllegalStateException(
                    "La mesa destino " + numDestino + " no está LIBRE (estado actual: " + estadoDestino + ").");
        }

        // 2. Obtener pedido activo en mesa origen (por id_mesa o numero_mesa)
        int idPedido = 0;
        String sqlPed = "SELECT id_pedido FROM pedido WHERE (numero_mesa = ? OR id_mesa = ?) "
                + "AND estado NOT IN ('COBRADO', 'CERRADO', 'PAGADO', 'CANCELADO', 'ANULADO') "
                + "ORDER BY id_pedido DESC LIMIT 1 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sqlPed)) {
            ps.setString(1, numOrigen);
            ps.setLong(2, idMesaOrigen);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idPedido = rs.getInt("id_pedido");
                }
            }
        }

        if (idPedido <= 0) {
            throw new IllegalStateException("No existe comanda activa en la mesa " + numOrigen);
        }

        // 3. Actualizar pedido con nueva mesa e incrementar versión optimista
        String sqlUpdPed = "UPDATE pedido SET numero_mesa = ?, id_mesa = ?, version = version + 1 WHERE id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUpdPed)) {
            ps.setString(1, numDestino);
            ps.setLong(2, idMesaDestino);
            ps.setInt(3, idPedido);
            ps.executeUpdate();
        }

        // 4. Liberar mesa origen y ocupar mesa destino incrementando version
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE mesa SET estado = 'LIBRE', id_pedido_actual = NULL, version = version + 1 WHERE id_mesa = ?")) {
            ps.setLong(1, idMesaOrigen);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE mesa SET estado = 'OCUPADA', id_pedido_actual = ?, version = version + 1 WHERE id_mesa = ?")) {
            ps.setInt(1, idPedido);
            ps.setLong(2, idMesaDestino);
            ps.executeUpdate();
        }

        // 5. Registrar en auditoría con columnas reales del esquema V6
        auditoriaDAO.registrarEventoSeguro(
                conn,
                idUsuario > 0 ? idUsuario : 1,
                "TRANSFERIR_MESA",
                "pedido#" + idPedido,
                "Mesa " + numOrigen,
                "Mesa " + numDestino
        );

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

        // Asegurar mesa principal con id_pedido_actual, estado OCUPADA y version + 1
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE mesa SET estado = 'OCUPADA', id_pedido_actual = ?, version = version + 1 WHERE id_mesa = ? OR numero = ?")) {
            ps.setInt(1, idPedido);
            ps.setLong(2, idMesaPrincipal);
            ps.setString(3, numeroPrincipal);
            ps.executeUpdate();
        }

        // 2. Asociar cada mesa secundaria
        for (String sec : numerosSecundarios) {
            if (sec.equalsIgnoreCase(numeroPrincipal)) {
                continue;
            }

            long idMesaSec = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id_mesa FROM mesa WHERE numero = ? FOR UPDATE")) {
                ps.setString(1, sec);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idMesaSec = rs.getLong("id_mesa");
                    }
                }
            }
            if (idMesaSec <= 0) {
                idMesaSec = Long.parseLong(sec.replaceAll("[^0-9]", ""));
            }

            // Ocupar mesa secundaria marcando id_mesa_principal y version + 1
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE mesa SET estado = 'OCUPADA', id_mesa_principal = ?, id_pedido_actual = ?, version = version + 1 WHERE numero = ?")) {
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

        // 3. Auditoría con columnas reales del esquema V6
        auditoriaDAO.registrarEventoSeguro(
                conn,
                idUsuario > 0 ? idUsuario : 1,
                "UNIR_MESAS",
                "mesa#" + idMesaPrincipal,
                "Mesa " + numeroPrincipal,
                "Unida con mesas: " + String.join(", ", numerosSecundarios)
        );

        return true;
    }

    @Override
    public boolean liberarMesasUnidasPorPedido(Connection conn, int idPedido) throws SQLException {
        // 1. Liberar secundarias vinculadas en mesa_union
        String sqlSec = "UPDATE mesa m JOIN mesa_union u ON m.id_mesa = u.id_mesa_secundaria "
                + "SET m.estado = 'LIBRE', m.id_mesa_principal = NULL, m.id_pedido_actual = NULL, m.version = m.version + 1 "
                + "WHERE u.id_pedido = ? AND u.activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlSec)) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }

        // 2. Liberar principal vinculada en mesa_union
        String sqlPrincUnion = "UPDATE mesa m JOIN mesa_union u ON m.id_mesa = u.id_mesa_principal "
                + "SET m.estado = 'LIBRE', m.id_mesa_principal = NULL, m.id_pedido_actual = NULL, m.version = m.version + 1 "
                + "WHERE u.id_pedido = ? AND u.activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlPrincUnion)) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }

        // 3. Liberar mesa referenciada directamente en pedido
        String sqlPrincPed = "UPDATE mesa m JOIN pedido p ON (m.id_mesa = p.id_mesa OR m.numero = p.numero_mesa) "
                + "SET m.estado = 'LIBRE', m.id_mesa_principal = NULL, m.id_pedido_actual = NULL, m.version = m.version + 1 "
                + "WHERE p.id_pedido = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlPrincPed)) {
            ps.setInt(1, idPedido);
            ps.executeUpdate();
        }

        // 4. Fallback directo por id_pedido_actual
        String sqlFallback = "UPDATE mesa SET estado = 'LIBRE', id_mesa_principal = NULL, id_pedido_actual = NULL, version = version + 1 WHERE id_pedido_actual = ?";
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
