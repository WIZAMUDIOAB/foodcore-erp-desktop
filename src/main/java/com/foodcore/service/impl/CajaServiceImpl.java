package com.foodcore.service.impl;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.dto.ResultadoCierreConActa;
import com.foodcore.service.ActaEntregaTurnoService;
import com.foodcore.service.CajaService;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.caja.ArqueoCierreZUseCase;
import com.foodcore.service.caja.CajaTurnoOperacionesUseCase;
import com.foodcore.service.caja.DefaultArqueoCierreZUseCase;
import com.foodcore.service.caja.DefaultCajaTurnoOperacionesUseCase;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación canónica de {@link CajaService} en paquete {@code com.foodcore.service.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-02.4).
 */
public class CajaServiceImpl implements CajaService {
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FMT_ACTA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final CajaTurnoOperacionesUseCase turnoOperacionesUseCase;
    private final ArqueoCierreZUseCase arqueoCierreZUseCase;

    public CajaServiceImpl() {
        this(new JdbcTransactionManager(), new CajaDAOImpl(), new InsumoDAOImpl(),
                new CierreContableService(), new ActaEntregaTurnoService());
    }

    public CajaServiceImpl(TransactionManager tx, CajaDAO cajaDAO) {
        this(tx, cajaDAO, new InsumoDAOImpl(), new CierreContableService(), new ActaEntregaTurnoService());
    }

    public CajaServiceImpl(TransactionManager tx, CajaDAO cajaDAO, InsumoDAO insumoDAO,
                           CierreContableService cierreContableService,
                           ActaEntregaTurnoService actaEntregaTurnoService) {
        this(new DefaultCajaTurnoOperacionesUseCase(tx, cajaDAO, cierreContableService),
                new DefaultArqueoCierreZUseCase(tx, cajaDAO, insumoDAO,
                        cierreContableService, actaEntregaTurnoService));
    }

    public CajaServiceImpl(CajaTurnoOperacionesUseCase turnoOperacionesUseCase,
                           ArqueoCierreZUseCase arqueoCierreZUseCase) {
        this.turnoOperacionesUseCase = turnoOperacionesUseCase;
        this.arqueoCierreZUseCase = arqueoCierreZUseCase;
    }

    @Override
    public Optional<Caja> obtenerCajaActiva() throws SQLException {
        return turnoOperacionesUseCase.obtenerCajaActiva();
    }

    @Override
    public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial)
            throws NegocioException, SQLException {
        return turnoOperacionesUseCase.abrirTurnoCaja(idUsuario, nombreCaja, montoInicial);
    }

    @Override
    public void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto)
            throws NegocioException, SQLException {
        registrarGastoOperativo(idCaja, idUsuario, monto, concepto, null);
    }

    @Override
    public void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto,
                                        Integer idCategoriaGasto) throws NegocioException, SQLException {
        turnoOperacionesUseCase.registrarGastoOperativo(
                idCaja, idUsuario, monto, concepto, idCategoriaGasto);
    }

    @Override
    public List<CategoriaGasto> listarCategoriasGastoActivas() throws SQLException {
        return turnoOperacionesUseCase.listarCategoriasGastoActivas();
    }

    @Override
    public List<MovimientoCaja> listarGastosTurno(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.listarGastosTurno(idCaja);
    }

    @Override
    public BigDecimal calcularVentasTurno(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.calcularVentasTurno(idCaja);
    }

    @Override
    public BigDecimal calcularGastosTurno(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.calcularGastosTurno(idCaja);
    }

    @Override
    public Map<String, Object[]> obtenerResumenVentasCierreZ(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.obtenerResumenVentasCierreZ(idCaja);
    }

    @Override
    public Map<String, BigDecimal> obtenerTotalesCierreZ(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.obtenerTotalesCierreZ(idCaja);
    }

    @Override
    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno() throws SQLException {
        return arqueoCierreZUseCase.obtenerInsumosCriticosTurno();
    }

    @Override
    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno(Connection conn) throws SQLException {
        return arqueoCierreZUseCase.obtenerInsumosCriticosTurno(conn);
    }

    @Override
    public void cerrarTurnoCaja(int idCaja, int idUsuarioCierre, BigDecimal efectivoFisicoContado)
            throws NegocioException, SQLException {
        arqueoCierreZUseCase.cerrarTurnoCaja(idCaja, idUsuarioCierre, efectivoFisicoContado);
    }

    @Override
    public ResultadoCierreConActa cerrarTurnoConActaPdf(int idCaja, int idUsuarioCierre,
                                                        BigDecimal efectivoFisicoContado,
                                                        String responsableSalida,
                                                        String responsableEntrada,
                                                        File archivoActaPdf)
            throws NegocioException, SQLException {
        File destino = archivoActaPdf != null
                ? archivoActaPdf
                : new File("logs/actas_turno/Acta_Entrega_Turno_" + idCaja + "_"
                        + LocalDateTime.now(ZONA_LIMA).format(FMT_ACTA_ARCHIVO) + ".pdf");
        return arqueoCierreZUseCase.cerrarTurnoConActaPdf(
                idCaja, idUsuarioCierre, efectivoFisicoContado,
                responsableSalida, responsableEntrada, destino);
    }
}
