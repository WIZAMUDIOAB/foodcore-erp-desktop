package com.foodcore.api;

import com.foodcore.api.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de arranque fail-fast para FOODCORE_JWT_SECRET en {@link JwtService}.
 */
class JwtServiceBootstrapTest {

    private static final String PROP_JWT_SECRET = "FOODCORE_JWT_SECRET";
    private static final String EXPECTED_MSG =
            "FOODCORE_JWT_SECRET is required and must be at least 32 bytes (UTF-8)";

    private String previousJwtSecretProp;

    @BeforeEach
    void capturePreviousProperty() {
        previousJwtSecretProp = System.getProperty(PROP_JWT_SECRET);
    }

    @AfterEach
    void restorePreviousProperty() {
        if (previousJwtSecretProp == null) {
            System.clearProperty(PROP_JWT_SECRET);
        } else {
            System.setProperty(PROP_JWT_SECRET, previousJwtSecretProp);
        }
    }

    @Test
    @DisplayName("Falla fail-fast cuando FOODCORE_JWT_SECRET está ausente o sin resolver (${...})")
    void shouldFailFastWhenJwtSecretMissing() {
        System.clearProperty(PROP_JWT_SECRET);
        if (System.getenv(PROP_JWT_SECRET) == null || System.getenv(PROP_JWT_SECRET).isBlank()) {
            IllegalStateException ex = assertThrows(IllegalStateException.class, JwtService::new);
            assertEquals(EXPECTED_MSG, ex.getMessage());
        }

        System.setProperty(PROP_JWT_SECRET, "${FOODCORE_JWT_SECRET}");
        IllegalStateException placeholderEx = assertThrows(
                IllegalStateException.class,
                JwtService::validarConfiguracionObligatoria
        );
        assertEquals(EXPECTED_MSG, placeholderEx.getMessage());
    }

    @Test
    @DisplayName("Falla fail-fast cuando FOODCORE_JWT_SECRET es blanco o menor a 32 bytes UTF-8")
    void shouldFailFastWhenJwtSecretBlankOrTooShort() {
        System.setProperty(PROP_JWT_SECRET, "   ");
        IllegalStateException blankEx = assertThrows(IllegalStateException.class, JwtService::new);
        assertEquals(EXPECTED_MSG, blankEx.getMessage());

        System.setProperty(PROP_JWT_SECRET, "secreto-corto-de-solo-16-bytes");
        IllegalStateException shortEx = assertThrows(IllegalStateException.class, JwtService::new);
        assertEquals(EXPECTED_MSG, shortEx.getMessage());

        IllegalStateException directBytesEx = assertThrows(
                IllegalStateException.class,
                () -> new JwtService("menos-de-32-bytes".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(EXPECTED_MSG, directBytesEx.getMessage());
    }

    @Test
    @DisplayName("Arranca y firma/verifica JWT cuando FOODCORE_JWT_SECRET tiene al menos 32 bytes UTF-8")
    void shouldSucceedWithValidJwtSecret() {
        System.setProperty(PROP_JWT_SECRET, "FoodCore-Bootstrap-JWT-Secret-2026-Valid-32Plus-Bytes");
        assertDoesNotThrow(JwtService::validarConfiguracionObligatoria);

        JwtService jwtService = new JwtService();
        String token = jwtService.generarToken(1, "admin", "ADMINISTRADOR");
        Optional<JwtService.JwtClaims> claims = jwtService.verificarToken(token);

        assertTrue(claims.isPresent());
        assertEquals(1, claims.get().idUsuario());
        assertEquals("admin", claims.get().username());
        assertEquals("ADMIN", claims.get().rol());
    }
}
