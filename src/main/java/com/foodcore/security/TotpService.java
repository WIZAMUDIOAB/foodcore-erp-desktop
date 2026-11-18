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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Servicio TOTP (RFC 6238) para autenticación en dos pasos (2FA) de administradores
 * usando {@code com.warrenstrange:googleauth} y generación de códigos QR con ZXing.
 */
public final class TotpService {

    public static final String ISSUER = "FoodCoreERP";
    private static final int QR_DEFAULT_SIZE = 220;

    private final GoogleAuthenticator authenticator;

    /**
     * Crea una instancia con la configuración estándar de Google Authenticator.
     */
    public TotpService() {
        this.authenticator = new GoogleAuthenticator();
    }

    /**
     * Genera un nuevo secreto TOTP aleatorio en Base32.
     *
     * @return Secreto Base32 listo para persistir y registrar en una app autenticadora.
     */
    public String generarSecretoBase32() {
        GoogleAuthenticatorKey key = authenticator.createCredentials();
        return key.getKey();
    }

    /**
     * Construye el URI estándar {@code otpauth://totp/...} compatible con Google Authenticator
     * y Authy.
     *
     * @param username Nombre de usuario propietario del secreto.
     * @param secret   Secreto Base32.
     * @return URI {@code otpauth://totp/...}.
     */
    public String construirOtpAuthUri(String username, String secret) {
        String cuenta = (username == null || username.isBlank()) ? "admin" : username.trim();
        String secretoLimpio = secret == null ? "" : secret.trim();
        String labelCodificado = codificarComponente(ISSUER + ":" + cuenta);
        String issuerCodificado = codificarComponente(ISSUER);
        return "otpauth://totp/" + labelCodificado
                + "?secret=" + codificarComponente(secretoLimpio)
                + "&issuer=" + issuerCodificado
                + "&digits=6&period=30";
    }

    /**
     * Verifica si un código OTP de 6 dígitos es válido para el secreto Base32 indicado.
     *
     * @param secret     Secreto Base32 almacenado para el usuario.
     * @param codigoText Código numérico ingresado por el usuario.
     * @return {@code true} si el código coincide dentro de la ventana TOTP permitida.
     */
    public boolean verificarCodigo(String secret, String codigoText) {
        if (secret == null || secret.isBlank() || codigoText == null) {
            return false;
        }
        String limpio = codigoText.trim().replaceAll("\\s+", "");
        if (!limpio.matches("\\d{6}")) {
            return false;
        }
        try {
            int codigo = Integer.parseInt(limpio);
            return authenticator.authorize(secret.trim(), codigo);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Calcula el código TOTP de 6 dígitos vigente en el instante actual para pruebas y diagnóstico.
     *
     * @param secret Secreto Base32 válido.
     * @return Código formateado con 6 dígitos (cero a la izquierda incluido).
     */
    public String obtenerCodigoActualFormateado(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("El secreto TOTP no puede estar vacío");
        }
        int code = authenticator.getTotpPassword(secret.trim());
        return String.format("%06d", code);
    }

    /**
     * Genera los bytes PNG de un código QR a partir del URI {@code otpauth://}.
     *
     * @param otpAuthUri URI TOTP generado por {@link #construirOtpAuthUri(String, String)}.
     * @param sizePx     Ancho y alto en píxeles.
     * @return Imagen PNG codificada en bytes.
     * @throws IOException si falla la escritura PNG o la codificación QR.
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
     *
     * @param username Nombre del usuario.
     * @param secret   Secreto Base32.
     * @return Imagen JavaFX del QR TOTP.
     * @throws IOException si falla la codificación del QR.
     */
    public Image generarQrImageFx(String username, String secret) throws IOException {
        String uri = construirOtpAuthUri(username, secret);
        byte[] png = generarQrPngBytes(uri, QR_DEFAULT_SIZE);
        return new Image(new ByteArrayInputStream(png));
    }

    private String codificarComponente(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
