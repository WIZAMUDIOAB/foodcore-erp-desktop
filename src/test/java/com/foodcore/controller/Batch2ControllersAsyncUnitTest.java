package com.foodcore.controller;

import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Asistencia;
import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.model.Vacaciones;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AusenciasVacacionesService;
import com.foodcore.service.AuthService;
import com.foodcore.service.HorariosService;
import com.foodcore.support.JavaFxTestSupport;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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
 * Valida la migración a {@code UiAsyncExecutor} del Lote 2 ({@link MiHorarioController},
 * {@link HorariosController}, {@link RelojAsistenciaController}, {@link MarcacionAsistenciaController}):
 * ejecución fuera del FX Thread, bloqueo anti-reentrancia, rehabilitación ante fallo
 * y descarte seguro de callbacks tras {@code detenerRecursos()}.
 */
class Batch2ControllersAsyncUnitTest {

    @BeforeAll
    static void initToolkit() {
        JavaFxTestSupport.iniciarToolkit();
    }

    @AfterEach
    void limpiarSesion() {
        AuthService.logout();
    }

    @Test
    void todosLosControladoresDelLote2UsanUiAsyncExecutorYLifecycleAwareSinDaoNiServiceImpl() throws Exception {
        List<String> controladoresLote2 = List.of(
                "MiHorarioController",
                "HorariosController",
                "RelojAsistenciaController",
                "MarcacionAsistenciaController"
        );
        for (String nombre : controladoresLote2) {
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
    void miHorarioControllerCargaYSolicitudEnWorkerRehabilitaYDescartaTrasDetener() throws Exception {
        Usuario cajero = new Usuario(2, 2, 2, "cajero", "hash", true);
        cajero.setNombreEmpleado("Lucía Cajera");
        cajero.setNombreRol("CAJERO");
        cajero.setSueldoBase(new BigDecimal("1500.00"));
        AuthService.setUsuarioSesionActual(cajero);

        CountDownLatch workerEnCurso = new CountDownLatch(1);
        CountDownLatch liberarWorker = new CountDownLatch(1);
        AtomicBoolean fallarSolicitud = new AtomicBoolean(true);
        AtomicBoolean solicitudOffFx = new AtomicBoolean(false);
        AtomicBoolean cargaOffFx = new AtomicBoolean(false);
        AtomicInteger llamadasRegistro = new AtomicInteger(0);
        List<SolicitudAusencia> solicitudes = new CopyOnWriteArrayList<>();

        HorariosService fakeHorarios = new HorariosService(null, null) {
            @Override
            public List<TurnoProgramado> listarPorUsuario(int idUsuario) {
                cargaOffFx.set(!Platform.isFxApplicationThread());
                return List.of();
            }
        };
        AsistenciaService fakeAsistencia = new AsistenciaService(null, null, null) {
            @Override
            public List<Asistencia> listarAsistenciaPorFecha(LocalDate fecha) {
                return List.of();
            }
        };
        AusenciasVacacionesService fakeAusencias = new AusenciasVacacionesService() {
            @Override
            public Vacaciones obtenerSaldoVacaciones(int idUsuario, int anio) {
                Vacaciones v = new Vacaciones();
                v.setIdUsuario(idUsuario);
                v.setPeriodoAnio(anio);
                v.setDiasAsignados(15);
                v.setDiasGozados(2);
                v.setDiasPendientesAprobacion(0);
                return v;
            }

            @Override
            public int calcularDiasSolicitados(LocalDate inicio, LocalDate fin) {
                return (int) ChronoUnit.DAYS.between(inicio, fin) + 1;
            }

            @Override
            public SolicitudAusencia registrarSolicitud(int idUsuario, String tipo, LocalDate inicio,
                                                      LocalDate fin, String motivo) {
                solicitudOffFx.set(!Platform.isFxApplicationThread());
                int n = llamadasRegistro.incrementAndGet();
                if (n == 1) {
                    workerEnCurso.countDown();
                    awaitLatch(liberarWorker);
                }
                if (fallarSolicitud.get()) {
                    throw new IllegalStateException("Fallo simulado en vacaciones");
                }
                SolicitudAusencia sol = new SolicitudAusencia(
                        10 + n, idUsuario, tipo, inicio, fin, motivo, "PENDIENTE");
                solicitudes.add(sol);
                return sol;
            }

            @Override
            public List<SolicitudAusencia> listarTodas() {
                return List.copyOf(solicitudes);
            }

            @Override
            public List<SolicitudAusencia> listarPorUsuario(int idUsuario) {
                return List.copyOf(solicitudes);
            }

            @Override
            public void aprobarSolicitud(int idSolicitud, String observacion, Integer idAprobador) {
            }

            @Override
            public void rechazarSolicitud(int idSolicitud, String observacion, Integer idAprobador) {
            }
        };

        AtomicReference<MiHorarioController> ctrlRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MiHorarioView.fxml"));
                    loader.setControllerFactory(type ->
                            new MiHorarioController(fakeHorarios, fakeAsistencia, fakeAusencias));
                    Parent root = loader.load();
                    new Scene(root, 1080, 768);
                    ctrlRef.set(loader.getController());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            MiHorarioController ctrl = ctrlRef.get();
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getListaBoletas().isEmpty()
                            && ctrl.getLblVacDisponibles().getText().contains("Disponibles: 13"),
                    5L,
                    "MiHorarioController debe cargar boletas y saldo asíncronamente");
            assertTrue(cargaOffFx.get());

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getTxtVacMotivo().setText("Descanso programado");
                ctrl.handleSolicitarVacaciones();
            });

            assertTrue(workerEnCurso.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertTrue(ctrl.getBtnSolicitarVacaciones().isDisabled());
                ctrl.handleSolicitarVacaciones();
            });

            liberarWorker.countDown();
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnSolicitarVacaciones().isDisabled()
                            && ctrl.getLblVacEstadoMsg().getText().contains("Fallo simulado en vacaciones"),
                    5L,
                    "Ante error debe rehabilitar btnSolicitarVacaciones y mostrar el fallo");
            assertTrue(solicitudOffFx.get());
            assertEquals(1, llamadasRegistro.get());

            fallarSolicitud.set(false);
            JavaFxTestSupport.runOnFxThreadAndWait(ctrl::handleSolicitarVacaciones);
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnSolicitarVacaciones().isDisabled()
                            && ctrl.getListaMisVacaciones().size() == 1,
                    5L,
                    "Al completar con éxito debe actualizar listaMisVacaciones");

            // Verificar descarte tras detenerRecursos()
            ctrl.detenerRecursos();
            JavaFxTestSupport.runOnFxThreadAndWait(ctrl::handleSolicitarVacaciones);
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnSolicitarVacaciones().isDisabled(),
                    5L,
                    "Debe rehabilitar btnSolicitarVacaciones sin alterar UI tras detenerRecursos()");
            assertEquals(1, ctrl.getListaMisVacaciones().size());
        } finally {
            liberarWorker.countDown();
            if (ctrlRef.get() != null) {
                ctrlRef.get().detenerRecursos();
            }
        }
    }

    @Test
    void horariosControllerGuardaPublicaEnWorkerRehabilitaBotonesYDescartaTardios() throws Exception {
        CountDownLatch guardarEnVuelo = new CountDownLatch(1);
        CountDownLatch liberarGuardar = new CountDownLatch(1);
        AtomicBoolean cargaOffFx = new AtomicBoolean(false);
        AtomicBoolean guardarOffFx = new AtomicBoolean(false);
        AtomicBoolean publicarOffFx = new AtomicBoolean(false);
        AtomicBoolean fallarGuardar = new AtomicBoolean(true);
        AtomicInteger llamadasGuardar = new AtomicInteger(0);
        AtomicInteger llamadasPublicar = new AtomicInteger(0);
        List<TurnoProgramado> turnos = new CopyOnWriteArrayList<>();

        Usuario colab = new Usuario(5, 5, 3, "mozo1", "hash", true);
        colab.setNombreEmpleado("Mario Mozo");
        colab.setNombreRol("MOZO");

        HorariosService fakeService = new HorariosService(null, null) {
            @Override
            public List<Usuario> listarColaboradoresDisponibles() {
                cargaOffFx.set(!Platform.isFxApplicationThread());
                return List.of(colab);
            }

            @Override
            public List<TurnoProgramado> listarSemana() {
                return List.copyOf(turnos);
            }

            @Override
            public List<String> evaluarAlertasCobertura() {
                return List.of("Sin cobertura sábado noche");
            }

            @Override
            public int asignarTurno(int idUsuario, String diaSemana, String turno, LocalTime inicio, LocalTime fin)
                    throws SQLException {
                guardarOffFx.set(!Platform.isFxApplicationThread());
                int n = llamadasGuardar.incrementAndGet();
                if (n == 1) {
                    guardarEnVuelo.countDown();
                    awaitLatch(liberarGuardar);
                }
                if (fallarGuardar.get()) {
                    throw new SQLException("Error BD asignando turno");
                }
                TurnoProgramado tp = new TurnoProgramado(10 + n, idUsuario, diaSemana, turno, inicio, fin);
                tp.setPublicado(true);
                tp.setNombreUsuario("Mario Mozo");
                tp.setRol("MOZO");
                turnos.add(tp);
                return tp.getIdTurnoProg();
            }

            @Override
            public boolean publicarHorariosSemana() {
                publicarOffFx.set(!Platform.isFxApplicationThread());
                llamadasPublicar.incrementAndGet();
                return !turnos.isEmpty();
            }
        };

        AtomicReference<HorariosController> ctrlRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/HorariosView.fxml"));
                    loader.setControllerFactory(type -> new HorariosController(fakeService));
                    Parent root = loader.load();
                    new Scene(root, 1080, 768);
                    ctrlRef.set(loader.getController());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            HorariosController ctrl = ctrlRef.get();
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getListColaboradores().getItems().isEmpty()
                            && ctrl.getGridSemana().getChildren().size() >= 14,
                    5L,
                    "HorariosController debe cargar colaboradores y grilla semanal asíncronamente");
            assertTrue(cargaOffFx.get());

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getCmbModalColaborador().setValue(colab);
                ctrl.getTxtModalInicio().setText("08:00");
                ctrl.getTxtModalFin().setText("16:00");
                ctrl.handleGuardarTurno();
            });

            assertTrue(guardarEnVuelo.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertTrue(ctrl.getBtnGuardarTurno().isDisabled());
                ctrl.handleGuardarTurno();
            });

            liberarGuardar.countDown();
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnGuardarTurno().isDisabled()
                            && ctrl.getLblTextoAlerta().getText().contains("Error BD asignando turno"),
                    5L,
                    "Debe rehabilitar btnGuardarTurno y mostrar el error SQL");
            assertTrue(guardarOffFx.get());
            assertEquals(1, llamadasGuardar.get());

            fallarGuardar.set(false);
            JavaFxTestSupport.runOnFxThreadAndWait(ctrl::handleGuardarTurno);
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnGuardarTurno().isDisabled() && turnos.size() == 1,
                    5L,
                    "Debe asignar el turno en worker y recargar grilla");

            JavaFxTestSupport.runOnFxThreadAndWait(ctrl::handlePublicarHorarios);
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnPublicarHorarios().isDisabled()
                            && ctrl.getLblTextoAlerta().getText().contains("publicados con éxito"),
                    5L,
                    "handlePublicarHorarios debe ejecutarse fuera de FX Thread y habilitar botón");
            assertTrue(publicarOffFx.get());
            assertEquals(1, llamadasPublicar.get());

            ctrl.detenerRecursos();
            JavaFxTestSupport.runOnFxThreadAndWait(ctrl::handlePublicarHorarios);
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnPublicarHorarios().isDisabled(),
                    5L,
                    "Debe rehabilitar botón aun tras detenerRecursos()");
        } finally {
            liberarGuardar.countDown();
            if (ctrlRef.get() != null) {
                ctrlRef.get().detenerRecursos();
            }
        }
    }

    @Test
    void relojAsistenciaControllerEvitaDobleMarcajeYDescartaCallbackTrasDetenerRecursos() throws Exception {
        CountDownLatch marcajeEnVuelo = new CountDownLatch(1);
        CountDownLatch liberarMarcaje = new CountDownLatch(1);
        CountDownLatch marcajeTardioEnVuelo = new CountDownLatch(1);
        CountDownLatch liberarMarcajeTardio = new CountDownLatch(1);
        AtomicBoolean fallarMarcaje = new AtomicBoolean(true);
        AtomicBoolean marcajeOffFx = new AtomicBoolean(false);
        AtomicInteger llamadasMarcaje = new AtomicInteger(0);

        Usuario emp = new Usuario(8, 8, 2, "cajero", "hash", true);
        emp.setNombreEmpleado("Elena Cajera");
        emp.setNombreRol("CAJERO");

        AsistenciaService fakeService = new AsistenciaService(null, null, null) {
            @Override
            public ResultadoMarcaje registrarMarcajeKiosco(String credencial) throws SQLException {
                marcajeOffFx.set(!Platform.isFxApplicationThread());
                int n = llamadasMarcaje.incrementAndGet();
                if (n == 1) {
                    marcajeEnVuelo.countDown();
                    awaitLatch(liberarMarcaje);
                } else if (n == 3) {
                    marcajeTardioEnVuelo.countDown();
                    awaitLatch(liberarMarcajeTardio);
                }
                if (fallarMarcaje.get()) {
                    throw new SQLException("BD kiosco no disponible");
                }
                Asistencia a = new Asistencia();
                a.setTardanzaMinutos(0);
                return new ResultadoMarcaje(true, "ENTRADA", emp, a, "Entrada puntual registrada");
            }
        };

        AtomicReference<RelojAsistenciaController> ctrlRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/com/foodcore/view/RelojAsistenciaView.fxml"));
                    loader.setControllerFactory(type -> new RelojAsistenciaController(fakeService));
                    Parent root = loader.load();
                    new Scene(root, 1080, 768);
                    ctrlRef.set(loader.getController());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            RelojAsistenciaController ctrl = ctrlRef.get();
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertEquals(Animation.Status.RUNNING, ctrl.getRelojTimelineStatus());
                ctrl.getTxtCredencial().setText("45892147");
                ctrl.handleMarcar();
            });

            assertTrue(marcajeEnVuelo.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertTrue(ctrl.getBtnMarcar().isDisabled(),
                        "btnMarcar debe deshabilitarse para evitar doble marcación en kiosco");
                ctrl.handleMarcar();
            });

            liberarMarcaje.countDown();
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnMarcar().isDisabled()
                            && ctrl.getLblConfirmacionDetalle().getText().contains("BD kiosco no disponible"),
                    5L,
                    "Ante fallo debe rehabilitar btnMarcar y mostrar mensaje de error");
            assertTrue(marcajeOffFx.get());
            assertEquals(1, llamadasMarcaje.get());

            fallarMarcaje.set(false);
            JavaFxTestSupport.runOnFxThreadAndWait(ctrl::handleMarcar);
            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnMarcar().isDisabled()
                            && ctrl.getTxtCredencial().getText().isEmpty()
                            && ctrl.getLblConfirmacionBadge().getText().contains("ENTRADA REGISTRADA PUNTUAL"),
                    5L,
                    "Al completar marcaje exitoso debe limpiar credencial y mostrar confirmación");

            // Callback tardío tras detenerRecursos() no debe volver a mostrar cardConfirmacion ni dejar Timeline activo
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getCardConfirmacion().setVisible(false);
                ctrl.getTxtCredencial().setText("45892147");
                ctrl.handleMarcar();
            });
            assertTrue(marcajeTardioEnVuelo.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.runOnFxThreadAndWait(ctrl::detenerRecursos);
            liberarMarcajeTardio.countDown();

            JavaFxTestSupport.awaitCondition(
                    () -> !ctrl.getBtnMarcar().isDisabled(),
                    5L,
                    "btnMarcar debe reestablecerse sin mostrar cardConfirmacion tras detenerRecursos()");
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertFalse(ctrl.getCardConfirmacion().isVisible());
                assertEquals(Animation.Status.STOPPED, ctrl.getRelojTimelineStatus());
            });
        } finally {
            liberarMarcaje.countDown();
            liberarMarcajeTardio.countDown();
            if (ctrlRef.get() != null) {
                JavaFxTestSupport.runOnFxThreadAndWait(ctrlRef.get()::detenerRecursos);
            }
        }
    }

    @Test
    void marcacionAsistenciaControllerBloqueaDobleTapYRehabilitaBotonEnErrorYExito() throws Exception {
        CountDownLatch marcacionEnVuelo = new CountDownLatch(1);
        CountDownLatch liberarMarcacion = new CountDownLatch(1);
        AtomicBoolean offFx = new AtomicBoolean(false);
        AtomicBoolean fallarNegocio = new AtomicBoolean(true);
        AtomicInteger llamadas = new AtomicInteger(0);

        AuthService fakeAuth = (AuthService) Proxy.newProxyInstance(
                AuthService.class.getClassLoader(),
                new Class<?>[] {AuthService.class},
                (proxy, method, args) -> {
                    if ("marcarAsistenciaInteligente".equals(method.getName())) {
                        offFx.set(!Platform.isFxApplicationThread());
                        int n = llamadas.incrementAndGet();
                        if (n == 1) {
                            marcacionEnVuelo.countDown();
                            awaitLatch(liberarMarcacion);
                        }
                        if (fallarNegocio.get()) {
                            throw new NegocioException("DNI no encontrado en turno");
                        }
                        return "Entrada registrada a las 08:00";
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });

        AtomicReference<MarcacionAsistenciaController> ctrlRef = new AtomicReference<>();
        AtomicReference<Button> btnRef = new AtomicReference<>();
        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/com/foodcore/view/MarcacionAsistenciaView.fxml"));
                    loader.setControllerFactory(type -> new MarcacionAsistenciaController(fakeAuth));
                    Parent root = loader.load();
                    new Scene(root, 480, 560);
                    ctrlRef.set(loader.getController());
                    btnRef.set(new Button("Registrar"));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            MarcacionAsistenciaController ctrl = ctrlRef.get();
            Button btnTrigger = btnRef.get();

            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getTxtDni().setText("12345678");
                ctrl.handleRegistrar(new ActionEvent(btnTrigger, null));
            });

            assertTrue(marcacionEnVuelo.await(5, TimeUnit.SECONDS));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                assertTrue(btnTrigger.isDisabled());
                ctrl.handleRegistrar(new ActionEvent(btnTrigger, null));
            });

            liberarMarcacion.countDown();
            JavaFxTestSupport.awaitCondition(
                    () -> !btnTrigger.isDisabled()
                            && ctrl.getLblEstado().getText().contains("DNI no encontrado en turno"),
                    5L,
                    "Debe rehabilitar botón y mostrar NegocioException explícita");
            assertTrue(offFx.get());
            assertEquals(1, llamadas.get());

            fallarNegocio.set(false);
            JavaFxTestSupport.runOnFxThreadAndWait(() ->
                    ctrl.handleRegistrar(new ActionEvent(btnTrigger, null)));
            JavaFxTestSupport.awaitCondition(
                    () -> !btnTrigger.isDisabled()
                            && ctrl.getTxtDni().getText().isEmpty()
                            && ctrl.getLblEstado().getText().contains("Entrada registrada"),
                    5L,
                    "Debe limpiar txtDni y mostrar confirmación tras marcación exitosa");

            ctrl.detenerRecursos();
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                ctrl.getTxtDni().setText("99999999");
                ctrl.handleRegistrar(new ActionEvent(btnTrigger, null));
            });
            JavaFxTestSupport.awaitCondition(
                    () -> !btnTrigger.isDisabled(),
                    5L,
                    "Debe rehabilitar botón sin limpiar txtDni tras detenerRecursos()");
            assertEquals("99999999", ctrl.getTxtDni().getText());
        } finally {
            liberarMarcacion.countDown();
            if (ctrlRef.get() != null) {
                ctrlRef.get().detenerRecursos();
            }
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout esperando latch en prueba async");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
