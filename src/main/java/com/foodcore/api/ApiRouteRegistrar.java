package com.foodcore.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.dao.HealthCheckDAO;
import com.foodcore.util.ConexionBD;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registrador de configuración Jetty, cadena de seguridad HTTP, rutas REST/PWA y readiness check sin JDBC directo.
 */
final class ApiRouteRegistrar {

    private static final Logger LOGGER = Logger.getLogger(ApiRouteRegistrar.class.getName());

    private ApiRouteRegistrar() {
    }

    static void configurarEstaticosYConectores(JavalinConfig config, int httpPort, int httpsPort,
            SslContextFactory.Server sslContextFactory) {
        config.startup.showJavalinBanner = false;
        if (esDevLoggingHabilitado()) {
            config.bundledPlugins.enableDevLogging();
        }

        File pwaDir = new File("pwa");
        if (pwaDir.exists() && pwaDir.isDirectory()) {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = pwaDir.getAbsolutePath();
                staticFiles.location = Location.EXTERNAL;
            });
        }
        if (ApiRouteRegistrar.class.getResource("/pwa/index.html") != null) {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/pwa";
                staticFiles.location = Location.CLASSPATH;
            });
        }

        config.jetty.addConnector((server, httpConfig) -> {
            ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(httpPort);
            return httpConnector;
        });

        if (httpsPort >= 0 && sslContextFactory != null) {
            config.jetty.addConnector((server, httpConfig) ->
                    ApiTlsConfigurator.crearConectorHttps(server, httpConfig, sslContextFactory, httpsPort));
        }
    }

    static void registrarMiddlewares(JavalinConfig config, ApiState state,
            CorsPolicy corsPolicy, ApiSecurityFilter securityFilter) {
        config.routes.before(ctx -> {
            PublicMenuServerHelper.aplicarCabecerasSeguridad(ctx);
            if (ApiSecurityFilter.esSolicitudDesdeCloudflare(ctx)
                    && !ApiSecurityFilter.esRutaPublicaTunel(ctx.method().name(), ctx.path())) {
                state.responderError(ctx, 403, "Ruta no permitida a través del túnel público.");
                ctx.skipRemainingHandlers();
                return;
            }
            corsPolicy.apply(ctx);
        });
        config.routes.options("/*", ctx -> {
            if (!ctx.res().isCommitted()) {
                ctx.status(204);
            }
        });
        config.routes.before("/api/*", securityFilter::handle);
    }

    static void registrarRutasSaludPublicasYAuth(JavalinConfig config, ApiState state,
            HealthCheckDAO healthCheckDAO, AuthController authController,
            SalonApiController salonController, ReportesController reportesController) {
        config.routes.get("/health", ctx -> handleHealthReadiness(ctx, state, healthCheckDAO));
        config.routes.head("/health", ctx -> handleHealthReadiness(ctx, state, healthCheckDAO));
        config.routes.get("/api/health", ctx -> handleHealth(ctx, state));
        config.routes.get("/api/public/productos", salonController::handleListarProductosPublicos);
        config.routes.get("/api/public/productos/disponibles",
                salonController::handleListarProductosDisponiblesPublicos);
        config.routes.get("/api/public/productos/{id}/imagen", salonController::handleImagenProducto);
        config.routes.get("/api/public/menu-digital", reportesController::handleMenuDigitalView);
        config.routes.post("/api/auth/login", authController::handleLogin);
        config.routes.post("/api/auth/pin-login", authController::handlePinLogin);
        config.routes.post("/api/auth/pin-switch", authController::handlePinSwitch);
        config.routes.post("/api/auth/force-logout", authController::handleForceLogout);
        config.routes.get("/api/usuarios/activos", authController::handleListarUsuariosActivos);
        config.routes.patch("/api/usuarios/{id}/pin", authController::handleActualizarPinUsuario);
    }

    static void registrarRutasOperativas(JavalinConfig config, SalonApiController salonController,
            KdsSyncApiController kdsSyncController, CobroApiController cobroController,
            KdsController kdsController, ReportesController reportesController) {
        config.routes.get("/api/productos", salonController::handleListarProductos);
        config.routes.get("/api/productos/disponibles", salonController::handleListarProductosDisponibles);
        config.routes.patch("/api/productos/{id}/disponibilidad", salonController::handleActualizarDisponibilidad);
        config.routes.get("/api/productos/{id}/imagen", salonController::handleImagenProducto);
        config.routes.get("/uploads/platillos/{archivo}", salonController::handleServirUploadPlatillo);
        config.routes.get("/api/mesas", salonController::handleListarMesas);

        config.routes.get("/api/pedidos", kdsSyncController::handleListarPedidos);
        config.routes.get("/api/pedidos/{uuid}", kdsSyncController::handleObtenerPedidoPorUuid);
        config.routes.post("/api/pedidos", kdsSyncController::handleCrearPedido);
        config.routes.patch("/api/pedidos/{id}/estado", kdsSyncController::handleActualizarEstadoPedido);

        config.routes.get("/api/caja/estado", cobroController::handleCajaEstado);
        config.routes.post("/api/caja/abrir", cobroController::handleCajaAbrir);
        config.routes.post("/api/caja/cerrar", cobroController::handleCajaCerrar);
        config.routes.post("/api/caja/cobrar", cobroController::handleCajaCobrar);

        config.routes.get("/api/kds/comandas", kdsController::handleKdsComandas);
        config.routes.get("/api/kds/items", kdsController::handleKdsItems);
        config.routes.get("/api/kds/pendientes", kdsController::handleKdsItems);
        config.routes.patch("/api/kds/items/{id}/estado", kdsController::handleActualizarEstadoItemKds);
        config.routes.patch("/api/kds/{id}/estado", kdsController::handleActualizarEstadoItemKds);
        config.routes.patch("/api/kds/pedidos/{id}/entregar", kdsController::handleMarcarPedidoEntregado);

        config.routes.get("/api/dashboard/kpis", reportesController::handleDashboardKpis);
        config.routes.get("/api/reportes/dashboard", reportesController::handleDashboardKpis);
        config.routes.get("/api/dashboard/ventas-por-hora", reportesController::handleDashboardVentasPorHora);
        config.routes.get("/api/dashboard/top-platos", reportesController::handleDashboardTopPlatos);
        config.routes.get("/api/dashboard/ventas-recientes", reportesController::handleDashboardVentasRecientes);
        config.routes.get("/api/menu-digital/view", reportesController::handleMenuDigitalView);
    }

    static void registrarRutasPwaYCertificado(JavalinConfig config, Consumer<Context> sslCertHandler) {
        config.routes.get("/kds", ctx -> PublicMenuServerHelper.servirPwaHtml(ctx, "pwa/kds/index.html"));
        config.routes.get("/kds/", ctx -> PublicMenuServerHelper.servirPwaHtml(ctx, "pwa/kds/index.html"));
        config.routes.get("/dashboard", ctx -> PublicMenuServerHelper.servirPwaHtml(ctx, "pwa/dashboard/index.html"));
        config.routes.get("/dashboard/", ctx -> PublicMenuServerHelper.servirPwaHtml(ctx, "pwa/dashboard/index.html"));
        config.routes.get("/foodcore.cer", sslCertHandler::accept);

        config.routes.get("/", ctx -> {
            File indexFile = new File("pwa/index.html");
            if (indexFile.exists()) {
                ctx.contentType("text/html; charset=UTF-8");
                ctx.result(Files.readString(indexFile.toPath(), StandardCharsets.UTF_8));
                return;
            }
            try (InputStream is = ApiRouteRegistrar.class.getResourceAsStream("/pwa/index.html")) {
                if (is != null) {
                    ctx.contentType("text/html; charset=UTF-8");
                    ctx.result(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    return;
                }
            }
            ctx.json(Map.of("service", "FoodCore ERP API v5.7.1", "status", "ONLINE"));
        });
    }

    static ObjectNode evaluarEstadoSalud(ApiState state, HealthCheckDAO healthCheckDAO, Connection conn) {
        HealthCheckDAO.ReadinessStatus readiness = healthCheckDAO.evaluarReadiness(conn);
        return serializarReadiness(state, readiness);
    }

    private static void handleHealthReadiness(Context ctx, ApiState state, HealthCheckDAO healthCheckDAO) {
        HealthCheckDAO.ReadinessStatus readiness = healthCheckDAO.evaluarReadiness();
        ObjectNode node = serializarReadiness(state, readiness);
        int status = readiness.isReady() ? 200 : 503;
        ctx.status(status)
                .contentType("application/json; charset=UTF-8")
                .result(node.toString());
    }

    private static ObjectNode serializarReadiness(ApiState state, HealthCheckDAO.ReadinessStatus readiness) {
        ObjectNode node = state.mapper.createObjectNode();
        boolean flywayListo = readiness.isReady();
        node.put("status", flywayListo ? "UP" : "DOWN");
        node.put("db", readiness.dbUp() ? "UP" : "DOWN");
        node.put("flyway", flywayListo ? "READY" : "NOT_READY");
        return node;
    }

    private static void handleHealth(Context ctx, ApiState state) {
        ObjectNode node = state.mapper.createObjectNode();
        node.put("status", "UP");
        ctx.status(200).contentType("application/json; charset=UTF-8").result(node.toString());
    }

    private static boolean esDevLoggingHabilitado() {
        String sysProp = System.getProperty("FOODCORE_DEV_LOGGING");
        if (sysProp != null) {
            return Boolean.parseBoolean(sysProp.trim());
        }
        String envVal = System.getenv("FOODCORE_DEV_LOGGING");
        if (envVal != null) {
            return Boolean.parseBoolean(envVal.trim());
        }
        Properties props = new Properties();
        try (InputStream is = ApiRouteRegistrar.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINEST, "No se pudo leer app.devLogging desde application.properties", e);
        }
        if (ConexionBD.esPerfilPruebasActivo()) {
            try (InputStream is = ApiRouteRegistrar.class.getResourceAsStream("/application-test.properties")) {
                if (is != null) {
                    props.load(is);
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINEST, "No se pudo leer app.devLogging desde application-test.properties", e);
            }
        }
        return Boolean.parseBoolean(props.getProperty("app.devLogging", "false").trim());
    }
}
