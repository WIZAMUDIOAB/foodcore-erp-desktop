package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class CajaService {

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

        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Caja> actual = cajaDAO.obtenerCajaAbierta(conn);
            if (actual.isPresent()) {
                throw new NegocioException("Ya existe una caja abierta (" + actual.get().getNombreCaja() + "). Debe cerrarla antes.");
            }

            Caja caja = new Caja(0, idUsuario, nombreCaja, montoInicial, "ABIERTA");
            int id = cajaDAO.abrirCaja(conn, caja);

            // Registrar movimiento de apertura
            MovimientoCaja mov = new MovimientoCaja(0, id, idUsuario, "INGRESO", montoInicial, "Fondo de apertura de turno");
            cajaDAO.registrarMovimiento(conn, mov);

            return id;
        }
    }

    public BigDecimal calcularVentasTurno(int idCaja) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cajaDAO.calcularTotalVentasTurno(conn, idCaja);
        }
    }

    public void cerrarTurnoCaja(int idCaja, int idUsuarioCierre, BigDecimal efectivoFisicoContado) throws NegocioException, SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Caja> opt = cajaDAO.obtenerCajaAbierta(conn);
            if (opt.isEmpty() || opt.get().getIdCaja() != idCaja) {
                throw new NegocioException("La caja seleccionada no se encuentra abierta.");
            }

            Caja caja = opt.get();
            BigDecimal totalVentas = cajaDAO.calcularTotalVentasTurno(conn, idCaja);
            BigDecimal montoEsperado = caja.getMontoApertura().add(totalVentas);
            BigDecimal diferencia = efectivoFisicoContado.subtract(montoEsperado);

            cajaDAO.cerrarCaja(conn, idCaja, idUsuarioCierre, efectivoFisicoContado, diferencia);
        }
    }
}
