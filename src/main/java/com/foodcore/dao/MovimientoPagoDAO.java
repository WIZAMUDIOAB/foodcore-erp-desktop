package com.foodcore.dao;

import com.foodcore.model.MovimientoPago;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * DAO para gestión administrativa de bonos, comisiones, adelantos y descuentos.
 */
public interface MovimientoPagoDAO {

    long insertar(Connection conn, MovimientoPago movimiento) throws SQLException;

    List<MovimientoPago> listarPorPeriodo(Connection conn, String periodoMes) throws SQLException;

    List<MovimientoPago> listarPorUsuarioYPeriodo(Connection conn, int idUsuario, String periodoMes) throws SQLException;

    boolean eliminar(Connection conn, long idMovimiento) throws SQLException;
}
