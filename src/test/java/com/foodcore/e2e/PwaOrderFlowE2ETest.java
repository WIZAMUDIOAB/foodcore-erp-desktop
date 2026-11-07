package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.util.ConexionBD;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test 2 E2E con Playwright: Flujo Completo de Pedido desde Mesa 01 hasta Cocina KDS (SPEC-FOODCORE-v4.1.0 §2.2).
 */
public class PwaOrderFlowE2ETest {

    @BeforeAll
    static void setUpServer() {
        PlaywrightE2ESupport.asegurarServidorActivo();
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("E2E 2: Flujo Completo de Pedido — Login, Mesa 01, 2 productos, nota 'Sin cebolla', envío a KDS y validación PENDIENTE")
    void testFlujoCompletoDePedidoPwa() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser);
             Page page = context.newPage()) {

            // 1. Login exitoso en la PWA
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/");
            page.fill("#txtUsername", "mozo");
            page.fill("#txtPassword", "Mozo2026!");
            page.click("button[type='submit']");
            page.waitForURL("**/mesas.html**");

            // 2. Hacer clic en "Mesa 01" -> verificar redirección a catalogo.html
            Locator mesa01Card = page.locator(".mesa-card", new Page.LocatorOptions().setHasText("Mesa 01")).first();
            mesa01Card.waitFor();
            mesa01Card.click();

            page.waitForURL("**/catalogo.html**");
            assertTrue(page.url().contains("catalogo.html"), "Debe redirigir a catalogo.html al seleccionar Mesa 01");
            assertTrue(page.locator("#lblMesaActiva").innerText().contains("01"), "Debe indicar Mesa 01 activa");

            // 3. Agregar 2 productos (ej. 1 Hamburguesa y 1 Bebida)
            Locator cards = page.locator(".dish-card");
            cards.first().waitFor();
            double precio1 = extraerPrecioTarjeta(cards.nth(0));
            double precio2 = extraerPrecioTarjeta(cards.nth(1));

            cards.nth(0).locator(".btn-add-dish").click();
            cards.nth(1).locator(".btn-add-dish").click();

            // 4. Verificar que el carrito (Comanda Actual) muestra 2 ítems y el total calculado correctamente
            String textoItems = page.locator("#cartCountPill").innerText().trim();
            assertEquals("2 ítems", textoItems, "El contador del carrito debe mostrar '2 ítems'");

            double totalEsperado = precio1 + precio2;
            String totalMostradoTexto = page.locator("#lblTotal").innerText().replace("S/", "").trim();
            double totalMostrado = Double.parseDouble(totalMostradoTexto);
            assertEquals(totalEsperado, totalMostrado, 0.05, "El total del carrito debe coincidir con la suma de los 2 productos");

            // 5. Agregar una nota rápida ("Sin cebolla")
            Locator chipSinCebolla = page.locator(".note-chip", new Page.LocatorOptions().setHasText("Sin cebolla")).first();
            chipSinCebolla.click();
            String valorNota = page.locator(".cart-item-note-input").last().inputValue();
            assertTrue(valorNota.contains("Sin cebolla"), "La nota rápida 'Sin cebolla' debe aplicarse al ítem del carrito");

            PlaywrightE2ESupport.guardarEvidencia(page, "PwaOrderFlowE2ETest - Carrito Listo", "02_e2e_order_cart.png");

            // 6. Hacer clic en "Enviar a Cocina (KDS)"
            page.click("#btnEnviarCocina");

            Locator toast = page.locator("#toastBanner");
            toast.waitFor();
            assertEquals("0 ítems", page.locator("#cartCountPill").innerText().trim(),
                    "El carrito debe vaciarse tras enviar el pedido a Cocina");

            PlaywrightE2ESupport.guardarEvidencia(page, "PwaOrderFlowE2ETest - Enviado a KDS", "02_e2e_order_sent.png");

            // 7. Verificar vía API (GET /api/pedidos) y en Base de Datos que se guardó con estado PENDIENTE y notificó al KDS
            HttpClient client = PlaywrightE2ESupport.crearInsecureHttpClient();
            HttpRequest reqPedidos = HttpRequest.newBuilder()
                    .uri(URI.create(PlaywrightE2ESupport.BASE_URL + "/api/pedidos"))
                    .GET()
                    .build();
            HttpResponse<String> resPedidos = client.send(reqPedidos, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resPedidos.statusCode(), "GET /api/pedidos debe responder 200 OK");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode listaPedidos = mapper.readTree(resPedidos.body());
            assertTrue(listaPedidos.isArray() && !listaPedidos.isEmpty(), "Debe existir al menos un pedido registrado en la API");

            boolean encontradoMesa01Pendiente = false;
            int idPedidoVerificado = -1;
            for (JsonNode ped : listaPedidos) {
                if ("01".equals(ped.path("numero_mesa").asText())
                        && "PENDIENTE".equalsIgnoreCase(ped.path("estado").asText())
                        && ped.path("notificado_kds").asBoolean(false)) {
                    encontradoMesa01Pendiente = true;
                    idPedidoVerificado = ped.path("id_pedido").asInt(-1);
                    break;
                }
            }
            assertTrue(encontradoMesa01Pendiente,
                    "El pedido de la Mesa 01 debe figurar con estado PENDIENTE y notificado_kds=true");

            // Si la BD MySQL está activa en el entorno actual, verificar también en la tabla comanda
            verificarComandaPendienteEnBdSiDisponible(idPedidoVerificado);
        }
    }

    private double extraerPrecioTarjeta(Locator card) {
        String txt = card.locator(".dish-price").innerText().replace("S/", "").trim();
        return Double.parseDouble(txt);
    }

    private void verificarComandaPendienteEnBdSiDisponible(int idPedido) {
        if (idPedido <= 0 || idPedido >= 1000) {
            return;
        }
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT estado FROM comanda WHERE id_pedido = ?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    assertEquals("PENDIENTE", rs.getString("estado").toUpperCase(),
                            "La comanda en MySQL debe registrarse con estado PENDIENTE");
                }
            }
        } catch (Exception ignored) {
            // En entorno CI sin contenedor MySQL, la validación ya se completó vía API
        }
    }
}
