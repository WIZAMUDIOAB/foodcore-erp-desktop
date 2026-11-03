package com.foodcore.model.enums;

/**
 * Máquina de estados de un Pedido / Comanda de salón (SPEC-FOODCORE-v1.0 §6.2).
 * ABIERTO/REGISTRADO -> EN_PREPARACION -> LISTO/ENTREGADO -> POR_COBRAR -> CERRADO/COBRADO | ANULADO.
 */
public enum EstadoPedido {
    ABIERTO,
    REGISTRADO,
    EN_PREPARACION,
    LISTO,
    ENTREGADO,
    POR_COBRAR,
    CERRADO,
    COBRADO,
    ANULADO;

    public boolean admiteNuevosItemsDirectos() {
        return this == ABIERTO || this == REGISTRADO || this == EN_PREPARACION;
    }

    public boolean esInmutable() {
        return this == CERRADO || this == COBRADO || this == ANULADO;
    }
}
