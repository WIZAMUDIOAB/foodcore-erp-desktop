package com.foodcore.service.impl;

import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.UsuarioDAOImpl;
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
import com.foodcore.service.AuthService;
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
 * Implementación canónica de {@link AuthService} en paquete {@code com.foodcore.service.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-18) y SPEC-FOODCORE-v4.3.0 (§1.2).
 * Contiene la lógica real de login, bloqueo por fuerza bruta, autenticación PIN,
 * cambio obligatorio de contraseña y marcación de asistencia.
 */
public class AuthServiceImpl extends AuthService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    public AuthServiceImpl() {
        super(new UsuarioDAOImpl(), new EncriptadorPassword());
    }

    public AuthServiceImpl(UsuarioDAO usuarioDAO, EncriptadorPassword encriptador) {
        super(usuarioDAO, encriptador);
    }

    @Override
    public Usuario login(String username, String password) throws NegocioException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Debe ingresar usuario y contraseña.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            return login(conn, username.trim(), password.trim());
        }
    }

    @Override
    public Usuario login(Connection conn, String username, String password) throws NegocioException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Debe ingresar usuario y contraseña.");
        }

        Optional<Usuario> userOpt = usuarioDAO.buscarPorUsername(conn, username.trim());
        if (userOpt.isEmpty()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Credenciales incorrectas o usuario inactivo.");
        }

        Usuario usuario = userOpt.get();
        if (!usuario.isActivo()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Credenciales incorrectas o usuario inactivo.");
        }

        LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);
        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(ahora)) {
                throw new NegocioException(
                        CodigoError.USUARIO_BLOQUEADO,
                        "Cuenta bloqueada temporalmente por exceso de intentos fallidos hasta " + usuario.getBloqueadoHasta()
                );
            } else {
                usuarioDAO.resetearIntentosLogin(conn, usuario.getIdUsuario());
                usuario.setIntentosFallidos(0);
                usuario.setBloqueadoHasta(null);
            }
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

        iniciarSesionParaUsuario(usuario, ahora);
        return usuario;
    }

    @Override
    public Optional<Usuario> autenticarPorPin(int idUsuario, String username, String pin) throws SQLException {
        if (pin == null || pin.trim().isEmpty()) {
            return Optional.empty();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return autenticarPorPin(conn, idUsuario, username, pin.trim());
        }
    }

    public Optional<Usuario> autenticarPorPin(Connection conn, int idUsuario, String username, String pin) throws SQLException {
        if (conn == null || pin == null || pin.trim().isEmpty()) {
            return Optional.empty();
        }
        String pinLimpio = pin.trim();
        Optional<Usuario> encontrado = Optional.empty();

        if (idUsuario > 0) {
            encontrado = usuarioDAO.autenticarPorPin(conn, idUsuario, pinLimpio);
        } else if (username != null && !username.isBlank()) {
            Optional<Usuario> optU = usuarioDAO.buscarPorUsername(conn, username.trim());
            if (optU.isPresent() && verificarPinUsuario(pinLimpio, optU.get())) {
                encontrado = optU;
            }
        } else {
            encontrado = usuarioDAO.autenticarPorPinGeneral(conn, pinLimpio);
        }

        if (encontrado.isPresent()) {
            iniciarSesionParaUsuario(encontrado.get(), LocalDateTime.now(ZONA_LIMA));
        }
        return encontrado;
    }

    public boolean verificarPinUsuario(String pinPlano, Usuario u) {
        if (pinPlano == null || pinPlano.isBlank() || u == null) {
            return false;
        }
        String hash = u.getPinHash();
        if (hash != null && hash.startsWith("$2") && encriptador.verificar(pinPlano, hash)) {
            return true;
        }
        return pinPlano.equals(u.getPin());
    }

    @Override
    public boolean actualizarPinUsuario(int idUsuario, String nuevoPin) throws NegocioException, SQLException {
        if (nuevoPin == null || !nuevoPin.trim().matches("^\\d{4,6}$")) {
            throw new NegocioException(
                    CodigoError.VALIDACION_ENTRADA,
                    "El PIN debe tener entre 4 y 6 dígitos numéricos."
            );
        }
        String pinLimpio = nuevoPin.trim();
        String pinHash = encriptador.hashear(pinLimpio);
        try (Connection conn = ConexionBD.getConnection()) {
            return usuarioDAO.actualizarPin(conn, idUsuario, pinLimpio, pinHash);
        }
    }

    @Override
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
            Usuario actual = getUsuarioSesionActual();
            if (actual != null && actual.getIdUsuario() == idUsuario) {
                actual.setCambioPasswordRequerido(false);
                actual.setPasswordHash(nuevoHash);
            }
            SesionManager.completarCambioPassword();
        }
    }

    @Override
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
                    return "ℹ JORNADA COMPLETA\nTrabajador: " + emp.getNombreCompleto()
                            + "\nYa registró ingreso (" + a.getHoraIngreso() + ") y salida (" + a.getHoraSalida() + ") hoy.";
                }
            }
        }
    }

    private void iniciarSesionParaUsuario(Usuario usuario, LocalDateTime ahora) {
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
        setUsuarioSesionActual(usuario);
    }
}
