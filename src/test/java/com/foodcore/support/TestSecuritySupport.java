package com.foodcore.support;

import com.foodcore.api.ApiServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utilidad compartida de pruebas para generar keystores HTTPS efímeros en directorio temporal
 * vía {@code keytool -genkeypair} sin almacenar contraseñas ni archivos {@code .jks} en el repositorio.
 */
public final class TestSecuritySupport {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static Path tempKeystoreDir;
    private static String ephemeralKeystorePass;
    private static String ephemeralJwtSecret;

    private TestSecuritySupport() {
    }

    public static synchronized KeystoreArtifacts generarKeystoreTemporal(Path directory) throws Exception {
        String pass = generarTextoAleatorio(24);
        Path ksPath = directory.resolve("keystore.jks");
        Path certPath = directory.resolve("foodcore.cer");
        ejecutarKeytool(List.of(
                resolverKeytoolExecutable(),
                "-genkeypair", "-noprompt",
                "-alias", "foodcore",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-storetype", "PKCS12",
                "-validity", "2",
                "-keystore", ksPath.toAbsolutePath().toString(),
                "-storepass", pass,
                "-keypass", pass,
                "-dname", "CN=localhost, OU=FoodCore-Test, O=FoodCore, L=Lima, C=PE",
                "-ext", "SAN=DNS:localhost,IP:127.0.0.1"
        ));
        ejecutarKeytool(List.of(
                resolverKeytoolExecutable(),
                "-exportcert", "-noprompt",
                "-alias", "foodcore",
                "-keystore", ksPath.toAbsolutePath().toString(),
                "-storepass", pass,
                "-file", certPath.toAbsolutePath().toString()
        ));
        return new KeystoreArtifacts(ksPath, certPath, pass);
    }

    public static synchronized void asegurarKeystoreE2E() {
        try {
            if (tempKeystoreDir == null) {
                tempKeystoreDir = Files.createTempDirectory("foodcore-e2e-ks-");
                KeystoreArtifacts artifacts = generarKeystoreTemporal(tempKeystoreDir);
                ephemeralKeystorePass = artifacts.password();
                System.setProperty(ApiServer.ENV_KEYSTORE_PATH, artifacts.keystorePath().toAbsolutePath().toString());
                System.setProperty(ApiServer.ENV_CERT_PATH, artifacts.certPath().toAbsolutePath().toString());
                System.setProperty(ApiServer.ENV_KEYSTORE_PASS, ephemeralKeystorePass);

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        Files.deleteIfExists(artifacts.keystorePath());
                        Files.deleteIfExists(artifacts.certPath());
                        Files.deleteIfExists(tempKeystoreDir);
                    } catch (IOException ignored) {
                        // Limpieza best-effort de carpeta temporal de tests
                    }
                }));
            } else {
                System.setProperty(ApiServer.ENV_KEYSTORE_PATH, tempKeystoreDir.resolve("keystore.jks").toAbsolutePath().toString());
                System.setProperty(ApiServer.ENV_CERT_PATH, tempKeystoreDir.resolve("foodcore.cer").toAbsolutePath().toString());
                System.setProperty(ApiServer.ENV_KEYSTORE_PASS, ephemeralKeystorePass);
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el keystore temporal para pruebas HTTPS", e);
        }
    }

    public static synchronized void asegurarJwtSecretTest() {
        String existing = System.getProperty("FOODCORE_JWT_SECRET");
        if (existing == null || existing.isBlank()) {
            String env = System.getenv("FOODCORE_JWT_SECRET");
            if (env != null && !env.isBlank()) {
                return;
            }
            if (ephemeralJwtSecret == null) {
                ephemeralJwtSecret = generarTextoAleatorio(36);
            }
            System.setProperty("FOODCORE_JWT_SECRET", ephemeralJwtSecret);
        }
    }

    public static String generarTextoAleatorio(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String resolverKeytoolExecutable() {
        Path binDir = Path.of(System.getProperty("java.home"), "bin");
        Path exe = binDir.resolve("keytool.exe");
        if (Files.isExecutable(exe)) {
            return exe.toString();
        }
        return binDir.resolve("keytool").toString();
    }

    private static void ejecutarKeytool(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("Timeout ejecutando keytool");
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("Error ejecutando keytool (exit " + p.exitValue() + "): " + output);
        }
    }

    public record KeystoreArtifacts(Path keystorePath, Path certPath, String password) {
    }
}
