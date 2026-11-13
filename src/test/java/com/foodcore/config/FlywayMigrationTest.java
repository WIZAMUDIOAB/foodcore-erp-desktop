package com.foodcore.config;

import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración de migraciones versionadas Flyway desde cero y con datos previos
 * según SPEC-FOODCORE-v1.0 (§14, §15.2, §19.3; REMEDIATION R-02.8, R-05), SPEC-FOODCORE-v1.2 (§7) y v4.4.0.
 */
class FlywayMigrationTest {

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
    }

    @Test
    @DisplayName("R-02.8 & R-05 (1/3): BD vacía -> flyway.migrate() aplica V1..V21 sin error y registra success=1 en flyway_schema_history")
    void debeAplicarMigracionesV1AV4SobreBaseDeDatosVacia() throws Exception {
        MigrateResult result = MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
        assertEquals(21, result.migrationsExecuted);
        assertEquals("21", result.targetSchemaVersion);

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank ASC")) {
            Set<String> versiones = new HashSet<>();
            while (rs.next()) {
                versiones.add(rs.getString("version"));
                assertTrue(rs.getBoolean("success"), "Toda migración debe tener success=1");
            }
            assertTrue(versiones.containsAll(Set.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21")));
        }

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rsMinoristas = st.executeQuery(
                 "SELECT COUNT(*) FROM cliente WHERE tipo_cliente = 'MINORISTA'")) {
            rsMinoristas.next();
            assertTrue(rsMinoristas.getInt(1) >= 2, "Deben existir clientes minoristas seedeados en V21");
        }
    }

    @Test
    @DisplayName("R-02.8 & R-05 (2/3): BD con datos previos -> flyway.migrate() respeta validateOnMigrate=true sin alterar datos existentes")
    void debeRespetarValidateOnMigrateSobreBaseConDatosPrevios() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO categoria (nombre, descripcion) VALUES ('Categoria Persistente', 'Datos previos')");
        }

        // Segunda ejecución con validateOnMigrate=true sobre BD ya poblada
        MigrateResult incremental = MySqlIntegrationTestSupport.ejecutarMigrateIncremental();
        assertEquals(0, incremental.migrationsExecuted, "No debe re-ejecutar migraciones ya aplicadas");

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM categoria WHERE nombre = 'Categoria Persistente'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "Los datos previos deben conservarse intactos");
        }
    }

    @Test
    @DisplayName("R-02.8 & R-05 (3/3): Seeds obligatorios -> usuario admin (cambio_password_requerido=1), roles ADMIN/CAJERO/MOZO/COCINA, permisos y terminales KDS-01/POS-01")
    void debeVerificarSeedsObligatoriosDeRolesPermisosAdminYTerminales() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {

            // 1. Verificar roles ADMIN, CAJERO, MOZO, COCINA
            Set<String> codigosRol = new HashSet<>();
            try (ResultSet rsRoles = st.executeQuery("SELECT codigo FROM rol")) {
                while (rsRoles.next()) {
                    codigosRol.add(rsRoles.getString("codigo"));
                }
            }
            assertTrue(codigosRol.containsAll(Set.of("ADMIN", "CAJERO", "MOZO", "COCINA")));

            // 2. Verificar los permisos canónicos §10.3 + VENTA_DESCUENTO (§3.2 v1.2)
            try (ResultSet rsPerm = st.executeQuery("SELECT COUNT(*) FROM permiso")) {
                rsPerm.next();
                assertTrue(rsPerm.getInt(1) >= 14, "Deben existir los permisos canónicos §10.3");
            }

            // 3. Verificar usuario admin con cambio_password_requerido=1 y hash BCrypt costo 12
            try (ResultSet rsAdmin = st.executeQuery(
                    "SELECT password_hash, cambio_password_requerido, activo FROM usuario WHERE username = 'admin'")) {
                assertTrue(rsAdmin.next(), "Debe existir el usuario admin seedeado en V3");
                assertTrue(rsAdmin.getString("password_hash").startsWith("$2a$12$"));
                assertTrue(rsAdmin.getBoolean("cambio_password_requerido"));
                assertTrue(rsAdmin.getBoolean("activo"));
            }

            // 4. Verificar terminales KDS-01 y POS-01
            Set<String> terminales = new HashSet<>();
            try (ResultSet rsTerm = st.executeQuery("SELECT codigo FROM terminal WHERE activo = 1")) {
                while (rsTerm.next()) {
                    terminales.add(rsTerm.getString("codigo"));
                }
            }
            assertTrue(terminales.containsAll(Set.of("KDS-01", "POS-01")));
        }
    }
}
