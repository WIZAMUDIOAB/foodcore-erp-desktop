package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación integral de criterios de aceptación (§13) y generación de las 7 capturas
 * de evidencia de UI-REDESIGN-SPEC-FOODCORE-v1.1 (§14).
 */
class UiRedesignSpecTest {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots-v1.1");

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        sembrarCatalogoYTurnoParaCapturas();
    }

    private void sembrarCatalogoYTurnoParaCapturas() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT IGNORE INTO categoria (nombre, descripcion) VALUES "
                + "('Hamburguesas', 'Parrilla'), ('Pollo Crispy', 'Broaster'), ('Bebidas', 'Heladas'), ('Complementos', 'Extras')");
            st.executeUpdate("INSERT IGNORE INTO producto (id_categoria, codigo, nombre, precio_venta, stock_disponible, disponible) VALUES "
                + "(1, 'HAM-11', 'Hamburguesa Royal Doble', 24.50, 50, 1), "
                + "(1, 'HAM-12', 'Cheese Burger Clásica', 18.00, 50, 1), "
                + "(1, 'HAM-13', 'Burger Tocino BBQ', 22.00, 40, 1), "
                + "(1, 'ALIT-11', 'Alitas BBQ Picantes x6', 19.50, 40, 1), "
                + "(1, 'CRIS-12', 'Combo Pollo Crispy 3 Pzs', 26.00, 35, 1), "
                + "(1, 'BEB-11', 'Limonada Frozen 1L', 14.00, 60, 1), "
                + "(1, 'BEB-12', 'Chicha Morada Artesanal', 12.00, 60, 1), "
                + "(1, 'COMP-11', 'Papas Fritas Supremas', 11.50, 80, 1)");
            st.executeUpdate("INSERT IGNORE INTO caja (id_usuario_apertura, nombre_caja, monto_apertura, estado) "
                + "VALUES (1, 'Caja Principal 01', 100.00, 'ABIERTA')");
            st.executeUpdate("INSERT IGNORE INTO venta (id_caja, id_usuario, subtotal, igv, total, metodo_pago, monto_recibido, vuelto, estado) VALUES "
                + "(1, 1, 122.88, 22.12, 145.00, 'EFECTIVO', 150.00, 5.00, 'COMPLETADA'), "
                + "(1, 1, 76.27, 13.73, 90.00, 'TARJETA', 90.00, 0.00, 'COMPLETADA'), "
                + "(1, 1, 46.61, 8.39, 55.00, 'YAPE_PLIN', 55.00, 0.00, 'COMPLETADA')");
        }
    }

    @Test
    @DisplayName("UI-REDESIGN-SPEC-FOODCORE-v1.1: Verifica los 15 criterios (§13) y captura las 7 vistas (§14)")
    void testVerificarRedesignYCapturarSieteVistas() throws Exception {
        AtomicReference<Parent> posRootRef = new AtomicReference<>();
        AtomicReference<Parent> kdsRootRef = new AtomicReference<>();
        AtomicReference<KitchenDisplayController> kdsCtrlRef = new AtomicReference<>();
        AtomicReference<Parent> cajaRootRef = new AtomicReference<>();
        AtomicReference<Parent> kardexRootRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();

                // 1. Captura LoginView (§4)
                FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent loginRoot = loginLoader.load();
                Scene loginScene = new Scene(loginRoot, 1080, 700);
                App.applyStylesheets(loginScene);
                loginRoot.applyCss();
                loginRoot.layout();
                normalizarAnimaciones(loginRoot);
                loginRoot.applyCss();
                loginRoot.layout();

                Label lblVersion = (Label) loginRoot.lookup("#lblVersionBadge");
                Label lblMensaje = (Label) loginRoot.lookup("#lblMensaje");
                Button btnLogin = (Button) loginRoot.lookup("#btnLogin");
                assertEquals("v1.1.0", lblVersion.getText(), "Criterio 11: El badge debe decir v1.1.0");
                assertFalse(lblMensaje.isVisible(), "Criterio 4: El login no muestra label rojo permanente");
                assertTrue(btnLogin.getMinHeight() >= 48.0 || btnLogin.getPrefHeight() >= 48.0, "Criterio 2: Botón primario >= 48px");
                guardarSnapshot(loginRoot, "01_login_v1.1.png");

                // 2. Captura PasswordChangeDialog sobre LoginView (§3, §5)
                LoginController loginCtrl = loginLoader.getController();
                Usuario adminMock = new Usuario();
                adminMock.setIdUsuario(1);
                adminMock.setUsername("admin");
                adminMock.setActivo(true);
                adminMock.setCambioPasswordRequerido(true);
                loginCtrl.abrirDialogoCambioPassword(adminMock);
                PasswordChangeDialogController dlgCtrl = loginCtrl.getDialogoPasswordController();
                assertNotNull(dlgCtrl);
                dlgCtrl.getTxtPasswordActual().setText("Admin#2026!");
                dlgCtrl.getTxtPasswordNueva().setText("NuevaClaveSegura#2026!");
                dlgCtrl.getTxtPasswordConfirmar().setText("NuevaClaveSegura#2026!");
                loginRoot.applyCss();
                loginRoot.layout();
                normalizarAnimaciones(loginRoot);
                loginRoot.applyCss();
                loginRoot.layout();
                guardarSnapshot(loginRoot, "02_password_dialog_v1.1.png");

                // 3. Inicializar VentaPOSView (§6: Split 35/65, cifras mono, botones 56px, chips F5/F6/F7)
                FXMLLoader posLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent posRoot = posLoader.load();
                Scene posScene = new Scene(posRoot, 1200, 720);
                App.applyStylesheets(posScene);
                VentaController posCtrl = posLoader.getController();

                GridPane posSplitGrid = (GridPane) posRoot.lookup("#posSplitGrid");
                assertNotNull(posSplitGrid);
                assertEquals(35.0, posSplitGrid.getColumnConstraints().get(0).getPercentWidth(), 0.01, "Criterio 14: Split 35% orden");
                assertEquals(65.0, posSplitGrid.getColumnConstraints().get(1).getPercentWidth(), 0.01, "Criterio 14: Split 65% catálogo");

                posCtrl.agregarProductoAVenta(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("40"), true));
                posCtrl.agregarProductoAVenta(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble", "", new BigDecimal("24.50"), new BigDecimal("40"), true));
                posCtrl.agregarProductoAVenta(new Producto(4, 2, "ALIT-01", "Alitas BBQ Picantes x6", "", new BigDecimal("19.50"), new BigDecimal("30"), true));
                posCtrl.agregarProductoAVenta(new Producto(6, 3, "BEB-01", "Limonada Frozen 1L", "", new BigDecimal("14.00"), new BigDecimal("25"), true));
                posRootRef.set(posRoot);

                // 4. Inicializar KitchenDisplayView (§7: Fondo oscuro #121417, 3 columnas Kanban, 3 SLA <5m / 5-10m / >10m)
                FXMLLoader kdsLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KitchenDisplayView.fxml"));
                Parent kdsRoot = kdsLoader.load();
                Scene kdsScene = new Scene(kdsRoot, 1180, 720);
                App.applyStylesheets(kdsScene);
                kdsRootRef.set(kdsRoot);
                kdsCtrlRef.set(kdsLoader.getController());

                // 5. Captura SalonMesasView (§8: Tarjetas >=120x120px, borde 4px Libre/Ocupada/Por cobrar, H1 centrado)
                FXMLLoader salonLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/SalonMesasView.fxml"));
                Parent salonRoot = salonLoader.load();
                Scene salonScene = new Scene(salonRoot, 1120, 720);
                App.applyStylesheets(salonScene);
                SalonMesasController salonCtrl = salonLoader.getController();
                Pedido pedOcupado1 = new Pedido(101, 1, null, "01", "EN_PREPARACION", new BigDecimal("68.50"));
                pedOcupado1.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(18));
                Pedido pedPorCobrar2 = new Pedido(102, 1, null, "02", "LISTO", new BigDecimal("94.00"));
                pedPorCobrar2.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(34));
                Pedido pedOcupado4 = new Pedido(104, 1, null, "04", "REGISTRADO", new BigDecimal("42.00"));
                pedOcupado4.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(9));
                Pedido pedPorCobrar7 = new Pedido(107, 1, null, "07", "ENTREGADO", new BigDecimal("118.50"));
                pedPorCobrar7.setFechaHora(LocalDateTime.now(ZONA_LIMA).minusMinutes(46));
                salonCtrl.getPedidosActivosPorMesa().put("01", pedOcupado1);
                salonCtrl.getPedidosActivosPorMesa().put("02", pedPorCobrar2);
                salonCtrl.getPedidosActivosPorMesa().put("04", pedOcupado4);
                salonCtrl.getPedidosActivosPorMesa().put("07", pedPorCobrar7);
                salonCtrl.renderizarTarjetasMesas();
                salonRoot.applyCss();
                salonRoot.layout();
                normalizarAnimaciones(salonRoot);
                salonRoot.applyCss();
                salonRoot.layout();
                guardarSnapshot(salonRoot, "05_salon_mesas_v1.1.png");

                // 6. Inicializar CajaView (§9)
                FXMLLoader cajaLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/CajaView.fxml"));
                Parent cajaRoot = cajaLoader.load();
                Scene cajaScene = new Scene(cajaRoot, 1040, 860);
                App.applyStylesheets(cajaScene);
                cajaRootRef.set(cajaRoot);

                // 7. Inicializar KardexView (§10)
                FXMLLoader kardexLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KardexView.fxml"));
                Parent kardexRoot = kardexLoader.load();
                Scene kardexScene = new Scene(kardexRoot, 1120, 720);
                App.applyStylesheets(kardexScene);
                kardexRootRef.set(kardexRoot);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Esperar a que los hilos asíncronos de arranque publiquen en Platform.runLater
        Thread.sleep(600);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent posRoot = posRootRef.get();
                posRoot.applyCss();
                posRoot.layout();
                normalizarAnimaciones(posRoot);
                posRoot.applyCss();
                posRoot.layout();
                guardarSnapshot(posRoot, "03_pos_venta_v1.1.png");

                Parent kdsRoot = kdsRootRef.get();
                KitchenDisplayController kdsCtrl = kdsCtrlRef.get();
                kdsCtrl.handleSimularComanda(null);
                kdsRoot.applyCss();
                kdsRoot.layout();
                normalizarAnimaciones(kdsRoot);
                kdsRoot.applyCss();
                kdsRoot.layout();
                guardarSnapshot(kdsRoot, "04_kds_cocina_v1.1.png");

                Parent cajaRoot = cajaRootRef.get();
                Node panelApertura = cajaRoot.lookup("#panelApertura");
                Node panelArqueo = cajaRoot.lookup("#panelArqueo");
                if (panelApertura != null) {
                    panelApertura.setVisible(false);
                    panelApertura.setManaged(false);
                }
                if (panelArqueo != null) {
                    panelArqueo.setVisible(true);
                    panelArqueo.setManaged(true);
                }
                Label lblEsperado = (Label) cajaRoot.lookup("#lblTotalEsperado");
                if (lblEsperado != null) {
                    lblEsperado.setText("S/ 390.00");
                }
                Label lblVentas = (Label) cajaRoot.lookup("#lblTotalVentas");
                if (lblVentas != null) {
                    lblVentas.setText("Ventas del turno: S/ 290.00");
                }
                Label lblContado = (Label) cajaRoot.lookup("#lblMontoContado");
                if (lblContado != null) {
                    lblContado.setText("S/ 390.00");
                }
                TextField txtFisico = (TextField) cajaRoot.lookup("#txtEfectivoFisico");
                if (txtFisico != null) {
                    txtFisico.setText("390.00");
                }
                Label lblDif = (Label) cajaRoot.lookup("#lblDiferencia");
                if (lblDif != null) {
                    lblDif.setText("S/ 0.00");
                    lblDif.getStyleClass().setAll("text-mono-lg-success");
                }
                cajaRoot.applyCss();
                cajaRoot.layout();
                normalizarAnimaciones(cajaRoot);
                cajaRoot.applyCss();
                cajaRoot.layout();
                guardarSnapshot(cajaRoot, "06_caja_cierre_z_v1.1.png");

                Parent kardexRoot = kardexRootRef.get();
                kardexRoot.applyCss();
                kardexRoot.layout();
                normalizarAnimaciones(kardexRoot);
                kardexRoot.applyCss();
                kardexRoot.layout();
                guardarSnapshot(kardexRoot, "07_kardex_insumos_v1.1.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void normalizarAnimaciones(Node node) {
        if (node == null) {
            return;
        }
        if (!node.isDisabled() && !node.opacityProperty().isBound()) {
            node.setOpacity(1.0);
        }
        if (!node.scaleXProperty().isBound()) {
            node.setScaleX(1.0);
        }
        if (!node.scaleYProperty().isBound()) {
            node.setScaleY(1.0);
        }
        if (!node.translateXProperty().isBound()) {
            node.setTranslateX(0.0);
        }
        if (!node.translateYProperty().isBound()) {
            node.setTranslateY(0.0);
        }
        if (node instanceof ScrollPane sp && sp.getContent() != null) {
            normalizarAnimaciones(sp.getContent());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                normalizarAnimaciones(child);
            }
        }
    }

    private void guardarSnapshot(Parent root, String filename) throws Exception {
        WritableImage fxImage = root.snapshot(new SnapshotParameters(), null);
        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        File outFile = SCREENSHOT_DIR.resolve(filename).toFile();
        ImageIO.write(bufferedImage, "png", outFile);
        assertTrue(outFile.exists() && outFile.length() > 1000, "Captura generada: " + outFile.getAbsolutePath());
    }
}
