package com.foodcore.api;

import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.api.security.TokenBlacklistService;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.TestSecuritySupport;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias e integración para revocación JWT e invalidación global de sesiones
 * mediante {@code POST /api/auth/force-logout} y {@link TokenBlacklistService} (ÍTEM 8 v5.1.0).
 */
class ForceLogoutBlacklistTest {

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
        TestSecuritySupport.asegurarJwtSecretTest();
        TokenBlacklistService.getInstance().limpiarMemoria();
    }

    @Test
    @DisplayName("TokenBlacklistService en memoria revoca tokens previos y permite tokens emitidos posteriormente")
    void listaNegraMemoriaRevocaPreviosYPermiteNuevos() {
        JwtService jwtService = new JwtService();
        TokenBlacklistService blacklist = TokenBlacklistService.crearAisladoEnMemoria();

        String tokenAntes = jwtService.generarToken(2, "cajero", "CAJERO");
        JwtService.JwtClaims claimsAntes = jwtService.verificarToken(tokenAntes).orElseThrow();
        assertFalse(blacklist.estaRevocado(claimsAntes));

        blacklist.forzarCierreSesionUsuario(null, 2, 1, "Prueba corte de sesión");
        assertTrue(blacklist.estaRevocado(claimsAntes),
                "El token anterior al corte debe quedar inmediatamente revocado");

        String tokenDespues = jwtService.generarToken(2, "cajero", "CAJERO");
        JwtService.JwtClaims claimsDespues = jwtService.verificarToken(tokenDespues).orElseThrow();
        assertFalse(blacklist.estaRevocado(claimsDespues),
                "Un token nuevo emitido tras el force-logout debe ser válido");

        blacklist.revocarToken(null, claimsDespues.jti(), 2, 1, "Revocación puntual JTI",
                Instant.ofEpochSecond(claimsDespues.expiresAtEpochSec()));
        assertTrue(blacklist.estaRevocado(claimsDespues));
    }

    @Test
    @DisplayName("POST /api/auth/force-logout requiere ADMIN, invalida tokens en BD y devuelve 401 al reutilizarlos")
    void endpointForceLogoutInvalidaTokensYAudita() throws Exception {
        int port = puertoLibre();
        JwtService jwtService = new JwtService();
        TokenBlacklistService serviceBd = new TokenBlacklistService(true);
        serviceBd.limpiarMemoria();

        ApiServer server = ApiServer.crearServidorAisladoConBlacklist(
                jwtService,
                new CorsPolicy(Set.of("http://localhost")),
                new AuthRateLimiter(50, 60_000L, System::currentTimeMillis),
                serviceBd);
        server.iniciar(port, -1);
        try {
            HttpClient client = HttpClient.newHttpClient();

            String tokenAdmin = jwtService.generarToken(1, "admin", "ADMIN");
            String tokenCajero = jwtService.generarToken(2, "cajero", "CAJERO");

            assertEquals(200, getConBearer(client, port, "/api/mesas", tokenCajero).statusCode());

            HttpResponse<String> intentoNoAdmin = postConBearer(
                    client, port, "/api/auth/force-logout", tokenCajero, "{\"id_usuario\":1}");
            assertEquals(403, intentoNoAdmin.statusCode(), "Un cajero no debe poder invocar force-logout");

            HttpResponse<String> revocarCajero = postConBearer(
                    client, port, "/api/auth/force-logout", tokenAdmin, "{\"id_usuario\":2}");
            assertEquals(200, revocarCajero.statusCode());
            assertTrue(revocarCajero.body().contains("\"usuarios_invalidados\":1"));

            HttpResponse<String> reusoCajero = getConBearer(client, port, "/api/mesas", tokenCajero);
            assertEquals(401, reusoCajero.statusCode(), "El token del cajero revocado debe recibir HTTP 401");

            assertEquals(200, getConBearer(client, port, "/api/mesas", tokenAdmin).statusCode());

            HttpResponse<String> revocarGlobal = postConBearer(
                    client, port, "/api/auth/force-logout", tokenAdmin, "{}");
            assertEquals(200, revocarGlobal.statusCode());

            HttpResponse<String> reusoAdmin = getConBearer(client, port, "/api/mesas", tokenAdmin);
            assertEquals(401, reusoAdmin.statusCode(),
                    "Tras force-logout global, el propio token activo también debe recibir HTTP 401");

            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement psAudit = conn.prepareStatement(
                         "SELECT COUNT(*) FROM auditoria WHERE accion = 'FORCE_LOGOUT'");
                 ResultSet rsAudit = psAudit.executeQuery()) {
                assertTrue(rsAudit.next());
                assertTrue(rsAudit.getInt(1) >= 2);
            }

            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement psBl = conn.prepareStatement("SELECT COUNT(*) FROM token_blacklist");
                 ResultSet rsBl = psBl.executeQuery()) {
                assertTrue(rsBl.next());
                assertTrue(rsBl.getInt(1) >= 1, "El JTI actual debe persistirse en token_blacklist");
            }
            assertNotRevokedFreshCheck(serviceBd, jwtService);
        } finally {
            server.detener();
        }
    }

    private static void assertNotRevokedFreshCheck(TokenBlacklistService serviceBd, JwtService jwtService) {
        String nuevoToken = jwtService.generarToken(1, "admin", "ADMIN");
        JwtService.JwtClaims claims = jwtService.verificarToken(nuevoToken).orElseThrow();
        assertFalse(serviceBd.estaRevocado(claims));
    }

    private static HttpResponse<String> getConBearer(HttpClient client, int port, String path, String token)
            throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postConBearer(
            HttpClient client, int port, String path, String token, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int puertoLibre() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
