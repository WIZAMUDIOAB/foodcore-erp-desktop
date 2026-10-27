package com.foodcore.service;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Asistencia;
import com.foodcore.model.Usuario;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
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

    public void marcarAsistenciaRapida(String dni) throws NegocioException, SQLException {
        if (dni == null || dni.trim().length() < 8) {
            throw new NegocioException("Ingrese un DNI válido de 8 dígitos.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Integer> empOpt = usuarioDAO.buscarEmpleadoPorDni(conn, dni.trim());
            if (empOpt.isEmpty()) {
                throw new NegocioException("No se encontró ningún empleado activo con el DNI ingresado.");
            }

            Asistencia a = new Asistencia();
            a.setIdEmpleado(empOpt.get());
            a.setFecha(LocalDate.now());
            a.setHoraIngreso(LocalTime.now());
            a.setEstado("ASISTIO");

            usuarioDAO.registrarMarcacionAsistencia(conn, a);
        }
    }

    public static Usuario getUsuarioSesionActual() {
        return usuarioSesionActual;
    }

    public static void logout() {
        usuarioSesionActual = null;
    }
}
