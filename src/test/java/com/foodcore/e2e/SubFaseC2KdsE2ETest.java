package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E para SUB-FASE C.2 — KDS (Cocina y Bar):
 * - Consulta de comandas agrupadas activas en cocina (GET /api/kds/comandas)
 * - Consulta de ítems individuales para Kanban táctil (GET /api/kds/items)
 * - Transición de estado por ítem (PATCH /api/kds/items/{id}/estado)
 * - Actualización global de estado de comanda (PATCH /api/pedidos/{id}/estado)
 */
public class SubFaseC2KdsE2ETest {

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
    @DisplayName("Sub-Fase C.2: KDS — GET /api/kds/comandas retorna comandas activas con ítems y mesa")
    void testConsultarComandasKdsYEstructura() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "COCINA");

            APIResponse resp = request.get("/api/kds/comandas");
            assertEquals(200, resp.status(), "GET /api/kds/comandas debe responder 200 OK");

            JsonNode root = MAPPER.readTree(resp.text());
            assertTrue(root.isArray(), "La respuesta debe ser una lista JSON de comandas activas");
            assertFalse(root.isEmpty(), "Debe existir al menos una comanda para cocina");

            JsonNode primera = root.get(0);
            assertTrue(primera.hasNonNull("id_pedido"), "La comanda debe indicar id_pedido");
            assertTrue(primera.hasNonNull("numero_mesa"), "La comanda debe indicar numero_mesa");
            assertTrue(primera.hasNonNull("items"), "La comanda debe contener el array de items");

            JsonNode items = primera.get("items");
            assertTrue(items.isArray() && !items.isEmpty(), "La comanda debe tener al menos un ítem");
            JsonNode it1 = items.get(0);
            assertTrue(it1.hasNonNull("nombre"), "El ítem debe tener nombre del platillo");
            assertTrue(it1.hasNonNull("cantidad"), "El ítem debe indicar cantidad");
            assertTrue(it1.hasNonNull("estado_cocina"), "El ítem debe indicar estado de cocina");

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.2: KDS — GET /api/kds/items retorna lista plana para Kanban de preparación")
    void testConsultarItemsKanbanKds() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "COCINA");

            APIResponse resp = request.get("/api/kds/items");
            assertEquals(200, resp.status(), "GET /api/kds/items debe responder 200 OK");

            JsonNode root = MAPPER.readTree(resp.text());
            assertTrue(root.isArray() && !root.isEmpty(), "Debe retornar una lista no vacía de ítems de cocina");

            JsonNode item = root.get(0);
            assertNotNull(item.get("id_detalle_pedido"), "El ítem debe tener id_detalle_pedido");
            assertNotNull(item.get("nombre"), "El ítem debe tener nombre");
            assertNotNull(item.get("estado_cocina"), "El ítem debe tener estado_cocina");
            assertNotNull(item.get("numero_mesa"), "El ítem debe asociar numero_mesa");

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.2: KDS — PATCH /api/kds/items/{id}/estado avanza flujo PENDIENTE -> EN_PREPARACION -> LISTO")
    void testActualizarEstadoItemKds() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "COCINA");

            // 1. Cambiar a EN_PREPARACION
            APIResponse respPrep = request.patch("/api/kds/items/501/estado",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("estado", "EN_PREPARACION")));
            assertEquals(200, respPrep.status(), "PATCH debe responder 200 OK");
            JsonNode jsonPrep = MAPPER.readTree(respPrep.text());
            assertEquals("OK", jsonPrep.path("status").asText());
            assertEquals("EN_PREPARACION", jsonPrep.path("nuevo_estado").asText());

            // 2. Cambiar a LISTO
            APIResponse respListo = request.patch("/api/kds/items/501/estado",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("estado", "LISTO")));
            assertEquals(200, respListo.status());
            JsonNode jsonListo = MAPPER.readTree(respListo.text());
            assertEquals("LISTO", jsonListo.path("nuevo_estado").asText());

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.2: KDS — PATCH /api/pedidos/{id}/estado actualiza comanda completa")
    void testActualizarEstadoPedido() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "COCINA");

            APIResponse resp = request.patch("/api/pedidos/101/estado",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("estado", "LISTO")));
            assertEquals(200, resp.status(), "PATCH /api/pedidos/{id}/estado debe responder 200 OK");
            JsonNode json = MAPPER.readTree(resp.text());
            assertEquals("OK", json.path("status").asText());
            assertEquals("LISTO", json.path("nuevo_estado").asText());

            request.dispose();
        }
    }
}
