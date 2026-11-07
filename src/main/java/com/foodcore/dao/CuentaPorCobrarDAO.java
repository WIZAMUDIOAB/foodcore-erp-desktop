package com.foodcore.dao;

import com.foodcore.model.CobroCliente;
import com.foodcore.model.CuentaPorCobrar;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface CuentaPorCobrarDAO {
    List<CuentaPorCobrar> listarTodas(Connection conn) throws SQLException;
    List<CuentaPorCobrar> listarPorEstado(Connection conn, String estado) throws SQLException;
    List<CuentaPorCobrar> listarPorCliente(Connection conn, int idCliente) throws SQLException;
    Optional<CuentaPorCobrar> buscarPorId(Connection conn, int idCuentaCobrar) throws SQLException;
    int insertar(Connection conn, CuentaPorCobrar cxc) throws SQLException;
    boolean registrarCobro(Connection conn, CobroCliente cobro) throws SQLException;
    int contarVencidas(Connection conn) throws SQLException;
}
