package com.foodcore.util;

import com.foodcore.App;
import com.foodcore.support.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida que el arranque de la aplicación y {@link UiAsyncExecutor} descarguen
 * las operaciones de I/O fuera del JavaFX Application Thread manteniendo la UI responsiva.
 */
class JavaFxAsyncConcurrencyTest {

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @Test
    @DisplayName("App.crearBootstrapTask migra y arranca servicios fuera del hilo JavaFX sin bloquear el render")
    void bootstrapTask_NoBloqueaHiloJavaFx_yActualizaEstado() throws Exception {
        CountDownLatch migracionIniciada = new CountDownLatch(1);
        CountDownLatch liberarMigracion = new CountDownLatch(1);
        CountDownLatch fxMientrasMigra = new CountDownLatch(1);
        CountDownLatch mensajeServiciosRecibido = new CountDownLatch(1);
        CountDownLatch liberarServicios = new CountDownLatch(1);
        CountDownLatch bootstrapFinalizado = new CountDownLatch(1);

        AtomicBoolean migratorOffFx = new AtomicBoolean(false);
        AtomicBoolean socketOffFx = new AtomicBoolean(false);
        AtomicBoolean apiOffFx = new AtomicBoolean(false);
        AtomicBoolean succeededOnFx = new AtomicBoolean(false);
        List<String> mensajes = Collections.synchronizedList(new ArrayList<>());

        Task<Void> task = App.crearBootstrapTask(
            () -> {
                migratorOffFx.set(!Platform.isFxApplicationThread());
                migracionIniciada.countDown();
                if (!liberarMigracion.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout esperando liberación de prueba");
                }
            },
            () -> {
                socketOffFx.set(!Platform.isFxApplicationThread());
                if (!liberarServicios.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout esperando liberación de servicios");
                }
            },
            () -> apiOffFx.set(!Platform.isFxApplicationThread())
        );

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            task.messageProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && !newV.isBlank()) {
                    mensajes.add(newV);
                    if ("Iniciando servicios locales…".equals(newV)) {
                        mensajeServiciosRecibido.countDown();
                    }
                }
            });
            task.setOnSucceeded(ev -> {
                succeededOnFx.set(Platform.isFxApplicationThread());
                bootstrapFinalizado.countDown();
            });
            UiAsyncExecutor.ejecutar(task);
        });

        assertTrue(migracionIniciada.await(3, TimeUnit.SECONDS), "La migración de arranque debe iniciar en worker");

        Platform.runLater(fxMientrasMigra::countDown);
        assertTrue(
                fxMientrasMigra.await(1, TimeUnit.SECONDS),
                "El hilo UI JavaFX debe seguir respondiendo mientras corre Flyway en segundo plano");

        liberarMigracion.countDown();
        assertTrue(mensajeServiciosRecibido.await(3, TimeUnit.SECONDS),
                "El estado 'Iniciando servicios locales…' debe notificarse antes del arranque del API");
        liberarServicios.countDown();
        assertTrue(bootstrapFinalizado.await(5, TimeUnit.SECONDS), "La tarea de bootstrap debe finalizar");

        assertTrue(migratorOffFx.get(), "Flyway debe ejecutarse fuera del hilo JavaFX");
        assertTrue(socketOffFx.get(), "ServidorSockets debe iniciar fuera del hilo JavaFX");
        assertTrue(apiOffFx.get(), "ApiServer debe iniciar fuera del hilo JavaFX");
        assertTrue(succeededOnFx.get(), "onSucceeded debe ejecutarse en el hilo JavaFX");
        assertTrue(mensajes.contains("Migrando base de datos…"));
        assertTrue(mensajes.contains("Iniciando servicios locales…"));
        assertTrue(mensajes.contains("Cargando interfaz de acceso…"));
    }

    @Test
    @DisplayName("UiAsyncExecutor corre trabajo pesado en worker daemon y despacha éxito/error en el hilo UI")
    void uiAsyncExecutor_EjecutaEnDaemonWorkerYNotificaEnFxThread() throws Exception {
        CountDownLatch trabajoIniciado = new CountDownLatch(1);
        CountDownLatch liberarTrabajo = new CountDownLatch(1);
        CountDownLatch fxDuranteTrabajo = new CountDownLatch(1);
        CountDownLatch exitoCompletado = new CountDownLatch(1);

        AtomicBoolean isDaemon = new AtomicBoolean(false);
        AtomicBoolean offFx = new AtomicBoolean(false);
        AtomicBoolean callbackOnFx = new AtomicBoolean(false);
        AtomicReference<String> threadNameRef = new AtomicReference<>();
        AtomicReference<String> resultadoRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> UiAsyncExecutor.ejecutarAsync(
            () -> {
                Thread current = Thread.currentThread();
                isDaemon.set(current.isDaemon());
                offFx.set(!Platform.isFxApplicationThread());
                threadNameRef.set(current.getName());
                trabajoIniciado.countDown();
                if (!liberarTrabajo.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout liberando worker");
                }
                return "OK_ASYNC";
            },
            res -> {
                callbackOnFx.set(Platform.isFxApplicationThread());
                resultadoRef.set(res);
                exitoCompletado.countDown();
            },
            null
        ));

        assertTrue(trabajoIniciado.await(3, TimeUnit.SECONDS));
        Platform.runLater(fxDuranteTrabajo::countDown);
        assertTrue(fxDuranteTrabajo.await(1, TimeUnit.SECONDS), "La cola de eventos FX no debe estar bloqueada");

        liberarTrabajo.countDown();
        assertTrue(exitoCompletado.await(5, TimeUnit.SECONDS));

        assertTrue(isDaemon.get(), "El pool UI debe usar hilos daemon");
        assertTrue(offFx.get(), "El Callable debe correr fuera de JavaFX Application Thread");
        assertTrue(threadNameRef.get().startsWith("foodcore-ui-worker-"));
        assertTrue(callbackOnFx.get(), "onSucceededFx debe invocar en JavaFX Application Thread");
        assertEquals("OK_ASYNC", resultadoRef.get());

        CountDownLatch errorCompletado = new CountDownLatch(1);
        AtomicBoolean errorOnFx = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> UiAsyncExecutor.ejecutarAsync(
            () -> {
                throw new IllegalStateException("fallo-controlado");
            },
            ignored -> { },
            ex -> {
                errorOnFx.set(Platform.isFxApplicationThread());
                errorRef.set(ex);
                errorCompletado.countDown();
            }
        ));

        assertTrue(errorCompletado.await(5, TimeUnit.SECONDS));
        assertTrue(errorOnFx.get(), "onFailedFx debe ejecutarse en el hilo UI de JavaFX");
        assertNotNull(errorRef.get());
        assertFalse(Platform.isFxApplicationThread());
        assertEquals("fallo-controlado", errorRef.get().getMessage());
    }
}
