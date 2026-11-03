package com.foodcore.model.enums;

/**
 * Máquina de estados de una Mesa de salón (SPEC-FOODCORE-v1.0 §6.3).
 * LIBRE -> OCUPADA -> POR_COBRAR -> LIBRE.
 */
public enum EstadoMesa {
    LIBRE,
    OCUPADA,
    POR_COBRAR
}
