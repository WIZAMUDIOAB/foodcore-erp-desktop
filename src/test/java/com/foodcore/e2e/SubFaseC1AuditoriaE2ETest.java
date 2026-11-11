package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.controller.AuditoriaController;
import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.util.ConexionBD;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E de Auditoría y Trazabilidad de Mozos para SUB-FASE C.1 (SPEC-FOODCORE-v4.2.0):
 * - Persistencia y verificación de 'id_usuario_creador' en comanda / pedido
 * - Actualización de PIN numérico vía PATCH /api/usuarios/{id}/pin con hashing BCrypt
 * - Filtro de auditoría por Mozo / Operador en Backoffice
 */
public class SubFaseC1AuditoriaE2ETest {

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
    @DisplayName("Sub-Fase C.1: API — PATCH /api/usuarios/{id}/pin actualiza PIN y valida 4-6 dígitos")
    void testPatchPinUsuarioYValidacion() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "ADMIN");

            // 1. PIN inválido (< 4 dígitos) -> 400 Bad Request
            APIResponse respInvalido = request.patch("/api/usuarios/3/pin",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "12")));
            assertEquals(400, respInvalido.status(), "PIN de menos de 4 dígitos debe retornar 400 Bad Request");

            // 2. PIN inválido no numérico -> 400 Bad Request
            APIResponse respLetras = request.patch("/api/usuarios/3/pin",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "abcd")));
            assertEquals(400, respLetras.status(), "PIN no numérico debe retornar 400 Bad Request");

            // 3. Actualizar a nuevo PIN '5678' -> 200 OK
            APIResponse respActualizar = request.patch("/api/usuarios/3/pin",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "5678")));
            assertEquals(200, respActualizar.status(), "PATCH /api/usuarios/3/pin debe retornar 200 OK");
            JsonNode bodyOk = MAPPER.readTree(respActualizar.text());
            assertEquals("OK", bodyOk.path("status").asText());
            assertTrue(bodyOk.path("tiene_pin").asBoolean());

            // 4. Probar login con el nuevo PIN '5678'
            APIResponse respNuevoPinLogin = request.post("/api/auth/pin-login",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "5678", "username", "mozo")));
            assertEquals(200, respNuevoPinLogin.status(), "El nuevo PIN 5678 debe autenticar al mozo");

            // 5. Restaurar PIN original '1234' para estabilidad de la suite
            APIResponse respRestaurar = request.patch("/api/usuarios/3/pin",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(Map.of("pin", "1234")));
            assertEquals(200, respRestaurar.status());

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.1: API — POST /api/pedidos registra y audita 'id_usuario_creador'")
    void testCrearPedidoAuditaUsuarioCreador() throws Exception {
        try (Playwright playwright = PlaywrightE2ESupport.crearPlaywright()) {
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContextAutenticado(playwright, "ADMIN");

            String uuidPedido = UUID.randomUUID().toString();
            Map<String, Object> payload = Map.of(
                    "uuid_local", uuidPedido,
                    "numero_mesa", "09",
                    "id_usuario", 3,
                    "id_usuario_creador", 3,
                    "items", List.of(
                            Map.of(
                                    "id_producto", 1,
                                    "nombre", "Hamburguesa Clásica Artesanal",
                                    "cantidad", 2,
                                    "precio_unitario", 24.50,
                                    "notas", "Mesa 09 atendida por Mateo Quispe"
                            )
                    )
            );

            APIResponse respPedido = request.post("/api/pedidos",
                    RequestOptions.create()
                            .setHeader("Content-Type", "application/json")
                            .setData(payload));

            assertEquals(201, respPedido.status(), "Pedido con id_usuario_creador debe registrarse con 201 Created");
            JsonNode jsonPedido = MAPPER.readTree(respPedido.text());
            assertEquals(3, jsonPedido.path("id_usuario_creador").asInt(),
                    "La respuesta de /api/pedidos debe incluir 'id_usuario_creador' = 3");

            // Verificar recuperación por UUID
            APIResponse respConsulta = request.get("/api/pedidos/" + uuidPedido);
            assertEquals(200, respConsulta.status());
            JsonNode jsonConsulta = MAPPER.readTree(respConsulta.text());
            assertEquals(3, jsonConsulta.path("id_usuario_creador").asInt());

            request.dispose();
        }
    }

    @Test
    @DisplayName("Sub-Fase C.1: Backoffice — Filtrado de Auditoría por Mozo / Operador")
    void testFiltroAuditoriaPorMozoYCaptura() throws Exception {
        // Sembrar evento de prueba en auditoría si hay conexión a BD
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                AuditoriaDAO dao = new AuditoriaDAOImpl();
                dao.registrarEvento(conn, 3, "CREACION_COMANDA", "PEDIDO", null, "Comanda creada por Mateo Quispe (mozo)");
            }
        } catch (Exception ignored) {
        }

        AuditoriaController ctrl = new AuditoriaController();

        // Simular lista de auditoría con registros de mozo y admin
        ctrl.getListaAuditoria().clear();
        RegistroAuditoria reg1 = new RegistroAuditoria();
        reg1.setIdAuditoria(101);
        reg1.setFecha(LocalDateTime.now().minusMinutes(15));
        reg1.setNombreUsuario("Mateo Quispe (mozo)");
        reg1.setAccion("CREACION_COMANDA");
        reg1.setEntidadAfectada("PEDIDO");
        reg1.setDatosNuevos("Comanda Mesa 04 enviada a KDS");

        RegistroAuditoria reg2 = new RegistroAuditoria();
        reg2.setIdAuditoria(102);
        reg2.setFecha(LocalDateTime.now().minusMinutes(5));
        reg2.setNombreUsuario("Administrador");
        reg2.setAccion("MODIFICAR_PIN");
        reg2.setEntidadAfectada("USUARIO");
        reg2.setDatosNuevos("PIN de Mozo actualizado");

        ctrl.getListaAuditoria().addAll(List.of(reg1, reg2));

        // Probar filtro por "Mateo Quispe (mozo)"
        ctrl.aplicarFiltroMozo("Mateo Quispe (mozo)");
        assertEquals(1, ctrl.getListaAuditoria().size(), "El filtro por mozo debe retornar exactamente 1 registro");

        // Restablecer a todos
        ctrl.aplicarFiltroMozo("Todos los Mozos / Operadores");
        assertEquals(2, ctrl.getListaAuditoria().size(), "El filtro 'Todos' debe retornar todos los registros");
    }
}
