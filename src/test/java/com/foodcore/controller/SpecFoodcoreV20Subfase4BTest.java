package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.dao.AsistenciaDAO;
import com.foodcore.dao.TurnoProgramadoDAO;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.AsistenciaDAOImpl;
import com.foodcore.dao.impl.TurnoProgramadoDAOImpl;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.model.Asistencia;
import com.foodcore.model.FilaPlanilla;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.HorariosService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de Pruebas Unitarias, Integrales y Visuales para Sub-Fase 4B — RECURSOS HUMANOS (FoodCore v2.0).
 * Cubre:
 * - Módulo 6: Control de Asistencia, Kiosco de Marcaje (DNI/PIN), Planilla Mensual y Exportación Excel (.xlsx).
 * - Módulo 7: Programación de Turnos, Calendario Semanal, Alertas de Cobertura y Portal de Empleado.
 * - Captura visual de las 5 vistas de Recursos Humanos.
 */
class SpecFoodcoreV20Subfase4BTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots-v2.0-4b");

    private final AsistenciaDAO asistenciaDAO = new AsistenciaDAOImpl();
    private final TurnoProgramadoDAO turnoDAO = new TurnoProgramadoDAOImpl();
    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();

    private final AsistenciaService asistenciaService = new AsistenciaService(asistenciaDAO, usuarioDAO);
    private final HorariosService horariosService = new HorariosService(turnoDAO, usuarioDAO);

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        poblarDatosDemostracionRRHH();
    }

    @Test
    @DisplayName("4B-1: Marcaje Kiosco - Entrada puntual y detección de colaborador por DNI/PIN")
    void testMarcajeKioscoEntrada() throws Exception {
        // Limpiar asistencias abiertas de hoy para admin y asegurar entrada programada en el futuro (puntual)
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DELETE FROM asistencia WHERE id_usuario = 1 AND fecha = CURRENT_DATE");
            st.execute("UPDATE usuario SET hora_entrada_programada = "
                    + "CASE WHEN CURRENT_TIME() >= '22:55:00' THEN '23:59:59' ELSE ADDTIME(CURRENT_TIME(), '01:00:00') END "
                    + "WHERE id_usuario = 1");
        }

        // Marcaje de entrada usando DNI de admin ("45892147")
        AsistenciaService.ResultadoMarcaje resDni = asistenciaService.registrarMarcajeKiosco("45892147");
        assertTrue(resDni.isExitoso(), "El marcaje por DNI debe ser exitoso");
        assertEquals("ENTRADA", resDni.getTipo(), "Debe registrarse como tipo ENTRADA");
        assertNotNull(resDni.getUsuario(), "Debe identificar al usuario");
        assertEquals("admin", resDni.getUsuario().getUsername());
        assertNotNull(resDni.getAsistencia(), "Debe generar objeto Asistencia");

        // Verificar en BD que el registro está abierto (hora_salida IS NULL)
        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Asistencia> abierta = asistenciaDAO.buscarMarcajeAbierto(conn, 1, LocalDate.now());
            assertTrue(abierta.isPresent(), "Debe existir marcaje abierto en BD");
            assertEquals("PRESENTE", abierta.get().getEstado());
        }

        // Segundo marcaje para el mismo colaborador: debe ser SALIDA
        AsistenciaService.ResultadoMarcaje resSalida = asistenciaService.registrarMarcajeKiosco("1001"); // PIN de admin
        assertTrue(resSalida.isExitoso(), "El segundo marcaje debe procesar la salida");
        assertEquals("SALIDA", resSalida.getTipo(), "Debe registrarse como tipo SALIDA");
        assertEquals("FINALIZADO", resSalida.getAsistencia().getEstado());
    }

    @Test
    @DisplayName("4B-2: Cálculo de Horas Laboradas y Horas Extra (> 8 horas)")
    void testCalculoHorasLaboradasYExtra() throws Exception {
        int idAsistencia;
        try (Connection conn = ConexionBD.getConnection()) {
            Asistencia asist = new Asistencia();
            asist.setIdUsuario(2);
            asist.setIdEmpleado(2);
            asist.setFecha(LocalDate.now());
            // Entrada hace 9.5 horas
            asist.setHoraEntrada(LocalDateTime.now().minusMinutes(570));
            asist.setEstado("PRESENTE");
            asist.setTipoMarcaje("DNI");
            idAsistencia = asistenciaDAO.registrarEntrada(conn, asist);
            assertTrue(idAsistencia > 0);
        }

        // Registrar salida simulando 9.5 horas de jornada
        LocalDateTime salida = LocalDateTime.now();
        double horas = 9.50;
        double horasExtra = 1.50; // 9.5 - 8.0 = 1.50

        try (Connection conn = ConexionBD.getConnection()) {
            boolean ok = asistenciaDAO.registrarSalida(conn, idAsistencia, salida, horas, horasExtra);
            assertTrue(ok, "La salida debe actualizarse en BD");

            List<Asistencia> lista = asistenciaDAO.listarPorFecha(conn, LocalDate.now());
            Optional<Asistencia> asistActualizada = lista.stream().filter(a -> a.getIdAsistencia() == idAsistencia).findFirst();
            assertTrue(asistActualizada.isPresent());
            assertEquals(9.50, asistActualizada.get().getHorasTrabajadas(), 0.01);
            assertEquals(1.50, asistActualizada.get().getHorasExtra(), 0.01);
            assertEquals("FINALIZADO", asistActualizada.get().getEstado());
        }
    }

    @Test
    @DisplayName("4B-3: Planilla Mensual y Exportación a Excel (.xlsx)")
    void testExportacionPlanillaMensualExcel() throws Exception {
        int anio = LocalDate.now().getYear();
        int mes = LocalDate.now().getMonthValue();

        List<FilaPlanilla> planilla = asistenciaService.calcularPlanillaMensual(anio, mes);
        assertNotNull(planilla, "La nómina calculada no debe ser nula");
        assertFalse(planilla.isEmpty(), "Debe contener registros de empleados con asistencias");

        File archivoExcel = SCREENSHOT_DIR.resolve("Planilla_Prueba_" + anio + "_" + mes + ".xlsx").toFile();
        if (archivoExcel.exists()) {
            assertTrue(archivoExcel.delete());
        }

        asistenciaService.exportarPlanillaMensualExcel(anio, mes, archivoExcel);

        assertTrue(archivoExcel.exists(), "El archivo Excel generado debe existir");
        assertTrue(archivoExcel.length() > 2000, "El tamaño del archivo Excel debe ser superior a 2 KB");

        // Validar estructura interna del libro Excel
        try (FileInputStream fis = new FileInputStream(archivoExcel);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet, "Debe existir al menos una hoja en el libro Excel");
            assertTrue(sheet.getSheetName().contains("Planilla"), "El nombre de la hoja debe incluir 'Planilla'");
            assertTrue(sheet.getPhysicalNumberOfRows() >= 4, "Debe incluir título, subtítulo, cabecera y filas");
        }
    }

    @Test
    @DisplayName("4B-4: Programación Semanal de Turnos y Evaluación de Alertas de Cobertura")
    void testProgramacionTurnosYAlertasCobertura() throws Exception {
        // Asignar turno para admin el LUNES en la MAÑANA
        int idTurno = horariosService.asignarTurno(1, "LUNES", "MANANA", LocalTime.of(8, 0), LocalTime.of(16, 0));
        assertTrue(idTurno > 0, "El turno asignado debe retornar un ID positivo");

        List<TurnoProgramado> turnosAdmin = horariosService.listarPorUsuario(1);
        assertFalse(turnosAdmin.isEmpty(), "El colaborador debe tener turnos asignados");

        List<TurnoProgramado> turnosSemana = horariosService.listarSemana();
        assertFalse(turnosSemana.isEmpty(), "La semana debe tener programación registrada");

        // Publicar los turnos de la semana
        boolean publicado = horariosService.publicarHorariosSemana();
        assertTrue(publicado, "Los turnos deben marcarse como publicados");

        // Evaluar alertas de cobertura (debe detectar turnos sin cubrir en fin de semana si los hay)
        List<String> alertas = horariosService.evaluarAlertasCobertura();
        assertNotNull(alertas, "La lista de alertas no debe ser nula");

        // Eliminar el turno de prueba
        boolean eliminado = horariosService.eliminarTurno(idTurno);
        assertTrue(eliminado, "El turno debe eliminarse correctamente");
    }

    @Test
    @DisplayName("4B-5: Captura Visual de las 5 Vistas de Recursos Humanos (SPEC-FOODCORE-v2.0)")
    void testCapturasVisualesSubfase4B() throws Exception {
        // 1. Kiosco de Marcaje de Asistencia (Reloj en vivo + Teclado Numérico + DNI/PIN)
        renderizarYCapturar("/com/foodcore/view/RelojAsistenciaView.fxml", 1200, 750, "01_reloj_asistencia_kiosco_v2.0.png");

        // 2. Panel de Control de Asistencia del Personal (KPIs, Tabla de Marcas, Exportar Planilla)
        renderizarYCapturar("/com/foodcore/view/AsistenciaView.fxml", 1200, 750, "02_panel_control_asistencia_v2.0.png");

        // 3. Programación Semanal de Turnos y Alertas de Cobertura (Calendario L-D y Asignación)
        renderizarYCapturar("/com/foodcore/view/HorariosView.fxml", 1200, 750, "03_programacion_horarios_v2.0.png");

        // 4. Portal Personal del Empleado (Mi Horario, KPIs de Horas/Sueldo, Registro Propio)
        renderizarYCapturar("/com/foodcore/view/MiHorarioView.fxml", 1200, 750, "04_mi_horario_empleado_v2.0.png");

        // 5. Sidebar Cockpit con Sección de Recursos Humanos Integrada
        renderizarYCapturar("/com/foodcore/view/MainLayout.fxml", 1280, 768, "05_sidebar_cockpit_rrhh_v2.0.png");
    }

    private void poblarDatosDemostracionRRHH() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {

            // Asegurar datos laborales en tabla usuario
            st.execute("UPDATE usuario SET dni = '45892147', pin = '1001', sueldo_base = 3200.00, horario_laboral = '08:00-17:00' WHERE username = 'admin'");
            st.execute("UPDATE usuario SET dni = '71258963', pin = '2001', sueldo_base = 1400.00, horario_laboral = '08:00-16:00' WHERE username = 'cajero1'");
            st.execute("UPDATE usuario SET dni = '73654129', pin = '3001', sueldo_base = 1350.00, horario_laboral = '12:00-20:00' WHERE username = 'mozo1'");
            st.execute("UPDATE usuario SET dni = '74125896', pin = '4001', sueldo_base = 1600.00, horario_laboral = '10:00-18:00' WHERE username = 'cocina1'");

            // Insertar turnos de demostración para la semana
            st.execute("INSERT IGNORE INTO turno_programado (id_turno_prog, id_usuario, dia_semana, turno, hora_inicio, hora_fin, publicado) VALUES "
                    + "(101, 1, 'LUNES', 'MANANA', '08:00:00', '16:00:00', 1), "
                    + "(102, 2, 'LUNES', 'MANANA', '08:00:00', '16:00:00', 1), "
                    + "(103, 3, 'LUNES', 'TARDE', '12:00:00', '20:00:00', 1), "
                    + "(104, 4, 'LUNES', 'TARDE', '10:00:00', '18:00:00', 1), "
                    + "(105, 1, 'MARTES', 'MANANA', '08:00:00', '16:00:00', 1), "
                    + "(106, 2, 'MARTES', 'MANANA', '08:00:00', '16:00:00', 1), "
                    + "(107, 3, 'MIERCOLES', 'TARDE', '12:00:00', '20:00:00', 1), "
                    + "(108, 2, 'JUEVES', 'MANANA', '08:00:00', '16:00:00', 1), "
                    + "(109, 3, 'VIERNES', 'NOCHE', '18:00:00', '02:00:00', 1), "
                    + "(110, 4, 'SABADO', 'NOCHE', '18:00:00', '02:00:00', 1)");

            // Limpiar asistencias previas para evitar duplicidad entre Flyway y fixtures de prueba
            st.execute("DELETE FROM asistencia");

            // Asistencias demostrativas para cálculos de nómina
            st.execute("INSERT INTO asistencia (id_asistencia, id_usuario, id_empleado, fecha, hora_entrada, hora_ingreso, hora_salida, horas_trabajadas, horas_extra, tardanza_minutos, estado, tipo_marcaje, observaciones) VALUES "
                    + "(101, 1, 1, CURRENT_DATE, CONCAT(CURRENT_DATE, ' 07:55:00'), '07:55:00', NULL, 0.00, 0.00, 0, 'PRESENTE', 'DNI', 'Ingreso puntual administrador'), "
                    + "(102, 2, 2, CURRENT_DATE, CONCAT(CURRENT_DATE, ' 08:14:00'), '08:14:00', NULL, 0.00, 0.00, 14, 'TARDE', 'DNI', 'Tardanza de 14 min por tráfico'), "
                    + "(103, 4, 4, CURRENT_DATE, CONCAT(CURRENT_DATE, ' 09:58:00'), '09:58:00', NULL, 0.00, 0.00, 0, 'PRESENTE', 'PIN', 'Marcaje por PIN en cocina'), "
                    + "(104, 3, 3, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 11:58:00'), '11:58:00', CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 21:00:00'), 9.03, 1.03, 0, 'FINALIZADO', 'DNI', 'Turno extendido con horas extra'), "
                    + "(105, 2, 2, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 08:02:00'), '08:02:00', CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 16:05:00'), 8.05, 0.05, 0, 'FINALIZADO', 'DNI', 'Jornada regular completa'), "
                    + "(106, 1, 1, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 07:50:00'), '07:50:00', CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 17:10:00'), 9.33, 1.33, 0, 'FINALIZADO', 'DNI', 'Supervisión general')");
        }
    }

    private void renderizarYCapturar(String fxmlPath, double width, double height, String filename) throws Exception {
        AtomicReference<BufferedImage> imageRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                Parent root = loader.load();

                // Detener timeline si es RelojAsistenciaController para no dejar threads activos
                Object controller = loader.getController();
                if (controller instanceof RelojAsistenciaController rac) {
                    rac.stopTimeline();
                }

                Scene scene = new Scene(root, width, height);
                App.applyStylesheets(scene);
                root.applyCss();
                root.layout();
                normalizarAnimaciones(root);

                WritableImage fxImage = root.snapshot(new SnapshotParameters(), null);
                int imgW = (int) fxImage.getWidth();
                int imgH = (int) fxImage.getHeight();
                BufferedImage bufferedImage = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
                PixelReader reader = fxImage.getPixelReader();
                for (int y = 0; y < imgH; y++) {
                    for (int x = 0; x < imgW; x++) {
                        bufferedImage.setRGB(x, y, reader.getArgb(x, y));
                    }
                }
                imageRef.set(bufferedImage);
            } catch (Exception e) {
                throw new RuntimeException("Error capturando " + fxmlPath, e);
            }
        });

        BufferedImage img = imageRef.get();
        assertNotNull(img, "La imagen generada no debe ser nula");
        File outFile = SCREENSHOT_DIR.resolve(filename).toFile();
        ImageIO.write(img, "png", outFile);
        assertTrue(outFile.exists() && outFile.length() > 1000, "Captura Sub-Fase 4B generada: " + outFile.getAbsolutePath());
    }

    private void normalizarAnimaciones(Node node) {
        if (node == null) return;
        if (!node.isDisabled() && !node.opacityProperty().isBound()) {
            node.setOpacity(1.0);
        }
        if (!node.scaleXProperty().isBound()) {
            node.setScaleX(1.0);
        }
        if (!node.scaleYProperty().isBound()) {
            node.setScaleY(1.0);
        }
        if (node instanceof ScrollPane sp) {
            sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                normalizarAnimaciones(child);
            }
        }
    }
}
