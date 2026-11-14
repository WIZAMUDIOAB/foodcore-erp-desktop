package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.Insumo;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Servicio de control de turnos de caja, gastos operativos de caja chica (§3.4),
 * arqueo físico y cierre Z con transacciones explícitas mediante {@link TransactionManager}
 * (SPEC-FOODCORE-v1.0 §6.4, §7.1, §8.3, R-02.4; SPEC-FOODCORE-v1.2 §3.4).
 */
public class CajaService {

    private static final Logger LOGGER = Logger.getLogger(CajaService.class.getName());
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

    private final TransactionManager tx;
    private final CajaDAO cajaDAO;
    private final InsumoDAO insumoDAO;
    private final CierreContableService cierreContableService;
    private final ActaEntregaTurnoService actaEntregaTurnoService;

    public CajaService() {
        this(new JdbcTransactionManager(), new CajaDAOImpl(), new InsumoDAOImpl(),
                new CierreContableService(), new ActaEntregaTurnoService());
    }

    public CajaService(TransactionManager tx, CajaDAO cajaDAO) {
        this(tx, cajaDAO, new InsumoDAOImpl(), new CierreContableService(), new ActaEntregaTurnoService());
    }

    public CajaService(TransactionManager tx,
                       CajaDAO cajaDAO,
                       InsumoDAO insumoDAO,
                       CierreContableService cierreContableService,
                       ActaEntregaTurnoService actaEntregaTurnoService) {
        this.tx = tx;
        this.cajaDAO = cajaDAO;
        this.insumoDAO = insumoDAO;
        this.cierreContableService = cierreContableService;
        this.actaEntregaTurnoService = actaEntregaTurnoService;
    }

    public Optional<Caja> obtenerCajaActiva() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.obtenerCajaAbierta(conn);
        }
    }

    public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial) throws NegocioException, SQLException {
        if (montoInicial == null || montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El monto inicial de caja no puede ser negativo.");
        }
        BigDecimal montoNorm = DesgloseFiscal.normalizarMoneda(montoInicial);

        return tx.executeChecked(conn -> {
            Optional<Caja> actual = cajaDAO.obtenerCajaAbierta(conn);
            if (actual.isPresent()) {
                throw new NegocioException("Ya existe una caja abierta (" + actual.get().getNombreCaja() + "). Debe cerrarla antes.");
            }

            Caja caja = new Caja(0, idUsuario, nombreCaja, montoNorm, "ABIERTA");
            int id = cajaDAO.abrirCaja(conn, caja);

            MovimientoCaja mov = new MovimientoCaja(0, id, idUsuario, "INGRESO", "EFECTIVO", montoNorm, "Fondo de apertura de turno");
            cajaDAO.registrarMovimiento(conn, mov);

            LOGGER.info("Caja #" + id + " abierta con fondo inicial S/ " + montoNorm);
            return id;
        });
    }

    /**
     * Registra una salida de efectivo de caja chica / gasto operativo no relacionado con ventas (§3.4).
     * Genera un {@code movimiento_caja} de tipo {@code EGRESO} que afecta el saldo teórico del turno
     * pero no altera el IGV ni las ventas fiscales.
     */
    public void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto)
            throws NegocioException, SQLException {
        registrarGastoOperativo(idCaja, idUsuario, monto, concepto, null);
    }

    /**
     * Registra una salida de efectivo de caja chica / gasto operativo categorizado (v4.9.0 Fase 1.1).
     */
    public void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto, Integer idCategoriaGasto)
            throws NegocioException, SQLException {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El monto del gasto operativo debe ser mayor a cero.");
        }
        if (concepto == null || concepto.trim().isEmpty()) {
            throw new NegocioException("Debe especificar el concepto o motivo del gasto de caja.");
        }
        BigDecimal montoNorm = DesgloseFiscal.normalizarMoneda(monto);
        String glosa = "Gasto Operativo: " + concepto.trim();

        tx.executeVoidChecked(conn -> {
            cierreContableService.validarPeriodoAbierto(conn, LocalDate.now(ZONA_LIMA));

            Optional<Caja> opt = cajaDAO.obtenerCajaAbierta(conn);
            if (opt.isEmpty() || opt.get().getIdCaja() != idCaja) {
                throw new CajaCerradaException("No hay un turno de caja abierto para registrar el gasto.");
            }
            if (idCategoriaGasto != null) {
                Optional<CategoriaGasto> catOpt = cajaDAO.obtenerCategoriaGastoPorId(conn, idCategoriaGasto);
                if (catOpt.isEmpty() || !catOpt.get().isActivo()) {
                    throw new NegocioException("La categoría de gasto seleccionada no existe o está inactiva.");
                }
            }
            MovimientoCaja movGasto = new MovimientoCaja(
                0, idCaja, idUsuario, "EGRESO", "EFECTIVO", montoNorm, glosa, idCategoriaGasto
            );
            cajaDAO.registrarMovimiento(conn, movGasto);
            LOGGER.info("Gasto operativo registrado en Caja #" + idCaja + ": S/ " + montoNorm + " (" + glosa + ")");
        });
    }

    public List<CategoriaGasto> listarCategoriasGastoActivas() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.listarCategoriasGastoActivas(conn);
        }
    }

    public List<MovimientoCaja> listarGastosTurno(int idCaja) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.listarGastosTurno(conn, idCaja);
        }
    }

    public BigDecimal calcularVentasTurno(int idCaja) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.calcularTotalVentasTurno(conn, idCaja);
        }
    }

    public BigDecimal calcularGastosTurno(int idCaja) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.calcularTotalGastosTurno(conn, idCaja);
        }
    }

    public Map<String, BigDecimal> obtenerTotalesCierreZ(int idCaja) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.obtenerTotalesPorMedioPago(conn, idCaja);
        }
    }

    /**
     * Consulta los insumos activos agotados o en/bajo stock mínimo para mostrar al cierre de turno (v5.2.0).
     */
    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return obtenerInsumosCriticosTurno(conn);
        }
    }

    /** Variante transaccional para capturar stock crítico consistente durante el cierre de caja (v5.2.0). */
    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno(Connection conn) throws SQLException {
        List<Insumo> todos = insumoDAO.listarTodos(conn);
        List<InsumoCriticoTurno> criticos = new ArrayList<>();
        for (Insumo ins : todos) {
            if (ins == null || !ins.isActivo()) {
                continue;
            }
            BigDecimal actual = ins.getStockActualDisponible() != null
                    ? ins.getStockActualDisponible() : BigDecimal.ZERO;
            BigDecimal minimo = ins.getStockMinimo() != null
                    ? ins.getStockMinimo() : BigDecimal.ZERO;
            if (actual.compareTo(BigDecimal.ZERO) <= 0) {
                criticos.add(new InsumoCriticoTurno(
                        ins.getIdInsumo(), ins.getCodigo(), ins.getNombre(),
                        ins.getUnidadMedida(), actual, minimo, "AGOTADO"));
            } else if (minimo.compareTo(BigDecimal.ZERO) > 0 && actual.compareTo(minimo) <= 0) {
                criticos.add(new InsumoCriticoTurno(
                        ins.getIdInsumo(), ins.getCodigo(), ins.getNombre(),
                        ins.getUnidadMedida(), actual, minimo, "STOCK_MINIMO"));
            }
        }
        criticos.sort(Comparator.comparing(InsumoCriticoTurno::isAgotado).reversed()
                .thenComparing(c -> c.nombre() != null ? c.nombre() : "", String.CASE_INSENSITIVE_ORDER));
        return criticos;
    }

    public void cerrarTurnoCaja(int idCaja, int idUsuarioCierre, BigDecimal efectivoFisicoContado) throws NegocioException, SQLException {
        ejecutarCierreTurnoTx(idCaja, idUsuarioCierre, efectivoFisicoContado);
    }

    /**
     * Ejecuta el Cierre Z transaccional y genera el Acta de Entrega de Turno PDF
     * incluyendo el inventario crítico del relevo (v5.2.0).
     */
    public ResultadoCierreConActa cerrarTurnoConActaPdf(int idCaja,
                                                        int idUsuarioCierre,
                                                        BigDecimal efectivoFisicoContado,
                                                        String responsableSalida,
                                                        String responsableEntrada,
                                                        File archivoActaPdf) throws NegocioException, SQLException {
        ResultadoCierreConActa parcial = ejecutarCierreTurnoTx(idCaja, idUsuarioCierre, efectivoFisicoContado);
        File destino = archivoActaPdf != null
                ? archivoActaPdf
                : new File("logs/actas_turno/Acta_Entrega_Turno_" + idCaja + "_"
                        + LocalDateTime.now(ZONA_LIMA).format(FMT_ACTA_ARCHIVO) + ".pdf");
        try {
            File pdfGenerado = actaEntregaTurnoService.generarActaEntregaTurnoPdf(
                    parcial.cajaCerrada(),
                    parcial.efectivoContado(),
                    parcial.diferencia(),
                    parcial.insumosCriticos(),
                    responsableSalida,
                    responsableEntrada,
                    destino
            );
            return new ResultadoCierreConActa(
                    parcial.cajaCerrada(),
                    parcial.montoEsperado(),
                    parcial.efectivoContado(),
                    parcial.diferencia(),
                    parcial.insumosCriticos(),
                    pdfGenerado
            );
        } catch (Exception ex) {
            throw new NegocioException("La caja fue cerrada pero falló la generación del Acta PDF: " + ex.getMessage());
        }
    }

    private ResultadoCierreConActa ejecutarCierreTurnoTx(int idCaja,
                                                         int idUsuarioCierre,
                                                         BigDecimal efectivoFisicoContado)
            throws NegocioException, SQLException {
        if (efectivoFisicoContado == null || efectivoFisicoContado.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El efectivo físico contado en el arqueo no puede ser negativo.");
        }
        BigDecimal fisicoNorm = DesgloseFiscal.normalizarMoneda(efectivoFisicoContado);

        return tx.executeChecked(conn -> {
            cierreContableService.validarPeriodoAbierto(conn, LocalDate.now(ZONA_LIMA));

            Optional<Caja> opt = cajaDAO.obtenerCajaAbierta(conn);
            if (opt.isEmpty() || opt.get().getIdCaja() != idCaja) {
                throw new CajaCerradaException("La caja seleccionada no se encuentra abierta.");
            }

            Caja caja = opt.get();
            BigDecimal totalVentasEfectivo = cajaDAO.calcularTotalVentasTurno(conn, idCaja);
            BigDecimal totalGastosEfectivo = cajaDAO.calcularTotalGastosTurno(conn, idCaja);
            BigDecimal montoEsperado = DesgloseFiscal.normalizarMoneda(
                caja.getMontoApertura().add(totalVentasEfectivo).subtract(totalGastosEfectivo)
            );
            BigDecimal diferencia = DesgloseFiscal.normalizarMoneda(fisicoNorm.subtract(montoEsperado));

            cajaDAO.cerrarCaja(conn, idCaja, idUsuarioCierre, montoEsperado, fisicoNorm, diferencia);
            caja.setEstado("CERRADA");
            caja.setIdUsuarioCierre(idUsuarioCierre);
            caja.setMontoCierre(fisicoNorm);
            caja.setDiferencia(diferencia);
            caja.setFechaCierre(LocalDateTime.now(ZONA_LIMA));

            String glosa = String.format("Cierre Z de turno (Esperado: S/ %s | Contado: S/ %s | Dif: S/ %s)",
                montoEsperado, fisicoNorm, diferencia);
            MovimientoCaja movCierre = new MovimientoCaja(0, idCaja, idUsuarioCierre, "EGRESO", "EFECTIVO", fisicoNorm, glosa);
            cajaDAO.registrarMovimiento(conn, movCierre);

            List<InsumoCriticoTurno> criticos = obtenerInsumosCriticosTurno(conn);
            LOGGER.info("Caja #" + idCaja + " cerrada exitosamente. " + glosa + " | Insumos críticos: " + criticos.size());
            return new ResultadoCierreConActa(caja, montoEsperado, fisicoNorm, diferencia, criticos, null);
        });
    }
}
