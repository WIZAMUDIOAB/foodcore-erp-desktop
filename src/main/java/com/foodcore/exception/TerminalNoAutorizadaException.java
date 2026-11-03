package com.foodcore.exception;

/**
 * Excepción lanzada cuando falla el handshake de una terminal KDS/POS o la firma HMAC-SHA256 es inválida
 * (SPEC-FOODCORE-v1.0 §10.4, §11).
 */
public class TerminalNoAutorizadaException extends NegocioException {

    public TerminalNoAutorizadaException(String message) {
        super(CodigoError.TERMINAL_NO_AUTORIZADA, message);
    }
}
