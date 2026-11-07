package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test 3 E2E con Playwright: Resiliencia Offline con IndexedDB y Sincronización Automática (SPEC-FOODCORE-v4.1.0 §2.2).
 */
public class PwaOfflineSyncE2ETest {

    @BeforeAll
    static void setUpServer() {
        PlaywrightE2ESupport.asegurarServidorActivo();
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("E2E 3: Resiliencia Offline con IndexedDB — Caída de red guarda en outbox_pedidos y reconexión sincroniza en <= 10s")
    void testResilienciaOfflineIndexedDbYSincronizacionAutomatica() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 850));
             Page page = context.newPage()) {

            // 1. Login en la PWA y navegación al catálogo de Mesa 03
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/");
            page.fill("#txtUsername", "mozo");
            page.fill("#txtPassword", "Mozo2026!");
            page.click("button[type='submit']");
            page.waitForURL("**/mesas.html**");

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/catalogo.html?mesa=03");
            page.locator(".dish-card").first().waitFor();
            page.locator(".dish-card").first().locator(".btn-add-dish").click();

            // 2. Simular caída de red: interceptar y abortar las peticiones a /api/pedidos
            page.route("**/api/pedidos", Route::abort);

            // 3. Hacer clic en "Enviar a Cocina"
            page.click("#btnEnviarCocina");

            // 4. Verificar que la UI muestra el indicador de "Modo Offline" y el pedido se guardó en IndexedDB (outbox_pedidos)
            page.waitForFunction("() => document.getElementById('statusText') && "
                    + "document.getElementById('statusText').innerText.includes('Modo Offline')");
            String statusTextOffline = page.locator("#statusText").innerText();
            assertTrue(statusTextOffline.contains("Modo Offline"),
                    "El indicador superior debe mostrar 'Modo Offline': " + statusTextOffline);

            Object uuidGuardadoObj = page.evaluate("async () => {"
                    + "const pendientes = await FoodCoreOfflineDB.obtenerPedidosPendientes();"
                    + "return pendientes.length > 0 ? pendientes[0].uuid_local : null;"
                    + "}");
            assertNotNull(uuidGuardadoObj, "El pedido debe haberse guardado en IndexedDB (outbox_pedidos) al fallar la red");
            String uuidOffline = uuidGuardadoObj.toString();

            PlaywrightE2ESupport.guardarEvidencia(page, "PwaOfflineSyncE2ETest - Outbox Offline", "03_e2e_offline_outbox.png");

            // 5. Restaurar red: habilitar nuevamente las peticiones a /api/pedidos
            page.unroute("**/api/pedidos");

            // 6. Esperar 10 segundos para que el ciclo automático (setInterval) sincronice la cola
            page.waitForTimeout(10000);

            // 7. Verificar que la cola en IndexedDB se vació y que el pedido fue recibido en la API
            Number cantidadPendientePostSync = (Number) page.evaluate("async () => {"
                    + "const pendientes = await FoodCoreOfflineDB.obtenerPedidosPendientes();"
                    + "return pendientes.length;"
                    + "}");
            assertEquals(0, cantidadPendientePostSync.intValue(),
                    "La cola outbox_pedidos en IndexedDB debe quedar vacía tras la sincronización automática");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest reqVerif = HttpRequest.newBuilder()
                    .uri(URI.create(PlaywrightE2ESupport.BASE_URL + "/api/pedidos/" + uuidOffline))
                    .GET()
                    .build();
            HttpResponse<String> resVerif = client.send(reqVerif, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resVerif.statusCode(),
                    "El pedido sincronizado desde IndexedDB debe existir en GET /api/pedidos/{uuid}");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode pedJson = mapper.readTree(resVerif.body());
            assertEquals(uuidOffline, pedJson.path("uuid_local").asText());
            assertEquals("03", pedJson.path("numero_mesa").asText());

            PlaywrightE2ESupport.guardarEvidencia(page, "PwaOfflineSyncE2ETest - Sincronizado Online", "03_e2e_offline_synced.png");
        }
    }
}
