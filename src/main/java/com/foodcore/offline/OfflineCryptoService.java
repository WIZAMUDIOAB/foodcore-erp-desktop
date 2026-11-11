package com.foodcore.offline;

import com.foodcore.util.ConexionBD;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Servicio criptográfico AES-256-GCM con derivación PBKDF2WithHmacSHA256
 * para proteger en reposo los datos sensibles y payloads del Outbox offline en SQLite.
 */
public final class OfflineCryptoService {

    public static final String ENV_OFFLINE_DB_KEY = "FOODCORE_OFFLINE_DB_KEY";

    private static final String VERSION_PREFIX = "v1:";
    private static final String VERSION_TAG = "v1";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65_536;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OfflineCryptoService() {
    }

    /**
     * Obtiene la clave maestra de cifrado offline desde propiedad del sistema o variable de entorno.
     * Falla explícitamente si falta o está vacía (sin fallback hardcodeado).
     */
    public static String requireKey() {
        return ConexionBD.resolverValorObligatorio(null, ENV_OFFLINE_DB_KEY, ENV_OFFLINE_DB_KEY);
    }

    /**
     * Indica si {@code FOODCORE_OFFLINE_DB_KEY} se encuentra configurada y no vacía.
     */
    public static boolean isKeyConfigured() {
        try {
            requireKey();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    /**
     * Cifra un texto usando la clave configurada en {@code FOODCORE_OFFLINE_DB_KEY}.
     */
    public static String encrypt(String plaintext) {
        return encrypt(requireKey(), plaintext);
    }

    /**
     * Cifra un texto con salt de 16 bytes e IV de 12 bytes aleatorios por registro usando AES-256-GCM.
     */
    public static String encrypt(String key, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        validarClave(key);

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        SECURE_RANDOM.nextBytes(iv);

        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] derivedKey = null;
        try {
            derivedKey = derivarClave(key, salt);
            SecretKeySpec keySpec = new SecretKeySpec(derivedKey, KEY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            Base64.Encoder encoder = Base64.getEncoder();
            return VERSION_PREFIX
                    + encoder.encodeToString(salt)
                    + ":"
                    + encoder.encodeToString(iv)
                    + ":"
                    + encoder.encodeToString(cipherBytes);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar registro offline con AES-256-GCM.", ex);
        } finally {
            Arrays.fill(plainBytes, (byte) 0);
            if (derivedKey != null) {
                Arrays.fill(derivedKey, (byte) 0);
            }
        }
    }

    /**
     * Descifra un registro usando la clave configurada en {@code FOODCORE_OFFLINE_DB_KEY}.
     */
    public static String decrypt(String encryptedPayload) {
        return decrypt(requireKey(), encryptedPayload);
    }

    /**
     * Descifra un registro en formato {@code v1:<b64salt>:<b64iv>:<b64cipher>} validando etiqueta GCM.
     */
    public static String decrypt(String key, String encryptedPayload) {
        if (encryptedPayload == null) {
            return null;
        }
        validarClave(key);
        if (!encryptedPayload.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("Formato de dato offline cifrado inválido o sin prefijo v1.");
        }

        String[] parts = encryptedPayload.split(":", -1);
        if (parts.length != 4 || !VERSION_TAG.equals(parts[0])) {
            throw new IllegalStateException("Estructura de dato offline cifrado inválida.");
        }

        byte[] salt;
        byte[] iv;
        byte[] cipherBytes;
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            salt = decoder.decode(parts[1]);
            iv = decoder.decode(parts[2]);
            cipherBytes = decoder.decode(parts[3]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Codificación Base64 inválida en dato offline cifrado.", ex);
        }

        if (salt.length != SALT_BYTES || iv.length != IV_BYTES || cipherBytes.length == 0) {
            throw new IllegalStateException("Parámetros criptográficos inválidos en dato offline cifrado.");
        }

        byte[] derivedKey = null;
        byte[] plainBytes = null;
        try {
            derivedKey = derivarClave(key, salt);
            SecretKeySpec keySpec = new SecretKeySpec(derivedKey, KEY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo descifrar registro offline: clave incorrecta o integridad comprometida.", ex);
        } finally {
            if (derivedKey != null) {
                Arrays.fill(derivedKey, (byte) 0);
            }
            if (plainBytes != null) {
                Arrays.fill(plainBytes, (byte) 0);
            }
        }
    }

    private static void validarClave(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la variable de entorno/propiedad obligatoria '" + ENV_OFFLINE_DB_KEY + "'.");
        }
    }

    private static byte[] derivarClave(String key, byte[] salt) throws GeneralSecurityException {
        char[] passwordChars = key.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(passwordChars, '\0');
        }
    }
}
