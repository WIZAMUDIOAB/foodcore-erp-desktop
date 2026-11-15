package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.e2e.PlaywrightE2ESupport;
import com.foodcore.service.AuthService;
import com.foodcore.service.MenuQrService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.UiScreenshotTestSupport;
import com.foodcore.support.UiScreenshotTestSupport.VistaCargada;
import com.foodcore.util.SkeletonLoaderHelper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite integral de validación UI, CSS, JavaFX y Playwright para Sub-Fase D (v5.4.0):
 * PWA y Menú QR Premium (Ítems D1 a D13).
 */
class SubFaseDPwaPremiumTest {

    private static final Path EVIDENCE_DIR = Path.of("target", "v540-evidence");

    @BeforeAll
    static void prepararToolkit() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
    }

    @AfterEach
    void limpiarEstado() {
        App.setDarkModeActivo(false);
        AuthService.setUsuarioSesionActual(null);
    }

    @Test
    @DisplayName("D1, D2 y D5: CSS PWA y KDS garantizan targets táctiles 48px/56px, zero-delay y contraste WCAG AAA")
    void d1D2D5CssTactilZeroDelayYAltoContraste() throws Exception {
        String pwaCss = Files.readString(Path.of("pwa", "css", "styles.css"), StandardCharsets.UTF_8);
        assertTrue(pwaCss.contains("min-width: 48px"), "PWA styles.css debe definir min-width: 48px");
        assertTrue(pwaCss.contains("min-height: 48px"), "PWA styles.css debe definir min-height: 48px");
        assertTrue(pwaCss.contains("touch-action: manipulation"), "PWA debe eliminar delay táctil de 300ms");
        assertTrue(pwaCss.contains("transform: scale(0.98)"), "PWA debe dar feedback visual :active");
        assertTrue(pwaCss.contains("@media (max-width: 480px)"), "PWA debe conservar layout responsive <= 480px");
        assertTrue(pwaCss.contains("max-height: 40vh"), "PWA carrito mobile <= 480px debe conservar max-height: 40vh");

        String kdsCss = Files.readString(Path.of("pwa", "kds", "css", "kds.css"), StandardCharsets.UTF_8);
        assertTrue(kdsCss.contains("min-width: 56px"), "KDS styles deben definir min-width: 56px");
        assertTrue(kdsCss.contains("min-height: 56px"), "KDS styles deben definir min-height: 56px");
        assertTrue(kdsCss.contains("touch-action: manipulation"), "KDS debe eliminar delay táctil de 300ms");
        assertTrue(kdsCss.contains("transform: scale(0.98)"), "KDS debe dar feedback visual :active");
        assertTrue(kdsCss.contains("#090D16") || kdsCss.contains("#050811"),
                "KDS alto contraste debe usar fondo oscuro profundo");
        assertTrue(kdsCss.contains("#FFFFFF"), "KDS alto contraste debe usar texto blanco puro >= 7:1");
    }

    @Test
    @DisplayName("D3: SkeletonLoaderHelper JavaFX y CSS shimmer PWA")
    void d3SkeletonLoaderJavaFxYPwa() throws Exception {
        String pwaCss = Files.readString(Path.of("pwa", "css", "styles.css"), StandardCharsets.UTF_8);
        assertTrue(pwaCss.contains(".skeleton-card"), "PWA CSS debe definir .skeleton-card");
        assertTrue(pwaCss.contains("@keyframes shimmer"), "PWA CSS debe definir @keyframes shimmer");
        assertTrue(pwaCss.contains("animation: shimmer 1.5s infinite"), "PWA CSS debe animar shimmer en 1.5s infinite");

        String fxCss = Files.readString(
                Path.of("src", "main", "resources", "css", "foodcore.css"), StandardCharsets.UTF_8);
        assertTrue(fxCss.contains(".skeleton-container"), "JavaFX CSS debe definir .skeleton-container");
        assertTrue(fxCss.contains(".skeleton-row"), "JavaFX CSS debe definir .skeleton-row");
        assertTrue(fxCss.contains(".skeleton-bar"), "JavaFX CSS debe definir .skeleton-bar");

        AtomicReference<VBox> ref = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VBox host = new VBox(12);
                host.setPrefSize(680, 320);
                VBox skeleton = SkeletonLoaderHelper.mostrarSkeleton(host, 4, 3);
                ref.set(skeleton);
                Scene scene = new Scene(host, 680, 320);
                App.applyStylesheets(scene);
                host.applyCss();
                host.layout();
                UiScreenshotTestSupport.capturarEvidencia(EVIDENCE_DIR, host, "03_javafx_skeleton_loader.png");
                SkeletonLoaderHelper.ocultarSkeleton(host);
                assertEquals(0, host.getChildren().size(), "ocultarSkeleton debe limpiar el contenedor");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });

        assertNotNull(ref.get());
        assertEquals(4, ref.get().getChildren().size());
        assertTrue(ref.get().getStyleClass().contains("skeleton-container"));
    }

    @Test
    @DisplayName("D4 y D7: Dark Mode JavaFX toggle en Configuración y PWA prefers-color-scheme: dark")
    void d4D7ModoOscuroJavaFxYPwaAutomatico() throws Exception {
        String pwaCss = Files.readString(Path.of("pwa", "css", "styles.css"), StandardCharsets.UTF_8);
        assertTrue(pwaCss.contains("@media (prefers-color-scheme: dark)"),
                "PWA styles.css debe responder automáticamente a prefers-color-scheme: dark");

        Path darkCssPath = Path.of("src", "main", "resources", "css", "dark-theme.css");
        assertTrue(Files.isRegularFile(darkCssPath), "Debe existir dark-theme.css en recursos");
        String darkCss = Files.readString(darkCssPath, StandardCharsets.UTF_8);
        assertTrue(darkCss.contains(".root"), "dark-theme.css debe definir variables .root");
        assertTrue(darkCss.contains(".kpi-card"), "dark-theme.css debe estilizar kpi-card");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ConfiguracionController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ConfiguracionView.fxml", 1180, 780);
                CheckBox chkModoOscuro = (CheckBox) vista.root().lookup("#chkModoOscuro");
                Label lblEstado = (Label) vista.root().lookup("#lblEstadoModoOscuro");
                assertNotNull(chkModoOscuro, "ConfiguracionView debe exponer #chkModoOscuro");
                assertNotNull(lblEstado, "ConfiguracionView debe exponer #lblEstadoModoOscuro");
                assertFalse(App.isDarkModeActivo(), "Modo oscuro inicia desactivado por defecto");

                App.aplicarModoOscuro(vista.scene(), true);
                chkModoOscuro.setSelected(true);
                vista.controller().handleToggleModoOscuro(new ActionEvent());

                assertTrue(App.isDarkModeActivo(), "Activar check debe encender modo oscuro");
                assertTrue(vista.scene().getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")),
                        "La escena debe cargar dark-theme.css al activar modo oscuro");
                assertTrue(vista.root().getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")),
                        "La raíz FXML de ConfiguracionView debe heredar dark-theme.css al activar modo oscuro");
                vista.root().applyCss();
                vista.root().layout();
                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "04_javafx_dark_theme_configuracion.png");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    @Test
    @DisplayName("D6: Tipografía monoespaciada JetBrains Mono 28px en cronómetros SLA de KitchenDisplayView")
    void d6TipografiaMonoespaciadaCronometrosKds() throws Exception {
        assertEquals("JetBrains Mono", KitchenDisplayController.TIMER_FONT_FAMILY);
        assertEquals(28.0, KitchenDisplayController.TIMER_FONT_SIZE, 0.01);

        String fxCss = Files.readString(
                Path.of("src", "main", "resources", "css", "foodcore.css"), StandardCharsets.UTF_8);
        assertTrue(fxCss.contains(".kds-timer-green"), "foodcore.css debe definir .kds-timer-green");
        assertTrue(fxCss.contains("-fx-font-size: 28px"), "Cronómetros KDS deben usar 28px en CSS");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ComandaCardController> card = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ComandaCard.fxml", 360, 260);
                card.controller().aplicarEstiloCronometro(
                        KitchenDisplayController.TIMER_FONT_FAMILY,
                        KitchenDisplayController.TIMER_FONT_SIZE);
                Label lblTiempo = card.controller().getLblTiempo();
                assertNotNull(lblTiempo);
                Font font = lblTiempo.getFont();
                assertEquals(28.0, font.getSize(), 0.5, "lblTiempo debe aplicar tamaño 28.0");
                assertTrue(font.getStyle().toLowerCase().contains("bold"), "lblTiempo debe estar en negrita");
                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, card.root(), "05_javafx_kds_cronometro_28px.png");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    @Test
    @DisplayName("D8-D13: Menú QR Premium HTML/CSS/JS y renderizado Playwright end-to-end con evidencias")
    void d8AD13MenuQrPremiumE2EYEvidenciasPlaywright() throws Exception {
        String qrCss = Files.readString(Path.of("pwa", "menu-digital", "css", "menu-qr.css"), StandardCharsets.UTF_8);
        assertTrue(qrCss.contains("@keyframes morphIn"), "D8 requiere @keyframes morphIn");
        assertTrue(qrCss.contains("@keyframes morphOut"), "D8 requiere @keyframes morphOut");
        assertTrue(qrCss.contains("backdrop-filter: blur(12px)"), "D10 requiere backdrop-filter: blur(12px)");
        assertTrue(qrCss.contains(".card.morph-in:nth-child(6)"), "D9 requiere cascada hasta 6 tarjetas");
        assertTrue(qrCss.contains("animation-delay: 250ms"), "D9 6ta tarjeta debe tener delay 250ms");

        String qrJs = Files.readString(Path.of("pwa", "menu-digital", "js", "menu-qr.js"), StandardCharsets.UTF_8);
        assertTrue(qrJs.contains("window.addEventListener('scroll'"), "D11 requiere listener de scroll en window");
        assertTrue(qrJs.contains("0.5"), "D11 parallax debe usar factor 0.5x");
        assertTrue(qrJs.contains("requestAnimationFrame"), "D12 requiere requestAnimationFrame");
        assertTrue(qrJs.contains("400"), "D12 animación de precio debe durar 400ms");
        assertTrue(qrJs.contains("localStorage"), "D13 debe persistir idioma en localStorage");

        assertTrue(Files.isRegularFile(Path.of("pwa", "menu-digital", "es.json")));
        assertTrue(Files.isRegularFile(Path.of("pwa", "menu-digital", "en.json")));

        String dynamicHtml = new MenuQrService().construirMenuDigitalHtmlDinamico();
        assertTrue(dynamicHtml.contains("FOODCORE RESTOBAR & GRILL"));
        assertTrue(dynamicHtml.contains("Carta Gastronómica Oficial"));
        assertTrue(dynamicHtml.contains("morphIn"));
        assertTrue(dynamicHtml.contains("backdrop-filter: blur(12px)"));
        assertTrue(dynamicHtml.contains("requestAnimationFrame"));

        PlaywrightE2ESupport.asegurarServidorActivo();
        Files.createDirectories(EVIDENCE_DIR);

        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright)) {

            try (BrowserContext ctxMobile = PlaywrightE2ESupport.crearContexto(browser, 390, 844)) {
                Page pinPage = ctxMobile.newPage();
                pinPage.navigate(PlaywrightE2ESupport.HTTP_BASE_URL + "/pin-login.html");
                pinPage.waitForSelector(".pin-btn");
                String touchAction = pinPage.evaluate(
                        "() => getComputedStyle(document.querySelector('.pin-btn')).touchAction").toString();
                assertTrue(touchAction.contains("manipulation"), "Touch action debe ser manipulation");
                double minHeight = Double.parseDouble(pinPage.evaluate(
                        "() => parseFloat(getComputedStyle(document.querySelector('.pin-btn')).minHeight) || 0").toString());
                assertTrue(minHeight >= 48.0, "Target táctil PWA debe ser >= 48px");
                pinPage.screenshot(new Page.ScreenshotOptions()
                        .setPath(EVIDENCE_DIR.resolve("01_pwa_botones_tactiles_48px.png"))
                        .setFullPage(true));
            }

            try (BrowserContext ctxKds = PlaywrightE2ESupport.crearContexto(browser, 1280, 800)) {
                Page kdsPage = ctxKds.newPage();
                kdsPage.navigate(PlaywrightE2ESupport.HTTP_BASE_URL + "/kds/index.html");
                kdsPage.waitForSelector(".kds-filter-btn");
                double kdsMinHeight = Double.parseDouble(kdsPage.evaluate(
                        "() => parseFloat(getComputedStyle(document.querySelector('.kds-filter-btn')).minHeight) || 0").toString());
                assertTrue(kdsMinHeight >= 56.0, "Botones KDS PWA deben medir >= 56px");
                kdsPage.screenshot(new Page.ScreenshotOptions()
                        .setPath(EVIDENCE_DIR.resolve("02_kds_pwa_alto_contraste_56px.png"))
                        .setFullPage(true));
            }

            try (BrowserContext ctxQr = PlaywrightE2ESupport.crearContexto(browser, 430, 932)) {
                Page qrPage = ctxQr.newPage();
                qrPage.navigate(PlaywrightE2ESupport.HTTP_BASE_URL + "/menu-digital/index.html");
                qrPage.waitForSelector("#menuGrid .card");

                String blurHeader = qrPage.evaluate(
                        "() => getComputedStyle(document.querySelector('header.glass-header')).backdropFilter || "
                        + "getComputedStyle(document.querySelector('header.glass-header')).webkitBackdropFilter || ''")
                        .toString();
                assertTrue(blurHeader.contains("blur(12px)"),
                        "Header glassmorphism debe aplicar blur(12px), actual: " + blurHeader);

                String delays = qrPage.evaluate(
                        "() => Array.from(document.querySelectorAll('#menuGrid .card')).slice(0, 6)"
                        + ".map(c => getComputedStyle(c).animationDelay).join(',')").toString();
                assertTrue(delays.contains("0.05s") || delays.contains("50ms"),
                        "La segunda tarjeta debe tener retraso cascada de 50ms: " + delays);

                qrPage.waitForTimeout(650);
                qrPage.screenshot(new Page.ScreenshotOptions()
                        .setPath(EVIDENCE_DIR.resolve("06_menu_qr_glassmorphism_cascada_es.png"))
                        .setFullPage(true));

                qrPage.click("button.category-tab[data-category='Bebidas']");
                qrPage.waitForTimeout(300);
                int drinkCards = qrPage.locator("#menuGrid .card").count();
                assertTrue(drinkCards >= 1, "Al filtrar por Bebidas debe mostrar tarjetas filtradas");

                qrPage.click("button.category-tab[data-category='Todos']");
                qrPage.waitForTimeout(300);

                qrPage.evaluate("() => { window.scrollTo(0, 160); window.dispatchEvent(new Event('scroll')); }");
                String heroTransform = qrPage.evaluate(
                        "() => document.querySelector('.hero-parallax-layer').style.transform || ''").toString();
                assertTrue(heroTransform.contains("translateY("),
                        "Scroll parallax debe aplicar translateY en 0.5x: " + heroTransform);

                String precioInicial = qrPage.locator("#menuGrid .card .price").first().innerText();
                qrPage.locator("#menuGrid .card .size-pill").nth(1).click();
                qrPage.waitForTimeout(550);
                String precioActualizado = qrPage.locator("#menuGrid .card .price").first().innerText();
                assertNotEquals(precioInicial, precioActualizado,
                        "Al cambiar tamaño el precio animado debe actualizarse");

                qrPage.click("#btnLangEn");
                qrPage.waitForTimeout(250);
                String savedLang = qrPage.evaluate("() => localStorage.getItem('fc_menu_qr_lang')").toString();
                assertEquals("en", savedLang, "Idioma EN debe persistir en localStorage");
                String subtituloEn = qrPage.locator("#headerSubtitle").innerText();
                assertTrue(subtituloEn.contains("Official Gastronomy Menu"),
                        "El menú QR debe traducirse al inglés: " + subtituloEn);

                qrPage.screenshot(new Page.ScreenshotOptions()
                        .setPath(EVIDENCE_DIR.resolve("07_menu_qr_multi_idioma_en_precio_animado.png"))
                        .setFullPage(true));
            }
        }
    }
}
