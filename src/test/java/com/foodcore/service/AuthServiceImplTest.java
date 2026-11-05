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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de pruebas de integración para {@link AuthServiceImpl} (B3-bis).
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
    @DisplayName("B3-bis (1/4): Login con usuario inactivo (activo = 0) lanza NegocioException CREDENCIALES_INVALIDAS")
    void testLogin_UsuarioInactivo_Falla() throws Exception {
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
    @DisplayName("B3-bis (2/4): Login con bloqueo vigente lanza NegocioException USUARIO_BLOQUEADO")
    void testLogin_BloqueoVigente_Falla() throws Exception {
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
    @DisplayName("B3-bis (3/4): Login con bloqueo expirado desbloquea la cuenta, resetea intentos y autentica")
    void testLogin_BloqueoExpirado_Desbloquea() throws Exception {
        LocalDateTime pasado = LocalDateTime.now(ZONA_LIMA).minusMinutes(5);
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE usuario SET intentos_fallidos = 5, bloqueado_hasta = ? WHERE username = 'cajero'"
             )) {
            ps.setObject(1, pasado);
            ps.executeUpdate();
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
    @DisplayName("B3-bis (4/4): Cambio de password con política fallida (< 8 caracteres) lanza VALIDACION_ENTRADA")
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
}
