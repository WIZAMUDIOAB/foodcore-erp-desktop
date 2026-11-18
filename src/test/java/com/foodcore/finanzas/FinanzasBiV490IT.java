package com.foodcore.finanzas;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.controller.ArqueoCajaController;
import com.foodcore.controller.DashboardController;
import com.foodcore.controller.EstadoResultadosController;
import com.foodcore.controller.ReportesAvanzadosController;
import com.foodcore.controller.ReportesAvanzadosController.FilaReporteAvanzado;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.Categoria;
import com.foodcore.model.CategoriaGasto;
import com.foodcore.model.Cliente;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.EstadoResultadosReporte;
import com.foodcore.model.EstadoResultadosReporte.GastoPorCategoria;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.model.ReporteCohortes.CohorteMensualFila;
import com.foodcore.model.ReporteCohortes.SegmentoRetencionFila;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.FinanzasBiService;
import com.foodcore.service.VentaService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ExportFileChooserHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Window;
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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración end-to-end para FOODCORE ERP v4.9.0 (Finanzas y Business Intelligence):
 * Fase 1 (Caja gastos categorizados + Estado de Resultados P&amp;L),
 * Fase 2 (Comparativas automáticas en Dashboard/Reportes + Márgenes por Categoría) y
 * Fase 3 (Análisis de Cohortes y Retención de Clientes).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FinanzasBiV490IT {

    private static final Path SCREENSHOT_DIR = Path.of("target", "v490-screenshots");
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private static CajaService cajaService;
    private static VentaService ventaService;
    private static FinanzasBiService finanzasBiService;
    private static ProductoDAOImpl productoDAO;
    private static InsumoDAOImpl insumoDAO;
    private static ClienteDAOImpl clienteDAO;

    @BeforeAll
    static void setUpSuite() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        MySqlIntegrationTestSupport.reiniciarEsquemaConFlyway();
        Files.createDirectories(SCREENSHOT_DIR);
        JavaFxTestSupport.iniciarToolkit();

        cajaService = new CajaService();
        ventaService = new VentaService();
        finanzasBiService = new FinanzasBiService();
        productoDAO = new ProductoDAOImpl();
        insumoDAO = new InsumoDAOImpl();
        clienteDAO = new ClienteDAOImpl();

        Usuario admin = new Usuario();
        admin.setIdUsuario(1);
        admin.setIdRol(1);
        admin.setUsername("admin");
        admin.setNombreEmpleado("Administrador Demo");
        AuthService.setUsuarioSesionActual(admin);
    }

    @Test
    @Order(1)
    @DisplayName("Fase 1: Gasto categorizado en Caja ('Pago de Luz') y Estado de Resultados P&L exacto")
    void test01CajaGastoCategorizadoYEstadoResultadosPnl(@TempDir Path tempExportDir) throws Exception {
        limpiarDatosTransaccionales();
        int idCaja = abrirCajaPrincipal("Caja Turno P&L v4.9", new BigDecimal("300.00"));

        // Verificar categorías sembradas en V20
        List<CategoriaGasto> categorias = cajaService.listarCategoriasGastoActivas();
        CategoriaGasto catLuz = categorias.stream()
                .filter(c -> "LUZ".equalsIgnoreCase(c.getCodigo()) || "Pago de Luz".equalsIgnoreCase(c.getNombre()))
                .findFirst()
                .orElse(null);
        CategoriaGasto catAlquiler = categorias.stream()
                .filter(c -> "ALQUILER".equalsIgnoreCase(c.getCodigo()))
                .findFirst()
                .orElse(null);
        assertNotNull(catLuz, "V20 debe sembrar la categoría 'Pago de Luz'");
        assertEquals("Pago de Luz", catLuz.getNombre());
        assertNotNull(catAlquiler, "V20 debe sembrar la categoría 'Alquiler'");

        // Cargar ArqueoCajaView y registrar gasto mediante modal UI seleccionando 'Pago de Luz'
        AtomicReference<Parent> cajaRootRef = new AtomicReference<>();
        AtomicReference<ArqueoCajaController> cajaCtrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ArqueoCajaView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1220, 780);
                cajaRootRef.set(root);
                cajaCtrlRef.set(loader.getController());
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Platform.runLater(() -> cajaCtrlRef.get().handleAbrirModalGasto(null));

        AtomicReference<Parent> modalRootRef = new AtomicReference<>();
        esperarHasta(() -> {
            AtomicBoolean encontrado = new AtomicBoolean(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    Parent host = localizarHostModalConSelector("#cbCategoriaGasto", cajaRootRef.get());
                    if (host != null) {
                        modalRootRef.set(host);
                        encontrado.set(true);
                    }
                    return null;
                });
            } catch (Exception ignored) {
                // Esperar apertura del modal
            }
            return encontrado.get();
        }, 6000);

        // Esperar a que termine la animación fade-in del modal (opacidad completa = 1.0)
        esperarHasta(() -> {
            AtomicBoolean opaco = new AtomicBoolean(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    Node cb = modalRootRef.get().lookup("#cbCategoriaGasto");
                    opaco.set(cb != null && estaCompletamenteOpaco(cb));
                    return null;
                });
            } catch (Exception ignored) {
                // Esperar animación
            }
            return opaco.get();
        }, 3000);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            Parent modalHost = modalRootRef.get();
            @SuppressWarnings("unchecked")
            ComboBox<CategoriaGasto> cbCat = (ComboBox<CategoriaGasto>) modalHost.lookup("#cbCategoriaGasto");
            TextField txtMonto = (TextField) modalHost.lookup("#txtMontoGasto");
            TextField txtConcepto = (TextField) modalHost.lookup("#txtConceptoGasto");
            Button btnConfirmar = (Button) modalHost.lookup("#btnConfirmarRegistroGasto");
            assertNotNull(cbCat, "El modal de egreso debe tener ComboBox #cbCategoriaGasto");
            assertNotNull(txtMonto);
            assertNotNull(txtConcepto);
            assertNotNull(btnConfirmar);

            CategoriaGasto seleccionLuz = cbCat.getItems().stream()
                    .filter(c -> "Pago de Luz".equals(c.getNombre()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(seleccionLuz, "ComboBox debe listar 'Pago de Luz'");
            cbCat.setValue(seleccionLuz);
            txtMonto.setText("150.00");
            txtConcepto.setText("Recibo Enel energía cocina y salón");

            modalHost.applyCss();
            modalHost.layout();
            try {
                guardarSnapshot(modalHost, "01_caja_gasto_categoria_v490.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            btnConfirmar.fire();
            return null;
        });

        // Esperar persistencia en BD del gasto de Luz
        esperarHasta(() -> contarGastosEnCaja(idCaja) >= 1, 6000);

        // Registrar segundo gasto categorizado (Alquiler) y un asiento sintético de Cierre Z (que NO debe sumarse a P&L)
        cajaService.registrarGastoOperativo(
                idCaja, 1, new BigDecimal("50.00"), "Prorrateo diario alquiler local", catAlquiler.getIdCategoriaGasto());
        try (Connection conn = ConexionBD.getConnection()) {
            new CajaDAOImpl().registrarMovimiento(
                    conn,
                    new MovimientoCaja(
                            0, idCaja, 1, "EGRESO", "EFECTIVO",
                            new BigDecimal("-999.00"), "Cierre Z de turno #1 - cuadre")
            );
        }

        // Preparar producto con costo exacto vía receta + Kardex (costo = 15.00, venta = 50.00, qty = 10 -> S/ 500 ingresos, S/ 150 costo)
        long idInsumo = asegurarInsumoConCosto(
                "INS-PNL-01", "Lomo Fino Seleccionado P&L", "KG", new BigDecimal("50.0000"), new BigDecimal("15.0000"));
        int idCatPnl = asegurarCategoria("Carnes P&L");
        Producto platoPnl = asegurarProductoConReceta(
                idCatPnl, "PNL-LOM", "Lomo Fino a la Parrilla P&L",
                new BigDecimal("50.00"), new BigDecimal("15.00"), idInsumo, BigDecimal.ONE);

        DetalleVenta det = new DetalleVenta(0, 0, platoPnl.getIdProducto(), 10, new BigDecimal("50.00"), new BigDecimal("500.00"));
        det.setNombreProducto(platoPnl.getNombre());
        Venta v = new Venta();
        v.setIdUsuario(1);
        v.setTotal(new BigDecimal("500.00"));
        v.setSubtotal(new BigDecimal("423.73"));
        v.setIgv(new BigDecimal("76.27"));
        v.setMetodoPago("TARJETA");
        v.setMontoRecibido(new BigDecimal("500.00"));
        v.setVuelto(BigDecimal.ZERO);
        v.setIdempotencyKey(UUID.randomUUID().toString());
        ComprobantePago comp = new ComprobantePago(0, 0, "BOLETA", "B001", "000490", new BigDecimal("500.00"));
        ventaService.registrarVenta(v, List.of(det), comp);

        // Abrir EstadoResultadosView y verificar UI + matemática exacta P&L
        AtomicReference<Parent> pnlRootRef = new AtomicReference<>();
        AtomicReference<EstadoResultadosController> pnlCtrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/EstadoResultadosView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1240, 780);
                root.applyCss();
                root.layout();
                EstadoResultadosController ctrl = loader.getController();
                ctrl.cargarPnlSincrono();
                pnlRootRef.set(root);
                pnlCtrlRef.set(ctrl);
                root.applyCss();
                root.layout();
                guardarSnapshot(root, "02_estado_resultados_pnl_v490.png");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        EstadoResultadosReporte rep = pnlCtrlRef.get().getUltimoReporte();
        assertNotNull(rep);
        assertEquals(0, new BigDecimal("500.00").compareTo(rep.ingresosBrutos()));
        assertEquals(0, new BigDecimal("150.00").compareTo(rep.costoVentas()));
        assertEquals(0, new BigDecimal("350.00").compareTo(rep.utilidadBruta()));
        assertEquals(0, new BigDecimal("200.00").compareTo(rep.gastosOperativos()));
        assertEquals(0, new BigDecimal("150.00").compareTo(rep.utilidadNeta()));

        GastoPorCategoria gastoLuzRep = rep.gastosPorCategoria().stream()
                .filter(g -> "Pago de Luz".equals(g.categoria()))
                .findFirst()
                .orElse(null);
        assertNotNull(gastoLuzRep, "El desglose de P&L debe listar 'Pago de Luz'");
        assertEquals(0, new BigDecimal("150.00").compareTo(gastoLuzRep.monto()));

        Parent pnlRoot = pnlRootRef.get();
        assertEquals("S/ 500.00", ((Label) pnlRoot.lookup("#lblPnlIngresosBrutos")).getText());
        assertEquals("S/ 150.00", ((Label) pnlRoot.lookup("#lblPnlCostoVentas")).getText());
        assertEquals("S/ 350.00", ((Label) pnlRoot.lookup("#lblPnlUtilidadBruta")).getText());
        assertEquals("S/ 150.00", ((Label) pnlRoot.lookup("#lblPnlUtilidadNeta")).getText());
        assertTrue(Files.exists(SCREENSHOT_DIR.resolve("01_caja_gasto_categoria_v490.png")));
        assertTrue(Files.exists(SCREENSHOT_DIR.resolve("02_estado_resultados_pnl_v490.png")));

        // Verificar filtro de fechas histórico vacío
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                DatePicker dpDesde = (DatePicker) pnlRoot.lookup("#dpDesde");
                DatePicker dpHasta = (DatePicker) pnlRoot.lookup("#dpHasta");
                dpDesde.setValue(LocalDate.of(2020, 1, 1));
                dpHasta.setValue(LocalDate.of(2020, 1, 10));
                pnlCtrlRef.get().cargarPnlSincrono();
                assertEquals(0, BigDecimal.ZERO.compareTo(pnlCtrlRef.get().getUltimoReporte().ingresosBrutos()));
                assertEquals(0, BigDecimal.ZERO.compareTo(pnlCtrlRef.get().getUltimoReporte().utilidadNeta()));

                LocalDate hoy = LocalDate.now(ZONA_LIMA);
                dpDesde.setValue(hoy.withDayOfMonth(1));
                dpHasta.setValue(hoy);
                pnlCtrlRef.get().cargarPnlSincrono();
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Verificar exportación PDF y Excel secuencialmente con selector de archivos
        Path pdfOut = tempExportDir.resolve("pnl_v490.pdf");
        Path xlsxOut = tempExportDir.resolve("pnl_v490.xlsx");
        try {
            ExportFileChooserHelper.setTestSelectorOverride(req ->
                    Optional.of(req.formatoPrincipal() == ExportFileChooserHelper.FormatoExportacion.PDF
                            ? pdfOut.toFile() : xlsxOut.toFile()));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                pnlCtrlRef.get().handleExportarPdf();
                return null;
            });
            esperarHasta(() -> estadoLabelContiene(pnlRoot, "✓ PDF exportado:"), 6000);
            assertTrue(Files.exists(pdfOut) && Files.size(pdfOut) > 100, "El PDF P&L exportado debe tener contenido");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                pnlCtrlRef.get().handleExportarExcel();
                return null;
            });
            esperarHasta(() -> estadoLabelContiene(pnlRoot, "✓ Excel exportado:"), 6000);
            assertTrue(Files.exists(xlsxOut) && Files.size(xlsxOut) > 100, "El Excel P&L exportado debe tener contenido");
        } finally {
            ExportFileChooserHelper.clearTestSelectorOverride();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Fase 2: Comparativas automáticas (Dashboard + Reportes) y Márgenes por Categoría ordenados")
    void test02ComparativasPeriodosYMargenesPorCategoria() throws Exception {
        limpiarDatosTransaccionales();
        int idCaja = abrirCajaPrincipal("Caja Comparativas v4.9", new BigDecimal("200.00"));

        // Preparar 3 categorías con márgenes netamente diferenciados: Bebidas (80%), Platos (55%), Postres (40%)
        int catBebidas = asegurarCategoria("Bebidas");
        int catPlatos = asegurarCategoria("Platos Principales");
        int catPostres = asegurarCategoria("Postres Artesanales");

        long insBeb = asegurarInsumoConCosto("INS-BI-BEB", "Concentrado Jugo Mango", "UND", new BigDecimal("500.0000"), new BigDecimal("4.0000"));
        long insPla = asegurarInsumoConCosto("INS-BI-PLA", "Corte Asado Especial", "UND", new BigDecimal("500.0000"), new BigDecimal("18.0000"));
        long insPos = asegurarInsumoConCosto("INS-BI-POS", "Base Cheesecake Berries", "UND", new BigDecimal("500.0000"), new BigDecimal("15.0000"));

        Producto prodBeb = asegurarProductoConReceta(catBebidas, "BI-BEB-01", "Frozen Mango Maracuyá", new BigDecimal("20.00"), new BigDecimal("4.00"), insBeb, BigDecimal.ONE);
        Producto prodPla = asegurarProductoConReceta(catPlatos, "BI-PLA-01", "Asado de Tira 450g", new BigDecimal("40.00"), new BigDecimal("18.00"), insPla, BigDecimal.ONE);
        Producto prodPos = asegurarProductoConReceta(catPostres, "BI-POS-01", "Cheesecake Frutos Rojos", new BigDecimal("25.00"), new BigDecimal("15.00"), insPos, BigDecimal.ONE);

        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        LocalDate ayer = hoy.minusDays(1);
        LocalDate mesAnteriorDia = YearMonth.from(hoy).minusMonths(1).atDay(12);

        // Ventas de HOY = S/ 360.00 (7x Bebidas = 140, 3x Platos = 120, 4x Postres = 100)
        insertarVentaHistoricaConDetalle(idCaja, null, prodBeb, 7, hoy.atTime(11, 30));
        insertarVentaHistoricaConDetalle(idCaja, null, prodPla, 3, hoy.atTime(13, 15));
        insertarVentaHistoricaConDetalle(idCaja, null, prodPos, 4, hoy.atTime(16, 45));

        // Ventas de AYER = S/ 300.00 (15x Bebidas = 300) -> Hoy vs Ayer = +20.0%
        insertarVentaHistoricaConDetalle(idCaja, null, prodBeb, 15, ayer.atTime(15, 0));

        // Ajustar ventas del MES ANTERIOR para que Mes Actual vs Mes Anterior sea exactamente -50.0% cualquier día del mes
        BigDecimal extraMesAnterior = hoy.getDayOfMonth() > 1
                ? new BigDecimal("1320.00")
                : new BigDecimal("420.00");
        insertarVentaHistoricaConDetalleMonto(idCaja, null, prodPla, 10, extraMesAnterior, mesAnteriorDia.atTime(14, 0));

        // 1. Verificar comparativas en DashboardView
        AtomicReference<Parent> dashRootRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/DashboardView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1260, 800);
                root.applyCss();
                root.layout();
                dashRootRef.set(root);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        esperarHasta(() -> {
            AtomicBoolean listo = new AtomicBoolean(false);
            try {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    Parent root = dashRootRef.get();
                    root.applyCss();
                    root.layout();
                    Label lblHoy = (Label) root.lookup("#lblDashCompHoyMonto");
                    listo.set(lblHoy != null && "S/ 360.00".equals(lblHoy.getText()));
                    return null;
                });
            } catch (Exception ignored) {
                // Esperar actualización async
            }
            return listo.get();
        }, 8000);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            Parent root = dashRootRef.get();
            Label badgeHoy = (Label) root.lookup("#lblDashCompHoyBadge");
            Label badgeMes = (Label) root.lookup("#lblDashCompMesBadge");
            Label miniHoy = (Label) root.lookup("#lblVarHoyVsAyer");
            Label miniMes = (Label) root.lookup("#lblVarMesVsAnteriorMini");

            assertEquals("↑ +20.0%", badgeHoy.getText());
            assertTrue(badgeHoy.getStyleClass().contains("badge-pill-success"));
            assertEquals("↓ -50.0%", badgeMes.getText());
            assertTrue(badgeMes.getStyleClass().contains("badge-pill-danger"));
            assertEquals("↑ +20.0%", miniHoy.getText());
            assertEquals("↓ -50.0%", miniMes.getText());

            root.applyCss();
            root.layout();
            try {
                guardarSnapshot(root, "03_dashboard_comparativas_v490.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 2. Verificar ReportesAvanzadosView (3 kpi-card-secondary + Comparativas + Márgenes por Categoría ordenados)
        AtomicReference<Parent> repRootRef = new AtomicReference<>();
        AtomicReference<ReportesAvanzadosController> repCtrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReportesAvanzadosView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1240, 800);
                root.applyCss();
                root.layout();
                ReportesAvanzadosController ctrl = loader.getController();
                ctrl.seleccionarReporteYCargarSincrono(ReportesAvanzadosController.REPORTE_MARGENES_CATEGORIA);
                root.applyCss();
                root.layout();
                repRootRef.set(root);
                repCtrlRef.set(ctrl);

                Set<Node> kpiCards = root.lookupAll(".kpi-card-secondary");
                assertEquals(3, kpiCards.size(), "Se deben mantener exactamente 3 tarjetas kpi-card-secondary");

                assertEquals("↑ +20.0%", ((Label) root.lookup("#lblRepHoyVsAyerBadge")).getText());
                assertEquals("↓ -50.0%", ((Label) root.lookup("#lblRepMesVsAntBadge")).getText());

                List<FilaReporteAvanzado> filas = ctrl.getFilasActuales();
                assertEquals(3, filas.size());
                assertEquals("Bebidas", filas.get(0).c1);
                assertEquals("80.0%", filas.get(0).c5);
                assertEquals("Platos Principales", filas.get(1).c1);
                assertEquals("55.0%", filas.get(1).c5);
                assertEquals("Postres Artesanales", filas.get(2).c1);
                assertEquals("40.0%", filas.get(2).c5);
                assertEquals("Bebidas", ((Label) root.lookup("#lblKpiTopItem")).getText());

                root.applyCss();
                root.layout();
                guardarSnapshot(root, "04_reportes_margenes_categoria_v490.png");
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(Files.exists(SCREENSHOT_DIR.resolve("03_dashboard_comparativas_v490.png")));
        assertTrue(Files.exists(SCREENSHOT_DIR.resolve("04_reportes_margenes_categoria_v490.png")));
    }

    @Test
    @Order(3)
    @DisplayName("Fase 3: Análisis de Cohortes (aviso datos insuficientes, matriz mensual y retención/perdidos)")
    void test03CohortesYRetencionClientes(@TempDir Path tempExportDir) throws Exception {
        limpiarDatosTransaccionales();
        int idCaja = abrirCajaPrincipal("Caja Cohortes v4.9", new BigDecimal("200.00"));
        Producto prod;
        try (Connection conn = ConexionBD.getConnection()) {
            prod = productoDAO.buscarPorCodigo(conn, "PNL-LOM");
            if (prod == null) {
                int idCat = asegurarCategoria("General BI");
                long idIns = asegurarInsumoConCosto(
                        "INS-COH-01", "Insumo Cohortes", "UND", new BigDecimal("200.0000"), new BigDecimal("5.0000"));
                prod = asegurarProductoConReceta(
                        idCat, "COH-01", "Plato Cohortes", new BigDecimal("25.00"), new BigDecimal("5.00"), idIns, BigDecimal.ONE);
            }
        }

        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        YearMonth ym0 = YearMonth.from(hoy);
        YearMonth ym1 = ym0.minusMonths(1);
        YearMonth ym2 = ym0.minusMonths(2);

        LocalDate fechaM2 = ym2.atDay(Math.min(10, ym2.lengthOfMonth()));
        LocalDate fechaM1 = ym1.atDay(Math.min(15, ym1.lengthOfMonth()));
        LocalDate fechaLost = hoy.minusDays(75); // Siempre > 60 días atrás sin volver

        // 1. Escenario con historial insuficiente (1 solo cliente en 1 mes)
        int cliUnico = insertarClientePrueba("CLI-COH-00", "Cliente Único Inicial");
        insertarVentaHistoricaConDetalle(idCaja, cliUnico, prod, 1, hoy.atTime(10, 0));

        AtomicReference<Parent> repRootRef = new AtomicReference<>();
        AtomicReference<ReportesAvanzadosController> repCtrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                cargarEstiloApp();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReportesAvanzadosView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1260, 800);
                root.applyCss();
                root.layout();
                ReportesAvanzadosController ctrl = loader.getController();
                ctrl.seleccionarReporteYCargarSincrono(ReportesAvanzadosController.REPORTE_COHORTES_CLIENTES);
                root.applyCss();
                root.layout();
                repRootRef.set(root);
                repCtrlRef.set(ctrl);

                Label aviso = (Label) root.lookup("#lblCohortesAviso");
                assertTrue(aviso.isVisible());
                assertTrue(aviso.getStyleClass().contains("badge-pill-warning"));
                assertTrue(aviso.getText().contains("insuficiente"));
                assertTrue(root.lookup("#panelCohortes").isVisible());
                assertFalse(root.lookup("#panelTablaGrafico").isVisible());
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 2. Poblar historia completa multi-cohorte (M-2, M-1, M + cliente perdido > 60 días)
        int cliA = insertarClientePrueba("CLI-COH-A", "Ana Torres (Fiel M-2/M-1/M)");
        int cliB = insertarClientePrueba("CLI-COH-B", "Bruno Díaz (M-2 -> M-1)");
        int cliC = insertarClientePrueba("CLI-COH-C", "Carla Ruiz (M-2 -> M)");
        int cliLost = insertarClientePrueba("CLI-COH-D", "Daniel Vega (Perdido > 60d)");
        int cliE = insertarClientePrueba("CLI-COH-E", "Elena Paredes (M-1 -> M)");
        int cliF = insertarClientePrueba("CLI-COH-F", "Fabián Soto (Solo M-1)");
        int cliG = insertarClientePrueba("CLI-COH-G", "Gabriela Mora (Nuevo M)");
        int cliH = insertarClientePrueba("CLI-COH-H", "Hugo Vargas (Nuevo M)");

        // Cohorte M-2
        insertarVentaHistoricaConDetalle(idCaja, cliA, prod, 1, fechaM2.atTime(12, 0));
        insertarVentaHistoricaConDetalle(idCaja, cliA, prod, 1, fechaM1.atTime(12, 0));
        insertarVentaHistoricaConDetalle(idCaja, cliA, prod, 1, hoy.atTime(12, 0));

        insertarVentaHistoricaConDetalle(idCaja, cliB, prod, 1, fechaM2.atTime(13, 0));
        insertarVentaHistoricaConDetalle(idCaja, cliB, prod, 1, fechaM1.atTime(13, 0));

        insertarVentaHistoricaConDetalle(idCaja, cliC, prod, 1, fechaM2.atTime(14, 0));
        insertarVentaHistoricaConDetalle(idCaja, cliC, prod, 1, hoy.atTime(14, 0));

        // Perdido > 60 días
        insertarVentaHistoricaConDetalle(idCaja, cliLost, prod, 1, fechaLost.atTime(15, 0));

        // Cohorte M-1
        insertarVentaHistoricaConDetalle(idCaja, cliE, prod, 1, fechaM1.atTime(16, 0));
        insertarVentaHistoricaConDetalle(idCaja, cliE, prod, 1, hoy.atTime(16, 0));
        insertarVentaHistoricaConDetalle(idCaja, cliF, prod, 1, fechaM1.atTime(17, 0));

        // Cohorte M (Nuevos del mes: cliG, cliH + cliUnico = 3)
        insertarVentaHistoricaConDetalle(idCaja, cliG, prod, 1, hoy.atTime(18, 0));
        insertarVentaHistoricaConDetalle(idCaja, cliH, prod, 1, hoy.atTime(18, 30));

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            ReportesAvanzadosController ctrl = repCtrlRef.get();
            Parent root = repRootRef.get();
            ctrl.seleccionarReporteYCargarSincrono(ReportesAvanzadosController.REPORTE_COHORTES_CLIENTES);

            Label aviso = (Label) root.lookup("#lblCohortesAviso");
            assertTrue(aviso.getStyleClass().contains("badge-pill-success"));
            assertTrue(aviso.getText().contains("9 clientes"));

            List<SegmentoRetencionFila> resumen = ctrl.getResumenCohortesActual();
            assertEquals(3, resumen.size());
            assertEquals("Clientes Nuevos del mes", resumen.get(0).segmento());
            assertEquals(3, resumen.get(0).cantidad()); // cliUnico + cliG + cliH
            assertEquals("Clientes Recurrentes del mes", resumen.get(1).segmento());
            assertEquals(3, resumen.get(1).cantidad()); // cliA + cliC + cliE
            assertEquals("Clientes Perdidos (> 60 días)", resumen.get(2).segmento());
            assertEquals(1, resumen.get(2).cantidad()); // cliLost

            List<CohorteMensualFila> matriz = ctrl.getMatrizCohortesActual();
            assertTrue(matriz.size() >= 3, "Deben existir al menos 3 cohortes mensuales");

            CohorteMensualFila filaM1 = matriz.stream()
                    .filter(c -> ym1.equals(c.mesCohorte()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(filaM1);
            assertEquals(2, filaM1.tamanoCohorte());
            assertEquals(0, new BigDecimal("100.0").compareTo(filaM1.mes1Pct()));
            assertEquals(0, new BigDecimal("50.0").compareTo(filaM1.mes2Pct()));

            @SuppressWarnings("unchecked")
            TableView<CohorteMensualFila> tblMatriz = (TableView<CohorteMensualFila>) root.lookup("#tblMatrizCohortes");
            assertNotNull(tblMatriz);
            assertFalse(tblMatriz.getItems().isEmpty());

            root.applyCss();
            root.layout();
            try {
                guardarSnapshot(root, "05_reportes_cohortes_clientes_v490.png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        assertTrue(Files.exists(SCREENSHOT_DIR.resolve("05_reportes_cohortes_clientes_v490.png")));

        // Exportación CSV desde modo Cohortes
        Path csvOut = tempExportDir.resolve("cohortes_v490.csv");
        try {
            ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(csvOut.toFile()));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                repCtrlRef.get().handleExportarCsv();
                return null;
            });
            esperarHasta(() -> estadoLabelContiene(repRootRef.get(), "✓ CSV exportado:"), 6000);
            assertTrue(Files.exists(csvOut) && Files.size(csvOut) > 50);
        } finally {
            ExportFileChooserHelper.clearTestSelectorOverride();
        }
    }

    private static boolean estadoLabelContiene(Parent root, String substring) {
        AtomicBoolean ok = new AtomicBoolean(false);
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                for (String selector : List.of("#lblEstadoPnl", "#lblEstadoReporte")) {
                    Node n = root.lookup(selector);
                    if (n instanceof Label lbl && lbl.getText() != null && lbl.getText().contains(substring)) {
                        ok.set(true);
                        return null;
                    }
                }
                return null;
            });
        } catch (Exception ignored) {
            return false;
        }
        return ok.get();
    }

    private static void limpiarDatosTransaccionales() throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM comprobante_pago");
            st.executeUpdate("DELETE FROM detalle_venta");
            st.executeUpdate("DELETE FROM venta");
            st.executeUpdate("DELETE FROM movimiento_caja");
            st.executeUpdate("DELETE FROM kardex_movimiento");
            st.executeUpdate("DELETE FROM detalle_pedido");
            st.executeUpdate("DELETE FROM pedido");
            st.executeUpdate("DELETE FROM caja");
        }
    }

    private static int abrirCajaPrincipal(String nombreCaja, BigDecimal montoApertura) throws Exception {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO caja (id_usuario_apertura, nombre_caja, monto_apertura, estado) VALUES (1, ?, ?, 'ABIERTA')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombreCaja);
            ps.setBigDecimal(2, montoApertura);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static int contarGastosEnCaja(int idCaja) {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM movimiento_caja WHERE id_caja = ? AND tipo_movimiento = 'EGRESO'")) {
            ps.setInt(1, idCaja);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static long asegurarInsumoConCosto(
            String codigo, String nombre, String um, BigDecimal stockInicial, BigDecimal costoUnit) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Insumo ex = insumoDAO.buscarPorCodigo(conn, codigo);
            if (ex != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE insumo SET stock_actual_disponible = ?, costo_unitario = ?, costo_unitario_actual = ?, activo = 1 WHERE id_insumo = ?")) {
                    ps.setBigDecimal(1, stockInicial);
                    ps.setBigDecimal(2, costoUnit);
                    ps.setBigDecimal(3, costoUnit);
                    ps.setLong(4, ex.getIdInsumo());
                    ps.executeUpdate();
                }
                return ex.getIdInsumo();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO insumo (codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario, costo_unitario_actual, activo) "
                            + "VALUES (?, ?, ?, ?, 5.0000, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, codigo);
                ps.setString(2, nombre);
                ps.setString(3, um);
                ps.setBigDecimal(4, stockInicial);
                ps.setBigDecimal(5, costoUnit);
                ps.setBigDecimal(6, costoUnit);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    return rs.getLong(1);
                }
            }
        }
    }

    private static int asegurarCategoria(String nombreCategoria) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            List<Categoria> existentes = productoDAO.listarCategorias(conn);
            for (Categoria c : existentes) {
                if (nombreCategoria.equalsIgnoreCase(c.getNombre())) {
                    return c.getIdCategoria();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion, activo) VALUES (?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nombreCategoria);
                ps.setString(2, "Categoría BI " + nombreCategoria);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    return rs.getInt(1);
                }
            }
        }
    }

    private static Producto asegurarProductoConReceta(
            int idCategoria, String codigo, String nombre, BigDecimal precioVenta,
            BigDecimal costoEstimado, long idInsumo, BigDecimal qtyReceta) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Producto ex = productoDAO.buscarPorCodigo(conn, codigo);
            int idProd;
            if (ex == null) {
                Producto nuevo = new Producto(0, idCategoria, codigo, nombre, "Producto BI " + nombre,
                        precioVenta, costoEstimado, true);
                productoDAO.crear(conn, nuevo);
                idProd = nuevo.getIdProducto();
            } else {
                idProd = ex.getIdProducto();
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE producto SET id_categoria = ?, precio_venta = ?, costo_estimado = ?, disponible = 1 WHERE id_producto = ?")) {
                    ps.setInt(1, idCategoria);
                    ps.setBigDecimal(2, precioVenta);
                    ps.setBigDecimal(3, costoEstimado);
                    ps.setInt(4, idProd);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE cantidad_necesaria = VALUES(cantidad_necesaria)")) {
                ps.setInt(1, idProd);
                ps.setLong(2, idInsumo);
                ps.setBigDecimal(3, qtyReceta);
                ps.executeUpdate();
            }
            return productoDAO.buscarPorCodigo(conn, codigo);
        }
    }

    private static void insertarVentaHistoricaConDetalle(
            int idCaja, Integer idCliente, Producto prod, int cantidad, LocalDateTime fechaHora) throws Exception {
        BigDecimal total = prod.getPrecioVenta().multiply(BigDecimal.valueOf(cantidad));
        insertarVentaHistoricaConDetalleMonto(idCaja, idCliente, prod, cantidad, total, fechaHora);
    }

    private static void insertarVentaHistoricaConDetalleMonto(
            int idCaja, Integer idCliente, Producto prod, int cantidad,
            BigDecimal totalVenta, LocalDateTime fechaHora) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int idVenta;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO venta (id_caja, id_usuario, id_cliente, subtotal, igv, descuento, total, "
                                + "metodo_pago, monto_recibido, vuelto, estado, fecha_hora) "
                                + "VALUES (?, 1, ?, ?, 0.00, 0.00, ?, 'EFECTIVO', ?, 0.00, 'COMPLETADA', ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, idCaja);
                    if (idCliente != null) {
                        ps.setInt(2, idCliente);
                    } else {
                        ps.setNull(2, java.sql.Types.INTEGER);
                    }
                    ps.setBigDecimal(3, totalVenta);
                    ps.setBigDecimal(4, totalVenta);
                    ps.setBigDecimal(5, totalVenta);
                    ps.setTimestamp(6, Timestamp.valueOf(fechaHora));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        assertTrue(rs.next());
                        idVenta = rs.getInt(1);
                    }
                }
                BigDecimal precioUnit = totalVenta.divide(
                        BigDecimal.valueOf(Math.max(1, cantidad)), 2, java.math.RoundingMode.HALF_UP);
                try (PreparedStatement psDet = conn.prepareStatement(
                        "INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    psDet.setInt(1, idVenta);
                    psDet.setInt(2, prod.getIdProducto());
                    psDet.setInt(3, cantidad);
                    psDet.setBigDecimal(4, precioUnit);
                    psDet.setBigDecimal(5, totalVenta);
                    psDet.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static int insertarClientePrueba(String doc, String nombre) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Cliente> ex = clienteDAO.buscarPorDocumento(conn, doc);
            if (ex.isPresent()) {
                return ex.get().getIdCliente();
            }
            Cliente cli = new Cliente();
            cli.setTipoDocumento("DNI");
            cli.setNumeroDocumento(doc);
            cli.setRazonSocial(nombre);
            cli.setContacto(nombre);
            cli.setTelefono("999000111");
            cli.setEmail(doc.toLowerCase() + "@test.pe");
            cli.setLimiteCredito(0.0);
            cli.setDiasCredito(0);
            cli.setPuntosFidelidad(10);
            cli.setEstado("ACTIVO");
            cli.setActivo(true);
            return clienteDAO.insertar(conn, cli);
        }
    }

    private static boolean estaCompletamenteOpaco(Node node) {
        Node cur = node;
        while (cur != null) {
            if (cur.getOpacity() < 0.99) {
                return false;
            }
            cur = cur.getParent();
        }
        return true;
    }

    private static Parent localizarHostModalConSelector(String selector, Parent fallbackRoot) {
        if (fallbackRoot != null && fallbackRoot.lookup(selector) != null) {
            return fallbackRoot;
        }
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w.getScene() != null && w.getScene().getRoot() != null) {
                Parent root = w.getScene().getRoot();
                if (root.lookup(selector) != null) {
                    return root;
                }
            }
        }
        return null;
    }

    private static void cargarEstiloApp() {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        App.cargarFuentesSistema();
    }

    private static void esperarHasta(java.util.function.BooleanSupplier condicion, long timeoutMs)
            throws InterruptedException {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
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
