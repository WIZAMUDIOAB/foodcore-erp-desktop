package com.foodcore.service;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.MermaDAO;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.MermaDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.Merma;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AnalisisMermasService {

    private static final Logger LOGGER = Logger.getLogger(AnalisisMermasService.class.getName());

    private final MermaDAO mermaDAO;
    private final InsumoDAO insumoDAO;

    public AnalisisMermasService() {
        this(new MermaDAOImpl(), new InsumoDAOImpl());
    }

    public AnalisisMermasService(MermaDAO mermaDAO, InsumoDAO insumoDAO) {
        this.mermaDAO = mermaDAO;
        this.insumoDAO = insumoDAO;
    }

    public int registrarMerma(long idInsumo, int idUsuario, BigDecimal cantidad, String motivo, String observacion) throws Exception {
        if (idInsumo <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un insumo válido");
        }
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad mermada debe ser mayor a cero");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Insumo ins = insumoDAO.obtenerPorId(conn, idInsumo);
                BigDecimal costoUnit = ins != null && ins.getCostoUnitario() != null
                        ? ins.getCostoUnitario() : BigDecimal.ZERO;
                BigDecimal costoTotal = cantidad.multiply(costoUnit);

                Merma m = new Merma();
                m.setIdInsumo(idInsumo);
                m.setIdUsuario(idUsuario > 0 ? idUsuario : 1);
                m.setCantidad(cantidad);
                m.setMotivo(motivo != null ? motivo : "Quemado");
                m.setCostoUnitario(costoUnit);
                m.setCostoTotal(costoTotal);
                m.setObservacion(observacion);
                m.setFechaHora(LocalDateTime.now());

                int idMerma = mermaDAO.registrarMerma(conn, m);

                // Descuento atómico de stock e inserción en kardex
                try {
                    insumoDAO.registrarMovimientoKardexAtomico(
                            conn, idInsumo, "MERMA", cantidad, idUsuario > 0 ? idUsuario : 1,
                            "Merma (" + motivo + "): " + (observacion != null ? observacion : ""));
                } catch (Exception exKardex) {
                    LOGGER.log(Level.FINE, "No se pudo actualizar kardex directamente, continuando merma", exKardex);
                }

                conn.commit();
                return idMerma;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<Merma> listarTodas() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.listarTodas(conn);
        }
    }

    public Map<String, BigDecimal> obtenerMermasPorMotivo(int anio, int mes) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.obtenerMermasPorMotivo(conn, anio, mes);
        }
    }

    public Map<String, BigDecimal> obtenerMermasPorMes(int anio) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.obtenerMermasPorMes(conn, anio);
        }
    }

    public List<Merma> obtenerTopInsumosPerdida(int anio, int mes, int limite) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.obtenerTopInsumosPerdida(conn, anio, mes, limite);
        }
    }

    public List<String> obtenerAlertasInsumosCriticos(int anio, int mes) throws SQLException {
        List<String> alertas = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            List<Insumo> todos = insumoDAO.listarTodos(conn);
            for (Insumo ins : todos) {
                int cantMermas = mermaDAO.contarMermasPorInsumoEnMes(conn, ins.getIdInsumo(), anio, mes);
                if (cantMermas >= 3) {
                    alertas.add(String.format("Alerta Crítica: El insumo '%s' registra %d mermas en el mes actual. Revisar rotación y cocción.",
                            ins.getNombre(), cantMermas));
                }
            }
        }
        return alertas;
    }
}
