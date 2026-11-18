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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que AuthController autentica exclusivamente contra base de datos
 * incluso si existen propiedades de perfil como {@code e2e-test}.
 */
class AuthControllerProductionTest {

    private static final byte[] TEST_JWT_SECRET =
            "FoodCore-ProdAuthTest-SecretKey-32Bytes!".getBytes(StandardCharsets.UTF_8);
    private static final String PROP_SPRING_PROFILES = "spring.profiles.active";
    private static final String PROP_FOODCORE_PROFILE = "foodcore.profile";

    private String prevSpringProfiles;
    private String prevFoodCoreProfile;

    @BeforeEach
    void limpiarPerfilAntesDePrueba() {
        prevSpringProfiles = System.getProperty(PROP_SPRING_PROFILES);
        prevFoodCoreProfile = System.getProperty(PROP_FOODCORE_PROFILE);
        System.setProperty(PROP_SPRING_PROFILES, "");
        System.setProperty(PROP_FOODCORE_PROFILE, "");
    }

    @AfterEach
    void restaurarPerfil() {
        restaurar(PROP_SPRING_PROFILES, prevSpringProfiles);
        restaurar(PROP_FOODCORE_PROFILE, prevFoodCoreProfile);
    }

    @Test
    @DisplayName("Sin respaldo en memoria: credenciales legacy o PIN hardcoded no autentican con DAO vacío")
    void credencialesLegacyOPinHardcodedNoAutenticanSinBd() throws Exception {
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

            // Incluso activando perfil e2e-test, NO existe ningún bypass en memoria
            System.setProperty(PROP_SPRING_PROFILES, "e2e-test");
            System.setProperty(PROP_FOODCORE_PROFILE, "e2e-test");

            HttpResponse<String> loginMozoLegacy = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"mozo\",\"password\":\"Mozo2026!\"}"
            );
            assertEquals(401, loginMozoLegacy.statusCode());
            assertFalse(loginMozoLegacy.body().contains("\"token\""));

            HttpResponse<String> loginAdminLegacy = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"admin\",\"password\":\"admin123\"}"
            );
            assertEquals(401, loginAdminLegacy.statusCode());
            assertFalse(loginAdminLegacy.body().contains("\"token\""));

            HttpResponse<String> pinMozoSinBd = postJson(
                    client, port, "/api/auth/pin-login", "{\"id_usuario\":3,\"username\":\"mozo\",\"pin\":\"1234\"}"
            );
            assertEquals(401, pinMozoSinBd.statusCode());
            assertFalse(pinMozoSinBd.body().contains("\"token\""));

            HttpResponse<String> pinAdminSinBd = postJson(
                    client, port, "/api/auth/pin-login", "{\"id_usuario\":1,\"username\":\"admin\",\"pin\":\"9999\"}"
            );
            assertEquals(401, pinAdminSinBd.statusCode());
            assertFalse(pinAdminSinBd.body().contains("\"token\""));

            String tokenAdmin = jwtService.generarToken(1, "admin", "ADMIN");
            HttpResponse<String> pinSwitchSinBd = postJsonConAuth(
                    client, port, "/api/auth/pin-switch", tokenAdmin,
                    "{\"id_usuario\":2,\"username\":\"cajero\",\"pin\":\"2001\"}"
            );
            assertEquals(401, pinSwitchSinBd.statusCode());
            assertFalse(pinSwitchSinBd.body().contains("\"token\""));

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
    @DisplayName("Contra BD real: solo autentican usuarios sembrados en Flyway incluso con perfil e2e-test")
    void soloAutenticanCredencialesRealesEnBaseDeDatos() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        System.setProperty(PROP_SPRING_PROFILES, "e2e-test");

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

            HttpResponse<String> pinInvalido = postJson(
                    client, port, "/api/auth/pin-login", "{\"id_usuario\":2,\"username\":\"cajero\",\"pin\":\"2001\"}"
            );
            assertEquals(401, pinInvalido.statusCode());
            assertFalse(pinInvalido.body().contains("\"token\""));

            HttpResponse<String> passLegacy = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"mozo\",\"password\":\"Mozo2026!\"}"
            );
            assertEquals(401, passLegacy.statusCode());

            // Usuario real sembrado en BD (V3) autentica vía AuthServiceImpl
            HttpResponse<String> loginReal = postJson(
                    client, port, "/api/auth/login", "{\"username\":\"mozo\",\"password\":\"Admin#2026!\"}"
            );
            assertEquals(200, loginReal.statusCode());
            assertTrue(loginReal.body().contains("\"token\":\"eyJ"));

            // PIN real sembrado en BD (V12) autentica vía AuthServiceImpl
            HttpResponse<String> pinReal = postJson(
                    client, port, "/api/auth/pin-login", "{\"id_usuario\":2,\"username\":\"cajero\",\"pin\":\"1234\"}"
            );
            assertEquals(200, pinReal.statusCode());
            assertTrue(pinReal.body().contains("\"token\":\"eyJ"));
        } finally {
            server.detener();
        }
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

    private static HttpResponse<String> postJsonConAuth(
            HttpClient client, int port, String path, String token, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
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
