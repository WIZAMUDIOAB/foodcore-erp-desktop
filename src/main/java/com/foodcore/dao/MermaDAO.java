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
}
