package com.foodcore.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final AuthController authController;
    private final PedidoController pedidoController;
    private final KdsController kdsController;
    private final ReportesController reportesController;

    private Javalin app;
    private int puertoActual = DEFAULT_PORT;
    private int puertoHttpsActual = DEFAULT_HTTPS_PORT;

    private ApiServer() {
        this(new AuthServiceImpl(), new ProductoDAOImpl(), new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), new UsuarioDAOImpl(), new CajaService());
    }

    private ApiServer(AuthService authService, ProductoDAO productoDAO, MesaDAO mesaDAO,
                      PedidoDAO pedidoDAO, PedidoService pedidoService, UsuarioDAO usuarioDAO,
                      CajaService cajaService) {
        this.authController = new AuthController(state, authService, usuarioDAO);
        this.pedidoController = new PedidoController(state, productoDAO, mesaDAO, pedidoDAO, pedidoService, cajaService);
        this.kdsController = new KdsController(state, pedidoDAO);
        this.reportesController = new ReportesController(state);
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
            config.showJavalinBanner = false;
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));

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
        });

        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Idempotency-Key, X-Requested-With");
        });

        app.options("/*", ctx -> ctx.status(204));

        // 1. Health & AuthController
        app.get("/api/health", this::handleHealth);
        app.post("/api/auth/login", authController::handleLogin);
        app.post("/api/auth/pin-login", authController::handlePinLogin);
        app.post("/api/auth/pin-switch", authController::handlePinSwitch);
        app.get("/api/usuarios/activos", authController::handleListarUsuariosActivos);
        app.patch("/api/usuarios/{id}/pin", authController::handleActualizarPinUsuario);

        // 2. PedidoController (Catálogo, Mesas, Pedidos y Caja)
        app.get("/api/productos", pedidoController::handleListarProductos);
        app.get("/api/productos/disponibles", pedidoController::handleListarProductosDisponibles);
        app.patch("/api/productos/{id}/disponibilidad", pedidoController::handleActualizarDisponibilidad);
        app.get("/api/productos/{id}/imagen", pedidoController::handleImagenProducto);
        app.get("/uploads/platillos/{archivo}", pedidoController::handleServirUploadPlatillo);
        app.get("/api/mesas", pedidoController::handleListarMesas);
        app.get("/api/pedidos", pedidoController::handleListarPedidos);
        app.get("/api/pedidos/{uuid}", pedidoController::handleObtenerPedidoPorUuid);
        app.post("/api/pedidos", pedidoController::handleCrearPedido);
        app.patch("/api/pedidos/{id}/estado", pedidoController::handleActualizarEstadoPedido);
        app.get("/api/caja/estado", pedidoController::handleCajaEstado);
        app.post("/api/caja/abrir", pedidoController::handleCajaAbrir);
        app.post("/api/caja/cerrar", pedidoController::handleCajaCerrar);
        app.post("/api/caja/cobrar", pedidoController::handleCajaCobrar);

        // 3. KdsController (Cocina / Bar)
        app.get("/api/kds/comandas", kdsController::handleKdsComandas);
        app.get("/api/kds/items", kdsController::handleKdsItems);
        app.get("/api/kds/pendientes", kdsController::handleKdsItems);
        app.patch("/api/kds/items/{id}/estado", kdsController::handleActualizarEstadoItemKds);
        app.patch("/api/kds/{id}/estado", kdsController::handleActualizarEstadoItemKds);
        app.patch("/api/kds/pedidos/{id}/entregar", kdsController::handleMarcarPedidoEntregado);

        // 4. ReportesController (Dashboard Ejecutivo)
        app.get("/api/dashboard/kpis", reportesController::handleDashboardKpis);
        app.get("/api/reportes/dashboard", reportesController::handleDashboardKpis);
        app.get("/api/dashboard/ventas-por-hora", reportesController::handleDashboardVentasPorHora);
        app.get("/api/dashboard/top-platos", reportesController::handleDashboardTopPlatos);
        app.get("/api/dashboard/ventas-recientes", reportesController::handleDashboardVentasRecientes);
        app.get("/api/menu-digital/view", reportesController::handleMenuDigitalView);

        // 5. Rutas directas para PWAs y Certificado SSL (con soporte classpath getResourceAsStream y filesystem)
        app.get("/kds", ctx -> servirPwaHtml(ctx, "pwa/kds/index.html"));
        app.get("/kds/", ctx -> servirPwaHtml(ctx, "pwa/kds/index.html"));
        app.get("/dashboard", ctx -> servirPwaHtml(ctx, "pwa/dashboard/index.html"));
        app.get("/dashboard/", ctx -> servirPwaHtml(ctx, "pwa/dashboard/index.html"));
        app.get("/foodcore.cer", this::servirCertificadoSsl);

        app.get("/", ctx -> {
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

        app.start();
        LOGGER.info("FoodCore API REST (Javalin) activa en HTTP (puerto " + httpPort + ") y HTTPS SSL (puerto " + httpsPort + ")");
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

    private void handleHealth(Context ctx) {
        ObjectNode node = state.mapper.createObjectNode();
        node.put("status", "UP");
        node.put("version", "4.4.0");
        node.put("port", puertoActual);
        ctx.status(200).contentType("application/json; charset=UTF-8").result(node.toString());
    }
}
