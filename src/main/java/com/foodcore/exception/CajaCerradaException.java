package com.foodcore.exception;

/**
 * Excepción lanzada cuando se intenta cobrar o registrar movimientos sin un turno de caja abierto
 * (SPEC-FOODCORE-v1.0 §6.4).
 */
public class CajaCerradaException extends NegocioException {

    public CajaCerradaException() {
        super(CodigoError.CAJA_CERRADA, "Operación bloqueada: No existe un turno de caja 'ABIERTA'. Debe abrir caja antes de cobrar.");
    }

    public CajaCerradaException(String message) {
        super(CodigoError.CAJA_CERRADA, message);
    }
}
