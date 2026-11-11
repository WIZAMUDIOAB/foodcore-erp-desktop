package com.foodcore.controller;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.api.ApiServer;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.SolicitudAusenciaDAO;
import com.foodcore.dao.impl.SolicitudAusenciaDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.support.TestSecuritySupport;
import com.foodcore.util.ConexionBD;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite integral de verificación para FOODCORE ERP v4.4.0:
 * 1. KDS no-destructivo (marcarEntregado mueve de LISTO a HISTORIAL sin borrar otros pedidos).
 * 2. POS Rediseñado (12 mesas, badges [MESA XX]/[KIOSCO]/[DELIVERY], carga en ticket, 35%/65%).
 * 3. Reportes Contables con tarjetas KPI pastel y valores monoespaciados.
 * 4. RRHH (Gestión de ausencias, Portal 3 tabs + Boleta PDF, Alertas 30 días en Dashboard, Sidebar).
 * 5. QA General (Sincronización de agotados across PWA/Kiosco/POS y RBAC de anulación/descuento).
 */
class FoodcoreV440FlowsTest {

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
        AuthService.setUsuarioSesionActual(null);
    }

    @AfterEach
    void tearDown() {
        AuthService.setUsuarioSesionActual(null);
    }

    @Test
    @DisplayName("v4.4.0 §1: KDS marcarEntregado mueve de LISTO a HISTORIAL sin borrar pedidos activos")
    void debeDespacharPedidoKdsSinEliminarOtrosPedidosActivos() throws Exception {
        AtomicReference<KitchenDisplayController> ctrlRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KitchenDisplayView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1280, 760);
                App.applyStylesheets(sc);

                KitchenDisplayController ctrl = loader.getController();
                ctrlRef.set(ctrl);

                LocalDateTime ahora = LocalDateTime.now(KitchenDisplayController.ZONA_LIMA);

                DetallePedido dPend = new DetallePedido(-91001, 1001, 1, "Ceviche Clásico", 1,
                        new BigDecimal("28.00"), new BigDecimal("28.00"), "");
                dPend.setNumeroMesa("02");
                dPend.setEstadoCocina("PENDIENTE");
                dPend.setEnviadoEn(ahora.minusMinutes(2));

                DetallePedido dPrep = new DetallePedido(-91002, 1002, 1, "Lomo Saltado", 1,
                        new BigDecimal("35.00"), new BigDecimal("35.00"), "");
                dPrep.setNumeroMesa("03");
                dPrep.setEstadoCocina("EN_PREPARACION");
                dPrep.setEnviadoEn(ahora.minusMinutes(6));

                DetallePedido dListo1 = new DetallePedido(-91003, 1003, 1, "Burger Royal", 2,
                        new BigDecimal("24.00"), new BigDecimal("48.00"), "");
                dListo1.setNumeroMesa("01");
                dListo1.setEstadoCocina("LISTO");
                dListo1.setEnviadoEn(ahora.minusMinutes(4));

                DetallePedido dListo2 = new DetallePedido(-91004, 1004, 1, "Alitas BBQ x8", 1,
                        new BigDecimal("22.00"), new BigDecimal("22.00"), "");
                dListo2.setNumeroMesa("T-104");
                dListo2.setEstadoCocina("LISTO");
                dListo2.setEnviadoEn(ahora.minusMinutes(5));

                ctrl.renderizarItemsKds(List.of(dPend, dPrep, dListo1, dListo2), ahora);

                assertEquals(1, ctrl.getVboxColPendiente().getChildren().size());
                assertEquals(1, ctrl.getVboxColEnPreparacion().getChildren().size());
                assertEquals(2, ctrl.getVboxColListo().getChildren().size());
                assertEquals(0, ctrl.getVboxColHistorial().getChildren().size());

                ctrl.marcarEntregado(1003);

                assertEquals(1, ctrl.getVboxColPendiente().getChildren().size(), "El pedido PENDIENTE debe permanecer intacto");
                assertEquals(1, ctrl.getVboxColEnPreparacion().getChildren().size(), "El pedido EN_PREPARACION debe permanecer intacto");
                assertEquals(1, ctrl.getVboxColListo().getChildren().size(), "Solo debe salir el pedido 1003 de LISTO; 1004 debe quedarse");
                assertEquals(1, ctrl.getVboxColHistorial().getChildren().size(), "El pedido 1003 debe pasar a la columna HISTORIAL");

                assertEquals("1", ctrl.getLblCountPendiente().getText());
                assertEquals("1", ctrl.getLblCountEnPreparacion().getText());
                assertEquals("1", ctrl.getLblCountListo().getText());
                assertEquals("1", ctrl.getLblCountHistorial().getText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (ctrlRef.get() != null) {
                    ctrlRef.get().stopTimers();
                }
            }
        });

        // Verificar que PedidoDAOImpl.listarItemsPendientesCocina incluye ítems en estado LISTO
        PedidoService pedidoService = new PedidoService();
        Pedido p = new Pedido();
        p.setIdUsuario(1);
        p.setNumeroMesa("05");
        p.setTipoPedido("MESA");
        p.setTotal(new BigDecimal("24.00"));
        DetallePedido det = new DetallePedido(0, 0, 1, "Hamburguesa", 1,
                new BigDecimal("24.00"), new BigDecimal("24.00"), "KDS test");
        Pedido creado = pedidoService.registrarPedidoYEnviarComanda(p, List.of(det));

        PedidoDAO pedidoDAO = new PedidoDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            pedidoDAO.actualizarEstadoDetalleCocina(conn, det.getIdDetallePedido(), "LISTO");
            pedidoDAO.actualizarEstadoPedido(conn, creado.getIdPedido(), "LISTO");

            List<DetallePedido> visibles = pedidoDAO.listarItemsPendientesCocina(conn);
            assertTrue(visibles.stream().anyMatch(i -> i.getIdDetallePedido() == det.getIdDetallePedido()),
                    "Los ítems en estado LISTO deben seguir visibles en el tablero hasta marcarse ENTREGADO");
        }
    }

    @Test
    @DisplayName("v4.4.0 §2: POS muestra 12 mesas, badges [MESA 04]/[KIOSCO]/[DELIVERY] y preserva Split 35/65")
    void debeRenderizar12MesasYBadgesMulticanalEnPOS() throws Exception {
        PedidoService pedidoService = new PedidoService();

        Pedido pMesa = new Pedido();
        pMesa.setIdUsuario(1);
        pMesa.setNumeroMesa("04");
        pMesa.setTipoPedido("MESA");
        pMesa.setTotal(new BigDecimal("48.00"));
        DetallePedido detMesa = new DetallePedido(0, 0, 1, "Burger Mesa 04", 2,
                new BigDecimal("24.00"), new BigDecimal("48.00"), "");
        pMesa = pedidoService.registrarPedidoYEnviarComanda(pMesa, List.of(detMesa));

        Pedido pKiosco = new Pedido();
        pKiosco.setIdUsuario(1);
        pKiosco.setNumeroMesa("T-105");
        pKiosco.setTipoPedido("KIOSCO");
        pKiosco.setClienteNombre("Cliente Kiosco #T-105");
        pKiosco.setTotal(new BigDecimal("24.00"));
        DetallePedido detKiosco = new DetallePedido(0, 0, 1, "Burger Kiosco", 1,
                new BigDecimal("24.00"), new BigDecimal("24.00"), "Autoservicio");
        pKiosco = pedidoService.registrarPedidoYEnviarComanda(pKiosco, List.of(detKiosco));

        Pedido pDelivery = new Pedido();
        pDelivery.setIdUsuario(1);
        pDelivery.setNumeroMesa("DL-09");
        pDelivery.setTipoPedido("DELIVERY");
        pDelivery.setClienteNombre("Elena Ríos");
        pDelivery.setTotal(new BigDecimal("36.00"));
        DetallePedido detDeliv = new DetallePedido(0, 0, 1, "Combo Delivery", 2,
                new BigDecimal("18.00"), new BigDecimal("36.00"), "");
        pDelivery = pedidoService.registrarPedidoYEnviarComanda(pDelivery, List.of(detDeliv));

        final Pedido mesaFinal = pMesa;
        final Pedido kioscoFinal = pKiosco;
        final Pedido deliveryFinal = pDelivery;

        AtomicReference<VentaController> vcRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1280, 760);
                App.applyStylesheets(sc);

                GridPane posSplitGrid = (GridPane) root.lookup("#posSplitGrid");
                assertNotNull(posSplitGrid);
                assertEquals(35.0, posSplitGrid.getColumnConstraints().get(0).getPercentWidth(), 0.01);
                assertEquals(65.0, posSplitGrid.getColumnConstraints().get(1).getPercentWidth(), 0.01);

                VentaController ctrl = loader.getController();
                vcRef.set(ctrl);
                ctrl.refrescarMesasYCanalesSincrono();

                assertEquals(12, ctrl.getTileMesasPos().getChildren().size(), "El grid POS debe mostrar las 12 mesas del salón");
                Node cardMesa04 = ctrl.getTileMesasPos().lookup("#posMesaCard_04");
                Node cardMesa01 = ctrl.getTileMesasPos().lookup("#posMesaCard_01");
                assertNotNull(cardMesa04);
                assertNotNull(cardMesa01);
                assertTrue(cardMesa04.getStyleClass().contains("pos-mesa-card-ocupada"));
                assertTrue(cardMesa01.getStyleClass().contains("pos-mesa-card-libre"));

                ctrl.seleccionarMesa("04");
                assertEquals("[MESA 04]", ctrl.getLblCanalBadge().getText());
                assertEquals(mesaFinal.getIdPedido(), ctrl.getPedidoActivoId());
                assertFalse(ctrl.getItemsVenta().isEmpty());

                ctrl.cargarPedidoEnPOS(kioscoFinal);
                assertEquals("[KIOSCO]", ctrl.getLblCanalBadge().getText());
                assertTrue(ctrl.getLblCanalBadge().getStyleClass().contains("badge-canal-kiosco"));

                ctrl.cargarPedidoEnPOS(deliveryFinal);
                assertEquals("[DELIVERY]", ctrl.getLblCanalBadge().getText());
                assertTrue(ctrl.getLblCanalBadge().getStyleClass().contains("badge-canal-delivery"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (vcRef.get() != null) {
                    vcRef.get().stopTimeline();
                }
            }
        });
    }

    @Test
    @DisplayName("v4.4.0 §3: Reportes Contables renderiza 4 tarjetas KPI con tintes pastel")
    void debeRenderizarTarjetasKpiPastelEnReportesContables() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReportesContablesView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1180, 760);
                App.applyStylesheets(sc);

                ReportesContablesController ctrl = loader.getController();
                assertTrue(ctrl.getCardKpiVentas().getStyleClass().containsAll(List.of("kpi-card", "kpi-card-sales")));
                assertTrue(ctrl.getCardKpiBase().getStyleClass().containsAll(List.of("kpi-card", "kpi-card-taxable")));
                assertTrue(ctrl.getCardKpiIgv().getStyleClass().containsAll(List.of("kpi-card", "kpi-card-igv")));
                assertTrue(ctrl.getCardKpiCompras().getStyleClass().containsAll(List.of("kpi-card", "kpi-card-purchases")));

                assertTrue(ctrl.getLblTotalVentas().getText().startsWith("S/ "));
                assertTrue(ctrl.getLblBaseImponible().getText().startsWith("S/ "));
                assertTrue(ctrl.getLblTotalIgv().getText().startsWith("S/ "));
                assertTrue(ctrl.getLblTotalCompras().getText().startsWith("S/ "));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("v4.4.0 §4: RRHH Ausencias (DAO + UI), Portal 3 tabs + Boleta PDF y Alertas 30 días en Dashboard")
    void debeGestionarRrhhAusenciasPortalBoletaPdfYAlertasVencimiento() throws Exception {
        // 1. DAO + UI de Solicitudes de Ausencia
        SolicitudAusenciaDAO solDao = new SolicitudAusenciaDAOImpl();
        try (Connection conn = ConexionBD.getConnection()) {
            List<SolicitudAusencia> iniciales = solDao.listarTodas(conn);
            assertTrue(iniciales.size() >= 2, "Debe existir data semilla de V13 en solicitud_ausencia");
            List<SolicitudAusencia> propias = solDao.listarPorUsuario(conn, 2);
            assertFalse(propias.isEmpty());
        }

        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setUsername("admin");
        admin.setNombreEmpleado("Administrador General");
        admin.setNombreRol("ADMIN");
        admin.setDni("70000001");
        admin.setSueldoBase(3500.00);
        AuthService.setUsuarioSesionActual(admin);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/SolicitudesView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1120, 740);
                App.applyStylesheets(sc);

                SolicitudesController ctrl = loader.getController();
                SolicitudAusencia s1 = ctrl.registrarSolicitud(2, "VACACIONES",
                        LocalDate.now().plusDays(5), LocalDate.now().plusDays(10), "Descanso médico / vacaciones");
                assertTrue(s1.getIdSolicitud() > 0);

                ctrl.aprobarSolicitud(s1);
                assertEquals("APROBADA", s1.getEstado());

                SolicitudAusencia s2 = ctrl.registrarSolicitud(3, "LICENCIA",
                        LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), "Trámite personal");
                ctrl.rechazarSolicitud(s2);
                assertEquals("RECHAZADA", s2.getEstado());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 2. Portal del Empleado (3 Tabs + Descarga Boleta PDF con OpenPDF)
        Path tempPdf = Files.createTempFile("foodcore_boleta_v44_", ".pdf");
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MiHorarioView.fxml"));
                    Parent root = loader.load();
                    Scene sc = new Scene(root, 1080, 720);
                    App.applyStylesheets(sc);

                    MiHorarioController ctrl = loader.getController();
                    assertEquals(3, ctrl.getTabPaneMiPortal().getTabs().size());
                    assertEquals("Mi Horario", ctrl.getTabPaneMiPortal().getTabs().get(0).getText());
                    assertEquals("Mis Asistencias", ctrl.getTabPaneMiPortal().getTabs().get(1).getText());
                    assertEquals("Mis Boletas", ctrl.getTabPaneMiPortal().getTabs().get(2).getText());
                    assertFalse(ctrl.getListaBoletas().isEmpty());

                    File out = ctrl.generarBoletaPdf(ctrl.getListaBoletas().get(0), admin, tempPdf.toFile());
                    assertTrue(out.exists() && out.length() > 800, "El PDF de la boleta debe generarse con contenido válido");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            byte[] pdfBytes = Files.readAllBytes(tempPdf);
            String header = new String(pdfBytes, 0, Math.min(5, pdfBytes.length), StandardCharsets.US_ASCII);
            assertEquals("%PDF-", header, "El archivo generado debe tener cabecera PDF válida");
        } finally {
            Files.deleteIfExists(tempPdf);
        }

        // 3. Alertas de Vencimiento en Dashboard (<= 30 días incluidos; > 30 días excluidos)
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement psUpd3 = conn.prepareStatement(
                     "UPDATE usuario SET fecha_vencimiento_contrato = ?, fecha_vencimiento_carnet_sanidad = ? WHERE id_usuario = 3");
             PreparedStatement psUpd4 = conn.prepareStatement(
                     "UPDATE usuario SET fecha_vencimiento_contrato = ? WHERE id_usuario = 4")) {
            LocalDate hoy = LocalDate.now(KitchenDisplayController.ZONA_LIMA);
            psUpd3.setDate(1, java.sql.Date.valueOf(hoy.plusDays(12)));
            psUpd3.setDate(2, java.sql.Date.valueOf(hoy.plusDays(95)));
            psUpd3.executeUpdate();

            psUpd4.setDate(1, java.sql.Date.valueOf(hoy.plusDays(120)));
            psUpd4.executeUpdate();
        }

        AtomicReference<DashboardController> dashCtrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                Parent root = loader.load();
                Scene sc = new Scene(root, 1180, 760);
                App.applyStylesheets(sc);
                dashCtrlRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        DashboardController dashCtrl = dashCtrlRef.get();
        esperarHastaEnFx(
                () -> !dashCtrl.getAlertasVencimientoActuales().isEmpty()
                        && !dashCtrl.getVboxAlertasVencimiento().getChildren().isEmpty(),
                5000,
                "Las métricas y alertas del Dashboard deben cargarse asíncronamente");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            List<DashboardController.AlertaVencimiento> alertas = dashCtrl.getAlertasVencimientoActuales();
            assertFalse(alertas.isEmpty());
            assertTrue(alertas.stream().anyMatch(a -> a.colaborador().contains("Carlos Mozo")
                    && a.documento().contains("Contrato")));
            assertFalse(alertas.stream().anyMatch(a -> a.diasRestantes() > 30),
                    "Ningún vencimiento mayor a 30 días debe figurar en las alertas");
            assertFalse(dashCtrl.getVboxAlertasVencimiento().getChildren().isEmpty());
            assertFalse(((Label) dashCtrl.getLblCountAlertasPersonal()).getText().startsWith("0 "));
        });

        // 4. MainLayout incluye botón de Solicitudes de Ausencia
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
                Parent mainRoot = mainLoader.load();
                new Scene(mainRoot, 1280, 768);
                MainDashboardController mainCtrl = mainLoader.getController();
                assertNotNull(mainCtrl.getBtnNavSolicitudes());
                assertTrue(mainCtrl.getBtnNavSolicitudes().isVisible());

                Usuario mozo = new Usuario();
                mozo.setIdUsuario(3);
                mozo.setUsername("mozo");
                mozo.setNombreRol("MOZO");
                mainCtrl.aplicarControlAcceso(mozo);
                assertTrue(mainCtrl.getBtnNavSolicitudes().isVisible(), "El mozo debe poder abrir Solicitudes");
                assertFalse(mainCtrl.getBtnNavInventario().isVisible(), "El mozo NO debe ver Backoffice");
                mainCtrl.handleNavMiHorario(null);
                mainCtrl.handleCerrarSesion(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("v4.4.0 §5: Consistencia de Agotados (PWA + Kiosco + POS) y RBAC estricta para CAJERO")
    void debeSincronizarAgotadosEnCanalesYRestringirAnulacionDescuentoACajero() throws Exception {
        // Parte A: Sincronización de producto AGOTADO (disponible = false) en BD, API PWA, Kiosco y POS
        ProductoDAO productoDAO = new ProductoDAOImpl();
        int idAgotado;
        String nombreAgotado = "Plato Agotado QA v44";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo) "
                             + "VALUES (1, 'AGOT-44', ?, 'Prueba QA agotados', 29.00, 10.00, TRUE, TRUE)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombreAgotado);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                idAgotado = rs.getInt(1);
            }
            productoDAO.cambiarDisponibilidad(conn, idAgotado, false);
            List<Producto> disponibles = productoDAO.listarProductosDisponibles(conn);
            assertFalse(disponibles.stream().anyMatch(p -> p.getIdProducto() == idAgotado),
                    "ProductoDAO.listarProductosDisponibles debe excluir productos agotados");
        }

        // Verificar /api/productos/disponibles en ApiServer
        int httpPort;
        int httpsPort;
        try (ServerSocket s1 = new ServerSocket(0); ServerSocket s2 = new ServerSocket(0)) {
            httpPort = s1.getLocalPort();
            httpsPort = s2.getLocalPort();
        }
        TestSecuritySupport.asegurarKeystoreE2E();
        TestSecuritySupport.asegurarJwtSecretTest();
        ApiServer api = ApiServer.getInstance();
        api.detener();
        api.iniciar(httpPort, httpsPort);
        try {
            String tokenMozo = api.generarTokenJwt(3, "mozo", "MOZO");
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resDisp = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + "/api/productos/disponibles"))
                            .header("Authorization", "Bearer " + tokenMozo)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, resDisp.statusCode());
            assertFalse(resDisp.body().contains(nombreAgotado),
                    "GET /api/productos/disponibles para PWA NO debe incluir productos agotados");

            HttpResponse<String> resPublicDisp = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + "/api/public/productos/disponibles"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, resPublicDisp.statusCode());
            assertFalse(resPublicDisp.body().contains(nombreAgotado));
        } finally {
            api.detener();
        }

        // Verificar KioscoController y VentaController
        AtomicReference<KioscoController> kioscoRef = new AtomicReference<>();
        AtomicReference<VentaController> vcRef = new AtomicReference<>();
        Producto prodNoDisp = new Producto(idAgotado, 1, "AGOT-44", nombreAgotado, "", new BigDecimal("29.00"), BigDecimal.ZERO, false);

        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

                    FXMLLoader kioscoLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/KioscoView.fxml"));
                    Parent kioscoRoot = kioscoLoader.load();
                    new Scene(kioscoRoot, 1180, 720);
                    kioscoRef.set(kioscoLoader.getController());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            KioscoController kioscoCtrl = kioscoRef.get();
            esperarHastaEnFx(() -> !kioscoCtrl.getTodosLosProductos().isEmpty(), 5000,
                    "KioscoController debe cargar el catálogo disponible de forma asíncrona");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertFalse(kioscoCtrl.getTodosLosProductos().stream().anyMatch(p -> p.getIdProducto() == idAgotado),
                        "KioscoController no debe mostrar productos agotados");
                kioscoCtrl.agregarAlCarrito(prodNoDisp);
                assertTrue(kioscoCtrl.getCarritoItems().isEmpty(), "Kiosco debe bloquear la adición de un producto agotado");

                Usuario cajero = new Usuario();
                cajero.setIdUsuario(2);
                cajero.setUsername("cajero");
                cajero.setNombreEmpleado("Maria Cajera");
                cajero.setNombreRol("CAJERO");
                AuthService.setUsuarioSesionActual(cajero);

                try {
                    FXMLLoader posLoader = new FXMLLoader(getClass().getResource("/com/foodcore/view/VentaPOSView.fxml"));
                    Parent posRoot = posLoader.load();
                    new Scene(posRoot, 1280, 740);
                    vcRef.set(posLoader.getController());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            VentaController ventaCtrl = vcRef.get();
            esperarHastaEnFx(() -> !ventaCtrl.getTodosLosProductos().isEmpty(), 5000,
                    "VentaController debe cargar el catálogo POS de forma asíncrona");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertFalse(ventaCtrl.getTodosLosProductos().stream().anyMatch(p -> p.getIdProducto() == idAgotado),
                        "POS no debe mostrar productos agotados en el catálogo");
                ventaCtrl.agregarProductoAVenta(prodNoDisp);
                assertTrue(ventaCtrl.getItemsVenta().isEmpty(), "POS no debe permitir agregar un producto agotado");

                assertTrue(ventaCtrl.getBtnDescuento().isDisabled(),
                        "El botón de Descuento en POS debe figurar deshabilitado para CAJERO");
                assertTrue(ventaCtrl.getBtnAnularVenta().isDisabled(),
                        "El botón de Anular Venta en POS debe figurar deshabilitado para CAJERO");
            });
        } finally {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                if (vcRef.get() != null) {
                    vcRef.get().stopTimeline();
                }
            });
        }

        // Parte B: Seguridad RBAC estricta para CAJERO vs SUPERVISOR/ADMIN en capa de servicio
        AutorizacionService authz = new AutorizacionService();
        SesionUsuario sesionCajero = authz.crearSesion(2L, "Maria Cajera", "CAJERO", "POS-01");
        SesionUsuario sesionSupervisor = authz.crearSesion(5L, "Supervisor Turno", "SUPERVISOR", "POS-01");
        SesionUsuario sesionAdmin = authz.crearSesion(1L, "Administrador", "ADMIN", "POS-01");

        assertFalse(authz.tienePermiso(sesionCajero, Permiso.VENTA_ANULAR), "CAJERO no puede tener VENTA_ANULAR");
        assertFalse(authz.tienePermiso(sesionCajero, Permiso.VENTA_DESCUENTO), "CAJERO no puede tener VENTA_DESCUENTO");
        assertTrue(authz.tienePermiso(sesionSupervisor, Permiso.VENTA_ANULAR), "SUPERVISOR debe tener VENTA_ANULAR");
        assertTrue(authz.tienePermiso(sesionSupervisor, Permiso.VENTA_DESCUENTO), "SUPERVISOR debe tener VENTA_DESCUENTO");
        assertTrue(authz.tienePermiso(sesionAdmin, Permiso.VENTA_ANULAR));
        assertTrue(authz.tienePermiso(sesionAdmin, Permiso.VENTA_DESCUENTO));

        new CajaService().abrirTurnoCaja(1, "Caja RBAC Test", new BigDecimal("100.00"));
        VentaService ventaService = new VentaService();

        NegocioException exDescCajero = assertThrows(NegocioException.class, () ->
                ventaService.calcularDescuentoAutorizado(new BigDecimal("100.00"), new BigDecimal("10"), true, sesionCajero));
        assertEquals(CodigoError.ACCESO_DENEGADO, exDescCajero.getCodigoError());

        NegocioException exAnulCajero = assertThrows(NegocioException.class, () ->
                ventaService.anularVenta(1, false, sesionCajero, "Intento no autorizado"));
        assertEquals(CodigoError.ACCESO_DENEGADO, exAnulCajero.getCodigoError());

        BigDecimal descSup = ventaService.calcularDescuentoAutorizado(
                new BigDecimal("100.00"), new BigDecimal("10"), true, sesionSupervisor);
        assertEquals(new BigDecimal("10.00"), descSup);
    }

    private static void esperarHastaEnFx(BooleanSupplier condicion, long timeoutMs, String mensaje) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean cumplida = new AtomicBoolean(false);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> cumplida.set(condicion.getAsBoolean()));
            if (cumplida.get()) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        AtomicBoolean cumplidaFinal = new AtomicBoolean(false);
        JavaFxTestSupport.runOnFxThreadAndWait(() -> cumplidaFinal.set(condicion.getAsBoolean()));
        assertTrue(cumplidaFinal.get(), mensaje);
    }
}
