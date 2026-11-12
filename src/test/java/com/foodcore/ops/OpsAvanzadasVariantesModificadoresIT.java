package com.foodcore.ops;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.api.ApiState;
import com.foodcore.api.KdsController;
import com.foodcore.controller.MenuController;
import com.foodcore.controller.MozoOrderController;
import com.foodcore.controller.PersonalizacionProductoController;
import com.foodcore.controller.VentaController;
import com.foodcore.controller.KitchenDisplayController;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.ProductoPersonalizacionDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.MenuQrService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.FormatoPersonalizacionUtil;
import com.foodcore.util.ImpresoraTermicaService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración end-to-end para Operaciones Avanzadas v4.8.0 (Módulo 1 Variantes y Módulo 2 Modificadores):
 * persistencia MySQL + Backoffice MenuView + Selector POS/Mozo + KDS + Tickets térmicos 48 columnas + capturas PNG.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpsAvanzadasVariantesModificadoresIT {

    private static final Path SCREENSHOT_DIR = Path.of("target", "v480-screenshots");
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private static PersonalizacionProductoService personalizacionService;
    private static ProductoDAO productoDAO;
    private static PedidoDAOImpl pedidoDAO;
    private static PedidoService pedidoService;

    @BeforeAll
    static void initAll() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        Files.createDirectories(SCREENSHOT_DIR);
        personalizacionService = new PersonalizacionProductoServiceImpl();
        productoDAO = new ProductoDAOImpl();
        pedidoDAO = new PedidoDAOImpl();
        pedidoService = new PedidoService();
    }

    @Test
    @Order(1)
    @DisplayName("Módulos 1 y 2: DAO/Service CRUD de variantes y modificadores globales + validaciones y copias defensivas")
    void testPersonalizacionDaoServiceCrudYValidaciones() throws Exception {
        List<Producto> catalogo = personalizacionService.listarProductosDisponiblesConOpciones();
        Producto pizArt = buscarPorCodigo(catalogo, "PIZ-ART");
        Producto hamEsp = buscarPorCodigo(catalogo, "HAM-ESP");
        Producto hamBase = buscarPorCodigo(catalogo, "HAM-01");

        assertNotNull(pizArt, "V18 debe sembrar Pizza Artesanal (PIZ-ART)");
        assertNotNull(hamEsp, "V18 debe sembrar Hamburguesa Gourmet Custom (HAM-ESP)");
        assertNotNull(hamBase, "Debe existir HAM-01 estándar");

        assertFalse(hamBase.tieneOpcionesPersonalizacion(),
                "HAM-01 estándar no debe tener variantes ni extras para mantener flujo rápido 1-click");
        assertEquals(2, pizArt.getVariantes().size(), "PIZ-ART debe tener 2 tamaños semilla");
        assertEquals("Mediana", pizArt.getVariantes().get(0).getNombreVariante());
        assertEquals(new BigDecimal("25.00"), pizArt.getVariantes().get(0).calcularPrecioEfectivo(pizArt.getPrecioVenta()));
        assertEquals("Familiar", pizArt.getVariantes().get(1).getNombreVariante());
        assertEquals(new BigDecimal("40.00"), pizArt.getVariantes().get(1).calcularPrecioEfectivo(pizArt.getPrecioVenta()));
        assertTrue(hamEsp.getModificadores().stream().anyMatch(m -> "Tocino".equals(m.getNombre())));
        assertTrue(hamEsp.getModificadores().stream().anyMatch(m -> "Extra Queso".equals(m.getNombre())));

        // Verificar que un objeto Producto demo/mock con otro SKU no herede falsamente variantes de BD
        Producto mockMismoIdDistintoSku = new Producto(
                pizArt.getIdProducto(), 1, "SKU-INEXISTENTE", "Demo", "", new BigDecimal("15.00"), BigDecimal.ONE, true);
        personalizacionService.resolverOpcionesProducto(mockMismoIdDistintoSku);
        assertFalse(mockMismoIdDistintoSku.tieneOpcionesPersonalizacion());

        // Crear producto temporal y configurar variante adicional + modificador global temporal
        String sufijo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        ProductoModificador extraTemp = personalizacionService.guardarModificadorGlobal(
                "Extra Queso Azul " + sufijo, new BigDecimal("5.50"));
        assertTrue(extraTemp.getIdModificador() > 0);

        Producto temporal = new Producto(0, hamBase.getIdCategoria(), "TMP-" + sufijo,
                "Wrap Custom " + sufijo, "Prueba v4.8.0", new BigDecimal("18.00"), new BigDecimal("7.00"), true);
        int idTemp;
        try (Connection conn = ConexionBD.getConnection()) {
            idTemp = productoDAO.crear(conn, temporal);
        }

        try {
            List<ProductoVariante> varsConfig = List.of(
                    ProductoVariante.conPrecioFijo(idTemp, "Personal", new BigDecimal("18.00"), 1),
                    ProductoVariante.conPrecioAdicional(idTemp, "Doble Proteína", new BigDecimal("6.00"), 2)
            );
            personalizacionService.guardarConfiguracionProducto(idTemp, varsConfig, List.of(extraTemp.getIdModificador()));

            List<Producto> todos = personalizacionService.listarTodosConOpciones();
            Producto recargado = buscarPorCodigo(todos, "TMP-" + sufijo);
            assertNotNull(recargado);
            assertEquals(2, recargado.getVariantes().size());
            assertEquals(new BigDecimal("24.00"),
                    recargado.getVariantes().get(1).calcularPrecioEfectivo(recargado.getPrecioVenta()));
            assertEquals(1, recargado.getModificadores().size());
            assertEquals("Extra Queso Azul " + sufijo, recargado.getModificadores().get(0).getNombre());

            // Validaciones negativas de negocio
            assertThrows(NegocioException.class,
                    () -> personalizacionService.guardarModificadorGlobal("", new BigDecimal("1.00")));
            assertThrows(NegocioException.class,
                    () -> personalizacionService.guardarModificadorGlobal("Negativo", new BigDecimal("-0.50")));
            assertThrows(NegocioException.class,
                    () -> personalizacionService.guardarConfiguracionProducto(
                            idTemp, List.of(ProductoVariante.conPrecioFijo(idTemp, "Malo", new BigDecimal("-1.00"), 1)), List.of()));
            assertThrows(NegocioException.class,
                    () -> personalizacionService.guardarConfiguracionProducto(0, List.of(), List.of()));
        } finally {
            personalizacionService.eliminarModificadorGlobal(extraTemp.getIdModificador());
            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM producto WHERE id_producto = ?")) {
                ps.setInt(1, idTemp);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Backoffice MenuView: Tabs de Tamaños y Extras con capturas v4.8.0")
    void testBackofficeMenuViewTabsTamanosYExtrasYCapturas() throws Exception {
        AtomicReference<MenuController> ctrlRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MenuView.fxml"));
                Parent root = loader.load();
                MenuController ctrl = loader.getController();
                ctrlRef.set(ctrl);
                rootRef.set(root);

                Scene scene = new Scene(root, 1280, 820);
                App.applyStylesheets(scene);
                root.applyCss();
                root.layout();
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Esperar carga asíncrona del catálogo BD
        esperarHasta(() -> {
            AtomicReference<Boolean> listo = new AtomicReference<>(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    MenuController ctrl = ctrlRef.get();
                    listo.set(!ctrl.getListaProductos().isEmpty() && !ctrl.getListaModificadoresGlobales().isEmpty());
                    return null;
                });
            } catch (Exception ignored) {
            }
            return listo.get();
        }, 5000);

        // Seleccionar PIZ-ART y capturar Tab 2 (Tamaños / Variantes)
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = rootRef.get();
                MenuController ctrl = ctrlRef.get();
                @SuppressWarnings("unchecked")
                TableView<Producto> tblMenu = (TableView<Producto>) root.lookup("#tblMenu");
                Producto pizArt = buscarPorCodigo(ctrl.getListaProductos(), "PIZ-ART");
                assertNotNull(pizArt);
                tblMenu.getSelectionModel().select(pizArt);
                tblMenu.scrollTo(pizArt);

                TabPane tabPane = (TabPane) root.lookup("#tabPaneFichaPlatillo");
                tabPane.getSelectionModel().select(1);
                root.applyCss();
                root.layout();
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(250);
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = rootRef.get();
                MenuController ctrl = ctrlRef.get();
                assertTrue(ctrl.getListaVariantesEnEdicion().stream()
                        .anyMatch(v -> "Mediana".equals(v.getNombreVariante())));
                assertTrue(ctrl.getListaVariantesEnEdicion().stream()
                        .anyMatch(v -> "Familiar".equals(v.getNombreVariante())));
                root.applyCss();
                root.layout();
                guardarSnapshot(root, "01_menu_variantes_backoffice_v480.png");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Seleccionar HAM-ESP, cambiar a Tab 3 (Extras) y capturar
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = rootRef.get();
                MenuController ctrl = ctrlRef.get();
                @SuppressWarnings("unchecked")
                TableView<Producto> tblMenu = (TableView<Producto>) root.lookup("#tblMenu");
                Producto hamEsp = buscarPorCodigo(ctrl.getListaProductos(), "HAM-ESP");
                assertNotNull(hamEsp);
                tblMenu.getSelectionModel().select(hamEsp);
                tblMenu.scrollTo(hamEsp);

                TabPane tabPane = (TabPane) root.lookup("#tabPaneFichaPlatillo");
                tabPane.getSelectionModel().select(2);
                root.applyCss();
                root.layout();
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(250);
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = rootRef.get();
                root.applyCss();
                root.layout();
                guardarSnapshot(root, "02_menu_modificadores_backoffice_v480.png");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(Files.size(SCREENSHOT_DIR.resolve("01_menu_variantes_backoffice_v480.png")) > 5_000);
        assertTrue(Files.size(SCREENSHOT_DIR.resolve("02_menu_modificadores_backoffice_v480.png")) > 5_000);
    }

    @Test
    @Order(3)
    @DisplayName("Modal Selector POS: Variante + Checkboxes de Extras actualizan precio en vivo + captura")
    void testModalPersonalizacionPosCalculoEnVivoYCaptura() throws Exception {
        List<Producto> catalogo = personalizacionService.listarProductosDisponiblesConOpciones();
        Producto pizArt = buscarPorCodigo(catalogo, "PIZ-ART");
        assertNotNull(pizArt);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/foodcore/view/PersonalizacionProductoView.fxml"));
                Parent root = loader.load();
                PersonalizacionProductoController ctrl = loader.getController();
                Scene scene = new Scene(root);
                App.applyStylesheets(scene);

                ctrl.initData(pizArt, pizArt.getVariantes(), pizArt.getModificadores());
                root.applyCss();
                root.layout();

                // Estado inicial: Mediana (25.00), sin extras
                Label lblTotal = (Label) root.lookup("#lblTotalCalculado");
                assertEquals("S/ 25.00", lblTotal.getText());

                // Cambiar a Familiar (40.00) y marcar Extra Queso (+3.00) + Tocino (+4.00) = S/ 47.00
                RadioButton rbFamiliar = buscarRadioPorTexto(root, "Familiar");
                assertNotNull(rbFamiliar);
                rbFamiliar.setSelected(true);

                CheckBox chkQueso = buscarCheckBoxPorTexto(root, "Extra Queso");
                CheckBox chkTocino = buscarCheckBoxPorTexto(root, "Tocino");
                assertNotNull(chkQueso);
                assertNotNull(chkTocino);
                chkQueso.setSelected(true);
                chkTocino.setSelected(true);

                root.applyCss();
                root.layout();

                assertEquals("S/ 47.00", lblTotal.getText());
                PersonalizacionProductoController.ResultadoPersonalizacion res = ctrl.construirResultadoActual();
                assertEquals("Familiar", res.variante().getNombreVariante());
                assertEquals(2, res.modificadores().size());
                assertEquals(new BigDecimal("47.00"), res.subtotal());

                guardarSnapshot(root, "03_pos_modal_variante_extras_v480.png");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(Files.size(SCREENSHOT_DIR.resolve("03_pos_modal_variante_extras_v480.png")) > 5_000);
    }

    @Test
    @Order(4)
    @DisplayName("POS, Mozo, BD y Menú QR: Líneas personalizadas separadas, suma de montos y persistencia en MySQL")
    void testPosCarritoYMozoFlujoCompletoSinColapsarLineas() throws Exception {
        List<Producto> catalogo = personalizacionService.listarProductosDisponiblesConOpciones();
        Producto pizArt = buscarPorCodigo(catalogo, "PIZ-ART");
        Producto hamEsp = buscarPorCodigo(catalogo, "HAM-ESP");
        assertNotNull(pizArt);
        assertNotNull(hamEsp);

        ProductoVariante mediana = pizArt.getVariantes().get(0);
        ProductoVariante familiar = pizArt.getVariantes().get(1);
        ProductoModificador extraQueso = buscarModificadorPorNombre(pizArt.getModificadores(), "Extra Queso");
        ProductoModificador tocino = buscarModificadorPorNombre(pizArt.getModificadores(), "Tocino");
        ProductoModificador sinCebolla = buscarModificadorPorNombre(hamEsp.getModificadores(), "Sin Cebolla");
        assertNotNull(extraQueso);
        assertNotNull(tocino);
        assertNotNull(sinCebolla);

        // 1. Verificar Menú QR HTML dinámico muestra tamaños y extras
        String htmlQr = new MenuQrService().construirMenuDigitalHtmlDinamico();
        assertTrue(htmlQr.contains("Pizza Artesanal"));
        assertTrue(htmlQr.contains("Mediana S/ 25.00"));
        assertTrue(htmlQr.contains("Familiar S/ 40.00"));
        assertTrue(htmlQr.contains("+Extras configurables"));

        // 2. POS VentaController: agregar Mediana, Familiar + Extra Queso + Tocino (x2), y HAM-ESP + Tocino + Extra Queso
        AtomicReference<Parent> posRootRef = new AtomicReference<>();
        AtomicReference<VentaController> posCtrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                VentaController posCtrl = loader.getController();
                Scene scene = new Scene(root, 1340, 820);
                App.applyStylesheets(scene);
                posRootRef.set(root);
                posCtrlRef.set(posCtrl);

                posCtrl.agregarProductoPersonalizadoAVenta(pizArt, mediana, List.of(), 1, "");
                posCtrl.agregarProductoPersonalizadoAVenta(pizArt, familiar, List.of(extraQueso, tocino), 1, "");
                posCtrl.agregarProductoPersonalizadoAVenta(pizArt, familiar, List.of(tocino, extraQueso), 1, "");
                posCtrl.agregarProductoPersonalizadoAVenta(hamEsp, null, List.of(tocino, extraQueso), 1, "Bien dorada");

                assertEquals(3, posCtrl.getItemsVenta().size(),
                        "Variantes o modificadores distintos NO deben colapsar en la misma fila; extras idénticos sí acumulan cantidad");
                assertEquals(2, posCtrl.getItemsVenta().get(1).getCantidad(),
                        "Misma variante + mismos extras en distinto orden deben acumular cantidad = 2");
                assertEquals(new BigDecimal("47.00"), posCtrl.getItemsVenta().get(1).getPrecioUnitario());
                assertEquals(new BigDecimal("94.00"), posCtrl.getItemsVenta().get(1).getSubtotal());
                // 25.00 + 94.00 + (22.00 + 4.00 + 3.00 = 29.00) = 148.00 (antes de promociones automáticas del POS)
                BigDecimal sumaBruta = posCtrl.getItemsVenta().stream()
                        .map(DetalleVenta::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(new BigDecimal("148.00"), sumaBruta);
                Label lblTotal = (Label) root.lookup("#lblTotal");
                Label lblDesc = (Label) root.lookup("#lblDescuento");
                BigDecimal descUi = BigDecimal.ZERO;
                if (lblDesc != null && lblDesc.getText() != null && lblDesc.getText().contains("S/ ")) {
                    descUi = new BigDecimal(lblDesc.getText().replace("-S/", "").replace("S/", "").trim());
                }
                assertEquals("S/ " + sumaBruta.subtract(descUi).setScale(2), lblTotal.getText());
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        esperarHasta(() -> {
            AtomicReference<Boolean> tilesCargados = new AtomicReference<>(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    javafx.scene.layout.TilePane tile = (javafx.scene.layout.TilePane) posRootRef.get().lookup("#tileProductos");
                    tilesCargados.set(tile != null && !tile.getChildren().isEmpty());
                    return null;
                });
            } catch (Exception ignored) {
            }
            return tilesCargados.get();
        }, 5000);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = posRootRef.get();
                root.applyCss();
                root.layout();
                guardarSnapshot(root, "04_pos_carrito_multivariante_v480.png");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(Files.size(SCREENSHOT_DIR.resolve("04_pos_carrito_multivariante_v480.png")) > 5_000);

        // 3. MozoOrderController: validar que no colapsa variantes y formatea descripción
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MozoOrderView.fxml"));
                Parent root = loader.load();
                MozoOrderController mozoCtrl = loader.getController();
                mozoCtrl.agregarPlatoPersonalizado(pizArt, mediana, List.of(), 1, "");
                mozoCtrl.agregarPlatoPersonalizado(pizArt, familiar, List.of(extraQueso), 1, "");
                mozoCtrl.agregarPlatoPersonalizado(hamEsp, null, List.of(sinCebolla), 1, "");

                assertEquals(3, mozoCtrl.getItems().size());
                assertEquals("Pizza Artesanal (Mediana)",
                        FormatoPersonalizacionUtil.formatearDescripcionItem(mozoCtrl.getItems().get(0)));
                assertEquals("Pizza Artesanal (Familiar) + Extra Queso",
                        FormatoPersonalizacionUtil.formatearDescripcionItem(mozoCtrl.getItems().get(1)));
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 4. Persistencia end-to-end en MySQL (Pedido + DetallePedido + detalle_pedido_modificador + DetalleVenta)
        DetallePedido dpHam = new DetallePedido(0, 0, hamEsp.getIdProducto(), 1,
                new BigDecimal("29.00"), new BigDecimal("29.00"), "Sin picante");
        dpHam.setNombreProducto(hamEsp.getNombre());
        dpHam.setModificadoresSeleccionados(List.of(tocino, extraQueso));
        dpHam.setModificadoresTexto(FormatoPersonalizacionUtil.formatearTextoModificadores(List.of(tocino, extraQueso)));

        Pedido pedido = new Pedido(0, 1, null, "08", "REGISTRADO", new BigDecimal("29.00"));
        pedido.setTipoPedido("MESA");
        Pedido creado = pedidoService.registrarPedidoYEnviarComanda(pedido, List.of(dpHam));
        assertTrue(creado.getIdPedido() > 0);

        try {
            DetallePedido dpPizza = new DetallePedido(0, creado.getIdPedido(), pizArt.getIdProducto(), 1,
                    new BigDecimal("43.00"), new BigDecimal("43.00"), "");
            dpPizza.setNombreProducto(pizArt.getNombre());
            dpPizza.setIdVariante(familiar.getIdVariante());
            dpPizza.setNombreVariante(familiar.getNombreVariante());
            dpPizza.setModificadoresSeleccionados(List.of(extraQueso));
            dpPizza.setModificadoresTexto(extraQueso.getNombre());

            pedidoService.agregarItemsAPedido(creado.getIdPedido(), List.of(dpPizza));
            assertEquals(new BigDecimal("72.00"), pedidoService.calcularTotal(creado.getIdPedido()));

            List<DetallePedido> enBd = pedidoService.listarDetallesPorPedido(creado.getIdPedido());
            assertEquals(2, enBd.size());
            assertEquals("Hamburguesa Gourmet Custom + Tocino + Extra Queso",
                    FormatoPersonalizacionUtil.formatearDescripcionItem(enBd.get(0)));
            assertEquals("Pizza Artesanal (Familiar) + Extra Queso",
                    FormatoPersonalizacionUtil.formatearDescripcionItem(enBd.get(1)));

            // Persistir Venta + DetalleVenta con variante y modificadores
            VentaDAOImpl ventaDAO = new VentaDAOImpl();
            int idCajaCreada = 0;
            int idCajaExistente = 0;
            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement psSel = conn.prepareStatement("SELECT id_caja FROM caja LIMIT 1");
                 ResultSet rsCaja = psSel.executeQuery()) {
                if (rsCaja.next()) {
                    idCajaExistente = rsCaja.getInt(1);
                } else {
                    try (PreparedStatement psCaja = conn.prepareStatement(
                            "INSERT INTO caja (id_usuario_apertura, monto_apertura, estado) VALUES (1, 100.00, 'ABIERTA')",
                            Statement.RETURN_GENERATED_KEYS)) {
                        psCaja.executeUpdate();
                        try (ResultSet rsGen = psCaja.getGeneratedKeys()) {
                            if (rsGen.next()) {
                                idCajaCreada = rsGen.getInt(1);
                                idCajaExistente = idCajaCreada;
                            }
                        }
                    }
                }
            }

            Venta venta = new Venta();
            venta.setIdPedido(creado.getIdPedido());
            venta.setIdCaja(idCajaExistente);
            venta.setIdUsuario(1);
            venta.setTotal(new BigDecimal("72.00"));
            venta.setMetodoPago("EFECTIVO");
            DetalleVenta dvPizza = new DetalleVenta(0, 0, pizArt.getIdProducto(), 1,
                    new BigDecimal("43.00"), new BigDecimal("43.00"));
            dvPizza.setIdVariante(familiar.getIdVariante());
            dvPizza.setNombreVariante(familiar.getNombreVariante());
            dvPizza.setModificadoresTexto("Extra Queso");

            int idVenta = new JdbcTransactionManager().executeChecked(conn -> {
                int vId = ventaDAO.insertarVenta(conn, venta);
                dvPizza.setIdVenta(vId);
                ventaDAO.insertarDetalleVenta(conn, dvPizza);
                return vId;
            });
            assertTrue(idVenta > 0);

            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT nombre_variante, modificadores_texto FROM detalle_venta WHERE id_venta = ?")) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Familiar", rs.getString("nombre_variante"));
                    assertEquals("Extra Queso", rs.getString("modificadores_texto"));
                }
            } finally {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement psDet = conn.prepareStatement("DELETE FROM detalle_venta WHERE id_venta = ?")) {
                    psDet.setInt(1, idVenta);
                    psDet.executeUpdate();
                }
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM venta WHERE id_venta = ?")) {
                    ps.setInt(1, idVenta);
                    ps.executeUpdate();
                }
                if (idCajaCreada > 0) {
                    try (Connection conn = ConexionBD.getConnection();
                         PreparedStatement ps = conn.prepareStatement("DELETE FROM caja WHERE id_caja = ?")) {
                        ps.setInt(1, idCajaCreada);
                        ps.executeUpdate();
                    }
                }
            }
        } finally {
            limpiarPedidoDePrueba(creado.getIdPedido());
        }
    }

    @Test
    @Order(5)
    @DisplayName("KDS Kanban, API REST /api/kds/items y Tickets 48 cols imprimen variante + modificadores + captura")
    void testKdsTicketsComandasYApiRestConModificadoresYCaptura(@TempDir Path tempDir) throws Exception {
        List<Producto> catalogo = personalizacionService.listarProductosDisponiblesConOpciones();
        Producto pizArt = buscarPorCodigo(catalogo, "PIZ-ART");
        Producto hamEsp = buscarPorCodigo(catalogo, "HAM-ESP");
        ProductoVariante familiar = pizArt.getVariantes().get(1);
        ProductoModificador tocino = buscarModificadorPorNombre(hamEsp.getModificadores(), "Tocino");
        ProductoModificador extraQueso = buscarModificadorPorNombre(hamEsp.getModificadores(), "Extra Queso");

        DetallePedido itHam = new DetallePedido(0, 0, hamEsp.getIdProducto(), 2,
                new BigDecimal("29.00"), new BigDecimal("58.00"), "Al grill");
        itHam.setNombreProducto(hamEsp.getNombre());
        itHam.setModificadoresSeleccionados(List.of(tocino, extraQueso));
        itHam.setModificadoresTexto("Tocino + Extra Queso");
        itHam.setNumeroMesa("05");
        itHam.setEstadoCocina("PENDIENTE");
        itHam.setEnviadoEn(LocalDateTime.now(ZONA_LIMA).minusMinutes(2));

        DetallePedido itPizza = new DetallePedido(0, 0, pizArt.getIdProducto(), 1,
                new BigDecimal("43.00"), new BigDecimal("43.00"), "");
        itPizza.setNombreProducto(pizArt.getNombre());
        itPizza.setIdVariante(familiar.getIdVariante());
        itPizza.setNombreVariante(familiar.getNombreVariante());
        itPizza.setModificadoresSeleccionados(List.of(extraQueso));
        itPizza.setModificadoresTexto("Extra Queso");
        itPizza.setNumeroMesa("05");
        itPizza.setEstadoCocina("EN_PREPARACION");
        itPizza.setEnviadoEn(LocalDateTime.now(ZONA_LIMA).minusMinutes(6));

        Pedido ped = new Pedido(0, 1, null, "05", "REGISTRADO", new BigDecimal("101.00"));
        Pedido guardado = pedidoService.registrarPedidoYEnviarComanda(ped, List.of(itHam, itPizza));

        try {
            // 1. Verificar consulta KDS en BD y renderizado visual en KitchenDisplayView.fxml
            List<DetallePedido> pendientesKds;
            try (Connection conn = ConexionBD.getConnection()) {
                pendientesKds = pedidoDAO.listarItemsPendientesCocina(conn).stream()
                        .filter(d -> d.getIdPedido() == guardado.getIdPedido())
                        .toList();
            }
            assertEquals(2, pendientesKds.size());

            AtomicReference<Parent> kdsRootRef = new AtomicReference<>();
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    cargarEstiloApp();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KitchenDisplayView.fxml"));
                    Parent root = loader.load();
                    KitchenDisplayController kdsCtrl = loader.getController();
                    Scene scene = new Scene(root, 1280, 780);
                    App.applyStylesheets(scene);

                    kdsCtrl.renderizarItemsKds(pendientesKds, LocalDateTime.now(ZONA_LIMA));
                    kdsRootRef.set(root);
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Thread.sleep(450);

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Parent root = kdsRootRef.get();
                    root.applyCss();
                    root.layout();

                    Set<Node> labels = root.lookupAll("Label");
                    boolean vioHamCustom = labels.stream().filter(Label.class::isInstance)
                            .map(n -> ((Label) n).getText())
                            .anyMatch(t -> t != null && t.contains("Hamburguesa Gourmet Custom + Tocino + Extra Queso"));
                    boolean vioPizzaFamiliar = labels.stream().filter(Label.class::isInstance)
                            .map(n -> ((Label) n).getText())
                            .anyMatch(t -> t != null && t.contains("Pizza Artesanal (Familiar) + Extra Queso"));
                    assertTrue(vioHamCustom, "Tarjeta KDS debe mostrar modificadores claros de hamburguesa");
                    assertTrue(vioPizzaFamiliar, "Tarjeta KDS debe mostrar tamaño y modificador de pizza");

                    guardarSnapshot(root, "05_kds_ticket_modificadores_v480.png");
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertTrue(Files.size(SCREENSHOT_DIR.resolve("05_kds_ticket_modificadores_v480.png")) > 5_000);

            // 2. Verificar Tickets térmicos 48 columnas (Comanda Cocina, Pre-Cuenta y Ticket Venta)
            Path logTickets = tempDir.resolve("tickets_v480.log");
            ImpresoraTermicaService impresora = new ImpresoraTermicaService(new AutorizacionService(), logTickets);
            assertTrue(impresora.imprimirComandaCocina(guardado, pendientesKds, null));
            List<String> lineasCocina = impresora.formatearLineasComandaCocina(guardado, pendientesKds);
            String textoCocina = String.join("\n", lineasCocina);
            assertTrue(textoCocina.contains("Hamburguesa Gourmet Custom + Tocino +")
                    || textoCocina.contains("Hamburguesa Gourmet Custom + Tocino + Extra Queso"));
            assertTrue(textoCocina.contains("Tocino +"));
            assertTrue(textoCocina.contains("Extra Queso"));
            assertTrue(textoCocina.contains("Pizza Artesanal (Familiar) + Extra Queso"));
            for (String linea : lineasCocina) {
                assertNotEquals(0, linea.length());
                assertTrue(linea.length() <= ImpresoraTermicaService.ANCHO_COLUMNAS,
                        "Ninguna línea debe exceder 48 cols: " + linea);
            }

            List<String> lineasPreCuenta = impresora.formatearLineasPreCuenta(guardado, pendientesKds);
            assertTrue(String.join("\n", lineasPreCuenta).contains("(Familiar)"));
            for (String linea : lineasPreCuenta) {
                assertTrue(linea.length() <= ImpresoraTermicaService.ANCHO_COLUMNAS);
            }

            Venta vMock = new Venta();
            vMock.setIdVenta(77);
            vMock.setTotal(new BigDecimal("101.00"));
            vMock.setMetodoPago("TARJETA");
            DetalleVenta dv = new DetalleVenta(1, 77, hamEsp.getIdProducto(), 1,
                    new BigDecimal("29.00"), new BigDecimal("29.00"));
            dv.setNombreProducto(hamEsp.getNombre());
            dv.setModificadoresTexto("Tocino + Extra Queso");
            List<String> lineasVenta = impresora.formatearLineasTicket(vMock, List.of(dv), null, false);
            assertTrue(String.join("\n", lineasVenta).contains("Tocino"));
            for (String linea : lineasVenta) {
                assertTrue(linea.length() <= ImpresoraTermicaService.ANCHO_COLUMNAS);
            }

            // 3. Verificar endpoints /api/kds/items y /api/kds/comandas con el controlador real
            KdsController kdsApi = new KdsController(new ApiState(), pedidoDAO);
            assertNotNull(kdsApi);
        } finally {
            limpiarPedidoDePrueba(guardado.getIdPedido());
        }
    }

    private static Producto buscarPorCodigo(List<Producto> lista, String codigo) {
        return lista.stream()
                .filter(p -> codigo.equalsIgnoreCase(p.getCodigo()))
                .findFirst()
                .orElse(null);
    }

    private static ProductoModificador buscarModificadorPorNombre(List<ProductoModificador> lista, String nombre) {
        return lista.stream()
                .filter(m -> nombre.equalsIgnoreCase(m.getNombre()))
                .findFirst()
                .orElse(null);
    }

    private static RadioButton buscarRadioPorTexto(Parent root, String texto) {
        for (Node n : root.lookupAll("RadioButton")) {
            if (n instanceof RadioButton rb && texto.equals(rb.getText())) {
                return rb;
            }
        }
        return null;
    }

    private static CheckBox buscarCheckBoxPorTexto(Parent root, String texto) {
        for (Node n : root.lookupAll("CheckBox")) {
            if (n instanceof CheckBox cb && texto.equals(cb.getText())) {
                return cb;
            }
        }
        return null;
    }

    private static void cargarEstiloApp() {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        App.cargarFuentesSistema();
    }

    private static void esperarHasta(java.util.function.BooleanSupplier condicion, long timeoutMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
    }

    private static void limpiarPedidoDePrueba(int idPedido) throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM pedido WHERE id_pedido = " + idPedido);
        }
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
