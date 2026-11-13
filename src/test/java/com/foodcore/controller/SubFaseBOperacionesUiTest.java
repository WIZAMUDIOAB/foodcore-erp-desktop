package com.foodcore.controller;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.model.Asistencia;
import com.foodcore.model.Caja;
import com.foodcore.model.CierreContable;
import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.model.CumplimientoTurnoFila;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.ItemInventarioFisico;
import com.foodcore.model.Pedido;
import com.foodcore.model.ReporteCumplimientoTurno;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.ActaEntregaTurnoService;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AuthService;
import com.foodcore.service.PedidoService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.UiScreenshotTestSupport;
import com.foodcore.support.UiScreenshotTestSupport.VistaCargada;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.ImpresoraTermicaService.DespachoImpresionGrupo;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias y de interfaz JavaFX (sin dependencia de servidor BD vivo)
 * para los 8 ítems de Sub-Fase B: Operaciones Avanzadas (v5.2.0),
 * generando las 9 capturas PNG de evidencia en {@code target/v520_evidence}.
 */
class SubFaseBOperacionesUiTest {

    private static final Path EVIDENCE_DIR = Path.of("target", "v520_evidence");

    @BeforeAll
    static void prepararToolkit() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @AfterEach
    void limpiarSesion() {
        AuthService.setUsuarioSesionActual(null);
    }

    @Test
    @DisplayName("Ítem 1: Ruteo paralelo por categoría a múltiples impresoras y UI ConfiguracionView")
    void item1MultiplesImpresorasConfiguracionYAgrupacion(@TempDir Path tempDir) throws Exception {
        Path logFallback = tempDir.resolve("comandas_paralelas.log");
        ImpresoraTermicaService servicioImp = new ImpresoraTermicaService(new AutorizacionService(), logFallback);

        Pedido pedido = new Pedido(501, 1, null, "Mesa 04", "REGISTRADO", new BigDecimal("64.00"));
        DetallePedido plato = new DetallePedido(1, 501, 10, "Burger Clásica Doble", 2,
                new BigDecimal("24.00"), new BigDecimal("48.00"), "Término medio");
        DetallePedido bebida = new DetallePedido(2, 501, 20, "Mojito Clásico", 1,
                new BigDecimal("16.00"), new BigDecimal("16.00"), "Poco hielo");
        DetallePedido postre = new DetallePedido(3, 501, 30, "Torta Chocolate", 1,
                new BigDecimal("12.00"), new BigDecimal("12.00"), "Sin crema");

        Map<Integer, Integer> prodCat = Map.of(10, 1, 20, 2, 30, 3);
        Map<Integer, String> catImp = Map.of(1, "EPSON-COCINA-80MM", 2, "STAR-BARRA-TSP100");

        Map<String, List<DetallePedido>> agrupado = servicioImp.agruparComandaPorImpresora(
                List.of(plato, bebida, postre), prodCat, catImp, "POS-DEFAULT");
        assertEquals(3, agrupado.size());
        assertEquals(1, agrupado.get("EPSON-COCINA-80MM").size());
        assertEquals(1, agrupado.get("STAR-BARRA-TSP100").size());
        assertEquals(1, agrupado.get("POS-DEFAULT").size());

        List<DespachoImpresionGrupo> despachos = servicioImp.imprimirComandaPorCategorias(
                pedido, List.of(plato, bebida), prodCat, catImp);
        assertEquals(2, despachos.size());
        String logContenido = Files.readString(logFallback, StandardCharsets.UTF_8);
        assertTrue(logContenido.contains("[IMPRESORA: EPSON-COCINA-80MM]"));
        assertTrue(logContenido.contains("[IMPRESORA: STAR-BARRA-TSP100]"));
        assertTrue(logContenido.contains("Burger"));
        assertTrue(logContenido.contains("Mojito"));

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ConfiguracionController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ConfiguracionView.fxml", 1180, 820);
                ConfiguracionController ctrl = vista.controller();

                ctrl.getCmbCategoriaImpresora().getItems().setAll(
                        new ConfiguracionController.CategoriaOpcion(1, "Cocina Caliente"),
                        new ConfiguracionController.CategoriaOpcion(2, "Bar y Bebidas"),
                        new ConfiguracionController.CategoriaOpcion(3, "Postres")
                );
                ctrl.getCmbCategoriaImpresora().getSelectionModel().selectFirst();
                ctrl.getCmbNombreImpresoraWindows().getItems().setAll(
                        "EPSON-COCINA-80MM", "STAR-BARRA-TSP100", "POSTRES-80MM");
                ctrl.getCmbNombreImpresoraWindows().setValue("EPSON-COCINA-80MM");

                ConfiguracionImpresora a1 = new ConfiguracionImpresora(
                        1, 1, "Cocina Caliente", "EPSON-COCINA-80MM", true);
                ConfiguracionImpresora a2 = new ConfiguracionImpresora(
                        2, 2, "Bar y Bebidas", "STAR-BARRA-TSP100", true);
                ctrl.getAsignaciones().setAll(a1, a2);

                assertEquals(2, ctrl.getAsignaciones().size());
                if (vista.root().lookup(".scroll-pane") instanceof ScrollPane sp) {
                    sp.setVvalue(1.0);
                }
                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "01_configuracion_multiples_impresoras_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 2: Toma de Inventario Físico calcula diferencias e impacto valorizado en UI")
    void item2TomaInventarioFisicoConteoYCalculo() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<InventarioFisicoController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/InventarioFisicoView.fxml", 1220, 780);
                InventarioFisicoController ctrl = vista.controller();

                ItemInventarioFisico i1 = crearItemConteo(101L, "INS-CARNE", "Carne Hamburguesa Res 150g",
                        "UND", new BigDecimal("25.000"), new BigDecimal("18.50"), "");
                ItemInventarioFisico i2 = crearItemConteo(102L, "INS-PAN", "Pan Brioche Artesanal",
                        "UND", new BigDecimal("40.000"), new BigDecimal("1.80"), "");
                ItemInventarioFisico i3 = crearItemConteo(103L, "INS-QUESO", "Queso Cheddar Laminado",
                        "UND", new BigDecimal("18.000"), new BigDecimal("2.20"), "");
                ItemInventarioFisico i4 = crearItemConteo(104L, "INS-SALSA", "Salsa Especial FoodCore",
                        "KG", new BigDecimal("5.500"), new BigDecimal("14.00"), "");

                ctrl.aplicarListaCargada(List.of(i1, i2, i3, i4));
                ctrl.actualizarConteoManual(101L, new BigDecimal("22.500"));
                ctrl.actualizarConteoManual(102L, new BigDecimal("44.000"));
                ctrl.actualizarConteoManual(104L, new BigDecimal("4.000"));

                if (vista.root().lookup("#txtMotivoAjuste") instanceof TextField txtMotivo) {
                    txtMotivo.setText("Conteo físico cierre de turno noche");
                }
                Label lblResumen = (Label) vista.root().lookup("#lblResumenConteo");
                assertEquals("4 insumos · 3 con diferencia", lblResumen.getText());
                assertEquals(0, new BigDecimal("-2.500").compareTo(ctrl.getTodosLosItems().get(0).getDiferencia()));
                assertEquals(0, new BigDecimal("4.000").compareTo(ctrl.getTodosLosItems().get(1).getDiferencia()));
                assertEquals(0, BigDecimal.ZERO.compareTo(ctrl.getTodosLosItems().get(2).getDiferencia()));

                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "02_inventario_fisico_conteo_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 3: Modo Supervisor habilita Anular Venta para CAJERO y exige PIN Supervisor")
    void item3ModoSupervisorPosCajeroYAutorizacionModal() throws Exception {
        Usuario cajero = new Usuario();
        cajero.setIdUsuario(2);
        cajero.setIdRol(2);
        cajero.setNombreRol("CAJERO");
        cajero.setUsername("cajero.turno1");
        cajero.setNombreEmpleado("Lucía Ramos (Cajera)");
        AuthService.setUsuarioSesionActual(cajero);

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<VentaController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/VentaPOSView.fxml", 1240, 820);
                VentaController ctrl = vista.controller();

                assertFalse(ctrl.getBtnAnularVenta().isDisabled(),
                        "CAJERO debe tener Anular Venta habilitado para usar Modo Supervisor");

                VentaController.ModalModoSupervisorHandle modal = ctrl.construirModalModoSupervisor(1042);
                modal.txtPinSupervisor().setText("4321");
                modal.txtMotivo().setText("Error de digitación en medio de pago");
                modal.chkPlatoPreparado().setSelected(false);

                DialogPane pane = modal.dialog().getDialogPane();
                pane.getStyleClass().add("modal-dialog-surface");
                pane.setMaxWidth(540);
                pane.setMaxHeight(340);

                Scene scenePos = vista.root().getScene();
                Parent posRoot = vista.root();
                scenePos.setRoot(new javafx.scene.Group());

                StackPane backdrop = new StackPane(pane);
                backdrop.setStyle("-fx-background-color: rgba(15, 23, 42, 0.58);");
                backdrop.setPadding(new Insets(24));
                StackPane.setAlignment(pane, Pos.CENTER);

                StackPane composicion = new StackPane(posRoot, backdrop);
                scenePos.setRoot(composicion);
                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, composicion, "03_pos_modo_supervisor_modal_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 4: Devoluciones Parciales calcula reembolso de ítem seleccionado en HistorialVentasView")
    void item4DevolucionesParcialesHistorialUi() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<HistorialVentasController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/HistorialVentasView.fxml", 1240, 800);
                HistorialVentasController ctrl = vista.controller();

                Venta v1 = crearVentaDemo(1048, 301, "YAPE", new BigDecimal("68.00"), "COMPLETADA");
                Venta v2 = crearVentaDemo(1047, null, "EFECTIVO", new BigDecimal("42.00"), "COMPLETADA");
                Venta v3 = crearVentaDemo(1046, 307, "TARJETA", new BigDecimal("54.00"), "DEVOLUCION_PARCIAL");
                ctrl.aplicarListaVentas(List.of(v1, v2, v3));

                DetalleVenta d1 = crearDetalleDemo(201, 1048, 10, "Burger Clásica Doble", 1, new BigDecimal("32.00"), "ACTIVO");
                DetalleVenta d2 = crearDetalleDemo(202, 1048, 11, "Papas Fritas Supremas", 1, new BigDecimal("18.00"), "ACTIVO");
                DetalleVenta d3 = crearDetalleDemo(203, 1048, 12, "Limonada Frozen Jarra", 1, new BigDecimal("18.00"), "ACTIVO");

                ctrl.mostrarDetalleVenta(v1, List.of(d1, d2, d3));
                ctrl.marcarDetalleParaDevolucion(202, true);

                if (vista.root().lookup("#txtMotivoDevolucion") instanceof TextField txtMot) {
                    txtMot.setText("Papas fritas llegaron frías · reembolso parcial ítem #2");
                }
                Label lblReembolso = (Label) vista.root().lookup("#lblTotalReembolso");
                Button btnDevolver = (Button) vista.root().lookup("#btnDevolucionParcial");

                assertEquals("Reembolso seleccionado: S/ 18.00", lblReembolso.getText());
                assertFalse(btnDevolver.isDisabled());

                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "04_historial_devolucion_parcial_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 5: KDS con Priorización Manual reordena tarjetas Drag & Drop y asigna badge de prioridad")
    void item5KdsPriorizacionManualDragDropYOrden() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<KitchenDisplayController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/KitchenDisplayView.fxml", 1400, 840);
                KitchenDisplayController ctrl = vista.controller();
                ctrl.stopTimers();

                PedidoService noOpPedidoService = new PedidoService(null, null, null) {
                    @Override
                    public void reordenarPrioridadKds(List<Integer> idsDetalleOrdenDescendente) {
                        // Sin BD en prueba unitaria UI
                    }
                };
                asignarCampo(ctrl, "pedidoService", noOpPedidoService);

                LocalDateTime ahora = LocalDateTime.of(2026, 11, 13, 12, 30);
                DetallePedido dp1 = new DetallePedido(501, 10, 1, "Salchipapa Clásica", 1,
                        null, null, "Cremas completas");
                dp1.setNumeroMesa("02");
                dp1.setEstadoCocina("PENDIENTE");
                dp1.setEnviadoEn(ahora.minusMinutes(3));

                DetallePedido dp2 = new DetallePedido(502, 11, 2, "Alitas BBQ x12", 1,
                        null, null, "Extra ranch");
                dp2.setNumeroMesa("05");
                dp2.setEstadoCocina("PENDIENTE");
                dp2.setEnviadoEn(ahora.minusMinutes(5));

                DetallePedido dp3 = new DetallePedido(503, 12, 3, "Chaufa Aeropuerto Familiar", 1,
                        null, null, "VIP · Priorizar en wok");
                dp3.setNumeroMesa("08");
                dp3.setEstadoCocina("PENDIENTE");
                dp3.setEnviadoEn(ahora.minusMinutes(11));

                DetallePedido dp4 = new DetallePedido(504, 13, 4, "Pollo Crispy x6", 2,
                        null, null, "En freidora");
                dp4.setNumeroMesa("04");
                dp4.setEstadoCocina("EN_PREPARACION");
                dp4.setEnviadoEn(ahora.minusMinutes(7));

                ctrl.aplicarItemsKdsPrueba(List.of(dp1, dp2, dp3, dp4), ahora, true);
                assertEquals(List.of(501, 502, 503), ctrl.extraerIdsOrdenColumna(ctrl.getVboxColPendiente()));

                ctrl.reordenarTarjetaManual(503, 0);
                assertEquals(List.of(503, 501, 502), ctrl.extraerIdsOrdenColumna(ctrl.getVboxColPendiente()));
                assertEquals(3, ctrl.getTarjetasActivas().get(503).getPrioridad());

                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "05_kds_priorizacion_manual_drag_drop_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 6: Cierre Contable Mensual bloquea período en EstadoResultadosView")
    void item6CierreContableMensualEstadoResultados() throws Exception {
        CierreContable cierre = new CierreContable();
        cierre.setIdCierre(12);
        cierre.setAnio(2026);
        cierre.setMes(10);
        cierre.setEstado("CERRADO");
        cierre.setObservaciones("Cierre mensual contable aprobado");
        assertTrue(cierre.estaCerrado());

        PeriodoCerradoException exPeriodo = new PeriodoCerradoException(2026, 10);
        assertEquals(CodigoError.PERIODO_CERRADO, exPeriodo.getCodigoError());
        assertTrue(exPeriodo.getMessage().contains("2026-10"));

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<EstadoResultadosController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/EstadoResultadosView.fxml", 1220, 780);
                EstadoResultadosController ctrl = vista.controller();
                ctrl.aplicarEstadoCierreVisual(YearMonth.of(2026, 10), cierre);

                assertEquals("MES 2026-10 CERRADO (BLOQUEADO)", ctrl.getLblEstadoCierreMes().getText());
                assertTrue(ctrl.getBtnCerrarMes().isDisabled());
                assertEquals("Cerrar Mes", ctrl.getBtnCerrarMes().getText());

                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "06_estado_resultados_cierre_contable_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 7: Turnos con Asistencia vinculados, columna Cumplimiento y reporte Atrasos/Faltas Excel")
    void item7TurnosAsistenciaVinculadosYReporteAtrasosFaltas(@TempDir Path tempDir) throws Exception {
        LocalDate diaPrueba = LocalDate.of(2026, 11, 13);
        assertEquals("VIERNES", AsistenciaService.diaSemanaEspanol(DayOfWeek.FRIDAY));

        CumplimientoTurnoFila fPuntual = new CumplimientoTurnoFila(
                11, "Ana Torres", "MOZO", diaPrueba, "VIERNES", "MAÑANA",
                LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(7, 58), LocalTime.of(16, 2), 0, "A TIEMPO");
        CumplimientoTurnoFila fAtraso = new CumplimientoTurnoFila(
                12, "Carlos Díaz", "COCINERO", diaPrueba, "VIERNES", "MAÑANA",
                LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(8, 18), LocalTime.of(16, 0), 18, "ATRASO (18 min)");
        CumplimientoTurnoFila fFalta = new CumplimientoTurnoFila(
                13, "Marco Vega", "CAJERO", diaPrueba, "VIERNES", "TARDE",
                LocalTime.of(14, 0), LocalTime.of(22, 0), null, null, 0, "FALTA");

        ReporteCumplimientoTurno reporte = new ReporteCumplimientoTurno(
                diaPrueba.minusDays(6), diaPrueba, 3, 1, 1, 1,
                List.of(fAtraso), List.of(fFalta), List.of(fPuntual, fAtraso, fFalta));

        AsistenciaService asistenciaService = new AsistenciaService(null, null, null);
        File archivoExcel = tempDir.resolve("reporte_atrasos_faltas.xlsx").toFile();
        asistenciaService.exportarReporteCumplimientoExcel(reporte, archivoExcel);
        assertTrue(archivoExcel.exists() && archivoExcel.length() > 1000);
        try (FileInputStream fis = new FileInputStream(archivoExcel);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            assertEquals("Atrasos y Faltas", wb.getSheetName(0));
            assertNotNull(wb.getSheetAt(0).getRow(0));
        }

        Asistencia a1 = new Asistencia(1, 11, diaPrueba, LocalTime.of(7, 58), LocalTime.of(16, 2), "FINALIZADO");
        a1.setNombreEmpleado("Ana Torres");
        a1.setRol("MOZO");
        a1.setTurnoProgramadoTexto("08:00 - 16:00 (MAÑANA)");
        a1.setCumplimientoTurno("A TIEMPO");

        Asistencia a2 = new Asistencia(2, 12, diaPrueba, LocalTime.of(8, 18), LocalTime.of(16, 0), "TARDE");
        a2.setNombreEmpleado("Carlos Díaz");
        a2.setRol("COCINERO");
        a2.setTurnoProgramadoTexto("08:00 - 16:00 (MAÑANA)");
        a2.setCumplimientoTurno("ATRASO (18 min)");

        Asistencia a3 = new Asistencia(3, 13, diaPrueba, LocalTime.of(13, 55), LocalTime.of(21, 35), "FINALIZADO");
        a3.setNombreEmpleado("Marco Vega");
        a3.setRol("CAJERO");
        a3.setTurnoProgramadoTexto("14:00 - 22:00 (TARDE)");
        a3.setCumplimientoTurno("SALIDA TEMPRANA (25 min)");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<AsistenciaController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/AsistenciaView.fxml", 1460, 780);
                AsistenciaController ctrl = vista.controller();

                @SuppressWarnings("unchecked")
                TableView<Asistencia> tbl = (TableView<Asistencia>) vista.root().lookup(".table-view");
                assertNotNull(tbl);
                tbl.getItems().setAll(a1, a2, a3);

                boolean tieneColCumplimiento = tbl.getColumns().stream()
                        .anyMatch(c -> "Cumplimiento de Turno".equals(c.getText()));
                assertTrue(tieneColCumplimiento);

                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "07a_asistencia_cumplimiento_turno_v520.png");

                Stage modalRep = ctrl.construirStageReporteCumplimiento(reporte);
                Parent modalRoot = modalRep.getScene().getRoot();
                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, modalRoot, "07b_reporte_atrasos_faltas_modal_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("Ítem 8: Sincronización de Inventario en Cierre Z y Acta de Entrega PDF OpenPDF")
    void item8CajaArqueoStockCriticoYActaPdfRelevo(@TempDir Path tempDir) throws Exception {
        Caja caja = new Caja(42, 1, "Caja Principal 01", new BigDecimal("250.00"), "CERRADA");
        caja.setMontoCierre(new BigDecimal("1480.50"));
        caja.setFechaCierre(LocalDateTime.of(2026, 11, 13, 22, 15));

        List<InsumoCriticoTurno> criticos = List.of(
                new InsumoCriticoTurno(10L, "INS-POLLO", "Pechuga de Pollo Marinada", "KG",
                        BigDecimal.ZERO.setScale(3), new BigDecimal("5.000"), "AGOTADO"),
                new InsumoCriticoTurno(11L, "INS-PAN", "Pan Brioche Artesanal", "UND",
                        BigDecimal.ZERO.setScale(3), new BigDecimal("24.000"), "AGOTADO"),
                new InsumoCriticoTurno(12L, "INS-ACEITE", "Aceite Vegetal Freidora", "LT",
                        new BigDecimal("4.500"), new BigDecimal("8.000"), "STOCK MÍNIMO")
        );

        ActaEntregaTurnoService actaService = new ActaEntregaTurnoService();
        File pdfDestino = tempDir.resolve("Acta_Entrega_Turno_42.pdf").toFile();
        File pdfGenerado = actaService.generarActaEntregaTurnoPdf(
                caja,
                new BigDecimal("1480.50"),
                BigDecimal.ZERO.setScale(2),
                criticos,
                "Lucía Ramos (Turno Día)",
                "Pedro Salazar (Turno Noche)",
                pdfDestino
        );
        assertTrue(pdfGenerado.exists() && pdfGenerado.length() > 1500);
        byte[] header = Files.readAllBytes(pdfGenerado.toPath());
        String pdfMagic = new String(header, 0, Math.min(8, header.length), StandardCharsets.US_ASCII);
        assertTrue(pdfMagic.startsWith("%PDF-"), "El acta generada debe tener cabecera PDF válida");

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ArqueoCajaController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ArqueoCajaView.fxml", 1180, 900);
                ArqueoCajaController ctrl = vista.controller();

                Caja cajaAbierta = new Caja(42, 1, "Caja Principal 01", new BigDecimal("250.00"), "ABIERTA");
                var desglose = FXCollections.observableArrayList(
                        new ArqueoCajaController.FilaCierreZ("EFECTIVO", "18", "S/ 680.50"),
                        new ArqueoCajaController.FilaCierreZ("YAPE / PLIN", "14", "S/ 420.00"),
                        new ArqueoCajaController.FilaCierreZ("TARJETA", "9", "S/ 380.00"),
                        new ArqueoCajaController.FilaCierreZ("TOTAL CONSOLIDADO Z", "41", "S/ 1480.50")
                );
                ctrl.aplicarVistaArqueoPrueba(
                        cajaAbierta,
                        new BigDecimal("250.00"),
                        new BigDecimal("1290.50"),
                        new BigDecimal("60.00"),
                        new BigDecimal("1480.50"),
                        desglose,
                        criticos
                );

                assertEquals(3, ctrl.getItemsStockCritico().size());
                Label lblCrit = (Label) vista.root().lookup("#lblResumenStockCritico");
                assertEquals("2 AGOTADO(S) · 1 MÍNIMO", lblCrit.getText());

                if (vista.root().lookup(".scroll-pane") instanceof ScrollPane sp) {
                    sp.setVvalue(1.0);
                }
                UiScreenshotTestSupport.capturarEvidencia(
                        EVIDENCE_DIR, vista.root(), "08_caja_cierre_z_stock_critico_v520.png");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static ItemInventarioFisico crearItemConteo(long idInsumo, String codigo, String nombre,
                                                        String unidad, BigDecimal stockSistema,
                                                        BigDecimal costoUnitario, String obs) {
        ItemInventarioFisico item = new ItemInventarioFisico();
        item.setIdInsumo(idInsumo);
        item.setCodigo(codigo);
        item.setNombre(nombre);
        item.setUnidadMedida(unidad);
        item.setStockSistema(stockSistema);
        item.setStockContado(stockSistema);
        item.setCostoUnitario(costoUnitario);
        item.setObservacion(obs);
        return item;
    }

    private static Venta crearVentaDemo(int idVenta, Integer idPedido, String metodo, BigDecimal total, String estado) {
        Venta v = new Venta();
        v.setIdVenta(idVenta);
        v.setIdPedido(idPedido);
        v.setMetodoPago(metodo);
        v.setTotal(total);
        v.setEstado(estado);
        v.setFechaHora(LocalDateTime.of(2026, 11, 13, 13, 15).minusMinutes(1050 - idVenta));
        return v;
    }

    private static DetalleVenta crearDetalleDemo(int idDetalle, int idVenta, int idProd,
                                                 String nombre, int cant, BigDecimal subtotal, String estado) {
        DetalleVenta d = new DetalleVenta();
        d.setIdDetalleVenta(idDetalle);
        d.setIdVenta(idVenta);
        d.setIdProducto(idProd);
        d.setNombreProducto(nombre);
        d.setCantidad(cant);
        d.setPrecioUnitario(subtotal);
        d.setSubtotal(subtotal);
        d.setEstado(estado);
        return d;
    }

    private static void asignarCampo(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
