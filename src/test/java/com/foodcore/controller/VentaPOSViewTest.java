package com.foodcore.controller;

import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite TestFX / JavaFX de la vista Punto de Venta (POS): agregar ítem, cobro con F5,
 * deshabilitación del botón Cobrar durante transacción (anti-doble cobro) y limpieza con Esc
 * según SPEC-FOODCORE-v1.0 (§15.3, §19.5, §19.12; REMEDIATION R-03).
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

    @Test
    @DisplayName("R-03 (1/2): VentaPOSView -> agregar ítem, cobrar con F5, botón Cobrar deshabilitado durante transacción (sin doble cobro)")
    void debeAgregarItemCobrarConF5YEvitarDobleCobroCuandoBotonEstaDeshabilitado() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1100, 720);

                VentaController controller = loader.getController();
                controller.setModoCobroDirectoTest(true);
                controller.registrarAtajosTeclado();

                Producto prod = new Producto(
                    idProductoPos, 1, "POL-01", "Combo Pollo Crispy",
                    "Crujiente", new BigDecimal("25.00"), new BigDecimal("50"), true
                );

                // 1. Agregar ítem al carrito
                controller.agregarProductoAVenta(prod);
                assertEquals(1, controller.getItemsVenta().size());

                Label lblTotal = (Label) root.lookup("#lblTotal");
                assertEquals("S/ 25.00", lblTotal.getText());

                // 2. Verificar que durante una transacción en curso el botón Cobrar se deshabilita e ignora clic duplicado
                controller.simularEstadoTransaccionEnCurso(true);
                assertTrue(controller.getBtnEfectivo().isDisabled(),
                    "El botón Cobrar debe estar deshabilitado mientras la transacción está en curso");
                controller.handleCobroEfectivo(null); // Debe ignorarse sin duplicar cobro
                assertEquals(1, controller.getItemsVenta().size(), "El carrito sigue intacto porque el segundo clic fue bloqueado");

                // 3. Liberar estado simulado y disparar tecla F5 para cobrar
                controller.simularEstadoTransaccionEnCurso(false);
                assertFalse(controller.getBtnEfectivo().isDisabled());

                KeyEvent f5Event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F5, false, false, false, false);
                scene.getRoot().fireEvent(f5Event);

                assertTrue(controller.getItemsVenta().isEmpty(), "El carrito debe vaciarse tras completar el cobro con F5");
                assertEquals("S/ 0.00", lblTotal.getText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM venta")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "Debe haberse registrado exactamente 1 venta (sin duplicados)");
        }
    }

    @Test
    @DisplayName("R-03 (2/2): VentaPOSView -> tecla Esc limpia el carrito con confirmación")
    void debeLimpiarCarritoAlPresionarEscapeConConfirmacion() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1100, 720);

                VentaController controller = loader.getController();
                controller.setConfirmacionEscapeAutomatica(true);
                controller.registrarAtajosTeclado();

                Producto prod = new Producto(
                    idProductoPos, 1, "POL-01", "Combo Pollo Crispy",
                    "Crujiente", new BigDecimal("25.00"), new BigDecimal("50"), true
                );
                controller.agregarProductoAVenta(prod);
                assertEquals(1, controller.getItemsVenta().size());

                KeyEvent escEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
                scene.getRoot().fireEvent(escEvent);

                assertTrue(controller.getItemsVenta().isEmpty(), "La tecla Esc confirmada debe limpiar los ítems del carrito");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
