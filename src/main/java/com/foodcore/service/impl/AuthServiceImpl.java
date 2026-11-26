package com.foodcore.service.impl;

import com.foodcore.api.security.JwtService;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
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
 * Contiene la lógica real de login, aislamiento de sesión Desktop/API, bloqueo atómico por fuerza bruta,
 * autenticación PIN endurecida, cambio obligatorio de contraseña y marcación de asistencia.
 */
public class AuthServiceImpl implements AuthService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();
    private final UsuarioDAO usuarioDAO;
    private final EncriptadorPassword encriptador;

    public AuthServiceImpl() {
        this(new UsuarioDAOImpl(), new EncriptadorPassword());
    }

    public AuthServiceImpl(UsuarioDAO usuarioDAO, EncriptadorPassword encriptador) {
        this.usuarioDAO = usuarioDAO;
        this.encriptador = encriptador;
    }

    @Override
    public Usuario login(String username, String password) throws NegocioException, SQLException {
        Usuario usuario = autenticarSoloCredenciales(username, password);
        establecerSesionActiva(usuario);
        return usuario;
    }

    @Override
    public Usuario login(Connection conn, String username, String password) throws NegocioException, SQLException {
        Usuario usuario = autenticarSoloCredenciales(conn, username, password);
        establecerSesionActiva(usuario);
        return usuario;
    }

    @Override
    public Usuario autenticarSoloCredenciales(String username, String password) throws NegocioException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Debe ingresar usuario y contraseña.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return autenticarSoloCredenciales(conn, username.trim(), password.trim());
        }
    }

    @Override
    public Usuario autenticarSoloCredenciales(Connection conn, String username, String password)
            throws NegocioException, SQLException {
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
                auditoriaDAO.registrarEventoSeguro(
                        conn, usuario.getIdUsuario(), "USUARIO_BLOQUEADO", "USUARIO", null,
                        "Intento de ingreso mientras cuenta está bloqueada hasta " + usuario.getBloqueadoHasta());
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
        boolean passwordValido = hash != null && hash.startsWith("$2") && encriptador.verificar(password, hash);

        if (!passwordValido) {
            int nuevosIntentos = usuario.getIntentosFallidos() + 1;
            boolean bloquear = nuevosIntentos >= MAX_INTENTOS_FALLIDOS;
            usuarioDAO.registrarIntentoFallido(conn, usuario.getIdUsuario(), nuevosIntentos, bloquear);

            Optional<Usuario> actualizado = usuarioDAO.buscarPorUsername(conn, usuario.getUsername());
            if (actualizado.isPresent()) {
                nuevosIntentos = Math.max(nuevosIntentos, actualizado.get().getIntentosFallidos());
                if (actualizado.get().getBloqueadoHasta() != null
                        && actualizado.get().getBloqueadoHasta().isAfter(ahora)) {
                    bloquear = true;
                }
            }

            auditoriaDAO.registrarEventoSeguro(
                    conn, usuario.getIdUsuario(), "LOGIN_FALLIDO", "USUARIO", null,
                    "Contraseña incorrecta para " + usuario.getUsername()
                            + " (intento " + nuevosIntentos + "/" + MAX_INTENTOS_FALLIDOS + ")");
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

        if (EncriptadorPassword.needsUpgrade(hash)) {
            String hashActualizado = encriptador.hashear(password);
            usuarioDAO.actualizarPasswordHash(conn, usuario.getIdUsuario(), hashActualizado);
            usuario.setPasswordHash(hashActualizado);
        }

        return usuario;
    }

    @Override
    public Optional<Usuario> autenticarPorPin(int idUsuario, String username, String pin) throws SQLException {
        Optional<Usuario> encontrado = autenticarPorPinStateless(idUsuario, username, pin);
        encontrado.ifPresent(this::establecerSesionActiva);
        return encontrado;
    }

    @Override
    public Optional<Usuario> autenticarPorPin(Connection conn, int idUsuario, String username, String pin)
            throws SQLException {
        Optional<Usuario> encontrado = autenticarPorPinStateless(conn, idUsuario, username, pin);
        encontrado.ifPresent(this::establecerSesionActiva);
        return encontrado;
    }

    @Override
    public Optional<Usuario> autenticarPorPinStateless(int idUsuario, String username, String pin) throws SQLException {
        if (pin == null || pin.trim().isEmpty()) {
            return Optional.empty();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return autenticarPorPinStateless(conn, idUsuario, username, pin.trim());
        }
    }

    @Override
    public Optional<Usuario> autenticarPorPinStateless(Connection conn, int idUsuario, String username, String pin)
            throws SQLException {
        if (conn == null || pin == null || pin.trim().isEmpty()) {
            return Optional.empty();
        }
        String pinLimpio = pin.trim();
        LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);
        Optional<Usuario> encontrado = Optional.empty();

        if (idUsuario > 0 || (username != null && !username.isBlank())) {
            Optional<Usuario> candidatoOpt = idUsuario > 0
                    ? usuarioDAO.buscarPorId(conn, idUsuario)
                    : usuarioDAO.buscarPorUsername(conn, username.trim());
            if (candidatoOpt.isEmpty()) {
                return Optional.empty();
            }
            Usuario candidato = candidatoOpt.get();
            if (!esPinPermitidoParaUsuario(candidato)) {
                return Optional.empty();
            }
            if (candidato.getBloqueadoHasta() != null) {
                if (candidato.getBloqueadoHasta().isAfter(ahora)) {
                    return Optional.empty();
                }
                usuarioDAO.resetearIntentosLogin(conn, candidato.getIdUsuario());
                candidato.setIntentosFallidos(0);
                candidato.setBloqueadoHasta(null);
            }
            if (verificarPinUsuario(pinLimpio, candidato)) {
                encontrado = Optional.of(candidato);
            } else {
                int nuevosIntentos = candidato.getIntentosFallidos() + 1;
                boolean bloquear = nuevosIntentos >= MAX_INTENTOS_FALLIDOS;
                usuarioDAO.registrarIntentoFallido(conn, candidato.getIdUsuario(), nuevosIntentos, bloquear);
                auditoriaDAO.registrarEventoSeguro(
                        conn, candidato.getIdUsuario(), "PIN_FALLIDO", "USUARIO", null,
                        "PIN incorrecto para " + candidato.getUsername()
                                + " (intento " + nuevosIntentos + "/" + MAX_INTENTOS_FALLIDOS + ")");
                return Optional.empty();
            }
        } else {
            encontrado = usuarioDAO.autenticarPorPinGeneral(conn, pinLimpio)
                    .filter(AuthServiceImpl::esPinPermitidoParaUsuario);
        }

        if (encontrado.isPresent()) {
            Usuario usuarioPin = encontrado.get();
            usuarioDAO.resetearIntentosLogin(conn, usuarioPin.getIdUsuario());
            usuarioPin.setIntentosFallidos(0);
            usuarioPin.setBloqueadoHasta(null);
            if (EncriptadorPassword.needsUpgrade(usuarioPin.getPinHash())) {
                String upgradedPinHash = encriptador.hashear(pinLimpio);
                usuarioDAO.actualizarPin(conn, usuarioPin.getIdUsuario(), null, upgradedPinHash);
                usuarioPin.setPinHash(upgradedPinHash);
            }
        }
        return encontrado;
    }

    /**
     * Evalúa si un usuario puede autenticarse mediante PIN rápido (bloquea cuentas inactivas,
     * cuentas con 2FA TOTP habilitado y roles privilegiados ADMIN / SUPERVISOR).
     */
    public static boolean esPinPermitidoParaUsuario(Usuario usuario) {
        if (usuario == null || !usuario.isActivo()) {
            return false;
        }
        if (usuario.isTotpHabilitado()) {
            return false;
        }
        String rolNorm = JwtService.normalizarRol(usuario.getNombreRol());
        return !"ADMIN".equals(rolNorm) && !"SUPERVISOR".equals(rolNorm);
    }

    public boolean verificarPinUsuario(String pinPlano, Usuario u) {
        if (pinPlano == null || pinPlano.isBlank() || u == null) {
            return false;
        }
        String hash = u.getPinHash();
        return hash != null && hash.startsWith("$2") && encriptador.verificar(pinPlano, hash);
    }

    @Override
    public void establecerSesionActiva(Usuario usuario) {
        if (usuario == null) {
            return;
        }
        iniciarSesionParaUsuario(usuario, LocalDateTime.now(ZONA_LIMA));
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
            return usuarioDAO.actualizarPin(conn, idUsuario, null, pinHash);
        }
    }

    @Override
    public void cambiarPasswordObligatorio(int idUsuario, String nuevoPassword) throws NegocioException, SQLException {
        if (nuevoPassword == null || !EncriptadorPassword.cumplePoliticaComplejidad(nuevoPassword.trim())) {
            throw new NegocioException(
                    CodigoError.VALIDACION_ENTRADA,
                    "La nueva contraseña debe tener al menos 8 caracteres, incluyendo mayúscula, minúscula y dígito."
            );
        }
        try (Connection conn = ConexionBD.getConnection()) {
            String nuevoHash = encriptador.hashear(nuevoPassword.trim());
            usuarioDAO.actualizarPasswordObligatorio(conn, idUsuario, nuevoHash);
            SesionManager.completarCambioPassword(idUsuario, nuevoHash);
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
            String horaActual = LocalTime.now(ZONA_LIMA).format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            if (asistHoy.isEmpty()) {
                Asistencia a = new Asistencia();
                a.setIdEmpleado(emp.getIdEmpleado());
                a.setEstado("ASISTIO");
                usuarioDAO.registrarIngresoAsistencia(conn, a);

                return "✓ INGRESO REGISTRADO (" + horaActual + ")\nTrabajador: " + emp.getNombreCompleto() + "\nCargo: " + emp.getCargo();
            } else {
                Asistencia a = asistHoy.get();
                if (a.getHoraSalida() == null) {
                    usuarioDAO.registrarSalidaAsistencia(conn, a.getIdAsistencia(), LocalTime.now(ZONA_LIMA));
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
        SesionManager.iniciarSesion(sesion, usuario, usuario.isCambioPasswordRequerido());
    }
}
