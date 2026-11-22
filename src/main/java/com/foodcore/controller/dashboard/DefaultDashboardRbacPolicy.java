package com.foodcore.controller.dashboard;

import com.foodcore.controller.AuditoriaController;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public class DefaultDashboardRbacPolicy implements DashboardRbacPolicy {
    private final Function<String, Set<Permiso>> proveedorPermisos;

    public DefaultDashboardRbacPolicy() {
        this(AutorizacionService::permisosPorDefectoParaRol);
    }

    public DefaultDashboardRbacPolicy(Function<String, Set<Permiso>> proveedorPermisos) {
        this.proveedorPermisos = proveedorPermisos != null
                ? proveedorPermisos : AutorizacionService::permisosPorDefectoParaRol;
    }

    @Override
    public PoliticaAccesoDashboard evaluarAcceso(Usuario user) {
        if (user == null || user.getNombreRol() == null || user.getNombreRol().isBlank()) {
            return new PoliticaAccesoDashboard(false, Set.of(), Set.of(), null);
        }

        String rol = user.getNombreRol().trim().toUpperCase(Locale.ROOT);
        EnumSet<NavSection> secciones = EnumSet.noneOf(NavSection.class);
        EnumSet<NavDestination> destinos = EnumSet.noneOf(NavDestination.class);
        NavDestination inicial = null;

        if (rol.equals("ADMIN") || rol.equals("ADMINISTRADOR") || rol.contains("ADMIN")) {
            secciones = EnumSet.allOf(NavSection.class);
            destinos = EnumSet.allOf(NavDestination.class);
            inicial = NavDestination.DASHBOARD;
        } else if (rol.contains("SUPERVISOR")) {
            secciones.addAll(EnumSet.of(NavSection.OPERACIONES, NavSection.COMERCIAL, NavSection.FINANZAS,
                    NavSection.RRHH, NavSection.BACKOFFICE));
            destinos.addAll(EnumSet.allOf(NavDestination.class));
            destinos.removeAll(EnumSet.of(NavDestination.USUARIOS, NavDestination.ROLES, NavDestination.CONFIGURACION,
                    NavDestination.ESTADO_SISTEMA, NavDestination.MANTENIMIENTO, NavDestination.AUDITORIA));
            inicial = NavDestination.DASHBOARD;
        } else if (rol.contains("CAJERO")) {
            secciones.addAll(EnumSet.of(NavSection.OPERACIONES, NavSection.RRHH));
            destinos.addAll(EnumSet.of(NavDestination.SALON, NavDestination.RESERVAS, NavDestination.DELIVERY,
                    NavDestination.POS, NavDestination.CAJA, NavDestination.HISTORIAL_VENTAS, NavDestination.RELOJ,
                    NavDestination.MI_HORARIO, NavDestination.SOLICITUDES));
            inicial = NavDestination.POS;
        } else if (rol.contains("MOZO") || rol.contains("MESERO")) {
            secciones.addAll(EnumSet.of(NavSection.OPERACIONES, NavSection.RRHH));
            destinos.addAll(EnumSet.of(NavDestination.SALON, NavDestination.RESERVAS, NavDestination.DELIVERY,
                    NavDestination.POS, NavDestination.RELOJ, NavDestination.MI_HORARIO, NavDestination.SOLICITUDES));
            inicial = NavDestination.SALON;
        } else if (rol.contains("COCIN")) {
            secciones.addAll(EnumSet.of(NavSection.OPERACIONES, NavSection.RRHH));
            destinos.addAll(EnumSet.of(NavDestination.KDS, NavDestination.RELOJ, NavDestination.MI_HORARIO,
                    NavDestination.SOLICITUDES));
            inicial = NavDestination.KDS;
        } else if (rol.contains("ALMACEN")) {
            secciones.addAll(EnumSet.of(NavSection.BACKOFFICE, NavSection.RRHH));
            destinos.addAll(EnumSet.of(NavDestination.INVENTARIO, NavDestination.INVENTARIO_FISICO,
                    NavDestination.MERMAS, NavDestination.ANALISIS_MERMAS, NavDestination.KARDEX,
                    NavDestination.RELOJ, NavDestination.MI_HORARIO, NavDestination.SOLICITUDES));
            inicial = NavDestination.INVENTARIO;
        } else {
            return evaluarPorPermisos(rol);
        }
        return new PoliticaAccesoDashboard(true, Set.copyOf(secciones), Set.copyOf(destinos), inicial);
    }

    private PoliticaAccesoDashboard evaluarPorPermisos(String rol) {
        Set<Permiso> permisos = proveedorPermisos.apply(rol);
        if (permisos == null || permisos.isEmpty()) {
            return new PoliticaAccesoDashboard(false, Set.of(), Set.of(), null);
        }
        EnumSet<NavSection> secciones = EnumSet.noneOf(NavSection.class);
        EnumSet<NavDestination> destinos = EnumSet.noneOf(NavDestination.class);
        NavDestination inicial = null;

        if (permisos.contains(Permiso.PEDIDO_CREAR) || permisos.contains(Permiso.PEDIDO_ADICIONAR)) {
            secciones.add(NavSection.OPERACIONES);
            destinos.addAll(EnumSet.of(NavDestination.SALON, NavDestination.RESERVAS, NavDestination.DELIVERY,
                    NavDestination.POS));
            inicial = NavDestination.SALON;
        }
        if (permisos.contains(Permiso.VENTA_COBRAR) || permisos.contains(Permiso.CAJA_APERTURA)
                || permisos.contains(Permiso.CAJA_CIERRE)) {
            secciones.add(NavSection.OPERACIONES);
            destinos.addAll(EnumSet.of(NavDestination.POS, NavDestination.CAJA, NavDestination.HISTORIAL_VENTAS));
            if (inicial == null) {
                inicial = NavDestination.POS;
            }
        }
        if (permisos.contains(Permiso.KDS_OPERAR)) {
            secciones.add(NavSection.OPERACIONES);
            destinos.add(NavDestination.KDS);
            if (inicial == null) {
                inicial = NavDestination.KDS;
            }
        }
        if (permisos.contains(Permiso.INSUMO_GESTIONAR)) {
            secciones.add(NavSection.BACKOFFICE);
            destinos.addAll(EnumSet.of(NavDestination.INVENTARIO, NavDestination.INVENTARIO_FISICO, NavDestination.MENU,
                    NavDestination.RECETAS, NavDestination.MERMAS, NavDestination.ANALISIS_MERMAS));
            if (inicial == null) {
                inicial = NavDestination.INVENTARIO;
            }
        }
        if (permisos.contains(Permiso.KARDEX_CONSULTAR)) {
            secciones.add(NavSection.BACKOFFICE);
            destinos.add(NavDestination.KARDEX);
            if (inicial == null) {
                inicial = NavDestination.KARDEX;
            }
        }
        if (permisos.contains(Permiso.USUARIO_GESTIONAR)) {
            secciones.add(NavSection.SISTEMA);
            destinos.addAll(EnumSet.of(NavDestination.USUARIOS, NavDestination.ROLES));
        }
        if (permisos.contains(Permiso.CONFIG_RED)) {
            secciones.add(NavSection.SISTEMA);
            destinos.add(NavDestination.CONFIGURACION);
        }
        secciones.add(NavSection.RRHH);
        destinos.addAll(EnumSet.of(NavDestination.RELOJ, NavDestination.MI_HORARIO, NavDestination.SOLICITUDES));
        return new PoliticaAccesoDashboard(true, Set.copyOf(secciones), Set.copyOf(destinos), inicial);
    }

    @Override
    public boolean puedeAccederAuditoria(Usuario user) {
        return AuditoriaController.esUsuarioAdmin(user);
    }

    @Override
    public boolean puedeAccederEstadoSistema(Usuario user) {
        return user != null && user.getNombreRol() != null
                && user.getNombreRol().toUpperCase(Locale.ROOT).contains("ADMIN");
    }
}