package com.foodcore.service.api;

/**
 * Excepción de aplicación con código HTTP asociado para operaciones REST.
 */
public class ApiOperacionException extends Exception {

    private final int statusCode;

    public ApiOperacionException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApiOperacionException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
