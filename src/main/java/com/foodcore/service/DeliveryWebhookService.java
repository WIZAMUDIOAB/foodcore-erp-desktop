package com.foodcore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.JsonMapperProvider;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio receptor HTTP de webhooks de delivery (Rappi / PedidosYa) endurecido (v5.7.1):
 * bind exclusivo a loopback ({@code 127.0.0.1}), límite estricto de 64 KB durante lectura,
 * verificación HMAC-SHA256 ({@code FOODCORE_DELIVERY_WEBHOOK_SECRET}), transacción ACID con precios
 * autoritativos desde {@link ProductoDAO} y cierre limpio del {@link ExecutorService}.
 */
public class DeliveryWebhookService {

    private static final Logger LOGGER = Logger.getLogger(DeliveryWebhookService.class.getName());
    private static final ObjectMapper MAPPER = JsonMapperProvider.getMapper();
    public static final int MAX_BODY_BYTES = 65_536; // 64 KB
    public static final String ENV_WEBHOOK_SECRET = "FOODCORE_DELIVERY_WEBHOOK_SECRET";
    public static final String HEADER_SIGNATURE = "X-FoodCore-Signature";

    private final PedidoDAO pedidoDAO;
    private final ProductoDAO productoDAO;
    private HttpServer server;
    private ExecutorService executor;
    private int puertoActivo = 0;

    static final class PayloadTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;

        PayloadTooLargeException() {
            super("El cuerpo del webhook excede el límite máximo de 64 KB.");
        }
    }

    public DeliveryWebhookService() {
        this(new PedidoDAOImpl(), new ProductoDAOImpl());
    }

    public DeliveryWebhookService(PedidoDAO pedidoDAO) {
        this(pedidoDAO, new ProductoDAOImpl());
    }

    public DeliveryWebhookService(PedidoDAO pedidoDAO, ProductoDAO productoDAO) {
        this.pedidoDAO = pedidoDAO != null ? pedidoDAO : new PedidoDAOImpl();
        this.productoDAO = productoDAO != null ? productoDAO : new ProductoDAOImpl();
    }

    /**
     * Inicia un servidor REST HTTP ligado exclusivamente a {@code 127.0.0.1} para recibir webhooks de delivery.
     */
    public synchronized void iniciarServidor(int puerto) throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
        server.createContext("/api/v1/delivery/webhook", new WebhookHandler());
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "foodcore-delivery-webhook");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.start();
        this.puertoActivo = server.getAddress().getPort();
        LOGGER.log(Level.INFO, "Servidor Delivery Webhook activo en http://127.0.0.1:{0}/api/v1/delivery/webhook",
                this.puertoActivo);
    }

    public synchronized void detenerServidor() {
        if (server != null) {
            server.stop(0);
            server = null;
            this.puertoActivo = 0;
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        LOGGER.log(Level.INFO, "Servidor Delivery Webhook detenido.");
    }

    public int getPuertoActivo() {
        return puertoActivo;
    }

    public boolean isServidorActivo() {
        return server != null;
    }

    /**
     * Calcula la firma HMAC-SHA256 en hexadecimal minúsculas para un payload y secreto dados.
     */
    public static String calcularFirmaHmacSha256(String payload, String secret) {
        if (payload == null || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Payload y secreto HMAC son obligatorios.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256.", ex);
        }
    }

    /**
     * Verifica una firma HMAC-SHA256 en tiempo constante (acepta prefijo opcional {@code sha256=}).
     */
    public static boolean verificarFirmaHmacSha256(String payload, String firmaRecibida, String secret) {
        if (payload == null || firmaRecibida == null || firmaRecibida.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        String limpia = firmaRecibida.trim();
        if (limpia.regionMatches(true, 0, "sha256=", 0, 7)) {
            limpia = limpia.substring(7).trim();
        }
        String esperada = calcularFirmaHmacSha256(payload, secret);
        return MessageDigest.isEqual(
                esperada.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                limpia.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String resolverSecretoWebhook() {
        String sysProp = System.getProperty(ENV_WEBHOOK_SECRET);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String envVal = System.getenv(ENV_WEBHOOK_SECRET);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        return null;
    }

    /**
     * Parsea un payload JSON de delivery, valida ítems y precios autoritativos contra {@link ProductoDAO}
     * y persiste cabecera + detalles en una única transacción ACID MySQL.
     *
     * @throws IllegalArgumentException si el JSON, ítems o disponibilidad de catálogo son inválidos (HTTP 422).
     * @throws IllegalStateException    si la persistencia en base de datos falla (HTTP 503).
     */
    public Pedido procesarPayloadJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("El payload JSON del webhook no puede estar vacío.");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON malformado en webhook de delivery.", ex);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("JSON malformado en webhook de delivery.");
        }

        String plataforma = root.hasNonNull("plataforma")
                ? root.get("plataforma").asText().trim() : "RAPPI";
        String ordenId = root.hasNonNull("orden_id")
                ? root.get("orden_id").asText().trim() : "ORD-" + System.currentTimeMillis();

        JsonNode clienteNode = root.get("cliente");
        JsonNode clienteObj = clienteNode != null && clienteNode.isObject()
                ? clienteNode : MAPPER.createObjectNode();
        String nombreCliente = clienteObj.hasNonNull("nombre")
                ? clienteObj.get("nombre").asText().trim() : "Cliente Delivery";
        String telefonoCliente = clienteObj.hasNonNull("telefono")
                ? clienteObj.get("telefono").asText().trim() : "";
        String direccionCliente = clienteObj.hasNonNull("direccion")
                ? clienteObj.get("direccion").asText().trim() : "Entrega Delivery";

        JsonNode itemsNode = root.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            throw new IllegalArgumentException("El pedido de delivery debe contener al menos un ítem.");
        }
        ArrayNode itemsArray = (ArrayNode) itemsNode;

        Pedido pedido = new Pedido();
        pedido.setTipoPedido("DELIVERY");
        pedido.setNumeroMesa(plataforma.toUpperCase(Locale.ROOT));
        pedido.setClienteNombre(nombreCliente);
        pedido.setClienteTelefono(telefonoCliente);
        pedido.setClienteDireccion(direccionCliente);
        pedido.setEstado("EN_PREPARACION");
        pedido.setIdUsuario(1);
        pedido.setIdempotencyKey(ordenId);

        Connection conn = null;
        boolean autoCommitOriginal = true;
        try {
            conn = ConexionBD.getConnection();
            if (conn == null) {
                throw new IllegalStateException("Base de datos no disponible para persistir pedido de delivery.");
            }
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            Optional<Pedido> existente = pedidoDAO.buscarPorIdempotencyKey(conn, ordenId);
            if (existente.isPresent()) {
                Pedido prev = existente.get();
                prev.setDetalles(pedidoDAO.listarDetallesPorPedido(conn, prev.getIdPedido()));
                conn.commit();
                return prev;
            }

            Map<Integer, Producto> catalogoPorId = new HashMap<>();
            for (Producto p : productoDAO.listarTodos(conn)) {
                catalogoPorId.put(p.getIdProducto(), p);
            }

            List<DetallePedido> detalles = new ArrayList<>();
            BigDecimal totalAutoritativo = BigDecimal.ZERO;

            for (JsonNode el : itemsArray) {
                if (!el.isObject()) {
                    throw new IllegalArgumentException("Cada ítem del webhook debe ser un objeto JSON.");
                }
                JsonNode item = el;
                int idProd = item.hasNonNull("id_producto")
                        ? item.get("id_producto").asInt() : 0;
                int cantidad = item.hasNonNull("cantidad")
                        ? item.get("cantidad").asInt() : 0;
                if (idProd <= 0 || cantidad <= 0) {
                    throw new IllegalArgumentException("Cada ítem requiere id_producto > 0 y cantidad > 0.");
                }
                Producto prodAutoritativo = catalogoPorId.get(idProd);
                if (prodAutoritativo == null) {
                    throw new IllegalArgumentException("Producto no encontrado en catálogo: #" + idProd);
                }
                if (!prodAutoritativo.isDisponible()) {
                    throw new IllegalArgumentException("Producto no disponible: " + prodAutoritativo.getNombre());
                }
                BigDecimal precioAutoritativo = prodAutoritativo.getPrecioVenta();
                if (precioAutoritativo == null || precioAutoritativo.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Precio de catálogo inválido para producto #" + idProd);
                }

                BigDecimal precioUnit = precioAutoritativo.setScale(2, RoundingMode.HALF_UP);
                BigDecimal subtotal = precioUnit.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
                String notas = item.hasNonNull("notas")
                        ? item.get("notas").asText() : "";

                DetallePedido d = new DetallePedido(0, 0, idProd, prodAutoritativo.getNombre(),
                        cantidad, precioUnit, subtotal, notas);
                d.setEstadoCocina("EN_PREPARACION");
                detalles.add(d);
                totalAutoritativo = totalAutoritativo.add(subtotal);
            }

            pedido.setTotal(totalAutoritativo.setScale(2, RoundingMode.HALF_UP));
            pedido.setDetalles(detalles);

            int id = pedidoDAO.crearPedido(conn, pedido);
            pedido.setIdPedido(id);
            for (DetallePedido d : detalles) {
                d.setIdPedido(id);
                int idDet = pedidoDAO.insertarDetallePedido(conn, d);
                d.setIdDetallePedido(idDet);
            }

            conn.commit();
            return pedido;
        } catch (IllegalArgumentException exVal) {
            hacerRollbackSeguro(conn);
            throw exVal;
        } catch (SQLException | RuntimeException exDb) {
            hacerRollbackSeguro(conn);
            LOGGER.log(Level.WARNING, "Error persistiendo pedido delivery en transacción BD", exDb);
            throw new IllegalStateException("No se pudo persistir el pedido de delivery en base de datos.", exDb);
        } finally {
            cerrarConexionRestaurandoAutoCommit(conn, autoCommitOriginal);
        }
    }

    private static void hacerRollbackSeguro(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException exRb) {
            LOGGER.log(Level.FINE, "Error en rollback de DeliveryWebhookService", exRb);
        }
    }

    private static void cerrarConexionRestaurandoAutoCommit(Connection conn, boolean autoCommitOriginal) {
        if (conn == null) {
            return;
        }
        try {
            conn.setAutoCommit(autoCommitOriginal);
        } catch (SQLException ignored) {
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    static byte[] leerCuerpoAcotado(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(4096, maxBytes));
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = is.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new PayloadTooLargeException();
            }
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    /**
     * Genera un payload mock estándar simulando un pedido de Rappi.
     */
    public static String generarPayloadMockRappi() {
        return """
        {
          "plataforma": "RAPPI",
          "orden_id": "RAPPI-84920",
          "cliente": {
            "nombre": "Esteban Morales",
            "telefono": "981234567",
            "direccion": "Av. Las Palmeras 450, San Isidro"
          },
          "items": [
            {
              "id_producto": 1,
              "nombre": "Hamburguesa Clasica",
              "cantidad": 2,
              "precio_unitario": 18.50,
              "notas": "Sin cebolla, papas bien doradas"
            },
            {
              "id_producto": 3,
              "nombre": "Gaseosa Personal",
              "cantidad": 2,
              "precio_unitario": 5.00,
              "notas": "Bien heladas"
            }
          ],
          "total": 47.00
        }
        """;
    }

    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String secret = resolverSecretoWebhook();
                if (secret == null || secret.isBlank()) {
                    LOGGER.warning("Webhook de delivery rechazado: " + ENV_WEBHOOK_SECRET + " no configurado.");
                    enviarJsonError(exchange, 503, "Servicio de webhook no configurado.");
                    return;
                }

                byte[] bodyBytes = leerCuerpoAcotado(is, MAX_BODY_BYTES);
                String body = new String(bodyBytes, StandardCharsets.UTF_8);

                String firma = extraerCabeceraFirma(exchange.getRequestHeaders());
                if (!verificarFirmaHmacSha256(body, firma, secret)) {
                    LOGGER.warning("Firma HMAC-SHA256 inválida o ausente en webhook de delivery.");
                    enviarJsonError(exchange, 401, "Firma HMAC-SHA256 inválida o ausente.");
                    return;
                }

                Pedido pedidoProcesado = procesarPayloadJson(body);

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("status", "SUCCESS");
                resp.put("mensaje", "Orden " + pedidoProcesado.getNumeroMesa() + " ingresada a cocina");
                resp.put("id_pedido", pedidoProcesado.getIdPedido());
                resp.put("total", pedidoProcesado.getTotal());

                enviarJson(exchange, 200, resp);
            } catch (PayloadTooLargeException ptle) {
                enviarJsonError(exchange, 413, "El cuerpo excede el límite máximo de 64 KB.");
            } catch (IllegalArgumentException iae) {
                enviarJsonError(exchange, 422, iae.getMessage() != null ? iae.getMessage() : "Payload de delivery inválido.");
            } catch (IllegalStateException ise) {
                LOGGER.log(Level.WARNING, "Error de persistencia en webhook de delivery", ise);
                enviarJsonError(exchange, 503, "No se pudo persistir el pedido de delivery.");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error inesperado en webhook de delivery", e);
                enviarJsonError(exchange, 503, "Error temporal procesando webhook de delivery.");
            } finally {
                exchange.close();
            }
        }

        private String extraerCabeceraFirma(Headers headers) {
            if (headers == null) {
                return null;
            }
            for (String nombre : List.of(HEADER_SIGNATURE, "X-Webhook-Signature", "X-Hub-Signature-256")) {
                String val = headers.getFirst(nombre);
                if (val != null && !val.isBlank()) {
                    return val.trim();
                }
            }
            return null;
        }

        private void enviarJsonError(HttpExchange exchange, int status, String mensaje) throws IOException {
            ObjectNode err = MAPPER.createObjectNode();
            err.put("status", "ERROR");
            err.put("error", mensaje);
            enviarJson(exchange, status, err);
        }

        private void enviarJson(HttpExchange exchange, int status, ObjectNode payload) throws IOException {
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
