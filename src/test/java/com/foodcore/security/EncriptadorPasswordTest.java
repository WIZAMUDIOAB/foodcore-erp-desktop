package com.foodcore.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de seguridad criptográfica BCrypt (costo 12) según SPEC-FOODCORE-v1.0 (§10.1, §15.1).
 */
class EncriptadorPasswordTest {

    @Test
    @DisplayName("Debe generar hash BCrypt costo 12 ($2a$12$) y verificar contraseña correcta e incorrecta")
    void debeGenerarYVerificarHashBcryptCosto12() {
        String passwordPlano = "FoodCore2026!";
        String hash = EncriptadorPassword.generarHash(passwordPlano);

        assertNotNull(hash);
        assertNotEquals(passwordPlano, hash);
        assertEquals(60, hash.length());
        assertTrue(hash.startsWith("$2a$12$"), "El hash debe utilizar factor de costo explícito 12: " + hash);
        assertFalse(EncriptadorPassword.needsUpgrade(hash), "Hash coste 12 no requiere actualización");

        assertTrue(EncriptadorPassword.verificarPassword(passwordPlano, hash));
        assertFalse(EncriptadorPassword.verificarPassword("PasswordIncorrecto1", hash));
    }

    @Test
    @DisplayName("needsUpgrade debe detectar hashes de coste inferior (< 12) y rechazar entradas inválidas")
    void debeDetectarNecesidadUpgradeParaHashesDeCosteInferior() {
        String hashCoste10 = EncriptadorPassword.generarHashConCoste("FoodCoreLegacy2026!", 10);
        assertTrue(hashCoste10.startsWith("$2a$10$"));
        assertTrue(EncriptadorPassword.verificarPassword("FoodCoreLegacy2026!", hashCoste10));
        assertTrue(EncriptadorPassword.needsUpgrade(hashCoste10), "Hash de coste 10 debe requerir upgrade a coste 12");

        String hashCoste12 = EncriptadorPassword.generarHash("FoodCore2026!");
        assertFalse(EncriptadorPassword.needsUpgrade(hashCoste12));

        assertFalse(EncriptadorPassword.needsUpgrade(null));
        assertFalse(EncriptadorPassword.needsUpgrade(""));
        assertFalse(EncriptadorPassword.needsUpgrade("no-es-bcrypt"));
        assertFalse(EncriptadorPassword.needsUpgrade("$2a$XX$longitud_exacta_sesenta_caracteres_invalido_123456789012345"));
    }

    @Test
    @DisplayName("Contrato §10.1 / §15.1: verificarPassword debe manejar null, vacío y hash inválido devolviendo false")
    void debeRetornarFalseAnteEntradasNulasVaciasOHashesCorruptos() {
        String hashValido = EncriptadorPassword.generarHash("AdminSecret123");

        assertFalse(EncriptadorPassword.verificarPassword(null, hashValido));
        assertFalse(EncriptadorPassword.verificarPassword("", hashValido));
        assertFalse(EncriptadorPassword.verificarPassword("AdminSecret123", null));
        assertFalse(EncriptadorPassword.verificarPassword("AdminSecret123", ""));
        assertFalse(EncriptadorPassword.verificarPassword("AdminSecret123", "   "));
        assertFalse(EncriptadorPassword.verificarPassword("AdminSecret123", "hash_texto_plano_invalido"));
        assertFalse(EncriptadorPassword.verificarPassword("AdminSecret123", "$2a$12$corto"));
        assertFalse(EncriptadorPassword.verificarPassword(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Corto1", "sinmayusculas123", "SINMINUSCULAS123", "SinDigitosValidos"})
    @DisplayName("Debe rechazar contraseñas que incumplan la política de complejidad (§10.1)")
    void debeValidarPoliticaDeComplejidad(String passwordDebil) {
        assertFalse(EncriptadorPassword.cumplePoliticaComplejidad(passwordDebil));
        assertTrue(EncriptadorPassword.cumplePoliticaComplejidad("ClaveSegura2026"));
    }
}
