package com.foodcore.service;

import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de pruebas de integración para {@link AuthServiceImpl} (B3-bis, v4.3.0 §1.2).
 */
class AuthServiceImplTest {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        authService = new AuthServiceImpl(new UsuarioDAOImpl(), new EncriptadorPassword());
        AuthService.logout();
    }

    @Test
    @DisplayName("B3-bis (1/6): Login con usuario inactivo (activo = 0) o vacío lanza NegocioException CREDENCIALES_INVALIDAS")
    void testLogin_UsuarioInactivo_Falla() throws Exception {
        assertThrows(NegocioException.class, () -> authService.login("", "Admin#2026!"));
        assertThrows(NegocioException.class, () -> authService.login("admin", " "));
        assertThrows(NegocioException.class, () -> authService.login("usuario_inexistente", "Admin#2026!"));

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE usuario SET activo = 0 WHERE username = 'mozo'")) {
            ps.executeUpdate();
        }

        AuthServiceImpl defaultAuth = new AuthServiceImpl();
        NegocioException ex = assertThrows(
                NegocioException.class,
                () -> defaultAuth.login("mozo", "Admin#2026!")
        );
        assertEquals(CodigoError.CREDENCIALES_INVALIDAS, ex.getCodigoError());
    }

    @Test
    @DisplayName("B3-bis (2/6): Login con bloqueo vigente o 5 intentos fallidos lanza USUARIO_BLOQUEADO")
    void testLogin_BloqueoVigente_Falla() throws Exception {
        // Primero probar incremento de intentos fallidos y bloqueo automático al 5to intento
        for (int i = 1; i <= 4; i++) {
            NegocioException exIntento = assertThrows(
                    NegocioException.class,
                    () -> authService.login("mozo", "PasswordIncorrecto!")
            );
            assertEquals(CodigoError.CREDENCIALES_INVALIDAS, exIntento.getCodigoError());
        }
        NegocioException exQuinto = assertThrows(
                NegocioException.class,
                () -> authService.login("mozo", "PasswordIncorrecto!")
        );
        assertEquals(CodigoError.USUARIO_BLOQUEADO, exQuinto.getCodigoError());

        // Probar también con cajero pre-bloqueado a futuro
        LocalDateTime futuro = LocalDateTime.now(ZONA_LIMA).plusMinutes(15);
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE usuario SET intentos_fallidos = 5, bloqueado_hasta = ? WHERE username = 'cajero'"
             )) {
            ps.setObject(1, futuro);
            ps.executeUpdate();
        }

        try (Connection conn = ConexionBD.getConnection()) {
            NegocioException ex = assertThrows(
                    NegocioException.class,
                    () -> authService.login(conn, "cajero", "Admin#2026!")
            );
            assertEquals(CodigoError.USUARIO_BLOQUEADO, ex.getCodigoError());
        }
    }

    @Test
    @DisplayName("B3-bis (3/6): Login con bloqueo expirado desbloquea la cuenta, resetea intentos y autentica")
    void testLogin_BloqueoExpirado_Desbloquea() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement clean = conn.prepareStatement(
                    "UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE username = 'cajero'")) {
                clean.executeUpdate();
            }
            LocalDateTime pasado = LocalDateTime.now(ZONA_LIMA).minusMinutes(5);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE usuario SET intentos_fallidos = 5, bloqueado_hasta = ? WHERE username = 'cajero'")) {
                ps.setObject(1, pasado);
                ps.executeUpdate();
            }
        }

        Usuario autenticado = authService.login("cajero", "Admin#2026!");
        assertNotNull(autenticado);
        assertEquals(0, autenticado.getIntentosFallidos());
        assertNull(autenticado.getBloqueadoHasta());
        assertNotNull(AuthService.getUsuarioSesionActual());

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT intentos_fallidos, bloqueado_hasta FROM usuario WHERE username = 'cajero'"
             );
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("intentos_fallidos"));
            assertNull(rs.getTimestamp("bloqueado_hasta"));
        }
    }

    @Test
    @DisplayName("B3-bis (4/6): Cambio de password con política fallida (< 8 caracteres) lanza VALIDACION_ENTRADA")
    void testCambioPassword_PoliticaFallida() throws Exception {
        Usuario admin = authService.login("admin", "Admin#2026!");
        assertNotNull(admin);

        NegocioException exCorta = assertThrows(
                NegocioException.class,
                () -> authService.cambiarPasswordObligatorio(admin.getIdUsuario(), "12345")
        );
        assertEquals(CodigoError.VALIDACION_ENTRADA, exCorta.getCodigoError());

        NegocioException exNula = assertThrows(
                NegocioException.class,
                () -> authService.cambiarPasswordObligatorio(admin.getIdUsuario(), null)
        );
        assertEquals(CodigoError.VALIDACION_ENTRADA, exNula.getCodigoError());

        authService.cambiarPasswordObligatorio(admin.getIdUsuario(), "Admin#Nueva2026!");
        Usuario reLogin = authService.login("admin", "Admin#Nueva2026!");
        assertNotNull(reLogin);
    }

    @Test
    @DisplayName("B3-bis (5/6): Configuración y autenticación por PIN con BCrypt en AuthServiceImpl")
    void testConfiguracionYAutenticacionPorPin() throws Exception {
        assertThrows(NegocioException.class, () -> authService.actualizarPinUsuario(3, "12a4"));
        assertThrows(NegocioException.class, () -> authService.actualizarPinUsuario(3, "12"));

        boolean actualizado = authService.actualizarPinUsuario(3, "4567");
        assertTrue(actualizado);

        Optional<Usuario> porId = authService.autenticarPorPin(3, null, "4567");
        assertTrue(porId.isPresent());
        assertEquals(3, porId.get().getIdUsuario());

        Optional<Usuario> porUsername = authService.autenticarPorPin(0, "mozo", "4567");
        assertTrue(porUsername.isPresent());

        Optional<Usuario> general = authService.autenticarPorPin(0, null, "4567");
        assertTrue(general.isPresent());

        Optional<Usuario> invalido = authService.autenticarPorPin(3, "mozo", "0000");
        assertFalse(invalido.isPresent());
        assertFalse(authService.autenticarPorPin(3, "mozo", "").isPresent());
    }

    @Test
    @DisplayName("B3-bis (6/6): Marcación de asistencia inteligente (ingreso, salida y jornada completa)")
    void testMarcarAsistenciaInteligente() throws Exception {
        assertThrows(NegocioException.class, () -> authService.marcarAsistenciaInteligente("123"));
        assertThrows(NegocioException.class, () -> authService.marcarAsistenciaInteligente("99999999"));

        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT IGNORE INTO empleado (dni, nombres, apellidos, telefono, cargo, sueldo_base, fecha_ingreso, estado) "
                    + "VALUES ('70809010', 'Carlos', 'Mendoza', '999888777', 'MOZO', 1200.00, CURRENT_DATE(), TRUE)")) {
                ins.executeUpdate();
            }
            try (PreparedStatement clean = conn.prepareStatement("DELETE FROM asistencia WHERE fecha = CURRENT_DATE()")) {
                clean.executeUpdate();
            }

            String dni = "70809010";
            String ingreso = authService.marcarAsistenciaInteligente(dni);
            assertTrue(ingreso.contains("INGRESO REGISTRADO"));

            String salida = authService.marcarAsistenciaInteligente(dni);
            assertTrue(salida.contains("SALIDA REGISTRADA"));

            String completa = authService.marcarAsistenciaInteligente(dni);
            assertTrue(completa.contains("JORNADA COMPLETA"));
        }
    }

    @Test
    @DisplayName("Tarea 4: Login exitoso con hash BCrypt coste 10 actualiza automáticamente a coste 12 en BD")
    void testLogin_ActualizaHashCoste10ACoste12() throws Exception {
        String passwordPlano = "Admin#2026!";
        String hashAntiguo = EncriptadorPassword.generarHashConCoste(passwordPlano, 10);
        assertTrue(EncriptadorPassword.needsUpgrade(hashAntiguo));

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE usuario SET password_hash = ?, activo = 1, intentos_fallidos = 0, bloqueado_hasta = NULL WHERE username = 'mozo'"
             )) {
            ps.setString(1, hashAntiguo);
            ps.executeUpdate();
        }

        Usuario autenticado = authService.login("mozo", passwordPlano);
        assertNotNull(autenticado);
        assertTrue(autenticado.getPasswordHash().startsWith("$2a$12$"),
                "El hash en memoria tras login debe haberse actualizado a coste 12");
        assertFalse(EncriptadorPassword.needsUpgrade(autenticado.getPasswordHash()));

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM usuario WHERE username = 'mozo'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            String dbHash = rs.getString("password_hash");
            assertNotNull(dbHash);
            assertTrue(dbHash.startsWith("$2a$12$"), "El hash persistido en BD debe ser BCrypt coste 12: " + dbHash);
            assertFalse(EncriptadorPassword.needsUpgrade(dbHash));
            assertTrue(EncriptadorPassword.verificarPassword(passwordPlano, dbHash));
        }
    }
}
