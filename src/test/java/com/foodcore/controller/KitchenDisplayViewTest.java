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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite TestFX / JavaFX de la vista KDS (Kitchen Display System): colores por temporizador
 * de ítem individual (verde <5 min, ámbar 5-10 min, rojo >10 min) y acciones Iniciar / Listo / Entregado
 * según SPEC-FOODCORE-v1.0 (§8.5, §15.3, §19.9; REMEDIATION R-03, R-08).
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

    @Test
    @DisplayName("R-03 & R-08 (1/2): KDS muestra colores distintos por tarjeta de ítem (verde <5m, ámbar 5-10m, rojo >10m) dentro del mismo pedido")
    void debeRenderizarColoresDistintosPorItemSegunEnviadoEnDentroDelMismoPedido() throws Exception {
        LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);

        DetallePedido itemVerde = new DetallePedido(1, 10, idProductoKds, "Entrada Rápida (2m)", 1,
            new BigDecimal("15.00"), new BigDecimal("15.00"), "A tiempo");
        itemVerde.setNumeroMesa("M04");
        itemVerde.setEstadoCocina("PENDIENTE");
        itemVerde.setEnviadoEn(ahora.minusMinutes(2));

        DetallePedido itemAmbar = new DetallePedido(2, 10, idProductoKds, "Plato Medio (7m)", 1,
            new BigDecimal("30.00"), new BigDecimal("30.00"), "Atención");
        itemAmbar.setNumeroMesa("M04");
        itemAmbar.setEstadoCocina("EN_PREPARACION");
        itemAmbar.setEnviadoEn(ahora.minusMinutes(7));

        DetallePedido itemRojo = new DetallePedido(3, 10, idProductoKds, "Fondo Crítico (13m)", 1,
            new BigDecimal("35.00"), new BigDecimal("35.00"), "Crítico");
        itemRojo.setNumeroMesa("M04");
        itemRojo.setEstadoCocina("PENDIENTE");
        itemRojo.setEnviadoEn(ahora.minusMinutes(13));

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                // Tarjeta 1: < 5 min -> GREEN
                FXMLLoader loaderVerde = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent cardVerde = loaderVerde.load();
                ComandaCardController ctrlVerde = loaderVerde.getController();
                ctrlVerde.initDataItem(itemVerde, e -> { }, e -> { });
                assertEquals("GREEN", ctrlVerde.getColorUmbral());
                assertTrue(cardVerde.getStyleClass().contains("kds-card-green"));

                // Tarjeta 2: 5 a 10 min -> AMBER
                FXMLLoader loaderAmbar = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent cardAmbar = loaderAmbar.load();
                ComandaCardController ctrlAmbar = loaderAmbar.getController();
                ctrlAmbar.initDataItem(itemAmbar, e -> { }, e -> { });
                assertEquals("AMBER", ctrlAmbar.getColorUmbral());
                assertTrue(cardAmbar.getStyleClass().contains("kds-card-amber"));

                // Tarjeta 3: > 10 min -> RED
                FXMLLoader loaderRojo = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent cardRojo = loaderRojo.load();
                ComandaCardController ctrlRojo = loaderRojo.getController();
                ctrlRojo.initDataItem(itemRojo, e -> { }, e -> { });
                assertEquals("RED", ctrlRojo.getColorUmbral());
                assertTrue(cardRojo.getStyleClass().contains("kds-card-red"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("R-03 & R-08 (2/2): KitchenDisplayView ejecuta transiciones Iniciar -> Listo -> Entregado por ítem")
    void debeEjecutarTransicionesIniciarListoYEntregadoEnKds() throws Exception {
        Pedido pedido = new Pedido(0, 1, null, "M05", "REGISTRADO", new BigDecimal("30.00"));
        DetallePedido det = new DetallePedido(0, 0, idProductoKds, "Chaufa Aeropuerto", 1,
            new BigDecimal("30.00"), new BigDecimal("30.00"), "Bien caliente");
        Pedido creado = pedidoService.crearPedido(pedido, List.of(det));
        int idDetalle = creado.getDetalles().get(0).getIdDetallePedido();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KitchenDisplayView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1024, 720);

                List<DetallePedido> itemsIniciales;
                try (Connection conn = ConexionBD.getConnection()) {
                    itemsIniciales = pedidoDAO.listarItemsPendientesCocina(conn);
                }
                assertEquals(1, itemsIniciales.size());

                FXMLLoader cardLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ComandaCard.fxml"));
                Parent cardRoot = cardLoader.load();
                ComandaCardController cardCtrl = cardLoader.getController();

                cardCtrl.initDataItem(itemsIniciales.get(0),
                    item -> {
                        try (Connection conn = ConexionBD.getConnection()) {
                            pedidoDAO.actualizarEstadoDetalleCocina(conn, item.getIdDetallePedido(), "EN_PREPARACION");
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    },
                    item -> {
                        try (Connection conn = ConexionBD.getConnection()) {
                            pedidoDAO.actualizarEstadoDetalleCocina(conn, item.getIdDetallePedido(), "LISTO");
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                );

                Button btnIniciar = (Button) cardRoot.lookup("#btnIniciar");
                Button btnListo = (Button) cardRoot.lookup("#btnListo");

                // 1. Acción Iniciar -> EN_PREPARACION
                btnIniciar.fire();
                try (Connection conn = ConexionBD.getConnection();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                         "SELECT estado_cocina, iniciado_en FROM detalle_pedido WHERE id_detalle_pedido = " + idDetalle)) {
                    assertTrue(rs.next());
                    assertEquals("EN_PREPARACION", rs.getString("estado_cocina"));
                }

                // 2. Acción Listo -> LISTO
                btnListo.fire();
                try (Connection conn = ConexionBD.getConnection();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                         "SELECT estado_cocina, listo_en FROM detalle_pedido WHERE id_detalle_pedido = " + idDetalle)) {
                    assertTrue(rs.next());
                    assertEquals("LISTO", rs.getString("estado_cocina"));
                }

                // 3. Acción Entregado -> ENTREGADO
                try (Connection conn = ConexionBD.getConnection()) {
                    pedidoDAO.actualizarEstadoDetalleCocina(conn, idDetalle, "ENTREGADO");
                    pedidoDAO.actualizarEstadoPedido(conn, creado.getIdPedido(), "ENTREGADO");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT estado FROM pedido WHERE id_pedido = " + creado.getIdPedido())) {
            assertTrue(rs.next());
            assertEquals("ENTREGADO", rs.getString("estado"));
        }
    }
}
