package com.foodcore.api;

import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que sin el perfil {@code e2e-test} activo (modo producción por defecto)
 * las credenciales demo y el mapa {@code pinsMemoria} no autentican ningún usuario ni PIN.
 */
class AuthControllerProductionTest {

    private static final byte[] TEST_JWT_SECRET =
            "FoodCore-ProdAuthTest-SecretKey-32Bytes!".getBytes(StandardCharsets.UTF_8);

    private String prevSpringProfiles;
    private String prevFoodCoreProfile;

    @BeforeEach
    void limpiarPerfilAntesDePrueba() {
        prevSpringProfiles = System.getProperty(AuthTestBypassConfig.PROP_SPRING_PROFILES);
        prevFoodCoreProfile = System.getProperty(AuthTestBypassConfig.PROP_FOODCORE_PROFILE);
        System.setProperty(AuthTestBypassConfig.PROP_SPRING_PROFILES, "");
        System.setProperty(AuthTestBypassConfig.PROP_FOODCORE_PROFILE, "");
    }

    @AfterEach
    void restaurarPerfil() {
        restaurar(AuthTestBypassConfig.PROP_SPRING_PROFILES, prevSpringProfiles);
        restaurar(AuthTestBypassConfig.PROP_FOODCORE_PROFILE, prevFoodCoreProfile);
    }

    @Test
    @DisplayName("Producción sin perfil e2e-test: usuario que existe solo en pinsMemoria / creds demo no autentica")
    void usuarioSoloEnPinsMemoriaOCredencialesDemoNoAutenticaSinPerfilE2eTest() throws Exception {
        assertFalse(AuthTestBypassConfig.isE2eTestProfileActive());
        assertFalse(AuthTestBypassConfig.esCredencialOperativaValida("mozo", "Mozo2026!"));
        assertNull(AuthTestBypassConfig.construirUsuarioOperativo("mozo"));

        UsuarioDAO emptyDao = crearUsuarioDaoVacio();
        AuthService emptyAuth = new AuthService(emptyDao, new com.foodcore.security.EncriptadorPassword()) {
            @Override
            public Usuario login(String username, String password) throws NegocioException {
                throw new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, "Credenciales inválidas.");
            }

            @Override
            public Optional<Usuario> autenticarPorPin(int idUsuario, String username, String pin) {
                return Optional.empty();
            }
        };

        JwtService jwtService = new JwtService(TEST_JWT_SECRET);
        int port = puertoLibre();
        ApiServer server = ApiServer.crearServidorAisladoConAuth(
                jwtService,
                new CorsPolicy(Set.of("http://localhost")),
                new AuthRateLimiter(),
                emptyAuth,
                emptyDao
        );
        server.iniciar(port, -1);
        try {
            HttpClient client = HttpClient.newHttpClient();

            // 1. Credenciales demo de mozo y admin no autentican
            HttpResponse<String> loginMozoDemo = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"mozo\",\"password\":\"Mozo2026!\"}"
            );
            assertEquals(401, loginMozoDemo.statusCode());
            assertFalse(loginMozoDemo.body().contains("\"token\""));

            HttpResponse<String> loginAdminDemo = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"admin\",\"password\":\"admin123\"}"
            );
            assertEquals(401, loginAdminDemo.statusCode());
            assertFalse(loginAdminDemo.body().contains("\"token\""));

            // 2. Usuario que existe solo en pinsMemoria (id=3 mozo / PIN 1234, id=1 admin / PIN 9999) NO autentica
            HttpResponse<String> pinMozoMemoria = postJson(
                    client, port, "/api/auth/pin-login", "{\"id_usuario\":3,\"username\":\"mozo\",\"pin\":\"1234\"}"
            );
            assertEquals(401, pinMozoMemoria.statusCode());
            assertFalse(pinMozoMemoria.body().contains("\"token\""));

            HttpResponse<String> pinAdminMemoria = postJson(
                    client, port, "/api/auth/pin-login", "{\"id_usuario\":1,\"username\":\"admin\",\"pin\":\"9999\"}"
            );
            assertEquals(401, pinAdminMemoria.statusCode());
            assertFalse(pinAdminMemoria.body().contains("\"token\""));

            // 3. pin-switch con usuario/PIN solo en pinsMemoria NO autentica
            String tokenAdmin = jwtService.generarToken(1, "admin", "ADMIN");
            HttpResponse<String> pinSwitchMemoria = postJsonConAuth(
                    client, port, "/api/auth/pin-switch", tokenAdmin,
                    "{\"id_usuario\":2,\"username\":\"cajero\",\"pin\":\"2001\"}"
            );
            assertEquals(401, pinSwitchMemoria.statusCode());
            assertFalse(pinSwitchMemoria.body().contains("\"token\""));

            // 4. GET /api/usuarios/activos con BD vacía retorna [] sin inyectar usuarios demo
            HttpResponse<String> activos = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/usuarios/activos"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, activos.statusCode());
            assertEquals("[]", activos.body().trim());
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("Producción contra BD real: un PIN presente solo en pinsMemoria ('2001' para cajero) no autentica")
    void pinSoloEnPinsMemoriaNoAutenticaContraBaseDeDatosReal() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();

        JwtService jwtService = new JwtService(TEST_JWT_SECRET);
        int port = puertoLibre();
        ApiServer server = ApiServer.crearServidorAislado(
                jwtService,
                new CorsPolicy(Set.of("http://localhost")),
                new AuthRateLimiter()
        );
        server.iniciar(port, -1);
        try {
            HttpClient client = HttpClient.newHttpClient();

            // En V12, cajero (id=2) tiene PIN '1234' en BD; '2001' solo existe en pinsMemoria.
            HttpResponse<String> pinSoloMemoria = postJson(
                    client, port, "/api/auth/pin-login", "{\"id_usuario\":2,\"username\":\"cajero\",\"pin\":\"2001\"}"
            );
            assertEquals(401, pinSoloMemoria.statusCode());
            assertFalse(pinSoloMemoria.body().contains("\"token\""));

            // Contraseña demo tampoco funciona contra BD real
            HttpResponse<String> passDemo = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"mozo\",\"password\":\"Mozo2026!\"}"
            );
            assertEquals(401, passDemo.statusCode());

            // Usuario real sembrado en BD con contraseña real sí autentica vía AuthServiceImpl
            HttpResponse<String> loginReal = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"mozo\",\"password\":\"Admin#2026!\"}"
            );
            assertEquals(200, loginReal.statusCode());
            assertTrue(loginReal.body().contains("\"token\":\"eyJ"));
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("El perfil e2e-test permanece inactivo por defecto y solo se habilita de forma explícita")
    void perfilE2eTestSoloSeActivaExplicitamente() {
        assertFalse(AuthTestBypassConfig.isE2eTestProfileActive(Map.<String, String>of()::get));
        assertFalse(AuthTestBypassConfig.isE2eTestProfileActive(Map.of("SPRING_PROFILES_ACTIVE", "prod")::get));

        System.clearProperty(AuthTestBypassConfig.PROP_SPRING_PROFILES);
        System.clearProperty(AuthTestBypassConfig.PROP_FOODCORE_PROFILE);
        assertTrue(AuthTestBypassConfig.isE2eTestProfileActive(Map.of("SPRING_PROFILES_ACTIVE", "e2e-test")::get));
        assertTrue(AuthTestBypassConfig.isE2eTestProfileActive(Map.of("FOODCORE_PROFILE", "e2e-test")::get));

        System.setProperty(AuthTestBypassConfig.PROP_SPRING_PROFILES, AuthTestBypassConfig.PROFILE_E2E_TEST);
        try {
            assertTrue(AuthTestBypassConfig.isE2eTestProfileActive());
            assertTrue(AuthTestBypassConfig.esCredencialOperativaValida("mozo", "Mozo2026!"));
            assertNotNull(AuthTestBypassConfig.autenticarPinMemoria(2, "cajero", "2001"));
        } finally {
            System.setProperty(AuthTestBypassConfig.PROP_SPRING_PROFILES, "");
        }
        assertFalse(AuthTestBypassConfig.esCredencialOperativaValida("mozo", "Mozo2026!"));
        assertNull(AuthTestBypassConfig.autenticarPinMemoria(2, "cajero", "2001"));
    }

    private static UsuarioDAO crearUsuarioDaoVacio() {
        return (UsuarioDAO) Proxy.newProxyInstance(
                UsuarioDAO.class.getClassLoader(),
                new Class<?>[]{UsuarioDAO.class},
                (proxy, method, args) -> {
                    Class<?> ret = method.getReturnType();
                    if (ret.equals(Optional.class)) {
                        return Optional.empty();
                    }
                    if (ret.equals(List.class)) {
                        return List.of();
                    }
                    if (ret.equals(boolean.class) || ret.equals(Boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );
    }

    private static int puertoLibre() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpResponse<String> postJson(HttpClient client, int port, String path, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> postJsonConAuth(HttpClient client,
                                                        int port,
                                                        String path,
                                                        String bearerToken,
                                                        String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + bearerToken)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static void restaurar(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }
}
