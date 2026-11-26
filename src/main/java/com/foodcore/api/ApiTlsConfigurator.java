package com.foodcore.api;

import io.javalin.http.Context;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configurador TLS HTTPS (keystore, contraseña obligatoria, conector Jetty SSL y certificado público X.509).
 */
final class ApiTlsConfigurator {

    private static final Logger LOGGER = Logger.getLogger(ApiTlsConfigurator.class.getName());

    private volatile byte[] certificadoActivo;

    void limpiarCertificadoActivo() {
        this.certificadoActivo = null;
    }

    static String resolverKeystorePasswordObligatorio(Function<String, String> envReader) {
        String configured = leerVariableOPropiedad(ApiServer.ENV_KEYSTORE_PASS, envReader);
        if (configured == null || configured.isBlank() || configured.startsWith("${")) {
            throw new IllegalStateException(
                    "Variable de entorno obligatoria no configurada: " + ApiServer.ENV_KEYSTORE_PASS
                    + " (mínimo " + ApiServer.MIN_KEYSTORE_PASS_LENGTH + " caracteres)."
            );
        }
        if (configured.length() < ApiServer.MIN_KEYSTORE_PASS_LENGTH) {
            throw new IllegalStateException(
                    ApiServer.ENV_KEYSTORE_PASS + " es demasiado corta; debe tener al menos "
                    + ApiServer.MIN_KEYSTORE_PASS_LENGTH + " caracteres."
            );
        }
        return configured;
    }

    static String leerVariableOPropiedad(String name) {
        return leerVariableOPropiedad(name, System::getenv);
    }

    static String leerVariableOPropiedad(String name, Function<String, String> envReader) {
        String sysProp = System.getProperty(name);
        if (sysProp != null) {
            String trimmed = sysProp.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        String envVal = envReader != null ? envReader.apply(name) : null;
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        return null;
    }

    static ServerConnector crearConectorHttps(Server server, HttpConfiguration httpConfig,
            SslContextFactory.Server sslContextFactory, int httpsPort) {
        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        SecureRequestCustomizer customizer = new SecureRequestCustomizer();
        customizer.setSniHostCheck(false);
        httpsConfig.addCustomizer(customizer);

        ServerConnector sslConnector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfig));
        sslConnector.setPort(httpsPort);
        return sslConnector;
    }

    SslContextFactory.Server construirDesdeKeystore(KeyStore ks, String keystorePassword) {
        extraerCertificadoActivo(ks);
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStore(ks);
        sslContextFactory.setKeyStorePassword(keystorePassword);
        sslContextFactory.setKeyManagerPassword(keystorePassword);
        return sslContextFactory;
    }

    KeyStore cargarKeystorePorDefecto(char[] passwordChars) throws IOException, GeneralSecurityException {
        File defaultFile = new File("keystore.jks");
        if (defaultFile.exists() && defaultFile.isFile()) {
            return KeyStore.getInstance(defaultFile, passwordChars);
        }
        try (InputStream is = ApiTlsConfigurator.class.getResourceAsStream("/keystore.jks")) {
            if (is != null) {
                return cargarKeystoreStream(is.readAllBytes(), passwordChars);
            }
        }
        return null;
    }

    boolean servirCertificadoDesdeMemoriaOClasspath(Context ctx) throws IOException {
        byte[] certEnMemoria = this.certificadoActivo;
        if (certEnMemoria != null && certEnMemoria.length > 0) {
            ctx.contentType("application/x-x509-ca-cert");
            ctx.result(certEnMemoria);
            return true;
        }
        try (InputStream is = ApiTlsConfigurator.class.getResourceAsStream("/foodcore.cer")) {
            if (is != null) {
                ctx.contentType("application/x-x509-ca-cert");
                ctx.result(is.readAllBytes());
                return true;
            }
        }
        File f = new File("foodcore.cer");
        if (!f.exists()) {
            f = new File("pwa/foodcore.cer");
        }
        if (f.exists()) {
            ctx.contentType("application/x-x509-ca-cert");
            ctx.result(Files.readAllBytes(f.toPath()));
            return true;
        }
        return false;
    }

    private KeyStore cargarKeystoreStream(byte[] rawBytes, char[] passwordChars) {
        for (String type : new String[]{"PKCS12", "JKS"}) {
            try {
                KeyStore ks = KeyStore.getInstance(type);
                try (ByteArrayInputStream bais = new ByteArrayInputStream(rawBytes)) {
                    ks.load(bais, passwordChars);
                    return ks;
                }
            } catch (IOException | GeneralSecurityException e) {
                LOGGER.log(Level.FINEST, "Formato de keystore " + type + " no compatible; intentando siguiente", e);
            }
        }
        throw new IllegalStateException("Formato de keystore HTTPS inválido o contraseña incorrecta.");
    }

    private void extraerCertificadoActivo(KeyStore ks) {
        try {
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert != null) {
                    this.certificadoActivo = cert.getEncoded();
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo extraer certificado X.509 desde keystore activo", e);
        }
    }
}
