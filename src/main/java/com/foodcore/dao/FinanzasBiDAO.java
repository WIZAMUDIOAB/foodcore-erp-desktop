package com.foodcore.dao;

import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.EstadoResultadosReporte;
import com.foodcore.model.MargenCategoria;
import com.foodcore.model.ReporteCohortes;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Acceso a datos analíticos para Finanzas y Business Intelligence (P&amp;L, comparativas, márgenes y cohortes) (v4.9.0).
 */
public interface FinanzasBiDAO {

    EstadoResultadosReporte generarEstadoResultados(Connection conn, LocalDate desde, LocalDate hasta)
            throws SQLException;

    ComparativaPeriodos obtenerComparativaPeriodos(Connection conn, LocalDate fechaReferencia)
            throws SQLException;

    List<MargenCategoria> calcularMargenesPorCategoria(Connection conn, LocalDate desde, LocalDate hasta)
            throws SQLException;

    ReporteCohortes generarAnalisisCohortes(Connection conn, LocalDate fechaCorte)
            throws SQLException;
}
