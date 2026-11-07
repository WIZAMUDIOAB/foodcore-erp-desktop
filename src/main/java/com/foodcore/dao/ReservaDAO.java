package com.foodcore.dao;

import com.foodcore.model.ReservaMesa;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReservaDAO {

    int crearReserva(Connection conn, ReservaMesa reserva) throws SQLException;

    List<ReservaMesa> listarTodas(Connection conn) throws SQLException;

    List<ReservaMesa> listarPorFecha(Connection conn, LocalDate fecha) throws SQLException;

    Optional<ReservaMesa> buscarReservaProximaParaMesa(Connection conn, String numeroMesa, LocalDate fecha, LocalTime horaActual, int minutosMargen) throws SQLException;

    boolean actualizarEstado(Connection conn, int idReserva, String nuevoEstado) throws SQLException;

    boolean eliminarReserva(Connection conn, int idReserva) throws SQLException;
}
