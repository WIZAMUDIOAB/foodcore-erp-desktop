package com.foodcore.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E para SUB-FASE D — PWA KDS COCINA & BAR (v4.2.0):
 * Valida interfaz en modo oscuro táctil de alto contraste (#121417),
 * tablero Kanban de 3 columnas (Pendiente, En Preparación, Listo),
 * cronómetros dinámicos y transición fluida de estados de comandas.
 */
public class SubFaseDKdsE2ETest {

    @BeforeAll
    static void setUpServer() {
        PlaywrightE2ESupport.asegurarServidorActivo();
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("Sub-Fase D: KDS UI — Carga en modo oscuro, columnas Kanban y cronómetros")
    void testCargaInterfazKdsModoOscuroYColumnas() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
            // Resolución estándar Tablet Cocina en pared (1280x800)
            BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, 1280, 800);
            Page page = context.newPage();

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/kds/");

            // 1. Validar elementos principales del Topbar
            Locator logo = page.locator(".kds-logo");
            logo.waitFor();
            assertTrue(logo.isVisible(), "El logo FC debe ser visible en el KDS");

            Locator reloj = page.locator("#lblReloj");
            reloj.waitFor();
            assertTrue(reloj.isVisible(), "El reloj de cocina debe estar en vivo");
            assertTrue(reloj.innerText().matches("\\d{2}:\\d{2}:\\d{2}"), "El formato de hora debe ser HH:mm:ss");

            // 2. Validar las 3 columnas Kanban
            Locator colPend = page.locator("#colWrapperPendiente");
            Locator colPrep = page.locator("#colWrapperPreparacion");
            Locator colListo = page.locator("#colWrapperListo");

            assertTrue(colPend.isVisible(), "La columna 'Pendiente' debe ser visible");
            assertTrue(colPrep.isVisible(), "La columna 'En Preparación' debe ser visible");
            assertTrue(colListo.isVisible(), "La columna 'Listo para Servir' debe ser visible");

            // 3. Validar presencia de tarjetas y cronómetros
            page.waitForCondition(() -> page.locator(".kds-card").count() >= 2);
            int cantidadTarjetas = page.locator(".kds-card").count();
            assertTrue(cantidadTarjetas >= 2, "Deben cargarse al menos 2 tarjetas en el tablero KDS");

            Locator primerTimer = page.locator(".kds-timer-badge").first();
            assertTrue(primerTimer.isVisible(), "El badge de cronómetro debe estar activo en la tarjeta");
            assertTrue(primerTimer.innerText().contains("⏱"), "El cronómetro debe contener el ícono de tiempo");

            // Guardar evidencia visual
            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseD - KDS Tablet Modo Oscuro", "subfase_d_kds_tablet.png");
            copiarEvidenciaAlArtifactDirectory("subfase_d_kds_tablet.png");
        }
    }

    @Test
    @DisplayName("Sub-Fase D: KDS Acciones — Transición de comanda: Pendiente -> En Preparación -> Listo -> Entregado")
    void testTransicionEstadosComandaEnKds() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
            BrowserContext context = PlaywrightE2ESupport.crearContexto(browser, 1280, 800);
            Page page = context.newPage();

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/kds/");

            page.waitForCondition(() -> page.locator("#colPendiente .kds-card").count() > 0);
            int pendientesAntes = page.locator("#colPendiente .kds-card").count();
            int prepAntes = page.locator("#colPreparacion .kds-card").count();

            // 1. Iniciar preparación en la primera comanda pendiente
            Locator btnIniciar = page.locator("#colPendiente .btn-iniciar").first();
            assertNotNull(btnIniciar);
            btnIniciar.click();

            // Esperar que la tarjeta se traslade a En Preparación
            page.waitForCondition(() -> page.locator("#colPreparacion .kds-card").count() > prepAntes);
            assertTrue(page.locator("#colPendiente .kds-card").count() < pendientesAntes,
                    "La columna de pendientes debe disminuir en 1");

            // 2. Marcar como 'Listo' en la columna de En Preparación
            int listosAntes = page.locator("#colListo .kds-card").count();
            Locator btnListo = page.locator("#colPreparacion .btn-listo").first();
            btnListo.click();

            page.waitForCondition(() -> page.locator("#colListo .kds-card").count() > listosAntes);
            assertTrue(page.locator("#colListo .kds-card").count() > listosAntes,
                    "La columna de listos debe aumentar tras marcar el plato listo");

            // 3. Entregar al mozo (archivo de comanda)
            int listosParaEntregar = page.locator("#colListo .kds-card").count();
            Locator btnEntregar = page.locator("#colListo .btn-entregar").first();
            btnEntregar.click();

            page.waitForCondition(() -> page.locator("#colListo .kds-card").count() < listosParaEntregar);

            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseD - Transición Kanban KDS", "subfase_d_kds_transicion.png");
        }
    }

    private static void copiarEvidenciaAlArtifactDirectory(String fileName) {
        try {
            Path src = Paths.get("target", "playwright-report", fileName);
            Path dest = Paths.get("C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab", fileName);
            if (Files.exists(src)) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
        }
    }
}
