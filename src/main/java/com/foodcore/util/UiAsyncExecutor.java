package com.foodcore.util;

import javafx.concurrent.Task;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ejecutor compartido basado en hilos daemon para tareas {@link Task} de JavaFX,
 * evitando bloqueos en el JavaFX Application Thread durante operaciones de BD o red
 * y ofreciendo cierre ordenado mediante {@link #shutdown()}.
 */
public final class UiAsyncExecutor {

    private static final Logger LOGGER = Logger.getLogger(UiAsyncExecutor.class.getName());
    private static final int WORKER_THREADS = Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));
    private static final long SHUTDOWN_WAIT_SECONDS = 2L;
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);
    private static ExecutorService executor = crearExecutor();

    private UiAsyncExecutor() {
    }

    private static ExecutorService crearExecutor() {
        return Executors.newFixedThreadPool(WORKER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "foodcore-ui-worker-" + THREAD_SEQ.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    private static synchronized ExecutorService obtenerExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = crearExecutor();
        }
        return executor;
    }

    /**
     * Detiene de forma ordenada y acotada el pool daemon de tareas UI (idempotente).
     */
    public static synchronized void shutdown() {
        if (executor == null) {
            return;
        }
        ExecutorService current = executor;
        executor = null;
        current.shutdown();
        try {
            if (!current.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException ex) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Indica si el pool daemon se encuentra detenido actualmente.
     */
    public static synchronized boolean isShutdown() {
        return executor == null || executor.isShutdown();
    }

    /**
     * Envía una {@link Task} de JavaFX al pool daemon de trabajadores en segundo plano.
     */
    public static <T> Task<T> ejecutar(Task<T> task) {
        Objects.requireNonNull(task, "La tarea asíncrona no puede ser nula.");
        obtenerExecutor().submit(task);
        return task;
    }

    /**
     * Crea y ejecuta una {@link Task} asíncrona devolviendo el resultado o error en el hilo UI de JavaFX.
     */
    public static <T> Task<T> ejecutarAsync(
            Callable<T> backgroundWork,
            Consumer<T> onSucceededFx,
            Consumer<Throwable> onFailedFx) {
        Objects.requireNonNull(backgroundWork, "El trabajo en segundo plano no puede ser nulo.");
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return backgroundWork.call();
            }
        };
        task.setOnSucceeded(ev -> {
            if (onSucceededFx != null) {
                onSucceededFx.accept(task.getValue());
            }
        });
        task.setOnFailed(ev -> {
            Throwable error = task.getException();
            LOGGER.log(Level.WARNING, "Fallo en tarea asíncrona de UI", error);
            if (onFailedFx != null) {
                onFailedFx.accept(error);
            }
        });
        obtenerExecutor().submit(task);
        return task;
    }

    /**
     * Ejecuta una acción sin retorno en segundo plano notificando finalización o error en el hilo UI.
     */
    public static Task<Void> ejecutarVoidAsync(
            ThrowingRunnable backgroundWork,
            Runnable onSucceededFx,
            Consumer<Throwable> onFailedFx) {
        Objects.requireNonNull(backgroundWork, "La acción en segundo plano no puede ser nula.");
        return ejecutarAsync(
            () -> {
                backgroundWork.run();
                return null;
            },
            ignored -> {
                if (onSucceededFx != null) {
                    onSucceededFx.run();
                }
            },
            onFailedFx
        );
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
