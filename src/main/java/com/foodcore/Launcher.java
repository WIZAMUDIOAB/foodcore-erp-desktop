package com.foodcore;

import com.foodcore.api.ApiServer;
import com.foodcore.config.FlywayMigrator;
import com.foodcore.config.LoggingBootstrap;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
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
        ConexionBD.validarVariablesEntornoObligatorias();
        ApiServer.resolverKeystorePasswordObligatorio();
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
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Entorno sin display gráfico detectado; iniciando modo Servidor Headless", t);
            iniciarModoServidorHeadless();
        }
    }

    private static void iniciarModoServidorHeadless() {
        ConexionBD.validarVariablesEntornoObligatorias();
        try {
            FlywayMigrator.migrateOnBootstrap();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Migraciones Flyway diferidas en arranque headless", e);
        }
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
