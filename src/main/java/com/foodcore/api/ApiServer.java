package com.foodcore.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.api.security.TokenBlacklistService;
import com.foodcore.dao.HealthCheckDAO;
import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.UsuarioDAOImpl;
import com.foodcore.dao.impl.HealthCheckDAOImpl;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.security.TotpService;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.AuthServiceImpl;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orquestador de ciclo de vida para el servidor HTTP/HTTPS REST (Javalin) y su conector público loopback de menú QR.
 */
public class ApiServer {

    private static final Logger LOGGER = Logger.getLogger(ApiServer.class.getName());
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_HTTPS_PORT = 8443;
    public static final int DEFAULT_PUBLIC_MENU_PORT = 8081;
    public static final String ENV_PUBLIC_MENU_PORT = "FOODCORE_PUBLIC_MENU_PORT";
    public static final String PROP_PUBLIC_MENU_PORT = "api.public.menu.port";
    public static final String ENV_KEYSTORE_PASS = "FOODCORE_KEYSTORE_PASS";
    public static final String ENV_KEYSTORE_PATH = "FOODCORE_KEYSTORE_PATH";
    public static final String ENV_CERT_PATH = "FOODCORE_CERT_PATH";
    public static final int MIN_KEYSTORE_PASS_LENGTH = 8;

    private static ApiServer instance;

    private final ApiState state = new ApiState();
    private final JwtService jwtService;
    private final CorsPolicy corsPolicy;
    private final AuthRateLimiter rateLimiter;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApiSecurityFilter securityFilter;
    private final AuthController authController;
    private final PedidoController pedidoController;
    private final KdsController kdsController;
    private final ReportesController reportesController;
    private final HealthCheckDAO healthCheckDAO;
    private final ApiTlsConfigurator tlsConfigurator;

    private Javalin app;
    private Javalin publicMenuApp;
    private int puertoActual = DEFAULT_PORT;
    private int puertoHttpsActual = DEFAULT_HTTPS_PORT;
    private int puertoMenuPublicoActual = -1;

    private ApiServer() {
        this(new AuthServiceImpl(), new ProductoDAOImpl(), new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), new UsuarioDAOImpl(), new CajaService(), new VentaService(),
                new JwtService(), CorsPolicy.cargarDesdeConfiguracion(), AuthRateLimiter.cargarDesdeConfiguracion(),
                TokenBlacklistService.getInstance());
    }

    private ApiServer(AuthService authService, ProductoDAO productoDAO, MesaDAO mesaDAO,
                      PedidoDAO pedidoDAO, PedidoService pedidoService, UsuarioDAO usuarioDAO,
                      CajaService cajaService, VentaService ventaService, JwtService jwtService, CorsPolicy corsPolicy,
                      AuthRateLimiter rateLimiter, TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService != null ? jwtService : new JwtService();
        this.corsPolicy = corsPolicy != null ? corsPolicy : CorsPolicy.cargarDesdeConfiguracion();
        this.rateLimiter = rateLimiter != null ? rateLimiter : new AuthRateLimiter();
        this.tokenBlacklistService = tokenBlacklistService != null
                ? tokenBlacklistService
                : TokenBlacklistService.getInstance();
        this.securityFilter = new ApiSecurityFilter(
                state, this.jwtService, this.rateLimiter, this.tokenBlacklistService);
        this.authController = new AuthController(
                state, authService, usuarioDAO, this.jwtService, this.tokenBlacklistService, new TotpService());
        this.pedidoController = new PedidoController(
                state, productoDAO, mesaDAO, pedidoDAO, pedidoService, cajaService, ventaService);
        this.kdsController = new KdsController(state, pedidoDAO);
        this.reportesController = new ReportesController(state);
        this.healthCheckDAO = new HealthCheckDAOImpl();
        this.tlsConfigurator = new ApiTlsConfigurator();
    }

    public static ApiServer crearServidorAisladoConBlacklist(JwtService jwtService,
            CorsPolicy corsPolicy,
            AuthRateLimiter rateLimiter,
            TokenBlacklistService tokenBlacklistService) {
        return new ApiServer(new AuthServiceImpl(), new ProductoDAOImpl(), new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), new UsuarioDAOImpl(), new CajaService(), new VentaService(),
                jwtService, corsPolicy, rateLimiter, tokenBlacklistService);
    }

    public TokenBlacklistService getTokenBlacklistService() {
        return tokenBlacklistService;
    }

    public static ApiServer crearServidorAislado(JwtService jwtService,
                                                 CorsPolicy corsPolicy,
                                                 AuthRateLimiter rateLimiter) {
        return new ApiServer(new AuthServiceImpl(), new ProductoDAOImpl(), new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), new UsuarioDAOImpl(), new CajaService(), new VentaService(),
                jwtService, corsPolicy, rateLimiter, TokenBlacklistService.crearAisladoEnMemoria());
    }

    static ApiServer crearServidorAisladoConAuth(JwtService jwtService,
                                                 CorsPolicy corsPolicy,
                                                 AuthRateLimiter rateLimiter,
                                                 AuthService authService,
                                                 UsuarioDAO usuarioDAO) {
        return new ApiServer(authService, new ProductoDAOImpl(), new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), usuarioDAO, new CajaService(), new VentaService(),
                jwtService, corsPolicy, rateLimiter, TokenBlacklistService.crearAisladoEnMemoria());
    }

    static ApiServer crearServidorAisladoConProductoDao(JwtService jwtService,
                                                        CorsPolicy corsPolicy,
                                                        AuthRateLimiter rateLimiter,
                                                        ProductoDAO productoDAO) {
        return new ApiServer(new AuthServiceImpl(), productoDAO, new MesaDAOImpl(),
                new PedidoDAOImpl(), new PedidoService(), new UsuarioDAOImpl(), new CajaService(), new VentaService(),
                jwtService, corsPolicy, rateLimiter, TokenBlacklistService.crearAisladoEnMemoria());
    }

    public static synchronized ApiServer getInstance() {
        if (instance == null) {
            instance = new ApiServer();
        }
        return instance;
    }

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

    public static int leerPuertoPublicoMenuConfigurado() {
        return PublicMenuServerHelper.leerPuertoPublicoMenuConfigurado();
    }

    public synchronized void iniciar() {
        iniciar(leerPuertoConfigurado(), DEFAULT_HTTPS_PORT, leerPuertoPublicoMenuConfigurado());
    }

    public synchronized void iniciar(int port) {
        iniciar(port, DEFAULT_HTTPS_PORT, -1);
    }

    public synchronized void iniciar(int httpPort, int httpsPort) {
        iniciar(httpPort, httpsPort, -1);
    }

    public synchronized void iniciar(int httpPort, int httpsPort, int publicMenuPort) {
        if (app != null) {
            return;
        }
        this.puertoActual = httpPort;
        this.puertoHttpsActual = httpsPort;
        this.puertoMenuPublicoActual = -1;
        this.tlsConfigurator.limpiarCertificadoActivo();
        SslContextFactory.Server sslContextFactory = httpsPort >= 0 ? obtenerSslContextFactory() : null;

        SalonApiController salonCtrl = pedidoController.salonController();
        KdsSyncApiController kdsSyncCtrl = pedidoController.kdsSyncController();
        CobroApiController cobroCtrl = pedidoController.cobroController();

        app = Javalin.create(config -> {
            ApiRouteRegistrar.configurarEstaticosYConectores(config, httpPort, httpsPort, sslContextFactory);
            ApiRouteRegistrar.registrarMiddlewares(config, state, corsPolicy, securityFilter);
            ApiRouteRegistrar.registrarRutasSaludPublicasYAuth(
                    config, state, healthCheckDAO, authController, salonCtrl, reportesController);
            ApiRouteRegistrar.registrarRutasOperativas(
                    config, salonCtrl, kdsSyncCtrl, cobroCtrl, kdsController, reportesController);
            ApiRouteRegistrar.registrarRutasPwaYCertificado(config, this::servirCertificadoSsl);
        });

        app.start();
        if (httpPort == 0) {
            this.puertoActual = app.port();
        }
        if (publicMenuPort >= 0) {
            this.publicMenuApp = PublicMenuServerHelper.iniciarServidorMenuPublico(
                    publicMenuPort, state, corsPolicy, salonCtrl, reportesController);
            if (this.publicMenuApp != null) {
                this.puertoMenuPublicoActual = this.publicMenuApp.port();
            }
        }
        LOGGER.info("FoodCore API REST (Javalin) activa en HTTP (puerto " + this.puertoActual
                + "), HTTPS SSL (puerto " + httpsPort + ") y Menú Público loopback (puerto "
                + this.puertoMenuPublicoActual + ")");
    }

    static void aplicarCabecerasSeguridad(Context ctx) {
        PublicMenuServerHelper.aplicarCabecerasSeguridad(ctx);
    }

    public static String resolverKeystorePasswordObligatorio() {
        return resolverKeystorePasswordObligatorio(System::getenv);
    }

    static String resolverKeystorePasswordObligatorio(Function<String, String> envReader) {
        return ApiTlsConfigurator.resolverKeystorePasswordObligatorio(envReader);
    }

    private void servirCertificadoSsl(Context ctx) {
        try {
            String configuredCert = ApiTlsConfigurator.leerVariableOPropiedad(ENV_CERT_PATH);
            if (configuredCert != null) {
                File certFile = new File(configuredCert);
                if (certFile.exists() && certFile.isFile()) {
                    ctx.contentType("application/x-x509-ca-cert");
                    ctx.result(Files.readAllBytes(certFile.toPath()));
                    return;
                }
            }
            if (!tlsConfigurator.servirCertificadoDesdeMemoriaOClasspath(ctx)) {
                ctx.status(404);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error sirviendo foodcore.cer", e);
            ctx.status(500);
        }
    }

    private SslContextFactory.Server obtenerSslContextFactory() {
        String keystorePassword = resolverKeystorePasswordObligatorio();
        char[] passwordChars = keystorePassword.toCharArray();
        try {
            KeyStore ks;
            String configuredPath = ApiTlsConfigurator.leerVariableOPropiedad(ENV_KEYSTORE_PATH);
            if (configuredPath != null) {
                File configuredFile = new File(configuredPath);
                if (!configuredFile.exists() || !configuredFile.isFile()) {
                    throw new IllegalStateException(
                            "No se encontró el keystore HTTPS configurado en "
                            + ENV_KEYSTORE_PATH + ": " + configuredPath
                    );
                }
                ks = KeyStore.getInstance(configuredFile, passwordChars);
            } else {
                ks = tlsConfigurator.cargarKeystorePorDefecto(passwordChars);
            }
            if (ks == null) {
                LOGGER.warning("No se encontró keystore.jks para SSL HTTPS. El servidor funcionará solo en HTTP.");
                return null;
            }
            return tlsConfigurator.construirDesdeKeystore(ks, keystorePassword);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error al inicializar el keystore SSL HTTPS en puerto "
                    + DEFAULT_HTTPS_PORT + ": " + e.getMessage(),
                    e
            );
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    public synchronized void detener() {
        tlsConfigurator.limpiarCertificadoActivo();
        if (publicMenuApp != null) {
            try {
                publicMenuApp.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Aviso al detener servidor de menú público", e);
            } finally {
                publicMenuApp = null;
                puertoMenuPublicoActual = -1;
            }
        }
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

    public int getPuertoMenuPublicoActual() {
        return puertoMenuPublicoActual;
    }

    public String generarTokenJwt(int idUsuario, String username, String rol) {
        return authController.generarTokenJwt(idUsuario, username, rol);
    }

    ObjectNode evaluarEstadoSalud(Connection conn) {
        return ApiRouteRegistrar.evaluarEstadoSalud(state, healthCheckDAO, conn);
    }
}
