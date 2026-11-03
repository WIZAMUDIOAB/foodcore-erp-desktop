package com.foodcore.service;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Asistencia;
import com.foodcore.model.Empleado;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

/**
 * Servicio de Autenticación, Bloqueo por Fuerza Bruta (5 intentos / 15 min) y Control de Sesión
 * según SPEC-FOODCORE-v1.0 (§10.1, §10.2; REMEDIATION R-18).
 */
public class AuthService {

    public static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final UsuarioDAO usuarioDAO;
    private final EncriptadorPassword encriptador;
    private static Usuario usuarioSesionActual;

    public AuthService() {
        this(new com.foodcore.dao.impl.UsuarioDAOImpl(), new EncriptadorPassword());
    }

    public AuthService(UsuarioDAO usuarioDAO, EncriptadorPassword encriptador) {
        this.usuarioDAO = usuarioDAO;
        this.encriptador = encriptador;
    }

    public Usuario login(String username, String password) throws NegocioException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Debe ingresar usuario y contraseña.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            return login(conn, username.trim(), password.trim());
        }
    }

    public Usuario login(Connection conn, String username, String password) throws NegocioException, SQLException {
        Optional<Usuario> userOpt = usuarioDAO.buscarPorUsername(conn, username);
        if (userOpt.isEmpty()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Credenciales incorrectas o usuario inactivo.");
        }

        Usuario usuario = userOpt.get();
        if (!usuario.isActivo()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Credenciales incorrectas o usuario inactivo.");
        }

        LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            throw new NegocioException(
                CodigoError.USUARIO_BLOQUEADO,
                "Cuenta bloqueada temporalmente por exceso de intentos fallidos hasta " + usuario.getBloqueadoHasta()
            );
        }

        String hash = usuario.getPasswordHash();
        boolean passwordValido = (hash != null && hash.startsWith("$2"))
            ? encriptador.verificar(password, hash)
            : password.equals(hash);

        if (!passwordValido) {
            int nuevosIntentos = usuario.getIntentosFallidos() + 1;
            boolean bloquear = nuevosIntentos >= MAX_INTENTOS_FALLIDOS;
            usuarioDAO.registrarIntentoFallido(conn, usuario.getIdUsuario(), nuevosIntentos, bloquear);
            if (bloquear) {
                throw new NegocioException(
                    CodigoError.USUARIO_BLOQUEADO,
                    "Cuenta bloqueada por 15 minutos tras alcanzar 5 intentos fallidos."
                );
            }
            throw new NegocioException(
                CodigoError.CREDENCIALES_INVALIDAS,
                "Credenciales incorrectas (" + nuevosIntentos + "/" + MAX_INTENTOS_FALLIDOS + ")."
            );
        }

        usuarioDAO.resetearIntentosLogin(conn, usuario.getIdUsuario());
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);

        Set<Permiso> permisos = AutorizacionService.permisosPorDefectoParaRol(usuario.getNombreRol());
        SesionUsuario sesion = new SesionUsuario(
            usuario.getIdUsuario(),
            usuario.getNombreEmpleado() != null ? usuario.getNombreEmpleado() : usuario.getUsername(),
            usuario.getNombreRol(),
            permisos,
            "POS-01",
            ahora
        );
        SesionManager.iniciarSesion(sesion, usuario.isCambioPasswordRequerido());
        usuarioSesionActual = usuario;
        return usuario;
    }

    public void cambiarPasswordObligatorio(int idUsuario, String nuevoPassword) throws NegocioException, SQLException {
        if (nuevoPassword == null || nuevoPassword.trim().length() < 8) {
            throw new NegocioException(
                CodigoError.VALIDACION_ENTRADA,
                "La nueva contraseña debe tener al menos 8 caracteres."
            );
        }
        try (Connection conn = ConexionBD.getConnection()) {
            String nuevoHash = encriptador.hashear(nuevoPassword.trim());
            usuarioDAO.actualizarPasswordObligatorio(conn, idUsuario, nuevoHash);
            if (usuarioSesionActual != null && usuarioSesionActual.getIdUsuario() == idUsuario) {
                usuarioSesionActual.setCambioPasswordRequerido(false);
                usuarioSesionActual.setPasswordHash(nuevoHash);
            }
            SesionManager.completarCambioPassword();
        }
    }

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
                Asistencia a = new Asistencia();
                a.setIdEmpleado(emp.getIdEmpleado());
                a.setEstado("ASISTIO");
                usuarioDAO.registrarIngresoAsistencia(conn, a);

                return "✓ INGRESO REGISTRADO (" + horaActual + ")\nTrabajador: " + emp.getNombreCompleto() + "\nCargo: " + emp.getCargo();

            } else {
                Asistencia a = asistHoy.get();
                if (a.getHoraSalida() == null) {
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
        SesionManager.cerrarSesion();
    }
}
