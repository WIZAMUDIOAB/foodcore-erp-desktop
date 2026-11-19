package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.model.Producto;
import io.javalin.http.Context;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado compartido y utilidades de serialización para los controladores REST de Javalin (v5.7.1 Hardening).
 */
public class ApiState {

    public static final String PROP_DEMO_FALLBACK = "foodcore.demoFallback";

    public final ObjectMapper mapper = new ObjectMapper();
    public final AtomicInteger secuenciaComprobantes = new AtomicInteger(100);
    public final List<Producto> catalogoRespaldoCache = new CopyOnWriteArrayList<>();

    /**
     * Indica si los datos demo en memoria están explícitamente habilitados vía {@code -Dfoodcore.demoFallback=true}.
     * Por defecto es {@code false} para evitar KPIs o comandas ficticias en producción.
     */
    public static boolean isDemoFallbackEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(PROP_DEMO_FALLBACK));
    }

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
