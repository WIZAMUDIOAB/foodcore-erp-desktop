package com.foodcore.dao;

import com.foodcore.model.Merma;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface MermaDAO {

    int registrarMerma(Connection conn, Merma merma) throws SQLException;

    List<Merma> listarTodas(Connection conn) throws SQLException;

    List<Merma> listarPorRango(Connection conn, LocalDateTime inicio, LocalDateTime fin) throws SQLException;

    Map<String, BigDecimal> obtenerMermasPorMotivo(Connection conn, int anio, int mes) throws SQLException;

    Map<String, BigDecimal> obtenerMermasPorMes(Connection conn, int anio) throws SQLException;

    List<Merma> obtenerTopInsumosPerdida(Connection conn, int anio, int mes, int limite) throws SQLException;

    int contarMermasPorInsumoEnMes(Connection conn, long idInsumo, int anio, int mes) throws SQLException;

    /**
     * Listado reciente de movimientos {@code MERMA*} del Kárdex enriquecido con insumo y unidad de medida
     * para la vista de registro de mermas (SPEC-FOODCORE-v1.2 §3.3), ordenado del más reciente al más antiguo.
     */
    List<Merma> listarMovimientosMermaRecientes(Connection conn, int limite) throws SQLException;
}
