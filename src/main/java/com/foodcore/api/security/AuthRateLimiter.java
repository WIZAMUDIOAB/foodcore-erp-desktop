package com.foodcore.api.security;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limitador de tasa por ventana deslizante por IP para endpoints de autenticación.
 */
public final class AuthRateLimiter {

    public static final int DEFAULT_MAX_REQUESTS = 10;
    public static final long DEFAULT_WINDOW_MILLIS = 60_000L;

    private final int maxRequests;
    private final long windowMillis;
    private final LongSupplier clockMillis;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        this(DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW_MILLIS, System::currentTimeMillis);
    }

    public static AuthRateLimiter cargarDesdeConfiguracion() {
        int limite = DEFAULT_MAX_REQUESTS;
        Properties props = new Properties();
        cargarSiExiste(props, "/application.properties");
        cargarSiExiste(props, "/application-test.properties");
        String configured = props.getProperty("security.auth.rateLimit.max");
        if (configured != null && !configured.isBlank()) {
            try {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed > 0) {
                    limite = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new AuthRateLimiter(limite, DEFAULT_WINDOW_MILLIS, System::currentTimeMillis);
    }

    private static void cargarSiExiste(Properties target, String path) {
        try (InputStream is = AuthRateLimiter.class.getResourceAsStream(path)) {
            if (is != null) {
                target.load(is);
            }
        } catch (java.io.IOException ignored) {
        }
    }

    public AuthRateLimiter(int maxRequests, long windowMillis, LongSupplier clockMillis) {
        if (maxRequests <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("maxRequests y windowMillis deben ser positivos.");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
        this.clockMillis = clockMillis == null ? System::currentTimeMillis : clockMillis;
    }

    public boolean tryConsume(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long now = clockMillis.getAsLong();
        long cutoff = now - windowMillis;

        Deque<Long> deque = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    public long getRetryAfterSeconds() {
        return Math.max(1L, windowMillis / 1000L);
    }

    public void reset() {
        buckets.clear();
    }
}
