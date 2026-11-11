package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E para SUB-FASE C.2 — Caja y Punto de Venta:
 * - Consulta de estado de turno (GET /api/caja/estado)
 * - Apertura de turno con fondo inicial (POST /api/caja/abrir)
 * - Cobro de comanda, emisión de boleta/factura y liberación de mesa (POST /api/caja/cobrar)
 * - Cierre Z de turno con arqueo de caja (POST /api/caja/cerrar)
 */
public class SubFaseC2CajaE2ETest {

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
    @DisplayName("Sub-Fase C.2: Caja — Flujo completo de turno: Apertura, Cobro con Boleta y Cierre Z")
    void testFlujoCompletoCaja() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "CAJERO");

            // 1. Abrir turno de caja
            APIResponse respAbrir = request.post("/api/caja/abrir",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of(
                                    "id_usuario", 1,
                                    "nombre_caja", "Caja Principal - Turno Tarde",
                                    "monto_inicial", 150.00
                            )));
            assertEquals(200, respAbrir.status(), "Apertura de caja debe responder 200 OK");
            JsonNode jsonAbrir = MAPPER.readTree(respAbrir.text());
            assertEquals("OK", jsonAbrir.path("status").asText());
            int idCaja = jsonAbrir.path("id_caja").asInt();
            assertTrue(idCaja > 0, "Debe retornar un id_caja válido");

            // 2. Consultar estado activo de caja
            APIResponse respEstado = request.get("/api/caja/estado");
            assertEquals(200, respEstado.status());
            JsonNode jsonEstado = MAPPER.readTree(respEstado.text());
            assertTrue(jsonEstado.path("abierta").asBoolean(), "La caja debe reportar estado abierta = true");
            assertEquals(150.00, jsonEstado.path("monto_apertura").asDouble());

            // 3. Procesar cobro de comanda en mesa 04
            APIResponse respCobro = request.post("/api/caja/cobrar",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of(
                                    "id_pedido", 101,
                                    "numero_mesa", "04",
                                    "id_usuario", 1,
                                    "tipo_comprobante", "BOLETA",
                                    "medio_pago", "EFECTIVO",
                                    "total", 50.00,
                                    "monto_recibido", 100.00
                            )));
            assertEquals(200, respCobro.status(), "POST /api/caja/cobrar debe responder 200 OK");
            JsonNode jsonCobro = MAPPER.readTree(respCobro.text());
            assertEquals("OK", jsonCobro.path("status").asText());
            assertEquals("BOLETA", jsonCobro.path("tipo_comprobante").asText());
            assertTrue(jsonCobro.path("numero_comprobante").asText().startsWith("B001-"));
            assertEquals(50.00, jsonCobro.path("total").asDouble());
            assertEquals(50.00, jsonCobro.path("vuelto").asDouble());
            assertTrue(jsonCobro.path("mesa_liberada").asBoolean(), "El cobro debe liberar automáticamente la mesa");

            // 4. Cerrar turno de caja (Cierre Z) con arqueo físico
            APIResponse respCerrar = request.post("/api/caja/cerrar",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of(
                                    "id_caja", idCaja,
                                    "id_usuario", 1,
                                    "efectivo_fisico_contado", 200.00
                            )));
            assertEquals(200, respCerrar.status(), "POST /api/caja/cerrar debe responder 200 OK");
            JsonNode jsonCerrar = MAPPER.readTree(respCerrar.text());
            assertEquals("OK", jsonCerrar.path("status").asText());
            assertNotNull(jsonCerrar.get("monto_esperado"), "El cierre debe calcular monto_esperado");
            assertNotNull(jsonCerrar.get("monto_contado"), "El cierre debe indicar monto_contado");
            assertNotNull(jsonCerrar.get("diferencia"), "El cierre debe calcular diferencia");

            request.dispose();
        }
    }
}
