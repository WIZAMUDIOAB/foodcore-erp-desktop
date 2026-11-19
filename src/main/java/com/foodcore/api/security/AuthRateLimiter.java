package com.foodcore.api.security;

import com.foodcore.util.ConexionBD;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Limitador de tasa por ventana deslizante por IP para endpoints de autenticación,
 * con tope máximo de IPs rastreadas y depuración periódica de entradas vencidas.
 */
public final class AuthRateLimiter {

    public static final int DEFAULT_MAX_REQUESTS = 10;
    public static final long DEFAULT_WINDOW_MILLIS = 60_000L;
    public static final int MAX_TRACKED_IPS = 10_000;
    private static final int CLEANUP_EVERY_N_CALLS = 512;

    private final int maxRequests;
    private final long windowMillis;
    private final int maxTrackedIps;
    private final LongSupplier clockMillis;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger opsCounter = new AtomicInteger(0);

    public AuthRateLimiter() {
        this(DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW_MILLIS, System::currentTimeMillis, MAX_TRACKED_IPS);
    }

    public static AuthRateLimiter cargarDesdeConfiguracion() {
        int limite = DEFAULT_MAX_REQUESTS;
        Properties props = new Properties();
        cargarSiExiste(props, "/application.properties");
        if (ConexionBD.esPerfilPruebasActivo()) {
            cargarSiExiste(props, "/application-test.properties");
        }
        String configured = System.getProperty("security.auth.rateLimit.max");
        if (configured == null || configured.isBlank()) {
            configured = props.getProperty("security.auth.rateLimit.max");
        }
        if (configured != null && !configured.isBlank()) {
            try {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed > 0) {
                    limite = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new AuthRateLimiter(limite, DEFAULT_WINDOW_MILLIS, System::currentTimeMillis, MAX_TRACKED_IPS);
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
        this(maxRequests, windowMillis, clockMillis, MAX_TRACKED_IPS);
    }

    public AuthRateLimiter(int maxRequests, long windowMillis, LongSupplier clockMillis, int maxTrackedIps) {
        if (maxRequests <= 0 || windowMillis <= 0 || maxTrackedIps <= 0) {
            throw new IllegalArgumentException("maxRequests, windowMillis y maxTrackedIps deben ser positivos.");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
        this.maxTrackedIps = maxTrackedIps;
        this.clockMillis = clockMillis == null ? System::currentTimeMillis : clockMillis;
    }

    public boolean tryConsume(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long now = clockMillis.getAsLong();
        long cutoff = now - windowMillis;

        if ((opsCounter.incrementAndGet() & (CLEANUP_EVERY_N_CALLS - 1)) == 0 || buckets.size() >= maxTrackedIps) {
            evictExpiredBuckets(cutoff);
        }

        if (!buckets.containsKey(key) && buckets.size() >= maxTrackedIps) {
            return false;
        }

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

    public void evictExpired() {
        long cutoff = clockMillis.getAsLong() - windowMillis;
        evictExpiredBuckets(cutoff);
    }

    private void evictExpiredBuckets(long cutoff) {
        Iterator<Map.Entry<String, Deque<Long>>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                    deque.pollFirst();
                }
                if (deque.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    public int getTrackedIpCount() {
        return buckets.size();
    }

    public long getRetryAfterSeconds() {
        return Math.max(1L, windowMillis / 1000L);
    }

    public void reset() {
        buckets.clear();
    }
}
