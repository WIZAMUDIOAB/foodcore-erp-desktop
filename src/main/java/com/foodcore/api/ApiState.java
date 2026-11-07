package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.model.Producto;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado compartido y utilidades de serialización para los controladores REST de Javalin (v4.3.0 §1.3).
 */
public class ApiState {

    public final ObjectMapper mapper = new ObjectMapper();
    public final Map<Integer, String> pinsMemoria = new ConcurrentHashMap<>(Map.of(
            1, "9999",
            2, "2001",
            3, "1234"
    ));
    public final AtomicInteger secuenciaComprobantes = new AtomicInteger(100);
    public final AtomicBoolean cajaTurnoActivoMemoria = new AtomicBoolean(false);
    public volatile String cajaNombreMemoria = "Caja Principal";
    public volatile BigDecimal cajaMontoAperturaMemoria = new BigDecimal("100.00");
    public volatile BigDecimal cajaVentasMemoria = BigDecimal.ZERO;
    public volatile BigDecimal ventasAcumuladasHoyMemoria = new BigDecimal("450.00");
    public final AtomicInteger transaccionesHoyMemoria = new AtomicInteger(6);
    public final List<ObjectNode> ventasRecientesMemoria = new CopyOnWriteArrayList<>();
    public final Map<String, ObjectNode> cacheIdempotenciaPedidos = new ConcurrentHashMap<>();
    public final Map<String, String> estadosMesasMemoria = new ConcurrentHashMap<>();
    public final Map<String, Double> totalesMesasMemoria = new ConcurrentHashMap<>();
    public final List<Producto> catalogoRespaldoCache = new CopyOnWriteArrayList<>();
    public final AtomicInteger secuenciaPedidosFallback = new AtomicInteger(1000);

    public void responderError(Context ctx, int status, String mensaje) {
        ObjectNode err = mapper.createObjectNode();
        err.put("status", "ERROR");
        err.put("message", mensaje);
        ctx.status(status).contentType("application/json; charset=UTF-8").result(err.toString());
    }

    public String extraerTexto(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String k : keys) {
            if (node.hasNonNull(k)) {
                String val = node.get(k).asText();
                if (val != null && !val.isBlank()) {
                    return val.trim();
                }
            }
        }
        return null;
    }
}
