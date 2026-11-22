package com.foodcore.controller.dashboard;

import com.foodcore.model.Usuario;
import org.junit.jupiter.api.Test;

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
}
