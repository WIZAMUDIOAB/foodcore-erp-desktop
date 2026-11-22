package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de consulta y cobro de cuentas por cobrar (SPEC-FOODCORE-v1.3-ENTERPRISE §3.3).
 * Centraliza la apertura de conexiones para el listado filtrado de CxC y la resolución
 * opcional de la caja abierta usada al registrar un cobro.
 */
public class CuentasPorCobrarService {

    private static final Logger LOGGER = Logger.getLogger(CuentasPorCobrarService.class.getName());

    private final CuentaPorCobrarDAO cuentaPorCobrarDAO;
    private final CajaDAO cajaDAO;

    public CuentasPorCobrarService() {
        this(new CuentaPorCobrarDAOImpl(), new CajaDAOImpl());
    }

    public CuentasPorCobrarService(CuentaPorCobrarDAO cuentaPorCobrarDAO, CajaDAO cajaDAO) {
        this.cuentaPorCobrarDAO = cuentaPorCobrarDAO;
        this.cajaDAO = cajaDAO;
    }

    /**
     * Lista las cuentas por cobrar según el estado seleccionado.
     *
     * @param filtro estado a filtrar; {@code null} o {@code TODAS} lista todas.
     * @return cuentas por cobrar que coinciden con el filtro.
     * @throws SQLException si falla la consulta en base de datos.
     */
    public List<CuentaPorCobrar> listarPorFiltro(String filtro) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return (filtro == null || "TODAS".equals(filtro))
                    ? cuentaPorCobrarDAO.listarTodas(conn)
                    : cuentaPorCobrarDAO.listarPorEstado(conn, filtro);
        }
    }

    /**
     * Obtiene el identificador de la caja abierta, si existe.
     *
     * @return identificador de la caja abierta, o {@code null} si no hay caja o falla la consulta.
     */
    public Integer obtenerIdCajaAbiertaOptional() {
        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Caja> cajaOpt = cajaDAO.obtenerCajaAbierta(conn);
            return cajaOpt.map(Caja::getIdCaja).orElse(null);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se pudo obtener la caja abierta para el cobro", ex);
            return null;
        }
    }
}
