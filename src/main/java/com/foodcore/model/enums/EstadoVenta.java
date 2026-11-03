package com.foodcore.model.enums;

/**
 * Máquina de estados de una Venta (SPEC-FOODCORE-v1.0 §6.1).
 * REGISTRADA -> ANULADA (con permiso VENTA_ANULAR y turno abierto).
 */
public enum EstadoVenta {
    REGISTRADA,
    ANULADA;

    public boolean puedeTransicionarA(EstadoVenta destino) {
        if (this == REGISTRADA && destino == ANULADA) {
            return true;
        }
        return false;
    }
}
