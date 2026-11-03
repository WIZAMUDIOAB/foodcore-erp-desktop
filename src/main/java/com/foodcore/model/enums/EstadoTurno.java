package com.foodcore.model.enums;

/**
 * Máquina de estados de un Turno de Caja (SPEC-FOODCORE-v1.0 §6.4).
 * ABIERTO -> CERRADO.
 */
public enum EstadoTurno {
    ABIERTO,
    ABIERTA,
    CERRADO,
    CERRADA
}
