package com.foodcore.controller.dashboard;

import com.foodcore.model.Usuario;
import com.foodcore.support.JavaFxTestSupport;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DashboardCoordinatorsUnitTest {
    @Test
    void resuelveBreadcrumbsConFallback() {
        DashboardNavigationCatalog catalog = new DashboardNavigationCatalog();
        assertEquals("Inicio > Salón > Mesas", catalog.resolverRutaBreadcrumbs("/com/foodcore/view/SalonMesasView.fxml", null));
        assertEquals("Inicio > Reportes", catalog.resolverRutaBreadcrumbs("desconocida", " Reportes "));
        assertEquals("Inicio > Operaciones > Dashboard Gerencial", catalog.resolverRutaBreadcrumbs(null, " "));
    }

    @Test
    void politicaRbacEsFailClosedYReconoceRoles() {
        DefaultDashboardRbacPolicy policy = new DefaultDashboardRbacPolicy(rol -> java.util.Set.of());
        assertFalse(policy.evaluarAcceso(null).autorizado());
        Usuario usuario = new Usuario();
        assertFalse(policy.evaluarAcceso(usuario).autorizado());
        assertRole(policy, "ADMIN", NavDestination.DASHBOARD);
        assertRole(policy, "SUPERVISOR", NavDestination.DASHBOARD);
        assertRole(policy, "CAJERO", NavDestination.POS);
        assertRole(policy, "MOZO", NavDestination.SALON);
        assertRole(policy, "MESERO", NavDestination.SALON);
        assertRole(policy, "COCINERO", NavDestination.KDS);
        assertRole(policy, "ALMACEN", NavDestination.INVENTARIO);
    }

    private void assertRole(DefaultDashboardRbacPolicy policy, String role, NavDestination initial) {
        Usuario usuario = new Usuario();
        usuario.setNombreRol(role);
        assertEquals(initial, policy.evaluarAcceso(usuario).destinoInicial());
    }

    @Test
    void estadosRedYLifecycleAceptanSinEstadoJavafx() {
        DashboardSyncIndicatorPresenter presenter = new DashboardSyncIndicatorPresenter();
        assertEquals("ONLINE", presenter.construirEstadoRed(true, 0).texto());
        assertEquals("ONLINE (2)", presenter.construirEstadoRed(true, 2).texto());
        assertEquals("OFFLINE", presenter.construirEstadoRed(false, 0).texto());
        assertEquals("OFFLINE (2 pend)", presenter.construirEstadoRed(false, 2).texto());
        assertEquals("status-dot-online", presenter.construirEstadoRed(true, 0).dotStyleClass());
        assertEquals("badge-pill-danger", presenter.construirEstadoRed(false, 0).badgeStyleClass());
        DashboardViewLifecycleHelper helper = new DashboardViewLifecycleHelper();
        assertDoesNotThrow(() -> helper.detenerControladorActivo(null));
        assertDoesNotThrow(() -> helper.detenerControladorActivo(new Object()));
    }

    @Test
    void detenerControladorActivoInvocaDetenerRecursosEnLifecycleAwareController() {
        DashboardViewLifecycleHelper helper = new DashboardViewLifecycleHelper();
        AtomicInteger invocaciones = new AtomicInteger(0);
        LifecycleAwareController controlador = invocaciones::incrementAndGet;

        helper.detenerControladorActivo(controlador);

        assertEquals(1, invocaciones.get());
        assertDoesNotThrow(() -> helper.detenerControladorActivo(null));
        assertDoesNotThrow(() -> helper.detenerControladorActivo(new Object()));
    }

    @Test
    void detenerControladorActivoDetieneTimelineRealDeJavaFx() throws Exception {
        JavaFxTestSupport.iniciarToolkit();
        DashboardViewLifecycleHelper helper = new DashboardViewLifecycleHelper();
        AtomicReference<Timeline> timelineRef = new AtomicReference<>();

        try {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200), e -> { }));
                timeline.setCycleCount(Animation.INDEFINITE);
                timeline.play();
                timelineRef.set(timeline);

                assertEquals(Animation.Status.RUNNING, timeline.getStatus());
                LifecycleAwareController controlador = timeline::stop;
                helper.detenerControladorActivo(controlador);
                assertEquals(Animation.Status.STOPPED, timeline.getStatus());
            });
        } finally {
            JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                Timeline timeline = timelineRef.get();
                if (timeline != null) {
                    timeline.stop();
                }
            });
        }
    }
}
