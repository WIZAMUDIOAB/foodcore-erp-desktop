package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E HOTFIX v4.3.1 — Responsive para celulares pequeños.
 * Emula un iPhone SE (375x667) y verifica que la PWA del Mozo, el Dashboard del Dueño
 * y el KDS no tengan desbordamiento horizontal ni elementos superpuestos. Incluye además
 * una verificación de regresión en tablet (1024x768) para asegurar que el diseño original se conserva.
 */
public class ResponsiveMobileE2ETest {

    private static final int MOBILE_WIDTH = 375;
    private static final int MOBILE_HEIGHT = 667;
    private static final int TABLET_WIDTH = 1024;
    private static final int TABLET_HEIGHT = 768;
    private static final double TOLERANCIA_PX = 2.0;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Detecta elementos visibles que se salgan del ancho del viewport. Se ignoran los contenedores
     * con desplazamiento horizontal intencional (chips de categoría, notas rápidas, filtros KDS)
     * y los elementos fijos fuera de pantalla (drawer cerrado, modales ocultos).
     */
    private static final String JS_DESBORDE_HORIZONTAL = "() => {"
            + "  const w = document.documentElement.clientWidth;"
            + "  const ignorar = '.category-tabs, .quick-notes-bar, .kds-topbar-center, .dash-drawer, .drawer-overlay, .mozo-switch-selector, .modal-backdrop, .toast-banner';"
            + "  const offenders = [];"
            + "  const raw = [];"
            + "  const describir = (el, r) => el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + '.' + String(el.className).split(' ').join('.') + ' [' + Math.round(r.left) + ',' + Math.round(r.right) + ']';"
            + "  document.querySelectorAll('body *').forEach(el => {"
            + "    const r = el.getBoundingClientRect();"
            + "    if (r.width === 0 || r.height === 0) return;"
            + "    if (r.right > w + 1 || r.left < -1) {"
            + "      if (el.closest(ignorar)) { raw.push(describir(el, r)); return; }"
            + "      offenders.push(describir(el, r));"
            + "    }"
            + "  });"
            + "  return JSON.stringify({ scrollWidth: document.documentElement.scrollWidth, clientWidth: w, offenders: offenders.slice(0, 10), ignorados: raw.slice(0, 10) });"
            + "}";

    @BeforeAll
    static void setUpServer() {
        PlaywrightE2ESupport.asegurarServidorActivo();
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("v4.3.1 Responsive: Catálogo Mozo en iPhone SE (375x667) — 1 plato por fila, carrito abajo y sin desborde")
    void testCatalogoMozoCelularPequeno() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, MOBILE_WIDTH, MOBILE_HEIGHT);
             Page page = context.newPage()) {

            // Login (pantalla de acceso también debe caber en 375px)
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/");
            page.locator("#txtUsername").waitFor();
            assertSinDesbordeHorizontal(page, "index.html (login)");

            page.fill("#txtUsername", "mozo");
            page.fill("#txtPassword", "Mozo2026!");
            page.click("button[type='submit']");
            page.waitForURL("**/mesas.html**");

            Locator mesa01Card = page.locator(".mesa-card", new Page.LocatorOptions().setHasText("Mesa 01")).first();
            mesa01Card.waitFor();
            assertSinDesbordeHorizontal(page, "mesas.html");
            mesa01Card.click();
            page.waitForURL("**/catalogo.html**");

            Locator cards = page.locator(".dish-card");
            cards.first().waitFor();
            page.waitForCondition(() -> cards.count() >= 2);

            // 1. Sin desborde horizontal en el catálogo (topbar, toolbar, tarjetas)
            assertSinDesbordeHorizontal(page, "catalogo.html");

            // 2. Un plato por fila: todas las tarjetas alineadas a la misma X y sin solaparse verticalmente
            BoundingBox grid = page.locator("#dishesGrid").boundingBox();
            List<BoundingBox> cajas = new ArrayList<>();
            int totalCards = Math.min(cards.count(), 6);
            for (int i = 0; i < totalCards; i++) {
                cajas.add(cards.nth(i).boundingBox());
            }
            for (int i = 0; i < cajas.size(); i++) {
                BoundingBox c = cajas.get(i);
                assertEquals(grid.x, c.x, TOLERANCIA_PX, "La tarjeta " + i + " debe iniciar en el borde izquierdo de la grilla (1 columna)");
                assertEquals(grid.width, c.width, TOLERANCIA_PX, "La tarjeta " + i + " debe ocupar todo el ancho de la grilla (1 columna)");
                if (i > 0) {
                    BoundingBox prev = cajas.get(i - 1);
                    assertTrue(c.y >= prev.y + prev.height - TOLERANCIA_PX,
                            "La tarjeta " + i + " no debe superponerse con la tarjeta anterior");
                }
            }

            // 3. Carrito fijo en la parte inferior, a todo el ancho y con altura máxima del 40% del viewport
            Locator cart = page.locator(".cart-sidebar");
            String position = (String) cart.evaluate("el => getComputedStyle(el).position");
            assertEquals("fixed", position, "El carrito debe quedar fijo en la parte inferior en celulares");
            BoundingBox cartBox = cart.boundingBox();
            assertEquals(0, cartBox.x, TOLERANCIA_PX, "El carrito debe iniciar en el borde izquierdo");
            assertEquals(MOBILE_WIDTH, cartBox.width, TOLERANCIA_PX, "El carrito debe ocupar el 100% del ancho");
            assertEquals(MOBILE_HEIGHT, cartBox.y + cartBox.height, TOLERANCIA_PX, "El carrito debe estar anclado al borde inferior");
            assertTrue(cartBox.height <= MOBILE_HEIGHT * 0.40 + TOLERANCIA_PX, "El carrito no debe superar el 40% de la altura de la pantalla");

            // 4. El flujo de agregar platos sigue funcionando y el botón 'Enviar a Cocina' es visible dentro del viewport
            cards.nth(0).locator(".btn-add-dish").click();
            cards.nth(1).locator(".btn-add-dish").click();
            assertEquals("2 ítems", page.locator("#cartCountPill").innerText().trim(), "El carrito debe mostrar 2 ítems");

            BoundingBox btnEnviar = page.locator("#btnEnviarCocina").boundingBox();
            assertTrue(btnEnviar.y >= 0 && btnEnviar.y + btnEnviar.height <= MOBILE_HEIGHT + TOLERANCIA_PX,
                    "El botón 'Enviar a Cocina' debe estar visible dentro de la pantalla");
            assertTrue(btnEnviar.x >= 0 && btnEnviar.x + btnEnviar.width <= MOBILE_WIDTH + TOLERANCIA_PX,
                    "El botón 'Enviar a Cocina' no debe desbordar horizontalmente");

            // 5. La última tarjeta puede desplazarse por encima del carrito (padding inferior suficiente)
            cards.last().scrollIntoViewIfNeeded();
            page.evaluate("() => window.scrollTo(0, document.documentElement.scrollHeight)");
            BoundingBox ultima = cards.last().boundingBox();
            BoundingBox cartTrasScroll = cart.boundingBox();
            assertTrue(ultima.y + ultima.height <= cartTrasScroll.y + TOLERANCIA_PX,
                    "Al final del scroll, la última tarjeta no debe quedar oculta detrás del carrito");
            assertSinDesbordeHorizontal(page, "catalogo.html (con carrito)");

            page.evaluate("() => window.scrollTo(0, 0)");
            PlaywrightE2ESupport.guardarEvidencia(page, "v4.3.1 Responsive - Catálogo Mozo iPhone SE", "v431_catalogo_mobile_375.png");
            copiarEvidenciaAlArtifactDirectory("v431_catalogo_mobile_375.png");
            guardarCapturaViewport(page, "v431_catalogo_mobile_375_viewport.png");
        }
    }

    @Test
    @DisplayName("v4.3.1 Responsive: PIN Login en iPhone SE (375x667) sin desborde horizontal")
    void testPinLoginCelularPequeno() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, MOBILE_WIDTH, MOBILE_HEIGHT);
             Page page = context.newPage()) {

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/pin-login.html");
            page.locator(".pin-keypad-grid").first().waitFor();
            assertSinDesbordeHorizontal(page, "pin-login.html");
            PlaywrightE2ESupport.guardarEvidencia(page, "v4.3.1 Responsive - PIN Login iPhone SE", "v431_pin_login_mobile_375.png");
        }
    }

    @Test
    @DisplayName("v4.3.1 Responsive: Dashboard Dueño en iPhone SE (375x667) — KPIs en 1 columna y drawer al 80%")
    void testDashboardCelularPequeno() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, MOBILE_WIDTH, MOBILE_HEIGHT);
             Page page = context.newPage()) {

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/dashboard/");
            Locator lblVentas = page.locator("#lblVentasDia");
            lblVentas.waitFor();
            page.locator("#chartVentasHora").waitFor();

            // 1. KPIs en una sola columna: misma X, ancho completo y apilados sin superposición
            BoundingBox grid = page.locator(".kpi-grid").boundingBox();
            Locator kpis = page.locator(".kpi-card");
            assertEquals(4, kpis.count(), "Deben existir 4 tarjetas KPI");
            BoundingBox anterior = null;
            for (int i = 0; i < 4; i++) {
                BoundingBox k = kpis.nth(i).boundingBox();
                assertEquals(grid.x, k.x, TOLERANCIA_PX, "El KPI " + i + " debe estar alineado a la izquierda (1 columna)");
                assertEquals(grid.width, k.width, TOLERANCIA_PX, "El KPI " + i + " debe ocupar todo el ancho (1 columna)");
                if (anterior != null) {
                    assertTrue(k.y >= anterior.y + anterior.height - TOLERANCIA_PX, "El KPI " + i + " no debe superponerse con el anterior");
                }
                anterior = k;
            }

            // 2. Gráfico a todo el ancho del contenido
            BoundingBox chart = page.locator("#cardGraficoVentas").boundingBox();
            assertEquals(grid.width, chart.width, TOLERANCIA_PX, "El gráfico debe ocupar todo el ancho disponible");

            assertSinDesbordeHorizontal(page, "dashboard/index.html");

            // 3. Drawer: cerrado fuera de pantalla y abierto al 80% del ancho
            Locator drawer = page.locator("#drawerMenu");
            BoundingBox drawerCerrado = drawer.boundingBox();
            assertTrue(drawerCerrado.x >= MOBILE_WIDTH - TOLERANCIA_PX, "El drawer cerrado debe quedar completamente fuera de la pantalla");

            page.locator("#btnOpenDrawer").click();
            page.waitForCondition(() -> drawer.getAttribute("class").contains("open"));
            page.waitForTimeout(450);
            BoundingBox drawerAbierto = drawer.boundingBox();
            assertEquals(MOBILE_WIDTH * 0.80, drawerAbierto.width, TOLERANCIA_PX, "El drawer debe ocupar el 80% del ancho");
            assertEquals(MOBILE_WIDTH, drawerAbierto.x + drawerAbierto.width, TOLERANCIA_PX, "El drawer abierto debe quedar alineado al borde derecho");
            page.locator("#btnCloseDrawer").click();
            page.waitForCondition(() -> !drawer.getAttribute("class").contains("open"));
            page.waitForTimeout(450);
            assertTrue(drawer.boundingBox().x >= MOBILE_WIDTH - TOLERANCIA_PX, "El drawer debe volver a ocultarse fuera de la pantalla");

            PlaywrightE2ESupport.guardarEvidencia(page, "v4.3.1 Responsive - Dashboard iPhone SE", "v431_dashboard_mobile_375.png");
            copiarEvidenciaAlArtifactDirectory("v431_dashboard_mobile_375.png");
            guardarCapturaViewport(page, "v431_dashboard_mobile_375_viewport.png");
        }
    }

    @Test
    @DisplayName("v4.3.1 Responsive: KDS en celular (375x667) — columnas Kanban apiladas sin desborde")
    void testKdsCelularPequeno() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, MOBILE_WIDTH, MOBILE_HEIGHT);
             Page page = context.newPage()) {

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/kds/");
            page.locator("#colWrapperListo").waitFor();

            BoundingBox col1 = page.locator("#colWrapperPendiente").boundingBox();
            BoundingBox col2 = page.locator("#colWrapperPreparacion").boundingBox();
            BoundingBox col3 = page.locator("#colWrapperListo").boundingBox();
            assertEquals(col1.x, col2.x, TOLERANCIA_PX, "Las columnas Kanban deben apilarse verticalmente");
            assertEquals(col1.x, col3.x, TOLERANCIA_PX, "Las columnas Kanban deben apilarse verticalmente");
            assertTrue(col2.y >= col1.y + col1.height - TOLERANCIA_PX, "'En Preparación' debe ubicarse debajo de 'Pendiente'");
            assertTrue(col3.y >= col2.y + col2.height - TOLERANCIA_PX, "'Listo' debe ubicarse debajo de 'En Preparación'");

            assertSinDesbordeHorizontal(page, "kds/index.html");
            PlaywrightE2ESupport.guardarEvidencia(page, "v4.3.1 Responsive - KDS Celular", "v431_kds_mobile_375.png");
            copiarEvidenciaAlArtifactDirectory("v431_kds_mobile_375.png");
        }
    }

    @Test
    @DisplayName("v4.3.1 Regresión Tablet (1024x768): catálogo con carrito lateral y Dashboard con 4 KPIs por fila")
    void testRegresionTabletSinCambios() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, TABLET_WIDTH, TABLET_HEIGHT);
             Page page = context.newPage()) {

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/");
            page.fill("#txtUsername", "mozo");
            page.fill("#txtPassword", "Mozo2026!");
            page.click("button[type='submit']");
            page.waitForURL("**/mesas.html**");
            Locator mesa01Card = page.locator(".mesa-card", new Page.LocatorOptions().setHasText("Mesa 01")).first();
            mesa01Card.waitFor();
            mesa01Card.click();
            page.waitForURL("**/catalogo.html**");

            Locator cards = page.locator(".dish-card");
            cards.first().waitFor();
            page.waitForCondition(() -> cards.count() >= 2);

            // Carrito lateral derecho (no fijo) con ancho original de 410px
            Locator cart = page.locator(".cart-sidebar");
            assertNotEquals("fixed", cart.evaluate("el => getComputedStyle(el).position"), "En tablet el carrito no debe ser fijo");
            BoundingBox cartBox = cart.boundingBox();
            assertEquals(410, cartBox.width, TOLERANCIA_PX, "En tablet el carrito conserva su ancho lateral de 410px");
            assertEquals(TABLET_WIDTH, cartBox.x + cartBox.width, TOLERANCIA_PX, "En tablet el carrito sigue anclado a la derecha");

            // Más de un plato por fila
            BoundingBox c0 = cards.nth(0).boundingBox();
            BoundingBox c1 = cards.nth(1).boundingBox();
            assertEquals(c0.y, c1.y, TOLERANCIA_PX, "En tablet los dos primeros platos deben compartir la misma fila");
            assertTrue(c1.x > c0.x + c0.width - TOLERANCIA_PX, "En tablet el segundo plato debe estar a la derecha del primero");

            // Dashboard: 4 KPIs en una sola fila
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/dashboard/");
            Locator kpis = page.locator(".kpi-card");
            kpis.first().waitFor();
            double yBase = kpis.nth(0).boundingBox().y;
            for (int i = 1; i < 4; i++) {
                assertEquals(yBase, kpis.nth(i).boundingBox().y, TOLERANCIA_PX, "En tablet los 4 KPIs deben estar en la misma fila");
            }
            BoundingBox drawer = page.locator("#drawerMenu").boundingBox();
            assertEquals(280, drawer.width, TOLERANCIA_PX, "En tablet el drawer conserva su ancho original de 280px");

            // KDS: tablero Kanban de 3 columnas lado a lado
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/kds/");
            page.locator("#colWrapperListo").waitFor();
            BoundingBox k1 = page.locator("#colWrapperPendiente").boundingBox();
            BoundingBox k2 = page.locator("#colWrapperPreparacion").boundingBox();
            assertEquals(k1.y, k2.y, TOLERANCIA_PX, "En tablet las columnas del KDS deben estar lado a lado");
            assertTrue(k2.x > k1.x + k1.width - TOLERANCIA_PX, "En tablet 'En Preparación' debe estar a la derecha de 'Pendiente'");
        }
    }

    private static void assertSinDesbordeHorizontal(Page page, String vista) throws Exception {
        String json = (String) page.evaluate(JS_DESBORDE_HORIZONTAL);
        JsonNode r = MAPPER.readTree(json);
        int scrollWidth = r.get("scrollWidth").asInt();
        int clientWidth = r.get("clientWidth").asInt();
        assertEquals(0, r.get("offenders").size(),
                vista + ": elementos que se salen del viewport -> " + r.get("offenders"));
        assertTrue(scrollWidth <= clientWidth,
                vista + ": no debe existir scroll horizontal (scrollWidth=" + scrollWidth + ", clientWidth=" + clientWidth
                        + ", contenedores con scroll propio fuera del viewport=" + r.get("ignorados") + ")");
    }

    /** Captura solo el viewport visible (como lo ve el usuario en el celular) y la copia como evidencia. */
    private static void guardarCapturaViewport(Page page, String fileName) {
        try {
            Files.createDirectories(PlaywrightE2ESupport.REPORT_DIR);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(PlaywrightE2ESupport.REPORT_DIR.resolve(fileName))
                    .setFullPage(false));
            copiarEvidenciaAlArtifactDirectory(fileName);
        } catch (Exception ignored) {
            // La evidencia visual es opcional y no debe afectar el resultado del test
        }
    }

    private static void copiarEvidenciaAlArtifactDirectory(String fileName) {
        try {
            Path src = Paths.get("target", "playwright-report", fileName);
            Path dest = Paths.get("C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab", fileName);
            if (Files.exists(src) && Files.exists(dest.getParent())) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // La copia de evidencias es opcional (no aplica en CI)
        }
    }
}
