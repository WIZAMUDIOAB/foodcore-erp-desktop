package com.foodcore.config;

import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Configura el enrutamiento de logs {@code java.util.logging} hacia SLF4J/Logback.
 */
public final class LoggingBootstrap {

    private static volatile boolean bridgeInstalled;

    private LoggingBootstrap() {
    }

    /**
     * Instala {@link SLF4JBridgeHandler} una sola vez para capturar logs JUL en Logback.
     */
    public static synchronized void installBridge() {
        if (bridgeInstalled) {
            return;
        }
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        bridgeInstalled = true;
    }
}
