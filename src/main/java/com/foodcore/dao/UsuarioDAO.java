package com.foodcore.dao;

import com.foodcore.model.Asistencia;
import com.foodcore.model.Empleado;
import com.foodcore.model.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface UsuarioDAO {
    Optional<Usuario> autenticar(Connection conn, String username, String password) throws SQLException;
    Optional<Usuario> buscarPorUsername(Connection conn, String username) throws SQLException;
    void registrarIntentoFallido(Connection conn, int idUsuario, int nuevosIntentos, boolean bloquear15Minutos) throws SQLException;
    void resetearIntentosLogin(Connection conn, int idUsuario) throws SQLException;
    void actualizarPasswordObligatorio(Connection conn, int idUsuario, String nuevoPasswordHash) throws SQLException;
    Optional<Empleado> buscarEmpleadoPorDni(Connection conn, String dni) throws SQLException;
    Optional<Asistencia> obtenerAsistenciaHoy(Connection conn, int idEmpleado) throws SQLException;
    void registrarIngresoAsistencia(Connection conn, Asistencia asistencia) throws SQLException;
    void registrarSalidaAsistencia(Connection conn, int idAsistencia, LocalTime horaSalida) throws SQLException;

    Optional<Usuario> buscarPorDniOPin(Connection conn, String credencial) throws SQLException;
    List<Usuario> listarTodosActivos(Connection conn) throws SQLException;
    boolean actualizarDatosLaborales(Connection conn, int idUsuario, String dni, String pin,
                                     double sueldoBase, String horarioLaboral,
                                     LocalTime horaEntrada, LocalTime horaSalida) throws SQLException;
}
