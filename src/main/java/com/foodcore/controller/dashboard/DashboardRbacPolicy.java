package com.foodcore.controller.dashboard;

import com.foodcore.model.Usuario;

import java.util.Set;

public interface DashboardRbacPolicy {
    record PoliticaAccesoDashboard(boolean autorizado, Set<NavSection> seccionesVisibles,
            Set<NavDestination> destinosVisibles, NavDestination destinoInicial) {
    }

    PoliticaAccesoDashboard evaluarAcceso(Usuario user);

    boolean puedeAccederAuditoria(Usuario user);

    boolean puedeAccederEstadoSistema(Usuario user);
}