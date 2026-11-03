package com.foodcore.support;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Soporte de ejecución en hilo JavaFX (con soporte Monocle Headless / TestFX)
 * según SPEC-FOODCORE-v1.0 (§15.3; REMEDIATION R-03).
 */
public final class JavaFxTestSupport {

    private static final AtomicBoolean FX_INITIALIZED = new AtomicBoolean(false);

    private JavaFxTestSupport() {
    }

    public static synchronized void iniciarToolkit() {
        if (FX_INITIALIZED.get()) {
            return;
        }
        System.setProperty("java.awt.headless", "true");
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
            latch.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        FX_INITIALIZED.set(true);
    }

    public static <T> T runOnFxThreadAndWait(Callable<T> action) throws Exception {
        iniciarToolkit();
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout esperando ejecución en hilo JavaFX");
        }
        if (error.get() != null) {
            if (error.get() instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    public static void runOnFxThreadAndWait(Runnable action) throws Exception {
        runOnFxThreadAndWait(() -> {
            action.run();
            return null;
        });
    }
}
