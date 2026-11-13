package com.foodcore.service;

import com.foodcore.api.ApiServer;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias y HTTP de Fase 2 (v4.6.0):
 * integración de Cloudflare Tunnel y generación dinámica de menú QR sin caché estático.
 */
class TunnelAndDynamicMenuQrTest {

    private static final byte[] TEST_SECRET =
            "FoodCore_Fase2_Test_Secret_Key_32_Bytes_Minimum!".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void limpiarAntes() {
        TunnelService.limpiarEstadoParaPruebas();
    }

    @AfterEach
    void limpiarDespues() {
        TunnelService.limpiarEstadoParaPruebas();
    }

    @Test
    @DisplayName("TunnelService extrae URL trycloudflare.com desde banner oficial de cloudflared")
    void testExtraccionUrlCloudflareDesdeLogReal() {
        String lineaBanner = "2026-11-12T04:52:26Z INF |  https://boot-hit-meanwhile-opens.trycloudflare.com  |";
        assertEquals("https://boot-hit-meanwhile-opens.trycloudflare.com",
                TunnelService.extraerUrlTryCloudflare(lineaBanner));

        assertNull(TunnelService.extraerUrlTryCloudflare("2026-11-12T04:52:20Z INF Requesting new quick Tunnel..."));
        assertNull(TunnelService.extraerUrlTryCloudflare(""));
        assertNull(TunnelService.extraerUrlTryCloudflare(null));
    }

    @Test
    @DisplayName("TunnelService lee tunnel.log en UTF-8 y UTF-16LE (BOM PowerShell) y publica URL global")
    void testLecturaLogUtf8YUtf16Le(@TempDir Path tempDir) throws Exception {
        Path logUtf8 = tempDir.resolve("tunnel_utf8.log");
        Files.writeString(logUtf8,
                "INF Initiating tunnel\nINF | https://qr-utf8-foodcore.trycloudflare.com |\n",
                StandardCharsets.UTF_8);

        assertTrue(TunnelService.leerUrlDesdeLog(logUtf8));
        assertTrue(TunnelService.tieneUrlActiva());
        assertEquals("https://qr-utf8-foodcore.trycloudflare.com", TunnelService.obtenerUrlPublicaActiva());
        assertEquals("https://qr-utf8-foodcore.trycloudflare.com/api/public/menu-digital",
                TunnelService.construirUrlPublicaEndpoint("/api/public/menu-digital"));

        TunnelService.limpiarEstadoParaPruebas();

        Path logUtf16 = tempDir.resolve("tunnel_utf16.log");
        String textoUtf16 = "INF | https://qr-utf16-foodcore.trycloudflare.com |\r\n";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF);
        baos.write(0xFE);
        baos.write(textoUtf16.getBytes(StandardCharsets.UTF_16LE));
        Files.write(logUtf16, baos.toByteArray());

        assertTrue(TunnelService.leerUrlDesdeLog(logUtf16));
        assertEquals("https://qr-utf16-foodcore.trycloudflare.com", TunnelService.obtenerUrlPublicaActiva());
    }

    @Test
    @DisplayName("Monitor en tiempo real detecta URL de tunnel.log escrita de forma asíncrona")
    void testMonitorLogEnTiempoRealDetectaUrl(@TempDir Path tempDir) throws Exception {
        Path logVivo = tempDir.resolve("tunnel.log");
        Files.writeString(logVivo, "INF Arrancando cloudflared...\n", StandardCharsets.UTF_8);

        TunnelService.iniciarMonitorSoloLectura(logVivo);
        assertFalse(TunnelService.tieneUrlActiva());

        Thread.sleep(150);
        Files.writeString(logVivo,
                "INF Arrancando cloudflared...\nINF | https://live-tail-menu.trycloudflare.com |\n",
                StandardCharsets.UTF_8);

        long limite = System.currentTimeMillis() + 3000L;
        while (!TunnelService.tieneUrlActiva() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }

        assertTrue(TunnelService.tieneUrlActiva(), "El monitor debe detectar la URL escrita en tunnel.log");
        assertEquals("https://live-tail-menu.trycloudflare.com", TunnelService.obtenerUrlPublicaActiva());
    }

    @Test
    @DisplayName("MenuQrService prioriza dominio de TunnelService y hace fallback a IP local")
    void testConstruirUrlMenuDigitalPriorizaTunelYFallbackIpLocal() {
        TunnelService.establecerUrlPublicaManual("https://mi-restobar-publico.trycloudflare.com/");
        assertEquals("https://mi-restobar-publico.trycloudflare.com/api/public/menu-digital",
                MenuQrService.construirUrlMenuDigital(8443));

        TunnelService.limpiarEstadoParaPruebas();
        String urlLocal = MenuQrService.construirUrlMenuDigital(8443);
        assertTrue(urlLocal.startsWith("https://"), "Fallback local debe usar HTTPS");
        assertTrue(urlLocal.endsWith(":8443/api/public/menu-digital"),
                "Fallback local debe conservar puerto HTTPS y ruta pública");
        assertFalse(urlLocal.contains("trycloudflare.com"), "Sin túnel activo no debe retornar trycloudflare");
    }

    @Test
    @DisplayName("Endpoint /api/public/menu-digital envía Cache-Control: no-store y no escribe caché estático")
    void testEndpointDinamicoEnviaCacheControlNoStoreYNoGeneraArchivoEstatico() throws Exception {
        Path htmlEstatico = Path.of("uploads", "qr", "menu_digital.html");
        Files.deleteIfExists(htmlEstatico);

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        ApiServer server = ApiServer.crearServidorAislado(
                new JwtService(TEST_SECRET),
                CorsPolicy.desdeCadena("https://localhost:8443"),
                new AuthRateLimiter()
        );
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/public/menu-digital"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
            assertTrue(cacheControl.contains("no-store"),
                    "El endpoint debe enviar Cache-Control con no-store, recibido: " + cacheControl);
            assertTrue(response.body().contains("FOODCORE RESTOBAR"),
                    "Debe renderizar el HTML del menú digital");
            assertFalse(Files.exists(htmlEstatico),
                    "Regla de oro v4.6.0: no se debe crear ni cachear uploads/qr/menu_digital.html");
        } finally {
            server.detener();
        }
    }
}
