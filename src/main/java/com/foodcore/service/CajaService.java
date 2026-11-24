package com.foodcore.service;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.dto.ResultadoCierreConActa;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrato de servicio para operaciones de turno de caja, gastos operativos y Cierre Z.
 */
public interface CajaService {

    Optional<Caja> obtenerCajaActiva() throws SQLException;

    int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial)
            throws NegocioException, SQLException;

    void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto)
            throws NegocioException, SQLException;

    void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto,
                                 Integer idCategoriaGasto) throws NegocioException, SQLException;

    List<CategoriaGasto> listarCategoriasGastoActivas() throws SQLException;

    List<MovimientoCaja> listarGastosTurno(int idCaja) throws SQLException;

    BigDecimal calcularVentasTurno(int idCaja) throws SQLException;

    BigDecimal calcularGastosTurno(int idCaja) throws SQLException;

    Map<String, Object[]> obtenerResumenVentasCierreZ(int idCaja) throws SQLException;

    Map<String, BigDecimal> obtenerTotalesCierreZ(int idCaja) throws SQLException;

    List<InsumoCriticoTurno> obtenerInsumosCriticosTurno() throws SQLException;

    List<InsumoCriticoTurno> obtenerInsumosCriticosTurno(Connection conn) throws SQLException;

    void cerrarTurnoCaja(int idCaja, int idUsuarioCierre, BigDecimal efectivoFisicoContado)
            throws NegocioException, SQLException;

    /** Ejecuta el Cierre Z y genera el Acta de Entrega de Turno PDF. */
    ResultadoCierreConActa cerrarTurnoConActaPdf(int idCaja, int idUsuarioCierre,
                                                 BigDecimal efectivoFisicoContado,
                                                 String responsableSalida,
                                                 String responsableEntrada,
                                                 File archivoActaPdf)
            throws NegocioException, SQLException;
}
