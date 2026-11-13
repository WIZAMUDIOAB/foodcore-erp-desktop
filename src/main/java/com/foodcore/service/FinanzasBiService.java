package com.foodcore.service;

import com.foodcore.dao.FinanzasBiDAO;
import com.foodcore.dao.impl.FinanzasBiDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.EstadoResultadosReporte;
import com.foodcore.model.MargenCategoria;
import com.foodcore.model.ReporteCohortes;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Servicio financiero y de Business Intelligence para P&amp;L, comparativas de períodos,
 * márgenes por categoría y análisis de cohortes (v4.9.0).
 */
public class FinanzasBiService {

    private final FinanzasBiDAO finanzasBiDAO;

    public FinanzasBiService() {
        this(new FinanzasBiDAOImpl());
    }

    public FinanzasBiService(FinanzasBiDAO finanzasBiDAO) {
        this.finanzasBiDAO = finanzasBiDAO;
    }

    /**
     * Genera el Estado de Resultados (P&amp;L) consolidado para el rango de fechas indicado.
     */
    public EstadoResultadosReporte generarEstadoResultados(LocalDate desde, LocalDate hasta)
            throws NegocioException, SQLException {
        if (desde == null || hasta == null) {
            throw new NegocioException("Debe especificar el rango de fechas (Desde / Hasta).");
        }
        if (desde.isAfter(hasta)) {
            throw new NegocioException("La fecha 'Desde' no puede ser posterior a la fecha 'Hasta'.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return finanzasBiDAO.generarEstadoResultados(conn, desde, hasta);
        }
    }

    /**
     * Calcula las comparativas de ventas "Hoy vs. Ayer" y "Este Mes vs. Mes Anterior".
     */
    public ComparativaPeriodos obtenerComparativaPeriodos(LocalDate fechaReferencia) throws SQLException {
        LocalDate ref = fechaReferencia != null ? fechaReferencia : LocalDate.now();
        try (Connection conn = ConexionBD.getConnection()) {
            return finanzasBiDAO.obtenerComparativaPeriodos(conn, ref);
        }
    }

    /**
     * Calcula ingresos, costo por receta y margen bruto por categoría ordenados de mayor a menor margen.
     */
    public List<MargenCategoria> calcularMargenesPorCategoria(LocalDate desde, LocalDate hasta)
            throws NegocioException, SQLException {
        if (desde == null || hasta == null) {
            throw new NegocioException("Debe especificar el rango de fechas (Desde / Hasta).");
        }
        if (desde.isAfter(hasta)) {
            throw new NegocioException("La fecha 'Desde' no puede ser posterior a la fecha 'Hasta'.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return finanzasBiDAO.calcularMargenesPorCategoria(conn, desde, hasta);
        }
    }

    /**
     * Genera el análisis de cohortes mensuales y la tabla de retención (Nuevos, Recurrentes y Perdidos &gt; 60d).
     */
    public ReporteCohortes generarAnalisisCohortes(LocalDate fechaCorte) throws SQLException {
        LocalDate corte = fechaCorte != null ? fechaCorte : LocalDate.now();
        try (Connection conn = ConexionBD.getConnection()) {
            return finanzasBiDAO.generarAnalisisCohortes(conn, corte);
        }
    }
}
