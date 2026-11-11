package com.foodcore.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.PedidoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.util.ConexionBD;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor HTTP/HTTPS REST basado en Javalin 6 modularizado mediante controladores dedicados
 * ({@link AuthController}, {@link PedidoController}, {@link KdsController}, {@link ReportesController})
 * y empaquetado de recursos estáticos embebidos en el JAR según SPEC-FOODCORE-v4.3.0 (§1.3, §2.1).
 */
public class ApiServer {

    private static final Logger LOGGER = Logger.getLogger(ApiServer.class.getName());
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_HTTPS_PORT = 8443;
    public static final String KEYSTORE_PASS = "foodcore123";

    private static ApiServer instance;

    private final ApiState state = new ApiState();
    private final JwtService jwtService;
    private final CorsPolicy corsPolicy;
    private final AuthRateLimiter rateLimiter;
    private final ApiSecurityFilter securityFilter;
    private final AuthController authController;
    private final PedidoController pedidoController;
    private final KdsController kdsController;
    private final ReportesController reportesController;

    private Javalin app;
    private int puertoActual = DEFAULT_PORT;
    private int puertoHttpsActual = DEFAULT_HTTPS_PORT;

    private ApiServer() {
        this(new AuthServiceImpl(), new ProductoDAOImpl(), new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), new UsuarioDAOImpl(), new CajaService(),
                new JwtService(), CorsPolicy.cargarDesdeConfiguracion(), AuthRateLimiter.cargarDesdeConfiguracion());
    }

    private ApiServer(AuthService authService, ProductoDAO productoDAO, MesaDAO mesaDAO,
                      PedidoDAO pedidoDAO, PedidoService pedidoService, UsuarioDAO usuarioDAO,
                      CajaService cajaService, JwtService jwtService, CorsPolicy corsPolicy,
                      AuthRateLimiter rateLimiter) {
        this.jwtService = jwtService != null ? jwtService : new JwtService();
        this.corsPolicy = corsPolicy != null ? corsPolicy : CorsPolicy.cargarDesdeConfiguracion();
        this.rateLimiter = rateLimiter != null ? rateLimiter : new AuthRateLimiter();
        this.securityFilter = new ApiSecurityFilter(state, this.jwtService, this.rateLimiter);
        this.authController = new AuthController(state, authService, usuarioDAO, this.jwtService);
        this.pedidoController = new PedidoController(state, productoDAO, mesaDAO, pedidoDAO, pedidoService, cajaService);
        this.kdsController = new KdsController(state, pedidoDAO);
        this.reportesController = new ReportesController(state);
    }

    /**
     * Crea una instancia aislada para pruebas de seguridad HTTP sin interferir con el singleton compartido.
     */
    public static ApiServer crearServidorAislado(JwtService jwtService,
                                                 CorsPolicy corsPolicy,
                                                 AuthRateLimiter rateLimiter) {
        return new ApiServer(new AuthServiceImpl(), new ProductoDAOImpl(), new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), new UsuarioDAOImpl(), new CajaService(),
                jwtService, corsPolicy, rateLimiter);
    }

    public static synchronized ApiServer getInstance() {
        if (instance == null) {
            instance = new ApiServer();
        }
        return instance;
    }

    /**
     * Lee el puerto configurado en {@code application.properties} ({@code api.rest.port}), por defecto 8080.
     */
    public static int leerPuertoConfigurado() {
        Properties props = new Properties();
        try (InputStream is = ApiServer.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                props.load(is);
                String val = props.getProperty("api.rest.port", String.valueOf(DEFAULT_PORT)).trim();
                return Integer.parseInt(val);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Usando puerto API por defecto 8080", e);
        }
        return DEFAULT_PORT;
    }

    public synchronized void iniciar() {
        iniciar(leerPuertoConfigurado(), DEFAULT_HTTPS_PORT);
    }

    public synchronized void iniciar(int port) {
        iniciar(port, DEFAULT_HTTPS_PORT);
    }

    public synchronized void iniciar(int httpPort, int httpsPort) {
        if (app != null) {
            return;
        }
        this.puertoActual = httpPort;
        this.puertoHttpsActual = httpsPort;
        File pwaDir = new File("pwa");

        app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            if (esDevLoggingHabilitado()) {
                config.bundledPlugins.enableDevLogging();
            }

            if (pwaDir.exists() && pwaDir.isDirectory()) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = pwaDir.getAbsolutePath();
                    staticFiles.location = Location.EXTERNAL;
                });
            }
            if (getClass().getResource("/pwa/index.html") != null) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/pwa";
                    staticFiles.location = Location.CLASSPATH;
                });
            }

            // Conector HTTP (8080)
            config.jetty.addConnector((server, httpConfig) -> {
                ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
                httpConnector.setPort(httpPort);
                return httpConnector;
            });

            // Conector HTTPS SSL (8443) con keystore.jks
            if (httpsPort >= 0) {
                SslContextFactory.Server sslContextFactory = obtenerSslContextFactory();
                if (sslContextFactory != null) {
                    config.jetty.addConnector((server, httpConfig) -> {
                        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
                        SecureRequestCustomizer customizer = new SecureRequestCustomizer();
                        customizer.setSniHostCheck(false);
                        httpsConfig.addCustomizer(customizer);

                        ServerConnector sslConnector = new ServerConnector(server,
                                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                                new HttpConnectionFactory(httpsConfig));
                        sslConnector.setPort(httpsPort);
                        return sslConnector;
                    });
                }
            }

            // Seguridad HTTP: CORS por whitelist explícita + API 100% stateless (Authorization: Bearer JWT, sin cookies ni CSRF por cookie)
            config.routes.before(ctx -> corsPolicy.apply(ctx));
            config.routes.options("/*", ctx -> {
                if (!ctx.res().isCommitted()) {
                    ctx.status(204);
                }
            });
            config.routes.before("/api/*", securityFilter::handle);

            // 1. Health, AuthController & Catálogo Público de Solo Lectura
            config.routes.get("/health", this::handleHealthReadiness);
            config.routes.head("/health", this::handleHealthReadiness);
            config.routes.get("/api/health", this::handleHealth);
            config.routes.get("/api/public/productos", pedidoController::handleListarProductosPublicos);
            config.routes.get("/api/public/productos/disponibles", pedidoController::handleListarProductosDisponiblesPublicos);
            config.routes.get("/api/public/productos/{id}/imagen", pedidoController::handleImagenProducto);
            config.routes.get("/api/public/menu-digital", reportesController::handleMenuDigitalView);
            config.routes.post("/api/auth/login", authController::handleLogin);
            config.routes.post("/api/auth/pin-login", authController::handlePinLogin);
            config.routes.post("/api/auth/pin-switch", authController::handlePinSwitch);
            config.routes.get("/api/usuarios/activos", authController::handleListarUsuariosActivos);
            config.routes.patch("/api/usuarios/{id}/pin", authController::handleActualizarPinUsuario);

            // 2. PedidoController (Catálogo, Mesas, Pedidos y Caja)
            config.routes.get("/api/productos", pedidoController::handleListarProductos);
            config.routes.get("/api/productos/disponibles", pedidoController::handleListarProductosDisponibles);
            config.routes.patch("/api/productos/{id}/disponibilidad", pedidoController::handleActualizarDisponibilidad);
            config.routes.get("/api/productos/{id}/imagen", pedidoController::handleImagenProducto);
            config.routes.get("/uploads/platillos/{archivo}", pedidoController::handleServirUploadPlatillo);
            config.routes.get("/api/mesas", pedidoController::handleListarMesas);
            config.routes.get("/api/pedidos", pedidoController::handleListarPedidos);
            config.routes.get("/api/pedidos/{uuid}", pedidoController::handleObtenerPedidoPorUuid);
            config.routes.post("/api/pedidos", pedidoController::handleCrearPedido);
            config.routes.patch("/api/pedidos/{id}/estado", pedidoController::handleActualizarEstadoPedido);
            config.routes.get("/api/caja/estado", pedidoController::handleCajaEstado);
            config.routes.post("/api/caja/abrir", pedidoController::handleCajaAbrir);
            config.routes.post("/api/caja/cerrar", pedidoController::handleCajaCerrar);
            config.routes.post("/api/caja/cobrar", pedidoController::handleCajaCobrar);

            // 3. KdsController (Cocina / Bar)
            config.routes.get("/api/kds/comandas", kdsController::handleKdsComandas);
            config.routes.get("/api/kds/items", kdsController::handleKdsItems);
            config.routes.get("/api/kds/pendientes", kdsController::handleKdsItems);
            config.routes.patch("/api/kds/items/{id}/estado", kdsController::handleActualizarEstadoItemKds);
            config.routes.patch("/api/kds/{id}/estado", kdsController::handleActualizarEstadoItemKds);
            config.routes.patch("/api/kds/pedidos/{id}/entregar", kdsController::handleMarcarPedidoEntregado);

            // 4. ReportesController (Dashboard Ejecutivo)
            config.routes.get("/api/dashboard/kpis", reportesController::handleDashboardKpis);
            config.routes.get("/api/reportes/dashboard", reportesController::handleDashboardKpis);
            config.routes.get("/api/dashboard/ventas-por-hora", reportesController::handleDashboardVentasPorHora);
            config.routes.get("/api/dashboard/top-platos", reportesController::handleDashboardTopPlatos);
            config.routes.get("/api/dashboard/ventas-recientes", reportesController::handleDashboardVentasRecientes);
            config.routes.get("/api/menu-digital/view", reportesController::handleMenuDigitalView);

            // 5. Rutas directas para PWAs y Certificado SSL (con soporte classpath getResourceAsStream y filesystem)
            config.routes.get("/kds", ctx -> servirPwaHtml(ctx, "pwa/kds/index.html"));
            config.routes.get("/kds/", ctx -> servirPwaHtml(ctx, "pwa/kds/index.html"));
            config.routes.get("/dashboard", ctx -> servirPwaHtml(ctx, "pwa/dashboard/index.html"));
            config.routes.get("/dashboard/", ctx -> servirPwaHtml(ctx, "pwa/dashboard/index.html"));
            config.routes.get("/foodcore.cer", this::servirCertificadoSsl);

            config.routes.get("/", ctx -> {
                File indexFile = new File("pwa/index.html");
                if (indexFile.exists()) {
                    ctx.contentType("text/html; charset=UTF-8");
                    ctx.result(Files.readString(indexFile.toPath(), StandardCharsets.UTF_8));
                    return;
                }
                try (InputStream is = getClass().getResourceAsStream("/pwa/index.html")) {
                    if (is != null) {
                        ctx.contentType("text/html; charset=UTF-8");
                        ctx.result(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                        return;
                    }
                }
                ctx.json(Map.of("service", "FoodCore ERP API v4.4.0", "status", "ONLINE"));
            });
        });

        app.start();
        if (httpPort == 0) {
            this.puertoActual = app.port();
        }
        LOGGER.info("FoodCore API REST (Javalin) activa en HTTP (puerto " + this.puertoActual + ") y HTTPS SSL (puerto " + httpsPort + ")");
    }

    private void servirPwaHtml(Context ctx, String ruta) {
        try {
            File f = new File(ruta);
            if (f.exists()) {
                ctx.contentType("text/html; charset=UTF-8");
                ctx.result(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                return;
            }
            try (InputStream is = getClass().getResourceAsStream("/" + ruta)) {
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

    private void servirCertificadoSsl(Context ctx) {
        try {
            try (InputStream is = getClass().getResourceAsStream("/foodcore.cer")) {
                if (is != null) {
                    ctx.contentType("application/x-x509-ca-cert");
                    ctx.result(is.readAllBytes());
                    return;
                }
            }
            File f = new File("foodcore.cer");
            if (!f.exists()) {
                f = new File("pwa/foodcore.cer");
            }
            if (f.exists()) {
                ctx.contentType("application/x-x509-ca-cert");
                ctx.result(Files.readAllBytes(f.toPath()));
                return;
            }
            ctx.status(404);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error sirviendo foodcore.cer", e);
            ctx.status(500);
        }
    }

    private SslContextFactory.Server obtenerSslContextFactory() {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            InputStream is = getClass().getResourceAsStream("/keystore.jks");
            if (is == null) {
                File keystoreFile = new File("keystore.jks");
                if (keystoreFile.exists()) {
                    is = new FileInputStream(keystoreFile);
                }
            }

            if (is != null) {
                try (InputStream inStream = is) {
                    ks.load(inStream, KEYSTORE_PASS.toCharArray());
                    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                    sslContextFactory.setKeyStore(ks);
                    sslContextFactory.setKeyStorePassword(KEYSTORE_PASS);
                    sslContextFactory.setKeyManagerPassword(KEYSTORE_PASS);
                    return sslContextFactory;
                }
            } else {
                LOGGER.warning("No se encontró keystore.jks para SSL HTTPS. El servidor funcionará solo en HTTP.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al inicializar SslContextFactory para HTTPS en puerto " + DEFAULT_HTTPS_PORT, e);
        }
        return null;
    }

    public synchronized void detener() {
        if (app != null) {
            try {
                app.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Aviso al detener ApiServer", e);
            } finally {
                app = null;
            }
        }
    }

    public synchronized boolean isRunning() {
        return app != null;
    }

    public int getPuertoActual() {
        return puertoActual;
    }

    public int getPuertoHttpsActual() {
        return puertoHttpsActual;
    }

    public String generarTokenJwt(int idUsuario, String username, String rol) {
        return authController.generarTokenJwt(idUsuario, username, rol);
    }

    private void handleHealthReadiness(Context ctx) {
        try (Connection conn = ConexionBD.getConnection()) {
            ObjectNode node = evaluarEstadoSalud(conn);
            int status = "UP".equals(node.path("status").asText()) ? 200 : 503;
            ctx.status(status)
                    .contentType("application/json; charset=UTF-8")
                    .result(node.toString());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Readiness /health en espera de BD/Flyway", e);
            ObjectNode node = evaluarEstadoSalud(null);
            ctx.status(503)
                    .contentType("application/json; charset=UTF-8")
                    .result(node.toString());
        }
    }

    ObjectNode evaluarEstadoSalud(Connection conn) {
        ObjectNode node = state.mapper.createObjectNode();
        boolean dbOk = false;
        int migracionesAplicadas = 0;
        if (conn != null) {
            try {
                if (conn.isValid(2)) {
                    dbOk = true;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            migracionesAplicadas = rs.getInt(1);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "BD o historial Flyway aún no disponible para /health", e);
            }
        }

        boolean flywayListo = dbOk && migracionesAplicadas > 0;
        node.put("status", flywayListo ? "UP" : "DOWN");
        node.put("db", dbOk ? "UP" : "DOWN");
        node.put("flyway", flywayListo ? "READY" : "NOT_READY");
        return node;
    }

    private void handleHealth(Context ctx) {
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
        try (InputStream is = ApiServer.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (java.io.IOException ignored) {
        }
        try (InputStream is = ApiServer.class.getResourceAsStream("/application-test.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (java.io.IOException ignored) {
        }
        return Boolean.parseBoolean(props.getProperty("app.devLogging", "false").trim());
    }
}
