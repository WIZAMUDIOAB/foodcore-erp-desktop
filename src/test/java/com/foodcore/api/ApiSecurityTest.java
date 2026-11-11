package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de seguridad HTTP Javalin (Tarea 3): CORS whitelist estricta, autenticación Bearer JWT,
 * autorización RBAC, exposición pública mínima, mitigación path traversal y rate limiting en auth.
 */
class ApiSecurityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] TEST_SECRET =
            "FoodCore_Test_Secret_Key_With_At_Least_32_Bytes!".getBytes(StandardCharsets.UTF_8);

    private static int puertoLibre() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    @DisplayName("CORS rechaza wildcard '*' y emite headers únicamente para orígenes en whitelist")
    void testCorsRejectsWildcardAndAllowsOnlyWhitelist() throws Exception {
        assertThrows(IllegalStateException.class, () -> CorsPolicy.desdeCadena("*"));
        assertThrows(IllegalStateException.class, () -> CorsPolicy.desdeCadena("https://ok.local,*"));

        int port = puertoLibre();
        JwtService jwtService = new JwtService(TEST_SECRET);
        CorsPolicy policy = CorsPolicy.desdeCadena("https://tablet.local:8443");
        ApiServer server = ApiServer.crearServidorAislado(jwtService, policy, new AuthRateLimiter());
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            URI publicUri = URI.create("http://127.0.0.1:" + port + "/api/public/productos");

            HttpResponse<String> optionsMalicioso = client.send(
                    HttpRequest.newBuilder(publicUri)
                            .header("Origin", "https://evil.example")
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, optionsMalicioso.statusCode());
            assertTrue(optionsMalicioso.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            assertEquals("Origin", optionsMalicioso.headers().firstValue("Vary").orElse(""));

            HttpResponse<String> getMalicioso = client.send(
                    HttpRequest.newBuilder(publicUri)
                            .header("Origin", "https://evil.example")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(getMalicioso.headers().firstValue("Access-Control-Allow-Origin").isEmpty());

            HttpResponse<String> optionsPermitido = client.send(
                    HttpRequest.newBuilder(publicUri)
                            .header("Origin", "https://tablet.local:8443")
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(204, optionsPermitido.statusCode());
            assertEquals("https://tablet.local:8443",
                    optionsPermitido.headers().firstValue("Access-Control-Allow-Origin").orElse(""));

            Properties prodProps = new Properties();
            try (InputStream is = ApiSecurityTest.class.getResourceAsStream("/application.properties")) {
                if (is != null) {
                    prodProps.load(is);
                }
            }
            assertEquals("", prodProps.getProperty("cors.allowed.origins", ""));
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("Endpoints protegidos exigen JWT válido firmado HS256 (sin bypass en 127.0.0.1)")
    void testAuthenticationRejectsUnauthenticatedTamperedAndExpiredTokens() throws Exception {
        int port = puertoLibre();
        JwtService jwtService = new JwtService(TEST_SECRET);
        ApiServer server = ApiServer.crearServidorAislado(
                jwtService, CorsPolicy.desdeCadena(""), new AuthRateLimiter());
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            List<String> protectedGets = List.of(
                    "/api/pedidos",
                    "/api/mesas",
                    "/api/kds/items",
                    "/api/dashboard/kpis",
                    "/api/caja/estado",
                    "/api/usuarios/activos",
                    "/api/menu-digital/view"
            );

            for (String path : protectedGets) {
                HttpResponse<String> unauth = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, unauth.statusCode(), "Ruta protegida debe exigir JWT incluso desde 127.0.0.1: " + path);
            }

            String validToken = jwtService.generarToken(3, "mozo", "MOZO");
            HttpResponse<String> tampered = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/mesas"))
                            .header("Authorization", "Bearer " + validToken + "tampered")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, tampered.statusCode());

            String expiredToken = jwtService.generarToken(3, "mozo", "MOZO", -60);
            HttpResponse<String> expired = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/mesas"))
                            .header("Authorization", "Bearer " + expiredToken)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, expired.statusCode());

            String algNoneHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String fakePayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"admin\",\"uid\":1,\"rol\":\"ADMIN\",\"exp\":4102444800}"
                            .getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> algNone = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/dashboard/kpis"))
                            .header("Authorization", "Bearer " + algNoneHeader + "." + fakePayload + ".")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, algNone.statusCode());

            HttpResponse<String> authorized = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/productos"))
                            .header("Authorization", "Bearer " + validToken)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authorized.statusCode());
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("RBAC aplica principio de mínimo privilegio entre MOZO, COCINA, CAJERO y ADMIN")
    void testRbacEnforcesRoleBoundariesAcrossEndpoints() throws Exception {
        int port = puertoLibre();
        JwtService jwtService = new JwtService(TEST_SECRET);
        ApiServer server = ApiServer.crearServidorAislado(
                jwtService, CorsPolicy.desdeCadena(""), new AuthRateLimiter());
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            String mozoToken = jwtService.generarToken(3, "mozo", "MOZO");
            String cocinaToken = jwtService.generarToken(4, "cocinero", "COCINA");
            String cajeroToken = jwtService.generarToken(2, "cajero", "CAJERO");
            String adminToken = jwtService.generarToken(1, "admin", "ADMIN");

            assertEquals(200, enviarGet(client, port, "/api/mesas", mozoToken).statusCode());
            assertEquals(403, enviarGet(client, port, "/api/kds/items", mozoToken).statusCode());
            assertEquals(403, enviarGet(client, port, "/api/dashboard/kpis", mozoToken).statusCode());
            assertEquals(403, enviarGet(client, port, "/api/caja/estado", mozoToken).statusCode());

            HttpResponse<String> mozoPinPatch = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/usuarios/3/pin"))
                            .header("Authorization", "Bearer " + mozoToken)
                            .header("Content-Type", "application/json")
                            .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"pin\":\"1234\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, mozoPinPatch.statusCode());

            assertEquals(200, enviarGet(client, port, "/api/kds/items", cocinaToken).statusCode());
            assertEquals(403, enviarGet(client, port, "/api/dashboard/kpis", cocinaToken).statusCode());
            HttpResponse<String> cocinaCrearPedido = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/pedidos"))
                            .header("Authorization", "Bearer " + cocinaToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"numero_mesa\":\"01\",\"items\":[]}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, cocinaCrearPedido.statusCode());

            assertEquals(200, enviarGet(client, port, "/api/caja/estado", cajeroToken).statusCode());
            assertEquals(403, enviarGet(client, port, "/api/dashboard/kpis", cajeroToken).statusCode());

            assertEquals(200, enviarGet(client, port, "/api/dashboard/kpis", adminToken).statusCode());
            assertEquals(200, enviarGet(client, port, "/api/kds/items", adminToken).statusCode());
            assertEquals(200, enviarGet(client, port, "/api/caja/estado", adminToken).statusCode());
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("Catálogo público, health minimalista y bloqueo de path traversal no filtran datos internos")
    void testPublicCatalogAndMinimalHealthDoNotLeakSecrets() throws Exception {
        int port = puertoLibre();
        JwtService jwtService = new JwtService(TEST_SECRET);
        ApiServer server = ApiServer.crearServidorAislado(
                jwtService, CorsPolicy.desdeCadena(""), new AuthRateLimiter());
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> publicCatalog = enviarGet(client, port, "/api/public/productos", null);
            assertEquals(200, publicCatalog.statusCode());
            JsonNode arr = MAPPER.readTree(publicCatalog.body());
            assertTrue(arr.isArray() && !arr.isEmpty());
            JsonNode primero = arr.get(0);
            assertTrue(primero.hasNonNull("nombre"));
            assertTrue(primero.hasNonNull("precio"));
            assertFalse(primero.has("costo"));
            assertFalse(primero.has("stock"));
            assertFalse(primero.has("codigo"));

            HttpResponse<String> apiHealth = enviarGet(client, port, "/api/health", null);
            assertEquals(200, apiHealth.statusCode());
            JsonNode apiHealthJson = MAPPER.readTree(apiHealth.body());
            assertEquals("UP", apiHealthJson.path("status").asText());
            assertFalse(apiHealthJson.has("port"));
            assertFalse(apiHealthJson.has("version"));

            HttpResponse<String> readyHealth = enviarGet(client, port, "/health", null);
            assertTrue(readyHealth.statusCode() == 200 || readyHealth.statusCode() == 503);
            JsonNode readyJson = MAPPER.readTree(readyHealth.body());
            assertTrue(readyJson.has("status"));
            assertTrue(readyJson.has("db"));
            assertTrue(readyJson.has("flyway"));
            assertFalse(readyJson.has("port"));
            assertFalse(readyJson.has("version"));
            assertFalse(readyJson.has("migrationsApplied"));

            HttpResponse<String> traversal = enviarGet(client, port, "/uploads/platillos/..%2F..%2Fapplication.properties", null);
            assertTrue(traversal.statusCode() == 403 || traversal.statusCode() == 404);
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("Rate limiting en /api/auth/* bloquea el intento 11 en menos de 1 minuto con HTTP 429")
    void testAuthRateLimitingBlocksBruteForceAfterTenRequestsPerMinute() throws Exception {
        assertEquals(10, AuthRateLimiter.DEFAULT_MAX_REQUESTS);

        Properties prodProps = new Properties();
        try (InputStream is = ApiSecurityTest.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                prodProps.load(is);
            }
        }
        assertEquals("10", prodProps.getProperty("security.auth.rateLimit.max"));

        int port = puertoLibre();
        JwtService jwtService = new JwtService(TEST_SECRET);
        ApiServer server = ApiServer.crearServidorAislado(
                jwtService, CorsPolicy.desdeCadena(""), new AuthRateLimiter());
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            URI loginUri = URI.create("http://127.0.0.1:" + port + "/api/auth/login");

            for (int i = 1; i <= 10; i++) {
                HttpResponse<String> resp = client.send(
                        HttpRequest.newBuilder(loginUri)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"mozo\",\"password\":\"wrong\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, resp.statusCode(), "Intento " + i + " debe rechazarse por credenciales inválidas (401)");
            }

            HttpResponse<String> bloqueado = client.send(
                    HttpRequest.newBuilder(loginUri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"mozo\",\"password\":\"wrong\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(429, bloqueado.statusCode(), "Intento 11 en el mismo minuto debe disparar HTTP 429");
            assertTrue(bloqueado.headers().firstValue("Retry-After").isPresent());
        } finally {
            server.detener();
        }
    }

    private static HttpResponse<String> enviarGet(HttpClient client, int port, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
