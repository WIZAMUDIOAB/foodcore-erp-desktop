package com.foodcore.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pruebas unitarias para el readiness check de /health cuando BD o Flyway no están listos.
 */
class HealthReadinessTest {

    @Test
    @DisplayName("/health reporta DOWN y NOT_READY sin exponer metadatos internos si la conexión es nula o inválida")
    void debeReportarDownCuandoBdNoEstaLista() {
        ApiServer api = ApiServer.getInstance();

        ObjectNode sinConexion = api.evaluarEstadoSalud(null);
        assertEquals("DOWN", sinConexion.path("status").asText());
        assertEquals("DOWN", sinConexion.path("db").asText());
        assertEquals("NOT_READY", sinConexion.path("flyway").asText());
        assertFalse(sinConexion.has("migrationsApplied"));
        assertFalse(sinConexion.has("port"));
        assertFalse(sinConexion.has("version"));

        Connection connInvalida = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("isValid".equals(method.getName())) {
                        return false;
                    }
                    return null;
                }
        );
        ObjectNode sinValidacion = api.evaluarEstadoSalud(connInvalida);
        assertEquals("DOWN", sinValidacion.path("status").asText());
        assertEquals("NOT_READY", sinValidacion.path("flyway").asText());
    }
}
