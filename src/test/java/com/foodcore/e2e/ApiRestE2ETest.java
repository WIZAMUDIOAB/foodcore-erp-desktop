package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test 4 E2E con Playwright: Validación de Endpoints Backend API REST (SPEC-FOODCORE-v4.1.0 §2.2).
 */
public class ApiRestE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUpServer() {
        PlaywrightE2ESupport.asegurarServidorActivo();
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("E2E 4: Endpoints Backend — POST /api/auth/login (JWT), GET /api/productos (>=5 con imagen_url) y GET /api/mesas (12 mesas)")
    void testEndpointsBackendApiRest() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = playwright.request().newContext(
                    new APIRequest.NewContextOptions().setBaseURL(PlaywrightE2ESupport.BASE_URL));

            // 1. POST /api/auth/login -> validar 200 OK con token JWT
            APIResponse loginRes = request.post("/api/auth/login",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("username", "mozo", "password", "Mozo2026!")));
            assertEquals(200, loginRes.status(), "POST /api/auth/login debe devolver 200 OK");

            JsonNode loginJson = MAPPER.readTree(loginRes.text());
            String tokenJwt = loginJson.path("token").asText("");
            assertFalse(tokenJwt.isBlank(), "La respuesta de login debe incluir un token JWT no vacío");
            assertTrue(tokenJwt.startsWith("eyJ"), "El token devuelto debe tener formato estándar JWT (eyJ...)");

            // 2. GET /api/productos con el token -> validar JSON con al menos 5 productos y campo imagen_url
            APIResponse prodRes = request.get("/api/productos",
                    RequestOptions.create().setHeader("Authorization", "Bearer " + tokenJwt));
            assertEquals(200, prodRes.status(), "GET /api/productos debe devolver 200 OK");

            JsonNode prodArray = MAPPER.readTree(prodRes.text());
            assertTrue(prodArray.isArray() && prodArray.size() >= 5,
                    "GET /api/productos debe retornar un arreglo JSON con al menos 5 productos");
            for (JsonNode prod : prodArray) {
                assertTrue(prod.has("id") && prod.has("nombre") && prod.has("precio"),
                        "Cada producto debe contener id, nombre y precio");
                assertTrue(prod.has("imagen_url"),
                        "Cada producto debe incluir explícitamente el campo 'imagen_url'");
            }

            // 3. GET /api/mesas -> validar el estado de las 12 mesas
            APIResponse mesasRes = request.get("/api/mesas",
                    RequestOptions.create().setHeader("Authorization", "Bearer " + tokenJwt));
            assertEquals(200, mesasRes.status(), "GET /api/mesas debe devolver 200 OK");

            JsonNode mesasArray = MAPPER.readTree(mesasRes.text());
            assertTrue(mesasArray.isArray(), "GET /api/mesas debe retornar un arreglo JSON");
            assertEquals(12, mesasArray.size(), "El salón debe contar exactamente con 12 mesas operativas");
            for (JsonNode mesa : mesasArray) {
                String estado = mesa.path("estado").asText("");
                assertTrue("LIBRE".equalsIgnoreCase(estado) || "OCUPADA".equalsIgnoreCase(estado),
                        "Cada mesa debe tener un estado válido (LIBRE u OCUPADA): " + estado);
            }

            PlaywrightE2ESupport.registrarResultadoApiEnReporte(
                    "ApiRestE2ETest", "Login JWT 200 OK, " + prodArray.size() + " productos con imagen_url y 12 mesas validadas");
            request.dispose();
        }
    }
}
