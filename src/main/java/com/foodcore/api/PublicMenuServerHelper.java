package com.foodcore.api;

import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.CorsPolicy;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper para cabeceras HTTP defensivas, servido de PWAs y conector loopback aislado del menú QR público.
 */
final class PublicMenuServerHelper {

    private static final Logger LOGGER = Logger.getLogger(PublicMenuServerHelper.class.getName());
    private static final String CSP_API_JSON =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";
    private static final String CSP_HTML_PWA =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; "
            + "frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    private PublicMenuServerHelper() {
    }

    static int leerPuertoPublicoMenuConfigurado() {
        String configured = ApiTlsConfigurator.leerVariableOPropiedad(ApiServer.ENV_PUBLIC_MENU_PORT);
        if (configured != null) {
            try {
                int parsed = Integer.parseInt(configured);
                if (parsed >= 0 && parsed <= 65535) {
                    return parsed;
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.FINEST, "Puerto inválido en " + ApiServer.ENV_PUBLIC_MENU_PORT, e);
            }
        }
        Properties props = new Properties();
        try (InputStream is = PublicMenuServerHelper.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                props.load(is);
                String val = props.getProperty(ApiServer.PROP_PUBLIC_MENU_PORT);
                if (val != null && !val.isBlank()) {
                    int parsed = Integer.parseInt(val.trim());
                    if (parsed >= 0 && parsed <= 65535) {
                        return parsed;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Usando puerto de menú público por defecto 8081", e);
        }
        return ApiServer.DEFAULT_PUBLIC_MENU_PORT;
    }

    static Javalin iniciarServidorMenuPublico(int publicMenuPort, ApiState state, CorsPolicy corsPolicy,
            SalonApiController salonController, ReportesController reportesController) {
        Javalin publicMenuApp = null;
        try {
            File menuDir = new File("pwa/menu-digital");
            File fontsDir = new File("pwa/fonts");
            publicMenuApp = Javalin.create(config -> {
                config.startup.showJavalinBanner = false;

                if (menuDir.exists() && menuDir.isDirectory()) {
                    config.staticFiles.add(sf -> {
                        sf.hostedPath = "/menu-digital";
                        sf.directory = menuDir.getAbsolutePath();
                        sf.location = Location.EXTERNAL;
                    });
                }
                if (PublicMenuServerHelper.class.getResource("/pwa/menu-digital/index.html") != null) {
                    config.staticFiles.add(sf -> {
                        sf.hostedPath = "/menu-digital";
                        sf.directory = "/pwa/menu-digital";
                        sf.location = Location.CLASSPATH;
                    });
                }
                if (fontsDir.exists() && fontsDir.isDirectory()) {
                    config.staticFiles.add(sf -> {
                        sf.hostedPath = "/fonts";
                        sf.directory = fontsDir.getAbsolutePath();
                        sf.location = Location.EXTERNAL;
                    });
                }

                config.jetty.addConnector((server, httpConfig) -> {
                    ServerConnector loopbackConnector = new ServerConnector(
                            server, new HttpConnectionFactory(httpConfig));
                    loopbackConnector.setHost("127.0.0.1");
                    loopbackConnector.setPort(publicMenuPort);
                    return loopbackConnector;
                });

                config.routes.before(ctx -> {
                    aplicarCabecerasSeguridad(ctx);
                    if (!ApiSecurityFilter.esRutaPublicaTunel(ctx.method().name(), ctx.path())) {
                        state.responderError(ctx, 403, "Ruta no permitida en el conector de menú público.");
                        ctx.skipRemainingHandlers();
                        return;
                    }
                    corsPolicy.apply(ctx);
                });

                config.routes.get("/api/public/productos", salonController::handleListarProductosPublicos);
                config.routes.get("/api/public/productos/disponibles",
                        salonController::handleListarProductosDisponiblesPublicos);
                config.routes.get("/api/public/productos/{id}/imagen", salonController::handleImagenProducto);
                config.routes.get("/api/public/menu-digital", reportesController::handleMenuDigitalView);
                config.routes.get("/uploads/platillos/{archivo}", salonController::handleServirUploadPlatillo);
                config.routes.get("/menu-digital", ctx -> servirPwaHtml(ctx, "pwa/menu-digital/index.html"));
                config.routes.get("/menu-digital/", ctx -> servirPwaHtml(ctx, "pwa/menu-digital/index.html"));
            });
            publicMenuApp.start();
            return publicMenuApp;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "No se pudo iniciar el conector aislado de menú público en 127.0.0.1:" + publicMenuPort, e);
            if (publicMenuApp != null) {
                try {
                    publicMenuApp.stop();
                } catch (Exception stopEx) {
                    LOGGER.log(Level.FINE, "Aviso deteniendo servidor fallido de menú público", stopEx);
                }
            }
            return null;
        }
    }

    static void aplicarCabecerasSeguridad(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("X-Frame-Options", "DENY");
        ctx.header("Referrer-Policy", "no-referrer");
        ctx.header("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        String path = ctx.path();
        boolean esApiJson = path.startsWith("/api/")
                && !"/api/public/menu-digital".equals(path)
                && !"/api/menu-digital/view".equals(path);
        ctx.header("Content-Security-Policy", esApiJson ? CSP_API_JSON : CSP_HTML_PWA);
        boolean esHttps = "https".equalsIgnoreCase(ctx.scheme())
                || ctx.req().isSecure()
                || "https".equalsIgnoreCase(ctx.header("X-Forwarded-Proto"));
        if (esHttps) {
            ctx.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    static void servirPwaHtml(Context ctx, String ruta) {
        try {
            File f = new File(ruta);
            if (f.exists()) {
                ctx.contentType("text/html; charset=UTF-8");
                ctx.result(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                return;
            }
            try (InputStream is = PublicMenuServerHelper.class.getResourceAsStream("/" + ruta)) {
                if (is != null) {
                    ctx.contentType("text/html; charset=UTF-8");
                    ctx.result(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    return;
                }
            }
            ctx.status(404).result("PWA no encontrada: " + ruta);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error sirviendo " + ruta, e);
            ctx.status(500).result("Error interno sirviendo PWA");
        }
    }
}
