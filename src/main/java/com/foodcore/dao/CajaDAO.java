package com.foodcore.dao;

import com.foodcore.model.Caja;
import com.foodcore.model.MovimientoCaja;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface CajaDAO {
    Optional<Caja> obtenerCajaAbierta(Connection conn) throws SQLException;
    int abrirCaja(Connection conn, Caja caja) throws SQLException;
    void cerrarCaja(Connection conn, int idCaja, int idUsuarioCierre, BigDecimal montoCierre, BigDecimal diferencia) throws SQLException;
    void registrarMovimiento(Connection conn, MovimientoCaja mov) throws SQLException;
    BigDecimal calcularTotalVentasTurno(Connection conn, int idCaja) throws SQLException;
}
