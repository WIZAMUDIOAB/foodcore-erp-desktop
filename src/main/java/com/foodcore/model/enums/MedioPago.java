package com.foodcore.model.enums;

/**
 * Medios de pago habilitados en POS (SPEC-FOODCORE-v1.0 §5, §13.2).
 */
public enum MedioPago {
    EFECTIVO,
    TARJETA,
    YAPE,
    PLIN,
    YAPE_PLIN;

    public boolean afectaArqueoEfectivoFisico() {
        return this == EFECTIVO;
    }
}
