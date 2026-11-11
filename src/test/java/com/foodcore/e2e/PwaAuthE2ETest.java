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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test 1 E2E con Playwright: Autenticación en la PWA de Mozos (SPEC-FOODCORE-v4.1.0 §2.2).
 */
public class PwaAuthE2ETest {

    @BeforeAll
    static void setUpServer() {
        PlaywrightE2ESupport.asegurarServidorActivo();
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("E2E 1: Autenticación PWA — Credenciales erróneas muestran error y credenciales válidas redirigen a mesas.html")
    void testAutenticacionPwaCompleta() {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright();
             Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
             BrowserContext context = PlaywrightE2ESupport.crearContexto(browser);
             Page page = context.newPage()) {

            // 1. Navegar a http://localhost:8080/
            page.navigate(PlaywrightE2ESupport.BASE_URL + "/");

            // 2. Verificar que el botón "Ingresar" está visible
            Locator btnIngresar = page.locator("button[type='submit']");
            btnIngresar.waitFor();
            assertTrue(btnIngresar.isVisible(), "El botón 'Ingresar' debe estar visible en la pantalla de login");
            assertTrue(btnIngresar.innerText().contains("Ingresar"), "El texto del botón debe contener 'Ingresar'");

            // 3. Ingresar credenciales erróneas y verificar mensaje de error
            page.fill("#txtUsername", "usuario_invalido");
            page.fill("#txtPassword", "clave_incorrecta_999");
            btnIngresar.click();

            Locator errorBanner = page.locator("#loginErrorMsg");
            errorBanner.waitFor();
            assertTrue(errorBanner.isVisible(), "Debe mostrarse el banner de error ante credenciales incorrectas");
            String textoError = errorBanner.innerText().toLowerCase();
            assertTrue(textoError.contains("credenciales") || textoError.contains("incorrect") || textoError.contains("inválid"),
                    "El mensaje de error debe informar credenciales inválidas: " + textoError);
            PlaywrightE2ESupport.guardarEvidencia(page, "PwaAuthE2ETest - Error Login", "01_e2e_auth_error.png");

            // 4. Ingresar credenciales correctas y verificar redirección a mesas.html
            page.fill("#txtUsername", "mozo");
            page.fill("#txtPassword", "Admin#2026!");
            btnIngresar.click();

            page.waitForURL("**/mesas.html**");
            assertTrue(page.url().contains("mesas.html"), "El login exitoso debe redirigir a mesas.html");
            PlaywrightE2ESupport.guardarEvidencia(page, "PwaAuthE2ETest - Login Exitoso", "01_e2e_auth_success.png");
        }
    }
}
