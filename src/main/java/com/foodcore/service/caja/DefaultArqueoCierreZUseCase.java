package com.foodcore.service.caja;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.Insumo;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.service.ActaEntregaTurnoService;
import com.foodcore.service.CajaService;
import com.foodcore.service.CierreContableService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class DefaultArqueoCierreZUseCase implements ArqueoCierreZUseCase {
    private static final Logger LOGGER = Logger.getLogger(DefaultArqueoCierreZUseCase.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final TransactionManager tx;
    private final CajaDAO cajaDAO;
    private final InsumoDAO insumoDAO;
    private final CierreContableService cierreContableService;
    private final ActaEntregaTurnoService actaEntregaTurnoService;
    private final JdbcTransactionManager.ConnectionSupplier connectionSupplier;

    public DefaultArqueoCierreZUseCase(TransactionManager tx, CajaDAO cajaDAO, InsumoDAO insumoDAO,
                                       CierreContableService cierreContableService,
                                       ActaEntregaTurnoService actaEntregaTurnoService,
                                       JdbcTransactionManager.ConnectionSupplier connectionSupplier) {
        this.tx = Objects.requireNonNull(tx);
        this.cajaDAO = Objects.requireNonNull(cajaDAO);
        this.insumoDAO = Objects.requireNonNull(insumoDAO);
        this.cierreContableService = Objects.requireNonNull(cierreContableService);
        this.actaEntregaTurnoService = Objects.requireNonNull(actaEntregaTurnoService);
        this.connectionSupplier = connectionSupplier != null ? connectionSupplier : ConexionBD::getConnection;
    }

    public DefaultArqueoCierreZUseCase(TransactionManager tx, CajaDAO cajaDAO, InsumoDAO insumoDAO,
                                       CierreContableService cierreContableService,
                                       ActaEntregaTurnoService actaEntregaTurnoService) {
        this(tx, cajaDAO, insumoDAO, cierreContableService, actaEntregaTurnoService,
                ConexionBD::getConnection);
    }

    @Override
    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno() throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return obtenerInsumosCriticosTurno(conn);
        }
    }

    @Override
    public List<InsumoCriticoTurno> obtenerInsumosCriticosTurno(Connection conn) throws SQLException {
        return clasificarInsumosCriticos(insumoDAO.listarTodos(conn));
    }

    @Override
    public List<InsumoCriticoTurno> clasificarInsumosCriticos(List<Insumo> insumos) {
        List<InsumoCriticoTurno> criticos = new ArrayList<>();
        for (Insumo ins : insumos) {
            if (ins == null || !ins.isActivo()) {
                continue;
            }
            BigDecimal actual = ins.getStockActualDisponible() != null
                    ? ins.getStockActualDisponible() : BigDecimal.ZERO;
            BigDecimal minimo = ins.getStockMinimo() != null
                    ? ins.getStockMinimo() : BigDecimal.ZERO;
            if (actual.compareTo(BigDecimal.ZERO) <= 0) {
                criticos.add(new InsumoCriticoTurno(ins.getIdInsumo(), ins.getCodigo(), ins.getNombre(),
                        ins.getUnidadMedida(), actual, minimo, "AGOTADO"));
            } else if (minimo.compareTo(BigDecimal.ZERO) > 0 && actual.compareTo(minimo) <= 0) {
                criticos.add(new InsumoCriticoTurno(ins.getIdInsumo(), ins.getCodigo(), ins.getNombre(),
                        ins.getUnidadMedida(), actual, minimo, "STOCK_MINIMO"));
            }
        }
        criticos.sort(Comparator.comparing(InsumoCriticoTurno::isAgotado).reversed()
                .thenComparing(c -> c.nombre() != null ? c.nombre() : "", String.CASE_INSENSITIVE_ORDER));
        return criticos;
    }

    @Override
    public CajaService.ResultadoCierreConActa cerrarTurnoCaja(int idCaja, int idUsuarioCierre,
                                                              BigDecimal efectivoFisicoContado)
            throws NegocioException, SQLException {
        return ejecutarCierreTurnoTx(idCaja, idUsuarioCierre, efectivoFisicoContado);
    }

    @Override
    public CajaService.ResultadoCierreConActa cerrarTurnoConActaPdf(int idCaja, int idUsuarioCierre,
                                                                    BigDecimal efectivoFisicoContado,
                                                                    String responsableSalida,
                                                                    String responsableEntrada,
                                                                    File archivoDestinoResuelto)
            throws NegocioException, SQLException {
        CajaService.ResultadoCierreConActa parcial = ejecutarCierreTurnoTx(
                idCaja, idUsuarioCierre, efectivoFisicoContado);
        try {
            File destino = Objects.requireNonNull(archivoDestinoResuelto,
                    "El archivo destino del Acta PDF es obligatorio.");
            File pdfGenerado = actaEntregaTurnoService.generarActaEntregaTurnoPdf(
                    parcial.cajaCerrada(), parcial.efectivoContado(), parcial.diferencia(),
                    parcial.insumosCriticos(), responsableSalida, responsableEntrada, destino);
            return new CajaService.ResultadoCierreConActa(parcial.cajaCerrada(), parcial.montoEsperado(),
                    parcial.efectivoContado(), parcial.diferencia(), parcial.insumosCriticos(), pdfGenerado);
        } catch (Exception ex) {
            throw new NegocioException("La caja fue cerrada pero falló la generación del Acta PDF: "
                    + ex.getMessage());
        }
    }

    private CajaService.ResultadoCierreConActa ejecutarCierreTurnoTx(int idCaja, int idUsuarioCierre,
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
            BigDecimal totalIngresosEfectivo = cajaDAO.calcularTotalIngresosEfectivoTurno(conn, idCaja);
            BigDecimal totalGastosEfectivo = cajaDAO.calcularTotalGastosTurno(conn, idCaja);
            BigDecimal montoEsperado = DesgloseFiscal.normalizarMoneda(
                    caja.getMontoApertura().add(totalVentasEfectivo)
                            .add(totalIngresosEfectivo).subtract(totalGastosEfectivo));
            BigDecimal diferencia = DesgloseFiscal.normalizarMoneda(fisicoNorm.subtract(montoEsperado));

            cajaDAO.cerrarCaja(conn, idCaja, idUsuarioCierre, montoEsperado, fisicoNorm, diferencia);
            caja.setEstado("CERRADA");
            caja.setIdUsuarioCierre(idUsuarioCierre);
            caja.setMontoCierre(fisicoNorm);
            caja.setDiferencia(diferencia);
            caja.setFechaCierre(LocalDateTime.now(ZONA_LIMA));

            String glosa = String.format("Cierre Z de turno (Esperado: S/ %s | Contado: S/ %s | Dif: S/ %s)",
                    montoEsperado, fisicoNorm, diferencia);
            MovimientoCaja movCierre = new MovimientoCaja(0, idCaja, idUsuarioCierre,
                    "CIERRE_Z", "EFECTIVO", fisicoNorm, glosa);
            cajaDAO.registrarMovimiento(conn, movCierre);

            List<InsumoCriticoTurno> criticos = obtenerInsumosCriticosTurno(conn);
            LOGGER.info("Caja #" + idCaja + " cerrada exitosamente. " + glosa
                    + " | Insumos críticos: " + criticos.size());
            return new CajaService.ResultadoCierreConActa(
                    caja, montoEsperado, fisicoNorm, diferencia, criticos, null);
        });
    }
}