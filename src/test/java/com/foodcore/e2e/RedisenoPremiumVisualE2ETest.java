package com.foodcore.e2e;

import com.foodcore.App;
import com.foodcore.controller.ConfiguracionController;
import com.foodcore.controller.DashboardController;
import com.foodcore.controller.MainDashboardController;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.MenuQrService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.UiScreenshotTestSupport;
import com.foodcore.support.UiScreenshotTestSupport.VistaCargada;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de regresión visual automatizada (toHaveScreenshot &lt;= 2% diff) y validación
 * del Checklist de Aprobación UI/UX Premium (Sub-Fase D).
 */
public class RedisenoPremiumVisualE2ETest {

    private static final Logger LOGGER = Logger.getLogger(RedisenoPremiumVisualE2ETest.class.getName());
    private static final Path BASELINE_DIR = Path.of("src", "test", "resources", "visual-baselines");
    private static final Path EVIDENCE_DIR = Path.of("target", "rediseno-premium-evidence");
    private static final double MAX_DIFF_PIXEL_RATIO = 0.02;
    private static final int CHANNEL_TOLERANCE = 12;
    private static final Pattern HEX_PATTERN = Pattern.compile("#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})\\b");
    private static final Pattern INLINE_STYLE_HEX = Pattern.compile("style\\s*=\\s*\"[^\"]*#[0-9A-Fa-f]{3,8}[^\"]*\"");

    @BeforeAll
    static void prepararInfraestructura() throws IOException {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        PlaywrightE2ESupport.asegurarServidorActivo();
        Files.createDirectories(BASELINE_DIR);
        Files.createDirectories(EVIDENCE_DIR);
    }

    @AfterAll
    static void detenerServidorSiAplica() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @AfterEach
    void restaurarEstadoUi() {
        App.setDarkModeActivo(false);
        AuthService.setUsuarioSesionActual(null);
    }

    @Test
    @DisplayName("Regresión visual Playwright (<=2% diff) y dual-mode en PIN, Menú QR, KDS y Dashboard móvil")
    void validarRegresionVisualPlaywrightYModoDual() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright)) {

            // 1. PIN Login — Claro & Oscuro (sin input visible arriba del teclado)
            try (BrowserContext ctxPin = PlaywrightE2ESupport.crearContexto(browser, 390, 844)) {
                Page pinPage = ctxPin.newPage();
                pinPage.addInitScript("() => localStorage.setItem('fc_theme', 'light')");
                pinPage.navigate(PlaywrightE2ESupport.HTTP_BASE_URL + "/pin-login.html");
                pinPage.waitForSelector(".pin-keypad-grid .pin-btn");

                int visibleInputs = ((Number) pinPage.evaluate(
                        "() => Array.from(document.querySelectorAll('input')).filter(i => i.offsetParent !== null).length"))
                        .intValue();
                assertEquals(0, visibleInputs, "PIN Login no debe mostrar inputs visibles encima del teclado");
                assertEquals(4, pinPage.locator("#pinDotsDisplay .pin-dot").count(),
                        "Debe mostrar exactamente 4 indicadores circulares");

                String bgLightPin = evaluarComputedStyle(pinPage, "body", "backgroundColor");
                assertEquals("rgb(249, 247, 244)", bgLightPin, "Fondo claro PIN Login debe ser #F9F7F4");
                assertMinTouchTarget(pinPage, ".pin-btn", 48.0);
                toHaveScreenshot(pinPage, "pin-login-light.png", MAX_DIFF_PIXEL_RATIO, false);

                pinPage.click("#btnThemeToggle");
                pinPage.waitForCondition(() -> Boolean.TRUE.equals(
                        pinPage.evaluate("() => document.documentElement.classList.contains('dark')")));
                String bgDarkPin = evaluarComputedStyle(pinPage, "body", "backgroundColor");
                String cardDarkPin = evaluarComputedStyle(pinPage, ".pin-login-container", "backgroundColor");
                assertEquals("rgb(9, 9, 11)", bgDarkPin, "Fondo oscuro PIN Login debe ser #09090B");
                assertEquals("rgb(24, 24, 27)", cardDarkPin, "Superficie oscura PIN Card debe ser #18181B");
                toHaveScreenshot(pinPage, "pin-login-dark.png", MAX_DIFF_PIXEL_RATIO, false);
            }

            // 2. Menú Digital QR — Claro & Oscuro (4:3 neutral Feather placeholder, glass-panel)
            try (BrowserContext ctxQr = PlaywrightE2ESupport.crearContexto(browser, 430, 932)) {
                Page qrPage = ctxQr.newPage();
                qrPage.addInitScript("() => localStorage.setItem('fc_theme', 'light')");
                qrPage.navigate(PlaywrightE2ESupport.HTTP_BASE_URL + "/menu-digital/index.html");
                qrPage.waitForSelector("#menuGrid .card");
                qrPage.waitForTimeout(450);

                String summaryPos = evaluarComputedStyle(qrPage, "#orderSummaryBar", "position");
                assertEquals("fixed", summaryPos, "La barra de resumen QR debe estar fija en bottom: 0");
                String cardBgLight = evaluarComputedStyle(qrPage, "#menuGrid .card", "backgroundColor");
                assertEquals("rgb(255, 255, 255)", cardBgLight, "Card QR claro debe ser #FFFFFF");
                assertEquals(0, qrPage.locator("#menuGrid .card-img-wrap circle").count(),
                        "Placeholders QR v2.0 no deben usar círculos marrones");
                assertMinTouchTarget(qrPage, ".category-tab", 48.0);
                toHaveScreenshot(qrPage, "menu-qr-light.png", MAX_DIFF_PIXEL_RATIO, false);

                qrPage.click("#btnThemeToggle");
                qrPage.waitForCondition(() -> Boolean.TRUE.equals(
                        qrPage.evaluate("() => document.documentElement.classList.contains('dark')")));
                String bgDarkQr = evaluarComputedStyle(qrPage, "body", "backgroundColor");
                String cardBgDark = evaluarComputedStyle(qrPage, "#menuGrid .card", "backgroundColor");
                assertEquals("rgb(9, 9, 11)", bgDarkQr, "Fondo oscuro Menú QR debe ser #09090B");
                assertEquals("rgb(24, 24, 27)", cardBgDark, "Card QR oscuro debe ser #18181B");
                toHaveScreenshot(qrPage, "menu-qr-dark.png", MAX_DIFF_PIXEL_RATIO, false);
            }

            // 3. KDS — 3 Estados Semánticos (verde, ámbar, rojo) & Modo Oscuro/Claro
            try (BrowserContext ctxKds = PlaywrightE2ESupport.crearContexto(browser, 1280, 800)) {
                Page kdsPage = ctxKds.newPage();
                kdsPage.addInitScript("() => { localStorage.removeItem('fc_theme'); }");
                configurarRutasDeterministicasKds(kdsPage);
                kdsPage.navigate(PlaywrightE2ESupport.HTTP_BASE_URL + "/kds/index.html");
                kdsPage.waitForSelector(".kds-card");
                congelarRelojYTimersKds(kdsPage);
                kdsPage.waitForSelector(".kds-card.state-red");

                assertEquals("rgb(9, 9, 11)", evaluarComputedStyle(kdsPage, "body", "backgroundColor"),
                        "KDS debe iniciar en OLED Zinc #09090B");
                assertEquals("rgb(24, 24, 27)", evaluarComputedStyle(kdsPage, ".kds-card", "backgroundColor"),
                        "Tarjetas KDS deben usar superficie #18181B");
                assertEquals("28px", evaluarComputedStyle(kdsPage, ".kds-timer-badge", "fontSize"),
                        "Cronómetro KDS debe medir 28px");
                assertTrue(evaluarComputedStyle(kdsPage, ".kds-timer-badge", "fontFamily")
                        .toLowerCase(Locale.ROOT).contains("jetbrains mono"),
                        "Cronómetro KDS debe usar JetBrains Mono");
                assertMinTouchTarget(kdsPage, ".kds-btn-action", 56.0);
                assertTrue(kdsPage.locator(".kds-card.state-green").count() >= 1);
                assertTrue(kdsPage.locator(".kds-card.state-amber").count() >= 1);
                assertTrue(kdsPage.locator(".kds-card.state-red").count() >= 1);
                toHaveScreenshot(kdsPage, "kds-timers-states.png", MAX_DIFF_PIXEL_RATIO, false);

                kdsPage.click("#btnThemeToggle");
                kdsPage.waitForCondition(() -> Boolean.FALSE.equals(
                        kdsPage.evaluate("() => document.documentElement.classList.contains('dark')")));
                assertEquals("rgb(249, 247, 244)", evaluarComputedStyle(kdsPage, "body", "backgroundColor"),
                        "KDS en modo claro debe usar #F9F7F4");
                toHaveScreenshot(kdsPage, "kds-timers-light.png", MAX_DIFF_PIXEL_RATIO, false);
            }

            // 4. Dashboard Móvil — Claro & Oscuro + Gráfico tokenizado
            try (BrowserContext ctxDash = PlaywrightE2ESupport.crearContexto(browser, 390, 844)) {
                Page dashPage = ctxDash.newPage();
                dashPage.addInitScript("() => localStorage.setItem('fc_theme', 'light')");
                configurarRutasDeterministicasDashboard(dashPage);
                dashPage.navigate(PlaywrightE2ESupport.HTTP_BASE_URL + "/dashboard/index.html");
                dashPage.waitForCondition(() -> Boolean.TRUE.equals(dashPage.evaluate(
                        "() => document.getElementById('lblVentasDia').innerText.includes('2,410.50')")));
                dashPage.waitForTimeout(300);

                assertEquals("12px", evaluarComputedStyle(dashPage, ".kpi-card", "borderRadius"),
                        "KPI cards deben tener border-radius 12px (v2.0 --radius-lg)");
                assertEquals("20px", evaluarComputedStyle(dashPage, ".kpi-card", "paddingTop"),
                        "KPI cards deben tener padding 20px (--space-5)");
                assertEquals("rgb(249, 247, 244)", evaluarComputedStyle(dashPage, "body", "backgroundColor"),
                        "Dashboard claro debe usar #F9F7F4");
                assertMinTouchTarget(dashPage, "#btnOpenDrawer", 48.0);
                toHaveScreenshot(dashPage, "dashboard-mobile-light.png", MAX_DIFF_PIXEL_RATIO, true);

                dashPage.click("#btnThemeToggle");
                dashPage.waitForCondition(() -> Boolean.TRUE.equals(
                        dashPage.evaluate("() => document.documentElement.classList.contains('dark')")));
                dashPage.waitForTimeout(250);
                assertEquals("rgb(9, 9, 11)", evaluarComputedStyle(dashPage, "body", "backgroundColor"),
                        "Dashboard oscuro debe usar #09090B");
                assertEquals("rgb(24, 24, 27)", evaluarComputedStyle(dashPage, ".kpi-card", "backgroundColor"),
                        "KPI cards oscuras deben usar #18181B");
                toHaveScreenshot(dashPage, "dashboard-mobile-dark.png", MAX_DIFF_PIXEL_RATIO, true);
            }
        }
    }

    @Test
    @DisplayName("Evidencias JavaFX en Modo Claro (#F9F7F4 / #F2EFEA) y Modo Oscuro (#09090B / #18181B Zinc)")
    void capturarEvidenciaJavaFxLightYDark() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                // 1. Cockpit Layout (Light & Dark)
                VistaCargada<MainDashboardController> cockpit = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/MainLayout.fxml", 1280, 800);
                Usuario admin = new Usuario();
                admin.setIdUsuario(1);
                admin.setUsername("admin");
                admin.setNombreRol("ADMIN");
                cockpit.controller().aplicarControlAcceso(admin);
                App.aplicarModoOscuro(cockpit.scene(), false);
                UiScreenshotTestSupport.capturarEvidencia(EVIDENCE_DIR, cockpit.root(), "javafx-cockpit-light.png");
                App.aplicarModoOscuro(cockpit.scene(), true);
                UiScreenshotTestSupport.capturarEvidencia(EVIDENCE_DIR, cockpit.root(), "javafx-cockpit-dark.png");

                // 2. Dashboard Analytics (Light & Dark)
                VistaCargada<DashboardController> dash = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/DashboardView.fxml", 1280, 800);
                dash.controller().cargarMetricasDashboardSincrono();
                App.aplicarModoOscuro(dash.scene(), false);
                UiScreenshotTestSupport.capturarEvidencia(EVIDENCE_DIR, dash.root(), "javafx-dashboard-light.png");
                App.aplicarModoOscuro(dash.scene(), true);
                UiScreenshotTestSupport.capturarEvidencia(EVIDENCE_DIR, dash.root(), "javafx-dashboard-dark.png");

                // 3. Configuración (Light & Dark)
                VistaCargada<ConfiguracionController> config = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ConfiguracionView.fxml", 1180, 780);
                App.aplicarModoOscuro(config.scene(), false);
                UiScreenshotTestSupport.capturarEvidencia(EVIDENCE_DIR, config.root(), "javafx-config-light.png");
                App.aplicarModoOscuro(config.scene(), true);
                UiScreenshotTestSupport.capturarEvidencia(EVIDENCE_DIR, config.root(), "javafx-config-dark.png");
            } catch (IOException ex) {
                throw new IllegalStateException("Error generando capturas JavaFX Light/Dark", ex);
            }
        });
    }

    @Test
    @DisplayName("Checklist de Aprobación §11: cero hex fuera de tokens, sidebar clara, charts y paleta cerrada")
    void verificarChecklistAprobacionTokensYComponentes() throws Exception {
        // 1. Cero hexadecimales hardcodeados fuera de :root / .dark / [data-theme="dark"] / .root en CSS
        List<Path> cssFiles = List.of(
                Path.of("src", "main", "resources", "css", "foodcore.css"),
                Path.of("src", "main", "resources", "css", "dark-theme.css"),
                Path.of("pwa", "css", "styles.css"),
                Path.of("pwa", "kds", "css", "kds.css"),
                Path.of("pwa", "dashboard", "css", "dashboard.css"),
                Path.of("pwa", "menu-digital", "css", "menu-qr.css")
        );
        for (Path cssPath : cssFiles) {
            String css = Files.readString(cssPath, StandardCharsets.UTF_8);
            List<String> fueraDeTokens = encontrarHexFueraDeBloquesToken(css);
            assertTrue(fueraDeTokens.isEmpty(),
                    "CSS " + cssPath + " contiene hex fuera de :root/.dark/.root: " + fueraDeTokens);
        }

        // 2. Cero inline style="#..." en FXML y HTML PWA + cero morados/azules prohibidos
        List<Path> fxmlYHtml = new ArrayList<>();
        try (Stream<Path> fxmls = Files.walk(Path.of("src", "main", "resources", "com", "foodcore", "view"))) {
            fxmls.filter(p -> p.toString().endsWith(".fxml")).forEach(fxmlYHtml::add);
        }
        try (Stream<Path> htmls = Files.walk(Path.of("pwa"))) {
            htmls.filter(p -> p.toString().endsWith(".html")).forEach(fxmlYHtml::add);
        }
        for (Path markupPath : fxmlYHtml) {
            String content = Files.readString(markupPath, StandardCharsets.UTF_8);
            assertFalse(INLINE_STYLE_HEX.matcher(content).find(),
                    "Archivo " + markupPath + " contiene inline style con hex hardcodeado");
        }

        // 3. Menú QR dinámico sin hex fuera de :root / .dark y con tokens obligatorios
        String dynamicQr = new MenuQrService().construirMenuDigitalHtmlDinamico();
        int styleStart = dynamicQr.indexOf("<style>");
        int styleEnd = dynamicQr.indexOf("</style>");
        assertTrue(styleStart > 0 && styleEnd > styleStart, "MenuQrService debe incluir <style>");
        String dynamicCss = dynamicQr.substring(styleStart + 7, styleEnd);
        List<String> qrHexFuera = encontrarHexFueraDeBloquesToken(dynamicCss);
        assertTrue(qrHexFuera.isEmpty(), "Menú QR dinámico contiene hex fuera de tokens: " + qrHexFuera);
        assertFalse(INLINE_STYLE_HEX.matcher(dynamicQr).find(),
                "Menú QR dinámico no debe usar hex en atributos style=");

        // 4. Sidebar claro #F2EFEA, fuentes locales Inter y cero colores no autorizados (#8B5CF6 morado)
        String fxCss = Files.readString(Path.of("src", "main", "resources", "css", "foodcore.css"), StandardCharsets.UTF_8);
        assertTrue(fxCss.contains("-bg-sidebar: #F2EFEA"), "Sidebar JavaFX claro debe definir #F2EFEA");
        assertTrue(fxCss.contains("-bg-canvas: #F9F7F4"), "Canvas JavaFX claro debe definir #F9F7F4");
        assertFalse(fxCss.contains("#8B5CF6"), "Prohibido usar morado #8B5CF6");
        assertFalse(fxCss.contains("\"Segoe UI\""), "CSS no debe declarar Segoe UI como fuente");
        assertTrue(fxCss.contains(".kpi-card") && fxCss.contains("20px") && fxCss.contains("12px"),
                "JavaFX KPI cards deben definir 12px radius (v2.0) y 20px padding");

        String darkFxCss = Files.readString(
                Path.of("src", "main", "resources", "css", "dark-theme.css"), StandardCharsets.UTF_8);
        assertTrue(darkFxCss.contains("-bg-canvas: #09090B") && darkFxCss.contains("-bg-surface: #18181B"),
                "JavaFX modo oscuro v2.0 debe usar #09090B / #18181B Zinc");
        assertTrue(Files.isRegularFile(Path.of("src", "main", "resources", "fonts", "font-7.woff2")));
        assertTrue(Files.isRegularFile(Path.of("pwa", "fonts", "font-7.woff2")));

        // 5. Dashboard JS lee variables CSS sin hex hardcodeados y PIN Login sin input
        String dashJs = Files.readString(Path.of("pwa", "dashboard", "js", "dashboard.js"), StandardCharsets.UTF_8);
        assertTrue(dashJs.contains("getComputedStyle"), "Dashboard chart debe leer tokens con getComputedStyle");
        assertFalse(dashJs.contains("#F05A22"), "Dashboard JS no debe hardcodear #F05A22");
        assertFalse(dashJs.contains("#6B7280"), "Dashboard JS no debe hardcodear #6B7280");

        String pinHtml = Files.readString(Path.of("pwa", "pin-login.html"), StandardCharsets.UTF_8);
        assertFalse(pinHtml.contains("id=\"txtPinInput\""), "PIN Login no debe renderizar input de dígitos");

        String qrJs = Files.readString(Path.of("pwa", "menu-digital", "js", "menu-qr.js"), StandardCharsets.UTF_8);
        assertFalse(qrJs.contains("buildFallbackDishSvg"),
                "Menú QR v2.0 debe usar placeholder neutro con icono Feather, sin círculos SVG");

        // 6. Gráficos JavaFX en DashboardController usan 0.20 y paleta v2.0 (#EA580C/#F97316)
        String dashCtrl = Files.readString(
                Path.of("src", "main", "java", "com", "foodcore", "controller", "DashboardController.java"),
                StandardCharsets.UTF_8);
        assertTrue(dashCtrl.contains("#EA580C") && dashCtrl.contains("#F97316") && dashCtrl.contains("0.20"),
                "DashboardController chart debe usar tokens #EA580C/#F97316 con gradiente al 20%");
    }

    private static List<String> encontrarHexFueraDeBloquesToken(String cssConComentarios) {
        String css = cssConComentarios.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        List<String> violaciones = new ArrayList<>();
        int len = css.length();
        int braceDepth = 0;
        boolean inTokenBlock = false;
        int tokenDepth = -1;
        StringBuilder selectorActual = new StringBuilder();

        for (int i = 0; i < len; i++) {
            char c = css.charAt(i);
            if (c == '{') {
                String sel = selectorActual.toString().trim();
                selectorActual.setLength(0);
                braceDepth++;
                if (esSelectorToken(sel)) {
                    inTokenBlock = true;
                    tokenDepth = braceDepth;
                }
                int closeIdx = encontrarCierreBloque(css, i + 1);
                if (closeIdx > i && !css.substring(i + 1, closeIdx).contains("{")) {
                    String body = css.substring(i + 1, closeIdx);
                    if (!inTokenBlock && !esSelectorToken(sel)) {
                        Matcher m = HEX_PATTERN.matcher(body);
                        while (m.find()) {
                            violaciones.add(sel + " -> " + m.group());
                        }
                    }
                    i = closeIdx - 1;
                }
            } else if (c == '}') {
                selectorActual.setLength(0);
                if (inTokenBlock && braceDepth == tokenDepth) {
                    inTokenBlock = false;
                    tokenDepth = -1;
                }
                braceDepth = Math.max(0, braceDepth - 1);
            } else if (c == ';') {
                selectorActual.setLength(0);
            } else {
                selectorActual.append(c);
            }
        }
        return violaciones;
    }

    private static boolean esSelectorToken(String selector) {
        return selector.contains(":root")
                || selector.contains(".dark")
                || selector.contains("[data-theme=\"dark\"]")
                || selector.equals(".root")
                || selector.startsWith(".root ");
    }

    private static int encontrarCierreBloque(String css, int from) {
        int depth = 1;
        for (int j = from; j < css.length(); j++) {
            char ch = css.charAt(j);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return j;
                }
            }
        }
        return -1;
    }

    private static void configurarRutasDeterministicasKds(Page kdsPage) {
        String itemsJson = "["
                + "{\"id_detalle_pedido\":501,\"id_pedido\":101,\"numero_mesa\":\"04\",\"nombre\":\"Hamburguesa Royal Doble\",\"cantidad\":2,\"notas\":\"Sin cebolla, papas crocantes\",\"estado_cocina\":\"PENDIENTE\"},"
                + "{\"id_detalle_pedido\":502,\"id_pedido\":102,\"numero_mesa\":\"02\",\"nombre\":\"Lomo Saltado Clásico Criollo\",\"cantidad\":1,\"notas\":\"Término medio 3/4\",\"estado_cocina\":\"EN_PREPARACION\"},"
                + "{\"id_detalle_pedido\":503,\"id_pedido\":103,\"numero_mesa\":\"06\",\"nombre\":\"Ceviche Mixto con Chicharrón\",\"cantidad\":1,\"notas\":\"Ají moderado\",\"estado_cocina\":\"PENDIENTE\"},"
                + "{\"id_detalle_pedido\":504,\"id_pedido\":104,\"numero_mesa\":\"01\",\"nombre\":\"Limonada Frozen Artesanal\",\"cantidad\":2,\"notas\":\"Con hierbabuena\",\"estado_cocina\":\"LISTO\"}"
                + "]";
        kdsPage.route("**/api/kds/items", route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody(itemsJson)));
    }

    private static void congelarRelojYTimersKds(Page kdsPage) {
        kdsPage.evaluate("() => {"
                + "  for (let id = 1; id < 1000; id++) { clearInterval(id); }"
                + "  const clock = document.getElementById('lblReloj');"
                + "  if (clock) clock.textContent = '12:00:00';"
                + "  const states = ["
                + "    { id: '501', timerCls: 'timer-green', cardCls: 'state-green', label: '⏱ 03:00' },"
                + "    { id: '502', timerCls: 'timer-amber', cardCls: 'state-amber', label: '⏱ 07:15' },"
                + "    { id: '503', timerCls: 'timer-red',   cardCls: 'state-red',   label: '⏱ 12:40' },"
                + "    { id: '504', timerCls: 'timer-green', cardCls: 'state-green', label: '⏱ 04:00' }"
                + "  ];"
                + "  states.forEach(s => {"
                + "    const card = document.querySelector('.kds-card[data-id=\"' + s.id + '\"]');"
                + "    if (!card) return;"
                + "    card.classList.remove('state-green', 'state-amber', 'state-red');"
                + "    card.classList.add(s.cardCls);"
                + "    const badge = card.querySelector('.kds-timer-badge');"
                + "    if (badge) { badge.className = 'kds-timer-badge ' + s.timerCls; badge.textContent = s.label; }"
                + "  });"
                + "}");
    }

    private static void configurarRutasDeterministicasDashboard(Page dashPage) {
        dashPage.route("**/api/dashboard/kpis", r -> r.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("{\"ventas_dia\":2410.50,\"ticket_promedio\":48.20,\"transacciones\":50,"
                        + "\"plato_top\":\"Hamburguesa Royal Doble\",\"plato_top_unidades\":14,"
                        + "\"mesas_ocupadas\":4,\"mesas_totales\":12}")));
        dashPage.route("**/api/dashboard/ventas-por-hora", r -> r.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("[{\"hora\":\"12:00\",\"monto\":180},{\"hora\":\"13:00\",\"monto\":345.5},"
                        + "{\"hora\":\"14:00\",\"monto\":490},{\"hora\":\"15:00\",\"monto\":210},"
                        + "{\"hora\":\"19:00\",\"monto\":390},{\"hora\":\"20:00\",\"monto\":620},"
                        + "{\"hora\":\"21:00\",\"monto\":480}]")));
        dashPage.route("**/api/dashboard/top-platos", r -> r.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("[{\"ranking\":1,\"nombre\":\"Hamburguesa Royal Doble\",\"unidades_vendidas\":14},"
                        + "{\"ranking\":2,\"nombre\":\"Lomo Saltado Criollo\",\"unidades_vendidas\":11},"
                        + "{\"ranking\":3,\"nombre\":\"Ceviche Mixto Tradicional\",\"unidades_vendidas\":9}]")));
        dashPage.route("**/api/dashboard/ventas-recientes", r -> r.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("[{\"id_venta\":108,\"numero_mesa\":\"04\",\"total\":64.50,\"medio_pago\":\"TARJETA\",\"hora\":\"14:22\"},"
                        + "{\"id_venta\":107,\"numero_mesa\":\"02\",\"total\":38.00,\"medio_pago\":\"EFECTIVO\",\"hora\":\"13:50\"}]")));
    }

    private static String evaluarComputedStyle(Page page, String selector, String property) {
        return page.evaluate("([sel, prop]) => {"
                + "  const el = document.querySelector(sel);"
                + "  if (!el) return '';"
                + "  return getComputedStyle(el)[prop] || '';"
                + "}", List.of(selector, property)).toString();
    }

    private static void assertMinTouchTarget(Page page, String selector, double minPx) {
        double h = Double.parseDouble(page.evaluate(
                "sel => document.querySelector(sel).getBoundingClientRect().height", selector).toString());
        assertTrue(h >= minPx, "Target táctil " + selector + " debe medir >= " + minPx + "px, actual: " + h);
    }

    private static void toHaveScreenshot(Page page, String filename, double maxDiffPixelRatio, boolean fullPage)
            throws IOException {
        page.evaluate("() => document.fonts ? document.fonts.ready : Promise.resolve()");
        page.evaluate("() => {"
                + "  if (!document.getElementById('fc-freeze-motion')) {"
                + "    const st = document.createElement('style');"
                + "    st.id = 'fc-freeze-motion';"
                + "    st.textContent = '*, *::before, *::after { animation: none !important; transition: none !important; }';"
                + "    document.head.appendChild(st);"
                + "  }"
                + "}");

        Path actualPath = EVIDENCE_DIR.resolve(filename);
        Path baselinePath = BASELINE_DIR.resolve(filename);
        page.screenshot(new Page.ScreenshotOptions().setPath(actualPath).setFullPage(fullPage));
        assertTrue(Files.exists(actualPath) && Files.size(actualPath) > 2000,
                "La captura visual " + actualPath + " está vacía");

        if (!Files.exists(baselinePath)) {
            Files.copy(actualPath, baselinePath);
            LOGGER.info(() -> "Baseline visual generada: " + baselinePath);
            return;
        }

        BufferedImage expected = ImageIO.read(baselinePath.toFile());
        BufferedImage actual = ImageIO.read(actualPath.toFile());
        assertNotNull(expected, "No se pudo leer baseline " + baselinePath);
        assertNotNull(actual, "No se pudo leer captura actual " + actualPath);
        assertEquals(expected.getWidth(), actual.getWidth(),
                "Ancho visual distinto para " + filename);
        assertEquals(expected.getHeight(), actual.getHeight(),
                "Alto visual distinto para " + filename);

        int width = expected.getWidth();
        int height = expected.getHeight();
        long totalPixels = (long) width * height;
        long diffPixels = 0;
        BufferedImage diffImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgbA = expected.getRGB(x, y);
                int rgbB = actual.getRGB(x, y);
                int dr = Math.abs(((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF));
                int dg = Math.abs(((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF));
                int db = Math.abs((rgbA & 0xFF) - (rgbB & 0xFF));
                if (dr > CHANNEL_TOLERANCE || dg > CHANNEL_TOLERANCE || db > CHANNEL_TOLERANCE) {
                    diffPixels++;
                    diffImage.setRGB(x, y, Color.RED.getRGB());
                } else {
                    diffImage.setRGB(x, y, rgbB);
                }
            }
        }

        final long diffCount = diffPixels;
        double ratio = totalPixels > 0 ? (double) diffCount / totalPixels : 0.0;
        LOGGER.info(() -> String.format(Locale.ROOT,
                "toHaveScreenshot(%s): diff=%.4f%% (%d/%d px, limite=%.2f%%)",
                filename, ratio * 100.0, diffCount, totalPixels, maxDiffPixelRatio * 100.0));

        if (ratio > maxDiffPixelRatio) {
            Path diffPath = EVIDENCE_DIR.resolve("diff-" + filename);
            ImageIO.write(diffImage, "png", diffPath.toFile());
        }
        assertTrue(ratio <= maxDiffPixelRatio,
                String.format(Locale.ROOT, "Regresión visual en %s: %.2f%% > %.2f%% (%s)",
                        filename, ratio * 100.0, maxDiffPixelRatio * 100.0, baselinePath));
    }
}
