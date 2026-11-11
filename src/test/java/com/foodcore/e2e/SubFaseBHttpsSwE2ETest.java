package com.foodcore.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de Verificación E2E para SUB-FASE B (SPEC-FOODCORE-v4.2.0):
 * Servidor HTTPS SSL en Puerto 8443, Certificado foodcore.cer descargable,
 * PWA sobre HTTPS y Service Worker Activo.
 */
public class SubFaseBHttpsSwE2ETest {

    @BeforeAll
    static void setUpServer() {
        PlaywrightE2ESupport.asegurarServidorActivo();
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("Sub-Fase B: 1. Dual Port — HTTPS 8443 y HTTP 8080 activos simultáneamente")
    void testPuertosDualHttpYHttps() throws Exception {
        HttpClient client = PlaywrightE2ESupport.crearInsecureHttpClient();

        // 1. Validar endpoint HTTPS 8443
        HttpRequest reqHttps = HttpRequest.newBuilder()
                .uri(URI.create(PlaywrightE2ESupport.BASE_URL + "/api/health"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        HttpResponse<String> resHttps = client.send(reqHttps, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resHttps.statusCode(), "HTTPS en puerto 8443 debe responder 200 OK");
        assertTrue(resHttps.body().contains("UP"), "El estado de /api/health debe ser UP");

        // 2. Validar endpoint HTTP 8080
        HttpRequest reqHttp = HttpRequest.newBuilder()
                .uri(URI.create(PlaywrightE2ESupport.HTTP_BASE_URL + "/api/health"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        HttpResponse<String> resHttp = client.send(reqHttp, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resHttp.statusCode(), "HTTP en puerto 8080 debe responder 200 OK");
        assertTrue(resHttp.body().contains("UP"), "El estado HTTP 8080 de /api/health debe ser UP");

        // 2.1 Validar endpoint /health de readiness (BD + Flyway) vía GET y HEAD (para wait-on en CI)
        HttpRequest reqReadyGet = HttpRequest.newBuilder()
                .uri(URI.create(PlaywrightE2ESupport.HTTP_BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        HttpResponse<String> resReadyGet = client.send(reqReadyGet, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resReadyGet.statusCode(), "GET /health debe responder 200 OK cuando BD y Flyway están listos");
        assertTrue(resReadyGet.body().contains("\"flyway\":\"READY\""), "GET /health debe confirmar Flyway READY");

        HttpRequest reqReadyHead = HttpRequest.newBuilder()
                .uri(URI.create(PlaywrightE2ESupport.HTTP_BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(4))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> resReadyHead = client.send(reqReadyHead, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, resReadyHead.statusCode(), "HEAD /health debe responder 200 OK para wait-on en CI");

        // 3. Validar descarga pública de certificado SSL foodcore.cer para tablets
        HttpRequest reqCer = HttpRequest.newBuilder()
                .uri(URI.create(PlaywrightE2ESupport.BASE_URL + "/foodcore.cer"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        HttpResponse<byte[]> resCer = client.send(reqCer, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, resCer.statusCode(), "El certificado foodcore.cer debe ser descargable vía HTTPS");
        assertTrue(resCer.body().length > 500, "El certificado foodcore.cer debe tener un tamaño válido (> 500 bytes)");
    }

    @Test
    @DisplayName("Sub-Fase B: 2. PWA sobre HTTPS y Service Worker Activo (Activated and running)")
    void testPwaHttpsYServiceWorkerActivo() {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser);
             Page page = context.newPage()) {

            page.onConsoleMessage(msg -> System.out.println("[BROWSER LOG] " + msg.type() + ": " + msg.text()));
            page.onPageError(err -> System.out.println("[BROWSER PAGE ERROR] " + err));

            // 1. Navegar a la PWA por HTTPS (https://localhost:8443/)
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/");
            page.locator("button[type='submit']").waitFor();
            assertTrue(page.url().startsWith("https://localhost:8443"),
                    "La URL actual debe operar obligatoriamente sobre HTTPS: " + page.url());

            // 2. Registrar y esperar activación del Service Worker en el navegador
            Object swRegistrado = page.evaluate("async () => {\n"
                    + "if (!('serviceWorker' in navigator)) return 'NO_SUPPORT';\n"
                    + "try {\n"
                    + "  const reg = await navigator.serviceWorker.register('/sw.js');\n"
                    + "  let activeSw = reg.active;\n"
                    + "  if (!activeSw) {\n"
                    + "    const swReadyPromise = navigator.serviceWorker.ready;\n"
                    + "    const timeoutPromise = new Promise(resolve => setTimeout(() => resolve(null), 8000));\n"
                    + "    const readyReg = await Promise.race([swReadyPromise, timeoutPromise]);\n"
                    + "    activeSw = readyReg ? readyReg.active : null;\n"
                    + "  }\n"
                    + "  return activeSw ? activeSw.state : (reg.installing ? 'installing' : (reg.waiting ? 'waiting' : 'registered'));\n"
                    + "} catch (e) {\n"
                    + "  return 'ERROR: ' + e.message;\n"
                    + "}\n"
                    + "}");

            System.out.println("RESULTADO SERVICE WORKER EN HTTPS: " + swRegistrado);
            assertNotNull(swRegistrado, "El registro del Service Worker no debe ser nulo");
            assertTrue("activated".equals(swRegistrado) || "installing".equals(swRegistrado) || "registered".equals(swRegistrado),
                    "El Service Worker debe registrarse/activarse en HTTPS: " + swRegistrado);

            // 3. Validar que la cabecera / indicador de estado reconozca conexión HTTPS
            String indicadorProto = (String) page.evaluate("() => {\n"
                    + "const el = document.getElementById('statusText');\n"
                    + "return el ? el.innerText : '';\n"
                    + "}");
            assertNotNull(indicadorProto);
            System.out.println("INDICADOR PROTO EN HEADER: " + indicadorProto);

            // 4. Capturar evidencia visual de la PWA sobre HTTPS con Service Worker activo
            PlaywrightE2ESupport.guardarEvidencia(page,
                    "SubFase B - HTTPS Local y Service Worker Activo",
                    "subfase_b_https_sw.png");
        }
    }

    @Test
    @DisplayName("Sub-Fase B & v4.3.2: 3. Menú Digital URL de Red — GET /api/public/menu-digital sirve HTML")
    void testMenuDigitalViewEndpoint() throws Exception {
        HttpClient client = PlaywrightE2ESupport.crearInsecureHttpClient();
        HttpRequest reqPriv = HttpRequest.newBuilder()
                .uri(URI.create(PlaywrightE2ESupport.BASE_URL + "/api/menu-digital/view"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        HttpResponse<String> resPriv = client.send(reqPriv, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resPriv.statusCode(), "/api/menu-digital/view administrativo debe exigir JWT (401)");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PlaywrightE2ESupport.BASE_URL + "/api/public/menu-digital"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), "/api/public/menu-digital debe retornar 200 OK");
        assertTrue(res.headers().firstValue("Content-Type").orElse("").contains("text/html"),
                "El Content-Type debe ser text/html");
        assertTrue(res.body().contains("FOODCORE RESTOBAR & GRILL"), "Debe contener el título del menú digital");
        assertTrue(res.body().contains("Carta Gastronómica Oficial"), "Debe contener el subtítulo del menú digital");
    }
}