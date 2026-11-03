package com.foodcore.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Servicio criptográfico de hashing y verificación de contraseñas y secretos de terminal
 * usando BCrypt con factor de costo 12 (SPEC-FOODCORE-v1.0 §3.1, §10.1, §15.1).
 */
public final class EncriptadorPassword {

    public static final int BCRYPT_COST = 12;

    private EncriptadorPassword() {
    }

    /**
     * Genera un hash BCrypt con costo 12.
     */
    public static String generarHash(String passwordPlano) {
        if (passwordPlano == null || passwordPlano.isBlank()) {
            throw new IllegalArgumentException("La contraseña a encriptar no puede ser nula ni vacía.");
        }
        return BCrypt.hashpw(passwordPlano, BCrypt.gensalt(BCRYPT_COST));
    }

    /**
     * Verifica una contraseña en texto plano contra un hash BCrypt almacenado.
     * Maneja de forma segura {@code null}, cadenas vacías y hashes malformados o no-BCrypt
     * retornando {@code false} sin propagar excepciones (§10.1, §15.1).
     */
    public static boolean verificarPassword(String passwordPlano, String hashAlmacenado) {
        if (passwordPlano == null || passwordPlano.isEmpty()) {
            return false;
        }
        if (hashAlmacenado == null || hashAlmacenado.trim().isEmpty()) {
            return false;
        }
        String hash = hashAlmacenado.trim();
        if (!hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$")) {
            return false;
        }
        if (hash.length() != 60) {
            return false;
        }
        try {
            return BCrypt.checkpw(passwordPlano, hash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
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
            if (Character.isUpperCase(c)) tieneMayus = true;
            else if (Character.isLowerCase(c)) tieneMinus = true;
            else if (Character.isDigit(c)) tieneDigito = true;
        }
        return tieneMayus && tieneMinus && tieneDigito;
    }
}
