package com.foodcore.dao;

import com.foodcore.model.Vacaciones;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO para saldo administrativo anual de vacaciones del personal.
 */
public interface VacacionesDAO {

    Optional<Vacaciones> buscarPorUsuarioYAnio(Connection conn, int idUsuario, int anio) throws SQLException;

    void guardarOActualizar(Connection conn, Vacaciones vacaciones) throws SQLException;

    void incrementarDiasGozados(Connection conn, int idUsuario, int anio, int dias) throws SQLException;
}
