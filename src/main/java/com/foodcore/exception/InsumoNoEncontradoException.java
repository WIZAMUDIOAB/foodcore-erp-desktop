package com.foodcore.exception;

/**
 * Excepción lanzada cuando un insumo referenciado en una receta o movimiento de Kárdex no existe
 * (SPEC-FOODCORE-v1.0 §9.1).
 */
public class InsumoNoEncontradoException extends NegocioException {

    private final int idInsumo;

    public InsumoNoEncontradoException(int idInsumo) {
        super(CodigoError.INSUMO_NO_ENCONTRADO, "El insumo con ID #" + idInsumo + " no existe en el catálogo de almacén.");
        this.idInsumo = idInsumo;
    }

    public int getIdInsumo() {
        return idInsumo;
    }
}
