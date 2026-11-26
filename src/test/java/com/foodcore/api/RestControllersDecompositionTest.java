package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.api.security.TokenBlacklistService;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Venta;
import com.foodcore.service.api.ApiOperacionException;
import com.foodcore.service.api.ApiOperacionesPort;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de enrutamiento y contratos JSON para {@link SalonApiController},
 * {@link KdsSyncApiController}, {@link CobroApiController}, fachada {@link PedidoController}
 * y servidor aislado {@link ApiServer}.
 */
class RestControllersDecompositionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] TEST_SECRET =
            "FoodCore_PR07_RestDecomposition_Test_Secret_32B!".getBytes(StandardCharsets.UTF_8);

    @Test
    void salonKdsSyncYCobroPreservanRutasContratosYValidaciones() throws Exception {
        StubOperacionesPort stub = new StubOperacionesPort();
        ApiState state = new ApiState();
        JwtService jwtService = new JwtService(TEST_SECRET);
        ApiSecurityFilter securityFilter = new ApiSecurityFilter(
                state, jwtService, new AuthRateLimiter(), TokenBlacklistService.crearAisladoEnMemoria());

        SalonApiController salonCtrl = new SalonApiController(state, stub);
        KdsSyncApiController kdsSyncCtrl = new KdsSyncApiController(state, stub);
        CobroApiController cobroCtrl = new CobroApiController(state, stub);

        Javalin app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.before("/api/*", securityFilter::handle);
            config.routes.get("/api/public/productos", salonCtrl::handleListarProductosPublicos);
            config.routes.get("/api/productos", salonCtrl::handleListarProductos);
            config.routes.patch("/api/productos/{id}/disponibilidad", salonCtrl::handleActualizarDisponibilidad);
            config.routes.get("/api/mesas", salonCtrl::handleListarMesas);
            config.routes.get("/uploads/platillos/{archivo}", salonCtrl::handleServirUploadPlatillo);

            config.routes.post("/api/pedidos", kdsSyncCtrl::handleCrearPedido);
            config.routes.get("/api/pedidos", kdsSyncCtrl::handleListarPedidos);
            config.routes.get("/api/pedidos/{uuid}", kdsSyncCtrl::handleObtenerPedidoPorUuid);
            config.routes.patch("/api/pedidos/{id}/estado", kdsSyncCtrl::handleActualizarEstadoPedido);

            config.routes.get("/api/caja/estado", cobroCtrl::handleCajaEstado);
            config.routes.post("/api/caja/abrir", cobroCtrl::handleCajaAbrir);
            config.routes.post("/api/caja/cerrar", cobroCtrl::handleCajaCerrar);
            config.routes.post("/api/caja/cobrar", cobroCtrl::handleCajaCobrar);
        });

        app.start(0);
        try {
            int port = app.port();
            HttpClient client = HttpClient.newHttpClient();
            String tokenMozo = jwtService.generarToken(5, "mozo1", "MOZO");
            String tokenCajero = jwtService.generarToken(2, "cajero1", "CAJERO");
            String tokenAdmin = jwtService.generarToken(1, "admin1", "ADMIN");
            String tokenCocina = jwtService.generarToken(3, "cocina1", "COCINA");

            HttpResponse<String> pubResp = get(client, port, "/api/public/productos", null);
            assertEquals(200, pubResp.statusCode());
            JsonNode pubArr = MAPPER.readTree(pubResp.body());
            assertEquals(1, pubArr.size());
            assertFalse(pubArr.get(0).has("codigo"));
            assertTrue(pubArr.get(0).path("disponible").asBoolean());

            HttpResponse<String> prodResp = get(client, port, "/api/productos", tokenMozo);
            assertEquals(200, prodResp.statusCode());
            assertEquals("HAM-01", MAPPER.readTree(prodResp.body()).get(0).path("codigo").asText());

            HttpResponse<String> dispBad = sendJson(client, port, "PATCH",
                    "/api/productos/0/disponibilidad", "{}", tokenAdmin);
            assertEquals(400, dispBad.statusCode());

            HttpResponse<String> dispOk = sendJson(client, port, "PATCH",
                    "/api/productos/10/disponibilidad", "{\"agotado\":true}", tokenAdmin);
            assertEquals(200, dispOk.statusCode());
            assertFalse(MAPPER.readTree(dispOk.body()).path("disponible").asBoolean());

            HttpResponse<String> lfiSvg = get(client, port, "/uploads/platillos/xss.svg", tokenMozo);
            assertEquals(403, lfiSvg.statusCode());

            HttpResponse<String> mesasResp = get(client, port, "/api/mesas", tokenMozo);
            assertEquals(200, mesasResp.statusCode());
            JsonNode mesas = MAPPER.readTree(mesasResp.body());
            assertEquals("04", mesas.get(0).path("numero").asText());
            assertEquals("OCUPADA", mesas.get(0).path("estado").asText());
            assertEquals(49.0, mesas.get(0).path("total_actual").asDouble(), 0.01);

            String pedidoPayload = "{\"uuid_local\":\"uuid-pr07-1\",\"numero_mesa\":\"4\","
                    + "\"items\":[{\"id_producto\":10,\"cantidad\":2,\"notas\":\"Sin cebolla\"}]}";
            HttpResponse<String> crearResp = sendJson(client, port, "POST",
                    "/api/pedidos", pedidoPayload, tokenMozo);
            assertEquals(201, crearResp.statusCode());
            JsonNode pedJson = MAPPER.readTree(crearResp.body());
            assertEquals(101, pedJson.path("id_pedido").asInt());
            assertEquals("04", pedJson.path("numero_mesa").asText());
            assertFalse(pedJson.path("idempotente").asBoolean());

            stub.siguienteIdempotente.set(true);
            HttpResponse<String> idemResp = sendJson(client, port, "POST",
                    "/api/pedidos", pedidoPayload, tokenMozo);
            assertEquals(200, idemResp.statusCode());
            assertTrue(MAPPER.readTree(idemResp.body()).path("idempotente").asBoolean());

            HttpResponse<String> listarPed = get(client, port, "/api/pedidos", tokenMozo);
            assertEquals(200, listarPed.statusCode());
            assertEquals(1, MAPPER.readTree(listarPed.body()).size());

            assertEquals(404, get(client, port, "/api/pedidos/no-existe", tokenMozo).statusCode());
            assertEquals(200, get(client, port, "/api/pedidos/uuid-pr07-1", tokenMozo).statusCode());

            assertEquals(400, sendJson(client, port, "PATCH",
                    "/api/pedidos/101/estado", "{\"estado\":\"INVALIDO\"}", tokenCocina).statusCode());
            HttpResponse<String> patchOk = sendJson(client, port, "PATCH",
                    "/api/pedidos/101/estado", "{\"estado\":\"LISTO\"}", tokenCocina);
            assertEquals(200, patchOk.statusCode());
            assertEquals("LISTO", MAPPER.readTree(patchOk.body()).path("nuevo_estado").asText());

            HttpResponse<String> cajaEst = get(client, port, "/api/caja/estado", tokenCajero);
            assertEquals(200, cajaEst.statusCode());
            assertTrue(MAPPER.readTree(cajaEst.body()).path("abierta").asBoolean());

            assertEquals(400, sendJson(client, port, "POST",
                    "/api/caja/abrir", "{\"monto_inicial\":-1}", tokenCajero).statusCode());
            HttpResponse<String> abrirOk = sendJson(client, port, "POST",
                    "/api/caja/abrir", "{\"nombre_caja\":\"Caja 1\",\"monto_inicial\":100}", tokenCajero);
            assertEquals(200, abrirOk.statusCode());
            assertEquals(7, MAPPER.readTree(abrirOk.body()).path("id_caja").asInt());

            HttpResponse<String> cerrarOk = sendJson(client, port, "POST",
                    "/api/caja/cerrar", "{\"efectivo_fisico_contado\":250}", tokenCajero);
            assertEquals(200, cerrarOk.statusCode());
            assertEquals(250.0, MAPPER.readTree(cerrarOk.body()).path("monto_contado").asDouble(), 0.01);

            assertEquals(400, sendJson(client, port, "POST",
                    "/api/caja/cobrar", "{\"tipo_comprobante\":\"OTRO\",\"id_pedido\":101}", tokenCajero).statusCode());
            HttpResponse<String> cobrarOk = sendJson(client, port, "POST", "/api/caja/cobrar",
                    "{\"id_pedido\":101,\"tipo_comprobante\":\"BOLETA\",\"medio_pago\":\"EFECTIVO\",\"monto_recibido\":50}",
                    tokenCajero);
            assertEquals(200, cobrarOk.statusCode());
            JsonNode cobrarJson = MAPPER.readTree(cobrarOk.body());
            assertEquals(888, cobrarJson.path("id_venta").asInt());
            assertEquals("B001-000042", cobrarJson.path("numero_comprobante").asText());
            assertEquals(1.0, cobrarJson.path("vuelto").asDouble(), 0.01);
        } finally {
            app.stop();
        }
    }

    @Test
    void fachadaPedidoControllerYApiServerAisladoMantienenCompatibilidad() throws Exception {
        ApiState state = new ApiState();
        PedidoController fachada = new PedidoController(
                state, new ProductoDAOImpl(), new MesaDAOImpl(), new PedidoDAOImpl(),
                new PedidoServiceImpl(), new CajaServiceImpl(), new VentaServiceImpl());
        assertTrue(PedidoController.esRutaImagenSospechosa("../evil.png"));
        assertTrue(PedidoController.resolverArchivoImagenConfinado(null, "a.png").isEmpty());
        assertTrue(fachada.salonController() != null);
        assertTrue(fachada.kdsSyncController() != null);
        assertTrue(fachada.cobroController() != null);

        ApiServer server = ApiServer.crearServidorAislado(
                new JwtService(TEST_SECRET), CorsPolicy.desdeCadena(""), new AuthRateLimiter());
        server.iniciar(0, -1);
        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.getPuertoActual();
            assertEquals(200, get(client, port, "/api/health", null).statusCode());
            assertEquals(200, get(client, port, "/api/public/productos", null).statusCode());
            assertEquals(401, get(client, port, "/api/mesas", null).statusCode());
            assertEquals(401, get(client, port, "/api/pedidos", null).statusCode());
            assertEquals(401, get(client, port, "/api/caja/estado", null).statusCode());
        } finally {
            server.detener();
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendJson(HttpClient client, int port, String method,
            String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class StubOperacionesPort implements ApiOperacionesPort {
        private final AtomicBoolean siguienteIdempotente = new AtomicBoolean(false);
        private final AtomicReference<Boolean> disponibleProducto = new AtomicReference<>(true);

        @Override
        public List<Producto> listarCatalogoProductos(boolean soloDisponibles) {
            Producto p = new Producto(10, 1, "HAM-01", "Royal PR07", "Desc",
                    new BigDecimal("24.50"), new BigDecimal("10.00"), Boolean.TRUE.equals(disponibleProducto.get()));
            return List.of(p);
        }

        @Override
        public void actualizarDisponibilidadProducto(int idProducto, boolean disponible) {
            disponibleProducto.set(disponible);
        }

        @Override
        public Optional<String> obtenerImagenUrlProducto(int idProducto) {
            return Optional.empty();
        }

        @Override
        public EstadoMesasSnapshot obtenerEstadoMesas() {
            return new EstadoMesasSnapshot(Map.of("04", "OCUPADA"), Map.of("04", new BigDecimal("49.00")));
        }

        @Override
        public ResultadoCreacionPedido crearPedidoOIdempotente(int idUsuario, String numeroMesa,
                String uuidLocal, List<ItemSolicitudPedido> items) {
            Pedido p = new Pedido(101, idUsuario, 4, numeroMesa, "REGISTRADO", new BigDecimal("49.00"));
            p.setIdempotencyKey(uuidLocal);
            DetallePedido d = new DetallePedido(201, 101, 10, "Royal PR07",
                    items.get(0).cantidad(), new BigDecimal("24.50"), new BigDecimal("49.00"), items.get(0).notas());
            return new ResultadoCreacionPedido(p, List.of(d), siguienteIdempotente.get());
        }

        @Override
        public PedidosActivosSnapshot listarPedidosActivosConDetalles() {
            Pedido p = new Pedido(101, 5, 4, "04", "REGISTRADO", new BigDecimal("49.00"));
            p.setIdempotencyKey("uuid-pr07-1");
            return new PedidosActivosSnapshot(List.of(p), Map.of(101, List.of()));
        }

        @Override
        public Optional<PedidoConDetalles> buscarPedidoPorUuid(String uuid) {
            if (!"uuid-pr07-1".equals(uuid)) {
                return Optional.empty();
            }
            Pedido p = new Pedido(101, 5, 4, "04", "REGISTRADO", new BigDecimal("49.00"));
            p.setIdempotencyKey(uuid);
            return Optional.of(new PedidoConDetalles(p, List.of()));
        }

        @Override
        public void actualizarEstadoPedido(int idPedido, String nuevoEstado) throws ApiOperacionException {
            if (idPedido != 101) {
                throw new ApiOperacionException(404, "No encontrado");
            }
        }

        @Override
        public EstadoCajaSnapshot consultarEstadoCaja() {
            Caja c = new Caja();
            c.setIdCaja(7);
            c.setNombreCaja("Caja 1");
            c.setIdUsuarioApertura(2);
            c.setMontoApertura(new BigDecimal("100.00"));
            c.setEstado("ABIERTA");
            return new EstadoCajaSnapshot(true, c, new BigDecimal("200.00"),
                    new BigDecimal("20.00"), new BigDecimal("280.00"));
        }

        @Override
        public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial) {
            return 7;
        }

        @Override
        public ResultadoCierreCaja cerrarTurnoCaja(int idUsuario, int idCajaSolicitado, BigDecimal contado) {
            return new ResultadoCierreCaja(7, new BigDecimal("250.00"), contado, BigDecimal.ZERO);
        }

        @Override
        public ResultadoCobroCaja cobrarEnCaja(SolicitudCobroCaja solicitud) {
            Venta v = new Venta(888, solicitud.idPedido(), 7, solicitud.idUsuario(), null,
                    new BigDecimal("41.53"), new BigDecimal("7.47"), new BigDecimal("49.00"),
                    solicitud.medioPago(), solicitud.montoRecibidoOpt(), new BigDecimal("1.00"));
            return new ResultadoCobroCaja(v, solicitud.idPedido(), "04", solicitud.tipoComprobante(),
                    "B001-000042", solicitud.medioPago(), new BigDecimal("50.00"), new BigDecimal("1.00"), true);
        }
    }
}
