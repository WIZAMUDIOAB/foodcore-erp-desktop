package com.foodcore.dao;

import com.foodcore.model.Asistencia;
import com.foodcore.model.Empleado;
import com.foodcore.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Optional;

public interface UsuarioDAO {
    Optional<Usuario> autenticar(Connection conn, String username, String password) throws SQLException;
    Optional<Empleado> buscarEmpleadoPorDni(Connection conn, String dni) throws SQLException;
    Optional<Asistencia> obtenerAsistenciaHoy(Connection conn, int idEmpleado) throws SQLException;
    void registrarIngresoAsistencia(Connection conn, Asistencia asistencia) throws SQLException;
    void registrarSalidaAsistencia(Connection conn, int idAsistencia, LocalTime horaSalida) throws SQLException;
}
