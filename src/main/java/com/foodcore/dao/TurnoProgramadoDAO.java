package com.foodcore.dao;

import com.foodcore.model.TurnoProgramado;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface TurnoProgramadoDAO {

    List<TurnoProgramado> listarSemana(Connection conn) throws SQLException;

    List<TurnoProgramado> listarPorUsuario(Connection conn, int idUsuario) throws SQLException;

    int asignarTurno(Connection conn, TurnoProgramado turno) throws SQLException;

    boolean eliminarTurno(Connection conn, int idTurnoProg) throws SQLException;

    int contarCobertura(Connection conn, String diaSemana, String turno) throws SQLException;

    boolean publicarHorariosSemana(Connection conn) throws SQLException;
}
