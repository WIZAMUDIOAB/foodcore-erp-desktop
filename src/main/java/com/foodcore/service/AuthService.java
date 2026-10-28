package com.foodcore.service;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Asistencia;
import com.foodcore.model.Empleado;
import com.foodcore.model.Usuario;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class AuthService {

    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();
    private static Usuario usuarioSesionActual;

    public Usuario login(String username, String password) throws NegocioException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new NegocioException("Debe ingresar usuario y contraseña.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Usuario> userOpt = usuarioDAO.autenticar(conn, username.trim(), password.trim());
            if (userOpt.isEmpty()) {
                throw new NegocioException("Credenciales incorrectas o usuario inactivo.");
            }
            usuarioSesionActual = userOpt.get();
            return usuarioSesionActual;
        }
    }

    /**
     * Registra marcación de asistencia de un empleado:
     * - Si no tiene marcación hoy: registra INGRESO.
     * - Si ya tiene ingreso pero no salida: registra SALIDA.
     * - Si ya completó ingreso y salida: informa que ya registró su jornada.
     * @return Mensaje descriptivo con el tipo de marcación y nombre del empleado.
     */
    public String marcarAsistenciaInteligente(String dni) throws NegocioException, SQLException {
        if (dni == null || dni.trim().length() < 8) {
            throw new NegocioException("Ingrese un DNI válido de 8 dígitos.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Empleado> empOpt = usuarioDAO.buscarEmpleadoPorDni(conn, dni.trim());
            if (empOpt.isEmpty()) {
                throw new NegocioException("No se encontró ningún trabajador activo con el DNI: " + dni);
            }

            Empleado emp = empOpt.get();
            Optional<Asistencia> asistHoy = usuarioDAO.obtenerAsistenciaHoy(conn, emp.getIdEmpleado());
            String horaActual = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            if (asistHoy.isEmpty()) {
                // Primer marcaje del día: ENTRADA
                Asistencia a = new Asistencia();
                a.setIdEmpleado(emp.getIdEmpleado());
                a.setEstado("ASISTIO");
                usuarioDAO.registrarIngresoAsistencia(conn, a);

                return "✓ INGRESO REGISTRADO (" + horaActual + ")\nTrabajador: " + emp.getNombreCompleto() + "\nCargo: " + emp.getCargo();

            } else {
                Asistencia a = asistHoy.get();
                if (a.getHoraSalida() == null) {
                    // Segundo marcaje del día: SALIDA
                    usuarioDAO.registrarSalidaAsistencia(conn, a.getIdAsistencia(), LocalTime.now());
                    return "✓ SALIDA REGISTRADA (" + horaActual + ")\nTrabajador: " + emp.getNombreCompleto() + "\n¡Buen descanso!";
                } else {
                    return "ℹ JORNADA COMPLETA\nTrabajador: " + emp.getNombreCompleto() + "\nYa registró ingreso (" + a.getHoraIngreso() + ") y salida (" + a.getHoraSalida() + ") hoy.";
                }
            }
        }
    }

    public static Usuario getUsuarioSesionActual() {
        return usuarioSesionActual;
    }

    public static void logout() {
        usuarioSesionActual = null;
    }
}
