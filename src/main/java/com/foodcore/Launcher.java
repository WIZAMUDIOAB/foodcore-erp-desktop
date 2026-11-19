package com.foodcore;

import com.foodcore.api.ApiServer;
import com.foodcore.api.security.JwtService;
import com.foodcore.config.FlywayMigrator;
import com.foodcore.config.LoggingBootstrap;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lanzador independiente para el JAR ejecutable de FoodCore ERP.
 * Evita la restricción de módulo de JavaFX al no extender directamente de Application
 * y soporta ejecución en modo Servidor Headless para CI/CD (GitHub Actions + Playwright E2E).
 */
public class Launcher {

    private static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());

    public static void main(String[] args) {
        LoggingBootstrap.installBridge();
        App.registrarManejadorExcepcionesNoCapturadas();
        ConexionBD.validarVariablesEntornoObligatorias();
        ApiServer.resolverKeystorePasswordObligatorio();
        JwtService.validarConfiguracionObligatoria();
        boolean modoServidor = Arrays.asList(args).contains("--server")
                || Arrays.asList(args).contains("--headless")
                || "true".equalsIgnoreCase(System.getenv("CI"))
                || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))
                || GraphicsEnvironment.isHeadless();

        if (modoServidor) {
            iniciarModoServidorHeadless();
            return;
        }

        try {
            App.main(args);
        } catch (HeadlessException ex) {
            LOGGER.log(Level.WARNING, "Entorno sin display gráfico detectado; iniciando modo Servidor Headless", ex);
            iniciarModoServidorHeadless();
        } catch (RuntimeException | LinkageError ex) {
            if (esErrorInicializacionHeadlessJavaFx(ex)) {
                LOGGER.log(Level.WARNING, "Error de inicialización gráfica JavaFX/Headless; iniciando modo Servidor Headless", ex);
                iniciarModoServidorHeadless();
            } else {
                throw ex;
            }
        }
    }

    /**
     * Determina de forma estricta si un error durante el lanzamiento de JavaFX corresponde
     * exclusivamente a ausencia de display/toolkit gráfico (headless/Glass/GTK),
     * permitiendo propagar fallos reales de negocio, configuración o programación.
     */
    static boolean esErrorInicializacionHeadlessJavaFx(Throwable error) {
        Throwable actual = error;
        int depth = 0;
        while (actual != null && depth++ < 16) {
            if (actual instanceof HeadlessException) {
                return true;
            }
            String className = actual.getClass().getName();
            String msg = actual.getMessage() != null ? actual.getMessage().toLowerCase(Locale.ROOT) : "";
            if (actual instanceof UnsupportedOperationException
                    && (msg.contains("unable to open display")
                        || msg.contains("headless")
                        || msg.contains("glass")
                        || msg.contains("display"))) {
                return true;
            }
            if ((actual instanceof UnsatisfiedLinkError || actual instanceof NoClassDefFoundError)
                    && (msg.contains("javafx") || msg.contains("glass") || msg.contains("prism")
                        || msg.contains("gtk") || className.contains("javafx"))) {
                return true;
            }
            if (msg.contains("unable to open display")
                    || msg.contains("no x11 display")
                    || msg.contains("cannot open display")
                    || msg.contains("graphics device initialization failed")) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    private static void iniciarModoServidorHeadless() {
        ConexionBD.validarVariablesEntornoObligatorias();
        try {
            FlywayMigrator.migrateOnBootstrap();
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Error crítico de configuración/migración Flyway en el arranque", ex);
            throw ex;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error crítico ejecutando migraciones Flyway en el arranque; se aborta el inicio", e);
            throw new IllegalStateException(
                    "No se pudieron ejecutar las migraciones Flyway en el arranque: " + e.getMessage(),
                    e
            );
        }
        App.registrarShutdownHookUnaVez();
        try {
            ServidorSockets.getInstance().iniciar();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "ServidorSockets ya activo o no disponible en arranque headless", e);
        }
        ApiServer.getInstance().iniciar();
        LOGGER.info("FoodCore ERP activo en modo Servidor Headless en http://localhost:"
                + ApiServer.getInstance().getPuertoActual());
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
