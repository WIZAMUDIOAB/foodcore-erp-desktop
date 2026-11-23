package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.Insumo;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
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

/** Fachada compatible para operaciones de turno de caja y cierre Z. */
public class CajaService {
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FMT_ACTA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** Resultado integral del Cierre Z con resumen de stock crítico y PDF de relevo (v5.2.0). */
    public record ResultadoCierreConActa(
            Caja cajaCerrada,
            BigDecimal montoEsperado,
            BigDecimal efectivoContado,
            BigDecimal diferencia,
            List<InsumoCriticoTurno> insumosCriticos,
            File archivoActaPdf
    ) {
        public ResultadoCierreConActa {
            insumosCriticos = insumosCriticos != null ? List.copyOf(insumosCriticos) : List.of();
        }
    }

    private final CajaTurnoOperacionesUseCase turnoOperacionesUseCase;
    private final ArqueoCierreZUseCase arqueoCierreZUseCase;

    public CajaService() {
        this(new JdbcTransactionManager(), new CajaDAOImpl(), new InsumoDAOImpl(),
                new CierreContableService(), new ActaEntregaTurnoService());
    }

    public CajaService(TransactionManager tx, CajaDAO cajaDAO) {
        this(tx, cajaDAO, new InsumoDAOImpl(), new CierreContableService(), new ActaEntregaTurnoService());
    }

    public CajaService(TransactionManager tx, CajaDAO cajaDAO, InsumoDAO insumoDAO,
                       CierreContableService cierreContableService,
                       ActaEntregaTurnoService actaEntregaTurnoService) {
        this(new DefaultCajaTurnoOperacionesUseCase(tx, cajaDAO, cierreContableService),
                new DefaultArqueoCierreZUseCase(tx, cajaDAO, insumoDAO,
                        cierreContableService, actaEntregaTurnoService));
    }

    public CajaService(CajaTurnoOperacionesUseCase turnoOperacionesUseCase,
                       ArqueoCierreZUseCase arqueoCierreZUseCase) {
        this.turnoOperacionesUseCase = turnoOperacionesUseCase;
        this.arqueoCierreZUseCase = arqueoCierreZUseCase;
    }

    public Optional<Caja> obtenerCajaActiva() throws SQLException {
        return turnoOperacionesUseCase.obtenerCajaActiva();
    }

    public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial)
            throws NegocioException, SQLException {
        return turnoOperacionesUseCase.abrirTurnoCaja(idUsuario, nombreCaja, montoInicial);
    }

    public void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto)
            throws NegocioException, SQLException {
        registrarGastoOperativo(idCaja, idUsuario, monto, concepto, null);
    }

    public void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto,
                                        Integer idCategoriaGasto) throws NegocioException, SQLException {
        turnoOperacionesUseCase.registrarGastoOperativo(
                idCaja, idUsuario, monto, concepto, idCategoriaGasto);
    }

    public List<CategoriaGasto> listarCategoriasGastoActivas() throws SQLException {
        return turnoOperacionesUseCase.listarCategoriasGastoActivas();
    }

    public List<MovimientoCaja> listarGastosTurno(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.listarGastosTurno(idCaja);
    }

    public BigDecimal calcularVentasTurno(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.calcularVentasTurno(idCaja);
    }

    public BigDecimal calcularGastosTurno(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.calcularGastosTurno(idCaja);
    }

    public Map<String, Object[]> obtenerResumenVentasCierreZ(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.obtenerResumenVentasCierreZ(idCaja);
    }

    public Map<String, BigDecimal> obtenerTotalesCierreZ(int idCaja) throws SQLException {
        return turnoOperacionesUseCase.obtenerTotalesCierreZ(idCaja);
    }

    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno() throws SQLException {
        return arqueoCierreZUseCase.obtenerInsumosCriticosTurno();
    }

    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno(Connection conn) throws SQLException {
        return arqueoCierreZUseCase.obtenerInsumosCriticosTurno(conn);
    }

    public void cerrarTurnoCaja(int idCaja, int idUsuarioCierre, BigDecimal efectivoFisicoContado)
            throws NegocioException, SQLException {
        arqueoCierreZUseCase.cerrarTurnoCaja(idCaja, idUsuarioCierre, efectivoFisicoContado);
    }

    /** Ejecuta el Cierre Z y genera el Acta de Entrega de Turno PDF. */
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
