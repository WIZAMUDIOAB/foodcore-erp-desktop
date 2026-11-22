package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.CuentaPorPagarDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CuentasPorPagarService {
    private final CuentaPorPagarDAO cuentaPorPagarDAO = new CuentaPorPagarDAOImpl();
    private final CajaDAO cajaDAO = new CajaDAOImpl();

    public List<CuentaPorPagar> listarPorFiltro(String filtro) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return filtro == null || "TODAS".equals(filtro)
                    ? cuentaPorPagarDAO.listarTodas(conn)
                    : cuentaPorPagarDAO.listarPorEstado(conn, filtro);
        }
    }

    public Integer obtenerIdCajaAbiertaOptional() {
        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Caja> caja = cajaDAO.obtenerCajaAbierta(conn);
            return caja.map(Caja::getIdCaja).orElse(null);
        } catch (SQLException | RuntimeException ignored) {
            return null;
        }
    }
}
