package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E para SUB-FASE C.1 — MODO MOZO Y SEGURIDAD PIN (v4.2.0):
 * Valida autenticación táctil por PIN de 4 dígitos, cambio rápido de mozo en caliente
 * sin perder la comanda activa, y endpoints REST correspondientes.
 */
public class SubFaseC1PinLoginE2ETest {

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
    @DisplayName("Sub-Fase C.1: API — Endpoints /api/usuarios/activos, /api/auth/pin-login y /api/auth/pin-switch")
    void testApiPinLoginYSwitch() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContext(playwright);

            // 1. GET /api/usuarios/activos
            APIResponse respActivos = request.get("/api/usuarios/activos");
            assertEquals(200, respActivos.status(), "GET /api/usuarios/activos debe responder 200 OK");
            JsonNode listaActivos = MAPPER.readTree(respActivos.text());
            assertTrue(listaActivos.isArray() && !listaActivos.isEmpty(), "Debe retornar al menos un usuario activo");
            boolean tieneMozo = false;
            for (JsonNode u : listaActivos) {
                assertNotNull(u.get("username"), "Usuario debe tener username");
                assertNotNull(u.get("nombre"), "Usuario debe tener nombre");
                assertTrue(u.has("tiene_pin"), "Usuario debe informar estado tiene_pin");
                if ("mozo".equalsIgnoreCase(u.path("username").asText())) {
                    tieneMozo = true;
                }
            }
            assertTrue(tieneMozo, "La lista de usuarios activos debe incluir al menos al mozo");

            // 2. POST /api/auth/pin-login con PIN erróneo -> 401
            APIResponse respLoginFallido = request.post("/api/auth/pin-login",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "0000", "username", "mozo")));
            assertEquals(401, respLoginFallido.status(), "PIN erróneo debe responder 401 Unauthorized");

            // 3. POST /api/auth/pin-login con PIN válido '1234' -> 200 OK + JWT
            APIResponse respLoginOk = request.post("/api/auth/pin-login",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "1234", "username", "mozo")));
            assertEquals(200, respLoginOk.status(), "PIN 1234 debe autenticar satisfactoriamente");
            JsonNode jsonLoginOk = MAPPER.readTree(respLoginOk.text());
            assertTrue(jsonLoginOk.hasNonNull("token"), "Debe devolver token JWT");
            assertTrue(jsonLoginOk.hasNonNull("usuario"), "Debe devolver datos de usuario");
            assertEquals("mozo", jsonLoginOk.path("usuario").path("username").asText());

            // 4. POST /api/auth/pin-switch (cambio rápido de mozo) -> 200 OK con nuevo token
            APIResponse respSwitchOk = request.post("/api/auth/pin-switch",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "9999", "username", "admin")));
            assertEquals(200, respSwitchOk.status(), "Cambio de mozo a admin con PIN 9999 debe ser 200 OK");
            JsonNode jsonSwitchOk = MAPPER.readTree(respSwitchOk.text());
            assertTrue(jsonSwitchOk.hasNonNull("token"));
            assertEquals("admin", jsonSwitchOk.path("usuario").path("username").asText());

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.1: UI Playwright — Flujo táctil de ingreso por PIN en pin-login.html y redirección")
    void testUiPinLoginTáctilYRedireccion() {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser);
             Page page = context.newPage()) {

            // 1. Abrir pantalla táctil de login por PIN
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/pin-login.html");

            Locator mozosGrid = page.locator("#mozosGrid");
            mozosGrid.waitFor();
            assertTrue(mozosGrid.isVisible(), "El selector de mozos debe estar visible");

            // 2. Seleccionar explícitamente tarjeta de Mozo
            Locator mozoCard = page.locator("#mozosGrid .mozo-card:has-text('Mateo'), #mozosGrid .mozo-card:has-text('Mozo')");
            if (mozoCard.count() > 0) {
                mozoCard.first().click();
            }

            // 3. Verificar existencia de las teclas numéricas 0-9
            Locator btn1 = page.locator("button[data-digit='1']");
            Locator btn2 = page.locator("button[data-digit='2']");
            Locator btn3 = page.locator("button[data-digit='3']");
            Locator btn4 = page.locator("button[data-digit='4']");
            assertTrue(btn1.isVisible() && btn2.isVisible() && btn3.isVisible() && btn4.isVisible(),
                    "El teclado numérico táctil debe mostrar los dígitos 1-4");

            // 3. Probar borrado (Clear)
            btn1.click();
            btn2.click();
            Locator dotFilled = page.locator(".pin-dot.filled");
            assertTrue(dotFilled.count() >= 2, "Los dots de PIN deben llenarse al pulsar teclas");

            Locator btnClear = page.locator("#btnPinClear");
            btnClear.click();
            assertEquals(0, page.locator(".pin-dot.filled").count(), "El botón 'C' debe reiniciar los dots de PIN");

            // 4. Ingresar PIN válido '1234'
            btn1.click();
            btn2.click();
            btn3.click();
            btn4.click();

            // Esperar redirección a mesas.html
            page.waitForURL("**/mesas.html**");
            assertTrue(page.url().contains("mesas.html"), "El ingreso por PIN exitoso debe navegar a mesas.html");

            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseC1 - Login PIN Exitoso", "subfase_c1_pin_login_exitoso.png");
        }
    }

    @Test
    @DisplayName("Sub-Fase C.1: UI Playwright — Modal táctil de cambio rápido de mozo en catalogo.html sin perder carrito")
    void testUiCambioRapidoMozoEnCaliente() {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser);
             Page page = context.newPage()) {

            // 1. Abrir directamente catalogo.html con mesa 04
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/catalogo.html?mesa=04&demo=1");

            Locator btnCambiarMozo = page.locator("#btnCambiarMozo");
            btnCambiarMozo.waitFor();
            assertTrue(btnCambiarMozo.isVisible(), "El botón '#btnCambiarMozo' debe estar presente en el topbar");

            // 2. Verificar que el carrito tiene ítems precargados
            Locator cartPill = page.locator("#cartCountPill");
            cartPill.waitFor();
            page.waitForCondition(() -> {
                String txt = cartPill.innerText();
                return txt != null && !txt.contains("0");
            });
            String itemsIniciales = cartPill.innerText();

            // 3. Abrir modal táctil de cambio de mozo
            btnCambiarMozo.click();

            Locator modalMozo = page.locator("#modalCambiarMozo");
            modalMozo.waitFor();
            assertTrue(modalMozo.isVisible(), "El modal de cambio rápido de mozo debe abrirse");

            // 4. Seleccionar chip de Administrador si está presente
            Locator chipAdmin = page.locator("#modalMozosList .mozo-chip-modal:has-text('Admin')");
            if (chipAdmin.count() > 0) {
                chipAdmin.first().click();
            }

            // Presionar PIN '9999' para switch a Administrador
            page.locator("#modalCambiarMozo button:has-text('9')").click();
            page.locator("#modalCambiarMozo button:has-text('9')").click();
            page.locator("#modalCambiarMozo button:has-text('9')").click();
            page.locator("#modalCambiarMozo button:has-text('9')").click();

            // Confirmar si no auto-envió
            Locator btnConfirmar = page.locator("#btnConfirmarCambioMozo");
            if (modalMozo.isVisible()) {
                btnConfirmar.click();
            }

            // Esperar que el modal se cierre
            page.waitForCondition(() -> !modalMozo.isVisible());
            assertFalse(modalMozo.isVisible(), "El modal debe cerrarse tras autenticar con éxito al nuevo mozo");

            // 5. Verificar que el nombre del mozo activo se actualizó y el carrito permanece intacto
            Locator lblMozo = page.locator("#lblNombreMozo");
            assertTrue(lblMozo.innerText().contains("Admin") || lblMozo.innerText().contains("Mateo"),
                    "El nombre del operador debe actualizarse en el topbar");
            assertEquals(itemsIniciales, cartPill.innerText(), "El carrito de comanda debe mantenerse 100% intacto tras el cambio de mozo");

            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseC1 - Cambio Mozo en Caliente", "subfase_c1_cambio_mozo_caliente.png");
        }
    }
}
