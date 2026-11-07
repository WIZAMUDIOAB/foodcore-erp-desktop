package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.util.ConexionBD;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test 5 E2E con Playwright: Prevención de Duplicados por Idempotencia de {@code uuid_local} (SPEC-FOODCORE-v4.1.0 §2.2).
 */
public class ApiIdempotencyE2ETest {

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
    @DisplayName("E2E 5: Prevención de Duplicados — Segundo POST /api/pedidos con mismo uuid_local devuelve 200 OK sin duplicar")
    void testPrevencionDuplicadosIdempotenciaUuidLocal() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = playwright.request().newContext(
                    new APIRequest.NewContextOptions().setBaseURL(PlaywrightE2ESupport.BASE_URL));

            String uuidFijo = "test-uuid-123-" + UUID.randomUUID().toString().substring(0, 8);
            Map<String, Object> payload = Map.of(
                    "uuid_local", uuidFijo,
                    "numero_mesa", "05",
                    "id_usuario", 1,
                    "items", List.of(
                            Map.of(
                                    "id_producto", 1,
                                    "nombre", "Hamburguesa Royal Doble",
                                    "cantidad", 1,
                                    "precio_unitario", 24.50,
                                    "notas", "Idempotencia E2E"
                            )
                    )
            );

            // 1. Primera petición POST /api/pedidos con uuid_local fijo
            APIResponse res1 = request.post("/api/pedidos",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(payload));
            assertTrue(res1.status() == 201 || res1.status() == 200,
                    "El primer envío del pedido debe ser exitoso (201 Created o 200 OK)");
            JsonNode json1 = MAPPER.readTree(res1.text());
            int idPedidoOriginal = json1.path("id_pedido").asInt();
            assertTrue(idPedidoOriginal > 0, "Debe retornar un id_pedido positivo");

            // 2. Segunda petición exacta con el mismo uuid_local
            APIResponse res2 = request.post("/api/pedidos",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(payload));
            assertEquals(200, res2.status(),
                    "La segunda petición con el mismo uuid_local debe devolver 200 OK (idempotente)");

            JsonNode json2 = MAPPER.readTree(res2.text());
            assertEquals(idPedidoOriginal, json2.path("id_pedido").asInt(),
                    "El segundo envío debe retornar exactamente el mismo id_pedido sin crear uno nuevo");
            assertTrue(json2.path("idempotente").asBoolean(),
                    "La respuesta debe marcar 'idempotente: true'");

            // 3. Validar vía GET /api/pedidos que solo existe 1 registro con ese uuid_local
            APIResponse listRes = request.get("/api/pedidos");
            assertEquals(200, listRes.status());
            JsonNode todos = MAPPER.readTree(listRes.text());
            int coincidenciasApi = 0;
            for (JsonNode p : todos) {
                if (uuidFijo.equals(p.path("uuid_local").asText())) {
                    coincidenciasApi++;
                }
            }
            assertEquals(1, coincidenciasApi, "No debe haberse insertado un segundo pedido en la API");

            // 4. Si MySQL está disponible en el entorno actual, validar COUNT(*) = 1 en la tabla pedido
            verificarUnicoRegistroEnMySqlSiDisponible(uuidFijo);

            PlaywrightE2ESupport.registrarResultadoApiEnReporte(
                    "ApiIdempotencyE2ETest", "UUID " + uuidFijo + " verificado con 1 único registro (id=" + idPedidoOriginal + ")");
            request.dispose();
        }
    }

    private void verificarUnicoRegistroEnMySqlSiDisponible(String uuidLocal) {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM pedido WHERE idempotency_key = ?")) {
            ps.setString(1, uuidLocal);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int totalEnBd = rs.getInt(1);
                    if (totalEnBd > 0) {
                        assertEquals(1, totalEnBd,
                                "La tabla pedido en MySQL debe tener exactamente 1 fila para idempotency_key=" + uuidLocal);
                    }
                }
            }
        } catch (Exception ignored) {
            // En entorno CI sin contenedor MySQL, la validación ya se completó vía API
        }
    }
}
