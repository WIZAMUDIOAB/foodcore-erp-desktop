package com.foodcore.controller;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.DemoDataService;
import com.foodcore.service.DemoDataService.ResumenDemo;
import com.foodcore.service.OnboardingService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de verificación integral para Sub-Fase E (v5.5.0):
 * - E1: Auditoría UI (filtros, paginación 50, EmptyState, exportación PDF/Excel/CSV, solo rol ADMIN)
 * - E2: Onboarding Wizard (validación de 4 pasos, persistencia, disparo con BD vacía)
 * - E3: Datos Demo Descartables (seed-demo.sql y borrado selectivo es_demo = 1)
 */
class SubFaseEAuditoriaOnboardingTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        AuthService.logout();
    }

    @AfterEach
    void tearDown() {
        AuthService.logout();
    }

    @Test
    @DisplayName("E1.1: Solo rol ADMIN accede a AuditoriaView; roles no ADMIN son bloqueados en controlador y Dashboard")
    void testAuditoriaAccesoExclusivoAdmin() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                // 1. Usuario CAJERO bloqueado
                Usuario cajero = new Usuario(2, 2, 2, "cajero", "hash", true);
                cajero.setNombreRol("CAJERO");
                AuthService.setUsuarioSesionActual(cajero);

                FXMLLoader loaderCajero = new FXMLLoader(getClass().getResource("/com/foodcore/view/AuditoriaView.fxml"));
                Parent rootCajero = loaderCajero.load();
                AuditoriaController ctrlCajero = loaderCajero.getController();
                assertFalse(ctrlCajero.isAccesoPermitido(), "CAJERO no debe tener acceso a Auditoría");
                assertTrue(ctrlCajero.getListaAuditoria().isEmpty(), "Lista de auditoría debe permanecer vacía para CAJERO");
                assertTrue(rootCajero.lookup("#boxAccesoRestringido").isVisible(), "Debe mostrar aviso de acceso restringido");

                // 2. MainLayout oculta botón Auditoría a no ADMIN
                FXMLLoader dashLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
                Parent dashRoot = dashLoader.load();
                new Scene(dashRoot, 1280, 800);
                dashRoot.applyCss();
                dashRoot.layout();
                MainDashboardController dashCtrl = dashLoader.getController();
                dashCtrl.aplicarControlAcceso(cajero);
                Button btnNavAuditoria = (Button) dashRoot.lookup("#btnNavAuditoria");
                assertNotNull(btnNavAuditoria);
                assertFalse(btnNavAuditoria.isVisible(), "Sidebar debe ocultar Bitácora de Auditoría para CAJERO");

                // 3. Usuario ADMIN sí accede y ve contenedor .card + combos
                Usuario admin = new Usuario(1, 1, 1, "admin", "hash", true);
                admin.setNombreRol("ADMIN");
                AuthService.setUsuarioSesionActual(admin);
                FXMLLoader loaderAdmin = new FXMLLoader(getClass().getResource("/com/foodcore/view/AuditoriaView.fxml"));
                Parent rootAdmin = loaderAdmin.load();
                new Scene(rootAdmin, 1080, 768);
                rootAdmin.applyCss();
                rootAdmin.layout();
                AuditoriaController ctrlAdmin = loaderAdmin.getController();
                assertTrue(ctrlAdmin.isAccesoPermitido(), "ADMIN debe tener acceso a Auditoría");
                assertTrue(rootAdmin.lookup("#boxContenedorPrincipal").getStyleClass().contains("card"),
                        "Contenedor principal de Auditoría debe usar clase .card");
                assertNotNull(rootAdmin.lookup("#emptyState"), "Debe incluir contenedor #emptyState");
                assertTrue(rootAdmin.lookupAll("#emptyState .label").stream()
                        .anyMatch(n -> n instanceof Label lbl && lbl.getText().contains("No hay registros de auditoría")),
                        "EmptyState debe mostrar 'No hay registros de auditoría'");
                assertNotNull(ctrlAdmin.getCmbFiltroMozo());
                assertNotNull(ctrlAdmin.getCmbFiltroAccion());
                assertNotNull(ctrlAdmin.getDpDesde());
                assertNotNull(ctrlAdmin.getDpHasta());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("E1.2: AuditoriaDAO filtra por usuario/acción/fechas y pagina de 50 en 50; exporta PDF/Excel/CSV")
    void testAuditoriaFiltrosPaginacion50YExportacion() throws Exception {
        AuditoriaDAO dao = new AuditoriaDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            for (int i = 1; i <= 55; i++) {
                dao.registrarEvento(conn, 1, i % 2 == 0 ? "VENTA_POS" : "LOGIN_EXITOSO",
                        "PRUEBA_E1", null, "Evento paginacion #" + i);
            }
            int totalVentas = dao.contarEventosFiltrados(
                    conn, "admin", "VENTA", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
            assertTrue(totalVentas >= 27, "Debe contar eventos filtrados por categoría VENTA");

            int totalTodos = dao.contarEventosFiltrados(
                    conn, "admin", null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
            assertTrue(totalTodos >= 55, "Debe haber al menos 55 eventos para validar 2 páginas de 50");

            List<RegistroAuditoria> pag1 = dao.listarEventosFiltrados(
                    conn, "admin", null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 0, 50);
            List<RegistroAuditoria> pag2 = dao.listarEventosFiltrados(
                    conn, "admin", null, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 50, 50);
            assertEquals(50, pag1.size(), "Página 1 debe contener exactamente 50 registros");
            assertTrue(pag2.size() >= 5, "Página 2 debe contener el resto (>= 5 registros)");

            Usuario admin = new Usuario(1, 1, 1, "admin", "hash", true);
            admin.setNombreRol("ADMIN");
            AuthService.setUsuarioSesionActual(admin);
            AuditoriaController ctrl = new AuditoriaController();
            Path pdf = tempDir.resolve("auditoria.pdf");
            Path xlsx = tempDir.resolve("auditoria.xlsx");
            Path csv = tempDir.resolve("auditoria.csv");

            ctrl.exportarListaAArchivo(FormatoExportacion.PDF, pdf.toFile(), pag1);
            ctrl.exportarListaAArchivo(FormatoExportacion.EXCEL, xlsx.toFile(), pag1);
            ctrl.exportarListaAArchivo(FormatoExportacion.CSV, csv.toFile(), pag1);

            assertTrue(Files.isRegularFile(pdf) && Files.size(pdf) > 200, "PDF debe generarse con contenido");
            assertTrue(Files.isRegularFile(xlsx) && Files.size(xlsx) > 500, "Excel debe generarse con contenido");
            assertTrue(Files.isRegularFile(csv) && Files.readString(csv).contains("VENTA_POS"), "CSV debe contener filas");
        }
    }

    @Test
    @DisplayName("E2.1: OnboardingService valida campos de pasos 1, 2 y 3 rechazando datos inválidos")
    void testOnboardingValidacionPasos() {
        assertThrows(IllegalArgumentException.class,
                () -> OnboardingService.validarPasoRestaurante("", "20609874512", "Av. Lima 123", "999888777"));
        assertThrows(IllegalArgumentException.class,
                () -> OnboardingService.validarPasoRestaurante("Bistro", "RUC_MAL", "Av. Lima 123", "999888777"));
        assertThrows(IllegalArgumentException.class,
                () -> OnboardingService.validarPasoPlatillo("Ceviche", "0", "Entradas"));
        assertThrows(IllegalArgumentException.class,
                () -> OnboardingService.validarPasoPlatillo("Ceviche", "abc", "Entradas"));
        assertThrows(IllegalArgumentException.class,
                () -> OnboardingService.validarPasoMesa("", "4"));
        assertThrows(IllegalArgumentException.class,
                () -> OnboardingService.validarPasoMesa("M01", "0"));

        OnboardingService.validarPasoRestaurante("FoodCore Grill", "20609874512", "Av. Pardo 200", "999000111");
        assertEquals("28.50", OnboardingService.validarPasoPlatillo("Lomo Saltado", "28.50", "Platos de Fondo").toPlainString());
        assertEquals(6, OnboardingService.validarPasoMesa("M99", "6"));
    }

    @Test
    @DisplayName("E2.2: OnboardingWizardView avanza pasos tras validar, guarda en BD e inserta configuracion_restaurante")
    void testOnboardingWizardFlujoCompletoDesdeBdVacia() throws Exception {
        OnboardingService onboardingService = new OnboardingService();
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM configuracion_restaurante");
            assertTrue(onboardingService.necesitaOnboarding(conn),
                    "Con configuracion_restaurante vacía debe requerir Onboarding");
        }

        AtomicReference<OnboardingWizardController> ctrlRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicBoolean dashboardRedirigido = new AtomicBoolean(false);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/OnboardingWizardView.fxml"));
                loader.setControllerFactory(type -> new OnboardingWizardController() {
                    @Override
                    protected void redirigirAlDashboard() {
                        dashboardRedirigido.set(true);
                    }
                });
                Parent root = loader.load();
                new Scene(root, 1080, 768);
                OnboardingWizardController ctrl = loader.getController();
                ctrlRef.set(ctrl);
                rootRef.set(root);

                // Intento fallido en Paso 1 con campos vacíos debe permanecer en Paso 1 y mostrar error
                ctrl.handleSiguiente(null);
                assertEquals(1, ctrl.getPasoActual());
                assertTrue(ctrl.getLblErrorWizard().isVisible(), "Debe mostrar error de validación en paso 1");

                // Completar Paso 1 y avanzar
                ((TextField) root.lookup("#txtNombreRestaurante")).setText("La Cocina de Prueba S.A.C.");
                ((TextField) root.lookup("#txtRuc")).setText("20554433221");
                ((TextField) root.lookup("#txtDireccion")).setText("Av. Arequipa 1200");
                ((TextField) root.lookup("#txtTelefono")).setText("01-4455667");
                ctrl.handleSiguiente(null);
                assertEquals(2, ctrl.getPasoActual(), "Debe avanzar al paso 2");

                // Completar Paso 2 (primer platillo) y guardar en BD
                ((TextField) root.lookup("#txtNombrePlatillo")).setText("Ceviche Clásico Onboarding");
                ((TextField) root.lookup("#txtPrecioPlatillo")).setText("34.00");
                @SuppressWarnings("unchecked")
                ComboBox<String> cmbCat = (ComboBox<String>) root.lookup("#cmbCategoriaPlatillo");
                cmbCat.setValue("Entradas Marina");
                ctrl.handleSiguiente(null);
                assertEquals(3, ctrl.getPasoActual(), "Debe avanzar al paso 3");
                assertTrue(ctrl.getPlatilloCreadoId() > 0, "Debe persistir el primer platillo en BD");

                // Completar Paso 3 (primera mesa) y guardar en BD
                ((TextField) root.lookup("#txtNumeroMesa")).setText("M95");
                ((TextField) root.lookup("#txtCapacidadMesa")).setText("6");
                ctrl.handleSiguiente(null);
                assertEquals(4, ctrl.getPasoActual(), "Debe avanzar al paso 4 de resumen");
                Label lblRes = (Label) root.lookup("#lblResumenRestaurante");
                assertTrue(lblRes.getText().contains("La Cocina de Prueba S.A.C."));

                // Finalizar onboarding
                ctrl.handleEmpezarOperar(null);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        assertTrue(dashboardRedirigido.get(), "Al finalizar onboarding debe redirigir al Dashboard principal");

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            assertFalse(onboardingService.necesitaOnboarding(conn),
                    "Tras completar el Wizard configuracion_restaurante no debe estar vacía");

            try (ResultSet rsMesa = st.executeQuery("SELECT capacidad FROM mesa WHERE numero = 'M95'")) {
                assertTrue(rsMesa.next(), "Mesa M95 debe haberse insertado en Onboarding");
                assertEquals(6, rsMesa.getInt("capacidad"), "Capacidad de mesa M95 debe persistirse");
            }
            try (ResultSet rsProd = st.executeQuery("SELECT COUNT(*) FROM producto WHERE nombre = 'Ceviche Clásico Onboarding'")) {
                rsProd.next();
                assertEquals(1, rsProd.getInt(1), "Platillo del Wizard debe existir en BD");
            }
        }
    }

    @Test
    @DisplayName("E3: DemoDataService carga scripts/seed-demo.sql (es_demo=1) y los borra sin tocar datos reales (es_demo=0)")
    void testCargaYBorradoDatosDemoPreservaDatosReales() throws Exception {
        DemoDataService demoService = new DemoDataService();

        try (Connection conn = ConexionBD.getConnection()) {
            // Insertar un cliente real explícito (es_demo = 0) y un producto real explícito (es_demo = 0)
            String docReal = "10887766";
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cliente (tipo_documento, numero_documento, tipo_cliente, nombre_razon_social, razon_social, activo, es_demo) "
                    + "VALUES ('DNI', ?, 'MINORISTA', 'Cliente Real Persistente', 'Cliente Real Persistente', 1, 0)")) {
                ps.setString(1, docReal);
                ps.executeUpdate();
            }

            ResumenDemo cargado = demoService.cargarDatosDemo(conn, Path.of("scripts", "seed-demo.sql"));
            assertTrue(cargado.clientes() >= 3, "seed-demo.sql debe insertar al menos 3 clientes demo");
            assertTrue(cargado.productos() >= 3, "seed-demo.sql debe insertar al menos 3 productos demo");
            assertTrue(cargado.ventas() >= 2, "seed-demo.sql debe insertar al menos 2 ventas demo");

            // Idempotencia al volver a cargar
            ResumenDemo recargado = demoService.cargarDatosDemo(conn, Path.of("scripts", "seed-demo.sql"));
            assertEquals(cargado.clientes(), recargado.clientes());

            // Borrar datos demo
            int eliminados = demoService.borrarDatosDemo(conn);
            assertTrue(eliminados >= 8, "Debe eliminar filas demo de clientes, productos, ventas y detalles");

            ResumenDemo despues = demoService.contarDatosDemo(conn);
            assertEquals(0, despues.total(), "No debe quedar ningún registro con es_demo = 1");

            // Verificar que el cliente real permanece intacto
            try (PreparedStatement psCheck = conn.prepareStatement(
                    "SELECT COUNT(*) FROM cliente WHERE numero_documento = ? AND es_demo = 0")) {
                psCheck.setString(1, docReal);
                try (ResultSet rs = psCheck.executeQuery()) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "El cliente real (es_demo = 0) no debe ser eliminado");
                }
            }
        }
    }
}
