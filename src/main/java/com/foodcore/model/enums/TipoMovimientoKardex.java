package com.foodcore.model.enums;

/**
 * Tipos de movimiento en el libro inmutable de Kárdex (SPEC-FOODCORE-v1.0 §5, §8.2).
 */
public enum TipoMovimientoKardex {
    ENTRADA(1),
    SALIDA(-1),
    AJUSTE(0),
    MERMA(-1),
    DEVOLUCION(1);

    private final int signoEsperado;

    TipoMovimientoKardex(int signoEsperado) {
        this.signoEsperado = signoEsperado;
    }

    public int getSignoEsperado() {
        return signoEsperado;
    }
}
