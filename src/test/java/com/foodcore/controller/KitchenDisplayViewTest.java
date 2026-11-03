package com.foodcore.controller;

import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite TestFX / JavaFX de la vista KDS (Kitchen Display System): colores por temporizador
 * de ítem individual (verde <5 min, ámbar 5-10 min, rojo >10 min) y acciones Iniciar / Listo / Entregado
 * según SPEC-FOODCORE-v1.0 (§8.5, §15.3, §19.9; REMEDIATION R-03, R-08, B6).
 */
class KitchenDisplayViewTest {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private PedidoServiceImpl pedidoService;
    private PedidoDAOImpl pedidoDAO;
    private int idProductoKds;

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        pedidoDAO = new PedidoDAOImpl();
        pedidoService = new PedidoServiceImpl(new JdbcTransactionManager(), pedidoDAO, new AutorizacionService());

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement psCat = conn.prepareStatement(
                 "INSERT INTO categoria (nombre, descripcion) VALUES ('KDS Platos', 'Cocina')",
                 Statement.RETURN_GENERATED_KEYS)) {
            psCat.executeUpdate();
            int idCat;
            try (ResultSet rs = psCat.getGeneratedKeys()) {
                rs.next();
                idCat = rs.getInt(1);
            }
            try (PreparedStatement psProd = conn.prepareStatement(
                    "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, 'Chaufa Aeropuerto', 30.00, 100)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psProd.setInt(1, idCat);
                psProd.executeUpdate();
                try (ResultSet rsP = psProd.getGeneratedKeys()) {
                    rsP.next();
                    idProductoKds = rsP.getInt(1);
                }
            }
        }
    }

    private DetallePedido crearDetalle(int minutosAtras, String estadoCocina) {
        DetallePedido item = new DetallePedido(1, 10, idProductoKds, "Plato Test", 1,
            new BigDecimal("30.00"), new BigDecimal("30.00"), "Notas");
        item.setNumeroMesa("M04");
        item.setEstadoCocina(estadoCocina);
        item.setEnviadoEn(LocalDateTime.now(ZONA_LIMA).minusMinutes(minutosAtras));
        return item;
    }

    @Test
    @DisplayName("B6.10: testTemporizadorItem_VerdeMenosCincoMinutos - Ítem menor a 5 min tiene color GREEN y clase kds-card-green")
    void testTemporizadorItem_VerdeMenosCincoMinutos() throws Exception {
        DetallePedido itemVerde = crearDetalle(2, "PENDIENTE");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent card = loader.load();
                ComandaCardController ctrl = loader.getController();
                ctrl.initDataItem(itemVerde, e -> { }, e -> { });

                assertEquals("GREEN", ctrl.getColorUmbral());
                assertTrue(card.getStyleClass().contains("kds-card-green"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.11: testTemporizadorItem_AmbarCincoADiezMinutos - Ítem entre 5 y 10 min tiene color AMBER y clase kds-card-amber")
    void testTemporizadorItem_AmbarCincoADiezMinutos() throws Exception {
        DetallePedido itemAmbar = crearDetalle(7, "EN_PREPARACION");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent card = loader.load();
                ComandaCardController ctrl = loader.getController();
                ctrl.initDataItem(itemAmbar, e -> { }, e -> { });

                assertEquals("AMBER", ctrl.getColorUmbral());
                assertTrue(card.getStyleClass().contains("kds-card-amber"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.12: testTemporizadorItem_RojoMasDiezMinutos - Ítem mayor a 10 min tiene color RED y clase kds-card-red")
    void testTemporizadorItem_RojoMasDiezMinutos() throws Exception {
        DetallePedido itemRojo = crearDetalle(13, "PENDIENTE");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent card = loader.load();
                ComandaCardController ctrl = loader.getController();
                ctrl.initDataItem(itemRojo, e -> { }, e -> { });

                assertEquals("RED", ctrl.getColorUmbral());
                assertTrue(card.getStyleClass().contains("kds-card-red"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("B6.13: testAccionKds_IniciarPasaAEnPreparacion - Clic en Iniciar transiciona a EN_PREPARACION")
    void testAccionKds_IniciarPasaAEnPreparacion() throws Exception {
        Pedido pedido = new Pedido(0, 1, null, "M05", "REGISTRADO", new BigDecimal("30.00"));
        DetallePedido det = new DetallePedido(0, 0, idProductoKds, "Chaufa Aeropuerto", 1,
            new BigDecimal("30.00"), new BigDecimal("30.00"), "Bien caliente");
        Pedido creado = pedidoService.crearPedido(pedido, List.of(det));
        int idDetalle = creado.getDetalles().get(0).getIdDetallePedido();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader cardLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent cardRoot = cardLoader.load();
                ComandaCardController cardCtrl = cardLoader.getController();

                DetallePedido itemPendiente = creado.getDetalles().get(0);
                cardCtrl.initDataItem(itemPendiente,
                    item -> {
                        try (Connection conn = ConexionBD.getConnection()) {
                            pedidoDAO.actualizarEstadoDetalleCocina(conn, item.getIdDetallePedido(), "EN_PREPARACION");
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    },
                    item -> { }
                );

                Button btnIniciar = (Button) cardRoot.lookup("#btnIniciar");
                btnIniciar.fire();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT estado_cocina, iniciado_en FROM detalle_pedido WHERE id_detalle_pedido = " + idDetalle)) {
            assertTrue(rs.next());
            assertEquals("EN_PREPARACION", rs.getString("estado_cocina"));
            assertNotNull(rs.getTimestamp("iniciado_en"));
        }
    }

    @Test
    @DisplayName("B6.14: testAccionKds_ListoPasaAListo - Clic en Listo transiciona a LISTO")
    void testAccionKds_ListoPasaAListo() throws Exception {
        Pedido pedido = new Pedido(0, 1, null, "M05", "REGISTRADO", new BigDecimal("30.00"));
        DetallePedido det = new DetallePedido(0, 0, idProductoKds, "Chaufa Aeropuerto", 1,
            new BigDecimal("30.00"), new BigDecimal("30.00"), "Sin cebolla");
        Pedido creado = pedidoService.crearPedido(pedido, List.of(det));
        int idDetalle = creado.getDetalles().get(0).getIdDetallePedido();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader cardLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent cardRoot = cardLoader.load();
                ComandaCardController cardCtrl = cardLoader.getController();

                DetallePedido item = creado.getDetalles().get(0);
                cardCtrl.initDataItem(item,
                    it -> { },
                    it -> {
                        try (Connection conn = ConexionBD.getConnection()) {
                            pedidoDAO.actualizarEstadoDetalleCocina(conn, it.getIdDetallePedido(), "LISTO");
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                );

                Button btnListo = (Button) cardRoot.lookup("#btnListo");
                btnListo.fire();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT estado_cocina, listo_en FROM detalle_pedido WHERE id_detalle_pedido = " + idDetalle)) {
            assertTrue(rs.next());
            assertEquals("LISTO", rs.getString("estado_cocina"));
            assertNotNull(rs.getTimestamp("listo_en"));
        }
    }

    @Test
    @DisplayName("B6.15: testAccionKds_EntregadoCompletaDespacho - Transición a ENTREGADO completa ciclo")
    void testAccionKds_EntregadoCompletaDespacho() throws Exception {
        Pedido pedido = new Pedido(0, 1, null, "M05", "REGISTRADO", new BigDecimal("30.00"));
        DetallePedido det = new DetallePedido(0, 0, idProductoKds, "Chaufa Aeropuerto", 1,
            new BigDecimal("30.00"), new BigDecimal("30.00"), "Empacar");
        Pedido creado = pedidoService.crearPedido(pedido, List.of(det));
        int idDetalle = creado.getDetalles().get(0).getIdDetallePedido();

        try (Connection conn = ConexionBD.getConnection()) {
            pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetalle, "ENTREGADO");
            pedidoDAO.actualizarEstadoPedido(conn, creado.getIdPedido(), "ENTREGADO");
        }

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT estado FROM pedido WHERE id_pedido = " + creado.getIdPedido())) {
            assertTrue(rs.next());
            assertEquals("ENTREGADO", rs.getString("estado"));
        }
    }
}
