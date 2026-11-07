package com.foodcore.dao;

import com.foodcore.model.CuentaPorPagar;
import com.foodcore.model.PagoProveedor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface CuentaPorPagarDAO {
    List<CuentaPorPagar> listarTodas(Connection conn) throws SQLException;
    List<CuentaPorPagar> listarPorEstado(Connection conn, String estado) throws SQLException;
    List<CuentaPorPagar> listarPorProveedor(Connection conn, int idProveedor) throws SQLException;
    Optional<CuentaPorPagar> buscarPorId(Connection conn, int idCuentaPagar) throws SQLException;
    int insertar(Connection conn, CuentaPorPagar cxp) throws SQLException;
    boolean registrarPago(Connection conn, PagoProveedor pago) throws SQLException;
    int contarVencidas(Connection conn) throws SQLException;
}
