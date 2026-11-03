package com.foodcore.exception;

/**
 * Excepción de dominio no verificada lanzada cuando un insumo referenciado en una receta
 * o movimiento de Kárdex no existe (SPEC-FOODCORE-v1.0 §9.1; REMEDIATION R-06, R-11).
 */
public class InsumoNoEncontradoException extends RuntimeException {

    private final CodigoError codigoError = CodigoError.INSUMO_NO_ENCONTRADO;
    private final long idInsumo;

    public InsumoNoEncontradoException(long idInsumo) {
        super("El insumo con ID #" + idInsumo + " no existe en el catálogo de almacén.");
        this.idInsumo = idInsumo;
    }

    public CodigoError getCodigoError() {
        return codigoError;
    }

    public long getIdInsumo() {
        return idInsumo;
    }
}
