package com.foodcore.api;

import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración MySQL (Failsafe *IT) para verificar la Regla de Oro (SPEC v4.6.0 §2.2):
 * cada petición HTTP GET a /api/public/menu-digital consulta MySQL en vivo y refleja de inmediato
 * cambios de precio y productos agotados sin depender de archivos estáticos ni caché.
 */
class MenuDigitalDinamicoIT {

    private static final String CODIGO_VIVO = "F2-QR-VIVO";
    private static final String CODIGO_AGOTABLE = "F2-QR-AGOT";
    private static final byte[] TEST_SECRET =
            "FoodCore_Fase2_IT_Secret_Key_32_Bytes_Minimum!!".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void inicializarBd() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
    }

    @AfterEach
    void limpiarDatosPrueba() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM producto WHERE codigo IN (?, ?)")) {
            ps.setString(1, CODIGO_VIVO);
            ps.setString(2, CODIGO_AGOTABLE);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("GET /api/public/menu-digital refleja al instante cambios de precio y stock-out desde MySQL sin caché")
    void testMenuDigitalReflejaCambiosPrecioYAgotadoEnVivo() throws Exception {
        Path cacheEstatico = Path.of("uploads", "qr", "menu_digital.html");
        Files.deleteIfExists(cacheEstatico);

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO categoria (id_categoria, nombre, descripcion, activo) VALUES "
                    + "(1, 'Cat Hamburguesas', 'Platos principales', 1) "
                    + "ON DUPLICATE KEY UPDATE activo = 1");

            try (PreparedStatement del = conn.prepareStatement("DELETE FROM producto WHERE codigo IN (?, ?)")) {
                del.setString(1, CODIGO_VIVO);
                del.setString(2, CODIGO_AGOTABLE);
                del.executeUpdate();
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible) "
                            + "VALUES (1, ?, ?, ?, ?, ?, 1)")) {
                ins.setString(1, CODIGO_VIVO);
                ins.setString(2, "Ceviche Vivo Fase2");
                ins.setString(3, "Pesca del día en vivo");
                ins.setBigDecimal(4, new BigDecimal("24.50"));
                ins.setBigDecimal(5, new BigDecimal("10.00"));
                ins.executeUpdate();

                ins.setString(1, CODIGO_AGOTABLE);
                ins.setString(2, "Causa Agotable Fase2");
                ins.setString(3, "Edición limitada");
                ins.setBigDecimal(4, new BigDecimal("15.00"));
                ins.setBigDecimal(5, new BigDecimal("6.00"));
                ins.executeUpdate();
            }
        }

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        ApiServer server = ApiServer.crearServidorAislado(
                new JwtService(TEST_SECRET),
                CorsPolicy.desdeCadena("https://localhost:8443"),
                new AuthRateLimiter()
        );
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            URI menuPublicoUri = URI.create("http://127.0.0.1:" + port + "/api/public/menu-digital");

            // 1. Primera lectura: ambos productos y precio inicial S/ 24.50
            HttpResponse<String> resp1 = client.send(
                    HttpRequest.newBuilder(menuPublicoUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, resp1.statusCode());
            assertTrue(resp1.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            String html1 = resp1.body();
            assertTrue(html1.contains("Ceviche Vivo Fase2"));
            assertTrue(Pattern.compile("24[.,]50").matcher(html1).find(),
                    "La primera carga debe mostrar el precio inicial 24.50");
            assertTrue(html1.contains("Causa Agotable Fase2"));
            assertFalse(Files.exists(cacheEstatico));

            // 2. Actualizar precio y marcar un plato agotado en MySQL sin reiniciar servidor
            try (Connection conn = ConexionBD.getConnection()) {
                try (PreparedStatement updPrecio = conn.prepareStatement(
                        "UPDATE producto SET precio_venta = ? WHERE codigo = ?")) {
                    updPrecio.setBigDecimal(1, new BigDecimal("38.90"));
                    updPrecio.setString(2, CODIGO_VIVO);
                    updPrecio.executeUpdate();
                }
                try (PreparedStatement updAgot = conn.prepareStatement(
                        "UPDATE producto SET disponible = 0 WHERE codigo = ?")) {
                    updAgot.setString(1, CODIGO_AGOTABLE);
                    updAgot.executeUpdate();
                }
            }

            // 3. Segunda lectura inmediata: refleja 38.90 y omite plato agotado
            HttpResponse<String> resp2 = client.send(
                    HttpRequest.newBuilder(menuPublicoUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, resp2.statusCode());
            assertTrue(resp2.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            String html2 = resp2.body();
            assertTrue(html2.contains("Ceviche Vivo Fase2"));
            assertTrue(Pattern.compile("38[.,]90").matcher(html2).find(),
                    "La recarga inmediata debe mostrar el nuevo precio 38.90 desde BD");
            assertFalse(Pattern.compile("24[.,]50").matcher(html2).find(),
                    "El HTML dinámico no debe servir el precio anterior cacheado");
            assertFalse(html2.contains("Causa Agotable Fase2"),
                    "El plato agotado (disponible=0) debe desaparecer al instante al recargar");
            assertFalse(Files.exists(cacheEstatico));

            // 4. La ruta administrativa /api/menu-digital/view sigue protegida con JWT
            HttpResponse<String> respPrivada = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/menu-digital/view"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(401, respPrivada.statusCode());
        } finally {
            server.detener();
        }
    }
}
