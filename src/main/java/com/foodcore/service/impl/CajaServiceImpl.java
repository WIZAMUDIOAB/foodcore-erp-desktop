package com.foodcore.service.impl;

import com.foodcore.dao.CajaDAO;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.service.CajaService;
import com.foodcore.tx.TransactionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación canónica de {@link CajaService} en paquete {@code com.foodcore.service.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-02.4).
 */
public class CajaServiceImpl extends CajaService {

    public CajaServiceImpl() {
        super();
    }

    public CajaServiceImpl(TransactionManager tx, CajaDAO cajaDAO) {
        super(tx, cajaDAO);
    }

    @Override
    public Optional<Caja> obtenerCajaActiva() throws SQLException {
        return super.obtenerCajaActiva();
    }

    @Override
    public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial) throws NegocioException, SQLException {
        return super.abrirTurnoCaja(idUsuario, nombreCaja, montoInicial);
    }

    @Override
    public BigDecimal calcularVentasTurno(int idCaja) throws SQLException {
        return super.calcularVentasTurno(idCaja);
    }

    @Override
    public Map<String, BigDecimal> obtenerTotalesCierreZ(int idCaja) throws SQLException {
        return super.obtenerTotalesCierreZ(idCaja);
    }

    @Override
    public void cerrarTurnoCaja(int idCaja, int idUsuarioCierre, BigDecimal efectivoFisicoContado) throws NegocioException, SQLException {
        super.cerrarTurnoCaja(idCaja, idUsuarioCierre, efectivoFisicoContado);
    }
}
