package com.foodcore.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;

/**
 * Servicio criptográfico de hashing y verificación de contraseñas y secretos de terminal
 * usando BCrypt con factor de costo explícito 12 (at.favre.lib:bcrypt).
 */
public final class EncriptadorPassword {

    public static final int BCRYPT_COST = 12;

    public EncriptadorPassword() {
    }

    public String hashear(String passwordPlano) {
        return generarHash(passwordPlano);
    }

    public boolean verificar(String passwordPlano, String hashAlmacenado) {
        return verificarPassword(passwordPlano, hashAlmacenado);
    }

    /**
     * Genera un hash BCrypt con coste explícito {@link #BCRYPT_COST}.
     */
    public static String generarHash(String passwordPlano) {
        if (passwordPlano == null || passwordPlano.isBlank()) {
            throw new IllegalArgumentException("La contraseña a encriptar no puede ser nula ni vacía.");
        }
        return BCrypt.with(BCrypt.Version.VERSION_2A)
                .hashToString(BCRYPT_COST, passwordPlano.toCharArray());
    }

    /**
     * Genera un hash BCrypt con un factor de coste explícito (uso de soporte/migración).
     */
    public static String generarHashConCoste(String passwordPlano, int cost) {
        if (passwordPlano == null || passwordPlano.isBlank()) {
            throw new IllegalArgumentException("La contraseña a encriptar no puede ser nula ni vacía.");
        }
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("El coste BCrypt debe estar entre 4 y 31.");
        }
        return BCrypt.with(BCrypt.Version.VERSION_2A)
                .hashToString(cost, passwordPlano.toCharArray());
    }

    /**
     * Indica si un hash BCrypt almacenado utiliza un coste inferior a {@link #BCRYPT_COST}.
     */
    public static boolean needsUpgrade(String storedHash) {
        if (storedHash == null) {
            return false;
        }
        String hash = storedHash.trim();
        if (!esFormatoBcryptValido(hash)) {
            return false;
        }
        try {
            int cost = Integer.parseInt(hash.substring(4, 6));
            return cost < BCRYPT_COST;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Verifica una contraseña en texto plano contra un hash BCrypt almacenado.
     * Maneja de forma segura {@code null}, cadenas vacías y hashes malformados o no-BCrypt
     * retornando {@code false} sin propagar excepciones.
     */
    public static boolean verificarPassword(String passwordPlano, String hashAlmacenado) {
        if (passwordPlano == null || passwordPlano.isEmpty()) {
            return false;
        }
        if (hashAlmacenado == null || hashAlmacenado.trim().isEmpty()) {
            return false;
        }
        String hash = hashAlmacenado.trim();
        if (!esFormatoBcryptValido(hash)) {
            return false;
        }
        try {
            BCrypt.Result result = BCrypt.verifyer()
                    .verify(passwordPlano.getBytes(StandardCharsets.UTF_8),
                            hash.getBytes(StandardCharsets.UTF_8));
            return result.verified;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean esFormatoBcryptValido(String hash) {
        if (hash.length() != 60) {
            return false;
        }
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$");
    }

    /**
     * Valida la política de complejidad de contraseñas (§10.1):
     * mínimo 8 caracteres, al menos una mayúscula, una minúscula y un dígito.
     */
    public static boolean cumplePoliticaComplejidad(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean tieneMayus = false;
        boolean tieneMinus = false;
        boolean tieneDigito = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                tieneMayus = true;
            } else if (Character.isLowerCase(c)) {
                tieneMinus = true;
            } else if (Character.isDigit(c)) {
                tieneDigito = true;
            }
        }
        return tieneMayus && tieneMinus && tieneDigito;
    }
}
