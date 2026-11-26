package com.foodcore.service.caja;

import com.foodcore.dao.CajaDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.service.CierreContableService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class DefaultCajaTurnoOperacionesUseCase implements CajaTurnoOperacionesUseCase {
    private static final Logger LOGGER = Logger.getLogger(DefaultCajaTurnoOperacionesUseCase.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final TransactionManager tx;
    private final CajaDAO cajaDAO;
    private final CierreContableService cierreContableService;
    private final JdbcTransactionManager.ConnectionSupplier connectionSupplier;

    public DefaultCajaTurnoOperacionesUseCase(TransactionManager tx, CajaDAO cajaDAO,
                                               CierreContableService cierreContableService,
                                               JdbcTransactionManager.ConnectionSupplier connectionSupplier) {
        this.tx = Objects.requireNonNull(tx);
        this.cajaDAO = Objects.requireNonNull(cajaDAO);
        this.cierreContableService = Objects.requireNonNull(cierreContableService);
        this.connectionSupplier = connectionSupplier != null ? connectionSupplier : ConexionBD::getConnection;
    }

    public DefaultCajaTurnoOperacionesUseCase(TransactionManager tx, CajaDAO cajaDAO,
                                               CierreContableService cierreContableService) {
        this(tx, cajaDAO, cierreContableService, ConexionBD::getConnection);
    }

    @Override
    public Optional<Caja> obtenerCajaActiva() throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return cajaDAO.obtenerCajaAbierta(conn);
        }
    }

    @Override
    public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial)
            throws NegocioException, SQLException {
        if (montoInicial == null || montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El monto inicial de caja no puede ser negativo.");
        }
        BigDecimal montoNorm = DesgloseFiscal.normalizarMoneda(montoInicial);

        return tx.executeChecked(conn -> {
            Optional<Caja> actual = cajaDAO.obtenerCajaAbierta(conn);
            if (actual.isPresent()) {
                throw new NegocioException("Ya existe una caja abierta (" + actual.get().getNombreCaja()
                        + "). Debe cerrarla antes.");
            }

            Caja caja = new Caja(0, idUsuario, nombreCaja, montoNorm, "ABIERTA");
            int id = cajaDAO.abrirCaja(conn, caja);
            MovimientoCaja mov = new MovimientoCaja(0, id, idUsuario, "INGRESO", "EFECTIVO", montoNorm,
                    "Fondo de apertura de turno");
            cajaDAO.registrarMovimiento(conn, mov);

            LOGGER.info("Caja #" + id + " abierta con fondo inicial S/ " + montoNorm);
            return id;
        });
    }

    @Override
    public void registrarGastoOperativo(int idCaja, int idUsuario, BigDecimal monto, String concepto,
                                        Integer idCategoriaGasto) throws NegocioException, SQLException {
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
                    0, idCaja, idUsuario, "EGRESO", "EFECTIVO", montoNorm, glosa, idCategoriaGasto);
            cajaDAO.registrarMovimiento(conn, movGasto);
            LOGGER.info("Gasto operativo registrado en Caja #" + idCaja + ": S/ " + montoNorm + " (" + glosa + ")");
        });
    }

    @Override
    public List<CategoriaGasto> listarCategoriasGastoActivas() throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return cajaDAO.listarCategoriasGastoActivas(conn);
        }
    }

    @Override
    public List<MovimientoCaja> listarGastosTurno(int idCaja) throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return cajaDAO.listarGastosTurno(conn, idCaja);
        }
    }

    @Override
    public BigDecimal calcularVentasTurno(int idCaja) throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return cajaDAO.calcularTotalVentasTurno(conn, idCaja);
        }
    }

    @Override
    public BigDecimal calcularGastosTurno(int idCaja) throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return cajaDAO.calcularTotalGastosTurno(conn, idCaja);
        }
    }

    @Override
    public Map<String, Object[]> obtenerResumenVentasCierreZ(int idCaja) throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return cajaDAO.obtenerResumenVentasCierreZ(conn, idCaja);
        }
    }

    @Override
    public Map<String, BigDecimal> obtenerTotalesCierreZ(int idCaja) throws SQLException {
        try (Connection conn = connectionSupplier.getConnection()) {
            return cajaDAO.obtenerTotalesPorMedioPago(conn, idCaja);
        }
    }
}