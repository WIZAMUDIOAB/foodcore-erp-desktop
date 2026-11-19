package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.api.security.TokenBlacklistService;
import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.security.TerminalAuthService;
import com.foodcore.security.TotpService;
import com.foodcore.service.AuthService;
import com.foodcore.service.DeliveryWebhookService;
import com.foodcore.service.TunnelService;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite integral de regresión de seguridad v5.7.1 para Autenticación, Aislamiento de Sesión,
 * TOTP Anti-Replay/Cifrado, LFI, Cloudflare Tunnel, Blacklist Fail-Closed, RateLimiter acotado,
 * Sockets KDS anti-OOM y Delivery Webhook HMAC-SHA256.
 */
class HardeningAuthApiSecurityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] TEST_SECRET =
            "FoodCore-Hardening-v571-Secret-Key-AtLeast32Bytes!".getBytes(StandardCharsets.UTF_8);

    @AfterEach
    void limpiarEstadoGlobal() {
        AuthService.logout();
        TotpService.limpiarCacheReplay();
        System.clearProperty(TotpService.ENV_TOTP_ENC_KEY);
        System.clearProperty("foodcore.profile");
        System.clearProperty("foodcore.test.profile");
        System.clearProperty("FOODCORE_CORS_ALLOWED_ORIGINS");
        System.clearProperty("security.auth.rateLimit.max");
        System.clearProperty(ApiState.PROP_DEMO_FALLBACK);
        System.clearProperty(DeliveryWebhookService.ENV_WEBHOOK_SECRET);
        TunnelService.limpiarEstadoParaPruebas();
    }

    @Test
    @DisplayName("LFI: confina imágenes en uploads/platillos, permite PNG/JPG/WEBP y bloquea SVG y traversal")
    void testLfiConfinamientoImagenesBloqueaSvgYTraversal(@TempDir Path tempDir) throws Exception {
        Path uploadsDir = tempDir.resolve("uploads").resolve("platillos");
        Files.createDirectories(uploadsDir);
        Path validPng = uploadsDir.resolve("ceviche.png");
        Path validJpg = uploadsDir.resolve("lomo.jpg");
        Path validWebp = uploadsDir.resolve("causa.webp");
        Path evilSvg = uploadsDir.resolve("xss.svg");
        Path outsidePng = tempDir.resolve("secreto.png");

        Files.write(validPng, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        Files.write(validJpg, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        Files.write(validWebp, "RIFFWEBP".getBytes(StandardCharsets.UTF_8));
        Files.writeString(evilSvg, "<svg onload=alert(1)></svg>", StandardCharsets.UTF_8);
        Files.write(outsidePng, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, "ceviche.png").isPresent());
        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, "uploads/platillos/lomo.jpg").isPresent());
        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, "/uploads/platillos/causa.webp").isPresent());

        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, "xss.svg").isEmpty(), ".svg debe bloquearse");
        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, "../secreto.png").isEmpty(), "traversal debe bloquearse");
        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, "../../etc/passwd").isEmpty());
        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, "C:\\Windows\\win.ini").isEmpty());
        assertTrue(PedidoController.resolverArchivoImagenConfinado(uploadsDir, outsidePng.toString()).isEmpty());
        assertTrue(PedidoController.esRutaImagenSospechosa("evil.svg"));
        assertTrue(PedidoController.esRutaImagenSospechosa("..\\secreto.png"));
    }

    @Test
    @DisplayName("Aislamiento Desktop<->API y cero fallback plaintext: stateless no muta SesionManager y rechaza plaintext")
    void testAislamientoSesionStatelessYCeroPlaintext() throws Exception {
        AuthService.logout();
        assertNull(SesionManager.getSesionActiva());
        assertNull(AuthService.getUsuarioSesionActual());

        EncriptadorPassword enc = new EncriptadorPassword();
        String bcryptPass = enc.hashear("Valido#2026!");
        String bcryptPin = enc.hashear("4567");

        Usuario mozoBcrypt = new Usuario(3, 3, 3, "mozo", bcryptPass, true);
        mozoBcrypt.setNombreRol("MOZO");
        mozoBcrypt.setNombreEmpleado("Mozo Prueba");
        mozoBcrypt.setPinHash(bcryptPin);

        Usuario legacyPlain = new Usuario(9, 9, 3, "legacy", "TextoPlano123", true);
        legacyPlain.setNombreRol("MOZO");
        legacyPlain.setPin("1234");
        legacyPlain.setPinHash(null);

        Usuario adminUsuario = new Usuario(1, 1, 1, "admin", bcryptPass, true);
        adminUsuario.setNombreRol("ADMINISTRADOR");
        adminUsuario.setPinHash(bcryptPin);

        Usuario mozo2fa = new Usuario(4, 4, 3, "mozo2fa", bcryptPass, true);
        mozo2fa.setNombreRol("MOZO");
        mozo2fa.setTotpHabilitado(true);
        mozo2fa.setPinHash(bcryptPin);

        UsuarioDAO mockDao = crearUsuarioDaoEnMemoria(List.of(mozoBcrypt, legacyPlain, adminUsuario, mozo2fa));
        Connection fakeConn = crearConexionNoOp();
        AuthServiceImpl authService = new AuthServiceImpl(mockDao, enc);

        // 1. Autenticación stateless válida NO muta SesionManager ni usuarioSesionActual
        Usuario okStateless = authService.autenticarSoloCredenciales(fakeConn, "mozo", "Valido#2026!");
        assertEquals("mozo", okStateless.getUsername());
        assertNull(SesionManager.getSesionActiva(), "autenticarSoloCredenciales no debe mutar SesionManager");
        assertNull(AuthService.getUsuarioSesionActual(), "autenticarSoloCredenciales no debe mutar usuarioSesionActual");

        // 2. PIN stateless válido para MOZO NO muta SesionManager
        Optional<Usuario> pinOk = authService.autenticarPorPinStateless(fakeConn, 3, "mozo", "4567");
        assertTrue(pinOk.isPresent());
        assertNull(SesionManager.getSesionActiva(), "autenticarPorPinStateless no debe mutar SesionManager");

        // 3. Plaintext password y plaintext PIN son rechazados estrictamente
        NegocioException exPlain = assertThrows(NegocioException.class,
                () -> authService.autenticarSoloCredenciales(fakeConn, "legacy", "TextoPlano123"));
        assertEquals(CodigoError.CREDENCIALES_INVALIDAS, exPlain.getCodigoError());
        assertFalse(authService.verificarPinUsuario("1234", legacyPlain), "PIN en texto plano debe rechazarse");

        // 4. PIN bloqueado para ADMIN/SUPERVISOR y usuarios con 2FA habilitado
        assertFalse(AuthServiceImpl.esPinPermitidoParaUsuario(adminUsuario));
        assertFalse(AuthServiceImpl.esPinPermitidoParaUsuario(mozo2fa));
        assertTrue(authService.autenticarPorPinStateless(fakeConn, 1, "admin", "4567").isEmpty());
        assertTrue(authService.autenticarPorPinStateless(fakeConn, 4, "mozo2fa", "4567").isEmpty());

        // 5. Política de complejidad en cambiarPasswordObligatorio rechaza claves débiles
        assertThrows(NegocioException.class, () -> authService.cambiarPasswordObligatorio(3, "solominusculas"));
        assertThrows(NegocioException.class, () -> authService.cambiarPasswordObligatorio(3, "12345678"));
        assertThrows(NegocioException.class, () -> authService.cambiarPasswordObligatorio(3, "SOLOMAYUSCULAS"));

        // 6. Desktop establecerSesionActiva sí establece SesionManager con zona America/Lima
        authService.establecerSesionActiva(okStateless);
        assertNotNull(SesionManager.getSesionActiva());
        assertEquals(3L, SesionManager.getSesionActiva().getIdUsuario());
        SesionUsuario sesionLima = new SesionUsuario(3L, "Mozo", "MOZO", Set.of(), "POS-01", null);
        LocalDateTime ahoraLima = LocalDateTime.now(ZoneId.of("America/Lima"));
        assertTrue(Math.abs(java.time.Duration.between(sesionLima.getIniciadaEn(), ahoraLima).toSeconds()) < 5);

        // 7. Bloqueo por fuerza bruta, desbloqueo tras expiración, PIN incorrecto y Desktop login/autenticarPorPin
        Usuario bloqueado = new Usuario(10, 10, 3, "locked", bcryptPass, true);
        bloqueado.setNombreRol("MOZO");
        bloqueado.setBloqueadoHasta(ahoraLima.plusMinutes(10));
        bloqueado.setPinHash(bcryptPin);

        Usuario expirado = new Usuario(11, 11, 3, "expired", bcryptPass, true);
        expirado.setNombreRol("MOZO");
        expirado.setBloqueadoHasta(ahoraLima.minusMinutes(2));
        expirado.setIntentosFallidos(5);
        expirado.setPinHash(bcryptPin);

        UsuarioDAO daoLock = crearUsuarioDaoEnMemoria(List.of(bloqueado, expirado, mozoBcrypt));
        AuthServiceImpl authLock = new AuthServiceImpl(daoLock, enc);
        assertEquals(CodigoError.USUARIO_BLOQUEADO,
                assertThrows(NegocioException.class,
                        () -> authLock.autenticarSoloCredenciales(fakeConn, "locked", "Valido#2026!")).getCodigoError());
        assertTrue(authLock.autenticarPorPinStateless(fakeConn, 10, "locked", "4567").isEmpty());
        assertEquals("expired", authLock.login(fakeConn, "expired", "Valido#2026!").getUsername());
        assertTrue(authLock.autenticarPorPin(fakeConn, 11, "expired", "4567").isPresent());
        assertTrue(authLock.autenticarPorPinStateless(fakeConn, 3, "mozo", "0000").isEmpty());
    }

    @Test
    @DisplayName("API /api/auth/pin-switch bloquea ADMIN/2FA con 403 y revoca el JWT anterior al cambiar de operador")
    void testPinSwitchBloqueaAdminYRevocaJwtAnterior() throws Exception {
        Usuario mozo = new Usuario(3, 3, 3, "mozo", "", true);
        mozo.setNombreRol("MOZO");
        Usuario cajero = new Usuario(2, 2, 2, "cajero", "", true);
        cajero.setNombreRol("CAJERO");
        Usuario admin = new Usuario(1, 1, 1, "admin", "", true);
        admin.setNombreRol("ADMIN");

        UsuarioDAO dao = crearUsuarioDaoEnMemoria(List.of(admin, cajero, mozo));
        AuthService fakeAuth = new AuthService(dao, new EncriptadorPassword()) {
            @Override
            public Optional<Usuario> autenticarPorPinStateless(int idUsuario, String username, String pin) {
                if ("admin".equals(username) && "9999".equals(pin)) {
                    return Optional.of(admin);
                }
                if ("cajero".equals(username) && "1234".equals(pin)) {
                    return Optional.of(cajero);
                }
                return Optional.empty();
            }
        };

        JwtService jwt = new JwtService(TEST_SECRET);
        int port = puertoLibre();
        ApiServer server = ApiServer.crearServidorAisladoConAuth(
                jwt, CorsPolicy.desdeCadena(""), new AuthRateLimiter(30, 60_000L, System::currentTimeMillis),
                fakeAuth, dao);
        server.iniciar(port, -1);

        try {
            HttpClient client = HttpClient.newHttpClient();
            String tokenMozo = jwt.generarToken(3, "mozo", "MOZO");

            // 1. Intento de escalada a ADMIN por pin-switch -> 403
            HttpResponse<String> switchAdmin = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/pin-switch"))
                            .header("Authorization", "Bearer " + tokenMozo)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"pin\":\"9999\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, switchAdmin.statusCode());

            // 2. pin-login directo a ADMIN -> 403
            HttpResponse<String> pinLoginAdmin = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/pin-login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"pin\":\"9999\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, pinLoginAdmin.statusCode());

            // 3. Switch legítimo a CAJERO -> 200 y revoca el JWT anterior del mozo
            HttpResponse<String> switchCajero = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/pin-switch"))
                            .header("Authorization", "Bearer " + tokenMozo)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"cajero\",\"pin\":\"1234\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, switchCajero.statusCode());
            JsonNode switchJson = MAPPER.readTree(switchCajero.body());
            String nuevoToken = switchJson.path("token").asText();
            assertFalse(nuevoToken.isBlank());
            assertNotEquals(tokenMozo, nuevoToken);

            // 4. Reutilizar tokenMozo anterior debe devolver 401 (revocado)
            HttpResponse<String> reusoViejo = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/mesas"))
                            .header("Authorization", "Bearer " + tokenMozo)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, reusoViejo.statusCode(), "El JWT anterior debe quedar revocado tras pin-switch");
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("TotpService: rechaza replay en mismo paso de 30s y cifra/descifra totp_secret con AES-256-GCM")
    void testTotpAntiReplayYCifradoEnReposo() throws Exception {
        AtomicLong reloj = new AtomicLong(1_700_000_000_000L);
        TotpService totp = new TotpService(reloj::get);

        String secretBase32 = totp.generarSecretoBase32();
        String codigoPaso1 = totp.obtenerCodigoActualFormateado(secretBase32);

        // Primer uso aceptado; replay inmediato en el mismo paso de 30s rechazado
        assertTrue(totp.verificarCodigo("1", secretBase32, codigoPaso1));
        assertFalse(totp.verificarCodigo("1", secretBase32, codigoPaso1), "Replay del mismo código TOTP debe rechazarse");

        // Avanzar 30 segundos al siguiente paso -> nuevo código aceptado
        reloj.addAndGet(30_000L);
        String codigoPaso2 = totp.obtenerCodigoActualFormateado(secretBase32);
        assertTrue(totp.verificarCodigo("1", secretBase32, codigoPaso2));

        // Cifrado opcional en reposo AES-256-GCM al configurar FOODCORE_TOTP_ENC_KEY
        System.setProperty(TotpService.ENV_TOTP_ENC_KEY, "ClaveMaestra-TOTP-Test-2026!");
        String cifrado = totp.protegerSecretoParaAlmacenamiento(secretBase32);
        assertTrue(cifrado.startsWith(TotpService.PREFIX_ENC));
        assertNotEquals(secretBase32, cifrado);
        assertEquals(secretBase32, totp.recuperarSecreto(cifrado));
        assertTrue(totp.construirOtpAuthUri("admin", cifrado).contains("secret=" + secretBase32));

        // Verificar código usando directamente el secreto cifrado en un paso nuevo
        reloj.addAndGet(30_000L);
        String codigoPaso3 = totp.obtenerCodigoActualFormateado(cifrado);
        assertTrue(totp.verificarCodigo("1", cifrado, codigoPaso3));

        // Sin clave configurada, un secreto cifrado falla de forma segura (fail-closed)
        System.clearProperty(TotpService.ENV_TOTP_ENC_KEY);
        assertFalse(totp.verificarCodigo("1", cifrado, codigoPaso3));
        assertThrows(IllegalStateException.class, () -> totp.recuperarSecreto(cifrado));

        // Cobertura completa de casos borde: entradas vacías, corruptas, clave incorrecta y QR PNG
        assertNull(totp.protegerSecretoParaAlmacenamiento(null));
        assertEquals("", totp.recuperarSecreto(""));
        assertEquals(secretBase32, totp.protegerSecretoParaAlmacenamiento(secretBase32));
        System.setProperty(TotpService.ENV_TOTP_ENC_KEY, "OtraClaveIncorrecta!");
        assertEquals(cifrado, totp.protegerSecretoParaAlmacenamiento(cifrado));
        assertThrows(IllegalStateException.class, () -> totp.recuperarSecreto(cifrado));
        assertThrows(IllegalStateException.class, () -> totp.recuperarSecreto(TotpService.PREFIX_ENC + "AAAA"));
        assertThrows(IllegalStateException.class, () -> totp.recuperarSecreto(TotpService.PREFIX_ENC + "%%%invalido%%%"));
        assertFalse(totp.verificarCodigo(null, "123456"));
        assertFalse(totp.verificarCodigo(secretBase32, "abc"));
        assertFalse(totp.verificarCodigo(secretBase32, "000000".equals(codigoPaso2) ? "999999" : "000000"));
        assertThrows(IllegalArgumentException.class, () -> totp.obtenerCodigoActualFormateado(""));
        assertThrows(IllegalArgumentException.class, () -> totp.generarQrPngBytes("", 200));
        byte[] qr = totp.generarQrPngBytes(totp.construirOtpAuthUri("", secretBase32), 0);
        assertTrue(qr.length > 100);
    }

    @Test
    @DisplayName("Aislamiento de application-test.properties: no se carga sin perfil explícito test")
    void testApplicationTestPropertiesNoSeCargaEnProduccion() {
        System.clearProperty("foodcore.profile");
        System.clearProperty("foodcore.test.profile");
        System.clearProperty("FOODCORE_CORS_ALLOWED_ORIGINS");
        assertFalse(ConexionBD.esPerfilPruebasActivo());

        CorsPolicy prodCors = CorsPolicy.cargarDesdeConfiguracion();
        assertTrue(prodCors.getAllowedOrigins().isEmpty(),
                "En producción cors.allowed.origins debe permanecer vacío (sin cargar application-test.properties)");

        System.setProperty("foodcore.profile", "test");
        assertTrue(ConexionBD.esPerfilPruebasActivo());
        CorsPolicy testCors = CorsPolicy.cargarDesdeConfiguracion();
        assertTrue(testCors.isOriginAllowed("https://localhost:8443"),
                "Con -Dfoodcore.profile=test debe cargar application-test.properties");
    }

    @Test
    @DisplayName("Aislamiento Cloudflare Tunnel: bloquea rutas no públicas por header CF-* y aísla conector loopback 127.0.0.1")
    void testAislamientoCloudflareYConectorMenuPublico() throws Exception {
        int mainPort = puertoLibre();
        int publicMenuPort = puertoLibre();
        JwtService jwt = new JwtService(TEST_SECRET);
        ApiServer server = ApiServer.crearServidorAislado(jwt, CorsPolicy.desdeCadena(""), new AuthRateLimiter());
        server.iniciar(mainPort, -1, publicMenuPort);

        try {
            HttpClient client = HttpClient.newHttpClient();
            String adminToken = jwt.generarToken(1, "admin", "ADMIN");

            // 1. Cabeceras CF-Ray / CF-Connecting-IP en servidor principal bloquean rutas privadas/auth con 403
            for (String rutaPrivada : List.of("/api/auth/login", "/api/dashboard/kpis", "/api/pedidos", "/dashboard", "/foodcore.cer")) {
                HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mainPort + rutaPrivada))
                        .header("CF-Ray", "8abc1234-LIM")
                        .header("Authorization", "Bearer " + adminToken);
                if ("/api/auth/login".equals(rutaPrivada)) {
                    req.header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"x\"}"));
                } else {
                    req.GET();
                }
                HttpResponse<String> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(403, resp.statusCode(),
                        "Ruta no pública debe rechazarse con 403 si incluye CF-Ray: " + rutaPrivada);
            }

            // 2. Ruta pública /api/public/productos sigue permitida con CF-Connecting-IP y emite headers de seguridad
            HttpResponse<String> publicViaCf = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mainPort + "/api/public/productos"))
                            .header("CF-Connecting-IP", "203.0.113.10")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, publicViaCf.statusCode());
            assertEquals("nosniff", publicViaCf.headers().firstValue("X-Content-Type-Options").orElse(""));
            assertEquals("DENY", publicViaCf.headers().firstValue("X-Frame-Options").orElse(""));
            assertEquals("no-referrer", publicViaCf.headers().firstValue("Referrer-Policy").orElse(""));

            // 3. Conector loopback dedicado (publicMenuPort) expone solo menú público y rechaza login/dashboard/cer
            assertEquals(publicMenuPort, server.getPuertoMenuPublicoActual());
            HttpResponse<String> menuPublico = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + publicMenuPort + "/api/public/menu-digital"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, menuPublico.statusCode());

            HttpResponse<String> authEnConectorPublico = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + publicMenuPort + "/api/auth/login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"x\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, authEnConectorPublico.statusCode());

            HttpResponse<String> dashEnConectorPublico = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + publicMenuPort + "/dashboard"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, dashEnConectorPublico.statusCode());

            // 4. Sin foodcore.demoFallback=true, dashboard KPIs y KDS items no inventan datos ficticios
            HttpResponse<String> kpis = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mainPort + "/api/dashboard/kpis"))
                            .header("Authorization", "Bearer " + adminToken)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, kpis.statusCode());
            JsonNode kpiJson = MAPPER.readTree(kpis.body());
            assertEquals(0.0, kpiJson.path("ventas_dia").asDouble());
            assertEquals(0, kpiJson.path("transacciones").asInt());

            HttpResponse<String> kdsItems = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mainPort + "/api/kds/items"))
                            .header("Authorization", "Bearer " + adminToken)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, kdsItems.statusCode());
            assertEquals("[]", kdsItems.body().trim());
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("TokenBlacklistService: caché negativa 15s, purga de expirados y postura fail-closed ante fallo de BD")
    void testTokenBlacklistNegativeCachePurgeYFailClosed() throws Exception {
        AtomicLong reloj = new AtomicLong(1_700_000_000_000L);
        AtomicInteger consultasBd = new AtomicInteger(0);

        UsuarioDAO countingDao = (UsuarioDAO) Proxy.newProxyInstance(
                UsuarioDAO.class.getClassLoader(),
                new Class<?>[]{UsuarioDAO.class},
                (proxy, method, args) -> {
                    if ("obtenerTokensInvalidadosAntes".equals(method.getName())) {
                        consultasBd.incrementAndGet();
                        return Optional.of(0L);
                    }
                    return null;
                });
        AuditoriaDAO noopAudit = (AuditoriaDAO) Proxy.newProxyInstance(
                AuditoriaDAO.class.getClassLoader(),
                new Class<?>[]{AuditoriaDAO.class},
                (proxy, method, args) -> null);

        TokenBlacklistService service = new TokenBlacklistService(true, countingDao, noopAudit, reloj::get);
        JwtService.JwtClaims claims = new JwtService.JwtClaims(
                7, "mozo", "MOZO", (reloj.get() / 1000L) + 3600L, "jti-cache-test", reloj.get());

        // 1. Primera consulta contra conexión simulada hit BD; segunda dentro de 15s usa negative-cache
        Connection connOk = crearConexionResultadoVacio();
        assertFalse(service.estaRevocado(connOk, claims));
        assertEquals(1, consultasBd.get());
        reloj.addAndGet(5_000L);
        assertFalse(service.estaRevocado(connOk, claims));
        assertEquals(1, consultasBd.get(), "Dentro del TTL de 15s no debe re-consultar la BD");

        // 2. Tras 16s expira negative-cache y vuelve a consultar BD
        reloj.addAndGet(11_000L);
        assertFalse(service.estaRevocado(connOk, claims));
        assertEquals(2, consultasBd.get());

        // 3. Ante fallo SQL en modo BD -> fail-closed (rechaza token)
        reloj.addAndGet(20_000L);
        Connection connFalla = crearConexionConErrorSql();
        assertTrue(service.estaRevocado(connFalla, claims), "En modo BD, error SQL debe fallar cerrado (revocado=true)");
        assertTrue(service.estaRevocado(null, claims), "En modo BD, conexión nula debe fallar cerrado");

        // 4. Purga de tokensRevocados expirados
        service.revocarToken(null, "jti-expirado", 7, 1, "test", Instant.ofEpochSecond((reloj.get() / 1000L) - 10));
        service.revocarToken(null, "jti-vigente", 7, 1, "test", Instant.ofEpochSecond((reloj.get() / 1000L) + 300));
        assertEquals(2, service.getCantidadTokensRevocadosEnMemoria());
        service.purgarExpirados();
        assertEquals(1, service.getCantidadTokensRevocadosEnMemoria());
    }

    @Test
    @DisplayName("AuthRateLimiter: limita el mapa buckets a maxTrackedIps y depura IPs expiradas")
    void testAuthRateLimiterAcotadoYEviccion() {
        AtomicLong reloj = new AtomicLong(1_000_000L);
        AuthRateLimiter limiter = new AuthRateLimiter(5, 60_000L, reloj::get, 3);

        assertTrue(limiter.tryConsume("10.0.0.1"));
        assertTrue(limiter.tryConsume("10.0.0.2"));
        assertTrue(limiter.tryConsume("10.0.0.3"));
        assertEquals(3, limiter.getTrackedIpCount());

        // Cuarta IP distinta con mapa lleno -> fail-closed
        assertFalse(limiter.tryConsume("10.0.0.4"));
        assertEquals(3, limiter.getTrackedIpCount());

        // Al vencer ventana, depura entradas antiguas y admite nuevas IPs
        reloj.addAndGet(61_000L);
        assertTrue(limiter.tryConsume("10.0.0.4"));
        assertEquals(1, limiter.getTrackedIpCount());
    }

    @Test
    @DisplayName("Sockets KDS y TerminalAuthService: anti-OOM en lectura, rechazo sin handshake, expiración y emitirMensajeInterno")
    void testSocketsAuthAntiOomYExpiracionSesion() throws Exception {
        // 1. Reader acotado detiene lectura inmediatamente al superar maxBytes (anti-OOM)
        byte[] gigante = "X".repeat(2048).getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class,
                () -> ServidorSockets.leerLineaAcotada(new ByteArrayInputStream(gigante), 1024));
        assertEquals("{\"type\":\"PING\"}",
                ServidorSockets.leerLineaAcotada(
                        new ByteArrayInputStream("{\"type\":\"PING\"}\n".getBytes(StandardCharsets.UTF_8)), 1024));

        // 2. TerminalAuthService.obtenerSesion verifica expiración y desaloja sesión expirada
        AtomicReference<LocalDateTime> reloj = new AtomicReference<>(LocalDateTime.of(2026, 11, 18, 10, 0));
        TerminalAuthService authSvc = new TerminalAuthService(new EncriptadorPassword(), reloj::get);
        String secretHash = EncriptadorPassword.generarHash("Sec#2026!");
        Connection mockTermConn = crearConexionTerminalMock(secretHash);
        TerminalAuthService.SesionTerminal sesion = authSvc.autenticar(mockTermConn, "KDS-TEST", "Sec#2026!");
        assertNotNull(authSvc.obtenerSesion(sesion.getSessionToken()));
        assertEquals("KDS-TEST", sesion.getCodigoTerminal());
        assertEquals(TerminalAuthService.TTL_SEGUNDOS, sesion.getTtlSegundos());
        assertNotNull(sesion.getExpiraEn());
        assertNull(authSvc.obtenerSesion(null));
        assertNull(authSvc.obtenerSesion("inexistente"));
        TerminalAuthService.SesionTerminal renovada = authSvc.renovar(sesion.getSessionToken());
        assertNotEquals(sesion.getSessionSecret(), renovada.getSessionSecret());
        reloj.set(reloj.get().plusSeconds(TerminalAuthService.TTL_SEGUNDOS + 1));
        assertNull(authSvc.obtenerSesion(sesion.getSessionToken()),
                "Sesión expirada debe retornar null y eliminarse del mapa");
        assertThrows(com.foodcore.exception.TerminalNoAutorizadaException.class,
                () -> authSvc.renovar(sesion.getSessionToken()));
        assertThrows(com.foodcore.exception.TerminalNoAutorizadaException.class,
                () -> authSvc.renovar(""));
        TerminalAuthService defaultSvc = new TerminalAuthService(new EncriptadorPassword());
        assertThrows(com.foodcore.exception.TerminalNoAutorizadaException.class,
                () -> defaultSvc.autenticar(mockTermConn, "", "Sec#2026!"));
        assertThrows(com.foodcore.exception.TerminalNoAutorizadaException.class,
                () -> defaultSvc.autenticar(mockTermConn, "KDS-TEST", "WrongSecret!"));
        assertThrows(com.foodcore.exception.TerminalNoAutorizadaException.class,
                () -> defaultSvc.autenticar(crearConexionResultadoVacio(), "KDS-NONE", "Sec#2026!"));
        authSvc.limpiarSesiones();

        // 3. ServidorSockets: cliente no autenticado NO entra a clientesConectados; trama desconocida rechazada; emitirMensajeInterno notifica listener
        ServidorSockets servidor = ServidorSockets.getInstance();
        servidor.detener();
        servidor.cerrarSesionesActivas();
        AtomicReference<JsonObject> recibidoListener = new AtomicReference<>();
        servidor.setMensajeListener(recibidoListener::set);

        JsonObject desconocida = servidor.procesarTramaProtocolo("{\"accion\":\"HACKEO\"}", null);
        assertEquals("ERROR", desconocida.get("type").getAsString());
        assertEquals("FRAME_NO_AUTORIZADO", desconocida.get("code").getAsString());
        assertNull(recibidoListener.get(), "Trama no autenticada nunca debe invocar mensajeListener");

        servidor.emitirMensajeInterno("{\"accion\":\"NUEVA_COMANDA\",\"idPedido\":42}");
        assertNotNull(recibidoListener.get());
        assertEquals(42, recibidoListener.get().get("idPedido").getAsInt());

        int tcpPort = puertoLibre();
        servidor.iniciar(tcpPort);
        try (Socket rawClient = new Socket("127.0.0.1", tcpPort)) {
            PrintWriter pw = new PrintWriter(rawClient.getOutputStream(), true, StandardCharsets.UTF_8);
            pw.println("{\"accion\":\"SIN_HELLO\"}");
            Thread.sleep(100);
            assertEquals(0, servidor.getCantidadClientesConectados(),
                    "Cliente TCP sin HELLO/AUTH_TERMINAL válido no debe registrarse en clientesConectados");
        } finally {
            servidor.setMensajeListener(null);
            servidor.detener();
        }
    }

    @Test
    @DisplayName("DeliveryWebhookService: bind loopback 127.0.0.1, HMAC-SHA256 obligatorio y límite estricto de 64 KB")
    void testDeliveryWebhookSeguridadHmacYLimite64Kb() throws Exception {
        DeliveryWebhookService webhook = new DeliveryWebhookService();
        webhook.iniciarServidor(0);
        int port = webhook.getPuertoActivo();
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/delivery/webhook");

        try {
            // 1. Sin secreto configurado -> 503 fail-closed
            System.clearProperty(DeliveryWebhookService.ENV_WEBHOOK_SECRET);
            HttpResponse<String> sinSecreto = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, sinSecreto.statusCode());

            // 2. Con secreto configurado, firma faltante o inválida -> 401
            String secret = "WebhookSecret#2026-StrongKey!";
            System.setProperty(DeliveryWebhookService.ENV_WEBHOOK_SECRET, secret);
            HttpResponse<String> sinFirma = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"items\":[]}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, sinFirma.statusCode());

            // 3. Cuerpo > 64 KB -> 413 Payload Too Large
            String payloadGrande = "{\"pad\":\"" + "A".repeat(DeliveryWebhookService.MAX_BODY_BYTES + 128) + "\"}";
            String firmaGrande = DeliveryWebhookService.calcularFirmaHmacSha256(payloadGrande, secret);
            HttpResponse<String> muyGrande = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .header(DeliveryWebhookService.HEADER_SIGNATURE, firmaGrande)
                            .POST(HttpRequest.BodyPublishers.ofString(payloadGrande))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(413, muyGrande.statusCode());

            // 4. Payload firmado pero inválido (sin items) -> 422
            String payloadInvalido = "{\"plataforma\":\"RAPPI\",\"items\":[]}";
            String firmaInvalido = DeliveryWebhookService.calcularFirmaHmacSha256(payloadInvalido, secret);
            HttpResponse<String> invalido = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .header(DeliveryWebhookService.HEADER_SIGNATURE, "sha256=" + firmaInvalido)
                            .POST(HttpRequest.BodyPublishers.ofString(payloadInvalido))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(422, invalido.statusCode());
        } finally {
            webhook.detenerServidor();
            assertFalse(webhook.isServidorActivo());
        }
    }

    private static int puertoLibre() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static UsuarioDAO crearUsuarioDaoEnMemoria(List<Usuario> usuarios) {
        return (UsuarioDAO) Proxy.newProxyInstance(
                UsuarioDAO.class.getClassLoader(),
                new Class<?>[]{UsuarioDAO.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "buscarPorUsername" -> {
                        String u = (String) args[1];
                        yield usuarios.stream().filter(x -> u != null && u.equalsIgnoreCase(x.getUsername())).findFirst();
                    }
                    case "buscarPorId" -> {
                        int id = (int) args[1];
                        yield usuarios.stream().filter(x -> x.getIdUsuario() == id).findFirst();
                    }
                    case "listarTodosActivos" -> usuarios.stream().filter(Usuario::isActivo).toList();
                    case "actualizarPin", "actualizarConfiguracion2FA", "registrarInvalidacionTokensAntes" -> true;
                    case "obtenerTokensInvalidadosAntes" -> Optional.of(0L);
                    default -> method.getReturnType().equals(Optional.class) ? Optional.empty() : null;
                });
    }

    private static Connection crearConexionNoOp() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        return crearPreparedStatementVacio();
                    }
                    return null;
                });
    }

    private static Connection crearConexionResultadoVacio() {
        return crearConexionNoOp();
    }

    private static PreparedStatement crearPreparedStatementVacio() {
        ResultSet emptyRs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, m, a) -> "next".equals(m.getName()) ? false : null);
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, m, a) -> switch (m.getName()) {
                    case "executeQuery" -> emptyRs;
                    case "executeUpdate" -> 0;
                    default -> null;
                });
    }

    private static Connection crearConexionConErrorSql() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        throw new SQLException("Simulated DB outage");
                    }
                    return null;
                });
    }

    private static Connection crearConexionTerminalMock(String secretHash) {
        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, m, a) -> switch (m.getName()) {
                    case "next" -> true;
                    case "getBoolean" -> true;
                    case "getString" -> secretHash;
                    default -> null;
                });
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, m, a) -> switch (m.getName()) {
                    case "executeQuery" -> rs;
                    case "executeUpdate" -> 1;
                    default -> null;
                });
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> "prepareStatement".equals(method.getName()) ? ps : null);
    }
}
