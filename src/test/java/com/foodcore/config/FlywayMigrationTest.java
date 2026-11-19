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
    @DisplayName("R-02.8 & R-05 (1/3): BD vacía -> flyway.migrate() aplica V1..V25 sin error y registra success=1 en flyway_schema_history")
    void debeAplicarMigracionesV1AV4SobreBaseDeDatosVacia() throws Exception {
        MigrateResult result = MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
        assertEquals(25, result.migrationsExecuted);
        assertEquals("25", result.targetSchemaVersion);

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
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25")));
        }

        // Verificar hardening V25: 0 PINs en texto plano, índices compuestos creados y collations normalizadas
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rsPin = st.executeQuery("SELECT COUNT(*) FROM usuario WHERE pin IS NOT NULL")) {
                rsPin.next();
                assertEquals(0, rsPin.getInt(1), "V25 debe limpiar todos los PINs en texto plano (usuario.pin IS NULL)");
            }
            try (ResultSet rsIdx = st.executeQuery(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() AND index_name IN ("
                    + "'idx_venta_estado_fecha', 'idx_pedido_estado_fecha', 'idx_detven_producto_estado', "
                    + "'idx_asistencia_usuario_fecha', 'idx_auditoria_fecha_accion', "
                    + "'idx_cxc_cliente_estado', 'idx_cxp_proveedor_estado', 'idx_kardex_ref_tipo')")) {
                rsIdx.next();
                assertEquals(8, rsIdx.getInt(1), "V25 debe crear todos los índices compuestos críticos");
            }
            try (ResultSet rsCol = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name IN ('documento_empleado', 'vacaciones', 'movimiento_pago', 'token_blacklist') "
                    + "AND table_collation = 'utf8mb4_0900_ai_ci'")) {
                rsCol.next();
                assertEquals(4, rsCol.getInt(1), "V25 debe normalizar collation de 4 tablas a utf8mb4_0900_ai_ci");
            }
        }

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rsBlacklist = st.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables "
                     + "WHERE table_schema = DATABASE() AND table_name IN ('token_blacklist', 'configuracion_impresora', 'cierre_contable')")) {
            rsBlacklist.next();
            assertEquals(3, rsBlacklist.getInt(1), "Las tablas de V22 y V23 deben existir");
        }

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rsCols = st.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.columns "
                     + "WHERE table_schema = DATABASE() AND ((table_name = 'detalle_pedido' AND column_name = 'prioridad') "
                     + "OR (table_name = 'detalle_venta' AND column_name = 'estado') "
                     + "OR (table_name = 'configuracion_impresora' AND column_name = 'actualizado_en') "
                     + "OR (table_name = 'mesa' AND column_name = 'capacidad') "
                     + "OR (table_name = 'cliente' AND column_name = 'es_demo') "
                     + "OR (table_name = 'producto' AND column_name = 'es_demo') "
                     + "OR (table_name = 'venta' AND column_name = 'es_demo'))")) {
            rsCols.next();
            assertEquals(7, rsCols.getInt(1),
                "Las columnas de V23 y V24 (capacidad, es_demo en cliente/producto/venta) deben existir");
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
