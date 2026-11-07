package com.foodcore.dao.impl;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.model.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClienteDAOImpl implements ClienteDAO {

    @Override
    public List<Cliente> listarTodos(Connection conn) throws SQLException {
        String sql = "SELECT c.*, "
                + "COALESCE(SUM(CASE WHEN cxc.estado <> 'COBRADA' THEN (cxc.monto_total - cxc.monto_cobrado) ELSE 0 END), 0) AS saldo_deudor, "
                + "COUNT(CASE WHEN cxc.estado <> 'COBRADA' THEN 1 END) AS comprobantes_pendientes "
                + "FROM cliente c "
                + "LEFT JOIN cuenta_por_cobrar cxc ON c.id_cliente = cxc.id_cliente "
                + "GROUP BY c.id_cliente "
                + "ORDER BY c.razon_social ASC";

        List<Cliente> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCliente(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Cliente> listarActivos(Connection conn) throws SQLException {
        String sql = "SELECT c.*, "
                + "COALESCE(SUM(CASE WHEN cxc.estado <> 'COBRADA' THEN (cxc.monto_total - cxc.monto_cobrado) ELSE 0 END), 0) AS saldo_deudor, "
                + "COUNT(CASE WHEN cxc.estado <> 'COBRADA' THEN 1 END) AS comprobantes_pendientes "
                + "FROM cliente c "
                + "LEFT JOIN cuenta_por_cobrar cxc ON c.id_cliente = cxc.id_cliente "
                + "WHERE c.activo = 1 "
                + "GROUP BY c.id_cliente "
                + "ORDER BY c.razon_social ASC";

        List<Cliente> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCliente(rs));
            }
        }
        return lista;
    }

    @Override
    public Optional<Cliente> buscarPorId(Connection conn, int idCliente) throws SQLException {
        String sql = "SELECT c.*, "
                + "COALESCE(SUM(CASE WHEN cxc.estado <> 'COBRADA' THEN (cxc.monto_total - cxc.monto_cobrado) ELSE 0 END), 0) AS saldo_deudor, "
                + "COUNT(CASE WHEN cxc.estado <> 'COBRADA' THEN 1 END) AS comprobantes_pendientes "
                + "FROM cliente c "
                + "LEFT JOIN cuenta_por_cobrar cxc ON c.id_cliente = cxc.id_cliente "
                + "WHERE c.id_cliente = ? "
                + "GROUP BY c.id_cliente";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearCliente(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Cliente> buscarPorDocumento(Connection conn, String numeroDocumento) throws SQLException {
        String sql = "SELECT c.*, 0 AS saldo_deudor, 0 AS comprobantes_pendientes FROM cliente c WHERE c.numero_documento = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroDocumento);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearCliente(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int insertar(Connection conn, Cliente cliente) throws SQLException {
        String sql = "INSERT INTO cliente (tipo_documento, numero_documento, nombre_razon_social, razon_social, contacto, telefono, email, "
                + "limite_credito, dias_credito, puntos_fidelidad, estado, activo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cliente.getTipoDocumento());
            ps.setString(2, cliente.getNumeroDocumento());
            ps.setString(3, cliente.getRazonSocial());
            ps.setString(4, cliente.getRazonSocial());
            ps.setString(5, cliente.getContacto());
            ps.setString(6, cliente.getTelefono());
            ps.setString(7, cliente.getEmail());
            ps.setDouble(8, cliente.getLimiteCredito());
            ps.setInt(9, cliente.getDiasCredito());
            ps.setInt(10, cliente.getPuntosFidelidad());
            ps.setString(11, cliente.getEstado());
            ps.setBoolean(12, cliente.isActivo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cliente.setIdCliente(rs.getInt(1));
                    return cliente.getIdCliente();
                }
            }
        }
        return 0;
    }

    @Override
    public boolean actualizar(Connection conn, Cliente cliente) throws SQLException {
        String sql = "UPDATE cliente SET tipo_documento = ?, numero_documento = ?, nombre_razon_social = ?, razon_social = ?, contacto = ?, "
                + "telefono = ?, email = ?, limite_credito = ?, dias_credito = ?, puntos_fidelidad = ?, estado = ?, activo = ? "
                + "WHERE id_cliente = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cliente.getTipoDocumento());
            ps.setString(2, cliente.getNumeroDocumento());
            ps.setString(3, cliente.getRazonSocial());
            ps.setString(4, cliente.getRazonSocial());
            ps.setString(5, cliente.getContacto());
            ps.setString(6, cliente.getTelefono());
            ps.setString(7, cliente.getEmail());
            ps.setDouble(8, cliente.getLimiteCredito());
            ps.setInt(9, cliente.getDiasCredito());
            ps.setInt(10, cliente.getPuntosFidelidad());
            ps.setString(11, cliente.getEstado());
            ps.setBoolean(12, cliente.isActivo());
            ps.setInt(13, cliente.getIdCliente());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean cambiarEstado(Connection conn, int idCliente, boolean activo) throws SQLException {
        String sql = "UPDATE cliente SET activo = ?, estado = ? WHERE id_cliente = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, activo);
            ps.setString(2, activo ? "ACTIVO" : "INACTIVO");
            ps.setInt(3, idCliente);
            return ps.executeUpdate() > 0;
        }
    }

    private Cliente mapearCliente(ResultSet rs) throws SQLException {
        Cliente c = new Cliente();
        c.setIdCliente(rs.getInt("id_cliente"));
        c.setTipoDocumento(rs.getString("tipo_documento"));
        c.setNumeroDocumento(rs.getString("numero_documento"));
        c.setRazonSocial(rs.getString("razon_social"));
        c.setContacto(rs.getString("contacto"));
        c.setTelefono(rs.getString("telefono"));
        c.setEmail(rs.getString("email"));
        c.setLimiteCredito(rs.getDouble("limite_credito"));
        c.setDiasCredito(rs.getInt("dias_credito"));
        int pf = rs.getInt("puntos_fidelidad");
        c.setPuntosFidelidad(pf);
        try {
            int pa = rs.getInt("puntos_acumulados");
            c.setPuntosAcumulados(pa > 0 ? pa : pf);
        } catch (SQLException ignored) {
            c.setPuntosAcumulados(pf);
        }
        try {
            java.sql.Date fn = rs.getDate("fecha_nacimiento");
            if (fn != null) {
                c.setFechaNacimiento(fn.toLocalDate());
            }
        } catch (SQLException ignored) {
        }
        c.setEstado(rs.getString("estado"));
        c.setActivo(rs.getBoolean("activo"));
        if (rs.getTimestamp("fecha_registro") != null) {
            c.setFechaRegistro(rs.getTimestamp("fecha_registro").toLocalDateTime());
        }
        c.setSaldoDeudor(rs.getDouble("saldo_deudor"));
        c.setComprobantesPendientes(rs.getInt("comprobantes_pendientes"));
        return c;
    }
}
