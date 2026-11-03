package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Servicio de control de turnos de caja, arqueo físico y cierre Z con transacciones explícitas
 * mediante {@link TransactionManager} (SPEC-FOODCORE-v1.0 §6.4, §7.1, §8.3).
 */
public class CajaService {

    private static final Logger LOGGER = Logger.getLogger(CajaService.class.getName());
    private final TransactionManager tx;
    private final CajaDAO cajaDAO;

    public CajaService() {
        this(new JdbcTransactionManager(), new CajaDAOImpl());
    }

    public CajaService(TransactionManager tx, CajaDAO cajaDAO) {
        this.tx = tx;
        this.cajaDAO = cajaDAO;
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

            MovimientoCaja mov = new MovimientoCaja(0, id, idUsuario, "INGRESO", montoNorm, "Fondo de apertura de turno");
            cajaDAO.registrarMovimiento(conn, mov);

            LOGGER.info("Caja #" + id + " abierta con fondo inicial S/ " + montoNorm);
            return id;
        });
    }

    public BigDecimal calcularVentasTurno(int idCaja) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.calcularTotalVentasTurno(conn, idCaja);
        }
    }

    public void cerrarTurnoCaja(int idCaja, int idUsuarioCierre, BigDecimal efectivoFisicoContado) throws NegocioException, SQLException {
        if (efectivoFisicoContado == null || efectivoFisicoContado.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El efectivo físico contado en el arqueo no puede ser negativo.");
        }
        BigDecimal fisicoNorm = DesgloseFiscal.normalizarMoneda(efectivoFisicoContado);

        tx.executeVoidChecked(conn -> {
            Optional<Caja> opt = cajaDAO.obtenerCajaAbierta(conn);
            if (opt.isEmpty() || opt.get().getIdCaja() != idCaja) {
                throw new CajaCerradaException("La caja seleccionada no se encuentra abierta.");
            }

            Caja caja = opt.get();
            BigDecimal totalVentasEfectivo = cajaDAO.calcularTotalVentasTurno(conn, idCaja);
            BigDecimal montoEsperado = DesgloseFiscal.normalizarMoneda(caja.getMontoApertura().add(totalVentasEfectivo));
            BigDecimal diferencia = DesgloseFiscal.normalizarMoneda(fisicoNorm.subtract(montoEsperado));

            cajaDAO.cerrarCaja(conn, idCaja, idUsuarioCierre, fisicoNorm, diferencia);

            String glosa = String.format("Cierre Z de turno (Esperado: S/ %s | Contado: S/ %s | Dif: S/ %s)",
                montoEsperado, fisicoNorm, diferencia);
            MovimientoCaja movCierre = new MovimientoCaja(0, idCaja, idUsuarioCierre, "EGRESO", fisicoNorm, glosa);
            cajaDAO.registrarMovimiento(conn, movCierre);

            LOGGER.info("Caja #" + idCaja + " cerrada exitosamente. " + glosa);
        });
    }
}
