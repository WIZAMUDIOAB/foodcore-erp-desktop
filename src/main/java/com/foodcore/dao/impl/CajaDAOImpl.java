package com.foodcore.dao.impl;

import com.foodcore.model.Caja;
import com.foodcore.model.MovimientoCaja;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación canónica de {@code CajaDAO} en paquete {@code com.foodcore.dao.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-02.4).
 */
public class CajaDAOImpl extends com.foodcore.dao.CajaDAOImpl {

    @Override
    public Optional<Caja> obtenerCajaAbierta(Connection conn) throws SQLException {
        return super.obtenerCajaAbierta(conn);
    }

    @Override
    public int abrirCaja(Connection conn, Caja caja) throws SQLException {
        return super.abrirCaja(conn, caja);
    }

    @Override
    public void cerrarCaja(Connection conn, int idCaja, int idUsuarioCierre, BigDecimal saldoFinalTeorico, BigDecimal saldoFinalContado, BigDecimal diferencia) throws SQLException {
        super.cerrarCaja(conn, idCaja, idUsuarioCierre, saldoFinalTeorico, saldoFinalContado, diferencia);
    }

    @Override
    public void registrarMovimiento(Connection conn, MovimientoCaja mov) throws SQLException {
        super.registrarMovimiento(conn, mov);
    }

    @Override
    public BigDecimal calcularTotalVentasTurno(Connection conn, int idCaja) throws SQLException {
        return super.calcularTotalVentasTurno(conn, idCaja);
    }

    @Override
    public Map<String, BigDecimal> obtenerTotalesPorMedioPago(Connection conn, int idCaja) throws SQLException {
        return super.obtenerTotalesPorMedioPago(conn, idCaja);
    }
}
