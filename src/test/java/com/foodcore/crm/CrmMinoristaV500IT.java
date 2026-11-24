package com.foodcore.crm;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.controller.ClientesController;
import com.foodcore.controller.VentaController;
import com.foodcore.controller.venta.DefaultVentaCobroCoordinator;
import com.foodcore.controller.venta.FakeVentaPosInteractionPort;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.FidelizacionDAO;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.FidelizacionDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.model.Cliente;
import com.foodcore.model.CompraClienteResumen;
import com.foodcore.model.HistorialPuntos;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.AuthService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
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
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de Pruebas de Integración End-to-End y Evidencia Visual UI para
 * SPEC-FOODCORE-v5.0.0-CRM (CRM Minorista y Fidelización).
 */
class CrmMinoristaV500IT {

    private static final Path SCREENSHOT_DIR = Path.of("target/v500-screenshots");

    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final FidelizacionDAO fidelizacionDAO = new FidelizacionDAOImpl();
    private final FidelizacionService fidelizacionService = new FidelizacionService();

    private int idProducto50;
    private int idProducto59;
    private int idProducto80;

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);

        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setUsername("admin");
        admin.setNombreEmpleado("Administrador CRM");
        admin.setNombreRol("ADMIN");
        AuthService.setUsuarioSesionActual(admin);

        CajaServiceImpl cajaService = new CajaServiceImpl(new JdbcTransactionManager(), new CajaDAOImpl());
        cajaService.abrirTurnoCaja(1, "Caja POS CRM v5.0", new BigDecimal("250.00"));

        InsumoDAOImpl insumoDAO = new InsumoDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            try (Statement stPromo = conn.createStatement()) {
                stPromo.executeUpdate("UPDATE promocion SET activo = FALSE");
            }
            long idInsumo = insumoDAO.crear(conn, new Insumo(
                    0L, "Proteina Selecta POS", "UNIDAD",
                    new BigDecimal("500.0000"), new BigDecimal("10.0000"), new BigDecimal("12.00")));
            int idCat;
            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('Especialidades CRM', 'Platos carta v5')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.executeUpdate();
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
            }
            idProducto50 = insertarProductoConReceta(conn, idCat, idInsumo, "CRM-50", "Lomo Saltado Clásico", "50.00");
            idProducto59 = insertarProductoConReceta(conn, idCat, idInsumo, "CRM-59", "Parrilla Personal Deluxe", "59.00");
            idProducto80 = insertarProductoConReceta(conn, idCat, idInsumo, "CRM-80", "Fuente Familiar Criolla", "80.00");
        }
    }

    private int insertarProductoConReceta(Connection conn,
                                          int idCat,
                                          long idInsumo,
                                          String codigo,
                                          String nombre,
                                          String precio) throws Exception {
        int idProd;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, stock_disponible, disponible) "
                        + "VALUES (?, ?, ?, 'Plato de prueba CRM', ?, 100, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idCat);
            ps.setString(2, codigo);
            ps.setString(3, nombre);
            ps.setBigDecimal(4, new BigDecimal(precio));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                idProd = rs.getInt(1);
            }
        }
        try (PreparedStatement psRec = conn.prepareStatement(
                "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 1.0000)")) {
            psRec.setInt(1, idProd);
            psRec.setLong(2, idInsumo);
            psRec.executeUpdate();
        }
        return idProd;
    }

    private static FakeVentaPosInteractionPort crearPortCobroDirecto() {
        FakeVentaPosInteractionPort port = new FakeVentaPosInteractionPort();
        port.configurarCobroDirecto(new DefaultVentaCobroCoordinator(new VentaService(), new AutorizacionService()));
        port.setConfirmarCumpleanos(false);
        return port;
    }

    private static Optional<Cliente> buscarYAsociarClienteAsync(VentaController ctrl, String criterio,
            Boolean confirmarCumpleanos) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Optional<Cliente>> resultadoRef = new AtomicReference<>(Optional.empty());
        JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrl.buscarYAsociarClientePorCriterioAsync(
                criterio, confirmarCumpleanos, res -> {
                    resultadoRef.set(res);
                    latch.countDown();
                }));
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timeout esperando búsqueda asíncrona de cliente " + criterio);
        return resultadoRef.get();
    }

    @Test
    @DisplayName("v5.0.0 Criterio 1 & 2: Registro rápido en POS/Backoffice, búsqueda por DNI/teléfono y acumulación S/ 50.00 = 50 puntos")
    void testRegistroRapidoYAcumulacionPuntosEnVentaPos() throws Exception {
        Producto prod50 = new Producto(idProducto50, 1, "CRM-50", "Lomo Saltado Clásico",
                "Al wok", new BigDecimal("50.00"), new BigDecimal("100"), true);

        AtomicReference<Cliente> cliRef = new AtomicReference<>();
        AtomicReference<VentaController> ctrlRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                    App.cargarFuentesSistema();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                    Parent root = loader.load();
                    Scene scene = new Scene(root, 1240, 780);
                    App.applyStylesheets(scene);

                    VentaController ctrl = loader.getController();
                    ctrl.setInteractionPort(crearPortCobroDirecto());
                    ctrl.cargarCatalogoProductosSincrono();
                    ctrlRef.set(ctrl);
                    rootRef.set(root);

                    // 1. Registrar rápidamente cliente minorista desde el POS (Nombre + DNI)
                    Cliente nuevo = ctrl.registrarYAsociarClienteRapidoPos(
                            "78901234", "Valeria Castro Ruiz", "999888777",
                            LocalDate.now().minusYears(26));
                    assertNotNull(nuevo);
                    assertTrue(nuevo.getIdCliente() > 0);
                    assertEquals("MINORISTA", nuevo.getTipoCliente());
                    cliRef.set(nuevo);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            VentaController ctrl = ctrlRef.get();
            Parent root = rootRef.get();

            // 2. Búsqueda asíncrona por teléfono debe localizar al mismo cliente
            Optional<Cliente> porTel = buscarYAsociarClienteAsync(ctrl, "999888777", Boolean.FALSE);
            assertTrue(porTel.isPresent());
            assertEquals("78901234", porTel.get().getNumeroDocumento());

            // 3. Agregar producto S/ 50.00
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.agregarProductoAVenta(prod50);
                Label lblTotal = (Label) root.lookup("#lblTotal");
                assertEquals("S/ 50.00", lblTotal.getText());
                HBox rowCli = (HBox) root.lookup("#rowClienteAsociadoPos");
                assertTrue(rowCli.isVisible(), "Debe mostrar barra de cliente asociado en POS");
            });

            // Capturar POS acumulando puntos para cliente minorista no cumpleañero (Carlos)
            buscarYAsociarClienteAsync(ctrl, "74568901", Boolean.FALSE);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    capturarNodo(root, "02_pos_acumular_puntos_v500.png");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Re-asociar Valeria (0 pts) y ejecutar cobro real de S/ 50.00
            buscarYAsociarClienteAsync(ctrl, "78901234", Boolean.FALSE);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrl.handleCobroEfectivo(null));
        } finally {
            if (ctrlRef.get() != null) {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrlRef.get().stopTimeline());
            }
        }

        int idCli = cliRef.get().getIdCliente();
        assertEquals(50, fidelizacionService.obtenerPuntosCliente(idCli),
                "Venta de S/ 50.00 debe acumular exactamente 50 puntos");

        try (Connection conn = ConexionBD.getConnection()) {
            List<HistorialPuntos> hist = fidelizacionDAO.listarHistorialPuntos(conn, idCli);
            assertFalse(hist.isEmpty(), "Debe registrarse movimiento en tabla historial_puntos");
            assertEquals(50, hist.get(0).getPuntosGanados());
            assertNotNull(hist.get(0).getIdVenta(), "historial_puntos.id_venta debe enlazar la venta generada");
            assertEquals("ACUMULACION", hist.get(0).getTipoMovimiento());
        }
    }

    @Test
    @DisplayName("v5.0.0 Criterio 3: Canje de 100 puntos por S/ 5.00 de descuento SIN alterar el IGV, solo al Total")
    void testCanjeDePuntosNoAlteraIgvSoloReduceTotal() throws Exception {
        Producto prod59 = new Producto(idProducto59, 1, "CRM-59", "Parrilla Personal Deluxe",
                "Parrilla", new BigDecimal("59.00"), new BigDecimal("100"), true);

        AtomicReference<VentaController> ctrlRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                    App.cargarFuentesSistema();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                    Parent root = loader.load();
                    Scene scene = new Scene(root, 1240, 780);
                    App.applyStylesheets(scene);

                    VentaController ctrl = loader.getController();
                    ctrl.setInteractionPort(crearPortCobroDirecto());
                    ctrl.cargarCatalogoProductosSincrono();
                    ctrlRef.set(ctrl);
                    rootRef.set(root);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            VentaController ctrl = ctrlRef.get();
            Parent root = rootRef.get();

            // Cliente seeded Carlos Ramírez Quispe (DNI 74568901, 220 pts, NO cumpleaños)
            Optional<Cliente> optCar = buscarYAsociarClienteAsync(ctrl, "74568901", Boolean.FALSE);
            assertTrue(optCar.isPresent());
            assertEquals(220, optCar.get().getPuntosAcumulados());

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    ctrl.agregarProductoAVenta(prod59);

                    Label lblSub = (Label) root.lookup("#lblSubtotal");
                    Label lblIgv = (Label) root.lookup("#lblIgv");
                    Label lblTotal = (Label) root.lookup("#lblTotal");
                    Button btnCanjear = (Button) root.lookup("#btnCanjearPuntosPos");

                    assertTrue(btnCanjear.isVisible() && !btnCanjear.isDisabled(),
                            "El botón Canjear Puntos debe mostrarse activo cuando el cliente tiene >= 100 puntos");
                    assertEquals("S/ 50.00", lblSub.getText());
                    assertEquals("S/ 9.00", lblIgv.getText());
                    assertEquals("S/ 59.00", lblTotal.getText());

                    // Canjear 1 bloque = 100 pts -> S/ 5.00 descuento
                    ctrl.handleCanjearPuntosPos();

                    assertEquals("S/ 50.00", lblSub.getText(),
                            "La base imponible no debe alterarse tras canjear puntos");
                    assertEquals("S/ 9.00", lblIgv.getText(),
                            "El IGV NO debe verse afectado por el canje de puntos");
                    assertEquals("S/ 54.00", lblTotal.getText(),
                            "El Total a Pagar debe reducirse en S/ 5.00");

                    HBox rowDescPts = (HBox) root.lookup("#rowDescuentoPuntos");
                    assertTrue(rowDescPts.isVisible());
                    assertEquals("-S/ 5.00", ((Label) root.lookup("#lblDescuentoPuntos")).getText());

                    capturarNodo(root, "03_pos_canjear_puntos_v500.png");

                    ctrl.handleCobroEfectivo(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            if (ctrlRef.get() != null) {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrlRef.get().stopTimeline());
            }
        }

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT subtotal, igv, descuento_puntos, total FROM venta ORDER BY id_venta DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(new BigDecimal("50.00"), rs.getBigDecimal("subtotal"));
            assertEquals(new BigDecimal("9.00"), rs.getBigDecimal("igv"));
            assertEquals(new BigDecimal("5.00"), rs.getBigDecimal("descuento_puntos"));
            assertEquals(new BigDecimal("54.00"), rs.getBigDecimal("total"));

            Optional<Cliente> postCarlos = clienteDAO.buscarPorDocumento(conn, "74568901");
            assertTrue(postCarlos.isPresent());
            // 220 iniciales - 100 canjeados + 54 ganados por total S/ 54.00 = 174 pts
            assertEquals(174, postCarlos.get().getPuntosAcumulados());
        }
    }

    @Test
    @DisplayName("v5.0.0 Criterio 4 & 5: Alerta 10% cumpleaños en POS y Backoffice Minoristas con Últimas 10 Compras + Puntos Acumulados")
    void testAlertaCumpleanosYBackofficeClientesMinoristasHistorial() throws Exception {
        Producto prod80 = new Producto(idProducto80, 1, "CRM-80", "Fuente Familiar Criolla",
                "Familiar", new BigDecimal("80.00"), new BigDecimal("100"), true);
        Producto prod50 = new Producto(idProducto50, 1, "CRM-50", "Lomo Saltado Clásico",
                "Al wok", new BigDecimal("50.00"), new BigDecimal("100"), true);

        AtomicReference<VentaController> ctrlRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        // 1. Verificar Alerta de Cumpleaños + 10% descuento en POS para Lucía Fernández Torres (DNI 71234567)
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                    App.cargarFuentesSistema();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                    Parent root = loader.load();
                    Scene scene = new Scene(root, 1240, 780);
                    App.applyStylesheets(scene);

                    VentaController ctrl = loader.getController();
                    ctrl.setInteractionPort(crearPortCobroDirecto());
                    ctrl.cargarCatalogoProductosSincrono();
                    ctrl.agregarProductoAVenta(prod80);
                    ctrlRef.set(ctrl);
                    rootRef.set(root);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            VentaController ctrl = ctrlRef.get();
            Parent root = rootRef.get();

            // Asociar cliente cumpleañero aceptando descuento del 10%
            Optional<Cliente> lucia = buscarYAsociarClienteAsync(ctrl, "71234567", Boolean.TRUE);
            assertTrue(lucia.isPresent());
            assertEquals("¡Hoy es el cumpleaños de Lucía Fernández Torres! ¿Deseas aplicar un 10% de descuento?",
                    ctrl.getUltimoMensajeAlertaCumpleanos());

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    HBox bannerCumple = (HBox) root.lookup("#rowAlertaCumplePos");
                    assertTrue(bannerCumple.isVisible(), "El banner de cumpleaños debe mostrarse en el POS");
                    assertEquals("-S/ 8.00", ((Label) root.lookup("#lblDescuento")).getText());
                    assertEquals("S/ 72.00", ((Label) root.lookup("#lblTotal")).getText());

                    capturarNodo(root, "04_pos_cumpleanos_alerta_10pct_v500.png");

                    ctrl.handleCobroEfectivo(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Registrar segunda compra rápida para validar el historial ordenado en Backoffice
            buscarYAsociarClienteAsync(ctrl, "71234567", Boolean.FALSE);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.agregarProductoAVenta(prod50);
                ctrl.handleCobroEfectivo(null);
            });
        } finally {
            if (ctrlRef.get() != null) {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrlRef.get().stopTimeline());
            }
        }

        // Ahora abrir Backoffice ClientesView.fxml, cambiar a Clientes Minoristas y verificar ficha + últimas compras
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ClientesView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1240, 780);
                App.applyStylesheets(scene);
                root.applyCss();
                root.layout();

                ClientesController ctrl = loader.getController();
                ctrl.cargarDatosSincrono();
                ctrl.seleccionarTabMinoristas();

                assertFalse(ctrl.getListaMinoristas().isEmpty(), "La pestaña Clientes Minoristas debe mostrar datos");
                Cliente luciaView = ctrl.getListaMinoristas().stream()
                        .filter(c -> "71234567".equals(c.getNumeroDocumento()))
                        .findFirst()
                        .orElseThrow();
                // Puntos esperados de Lucía: 150 + 72 + 50 = 272 pts
                assertEquals(272, luciaView.getPuntosAcumulados());

                ctrl.getTblClientesMinoristas().getSelectionModel().select(luciaView);
                ctrl.seleccionarCliente(luciaView);
                ctrl.cargarHistorialComprasSincrono(luciaView.getIdCliente());

                List<CompraClienteResumen> compras = ctrl.getHistorialCompras();
                assertEquals(2, compras.size(), "Debe listar las 2 compras completadas de Lucía");
                assertTrue(compras.stream().anyMatch(c -> c.getResumenProductos().contains("Fuente Familiar Criolla")));
                assertTrue(compras.stream().anyMatch(c -> c.getResumenProductos().contains("Lomo Saltado Clásico")));

                assertTrue(ctrl.getTblClientesMinoristas().getColumns().stream()
                        .anyMatch(col -> "Puntos Acumulados".equals(col.getText())),
                        "Debe existir columna 'Puntos Acumulados'");

                capturarNodo(root, "01_clientes_minoristas_backoffice_v500.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void capturarNodo(Parent root, String filename) throws Exception {
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
        File outFile = SCREENSHOT_DIR.resolve(filename).toFile();
        ImageIO.write(bufferedImage, "png", outFile);
        assertTrue(outFile.exists() && outFile.length() > 2000, "Screenshot generado: " + outFile.getAbsolutePath());
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
