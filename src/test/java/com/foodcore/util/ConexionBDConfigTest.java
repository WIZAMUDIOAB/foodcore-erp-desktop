package com.foodcore.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConexionBDConfigTest {

    private static final Map<String, String> VALID_PASSES = Map.of(
        "FOODCORE_DB_PASS", "AppSecret#Test123",
        "FOODCORE_MIGRATOR_PASS", "MigSecret#Test123"
    );

    @AfterEach
    void limpiarPropiedadesSistema() {
        System.clearProperty("FOODCORE_DB_USER");
        System.clearProperty("FOODCORE_DB_PASS");
        System.clearProperty("FOODCORE_MIGRATOR_USER");
        System.clearProperty("FOODCORE_MIGRATOR_PASS");
        System.clearProperty("FOODCORE_DB_SSL_MODE");
        System.clearProperty("FOODCORE_DB_URL");
    }

    @Test
    @DisplayName("Fail-fast cuando falta FOODCORE_DB_PASS o esta vacia")
    void debeLanzarIllegalStateExceptionSiFaltaDbPass() {
        Properties props = baseProps();
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> ConexionBD.validarConfiguracion(props, key -> null)
        );
        assertTrue(ex.getMessage().contains("FOODCORE_DB_PASS"));
    }

    @Test
    @DisplayName("Fail-fast cuando falta FOODCORE_MIGRATOR_PASS")
    void debeLanzarIllegalStateExceptionSiFaltaMigratorPass() {
        Properties props = baseProps();
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> ConexionBD.validarConfiguracion(
                props,
                key -> "FOODCORE_DB_PASS".equals(key) ? "AppSecret#Only" : null
            )
        );
        assertTrue(ex.getMessage().contains("FOODCORE_MIGRATOR_PASS"));
    }

    @Test
    @DisplayName("Rechaza configuracion cuando db.user y db.migrator.user coinciden")
    void debeRechazarMismoUsuarioParaAppYMigrador() {
        Properties props = baseProps();
        props.setProperty("db.user", "root");
        props.setProperty("db.migrator.user", "root");

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> ConexionBD.validarConfiguracion(props, VALID_PASSES::get)
        );
        assertTrue(ex.getMessage().contains("cuentas distintas"));
    }

    @Test
    @DisplayName("Rechaza sslMode=DISABLED o JDBC URL con useSSL=false")
    void debeRechazarTlsDeshabilitado() {
        Properties props = baseProps();
        props.setProperty("db.sslMode", "DISABLED");

        IllegalStateException exMode = assertThrows(
            IllegalStateException.class,
            () -> ConexionBD.validarConfiguracion(props, VALID_PASSES::get)
        );
        assertTrue(exMode.getMessage().contains("TLS"));

        assertThrows(
            IllegalStateException.class,
            () -> ConexionBD.validarJdbcUrlSegura("jdbc:mysql://127.0.0.1:3306/foodcore?useSSL=false")
        );
    }

    @Test
    @DisplayName("Construye configuracion valida con sslMode=REQUIRED y cuentas separadas")
    void debeConstruirConfiguracionSeguraValida() {
        Properties props = baseProps();
        ConexionBD.DatabaseConfig cfg = ConexionBD.validarConfiguracion(props, VALID_PASSES::get);
        assertEquals("REQUIRED", cfg.sslMode());
        assertEquals("foodcore_app", cfg.appUser());
        assertEquals("foodcore_migrator", cfg.migratorUser());
        assertTrue(cfg.jdbcUrl().contains("sslMode=REQUIRED"));
    }

    private Properties baseProps() {
        Properties props = new Properties();
        props.setProperty("db.host", "127.0.0.1");
        props.setProperty("db.port", "3306");
        props.setProperty("db.name", "foodcore_test");
        props.setProperty("db.sslMode", "REQUIRED");
        props.setProperty("db.user", "foodcore_app");
        props.setProperty("db.password", "${FOODCORE_DB_PASS}");
        props.setProperty("db.migrator.user", "foodcore_migrator");
        props.setProperty("db.migrator.password", "${FOODCORE_MIGRATOR_PASS}");
        return props;
    }
}
