package com.foodcore.api.security;

import com.foodcore.api.ApiState;
import io.javalin.http.Context;

import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Filtro central de seguridad HTTP para {@code /api/*}:
 * rate limiting en autenticación, validación de JWT Bearer real y control de acceso por rol (RBAC).
 * API 100% stateless sin cookies de sesión HTTP, por lo que CSRF basado en cookies no aplica.
 */
public final class ApiSecurityFilter {

    private static final Logger LOGGER = Logger.getLogger(ApiSecurityFilter.class.getName());
    public static final String ATTR_AUTH_CLAIMS = "authClaims";

    private static final Set<String> ROLES_OPERATIVOS = Set.of("MOZO", "CAJERO", "COCINA", "ADMIN", "SUPERVISOR");
    private static final Set<String> ROLES_SALON = Set.of("MOZO", "CAJERO", "ADMIN", "SUPERVISOR");
    private static final Set<String> ROLES_KDS = Set.of("COCINA", "ADMIN", "SUPERVISOR");
    private static final Set<String> ROLES_CAJA = Set.of("CAJERO", "ADMIN", "SUPERVISOR");
    private static final Set<String> ROLES_ADMIN = Set.of("ADMIN");

    private final ApiState state;
    private final JwtService jwtService;
    private final AuthRateLimiter rateLimiter;

    public ApiSecurityFilter(ApiState state, JwtService jwtService, AuthRateLimiter rateLimiter) {
        this.state = state;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    public void handle(Context ctx) {
        String path = normalizarPath(ctx.path());
        String method = ctx.method().name();
        String clientIp = ctx.req().getRemoteAddr();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return;
        }

        if (path.startsWith("/api/auth/")) {
            if (!rateLimiter.tryConsume(clientIp)) {
                LOGGER.warning(() -> String.format(
                    "Rate limit excedido en autenticacion (IP=%s, %s %s)",
                    clientIp, method, path
                ));
                ctx.header("Retry-After", String.valueOf(rateLimiter.getRetryAfterSeconds()));
                state.responderError(ctx, 429, "Demasiados intentos de autenticación. Espere 60 segundos.");
                ctx.skipRemainingHandlers();
                return;
            }
            if ("/api/auth/login".equals(path) || "/api/auth/pin-login".equals(path)) {
                return;
            }
        }

        if (esEndpointPublico(method, path)) {
            return;
        }

        String authHeader = ctx.header("Authorization");
        String token = extraerBearerToken(authHeader);
        Optional<JwtService.JwtClaims> claimsOpt = jwtService.verificarToken(token);
        if (claimsOpt.isEmpty()) {
            LOGGER.warning(() -> String.format(
                "Intento no autenticado rechazado en endpoint protegido (IP=%s, %s %s)",
                clientIp, method, path
            ));
            state.responderError(ctx, 401, "Autenticación Bearer JWT requerida o token inválido.");
            ctx.skipRemainingHandlers();
            return;
        }

        JwtService.JwtClaims claims = claimsOpt.get();
        ctx.attribute(ATTR_AUTH_CLAIMS, claims);

        Set<String> rolesRequeridos = resolverRolesRequeridos(method, path);
        if (!rolesRequeridos.contains(claims.rol())) {
            LOGGER.warning(() -> String.format(
                "Acceso denegado por rol insuficiente (usuario=%s, rol=%s, IP=%s, %s %s)",
                claims.username(), claims.rol(), clientIp, method, path
            ));
            state.responderError(ctx, 403, "No tiene permisos suficientes para esta operación.");
            ctx.skipRemainingHandlers();
        }
    }

    private static boolean esEndpointPublico(String method, String path) {
        if ("/api/health".equals(path)) {
            return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        }
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/public/")) {
            return true;
        }
        return false;
    }

    private static Set<String> resolverRolesRequeridos(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && "/api/auth/pin-switch".equals(path)) {
            return ROLES_SALON;
        }
        if (path.startsWith("/api/kds/")) {
            return ROLES_KDS;
        }
        if ("PATCH".equalsIgnoreCase(method) && path.matches("^/api/pedidos/[^/]+/estado$")) {
            return ROLES_KDS;
        }
        if (path.startsWith("/api/dashboard/") || path.startsWith("/api/reportes/")
                || "/api/menu-digital/view".equals(path)) {
            return ROLES_ADMIN;
        }
        if (path.startsWith("/api/caja/")) {
            return ROLES_CAJA;
        }
        if ("PATCH".equalsIgnoreCase(method) && path.matches("^/api/productos/[^/]+/disponibilidad$")) {
            return ROLES_ADMIN;
        }
        if ("PATCH".equalsIgnoreCase(method) && path.matches("^/api/usuarios/[^/]+/pin$")) {
            return ROLES_ADMIN;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/usuarios/activos".equals(path)) {
            return ROLES_OPERATIVOS;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/pedidos".equals(path)) {
            return ROLES_SALON;
        }
        if ("GET".equalsIgnoreCase(method) && (path.equals("/api/pedidos")
                || path.startsWith("/api/pedidos/")
                || path.equals("/api/mesas")
                || path.startsWith("/api/productos"))) {
            return ROLES_OPERATIVOS;
        }
        return ROLES_ADMIN;
    }

    private static String extraerBearerToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String trimmed = authHeader.trim();
        if (trimmed.length() <= 7 || !trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return trimmed.substring(7).trim();
    }

    private static String normalizarPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        if (rawPath.length() > 1 && rawPath.endsWith("/")) {
            return rawPath.substring(0, rawPath.length() - 1);
        }
        return rawPath;
    }
}
