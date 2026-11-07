package com.foodcore.dao;

import com.foodcore.model.Asistencia;
import com.foodcore.model.FilaPlanilla;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AsistenciaDAO {

    Optional<Asistencia> buscarMarcajeAbierto(Connection conn, int idUsuario, LocalDate fecha) throws SQLException;

    int registrarEntrada(Connection conn, Asistencia asistencia) throws SQLException;

    boolean registrarSalida(Connection conn, int idAsistencia, LocalDateTime horaSalida,
                            double horasTrabajadas, double horasExtra) throws SQLException;

    List<Asistencia> listarPorFecha(Connection conn, LocalDate fecha) throws SQLException;

    List<Asistencia> listarPorRango(Connection conn, LocalDate inicio, LocalDate fin) throws SQLException;

    List<Asistencia> listarActivosAhora(Connection conn) throws SQLException;

    List<FilaPlanilla> calcularPlanillaMensual(Connection conn, int anio, int mes) throws SQLException;
}
