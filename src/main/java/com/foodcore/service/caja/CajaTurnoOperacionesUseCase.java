package com.foodcore.service.caja;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.MovimientoCaja;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CajaTurnoOperacionesUseCase {
    Optional<Caja> obtenerCajaActiva() throws SQLException;

    int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial)
            throws NegocioException, SQLException;

    void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto,
                                 Integer idCategoriaGasto) throws NegocioException, SQLException;

    List<CategoriaGasto> listarCategoriasGastoActivas() throws SQLException;

    List<MovimientoCaja> listarGastosTurno(int idCaja) throws SQLException;

    BigDecimal calcularVentasTurno(int idCaja) throws SQLException;

    BigDecimal calcularGastosTurno(int idCaja) throws SQLException;

    Map<String, Object[]> obtenerResumenVentasCierreZ(int idCaja) throws SQLException;

    Map<String, BigDecimal> obtenerTotalesCierreZ(int idCaja) throws SQLException;
}