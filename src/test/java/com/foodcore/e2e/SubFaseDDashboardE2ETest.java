package com.foodcore.e2e;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E para SUB-FASE D — PWA DASHBOARD GERENCIAL (DUEÑO) (v4.2.0):
 * Valida diseño responsive Mobile-First para smartphone del dueño (390x844),
 * las 4 tarjetas de KPIs ejecutivos (Ventas, Ticket Prom., Transacciones, Plato Estrella),
 * el gráfico interactivo de ventas por hora y el drawer de navegación.
 */
public class SubFaseDDashboardE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUpServer() throws Exception {
        PlaywrightE2ESupport.asegurarServidorActivo();
        sembrarVentaRealParaDashboard();
    }

    private static void sembrarVentaRealParaDashboard() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext adminReq = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "ADMIN");
            APIResponse kpisResp = adminReq.get("/api/dashboard/kpis");
            APIResponse topResp = adminReq.get("/api/dashboard/top-platos");
            boolean yaHayDatosHoy = kpisResp.status() == 200
                    && MAPPER.readTree(kpisResp.text()).path("ventas_dia").asDouble(0.0) > 0.0
                    && topResp.status() == 200
                    && MAPPER.readTree(topResp.text()).size() > 0;
            if (!yaHayDatosHoy) {
                APIResponse estadoCaja = adminReq.get("/api/caja/estado");
                boolean abierta = estadoCaja.status() == 200
                        && MAPPER.readTree(estadoCaja.text()).path("abierta").asBoolean();
                if (!abierta) {
                    APIResponse abrirResp = adminReq.post("/api/caja/abrir",
                            RequestOptions.create()
                                    .setHeader("Content-Type", "application/json")
                                    .setData(Map.of(
                                            "nombre_caja", "Caja Dashboard Mobile E2E",
                                            "monto_inicial", 150.00
                                    )));
                    assertEquals(200, abrirResp.status(),
                            "Debe abrir caja para sembrar venta real de Dashboard; " + abrirResp.text());
                }
                APIResponse pedResp = adminReq.post("/api/pedidos",
                        RequestOptions.create()
                                .setHeader("Content-Type", "application/json")
                                .setData(Map.of(
                                        "uuid_local", UUID.randomUUID().toString(),
                                        "numero_mesa", "07",
                                        "items", List.of(Map.of("id_producto", 1, "cantidad", 2))
                                )));
                assertTrue(pedResp.status() == 201 || pedResp.status() == 200,
                        "Debe crearse pedido real para Dashboard; recibido " + pedResp.status() + ": " + pedResp.text());
                int idPedido = MAPPER.readTree(pedResp.text()).path("id_pedido").asInt();

                APIResponse cobroResp = adminReq.post("/api/caja/cobrar",
                        RequestOptions.create()
                                .setHeader("Content-Type", "application/json")
                                .setData(Map.of(
                                        "id_pedido", idPedido,
                                        "numero_mesa", "07",
                                        "tipo_comprobante", "BOLETA",
                                        "medio_pago", "EFECTIVO",
                                        "monto_recibido", 100.00
                                )));
                assertEquals(200, cobroResp.status(),
                        "Debe cobrarse el pedido real para Dashboard; recibido " + cobroResp.status() + ": " + cobroResp.text());
            }
            adminReq.dispose();
        }
    }

    @AfterAll
    static void tearDownServer() {
        PlaywrightE2ESupport.detenerServidorSiCorresponde();
    }

    @Test
    @DisplayName("Sub-Fase D: Dashboard Móvil — 4 KPIs ejecutivos y gráfico interactivo de ventas")
    void testCargaDashboardKpisYGraficoMobile() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
            // Resolución Mobile iPhone 14 / Samsung Galaxy (390x844)
            BrowserContext context = PlaywrightE2ESupport.crearContextoAutenticado(browser, 390, 844, "ADMIN");
            Page page = context.newPage();

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/dashboard/");

            // 1. Validar las 4 tarjetas KPI
            Locator lblVentas = page.locator("#lblVentasDia");
            lblVentas.waitFor();
            page.waitForCondition(() -> !lblVentas.innerText().contains("S/ 0.00"));
            assertTrue(lblVentas.innerText().contains("S/"), "El KPI de ventas debe mostrar la moneda en soles");

            Locator lblTicket = page.locator("#lblTicketPromedio");
            lblTicket.waitFor();
            assertTrue(lblTicket.innerText().contains("S/"), "El ticket promedio debe contener S/");

            Locator lblTx = page.locator("#lblTransacciones");
            lblTx.waitFor();
            assertTrue(lblTx.innerText().contains("tx"), "Las transacciones deben reportarse con sufijo 'tx'");

            Locator lblPlato = page.locator("#lblPlatoTop");
            lblPlato.waitFor();
            page.waitForCondition(() -> !lblPlato.innerText().contains("Cargando"));
            assertTrue(lblPlato.innerText().length() > 3, "El plato estrella debe estar identificado");

            // 2. Validar que el lienzo del gráfico existe y está montado
            Locator canvasGrafico = page.locator("#chartVentasHora");
            canvasGrafico.waitFor();
            assertTrue(canvasGrafico.isVisible(), "El canvas del gráfico de ventas por hora debe ser visible");

            // 3. Validar listas secundarias de ranking y mesas
            Locator topPlatos = page.locator("#topPlatosList");
            topPlatos.waitFor();
            page.waitForCondition(() -> page.locator(".top-dish-row").count() > 0);
            assertTrue(page.locator(".top-dish-row").count() > 0, "La lista de top platos debe tener contenido");

            // Guardar evidencia visual móvil
            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseD - Dashboard Dueño Móvil", "subfase_d_dashboard_mobile.png");
            PlaywrightE2ESupport.copiarEvidenciaAScreenshots("subfase_d_dashboard_mobile.png");
        }
    }

    @Test
    @DisplayName("Sub-Fase D: Dashboard Menú — Apertura y cierre del Drawer táctil")
    void testDrawerMenuNavegacion() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            Browser browser = PlaywrightE2ESupport.lanzarNavegador(playwright);
            BrowserContext context = PlaywrightE2ESupport.crearContextoAutenticado(browser, 390, 844, "ADMIN");
            Page page = context.newPage();

            page.navigate(PlaywrightE2ESupport.BASE_URL + "/dashboard/");

            Locator btnOpen = page.locator("#btnOpenDrawer");
            btnOpen.waitFor();

            Locator drawer = page.locator("#drawerMenu");
            assertFalse(drawer.getAttribute("class").contains("open"), "El drawer debe iniciar cerrado");

            // Abrir Drawer
            btnOpen.click();
            page.waitForCondition(() -> page.locator("#drawerMenu").getAttribute("class").contains("open"));
            assertTrue(drawer.getAttribute("class").contains("open"), "El drawer debe abrirse con la clase 'open'");

            // Verificar enlaces clave
            assertTrue(page.locator(".drawer-link:has-text('KDS')").isVisible(), "El enlace a KDS debe estar presente");

            // Cerrar Drawer
            Locator btnClose = page.locator("#btnCloseDrawer");
            btnClose.click();
            page.waitForCondition(() -> !page.locator("#drawerMenu").getAttribute("class").contains("open"));
            assertFalse(drawer.getAttribute("class").contains("open"), "El drawer debe cerrarse correctamente");

            PlaywrightE2ESupport.guardarEvidencia(page, "SubFaseD - Drawer Navegación Dashboard", "subfase_d_dashboard_drawer.png");
        }
    }
}
