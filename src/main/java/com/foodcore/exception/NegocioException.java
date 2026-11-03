package com.foodcore.exception;

/**
 * Excepción base de dominio para control de reglas de negocio en la capa de servicios.
 * Incluye catalogación tipada mediante {@link CodigoError} (SPEC-FOODCORE-v1.0 §4.2).
 */
public class NegocioException extends Exception {

    private final CodigoError codigoError;

    public NegocioException(String message) {
        this(CodigoError.REGLA_NEGOCIO_GENERAL, message);
    }

    public NegocioException(CodigoError codigoError, String message) {
        super(message);
        this.codigoError = codigoError != null ? codigoError : CodigoError.REGLA_NEGOCIO_GENERAL;
    }

    public NegocioException(String message, Throwable cause) {
        this(CodigoError.REGLA_NEGOCIO_GENERAL, message, cause);
    }

    public NegocioException(CodigoError codigoError, String message, Throwable cause) {
        super(message, cause);
        this.codigoError = codigoError != null ? codigoError : CodigoError.REGLA_NEGOCIO_GENERAL;
    }

    public CodigoError getCodigoError() {
        return codigoError;
    }
}
