package com.foodcore.dao;

import java.util.Collections;
import java.util.Map;

/**
 * Utilidad centralizada para construcción segura de cláusulas SQL parametrizadas y
 * validación estricta por lista blanca de identificadores (columnas/ordenamiento).
 */
public final class SqlQueryBuilder {

    public static final int MAX_IN_PLACEHOLDERS = 1000;

    private SqlQueryBuilder() {
    }

    /**
     * Genera exactamente {@code count} marcadores {@code ?} separados por coma para cláusulas {@code IN (...)}.
     * Nunca interpola valores externos dentro de la sentencia SQL.
     */
    public static String buildInPlaceholders(int count) {
        if (count < 1 || count > MAX_IN_PLACEHOLDERS) {
            throw new IllegalArgumentException(
                    "Cantidad de marcadores IN fuera de rango permitido (1.." + MAX_IN_PLACEHOLDERS + "): " + count);
        }
        return String.join(",", Collections.nCopies(count, "?"));
    }

    /**
     * Resuelve un identificador SQL (por ejemplo columna de ordenamiento) contra una whitelist estricta.
     * Lanza {@link IllegalArgumentException} si la clave solicitada no está autorizada explícitamente.
     */
    public static String resolveWhitelistedIdentifier(String requestedKey, Map<String, String> whitelist) {
        if (requestedKey == null || whitelist == null) {
            throw new IllegalArgumentException("Clave o whitelist SQL nula no permitida.");
        }
        String normalized = requestedKey.trim();
        String safeColumn = whitelist.get(normalized);
        if (safeColumn == null) {
            throw new IllegalArgumentException("Identificador SQL no autorizado: '" + requestedKey + "'");
        }
        return safeColumn;
    }
}
