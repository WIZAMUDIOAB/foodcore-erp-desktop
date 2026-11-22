package com.foodcore.controller.dashboard;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardNavigationCatalog {
    private static final String BREADCRUMB_INICIAL = "Inicio > Operaciones > Dashboard Gerencial";
    private final Map<String, String> breadcrumbs = Arrays.stream(NavDestination.values())
            .collect(Collectors.toUnmodifiableMap(NavDestination::getFxmlPath, NavDestination::getBreadcrumb,
                    (first, second) -> first));

    public DashboardNavigationCatalog() {
    }

    public String resolverRutaBreadcrumbs(String fxmlPath, String textoBotonFallback) {
        String breadcrumb = fxmlPath != null ? breadcrumbs.get(fxmlPath) : null;
        if (breadcrumb != null) {
            return breadcrumb;
        }
        if (textoBotonFallback != null && !textoBotonFallback.isBlank()) {
            return "Inicio > " + textoBotonFallback.trim();
        }
        return BREADCRUMB_INICIAL;
    }
}