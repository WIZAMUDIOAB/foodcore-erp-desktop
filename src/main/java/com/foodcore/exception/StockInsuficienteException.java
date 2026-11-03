package com.foodcore.exception;

import java.math.BigDecimal;

/**
 * Excepción de dominio no verificada lanzada cuando un descuento de inventario viola
 * la restricción de stock no negativo (SPEC-FOODCORE-v1.0 §9.1; REMEDIATION R-06, R-11).
 */
public class StockInsuficienteException extends RuntimeException {

    private final CodigoError codigoError = CodigoError.STOCK_INSUFICIENTE;
    private final long idInsumo;
    private final BigDecimal cantidadRequerida;

    public StockInsuficienteException(long idInsumo, BigDecimal cantidadRequerida) {
        super(String.format("Stock insuficiente para el insumo #%d (requerido: %s).", idInsumo, cantidadRequerida));
        this.idInsumo = idInsumo;
        this.cantidadRequerida = cantidadRequerida;
    }

    public StockInsuficienteException(String message) {
        super(message);
        this.idInsumo = -1L;
        this.cantidadRequerida = BigDecimal.ZERO;
    }

    public CodigoError getCodigoError() {
        return codigoError;
    }

    public long getIdInsumo() {
        return idInsumo;
    }

    public BigDecimal getCantidadRequerida() {
        return cantidadRequerida;
    }
}
