package com.foodcore.controller;

import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.MovimientoPago;
import com.foodcore.model.PlanillaConsolidada;
import com.foodcore.model.Promocion;
import com.foodcore.model.ReservaMesa;
import com.foodcore.model.Usuario;
import com.foodcore.service.PlanillaService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.ReservasService;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.util.ToastService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida la migración a {@code UiAsyncExecutor} del Lote 1 sin bloquear el hilo JavaFX,
 * garantizando bloqueo anti-reentrancia y rehabilitación ante éxito o fallo.
 */
class Batch1ControllersAsyncUnitTest {

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @Test
    void todosLosControladoresDelLote1UsanUiAsyncExecutorYLifecycleAwareSinDaoNiServiceImpl() throws Exception {
        List<String> controladoresLote1 = List.of(
                "PromocionesController",
                "ReservasController",
                "PlanillaController",
                "OnboardingWizardController",
                "MenuQrModalController",
                "ExpedienteEmpleadoController"
        );
        for (String nombre : controladoresLote1) {
            Path archivo = Path.of("src", "main", "java", "com", "foodcore", "controller", nombre + ".java");
            String source = Files.readString(archivo);
            assertTrue(source.contains("UiAsyncExecutor"),
                    nombre + " debe delegar I/O en UiAsyncExecutor");
            assertFalse(source.contains("com.foodcore.dao.impl."),
                    nombre + " no debe importar dao.impl");
            assertFalse(source.contains("com.foodcore.service.impl."),
                    nombre + " no debe importar service.impl");
            Class<?> clazz = Class.forName("com.foodcore.controller." + nombre);
            assertTrue(LifecycleAwareController.class.isAssignableFrom(clazz),
                    nombre + " debe implementar LifecycleAwareController");
        }
    }

    @Test
    void planillaControllerEjecutaRegistroFueraDelFxThreadYRehabilitaBotonEnExitoYError() throws Exception {
        CountDownLatch enCursoEnWorker = new CountDownLatch(1);
        CountDownLatch liberarWorker = new CountDownLatch(1);
        AtomicBoolean fallarRegistro = new AtomicBoolean(false);
        AtomicBoolean workerOffFx = new AtomicBoolean(false);
        AtomicInteger llamadasRegistro = new AtomicInteger(0);
        List<MovimientoPago> movimientos = new CopyOnWriteArrayList<>();

        Usuario emp = new Usuario(10, 10, 2, "cajero1", "hash", true);
        emp.setNombreEmpleado("Ana Torres");

        PlanillaService fakeService = new PlanillaService() {
            @Override
            public List<Usuario> listarColaboradoresActivos() {
                return List.of(emp);
            }

            @Override
            public MovimientoPago registrarMovimiento(int idUsuario, String tipoMovimiento, String concepto,
                                                      BigDecimal monto, LocalDate fecha, Integer registradoPor,
                                                      String observaciones) {
                workerOffFx.set(!Platform.isFxApplicationThread());
                int llamada = llamadasRegistro.incrementAndGet();
                if (llamada == 1) {
                    enCursoEnWorker.countDown();
                    try {
                        if (!liberarWorker.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timeout esperando liberación de registro");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                }
                if (fallarRegistro.get()) {
                    throw new IllegalStateException("Error simulado al guardar movimiento");
                }
                MovimientoPago mov = new MovimientoPago();
                mov.setIdMovimiento(100L + llamada);
                mov.setIdUsuario(idUsuario);
                mov.setNombreEmpleado("Ana Torres");
                mov.setPeriodoMes(YearMonth.from(fecha).toString());
                mov.setTipoMovimiento(tipoMovimiento);
                mov.setConcepto(concepto);
                mov.setMonto(monto);
                movimentosAdd(mov);
                return mov;
            }

            private void movimentosAdd(MovimientoPago mov) {
                movimientos.add(mov);
            }

            @Override
            public void eliminarMovimiento(long idMovimiento) {
            }

            @Override
            public List<MovimientoPago> listarMovimientosPorPeriodo(String periodoMes) {
                return List.copyOf(movimientos);
            }

            @Override
            public List<PlanillaConsolidada> calcularPlanillaConsolidada(String periodoMes) {
                return List.of();
            }

            @Override
            public File exportarPlanillaExcel(List<PlanillaConsolidada> filas, String periodoMes, File destino) {
                return destino;
            }

            @Override
            public File exportarPlanillaPdf(List<PlanillaConsolidada> filas, String periodoMes, File destino) {
                return destino;
            }
        };

        AtomicReference<PlanillaController> ctrlRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/PlanillaView.fxml"));
                    loader.setControllerFactory(type -> new PlanillaController(fakeService));
                    Parent root = loader.load();
                    new Scene(root, 1120, 768);
                    ctrlRef.set(loader.getController());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            PlanillaController ctrl = ctrlRef.get();
            JavaFxTestSupport.awaitCondition(
                    () -> ctrl.getCbColaborador().getValue() != null,
                    5L,
                    "Colaboradores de PlanillaController deben cargarse asíncronamente");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getTxtConcepto().setText("Bono async test");
                ctrl.getTxtMonto().setText("75.00");
                ctrl.handleRegistrarMovimiento();
            });

            assertTrue(enCursoEnWorker.await(5, TimeUnit.SECONDS), "El worker debe iniciar el registro");
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertTrue(ctrl.getBtnRegistrarMovimiento().isDisabled(),
                        "Botón debe deshabilitarse durante la operación asíncrona");
                // Segundo clic en pleno vuelo debe ser ignorado por guardia AtomicBoolean
                ctrl.handleRegistrarMovimiento();
            });

            liberarWorker.countDown();
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnRegistrarMovimiento().isDisabled()
                            && !ctrl.getListaMovimientos().isEmpty(),
                    5L,
                    "Al finalizar con éxito debe rehabilitar botón y actualizar movimientos");

            assertTrue(workerOffFx.get(), "registrarMovimiento debe ejecutarse fuera del hilo FX");
            assertEquals(1, llamadasRegistro.get(), "Doble clic en vuelo no debe generar doble llamada");
            assertTrue(ctrl.getLblEstadoPlanilla().getText().contains("registrado correctamente"));

            // Verificar flujo de fallo explícito y rehabilitación del botón
            fallarRegistro.set(true);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getTxtConcepto().setText("Bono fallido");
                ctrl.getTxtMonto().setText("50.00");
                ctrl.handleRegistrarMovimiento();
            });

            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnRegistrarMovimiento().isDisabled()
                            && ctrl.getLblEstadoPlanilla().getText().contains("Error simulado al guardar movimiento"),
                    5L,
                    "Ante fallo de servicio debe rehabilitar el botón y mostrar error explícito");
        } finally {
            liberarWorker.countDown();
            if (ctrlRef.get() != null) {
                ctrlRef.get().detenerRecursos();
            }
        }
    }

    @Test
    void reservasControllerSentarReservaCorreEnWorkerYRehabilitaBotonEnErrorYExito() throws Exception {
        CountDownLatch workerEnVuelo = new CountDownLatch(1);
        CountDownLatch liberarWorker = new CountDownLatch(1);
        AtomicBoolean fallarActualizar = new AtomicBoolean(true);
        AtomicBoolean workerOffFx = new AtomicBoolean(false);
        AtomicInteger llamadasActualizar = new AtomicInteger(0);
        AtomicReference<String> estadoEnServicio = new AtomicReference<>("CONFIRMADA");

        ReservasService fakeService = new ReservasService(null) {
            @Override
            public List<ReservaMesa> listarPorFecha(LocalDate fecha) {
                ReservaMesa r = new ReservaMesa(3L, "Carlos Ruiz", "999888777",
                        fecha, LocalTime.of(20, 0), 4, "Ventana");
                r.setIdReserva(77);
                r.setNumeroMesa("03");
                r.setEstado(estadoEnServicio.get());
                return List.of(r);
            }

            @Override
            public boolean actualizarEstado(int idReserva, String estado) throws SQLException {
                workerOffFx.set(!Platform.isFxApplicationThread());
                int n = llamadasActualizar.incrementAndGet();
                if (n == 1) {
                    workerEnVuelo.countDown();
                    try {
                        if (!liberarWorker.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timeout liberando actualizarEstado");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                }
                if (fallarActualizar.get()) {
                    throw new SQLException("Conexión caída simulada en reservas");
                }
                estadoEnServicio.set(estado);
                return true;
            }
        };

        AtomicReference<ReservasController> ctrlRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/ReservasView.fxml"));
                    loader.setControllerFactory(type -> new ReservasController(fakeService));
                    Parent root = loader.load();
                    new Scene(root, 1120, 768);
                    ctrlRef.set(loader.getController());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            ReservasController ctrl = ctrlRef.get();
            JavaFxTestSupport.awaitCondition(
                    () -> ctrl.getListaReservas().size() == 1,
                    5L,
                    "ReservasController debe poblar listaReservas asíncronamente");

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getTblReservas().getSelectionModel().selectFirst();
                ctrl.handleSentarReserva();
            });

            assertTrue(workerEnVuelo.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertTrue(ctrl.getBtnSentar().isDisabled(),
                        "btnSentar debe bloquearse durante actualizarEstado");
                ctrl.handleSentarReserva();
            });

            liberarWorker.countDown();
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnSentar().isDisabled()
                            && ToastService.getUltimoTipoToast() == ToastService.TipoToast.ERROR,
                    5L,
                    "Al fallar actualizarEstado debe rehabilitar btnSentar y notificar error");

            assertTrue(workerOffFx.get());
            assertEquals(1, llamadasActualizar.get());
            assertEquals("CONFIRMADA", ctrl.getListaReservas().get(0).getEstado());

            fallarActualizar.set(false);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getTblReservas().getSelectionModel().selectFirst();
                ctrl.handleSentarReserva();
            });

            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnSentar().isDisabled()
                            && "SENTADA".equals(ctrl.getListaReservas().get(0).getEstado()),
                    5L,
                    "Al completar con éxito debe actualizar el estado a SENTADA y rehabilitar btnSentar");
        } finally {
            liberarWorker.countDown();
            if (ctrlRef.get() != null) {
                ctrlRef.get().detenerRecursos();
            }
        }
    }

    @Test
    void promocionesControllerNoVolteaEstadoSiFallaYRehabilitaBoton() throws Exception {
        CountDownLatch workerEnVuelo = new CountDownLatch(1);
        CountDownLatch liberarWorker = new CountDownLatch(1);
        AtomicBoolean fallarToggle = new AtomicBoolean(true);
        AtomicBoolean listarOffFx = new AtomicBoolean(false);
        AtomicBoolean cambiarOffFx = new AtomicBoolean(false);
        AtomicInteger llamadasCambiar = new AtomicInteger(0);

        Promocion promo = new Promocion(9, "PROMO-ASYNC", "Promo Async", "Desc",
                "PORCENTAJE", new BigDecimal("15.00"), "TOTAL_ORDEN",
                null, null, BigDecimal.ZERO, "LUN", null, null, true);

        PromocionService fakeService = new PromocionService(null) {
            @Override
            public List<Promocion> listarTodas() {
                listarOffFx.set(!Platform.isFxApplicationThread());
                return List.of(promo);
            }

            @Override
            public boolean cambiarEstado(int idPromocion, boolean activo) {
                cambiarOffFx.set(!Platform.isFxApplicationThread());
                int n = llamadasCambiar.incrementAndGet();
                if (n == 1) {
                    workerEnVuelo.countDown();
                    try {
                        if (!liberarWorker.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timeout liberando cambiarEstado");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                }
                if (fallarToggle.get()) {
                    throw new IllegalStateException("Error simulado en promociones");
                }
                return true;
            }
        };

        AtomicReference<PromocionesController> ctrlRef = new AtomicReference<>();
        AtomicReference<Button> triggerRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/com/foodcore/view/PromocionesView.fxml"));
                    loader.setControllerFactory(type -> new PromocionesController(fakeService));
                    Parent root = loader.load();
                    new Scene(root, 1080, 720);
                    ctrlRef.set(loader.getController());
                    triggerRef.set(new Button("Desactivar"));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            PromocionesController ctrl = ctrlRef.get();
            Button trigger = triggerRef.get();
            JavaFxTestSupport.awaitCondition(
                    () -> ctrl.getTblPromociones().getItems().size() == 1,
                    5L,
                    "PromocionesController debe cargar lista inicial en worker");
            assertTrue(listarOffFx.get());

            JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrl.toggleEstadoPromocion(promo, trigger));
            assertTrue(workerEnVuelo.await(5, TimeUnit.SECONDS));

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertTrue(trigger.isDisabled());
                ctrl.toggleEstadoPromocion(promo, trigger);
            });

            liberarWorker.countDown();
            JavaFxTestSupport.awaitCondition(
                    () -> !trigger.isDisabled() && ToastService.getUltimoTipoToast() == ToastService.TipoToast.ERROR,
                    5L,
                    "Ante error debe rehabilitar trigger y reportar error");

            assertTrue(cambiarOffFx.get());
            assertEquals(1, llamadasCambiar.get());
            assertTrue(promo.isActivo(), "No debe cambiar estado en UI si falló en servicio");

            fallarToggle.set(false);
            JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrl.toggleEstadoPromocion(promo, trigger));
            JavaFxTestSupport.awaitCondition(
                    () -> !trigger.isDisabled() && !promo.isActivo(),
                    5L,
                    "Al completar con éxito debe actualizar el estado de la promoción");
        } finally {
            liberarWorker.countDown();
            if (ctrlRef.get() != null) {
                ctrlRef.get().detenerRecursos();
            }
        }
    }
}
