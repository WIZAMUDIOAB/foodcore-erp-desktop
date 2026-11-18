package com.foodcore.controller;

import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.ResumenSeguridadHoy;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.UiScreenshotTestSupport;
import com.foodcore.support.UiScreenshotTestSupport.VistaCargada;
import com.foodcore.util.ConexionBD;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integración para el KPI Card "Alertas de Seguridad del Sistema (Hoy)"
 * en {@link DashboardController} (ÍTEM 9 v5.1.0).
 */
class DashboardSecurityAlertsTest {

    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
    }

    @AfterAll
    static void limpiarAuditoriaSeguridad() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
    }

    @Test
    @DisplayName("Dashboard consulta auditoria, refleja intentos fallidos/anulaciones reales y genera captura")
    void dashboardMuestraAlertasSeguridadDesdeAuditoriaReal() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            ResumenSeguridadHoy inicial = auditoriaDAO.obtenerResumenSeguridadHoy(conn);
            assertEquals(0, inicial.intentosFallidosHoy());
            assertEquals(0, inicial.anulacionesHoy());
        }

        // Generar intento de login fallido real + evento 2FA fallido + anulación de venta
        AuthServiceImpl auth = new AuthServiceImpl();
        assertThrows(NegocioException.class, () -> auth.login("mozo", "ClaveIncorrecta#999"));

        try (Connection conn = ConexionBD.getConnection()) {
            auditoriaDAO.registrarEvento(
                    conn, 1, "2FA_FALLIDO", "USUARIO", null, "Código TOTP rechazado en terminal POS-01");
            auditoriaDAO.registrarEvento(
                    conn, 2, "VENTA_ANULADA", "VENTA", "COMPLETADA", "Anulación autorizada de boleta B001-00018");

            ResumenSeguridadHoy resumen = auditoriaDAO.obtenerResumenSeguridadHoy(conn);
            assertEquals(2, resumen.intentosFallidosHoy());
            assertEquals(1, resumen.anulacionesHoy());
            assertTrue(resumen.requiereAtencion());
            assertTrue(resumen.ultimoAccesoSospechoso().contains("admin")
                    || resumen.ultimoAccesoSospechoso().contains("2FA_FALLIDO"));
        }

        AtomicReference<VistaCargada<DashboardController>> holder = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                holder.set(UiScreenshotTestSupport.cargarVista("/com/foodcore/view/DashboardView.fxml", 1280, 860));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // Ejecutar recarga síncrona desde hilo de test para poblar desde MySQL + flush Platform.runLater
        DashboardController ctrl = holder.get().controller();
        ctrl.cargarMetricasDashboardSincrono();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Label lblFallidos = ctrl.getLblSecIntentosFallidos();
                Label lblAnulaciones = ctrl.getLblSecAnulacionesHoy();
                Label lblUltimo = ctrl.getLblSecUltimoSospechoso();
                Label lblBadge = ctrl.getLblSecEstadoBadge();

                assertNotNull(ctrl.getVboxAlertasVencimiento());
                assertEquals("2", lblFallidos.getText());
                assertEquals("1", lblAnulaciones.getText());
                assertTrue(lblUltimo.getText().contains("2FA_FALLIDO")
                        || lblUltimo.getText().contains("LOGIN_FALLIDO"));
                assertEquals("ATENCIÓN REQUERIDA", lblBadge.getText());
                assertTrue(lblBadge.getStyleClass().contains("badge-pill-danger"));

                // Desplazar scroll hacia el card inferior para fotografiar claramente Alertas de Seguridad
                if (holder.get().root().lookup(".scroll-pane") instanceof javafx.scene.control.ScrollPane sp) {
                    sp.setVvalue(1.0);
                }
                UiScreenshotTestSupport.capturarEvidencia(
                        holder.get().root(), "03_dashboard_alertas_seguridad_v510.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Resumen de seguridad hoy funciona con JVM en UTC, sesión MySQL UTC y eventos después de las 19:00 Lima")
    void resumenSeguridadHoyResisteDiferenciaUtcEntreJvmYMySql() throws Exception {
        TimeZone zonaOriginal = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long finDiaLimaMenosSegundos = LocalDate.now(ZoneId.of("America/Lima"))
                    .atTime(23, 30)
                    .atZone(ZoneId.of("America/Lima"))
                    .toEpochSecond();

            try (Connection conn = ConexionBD.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("SET SESSION time_zone = '+00:00'");
                st.executeUpdate("INSERT INTO auditoria (id_usuario, accion, entidad_afectada, datos_nuevos, fecha) "
                        + "VALUES (1, 'LOGIN_FALLIDO', 'USUARIO', 'Evento tardío UTC+1d', FROM_UNIXTIME(" + finDiaLimaMenosSegundos + ")), "
                        + "(1, '2FA_FALLIDO', 'USUARIO', 'Evento tardío 2FA', FROM_UNIXTIME(" + (finDiaLimaMenosSegundos + 5) + ")), "
                        + "(2, 'VENTA_ANULADA', 'VENTA', 'Anulación tardía', FROM_UNIXTIME(" + (finDiaLimaMenosSegundos + 10) + "))");

                ResumenSeguridadHoy resumen = auditoriaDAO.obtenerResumenSeguridadHoy(conn);
                assertEquals(2, resumen.intentosFallidosHoy());
                assertEquals(1, resumen.anulacionesHoy());
                assertTrue(resumen.requiereAtencion());
            }
        } finally {
            TimeZone.setDefault(zonaOriginal);
        }
    }
}
