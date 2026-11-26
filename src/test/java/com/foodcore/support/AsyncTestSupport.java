package com.foodcore.support;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Utilidades determinísticas de espera para pruebas sin usar {@code Thread.sleep}.
 */
public final class AsyncTestSupport {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(20);

    private AsyncTestSupport() {
    }

    /**
     * Espera hasta que {@code condition} retorne {@code true} dentro de {@code timeout} usando intervalo por defecto.
     */
    public static void awaitUntil(BooleanSupplier condition, Duration timeout, String failureMessage) {
        awaitUntil(condition, timeout, DEFAULT_POLL_INTERVAL, failureMessage);
    }

    /**
     * Espera hasta que {@code condition} retorne {@code true} dentro de {@code timeout}.
     */
    public static void awaitUntil(
            BooleanSupplier condition,
            Duration timeout,
            Duration pollInterval,
            String failureMessage) {
        if (!awaitUntilQuiet(condition, timeout, pollInterval)) {
            throw new AssertionError(failureMessage != null ? failureMessage : "Timeout esperando condición");
        }
    }

    /**
     * Espera hasta que {@code condition} retorne {@code true} o expire {@code timeout}, retornando el resultado final.
     */
    public static boolean awaitUntilQuiet(BooleanSupplier condition, Duration timeout, Duration pollInterval) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(pollInterval, "pollInterval");

        long timeoutNanos = Math.max(1L, timeout.toNanos());
        long pollNanos = Math.max(TimeUnit.MILLISECONDS.toNanos(1L), pollInterval.toNanos());
        long deadline = System.nanoTime() + timeoutNanos;

        while (true) {
            if (condition.getAsBoolean()) {
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                return condition.getAsBoolean();
            }
            LockSupport.parkNanos(Math.min(pollNanos, remaining));
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError("Hilo interrumpido mientras esperaba condición en prueba");
            }
        }
    }

    /**
     * Pausa acotada basada en {@link LockSupport#parkNanos(long)} para ventanas reales de protocolo externo.
     */
    public static void pauseBounded(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long totalNanos = duration.toNanos();
        if (totalNanos <= 0L) {
            return;
        }
        long deadline = System.nanoTime() + totalNanos;
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                return;
            }
            LockSupport.parkNanos(remaining);
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Espera acotada interrumpida");
            }
        }
    }
}
