package com.foodcore.api;

import com.foodcore.api.security.AuthRateLimiter;
import com.foodcore.api.security.CorsPolicy;
import com.foodcore.api.security.JwtService;
import com.foodcore.support.TestSecuritySupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas fail-fast para {@code FOODCORE_KEYSTORE_PASS} y arranque HTTPS con keystore efímero en {@code @TempDir}.
 */
class ApiServerKeystoreTest {

    private static final byte[] TEST_JWT_SECRET =
            "FoodCore-KeystoreTest-Secret-Key-2026-32B!".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private String prevPass;
    private String prevPath;
    private String prevCert;

    @BeforeEach
    void saveAndClearProps() {
        prevPass = System.getProperty(ApiServer.ENV_KEYSTORE_PASS);
        prevPath = System.getProperty(ApiServer.ENV_KEYSTORE_PATH);
        prevCert = System.getProperty(ApiServer.ENV_CERT_PATH);
        System.setProperty(ApiServer.ENV_KEYSTORE_PASS, "");
        System.clearProperty(ApiServer.ENV_KEYSTORE_PATH);
        System.clearProperty(ApiServer.ENV_CERT_PATH);
    }

    @AfterEach
    void restoreProps() {
        restaurarProp(ApiServer.ENV_KEYSTORE_PASS, prevPass);
        restaurarProp(ApiServer.ENV_KEYSTORE_PATH, prevPath);
        restaurarProp(ApiServer.ENV_CERT_PATH, prevCert);
    }

    @Test
    @DisplayName("Falla fail-fast cuando falta FOODCORE_KEYSTORE_PASS")
    void fallaSiFaltaKeystorePassword() {
        System.clearProperty(ApiServer.ENV_KEYSTORE_PASS);
        IllegalStateException envMissingEx = assertThrows(
                IllegalStateException.class,
                () -> ApiServer.resolverKeystorePasswordObligatorio(key -> null)
        );
        assertTrue(envMissingEx.getMessage().contains(ApiServer.ENV_KEYSTORE_PASS));

        System.setProperty(ApiServer.ENV_KEYSTORE_PASS, "");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                ApiServer::resolverKeystorePasswordObligatorio
        );
        assertTrue(ex.getMessage().contains(ApiServer.ENV_KEYSTORE_PASS));

        ApiServer server = crearServidorTest();
        try {
            assertThrows(IllegalStateException.class, () -> server.iniciar(0, 0));
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("Falla fail-fast cuando FOODCORE_KEYSTORE_PASS tiene menos de 8 caracteres")
    void fallaSiKeystorePasswordEsMuyCorta() {
        System.setProperty(ApiServer.ENV_KEYSTORE_PASS, "abc1234");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                ApiServer::resolverKeystorePasswordObligatorio
        );
        assertTrue(ex.getMessage().contains("8"));

        ApiServer server = crearServidorTest();
        try {
            assertThrows(IllegalStateException.class, () -> server.iniciar(0, 0));
        } finally {
            server.detener();
        }
    }

    @Test
    @DisplayName("Inicia HTTPS con keystore efímero generado por keytool en @TempDir")
    void iniciaHttpsConKeystoreEfimero() throws Exception {
        TestSecuritySupport.KeystoreArtifacts artifacts = TestSecuritySupport.generarKeystoreTemporal(tempDir);
        System.setProperty(ApiServer.ENV_KEYSTORE_PASS, artifacts.password());
        System.setProperty(ApiServer.ENV_KEYSTORE_PATH, artifacts.keystorePath().toAbsolutePath().toString());
        System.setProperty(ApiServer.ENV_CERT_PATH, artifacts.certPath().toAbsolutePath().toString());

        int httpPort;
        int httpsPort;
        try (ServerSocket s1 = new ServerSocket(0); ServerSocket s2 = new ServerSocket(0)) {
            httpPort = s1.getLocalPort();
            httpsPort = s2.getLocalPort();
        }

        ApiServer server = crearServidorTest();
        server.iniciar(httpPort, httpsPort);
        try {
            HttpClient client = crearHttpClientTrustAll();
            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + httpsPort + "/api/health"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("UP"));

            HttpResponse<byte[]> certResp = client.send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + httpsPort + "/foodcore.cer"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            assertEquals(200, certResp.statusCode());
            assertTrue(certResp.body().length > 200);
        } finally {
            server.detener();
        }
    }

    private static ApiServer crearServidorTest() {
        return ApiServer.crearServidorAislado(
                new JwtService(TEST_JWT_SECRET),
                new CorsPolicy(Set.of("http://localhost")),
                new AuthRateLimiter()
        );
    }

    private static void restaurarProp(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    private static HttpClient crearHttpClientTrustAll() throws Exception {
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }}, new SecureRandom());
        return HttpClient.newBuilder().sslContext(sc).build();
    }
}
