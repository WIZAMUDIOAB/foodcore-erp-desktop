package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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
 * Suite de Verificación E2E para SUB-FASE A (SPEC-FOODCORE-v4.2.0):
 * Catálogo Dinámico, Menú del Día, Búsqueda en Tiempo Real y API de Disponibilidad.
 */
public class SubFaseACatalogoE2ETest {

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
    @DisplayName("Sub-Fase A: API — GET /api/productos/disponibles y PATCH /api/productos/{id}/disponibilidad")
    void testApiDisponibilidadYProductosDisponibles() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "ADMIN");

            // 1. GET /api/productos/disponibles
            APIResponse respDisponibles = request.get("/api/productos/disponibles");
            assertEquals(200, respDisponibles.status(), "GET /api/productos/disponibles debe responder 200 OK");
            JsonNode lista = MAPPER.readTree(respDisponibles.text());
            assertTrue(lista.isArray() && !lista.isEmpty(), "Debe existir al menos un producto disponible");

            for (JsonNode p : lista) {
                assertTrue(p.path("disponible").asBoolean(false), "Cada producto en /disponibles debe tener disponible=true");
            }

            // 2. PATCH /api/productos/2/disponibilidad -> marcar agotado (disponible=false)
            APIResponse patchAgotado = request.patch("/api/productos/2/disponibilidad",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("disponible", false)));
            assertEquals(200, patchAgotado.status(), "PATCH disponibilidad debe retornar 200 OK");
            JsonNode resAgotado = MAPPER.readTree(patchAgotado.text());
            assertTrue(resAgotado.path("success").asBoolean());
            assertFalse(resAgotado.path("disponible").asBoolean());

            // 3. Verificar que el producto 2 ya no aparece en /api/productos/disponibles
            APIResponse respPostAgotado = request.get("/api/productos/disponibles");
            JsonNode listaPostAgotado = MAPPER.readTree(respPostAgotado.text());
            boolean prod2Presente = false;
            for (JsonNode p : listaPostAgotado) {
                if (p.path("id").asInt() == 2) {
                    prod2Presente = true;
                    break;
                }
            }
            assertFalse(prod2Presente, "El producto 2 agotado NO debe figurar en /api/productos/disponibles");

            // 4. Restaurar producto 2 a disponible=true
            APIResponse patchRestaurar = request.patch("/api/productos/2/disponibilidad",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("disponible", true)));
            assertEquals(200, patchRestaurar.status());
            JsonNode resRestaurado = MAPPER.readTree(patchRestaurar.text());
            assertTrue(resRestaurado.path("disponible").asBoolean());

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase A: PWA — Chips dinámicos, Pestaña 'Menú del Día' y Búsqueda en Tiempo Real")
    void testPwaCatalogoDinamicoYMenuDelDia() {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser);
             Page page = context.newPage()) {

            // 1. Navegar a catalogo.html
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/catalogo.html?mesa=02");
            page.locator(".dish-card").first().waitFor();

            // 2. Verificar que el chip "⭐ Menú del Día" está presente y hacer clic
            Locator tabMenuDia = page.locator(".tab-menu-dia");
            tabMenuDia.waitFor();
            assertTrue(tabMenuDia.isVisible(), "El tab '⭐ Menú del Día' debe estar visible en la PWA");
            tabMenuDia.click();

            // 3. Verificar que se muestran platillos con el badge del Menú del Día
            Locator badgesMenuDia = page.locator(".dish-badge-menu-dia");
            assertTrue(badgesMenuDia.count() > 0, "Al filtrar por Menú del Día deben mostrarse platillos con el distintivo");
            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseA - Tab Menu del Dia", "subfase_a_menu_del_dia.png");

            // 4. Volver a "Todos los Platos" y probar búsqueda en tiempo real
            page.locator(".cat-tab", new Page.LocatorOptions().setHasText("Todos los Platos")).first().click();
            Locator searchInput = page.locator("#txtBuscarPlato");
            searchInput.fill("Royal");

            Locator cardsFiltradas = page.locator(".dish-card");
            assertEquals(1, cardsFiltradas.count(), "La búsqueda en tiempo real de 'Royal' debe filtrar a 1 platillo");
            assertTrue(cardsFiltradas.first().innerText().contains("Royal Doble"));
            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseA - Busqueda en Tiempo Real", "subfase_a_busqueda_tiempo_real.png");

            // 5. Limpiar búsqueda
            searchInput.fill("");
            assertTrue(page.locator(".dish-card").count() >= 5, "Al limpiar búsqueda deben restaurarse todas las tarjetas");
        }
    }
}
