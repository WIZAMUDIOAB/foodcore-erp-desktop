package com.foodcore.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.controller.AuditoriaController;
import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.util.ConexionBD;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite E2E de Auditoría y Trazabilidad de Mozos para SUB-FASE C.1 (SPEC-FOODCORE-v4.2.0):
 * - Persistencia y verificación de 'id_usuario_creador' en comanda / pedido
 * - Actualización de PIN numérico vía PATCH /api/usuarios/{id}/pin con hashing BCrypt
 * - Filtro de auditoría por Mozo / Operador en Backoffice
 */
public class SubFaseC1AuditoriaE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ARTIFACTS_DIR = "C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab";

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
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContext(playwright);

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
            APIRequestContext request = PlaywrightE2ESupport.crearApiRequestContext(playwright);

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
    @DisplayName("Sub-Fase C.1: Backoffice JavaFX — Filtrado de Auditoría por Mozo / Operador")
    void testFiltroAuditoriaPorMozoYCaptura() throws Exception {
        // Sembrar evento de prueba en auditoría si hay conexión a BD
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                AuditoriaDAO dao = new AuditoriaDAOImpl();
                dao.registrarEvento(conn, 3, "CREACION_COMANDA", "PEDIDO", null, "Comanda creada por Mateo Quispe (mozo)");
            }
        } catch (Exception ignored) {}

        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<AuditoriaController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/AuditoriaView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1180, 720);
                AuditoriaController ctrl = loader.getController();

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

                root.applyCss();
                root.layout();
                rootRef.set(root);
                ctrlRef.set(ctrl);
            } catch (Exception e) {
                throw new RuntimeException("Error en test de AuditoriaView", e);
            }
        });

        // Capturar evidencia fotográfica de la bitácora
        capturarSnapshotJavaFx(rootRef.get(), "subfase_c1_auditoria_filtrada.png");
    }

    private void capturarSnapshotJavaFx(Parent root, String filename) {
        if (root == null) return;
        try {
            AtomicReference<BufferedImage> imgRef = new AtomicReference<>();
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                WritableImage fxImg = root.snapshot(new SnapshotParameters(), null);
                int w = (int) fxImg.getWidth();
                int h = (int) fxImg.getHeight();
                BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                PixelReader pr = fxImg.getPixelReader();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        buf.setRGB(x, y, pr.getArgb(x, y));
                    }
                }
                imgRef.set(buf);
            });

            File targetFile = new File("target/playwright-report/" + filename);
            targetFile.getParentFile().mkdirs();
            ImageIO.write(imgRef.get(), "png", targetFile);

            // Publicar también en directorio de artefactos si existe
            File brainDir = new File(ARTIFACTS_DIR);
            if (brainDir.exists() && brainDir.isDirectory()) {
                File artifactFile = new File(brainDir, filename);
                Files.copy(targetFile.toPath(), artifactFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("Aviso al capturar snapshot JavaFX: " + e.getMessage());
        }
    }
}
