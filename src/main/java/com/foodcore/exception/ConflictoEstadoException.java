package com.foodcore.exception;

/**
 * Excepción lanzada ante conflictos de bloqueo optimista (version) o transiciones inválidas
 * en las máquinas de estado de Pedido, Mesa, Venta o Turno (SPEC-FOODCORE-v1.0 §6, §8.4).
 */
public class ConflictoEstadoException extends NegocioException {

    public ConflictoEstadoException(String message) {
        super(CodigoError.CONFLICTO_ESTADO, message);
    }
}
