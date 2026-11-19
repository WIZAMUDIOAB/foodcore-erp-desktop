package com.foodcore.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import javafx.scene.image.Image;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Servicio TOTP (RFC 6238) para autenticación en dos pasos (2FA) de administradores
 * usando {@code com.warrenstrange:googleauth}, generación de códigos QR con ZXing,
 * protección contra replay por ventana de 30 segundos y cifrado opcional AES-256-GCM en reposo.
 */
public final class TotpService {

    public static final String ISSUER = "FoodCoreERP";
    public static final String ENV_TOTP_ENC_KEY = "FOODCORE_TOTP_ENC_KEY";
    public static final String PREFIX_ENC = "ENC:";
    private static final int QR_DEFAULT_SIZE = 220;
    private static final long TIME_STEP_MS = 30_000L;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_REPLAY_ENTRIES = 5_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ConcurrentHashMap<String, Long> ULTIMO_PASO_USADO = new ConcurrentHashMap<>();

    private final GoogleAuthenticator authenticator;
    private final LongSupplier clockMillis;

    /**
     * Crea una instancia con la configuración estándar de Google Authenticator.
     */
    public TotpService() {
        this(System::currentTimeMillis);
    }

    /**
     * Crea una instancia con reloj inyectable para pruebas deterministas de ventanas TOTP.
     */
    public TotpService(LongSupplier clockMillis) {
        this.authenticator = new GoogleAuthenticator();
        this.clockMillis = clockMillis != null ? clockMillis : System::currentTimeMillis;
    }

    /**
     * Genera un nuevo secreto TOTP aleatorio en Base32.
     */
    public String generarSecretoBase32() {
        GoogleAuthenticatorKey key = authenticator.createCredentials();
        return key.getKey();
    }

    /**
     * Cifra el secreto TOTP con AES-256-GCM cuando {@code FOODCORE_TOTP_ENC_KEY} está configurado;
     * en caso contrario lo retorna limpio para compatibilidad retroactiva.
     */
    public String protegerSecretoParaAlmacenamiento(String secretoBase32) {
        if (secretoBase32 == null || secretoBase32.isBlank()) {
            return secretoBase32;
        }
        String limpio = secretoBase32.trim();
        if (limpio.startsWith(PREFIX_ENC)) {
            return limpio;
        }
        byte[] keyBytes = resolverClaveCifradoOpcional();
        if (keyBytes == null) {
            return limpio;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(limpio.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX_ENC + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP en reposo.", ex);
        }
    }

    /**
     * Recupera el secreto Base32 plano, descifrándolo si está almacenado con prefijo {@value #PREFIX_ENC}.
     */
    public String recuperarSecreto(String secretoAlmacenado) {
        if (secretoAlmacenado == null || secretoAlmacenado.isBlank()) {
            return "";
        }
        String limpio = secretoAlmacenado.trim();
        if (!limpio.startsWith(PREFIX_ENC)) {
            return limpio;
        }
        byte[] keyBytes = resolverClaveCifradoOpcional();
        if (keyBytes == null) {
            throw new IllegalStateException(
                    "El secreto TOTP está cifrado pero " + ENV_TOTP_ENC_KEY + " no está configurado.");
        }
        try {
            byte[] combined = Base64.getUrlDecoder().decode(limpio.substring(PREFIX_ENC.length()));
            if (combined.length <= GCM_IV_BYTES) {
                throw new IllegalStateException("Secreto TOTP cifrado corrupto.");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No se pudo descifrar el secreto TOTP.", ex);
        }
    }

    /**
     * Construye el URI estándar {@code otpauth://totp/...} compatible con Google Authenticator y Authy.
     */
    public String construirOtpAuthUri(String username, String secret) {
        String cuenta = (username == null || username.isBlank()) ? "admin" : username.trim();
        String secretoLimpio = recuperarSecreto(secret);
        String labelCodificado = codificarComponente(ISSUER + ":" + cuenta);
        String issuerCodificado = codificarComponente(ISSUER);
        return "otpauth://totp/" + labelCodificado
                + "?secret=" + codificarComponente(secretoLimpio)
                + "&issuer=" + issuerCodificado
                + "&digits=6&period=30";
    }

    /**
     * Verifica un código OTP de 6 dígitos con protección anti-replay por ventana de 30s.
     */
    public boolean verificarCodigo(String secret, String codigoText) {
        return verificarCodigo(null, secret, codigoText);
    }

    /**
     * Verifica un código OTP de 6 dígitos para un usuario concreto y rechaza la reutilización
     * del mismo paso temporal de 30 segundos (o pasos anteriores) por RFC 6238.
     */
    public boolean verificarCodigo(String claveUsuario, String secret, String codigoText) {
        if (secret == null || secret.isBlank() || codigoText == null) {
            return false;
        }
        String limpio = codigoText.trim().replaceAll("\\s+", "");
        if (!limpio.matches("\\d{6}")) {
            return false;
        }
        try {
            String secretoPlano = recuperarSecreto(secret);
            if (secretoPlano.isBlank()) {
                return false;
            }
            int codigo = Integer.parseInt(limpio);
            long pasoActual = clockMillis.getAsLong() / TIME_STEP_MS;
            Long pasoCoincidente = buscarPasoCoincidente(secretoPlano, codigo, pasoActual);
            if (pasoCoincidente == null) {
                return false;
            }
            return registrarPasoSinReplay(construirClaveReplay(claveUsuario, secretoPlano), pasoCoincidente, pasoActual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Calcula el código TOTP de 6 dígitos vigente en el instante actual para pruebas y diagnóstico.
     */
    public String obtenerCodigoActualFormateado(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("El secreto TOTP no puede estar vacío");
        }
        String secretoPlano = recuperarSecreto(secret);
        int code = authenticator.getTotpPassword(secretoPlano, clockMillis.getAsLong());
        return String.format("%06d", code);
    }

    /**
     * Limpia el registro anti-replay en memoria (uso en pruebas).
     */
    public static void limpiarCacheReplay() {
        ULTIMO_PASO_USADO.clear();
    }

    /**
     * Genera los bytes PNG de un código QR a partir del URI {@code otpauth://}.
     */
    public byte[] generarQrPngBytes(String otpAuthUri, int sizePx) throws IOException {
        if (otpAuthUri == null || otpAuthUri.isBlank()) {
            throw new IllegalArgumentException("El URI TOTP no puede estar vacío");
        }
        int size = sizePx > 0 ? sizePx : QR_DEFAULT_SIZE;
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpAuthUri, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(buffered, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException ex) {
            throw new IOException("No se pudo generar el código QR 2FA", ex);
        }
    }

    /**
     * Genera una instancia {@link javafx.scene.image.Image} lista para mostrar en un {@code ImageView}.
     */
    public Image generarQrImageFx(String username, String secret) throws IOException {
        String uri = construirOtpAuthUri(username, secret);
        byte[] png = generarQrPngBytes(uri, QR_DEFAULT_SIZE);
        return new Image(new ByteArrayInputStream(png));
    }

    private Long buscarPasoCoincidente(String secretoPlano, int codigo, long pasoActual) {
        for (long delta : new long[]{0L, -1L, 1L}) {
            long paso = pasoActual + delta;
            if (paso >= 0 && authenticator.getTotpPassword(secretoPlano, paso * TIME_STEP_MS) == codigo) {
                return paso;
            }
        }
        return null;
    }

    private static boolean registrarPasoSinReplay(String replayKey, long pasoCoincidente, long pasoActual) {
        if (ULTIMO_PASO_USADO.size() > MAX_REPLAY_ENTRIES) {
            long umbral = pasoActual - 10L;
            ULTIMO_PASO_USADO.entrySet().removeIf(entry -> entry.getValue() < umbral);
        }
        AtomicBoolean aceptado = new AtomicBoolean(false);
        ULTIMO_PASO_USADO.compute(replayKey, (k, previo) -> {
            if (previo != null && previo >= pasoCoincidente) {
                aceptado.set(false);
                return previo;
            }
            aceptado.set(true);
            return pasoCoincidente;
        });
        return aceptado.get();
    }

    private static String construirClaveReplay(String claveUsuario, String secretoPlano) {
        String huella = sha256Base64(secretoPlano);
        if (claveUsuario == null || claveUsuario.isBlank()) {
            return huella;
        }
        return claveUsuario.trim() + ":" + huella;
    }

    private static byte[] resolverClaveCifradoOpcional() {
        String raw = System.getProperty(ENV_TOTP_ENC_KEY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(ENV_TOTP_ENC_KEY);
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return sha256Bytes(raw.trim());
    }

    private static byte[] sha256Bytes(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM.", ex);
        }
    }

    private static String sha256Base64(String valor) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256Bytes(valor));
    }

    private String codificarComponente(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
