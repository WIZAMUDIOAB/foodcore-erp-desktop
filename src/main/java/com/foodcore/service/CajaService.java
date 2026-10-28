package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CajaService {

    private static final Logger LOGGER = Logger.getLogger(CajaService.class.getName());
    private final CajaDAO cajaDAO = new CajaDAOImpl();

    public Optional<Caja> obtenerCajaActiva() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.obtenerCajaAbierta(conn);
        }
    }

    public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial) throws NegocioException, SQLException {
        if (montoInicial == null || montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El monto inicial de caja no puede ser negativo.");
        }
        BigDecimal montoNorm = montoInicial.setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Optional<Caja> actual = cajaDAO.obtenerCajaAbierta(conn);
                if (actual.isPresent()) {
                    throw new NegocioException("Ya existe una caja abierta (" + actual.get().getNombreCaja() + "). Debe cerrarla antes.");
                }

                Caja caja = new Caja(0, idUsuario, nombreCaja, montoNorm, "ABIERTA");
                int id = cajaDAO.abrirCaja(conn, caja);

                // Registrar asiento de apertura en el libro de caja
                MovimientoCaja mov = new MovimientoCaja(0, id, idUsuario, "INGRESO", montoNorm, "Fondo de apertura de turno");
                cajaDAO.registrarMovimiento(conn, mov);

                conn.commit();
                LOGGER.info("Caja #" + id + " abierta con fondo inicial S/ " + montoNorm);
                return id;
            } catch (NegocioException ne) {
                conn.rollback();
                throw ne;
            } catch (Exception ex) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Error al abrir turno de caja. Rollback ejecutado.", ex);
                throw new SQLException("Error transaccional al abrir caja: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        }
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
        BigDecimal fisicoNorm = efectivoFisicoContado.setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Optional<Caja> opt = cajaDAO.obtenerCajaAbierta(conn);
                if (opt.isEmpty() || opt.get().getIdCaja() != idCaja) {
                    throw new NegocioException("La caja seleccionada no se encuentra abierta.");
                }

                Caja caja = opt.get();
                BigDecimal totalVentasEfectivo = cajaDAO.calcularTotalVentasTurno(conn, idCaja);
                BigDecimal montoEsperado = caja.getMontoApertura()
                    .add(totalVentasEfectivo)
                    .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
                BigDecimal diferencia = fisicoNorm
                    .subtract(montoEsperado)
                    .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);

                cajaDAO.cerrarCaja(conn, idCaja, idUsuarioCierre, fisicoNorm, diferencia);

                // Registrar asiento de auditoría del arqueo de cierre
                String glosa = String.format("Cierre de turno (Esperado: S/ %s | Contado: S/ %s | Dif: S/ %s)",
                    montoEsperado, fisicoNorm, diferencia);
                MovimientoCaja movCierre = new MovimientoCaja(0, idCaja, idUsuarioCierre, "EGRESO", fisicoNorm, glosa);
                cajaDAO.registrarMovimiento(conn, movCierre);

                conn.commit();
                LOGGER.info("Caja #" + idCaja + " cerrada exitosamente. " + glosa);
            } catch (NegocioException ne) {
                conn.rollback();
                throw ne;
            } catch (Exception ex) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Error al cerrar turno de caja. Rollback ejecutado.", ex);
                throw new SQLException("Error transaccional al cerrar caja: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
