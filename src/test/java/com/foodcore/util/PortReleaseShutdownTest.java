package com.foodcore.util;

import com.foodcore.App;
import com.foodcore.api.ApiServer;
import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.support.AsyncTestSupport;
import com.foodcore.support.TestSecuritySupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que el cierre de la aplicación libera los puertos de ApiServer y ServidorSockets (ÍTEM 5 v5.1.0).
 */
class PortReleaseShutdownTest {

    private static final byte[] TEST_JWT_SECRET =
            "FoodCore-PortRelease-Test-Key-2026-32B!!".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private String prevKeyPass;
    private String prevKeyPath;
    private String prevCertPath;

    @BeforeEach
    void setupProps() {
        App.detenerServiciosAplicacion();
        prevKeyPass = System.getProperty(ApiServer.ENV_KEYSTORE_PASS);
        prevKeyPath = System.getProperty(ApiServer.ENV_KEYSTORE_PATH);
        prevCertPath = System.getProperty(ApiServer.ENV_CERT_PATH);
    }

    @AfterEach
    void restoreProps() {
        App.detenerServiciosAplicacion();
        restaurar(ApiServer.ENV_KEYSTORE_PASS, prevKeyPass);
        restaurar(ApiServer.ENV_KEYSTORE_PATH, prevKeyPath);
        restaurar(ApiServer.ENV_CERT_PATH, prevCertPath);
    }

    @Test
    @DisplayName("ÍTEM 5: App.detenerServiciosAplicacion libera HTTP, HTTPS y Socket TCP para reutilización inmediata")
    void detenerServiciosLiberaPuertosDeApiYSockets() throws Exception {
        TestSecuritySupport.KeystoreArtifacts artifacts = TestSecuritySupport.generarKeystoreTemporal(tempDir);
        System.setProperty(ApiServer.ENV_KEYSTORE_PASS, artifacts.password());
        System.setProperty(ApiServer.ENV_KEYSTORE_PATH, artifacts.keystorePath().toAbsolutePath().toString());
        System.setProperty(ApiServer.ENV_CERT_PATH, artifacts.certPath().toAbsolutePath().toString());

        int[] puertos = reservarPuertosDistintos(3);
        int tcpPort = puertos[0];
        int httpPort = puertos[1];
        int httpsPort = puertos[2];

        ServidorSockets sockets = ServidorSockets.getInstance();
        sockets.iniciar(tcpPort);
        assertTrue(sockets.isCorriendo(), "ServidorSockets debe quedar escuchando");
        assertThrows(IOException.class, () -> {
            try (ServerSocket ocupado = new ServerSocket(tcpPort)) {
                occupiedCheck(ocupado);
            }
        }, "El puerto TCP debe estar ocupado mientras ServidorSockets corre");

        TestSecuritySupport.asegurarJwtSecretTest();
        ApiServer singleton = ApiServer.getInstance();
        singleton.iniciar(httpPort, httpsPort);
        assertTrue(singleton.isRunning(), "ApiServer singleton debe quedar activo");
        assertThrows(IOException.class, () -> {
            try (ServerSocket ocupado = new ServerSocket(httpPort)) {
                occupiedCheck(ocupado);
            }
        });
        assertThrows(IOException.class, () -> {
            try (ServerSocket ocupado = new ServerSocket(httpsPort)) {
                occupiedCheck(ocupado);
            }
        });

        // Acción bajo prueba: cierre global de servicios de la aplicación
        App.detenerServiciosAplicacion();
        // Idempotencia al llamarlo dos veces (setOnCloseRequest + stop / shutdownHook)
        App.detenerServiciosAplicacion();

        assertFalse(singleton.isRunning(), "ApiServer debe estar detenido");
        assertFalse(sockets.isCorriendo(), "ServidorSockets debe estar detenido");

        // Verificar que todos los puertos quedaron libres para enlace
        assertPuertoLiberado(tcpPort);
        assertPuertoLiberado(httpPort);
        assertPuertoLiberado(httpsPort);

        // Además verificar un servidor aislado HTTP con puertos dedicados
        int isoHttp = reservarPuertosDistintos(1)[0];
        ApiServer aislado = ApiServer.crearServidorAislado(
                new JwtService(TEST_JWT_SECRET),
                new CorsPolicy(Set.of("http://localhost")),
                new AuthRateLimiter()
        );
        aislado.iniciar(isoHttp, -1);
        assertTrue(aislado.isRunning());
        aislado.detener();
        assertFalse(aislado.isRunning());
        assertPuertoLiberado(isoHttp);
    }

    private static void assertPuertoLiberado(int port) throws Exception {
        AtomicReference<IOException> lastEx = new AtomicReference<>();
        boolean libre = AsyncTestSupport.awaitUntilQuiet(() -> {
            try (ServerSocket ss = new ServerSocket()) {
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(port));
                assertTrue(ss.isBound(), "Puerto " + port + " debe quedar disponible");
                return true;
            } catch (IOException ex) {
                lastEx.set(ex);
                return false;
            }
        }, Duration.ofMillis(1000), Duration.ofMillis(20));
        if (!libre) {
            throw lastEx.get() != null ? lastEx.get() : new IOException("Puerto " + port + " no liberado");
        }
    }

    private static int[] reservarPuertosDistintos(int cantidad) throws IOException {
        ServerSocket[] abiertos = new ServerSocket[cantidad];
        int[] puertos = new int[cantidad];
        try {
            for (int i = 0; i < cantidad; i++) {
                abiertos[i] = new ServerSocket(0);
                puertos[i] = abiertos[i].getLocalPort();
            }
            return puertos;
        } finally {
            for (ServerSocket ss : abiertos) {
                if (ss != null && !ss.isClosed()) {
                    try {
                        ss.close();
                    } catch (IOException ignored) {
                        // Cierre preventivo de reserva temporal
                    }
                }
            }
        }
    }

    private static void occupiedCheck(ServerSocket s) {
        if (s != null) {
            s.getLocalPort();
        }
    }

    private static void restaurar(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }
}
