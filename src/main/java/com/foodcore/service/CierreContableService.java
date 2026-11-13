package com.foodcore.service;

import com.foodcore.dao.CierreContableDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.dao.impl.CierreContableDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.model.CierreContable;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de gestión del Cierre Contable Mensual (v5.2.0): bloquea ventas,
 * devoluciones parciales y gastos operativos en meses cerrados.
 */
public class CierreContableService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final TransactionManager tx;
    private final CierreContableDAO cierreDAO;
    private final AuditoriaDAOImpl auditoriaDAO;

    public CierreContableService() {
        this(new JdbcTransactionManager(), new CierreContableDAOImpl(), new AuditoriaDAOImpl());
    }

    public CierreContableService(TransactionManager tx,
                                 CierreContableDAO cierreDAO,
                                 AuditoriaDAOImpl auditoriaDAO) {
        this.tx = tx;
        this.cierreDAO = cierreDAO;
        this.auditoriaDAO = auditoriaDAO;
    }

    public boolean estaPeriodoCerrado(int anio, int mes) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return estaPeriodoCerrado(conn, anio, mes);
        }
    }

    public boolean estaPeriodoCerrado(Connection conn, int anio, int mes) throws SQLException {
        if (conn == null || anio <= 0 || mes < 1 || mes > 12) {
            return false;
        }
        return cierreDAO.estaPeriodoCerrado(conn, anio, mes);
    }

    public Optional<CierreContable> obtenerCierre(int anio, int mes) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cierreDAO.obtenerPorPeriodo(conn, anio, mes);
        }
    }

    public List<CierreContable> listarCierres() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return cierreDAO.listarTodos(conn);
        }
    }

    public CierreContable cerrarMes(int anio, int mes, int idUsuario, String observaciones)
            throws NegocioException, SQLException {
        if (anio < 2000 || anio > 2100 || mes < 1 || mes > 12) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                    "Año o mes inválido para cierre contable.");
        }
        String obs = observaciones != null && !observaciones.isBlank()
                ? observaciones.trim() : "Cierre contable mensual definitivo";

        return tx.executeChecked(conn -> {
            if (cierreDAO.estaPeriodoCerrado(conn, anio, mes)) {
                throw new PeriodoCerradoException(anio, mes,
                        String.format("El período contable %04d-%02d ya se encuentra cerrado.", anio, mes));
            }
            CierreContable nuevo = new CierreContable();
            nuevo.setAnio(anio);
            nuevo.setMes(mes);
            nuevo.setIdUsuario(idUsuario > 0 ? idUsuario : 1);
            nuevo.setEstado("CERRADO");
            nuevo.setObservaciones(obs);

            CierreContable guardado = cierreDAO.registrarCierre(conn, nuevo);
            auditoriaDAO.registrarEventoSeguro(
                    conn,
                    idUsuario > 0 ? idUsuario : 1,
                    "CIERRE_CONTABLE_MES",
                    String.format("cierre_contable#%04d-%02d", anio, mes),
                    "ABIERTO",
                    "CERRADO | " + obs
            );
            return guardado;
        });
    }

    public void validarPeriodoAbierto(Connection conn, LocalDate fecha)
            throws PeriodoCerradoException, SQLException {
        LocalDate ref = fecha != null ? fecha : LocalDate.now(ZONA_LIMA);
        if (estaPeriodoCerrado(conn, ref.getYear(), ref.getMonthValue())) {
            throw new PeriodoCerradoException(ref.getYear(), ref.getMonthValue());
        }
    }

    public void validarPeriodoAbierto(Connection conn, LocalDateTime fechaHora)
            throws PeriodoCerradoException, SQLException {
        LocalDate ref = fechaHora != null ? fechaHora.toLocalDate() : LocalDate.now(ZONA_LIMA);
        validarPeriodoAbierto(conn, ref);
    }
}
