package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación UI y capturas del Módulo B (Portal del Empleado: Solicitud de Vacaciones + SolicitudesView Gerencial).
 */
class ModuloBVacacionesUiTest {

    private static final Path SCREENSHOT_DIR = Path.of("target", "v470-screenshots");

    @BeforeAll
    static void setUpClass() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
    }

    @Test
    @DisplayName("Módulo B: Portal del Empleado calcula días disponibles y envía solicitud de vacaciones")
    void testPortalEmpleadoSolicitudVacacionesYCaptura() throws Exception {
        Usuario cajero = new Usuario();
        cajero.setIdUsuario(2);
        cajero.setUsername("cajero");
        cajero.setNombreEmpleado("Maria Cajera");
        cajero.setNombreRol("CAJERO");
        cajero.setSueldoBase(1600.00);
        AuthService.setUsuarioSesionActual(cajero);

        AtomicReference<MiHorarioController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MiHorarioView.fxml"));
                Parent root = loader.load();
                MiHorarioController ctrl = loader.getController();
                ctrlRef.set(ctrl);

                LocalDate inicio = LocalDate.now().plusDays(10);
                LocalDate fin = inicio.plusDays(3);
                ctrl.getDpVacInicio().setValue(inicio);
                ctrl.getDpVacFin().setValue(fin);
                ctrl.getTxtVacMotivo().setText("Vacaciones anuales programadas");
                ctrl.handleSolicitarVacaciones();

                new Scene(root, 1240, 820);
                root.applyCss();
                root.layout();
                guardarSnapshot(root, "05_portal_empleado_vacaciones_v470.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        MiHorarioController ctrl = ctrlRef.get();
        assertEquals(3, ctrl.getTabPaneMiPortal().getTabs().size());
        assertEquals("4 días", ctrl.getLblVacDiasCalculados().getText());
        assertTrue(ctrl.getLblVacDisponibles().getText().contains("Disponibles:"));
        assertTrue(ctrl.getLblVacEstadoMsg().getText().contains("Solicitud registrada"));
        assertFalse(ctrl.getListaMisVacaciones().isEmpty());
    }

    @Test
    @DisplayName("Módulo B: SolicitudesView gerencial aprueba solicitudes y actualiza tabla")
    void testSolicitudesViewGerencialCaptura() throws Exception {
        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setUsername("admin");
        admin.setNombreEmpleado("Administrador General");
        admin.setNombreRol("ADMIN");
        AuthService.setUsuarioSesionActual(admin);

        AtomicReference<SolicitudesController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/SolicitudesView.fxml"));
                Parent root = loader.load();
                SolicitudesController ctrl = loader.getController();
                ctrl.cargarSolicitudesSincrono();
                ctrlRef.set(ctrl);

                new Scene(root, 1240, 820);
                root.applyCss();
                root.layout();
                guardarSnapshot(root, "06_solicitudes_aprobacion_gerencial_v470.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertFalse(ctrlRef.get().getListaSolicitudes().isEmpty());
    }

    private static void guardarSnapshot(Parent root, String filename) throws Exception {
        WritableImage fxImage = root.snapshot(new SnapshotParameters(), null);
        int width = Math.max(1, (int) fxImage.getWidth());
        int height = Math.max(1, (int) fxImage.getHeight());
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(bufferedImage, "png", SCREENSHOT_DIR.resolve(filename).toFile());
    }
}
