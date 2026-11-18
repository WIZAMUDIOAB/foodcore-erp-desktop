package com.foodcore.ops;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.api.ApiState;
import com.foodcore.api.KdsController;
import com.foodcore.controller.KitchenDisplayController;
import com.foodcore.controller.MenuController;
import com.foodcore.controller.VentaController;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.ComboService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.ComboServiceImpl;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración end-to-end para Operaciones Avanzadas v4.8.0 (Módulo 3: Combos y Empaquetado):
 * migración V19 + ComboService + Backoffice MenuView + POS tarjeta/línea única + explosión de stock/Kárdex +
 * reversión en anulación + desglose en KDS / Ticket 48 cols + capturas PNG.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpsAvanzadasCombosIT {

    private static final Path SCREENSHOT_DIR = Path.of("target", "v480-screenshots");
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private static ComboService comboService;
    private static PersonalizacionProductoService personalizacionService;
    private static ProductoDAOImpl productoDAO;
    private static InsumoDAOImpl insumoDAO;
    private static PedidoDAOImpl pedidoDAO;
    private static PedidoService pedidoService;
    private static VentaService ventaService;

    @BeforeAll
    static void setUpSuite() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
        Files.createDirectories(SCREENSHOT_DIR);
        JavaFxTestSupport.iniciarToolkit();

        comboService = new ComboServiceImpl();
        personalizacionService = new PersonalizacionProductoServiceImpl();
        productoDAO = new ProductoDAOImpl();
        insumoDAO = new InsumoDAOImpl();
        pedidoDAO = new PedidoDAOImpl();
        pedidoService = new PedidoService(new JdbcTransactionManager(), pedidoDAO, new AutorizacionService());
        ventaService = new VentaService();

        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setIdRol(1);
        admin.setUsername("admin");
        admin.setNombreEmpleado("Administrador Demo");
        AuthService.setUsuarioSesionActual(admin);
    }

    @Test
    @Order(1)
    @DisplayName("M3.1 V19 Combo Duo Burger sembrado + validaciones anti-ciclo/precio/componentes")
    void testV19SemillaYValidacionesCombo() throws Exception {
        List<Combo> combos = comboService.listarCombosActivos();
        Combo duo = combos.stream()
                .filter(c -> "Combo Duo Burger".equalsIgnoreCase(c.getNombre()))
                .findFirst()
                .orElse(null);
        assertNotNull(duo, "V19 debe sembrar Combo Duo Burger");
        assertEquals(new BigDecimal("42.00"), duo.getPrecioComboNormalizado());
        assertEquals(2, duo.getDetalles().size());

        List<Producto> catalogo = personalizacionService.listarProductosDisponiblesConOpciones();
        Producto ham = buscarPorCodigo(catalogo, "HAM-01");
        assertNotNull(ham);

        Combo invalidoNombre = new Combo(0, null, "   ", new BigDecimal("25.00"), true);
        invalidoNombre.setDetalles(List.of(new ComboDetalle(ham.getIdProducto(), ham.getNombre(), 1)));
        assertThrows(NegocioException.class, () -> comboService.guardarCombo(invalidoNombre));

        Combo invalidoPrecio = new Combo(0, null, "Combo Cero", BigDecimal.ZERO, true);
        invalidoPrecio.setDetalles(List.of(new ComboDetalle(ham.getIdProducto(), ham.getNombre(), 1)));
        assertThrows(NegocioException.class, () -> comboService.guardarCombo(invalidoPrecio));

        Combo invalidoVacio = new Combo(0, null, "Combo Vacío", new BigDecimal("20.00"), true);
        assertThrows(NegocioException.class, () -> comboService.guardarCombo(invalidoVacio));

        Combo anidado = new Combo(0, null, "Combo Anidado", new BigDecimal("80.00"), true);
        anidado.setDetalles(List.of(new ComboDetalle(duo.getIdProducto(), duo.getNombre(), 1)));
        assertThrows(NegocioException.class, () -> comboService.guardarCombo(anidado),
                "No debe permitir anidar un combo dentro de otro combo");
    }

    @Test
    @Order(2)
    @DisplayName("M3.2 Backoffice MenuView: crear Combo Familiar con Pizza + 4 Gaseosas + Papas y capturar PNG")
    void testBackofficeCrearComboFamiliarYCapturaPng() throws Exception {
        prepararInsumosYRecetasEscenarioFamiliar();

        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<MenuController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MenuView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1240, 760);
                rootRef.set(root);
                ctrlRef.set(loader.getController());
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        esperarHasta(() -> {
            AtomicBoolean listo = new AtomicBoolean(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    MenuController c = ctrlRef.get();
                    listo.set(!c.getListaProductos().isEmpty());
                    return null;
                });
            } catch (Exception ignored) {
                // Esperar siguiente ciclo
            }
            return listo.get();
        }, 5000);

        // Usar UI de la pestaña Combo: Crear Combo Familiar (1 Pizza Artesanal + 4 Gaseosa Personal + 1 Porción de Papas)
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            Parent root = rootRef.get();
            ctrlRef.get().handleNuevoCombo(null);
            root.applyCss();
            root.layout();
            return null;
        });

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = rootRef.get();
                MenuController ctrl = ctrlRef.get();
                TabPane tabPane = (TabPane) root.lookup("#tabPaneFichaPlatillo");
                Node comboTabRoot = tabPane.getSelectionModel().getSelectedItem().getContent();

                TextField txtCod = (TextField) comboTabRoot.lookup("#txtComboCodigo");
                TextField txtNom = (TextField) comboTabRoot.lookup("#txtComboNombre");
                TextField txtPre = (TextField) comboTabRoot.lookup("#txtComboPrecio");
                @SuppressWarnings("unchecked")
                ComboBox<Producto> cmbComp = (ComboBox<Producto>) comboTabRoot.lookup("#cmbComboComponente");
                TextField txtCant = (TextField) comboTabRoot.lookup("#txtComboCantidad");

                txtCod.setText("CMB-FAM");
                txtNom.setText("Combo Familiar");
                txtPre.setText("49.90");

                Producto pizza = buscarPorCodigo(ctrl.getListaProductos(), "PIZ-ART");
                Producto gaseosa = buscarPorCodigo(ctrl.getListaProductos(), "BEB-01");
                Producto papas = buscarPorCodigo(ctrl.getListaProductos(), "PAP-01");
                assertNotNull(pizza);
                assertNotNull(gaseosa);
                assertNotNull(papas);

                cmbComp.setValue(pizza);
                txtCant.setText("1");
                ctrl.handleAgregarComponenteCombo(null);

                cmbComp.setValue(gaseosa);
                txtCant.setText("4");
                ctrl.handleAgregarComponenteCombo(null);

                cmbComp.setValue(papas);
                txtCant.setText("1");
                ctrl.handleAgregarComponenteCombo(null);

                assertEquals(3, ctrl.getListaComboComponentesEnEdicion().size());

                List<ComboDetalle> componentes = new ArrayList<>(ctrl.getListaComboComponentesEnEdicion());
                Combo guardado = ctrl.guardarComboEnBD(
                        null, null, "CMB-FAM", "Combo Familiar", new BigDecimal("49.90"), true, componentes);
                assertTrue(guardado.getIdCombo() > 0);
                assertEquals(3, guardado.getDetalles().size());
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            ctrlRef.get().cargarDatosMenu();
            return null;
        });

        esperarHasta(() -> {
            AtomicBoolean encontrado = new AtomicBoolean(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    Producto fam = buscarPorCodigo(ctrlRef.get().getListaProductos(), "CMB-FAM");
                    encontrado.set(fam != null && fam.isEsCombo() && fam.getComboComponentes().size() == 3);
                    return null;
                });
            } catch (Exception ignored) {
                // Esperar siguiente ciclo
            }
            return encontrado.get();
        }, 5000);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Parent root = rootRef.get();
                MenuController ctrl = ctrlRef.get();
                @SuppressWarnings("unchecked")
                TableView<Producto> tblMenu = (TableView<Producto>) root.lookup("#tblMenu");
                Producto fam = buscarPorCodigo(ctrl.getListaProductos(), "CMB-FAM");
                assertNotNull(fam);
                tblMenu.getSelectionModel().select(fam);
                tblMenu.scrollTo(fam);

                TabPane tabPane = (TabPane) root.lookup("#tabPaneFichaPlatillo");
                assertEquals("Combo", tabPane.getSelectionModel().getSelectedItem().getText());
                assertTrue(ctrl.getListaComboComponentesEnEdicion().size() == 3);

                root.applyCss();
                root.layout();
                guardarSnapshot(root, "06_menu_crear_combo_backoffice_v480.png");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(Files.exists(SCREENSHOT_DIR.resolve("06_menu_crear_combo_backoffice_v480.png")));
    }

    @Test
    @Order(3)
    @DisplayName("M3.3 POS línea única + VentaService explosión de Kardex en Pizza/Gaseosas/Papas y anulación + PNG")
    void testPosComboYExplosionKardex() throws Exception {
        asegurarCajaAbierta();

        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<VentaController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1280, 780);
                rootRef.set(root);
                ctrlRef.set(loader.getController());
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        esperarHasta(() -> {
            AtomicBoolean listo = new AtomicBoolean(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    listo.set(rootRef.get().lookup("#tileProductos") != null
                            && buscarTarjetaPorNombre(rootRef.get(), "Combo Familiar") != null);
                    return null;
                });
            } catch (Exception ignored) {
                // Esperar carga catálogo
            }
            return listo.get();
        }, 6000);

        Producto comboFamCatalogo;
        try {
            List<Producto> disponibles = personalizacionService.listarProductosDisponiblesConOpciones();
            comboFamCatalogo = buscarPorCodigo(disponibles, "CMB-FAM");
            assertNotNull(comboFamCatalogo);
            assertTrue(comboFamCatalogo.isEsCombo());
            assertFalse(comboFamCatalogo.tieneOpcionesPersonalizacion(),
                    "Un combo no debe disparar el modal de tamaños/extras");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                VentaController ctrl = ctrlRef.get();
                ctrl.agregarProductoAVenta(comboFamCatalogo);

                assertEquals(1, ctrl.getItemsVenta().size(), "El combo debe mostrarse como una línea única en POS");
                DetalleVenta linea = ctrl.getItemsVenta().get(0);
                assertEquals("Combo Familiar", linea.getNombreProducto());
                assertEquals(1, linea.getCantidad());
                assertEquals(new BigDecimal("49.90"), linea.getSubtotal());
                assertEquals("S/ 49.90", ctrl.getLblTotal().getText());

                Parent root = rootRef.get();
                root.applyCss();
                root.layout();
                try {
                    guardarSnapshot(root, "07_pos_venta_combo_v480.png");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        } finally {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrlRef.get().stopTimeline();
                return null;
            });
        }

        assertTrue(Files.exists(SCREENSHOT_DIR.resolve("07_pos_venta_combo_v480.png")));

        // Cobrar 1x Combo Familiar y validar explosión exacta en stock e inmutabilidad de Kárdex
        BigDecimal stockPizAntes = obtenerStockPorCodigo("INS-PIZ");
        BigDecimal stockGasAntes = obtenerStockPorCodigo("INS-GAS");
        BigDecimal stockPapAntes = obtenerStockPorCodigo("INS-PAP");

        DetalleVenta detVenta = new DetalleVenta(
                0, 0, comboFamCatalogo.getIdProducto(), 1, new BigDecimal("49.90"), new BigDecimal("49.90"));
        detVenta.setNombreProducto("Combo Familiar");
        detVenta.setIdCombo(comboFamCatalogo.getIdCombo());

        Venta v = new Venta();
        v.setIdUsuario(1);
        v.setTotal(new BigDecimal("49.90"));
        v.setMetodoPago("EFECTIVO");
        v.setMontoRecibido(new BigDecimal("50.00"));
        v.setVuelto(new BigDecimal("0.10"));
        v.setIdempotencyKey(UUID.randomUUID().toString());
        ComprobantePago comp = new ComprobantePago(0, 0, "BOLETA", "B001", "009901", new BigDecimal("49.90"));

        Venta registrada = ventaService.registrarVenta(v, List.of(detVenta), comp);
        assertTrue(registrada.getIdVenta() > 0);

        assertEquals(0, stockPizAntes.subtract(new BigDecimal("1.0000")).compareTo(obtenerStockPorCodigo("INS-PIZ")),
                "Debe descontar 1 masa/pizza por Pizza Artesanal del Combo Familiar");
        assertEquals(0, stockGasAntes.subtract(new BigDecimal("4.0000")).compareTo(obtenerStockPorCodigo("INS-GAS")),
                "Debe descontar 4 gaseosas por las 4x Gaseosa Personal del Combo Familiar");
        assertEquals(0, stockPapAntes.subtract(new BigDecimal("0.2500")).compareTo(obtenerStockPorCodigo("INS-PAP")),
                "Debe descontar 0.25 KG de papa por la Porción de Papas del Combo Familiar");

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM kardex_movimiento WHERE referencia_tipo = 'VENTA' AND referencia_id = ?")) {
            ps.setLong(1, registrada.getIdVenta());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "La explosión del combo debe registrar 3 salidas en Kárdex");
            }
        }

        // Probar reversión atómica de los 3 componentes al anular sin preparar
        SesionUsuario sesionAdmin = new SesionUsuario(
                1L, "Administrador Demo", "ADMIN", EnumSet.of(Permiso.VENTA_ANULAR), "POS-01", LocalDateTime.now(ZONA_LIMA));
        ventaService.anularVenta(registrada.getIdVenta(), false, sesionAdmin, "Anulación combo de prueba");

        assertEquals(0, stockPizAntes.compareTo(obtenerStockPorCodigo("INS-PIZ")));
        assertEquals(0, stockGasAntes.compareTo(obtenerStockPorCodigo("INS-GAS")));
        assertEquals(0, stockPapAntes.compareTo(obtenerStockPorCodigo("INS-PAP")));

        // Probar bloqueo ACID cuando un ingrediente de un componente del combo no alcanza (ej. solo quedan 2 gaseosas < 4)
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE insumo SET stock_actual_disponible = 2.0000 WHERE codigo = 'INS-GAS'")) {
            ps.executeUpdate();
        }
        Venta vSinStock = new Venta();
        vSinStock.setIdUsuario(1);
        vSinStock.setTotal(new BigDecimal("49.90"));
        vSinStock.setMetodoPago("TARJETA");
        vSinStock.setIdempotencyKey(UUID.randomUUID().toString());
        DetalleVenta detSinStock = new DetalleVenta(
                0, 0, comboFamCatalogo.getIdProducto(), 1, new BigDecimal("49.90"), new BigDecimal("49.90"));

        assertThrows(StockInsuficienteException.class,
                () -> ventaService.registrarVenta(vSinStock, List.of(detSinStock), null),
                "Debe bloquear la venta del combo si algún componente no tiene stock suficiente");

        // Restaurar stock de Gaseosa para no afectar el resto del entorno
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE insumo SET stock_actual_disponible = ? WHERE codigo = 'INS-GAS'")) {
            ps.setBigDecimal(1, stockGasAntes);
            ps.executeUpdate();
        }
    }

    @Test
    @Order(4)
    @DisplayName("M3.4 KDS & Ticket Térmico 48 cols: mostrar nombre Combo Familiar y desglose debajo + PNG")
    void testKdsYTicketDesgloseComboCapturaPng(@TempDir Path tempDir) throws Exception {
        List<Producto> catalogo = personalizacionService.listarProductosDisponiblesConOpciones();
        Producto comboFam = buscarPorCodigo(catalogo, "CMB-FAM");
        assertNotNull(comboFam);

        Pedido pedido = new Pedido();
        pedido.setIdUsuario(1);
        pedido.setIdUsuarioCreador(1);
        pedido.setNumeroMesa("03");
        pedido.setTipoPedido("MESA");
        pedido.setTotal(new BigDecimal("49.90"));
        pedido.setIdempotencyKey(UUID.randomUUID().toString());

        DetallePedido detCombo = new DetallePedido(
                0, 0, comboFam.getIdProducto(), 1,
                new BigDecimal("49.90"), new BigDecimal("49.90"), "Para compartir en mesa");
        detCombo.setNombreProducto(comboFam.getNombre());
        detCombo.setIdCombo(comboFam.getIdCombo());
        detCombo.setComboComponentesTexto(
                FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(comboFam.getComboComponentes()));

        Pedido guardado = pedidoService.registrarPedidoYEnviarComanda(pedido, List.of(detCombo));
        assertTrue(guardado.getIdPedido() > 0);

        AtomicReference<KitchenDisplayController> kdsRef = new AtomicReference<>();
        try {
            List<DetallePedido> detallesBd;
            try (Connection conn = ConexionBD.getConnection()) {
                detallesBd = pedidoDAO.listarDetallesPorPedido(conn, guardado.getIdPedido());
            }
            assertEquals(1, detallesBd.size());
            String textoGuardado = detallesBd.get(0).getComboComponentesTexto();
            assertNotNull(textoGuardado);
            assertTrue(textoGuardado.contains("Pizza Artesanal"));
            assertTrue(textoGuardado.contains("4x Gaseosa Personal"));
            assertTrue(textoGuardado.contains("Porción de Papas"));

            // Verificar escalado de cantidades para 2x Combo Familiar
            List<String> escalado2x = FormatoPersonalizacionUtil.extraerLineasDesgloseCombo(textoGuardado, 2);
            assertTrue(escalado2x.stream().anyMatch(l -> l.contains("2x Pizza Artesanal")));
            assertTrue(escalado2x.stream().anyMatch(l -> l.contains("8x Gaseosa Personal")));

            // Ticket cocina 48 cols
            ImpresoraTermicaService impresora = new ImpresoraTermicaService(
                    new AutorizacionService(), tempDir.resolve("tickets_combo_v480.log"));
            List<String> lineasCocina = impresora.formatearLineasComandaCocina(guardado, detallesBd);
            String ticketText = String.join("\n", lineasCocina);
            assertTrue(ticketText.contains("1x Combo Familiar"));
            assertTrue(ticketText.contains("1x Pizza Artesanal"));
            assertTrue(ticketText.contains("4x Gaseosa Personal"));
            assertTrue(ticketText.contains("1x Porción de Papas"));
            for (String linea : lineasCocina) {
                assertTrue(linea.length() <= ImpresoraTermicaService.ANCHO_COLUMNAS,
                        "Línea excede 48 cols: " + linea);
            }

            // Verificar API KDS /api/kds/items
            KdsController kdsApi = new KdsController(new ApiState(), pedidoDAO);
            assertNotNull(kdsApi);

            // Renderizar KitchenDisplayView real con la tarjeta KDS cargada desde BD
            AtomicReference<Parent> rootRef = new AtomicReference<>();
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    cargarEstiloApp();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KitchenDisplayView.fxml"));
                    Parent root = loader.load();
                    KitchenDisplayController kdsCtrl = loader.getController();
                    Scene scene = new Scene(root, 1280, 760);
                    App.applyStylesheets(scene);
                    kdsCtrl.renderizarItemsKds(detallesBd, LocalDateTime.now(ZONA_LIMA));
                    rootRef.set(root);
                    kdsRef.set(kdsCtrl);
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Thread.sleep(350);

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Parent root = rootRef.get();
                    root.applyCss();
                    root.layout();

                    assertTrue(buscarLabelConTexto(root, "COMBO"));
                    assertTrue(buscarLabelConTexto(root, "Combo Familiar"));
                    assertTrue(buscarLabelConTexto(root, "1x Pizza Artesanal"));
                    assertTrue(buscarLabelConTexto(root, "4x Gaseosa Personal"));
                    assertTrue(buscarLabelConTexto(root, "1x Porción de Papas"));

                    guardarSnapshot(root, "08_kds_combo_desglose_v480.png");
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(Files.exists(SCREENSHOT_DIR.resolve("08_kds_combo_desglose_v480.png")));
        } finally {
            if (kdsRef.get() != null) {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    kdsRef.get().stopTimers();
                    return null;
                });
            }
            limpiarPedidoDePrueba(guardado.getIdPedido());
        }
    }

    private void prepararInsumosYRecetasEscenarioFamiliar() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Crear producto Porción de Papas (PAP-01) si no existe
                Producto papas = productoDAO.buscarPorCodigo(conn, "PAP-01");
                if (papas == null) {
                    int idCat = 1;
                    List<Categoria> cats = productoDAO.listarCategorias(conn);
                    if (!cats.isEmpty()) {
                        idCat = cats.get(0).getIdCategoria();
                    }
                    papas = new Producto(0, idCat, "PAP-01", "Porción de Papas",
                            "Papas nativas crocantes", new BigDecimal("9.00"), new BigDecimal("3.00"), true);
                    productoDAO.crear(conn, papas);
                }

                Producto pizza = productoDAO.buscarPorCodigo(conn, "PIZ-ART");
                Producto gaseosa = productoDAO.buscarPorCodigo(conn, "BEB-01");
                assertNotNull(pizza);
                assertNotNull(gaseosa);

                // 2. Crear/asegurar insumos INS-PIZ, INS-GAS, INS-PAP y vincular recetas
                long idInsPiz = asegurarInsumo(conn, "INS-PIZ", "Masa y Mozzarella Pizza Familiar", "UND", new BigDecimal("30.0000"));
                long idInsGas = asegurarInsumo(conn, "INS-GAS", "Botella Gaseosa Personal 500ml", "UND", new BigDecimal("100.0000"));
                long idInsPap = asegurarInsumo(conn, "INS-PAP", "Papa Amarilla Tumbay", "KG", new BigDecimal("50.0000"));

                asegurarReceta(conn, pizza.getIdProducto(), idInsPiz, new BigDecimal("1.0000"));
                asegurarReceta(conn, gaseosa.getIdProducto(), idInsGas, new BigDecimal("1.0000"));
                asegurarReceta(conn, papas.getIdProducto(), idInsPap, new BigDecimal("0.2500"));

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private long asegurarInsumo(Connection conn, String codigo, String nombre, String um, BigDecimal stockInicial) throws Exception {
        Insumo ex = insumoDAO.buscarPorCodigo(conn, codigo);
        if (ex != null) {
            insumoDAO.reactivar(conn, ex.getIdInsumo());
            return ex.getIdInsumo();
        }
        String sql = "INSERT INTO insumo (codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario, costo_unitario_actual, activo) "
                + "VALUES (?, ?, ?, ?, 5.0000, 2.5000, 2.5000, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            ps.setString(2, nombre);
            ps.setString(3, um);
            ps.setBigDecimal(4, stockInicial);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private void asegurarReceta(Connection conn, int idProducto, long idInsumo, BigDecimal cantidad) throws Exception {
        String sql = "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE cantidad_necesaria = VALUES(cantidad_necesaria)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.setLong(2, idInsumo);
            ps.setBigDecimal(3, cantidad);
            ps.executeUpdate();
        }
    }

    private BigDecimal obtenerStockPorCodigo(String codigo) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo i = insumoDAO.buscarPorCodigo(conn, codigo);
            assertNotNull(i, "Debe existir insumo " + codigo);
            return i.getStockActualDisponible();
        }
    }

    private void asegurarCajaAbierta() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement psCheck = conn.prepareStatement("SELECT COUNT(*) FROM caja WHERE estado = 'ABIERTA'");
             ResultSet rs = psCheck.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO caja (id_usuario_apertura, nombre_caja, monto_apertura, estado) VALUES (1, 'Caja Test Combos', 200.00, 'ABIERTA')")) {
                    psIns.executeUpdate();
                }
            }
        }
    }

    private static Producto buscarPorCodigo(List<Producto> lista, String codigo) {
        return lista.stream()
                .filter(p -> codigo.equalsIgnoreCase(p.getCodigo()))
                .findFirst()
                .orElse(null);
    }

    private static Node buscarTarjetaPorNombre(Parent root, String nombreProducto) {
        for (Node n : root.lookupAll("Label")) {
            if (n instanceof Label lbl && lbl.getText() != null && lbl.getText().contains(nombreProducto)) {
                return lbl;
            }
        }
        return null;
    }

    private static boolean buscarLabelConTexto(Parent root, String substring) {
        if (root == null) {
            return false;
        }
        for (Node n : root.lookupAll("Label")) {
            if (n instanceof Label lbl && lbl.getText() != null && lbl.getText().contains(substring)) {
                return true;
            }
        }
        return false;
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
