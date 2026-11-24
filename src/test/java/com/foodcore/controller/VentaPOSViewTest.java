package com.foodcore.controller;

import com.foodcore.controller.venta.DefaultVentaCobroCoordinator;
import com.foodcore.controller.venta.FakeVentaPosInteractionPort;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Insumo;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite TestFX / JavaFX de la vista Punto de Venta (POS): agregar ítem, cobro con F5,
 * deshabilitación del botón Cobrar durante transacción (anti-doble cobro) y limpieza con Esc
 * según SPEC-FOODCORE-v1.0 (§15.3, §19.5, §19.12; REMEDIATION R-03, B6).
 */
class VentaPOSViewTest {

    private int idProductoPos;

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();

        CajaServiceImpl cajaService = new CajaServiceImpl(new JdbcTransactionManager(), new CajaDAOImpl());
        cajaService.abrirTurnoCaja(1, "Caja POS TestFX", new BigDecimal("100.00"));

        InsumoDAOImpl insumoDAO = new InsumoDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            long idInsumo = insumoDAO.crear(conn, new Insumo(
                0L, "Pechuga Pollo Crocante", "UNIDAD",
                new BigDecimal("20.0000"), new BigDecimal("2.0000"), new BigDecimal("8.00")));
            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES ('Pollos', 'POS')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.executeUpdate();
                int idCat;
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    rs.next();
                    idCat = rs.getInt(1);
                }
                try (PreparedStatement psProd = conn.prepareStatement(
                        "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, 'Combo Pollo Crispy', 25.00, 50)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    psProd.setInt(1, idCat);
                    psProd.executeUpdate();
                    try (ResultSet rsP = psProd.getGeneratedKeys()) {
                        rsP.next();
                        idProductoPos = rsP.getInt(1);
                    }
                }
            }
            try (PreparedStatement psRec = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, 1.0000)")) {
                psRec.setInt(1, idProductoPos);
                psRec.setLong(2, idInsumo);
                psRec.executeUpdate();
            }
        }
    }

    private Producto crearProductoPrueba() {
        return new Producto(
            idProductoPos, 1, "POL-01", "Combo Pollo Crispy",
            "Crujiente", new BigDecimal("25.00"), new BigDecimal("50"), true
        );
    }

    private static void esperarHastaEnFx(BooleanSupplier condicionFx, Duration timeout, String mensajeFallo)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean cumplida = new AtomicBoolean(false);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> cumplida.set(condicionFx.getAsBoolean()));
            if (cumplida.get()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timeout esperando condición asíncrona en FX: " + mensajeFallo);
    }

    @Test
    @DisplayName("B6.6: testAgregarProductoAVenta_ActualizaTotalesYCarrito - Añadir producto actualiza items y label total")
    void testAgregarProductoAVenta_ActualizaTotalesYCarrito() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1100, 720);

                VentaController controller = loader.getController();
                try {
                    controller.agregarProductoAVenta(crearProductoPrueba());

                    assertEquals(1, controller.getItemsVenta().size());
                    Label lblTotal = (Label) root.lookup("#lblTotal");
                    assertEquals("S/ 25.00", lblTotal.getText());
                } finally {
                    controller.stopTimeline();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.7: testBotonCobrarDeshabilitado_BloqueaDobleCobro - Bloqueo estricto durante transacción en curso")
    void testBotonCobrarDeshabilitado_BloqueaDobleCobro() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1100, 720);

                VentaController controller = loader.getController();
                try {
                    AtomicInteger aperturasCobro = new AtomicInteger();
                    AtomicBoolean btnDeshabilitadoDuranteCobro = new AtomicBoolean(false);
                    FakeVentaPosInteractionPort fakePort = new FakeVentaPosInteractionPort();
                    fakePort.setCobroHandler(solicitud -> {
                        aperturasCobro.incrementAndGet();
                        btnDeshabilitadoDuranteCobro.set(controller.getBtnEfectivo().isDisabled());
                        // Intento reentrante de doble cobro mientras la transacción está en curso
                        controller.handleCobroEfectivo(null);
                    });
                    controller.setInteractionPort(fakePort);
                    controller.agregarProductoAVenta(crearProductoPrueba());

                    controller.handleCobroEfectivo(null);
                    assertEquals(1, aperturasCobro.get(),
                            "El segundo intento de cobro reentrante debe ser completamente ignorado");
                    assertTrue(btnDeshabilitadoDuranteCobro.get(),
                            "El botón Cobrar debe figurar deshabilitado durante la transacción en curso");
                    assertEquals(1, controller.getItemsVenta().size());
                    assertFalse(controller.getBtnEfectivo().isDisabled(),
                            "Al finalizar la operación los botones deben rehabilitarse");
                } finally {
                    controller.stopTimeline();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.8: testCobroExitosoConTeclaF5_RegistraVentaYVaciaCarrito - Atajo F5 procesa cobro")
    void testCobroExitosoConTeclaF5_RegistraVentaYVaciaCarrito() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1100, 720);

                VentaController controller = loader.getController();
                try {
                    FakeVentaPosInteractionPort fakePort = new FakeVentaPosInteractionPort();
                    fakePort.configurarCobroDirecto(
                            new DefaultVentaCobroCoordinator(new VentaService(), new AutorizacionService()));
                    controller.setInteractionPort(fakePort);
                    controller.registrarAtajosTeclado();
                    controller.agregarProductoAVenta(crearProductoPrueba());

                    KeyEvent f5Event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F5,
                            false, false, false, false);
                    scene.getRoot().fireEvent(f5Event);

                    assertTrue(controller.getItemsVenta().isEmpty(),
                            "El carrito debe vaciarse al completar la venta con F5");
                } finally {
                    controller.stopTimeline();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM venta")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "Debe haberse registrado exactamente 1 venta");
        }
    }

    @Test
    @DisplayName("B6.9: testTeclaEscape_LimpiaCarritoConConfirmacion - Atajo Esc vacía el carrito tras confirmación")
    void testTeclaEscape_LimpiaCarritoConConfirmacion() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1100, 720);

                VentaController controller = loader.getController();
                try {
                    FakeVentaPosInteractionPort fakePort = new FakeVentaPosInteractionPort();
                    fakePort.setConfirmarLimpieza(true);
                    controller.setInteractionPort(fakePort);
                    controller.registrarAtajosTeclado();
                    controller.agregarProductoAVenta(crearProductoPrueba());
                    assertEquals(1, controller.getItemsVenta().size());

                    KeyEvent escEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                            false, false, false, false);
                    scene.getRoot().fireEvent(escEvent);

                    assertTrue(controller.getItemsVenta().isEmpty(), "La tecla Esc debe vaciar el carrito");
                } finally {
                    controller.stopTimeline();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("v4.6.0-F1.1a: POS al agregar ítem a mesa ocupada recalcula total acumulado y refresca lista completa")
    void testAgregarItemsAMesaOcupadaAcumulaTotalYListaCompletaEnPOS() throws Exception {
        int idSegundoProd;
        try (Connection conn = ConexionBD.getConnection()) {
            try (Statement stPromo = conn.createStatement()) {
                stPromo.executeUpdate("UPDATE promocion SET activo = FALSE");
            }
            try (PreparedStatement psProd = conn.prepareStatement(
                     "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) "
                     + "SELECT id_categoria, 'Limonada Frozen', 18.00, 50 FROM categoria LIMIT 1",
                     Statement.RETURN_GENERATED_KEYS)) {
                psProd.executeUpdate();
                try (ResultSet rs = psProd.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    idSegundoProd = rs.getInt(1);
                }
            }
        }

        PedidoService pedidoService = new PedidoService();
        Pedido pedido = new Pedido(0, 1, null, "02", "REGISTRADO", new BigDecimal("25.00"));
        DetallePedido itemInicial = new DetallePedido(0, 0, idProductoPos, "Combo Pollo Crispy", 1,
            new BigDecimal("25.00"), new BigDecimal("25.00"), "");
        Pedido creado = pedidoService.crearPedido(pedido, List.of(itemInicial));

        Producto segundoProducto = new Producto(
            idSegundoProd, 1, "BEB-01", "Limonada Frozen",
            "Refrescante", new BigDecimal("18.00"), new BigDecimal("50"), true
        );

        AtomicReference<VentaController> ctrlRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                    Parent root = loader.load();
                    new Scene(root, 1100, 720);

                    VentaController controller = loader.getController();
                    ctrlRef.set(controller);
                    controller.refrescarMesasYCanalesSincrono();
                    controller.seleccionarMesa("02");

                    assertEquals(1, controller.getItemsVenta().size());
                    assertEquals("S/ 25.00", controller.getLblTotal().getText());

                    controller.agregarProductoAVenta(segundoProducto);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            VentaController ctrl = ctrlRef.get();
            esperarHastaEnFx(() -> !ctrl.isOperacionPedidoEnProgreso() && ctrl.getItemsVenta().size() == 2,
                    Duration.ofSeconds(5), "Debe agregar segundo ítem al pedido existente de forma asíncrona");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertEquals(2, ctrl.getItemsVenta().size(), "La tabla POS debe mostrar los 2 ítems del pedido");
                assertEquals(2, ctrl.getTblDetalle().getItems().size());
                assertEquals("S/ 43.00", ctrl.getLblTotal().getText(),
                        "El Label total en POS debe mostrar la suma acumulada (25 + 18 = 43.00)");
                ctrl.agregarProductoAVenta(crearProductoPrueba());
            });

            esperarHastaEnFx(() -> !ctrl.isOperacionPedidoEnProgreso() && ctrl.getItemsVenta().size() == 3,
                    Duration.ofSeconds(5), "Debe agregar tercer ítem al pedido existente de forma asíncrona");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertEquals(3, ctrl.getItemsVenta().size(), "Debe incluir también el tercer ítem");
                assertEquals("S/ 68.00", ctrl.getLblTotal().getText());

                ctrl.refrescarMesasYCanalesSincrono();
                ctrl.seleccionarMesa("02");
                assertEquals(3, ctrl.getItemsVenta().size(), "Al recargar mesa deben permanecer los 3 ítems");
                assertEquals("S/ 68.00", ctrl.getLblTotal().getText());
            });
        } finally {
            if (ctrlRef.get() != null) {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrlRef.get().stopTimeline());
            }
        }

        assertEquals(0, new BigDecimal("68.00").compareTo(pedidoService.calcularTotal(creado.getIdPedido())));
    }

    @Test
    @DisplayName("v4.6.0-F1.1b: MozoOrderController precarga pedido existente y acumula ítems sin perder los previos")
    void testMozoAgregarPlatosAPedidoExistenteMuestraListaCompletaYTotalAcumulado() throws Exception {
        int idSegundoProd;
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement psProd = conn.prepareStatement(
                     "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) "
                     + "SELECT id_categoria, 'Papas Supremas', 14.00, 50 FROM categoria LIMIT 1",
                     Statement.RETURN_GENERATED_KEYS)) {
            psProd.executeUpdate();
            try (ResultSet rs = psProd.getGeneratedKeys()) {
                assertTrue(rs.next());
                idSegundoProd = rs.getInt(1);
            }
        }

        PedidoService pedidoService = new PedidoService();
        Pedido pedido = new Pedido(0, 1, null, "03", "REGISTRADO", new BigDecimal("25.00"));
        DetallePedido itemInicial = new DetallePedido(0, 0, idProductoPos, "Combo Pollo Crispy", 1,
            new BigDecimal("25.00"), new BigDecimal("25.00"), "");
        Pedido creado = pedidoService.crearPedido(pedido, List.of(itemInicial));

        Producto segundoProducto = new Producto(
            idSegundoProd, 1, "COM-01", "Papas Supremas",
            "Crujientes", new BigDecimal("14.00"), new BigDecimal("50"), true
        );

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MozoOrderView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1060, 720);

                MozoOrderController controller = loader.getController();
                controller.cargarPedidoExistente(creado);

                assertEquals(1, controller.getItems().size(), "Debe precargar los ítems previos del pedido");
                assertEquals("Total: S/ 25.00", controller.getLblTotal().getText());

                controller.agregarPlato(segundoProducto);
                assertEquals(2, controller.getItems().size(), "Debe mostrar ítem previo + nuevo ítem");
                assertEquals(2, controller.getTblItems().getItems().size());
                assertEquals("Total: S/ 39.00", controller.getLblTotal().getText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
