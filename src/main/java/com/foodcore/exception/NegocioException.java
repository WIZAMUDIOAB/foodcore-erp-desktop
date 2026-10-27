package com.foodcore.exception;

/**
 * Excepción personalizada para control de reglas de negocio en la capa de servicios.
 */
public class NegocioException extends Exception {

    public NegocioException(String message) {
        super(message);
    }

    public NegocioException(String message, Throwable cause) {
        super(message, cause);
    }
}
