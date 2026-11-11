package com.foodcore.api;

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
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el comportamiento CORS estricto para despliegues PWA en LAN.
 */
class CorsPwaTest {

    private static final byte[] TEST_SECRET =
            "FoodCore-CorsPwaTest-Secret-2026-Key-32Plus-Bytes!".getBytes(StandardCharsets.UTF_8);
    private static final String LAN_ORIGIN = "https://192.168.1.50:8443";

    @Test
    @DisplayName("Con cors.allowed.origins vacío (default seguro), OPTIONS desde origen externo responde 403")
    void emptyAllowedOriginsRejectsExternalPreflightWith403() throws Exception {
        Properties prodDefaults = new Properties();
        try (InputStream is = CorsPwaTest.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                prodDefaults.load(is);
            }
        }
        String defaultOrigins = prodDefaults.getProperty("cors.allowed.origins", "");
        assertEquals("", defaultOrigins);

        int port = freePort();
        ApiServer server = ApiServer.crearServidorAislado(
                new JwtService(TEST_SECRET),
                CorsPolicy.desdeCadena(defaultOrigins),
                new AuthRateLimiter()
        );
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = sendOptions(client, port, LAN_ORIGIN);
            assertEquals(403, response.statusCode());
            assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("Con origen LAN configurado explícitamente, OPTIONS responde 204 con Access-Control-Allow-Origin")
    void configuredLanOriginAllowsPreflightWith204AndHeader() throws Exception {
        int port = freePort();
        ApiServer server = ApiServer.crearServidorAislado(
                new JwtService(TEST_SECRET),
                CorsPolicy.desdeCadena(LAN_ORIGIN),
                new AuthRateLimiter()
        );
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> allowedResponse = sendOptions(client, port, LAN_ORIGIN);
            assertEquals(204, allowedResponse.statusCode());
            assertEquals(
                    LAN_ORIGIN,
                    allowedResponse.headers().firstValue("Access-Control-Allow-Origin").orElse("")
            );

            HttpResponse<String> blockedResponse = sendOptions(client, port, "https://untrusted.example");
            assertEquals(403, blockedResponse.statusCode());
            assertTrue(blockedResponse.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        } finally {
            server.detener();
        }
    }

    private static HttpResponse<String> sendOptions(HttpClient client, int port, String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/public/productos"))
                .header("Origin", origin)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
