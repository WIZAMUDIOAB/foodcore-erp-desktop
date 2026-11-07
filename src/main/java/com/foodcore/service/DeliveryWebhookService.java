package com.foodcore.service;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.util.ConexionBD;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeliveryWebhookService {

    private static final Logger LOGGER = Logger.getLogger(DeliveryWebhookService.class.getName());
    private static final Gson GSON = new Gson();

    private final PedidoDAO pedidoDAO;
    private HttpServer server;
    private int puertoActivo = 0;

    public DeliveryWebhookService() {
        this(new PedidoDAOImpl());
    }

    public DeliveryWebhookService(PedidoDAO pedidoDAO) {
        this.pedidoDAO = pedidoDAO;
    }

    /**
     * Inicia un servidor REST HTTP local para recibir webhooks de delivery.
     */
    public synchronized void iniciarServidor(int puerto) throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(puerto), 0);
        server.createContext("/api/v1/delivery/webhook", new WebhookHandler());
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        this.puertoActivo = puerto;
        LOGGER.log(Level.INFO, "Servidor Delivery Webhook activo en http://localhost:{0}/api/v1/delivery/webhook", puerto);
    }

    public synchronized void detenerServidor() {
        if (server != null) {
            server.stop(0);
            server = null;
            this.puertoActivo = 0;
            LOGGER.log(Level.INFO, "Servidor Delivery Webhook detenido.");
        }
    }

    public int getPuertoActivo() {
        return puertoActivo;
    }

    public boolean isServidorActivo() {
        return server != null;
    }

    /**
     * Parsea un payload JSON proveniente de Rappi o PedidosYa y lo convierte
     * en un Pedido interno estructurado con sus DetallePedido.
     */
    public Pedido procesarPayloadJson(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);

        String plataforma = root.has("plataforma") ? root.get("plataforma").getAsString() : "RAPPI";
        String ordenId = root.has("orden_id") ? root.get("orden_id").getAsString() : "ORD-" + System.currentTimeMillis();

        JsonObject clienteObj = root.has("cliente") ? root.getAsJsonObject("cliente") : new JsonObject();
        String nombreCliente = clienteObj.has("nombre") ? clienteObj.get("nombre").getAsString() : "Cliente Delivery";
        String telefonoCliente = clienteObj.has("telefono") ? clienteObj.get("telefono").getAsString() : "";
        String direccionCliente = clienteObj.has("direccion") ? clienteObj.get("direccion").getAsString() : "Entrega Delivery";

        BigDecimal total = root.has("total") ? root.get("total").getAsBigDecimal() : BigDecimal.ZERO;

        Pedido pedido = new Pedido();
        pedido.setTipoPedido("DELIVERY");
        pedido.setNumeroMesa(plataforma.toUpperCase());
        pedido.setClienteNombre(nombreCliente);
        pedido.setClienteTelefono(telefonoCliente);
        pedido.setClienteDireccion(direccionCliente);
        pedido.setEstado("EN_PREPARACION");
        pedido.setTotal(total);
        pedido.setIdUsuario(1); // Usuario Admin / Sistema por defecto
        pedido.setIdempotencyKey(ordenId);

        List<DetallePedido> detalles = new ArrayList<>();
        if (root.has("items")) {
            JsonArray itemsArray = root.getAsJsonArray("items");
            for (JsonElement el : itemsArray) {
                JsonObject item = el.getAsJsonObject();
                int idProd = item.has("id_producto") ? item.get("id_producto").getAsInt() : 1;
                String nomProd = item.has("nombre") ? item.get("nombre").getAsString() : "Item Delivery";
                int cantidad = item.has("cantidad") ? item.get("cantidad").getAsInt() : 1;
                BigDecimal precioUnit = item.has("precio_unitario") ? item.get("precio_unitario").getAsBigDecimal() : BigDecimal.ZERO;
                String notas = item.has("notas") ? item.get("notas").getAsString() : "";
                BigDecimal subtotal = precioUnit.multiply(BigDecimal.valueOf(cantidad));

                DetallePedido d = new DetallePedido(0, 0, idProd, nomProd, cantidad, precioUnit, subtotal, notas);
                d.setEstadoCocina("EN_PREPARACION");
                detalles.add(d);
            }
        }
        pedido.setDetalles(detalles);

        // Guardar en BD si es posible
        try (Connection conn = ConexionBD.getConnection()) {
            int id = pedidoDAO.crearPedido(conn, pedido);
            pedido.setIdPedido(id);
            for (DetallePedido d : detalles) {
                d.setIdPedido(id);
                try {
                    pedidoDAO.insertarDetallePedido(conn, d);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Detalle añadido en memoria: {0}", ex.getMessage());
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "No se pudo persistir pedido delivery en BD, manteniendo en memoria: {0}", e.getMessage());
            if (pedido.getIdPedido() == 0) {
                pedido.setIdPedido(9900 + java.util.concurrent.ThreadLocalRandom.current().nextInt(100));
            }
        }

        return pedido;
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
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Pedido pedidoProcesado = procesarPayloadJson(body);

                JsonObject resp = new JsonObject();
                resp.addProperty("status", "SUCCESS");
                resp.addProperty("mensaje", "Orden " + pedidoProcesado.getNumeroMesa() + " ingresada a cocina");
                resp.addProperty("id_pedido", pedidoProcesado.getIdPedido());
                resp.addProperty("total", pedidoProcesado.getTotal());

                byte[] bytes = resp.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error en webhook de delivery: " + e.getMessage(), e);
                JsonObject err = new JsonObject();
                err.addProperty("status", "ERROR");
                err.addProperty("error", e.getMessage());
                byte[] bytes = err.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(400, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }
}
