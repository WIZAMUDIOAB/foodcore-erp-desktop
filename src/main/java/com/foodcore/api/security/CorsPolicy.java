package com.foodcore.api.security;

import io.javalin.http.Context;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Política CORS estricta por lista blanca explícita de orígenes permitidos.
 * Nunca emite wildcard {@code *}; si no hay orígenes configurados, deniega todo CORS.
 */
public final class CorsPolicy {

    private static final Logger LOGGER = Logger.getLogger(CorsPolicy.class.getName());
    private static final String ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Content-Type, Authorization, X-Idempotency-Key";

    private final Set<String> allowedOrigins;

    public CorsPolicy(Set<String> allowedOrigins) {
        this.allowedOrigins = Collections.unmodifiableSet(new LinkedHashSet<>(allowedOrigins));
    }

    public static CorsPolicy cargarDesdeConfiguracion() {
        String raw = leerOrigenesConfigurados();
        return desdeCadena(raw);
    }

    public static CorsPolicy desdeCadena(String rawOrigins) {
        Set<String> set = new LinkedHashSet<>();
        if (rawOrigins == null || rawOrigins.isBlank()) {
            return new CorsPolicy(set);
        }
        String[] parts = rawOrigins.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("*")) {
                throw new IllegalStateException(
                    "Configuración CORS insegura: wildcard '*' prohibido en cors.allowed.origins (" + trimmed + ")."
                );
            }
            set.add(normalizarOrigen(trimmed));
        }
        return new CorsPolicy(set);
    }

    private static String leerOrigenesConfigurados() {
        String sysProp = System.getProperty("FOODCORE_CORS_ALLOWED_ORIGINS");
        if (sysProp != null) {
            return sysProp;
        }
        String envVal = System.getenv("FOODCORE_CORS_ALLOWED_ORIGINS");
        if (envVal != null) {
            return envVal;
        }
        Properties props = new Properties();
        cargarSiExiste(props, "/application.properties");
        cargarSiExiste(props, "/application-test.properties");
        return props.getProperty("cors.allowed.origins", "");
    }

    private static void cargarSiExiste(Properties target, String path) {
        try (InputStream is = CorsPolicy.class.getResourceAsStream(path)) {
            if (is != null) {
                target.load(is);
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isBlank() || allowedOrigins.isEmpty()) {
            return false;
        }
        return allowedOrigins.contains(normalizarOrigen(origin));
    }

    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Aplica encabezados CORS únicamente cuando {@code Origin} está en la lista blanca.
     *
     * @return {@code true} si la solicitud puede continuar, {@code false} si fue rechazada (preflight inválido).
     */
    public boolean apply(Context ctx) {
        ctx.header("Vary", "Origin");
        String origin = ctx.header("Origin");
        boolean isOptions = "OPTIONS".equalsIgnoreCase(ctx.method().name());

        if (origin == null || origin.isBlank()) {
            if (isOptions) {
                ctx.status(204);
                ctx.skipRemainingHandlers();
                return false;
            }
            return true;
        }

        if (isOriginAllowed(origin)) {
            ctx.header("Access-Control-Allow-Origin", origin.trim());
            ctx.header("Access-Control-Allow-Methods", ALLOWED_METHODS);
            ctx.header("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            if (isOptions) {
                ctx.status(204);
                ctx.skipRemainingHandlers();
                return false;
            }
            return true;
        }

        LOGGER.warning(() -> String.format(
            "CORS bloqueado desde origen no autorizado '%s' (IP=%s, %s %s)",
            origin, ctx.req().getRemoteAddr(), ctx.method(), ctx.path()
        ));
        if (isOptions) {
            ctx.status(403)
                .contentType("application/json; charset=UTF-8")
                .result("{\"status\":\"ERROR\",\"message\":\"Origen CORS no autorizado.\"}");
            ctx.skipRemainingHandlers();
            return false;
        }
        return true;
    }

    private static String normalizarOrigen(String origin) {
        String limpio = origin.trim();
        while (limpio.endsWith("/")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        return limpio.toLowerCase(Locale.ROOT);
    }
}
