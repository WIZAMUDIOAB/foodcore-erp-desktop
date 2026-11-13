package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.api.ApiServer;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.TestSecuritySupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.RequestOptions;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Infraestructura compartida para la suite E2E con Microsoft Playwright (SPEC-FOODCORE-v4.1.0 §2, v4.2.0 Sub-Fase B).
 * Gestiona el ciclo de vida del servidor Javalin en {@code https://localhost:8443} (HTTPS con SSL autofirmado)
 * y {@code http://localhost:8080} (HTTP), generando reportes en {@code target/playwright-report/}.
 */
public final class PlaywrightE2ESupport {

    private static final Logger LOGGER = Logger.getLogger(PlaywrightE2ESupport.class.getName());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Map<String, String> TOKENS_POR_ROL = new ConcurrentHashMap<>();
    public static final String BASE_URL = "https://localhost:8443";
    public static final String HTTP_BASE_URL = "http://localhost:8080";
    public static final Path REPORT_DIR = Path.of("target", "playwright-report");
    public static final Path SCREENSHOTS_DIR = Path.of("target", "screenshots");

    private static boolean servidorIniciadoEnProceso = false;

    private PlaywrightE2ESupport() {
    }

    /**
     * Garantiza que {@code http://localhost:8080} esté activo y con catálogo de prueba listo.
     */
    public static synchronized void asegurarServidorActivo() {
        try {
            Files.createDirectories(REPORT_DIR);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso creando directorio playwright-report", e);
        }

        if (estaServidorRespondiendo()) {
            sembrarCatalogoSiBdDisponible();
            return;
        }

        sembrarCatalogoSiBdDisponible();

        try {
            ServidorSockets.getInstance().iniciar();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "ServidorSockets ya activo", e);
        }

        TestSecuritySupport.asegurarKeystoreE2E();
        TestSecuritySupport.asegurarJwtSecretTest();
        ApiServer.getInstance().iniciar(8080);
        if (!servidorIniciadoEnProceso) {
            servidorIniciadoEnProceso = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    ApiServer.getInstance().detener();
                } catch (Exception ignored) {
                }
            }));
        }
    }

    public static synchronized void detenerServidorSiCorresponde() {
        // El servidor Javalin se mantiene activo para toda la suite E2E y se cierra en el ShutdownHook de la JVM
    }

    public static Playwright crearPlaywright() {
        return Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
    }

    private static final double PLAYWRIGHT_TIMEOUT_MS = 45_000d;
    private static final long RETRY_AFTER_MAX_SEGUNDOS = 61L;

    /**
     * Respeta la ventana {@code Retry-After} si el servidor E2E compartido devuelve HTTP 429 en {@code /api/auth/*}.
     */
    public static void esperarRetryAfter(String retryAfterHeader) {
        long segundos = RETRY_AFTER_MAX_SEGUNDOS;
        if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
            try {
                segundos = Math.min(RETRY_AFTER_MAX_SEGUNDOS, Math.max(1L, Long.parseLong(retryAfterHeader.trim()) + 1L));
            } catch (NumberFormatException ignored) {
                segundos = RETRY_AFTER_MAX_SEGUNDOS;
            }
        }
        try {
            Thread.sleep(segundos * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera de Retry-After interrumpida", e);
        }
    }

    /**
     * Ejecuta un POST a {@code /api/auth/*} reintentando una vez si recibe HTTP 429.
     */
    public static APIResponse postAuthConReintento429(APIRequestContext request, String path, Object body) {
        return postAuthConReintento429(request, path, null, body);
    }

    /**
     * Ejecuta un POST opcionalmente autenticado a {@code /api/auth/*} reintentando una vez si recibe HTTP 429.
     */
    public static APIResponse postAuthConReintento429(
            APIRequestContext request, String path, String bearerToken, Object body) {
        RequestOptions opts = RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(body);
        if (bearerToken != null && !bearerToken.isBlank()) {
            opts.setHeader("Authorization", "Bearer " + bearerToken);
        }
        APIResponse resp = request.post(path, opts);
        for (int intento = 0; intento < 3 && resp.status() == 429; intento++) {
            esperarRetryAfter(resp.headers().get("retry-after"));
            resp = request.post(path, opts);
        }
        return resp;
    }

    /**
     * Envía el formulario web de login tolerando HTTP 429 ({@code Retry-After}) en el servidor E2E compartido.
     */
    public static Response submitLoginConReintento429(Page page, Locator submitBtn) {
        submitBtn.waitFor();
        Response resp = page.waitForResponse(
                r -> r.url().contains("/api/auth/login") && "POST".equalsIgnoreCase(r.request().method()),
                submitBtn::click);
        for (int intento = 0; intento < 3 && resp.status() == 429; intento++) {
            esperarRetryAfter(resp.headerValue("retry-after"));
            resp = page.waitForResponse(
                    r -> r.url().contains("/api/auth/login") && "POST".equalsIgnoreCase(r.request().method()),
                    submitBtn::click);
        }
        return resp;
    }

    public static Browser lanzarNavegador(Playwright playwright) {
        List<String> browserArgs = List.of(
                "--ignore-certificate-errors",
                "--allow-insecure-localhost",
                "--disable-web-security",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu"
        );
        try {
            return playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(PLAYWRIGHT_TIMEOUT_MS)
                    .setArgs(browserArgs));
        } catch (Exception exChromium) {
            Path edgeExe = resolverEdgeEjecutable();
            if (edgeExe != null) {
                return playwright.chromium().launch(
                        new BrowserType.LaunchOptions()
                                .setExecutablePath(edgeExe)
                                .setHeadless(true)
                                .setTimeout(PLAYWRIGHT_TIMEOUT_MS)
                                .setArgs(browserArgs));
            }
            return playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setChannel("msedge")
                            .setHeadless(true)
                            .setTimeout(PLAYWRIGHT_TIMEOUT_MS)
                            .setArgs(browserArgs));
        }
    }

    public static Path resolverEdgeEjecutable() {
        for (String envVar : List.of("ProgramFiles(x86)", "ProgramFiles")) {
            String baseDir = System.getenv(envVar);
            if (baseDir == null || baseDir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(baseDir, "Microsoft", "Edge", "Application", "msedge.exe");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static BrowserContext crearContexto(Browser browser) {
        return crearContexto(browser, 1366, 850);
    }

    public static BrowserContext crearContexto(Browser browser, int width, int height) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(width, height)
                .setIgnoreHTTPSErrors(true));
        context.setDefaultTimeout(PLAYWRIGHT_TIMEOUT_MS);
        context.setDefaultNavigationTimeout(PLAYWRIGHT_TIMEOUT_MS);
        return context;
    }

    public static APIRequestContext crearApiRequestContext(Playwright playwright) {
        return playwright.request().newContext(
                new APIRequest.NewContextOptions()
                        .setBaseURL(BASE_URL)
                        .setIgnoreHTTPSErrors(true)
                        .setTimeout(PLAYWRIGHT_TIMEOUT_MS));
    }

    public static APIRequestContext crearApiRequestContextAutenticado(Playwright playwright, String rol) {
        return playwright.request().newContext(
                new APIRequest.NewContextOptions()
                        .setBaseURL(BASE_URL)
                        .setIgnoreHTTPSErrors(true)
                        .setTimeout(PLAYWRIGHT_TIMEOUT_MS)
                        .setExtraHTTPHeaders(Map.of("Authorization", "Bearer " + obtenerTokenRol(rol))));
    }

    public static BrowserContext crearContextoAutenticado(Browser browser, int width, int height, String rol) {
        BrowserContext context = crearContexto(browser, width, height);
        try {
            String tokenJson = JSON_MAPPER.writeValueAsString(obtenerTokenRol(rol));
            context.addInitScript("if (!localStorage.getItem('fc_pwa_token')) { localStorage.setItem('fc_pwa_token', " + tokenJson + "); }");
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inyectar token JWT en BrowserContext", e);
        }
        return context;
    }

    public static String obtenerTokenRol(String rol) {
        String clave = rol == null ? "MOZO" : rol.trim().toUpperCase(Locale.ROOT);
        return TOKENS_POR_ROL.computeIfAbsent(clave, PlaywrightE2ESupport::autenticarYExtraerJwt);
    }

    private static String autenticarYExtraerJwt(String rolNormalizado) {
        String[] creds = switch (rolNormalizado) {
            case "ADMIN" -> new String[]{"admin", "Admin#2026!"};
            case "CAJERO" -> new String[]{"cajero", "Admin#2026!"};
            case "COCINA" -> new String[]{"cocinero", "Admin#2026!"};
            default -> new String[]{"mozo", "Admin#2026!"};
        };
        try {
            HttpClient client = crearInsecureHttpClient();
            String body = JSON_MAPPER.writeValueAsString(Map.of("username", creds[0], "password", creds[1]));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/login"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            for (int intento = 0; intento < 3 && resp.statusCode() == 429; intento++) {
                esperarRetryAfter(resp.headers().firstValue("retry-after").orElse(null));
                resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            }
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Login fallido para rol " + rolNormalizado + ": HTTP " + resp.statusCode());
            }
            JsonNode json = JSON_MAPPER.readTree(resp.body());
            String token = json.path("token").asText("");
            if (token.isBlank()) {
                throw new IllegalStateException("Respuesta de login sin JWT para rol " + rolNormalizado);
            }
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo obtener JWT para rol " + rolNormalizado, e);
        }
    }

    public static HttpClient crearInsecureHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sc)
                    .connectTimeout(Duration.ofSeconds(4))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar HttpClient con SSL permisivo", e);
        }
    }

    public static void guardarEvidencia(Page page, String nombreTest, String nombreArchivoPng) {
        try {
            Files.createDirectories(REPORT_DIR);
            if (page != null) {
                Path screenshotPath = REPORT_DIR.resolve(nombreArchivoPng);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(screenshotPath)
                        .setFullPage(true));
            }
            registrarEntradaReporteHtml(nombreTest, nombreArchivoPng);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso guardando evidencia Playwright", e);
        }
    }

    public static void registrarResultadoApiEnReporte(String nombreTest, String detalle) {
        try {
            Files.createDirectories(REPORT_DIR);
            registrarEntradaReporteHtml(nombreTest, detalle);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso registrando reporte API E2E", e);
        }
    }

    private static synchronized void registrarEntradaReporteHtml(String nombreTest, String evidencia) {
        try {
            Path indexHtml = REPORT_DIR.resolve("index.html");
            if (!Files.exists(indexHtml)) {
                String header = "<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'/>"
                        + "<title>FoodCore ERP v4.1.0 — Playwright E2E Report</title>"
                        + "<style>body{font-family:Inter,sans-serif;background:#F8F9FA;color:#111827;padding:24px;}"
                        + ".card{background:#fff;border:1px solid #E5E7EB;border-radius:12px;padding:16px;margin-bottom:12px;}"
                        + ".pass{color:#047857;font-weight:700;}</style></head><body>"
                        + "<h1>FoodCore ERP v4.1.0 — Reporte E2E Playwright</h1>\n";
                Files.writeString(indexHtml, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            }
            String entry = "<div class='card'><span class='pass'>[PASS]</span> <strong>"
                    + nombreTest + "</strong> — " + evidencia + " <small>(" + LocalDateTime.now() + ")</small></div>\n";
            Files.writeString(indexHtml, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso escribiendo index.html de reporte E2E", e);
        }
    }

    /** Copia una captura generada en {@code target/playwright-report} hacia {@code target/screenshots}. */
    public static void copiarEvidenciaAScreenshots(String fileName) {
        try {
            Path src = REPORT_DIR.resolve(fileName);
            if (!Files.exists(src)) {
                return;
            }
            if (!Files.exists(SCREENSHOTS_DIR)) {
                Files.createDirectories(SCREENSHOTS_DIR);
            }
            Files.copy(src, SCREENSHOTS_DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // La copia de evidencias es opcional y no debe afectar el resultado del test
        }
    }

    private static boolean estaServidorRespondiendo() {
        try {
            HttpClient client = crearInsecureHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sembrarCatalogoSiBdDisponible() {
        try {
            if (!"true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
                MySqlIntegrationTestSupport.inicializarEntornoMySql();
            }
            try (Connection conn = ConexionBD.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO categoria (id_categoria, nombre, descripcion, activo) VALUES "
                        + "(1, 'Hamburguesas', 'Hamburguesas al carbón', 1), "
                        + "(2, 'Alitas & Pollo', 'Alitas crujientes broaster', 1), "
                        + "(3, 'Bebidas', 'Bebidas heladas y artesanales', 1), "
                        + "(4, 'Guarniciones', 'Papas y complementos', 1) "
                        + "ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), activo = 1");

                st.executeUpdate("INSERT INTO producto (id_producto, id_categoria, codigo, nombre, descripcion, "
                        + "precio_venta, costo_estimado, disponible, activo, imagen_url, es_menu_del_dia) VALUES "
                        + "(1, 1, 'HAM-01', 'Hamburguesa Royal Doble', 'Doble carne angus 180g, huevo, queso cheddar y papas', 24.50, 9.50, 1, 1, NULL, 1), "
                        + "(2, 1, 'HAM-02', 'Hamburguesa Clásica', 'Carne 180g al carbón, queso cheddar fundido y vegetales', 18.00, 6.80, 1, 1, NULL, 0), "
                        + "(3, 2, 'ALI-01', 'Alitas BBQ Picantes', '6 alitas bañadas en salsa artesanal BBQ ligeramente picante', 22.00, 8.50, 1, 1, NULL, 0), "
                        + "(4, 2, 'CRIS-01', 'Combo Pollo Crispy', '3 piezas broaster crocantes con papas amarillas', 26.00, 10.20, 1, 1, NULL, 1), "
                        + "(5, 3, 'BEB-01', 'Limonada Frozen Artesanal', 'Limonada natural frappeada con hierbabuena', 14.00, 3.50, 1, 1, NULL, 1), "
                        + "(6, 3, 'BEB-02', 'Chicha Morada Tradicional', 'Receta tradicional con manzana y canela', 12.00, 3.00, 1, 1, NULL, 0), "
                        + "(7, 4, 'COM-01', 'Papas Fritas Supremas', 'Porción gigante de papas con queso fundido y tocino', 11.50, 4.00, 1, 1, NULL, 0) "
                        + "ON DUPLICATE KEY UPDATE id_categoria = VALUES(id_categoria), nombre = VALUES(nombre), "
                        + "precio_venta = VALUES(precio_venta), disponible = 1, activo = 1, imagen_url = VALUES(imagen_url), es_menu_del_dia = VALUES(es_menu_del_dia)");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Usando catálogo embebido de respaldo en ApiServer para entorno CI sin MySQL local", e);
        }
    }
}
