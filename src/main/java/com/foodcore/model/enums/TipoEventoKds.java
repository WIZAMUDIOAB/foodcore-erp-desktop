package com.foodcore.model.enums;

/**
 * Eventos definidos en el protocolo de red KDS v1 (SPEC-FOODCORE-v1.0 §11).
 */
public enum TipoEventoKds {
    COMANDA_NUEVA,
    ITEMS_ADICIONALES,
    ITEM_EN_PREPARACION,
    ITEM_LISTO,
    ITEM_ENTREGADO,
    COMANDA_CANCELADA
}
