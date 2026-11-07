package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E para SUB-FASE C.2 — Dashboard y Analítica Gerencial:
 * - KPIs del día en tiempo real (GET /api/dashboard/kpis)
 * - Curva de ventas por hora para gráfico suavizado (GET /api/dashboard/ventas-por-hora)
 * - Top 5 platos más vendidos (GET /api/dashboard/top-platos)
 * - Bitácora de transacciones recientes (GET /api/dashboard/ventas-recientes)
 */
public class SubFaseC2DashboardE2ETest {

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
    @DisplayName("Sub-Fase C.2: Dashboard — GET /api/dashboard/kpis retorna métricas gerenciales")
    void testDashboardKpis() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContext(playwright);

            APIResponse resp = request.get("/api/dashboard/kpis");
            assertEquals(200, resp.status(), "GET /api/dashboard/kpis debe responder 200 OK");

            JsonNode root = MAPPER.readTree(resp.text());
            assertTrue(root.hasNonNull("ventas_dia"), "Debe informar ventas_dia");
            assertTrue(root.hasNonNull("transacciones"), "Debe informar transacciones");
            assertTrue(root.hasNonNull("ticket_promedio"), "Debe calcular ticket_promedio");
            assertTrue(root.hasNonNull("plato_top"), "Debe identificar plato_top");
            assertTrue(root.hasNonNull("mesas_ocupadas"), "Debe indicar mesas_ocupadas");
            assertEquals(12, root.path("mesas_totales").asInt(), "El total de mesas del salón debe ser 12");

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.2: Dashboard — GET /api/dashboard/ventas-por-hora provee datos para gráfica Bézier")
    void testDashboardVentasPorHora() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContext(playwright);

            APIResponse resp = request.get("/api/dashboard/ventas-por-hora");
            assertEquals(200, resp.status(), "GET /api/dashboard/ventas-por-hora debe responder 200 OK");

            JsonNode root = MAPPER.readTree(resp.text());
            assertTrue(root.isArray(), "Debe retornar un array JSON");
            assertFalse(root.isEmpty(), "La curva de ventas horarias debe contener datos");

            JsonNode p1 = root.get(0);
            assertNotNull(p1.get("hora"), "Cada punto debe indicar hora");
            assertNotNull(p1.get("monto"), "Cada punto debe indicar monto");

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.2: Dashboard — GET /api/dashboard/top-platos y /api/dashboard/ventas-recientes")
    void testDashboardTopPlatosYVentasRecientes() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContext(playwright);

            // 1. Top platos
            APIResponse respTop = request.get("/api/dashboard/top-platos");
            assertEquals(200, respTop.status());
            JsonNode topRoot = MAPPER.readTree(respTop.text());
            assertTrue(topRoot.isArray() && !topRoot.isEmpty());
            JsonNode top1 = topRoot.get(0);
            assertTrue(top1.hasNonNull("nombre"));
            assertTrue(top1.hasNonNull("unidades"));
            assertTrue(top1.hasNonNull("ingresos"));

            // 2. Ventas recientes
            APIResponse respRecientes = request.get("/api/dashboard/ventas-recientes");
            assertEquals(200, respRecientes.status());
            JsonNode recRoot = MAPPER.readTree(respRecientes.text());
            assertTrue(recRoot.isArray() && !recRoot.isEmpty());
            JsonNode rec1 = recRoot.get(0);
            assertTrue(rec1.hasNonNull("numero_comprobante"));
            assertTrue(rec1.hasNonNull("total"));

            request.dispose();
        }
    }
}
