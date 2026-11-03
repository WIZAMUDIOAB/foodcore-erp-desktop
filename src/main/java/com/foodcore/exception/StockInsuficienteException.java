package com.foodcore.exception;

import java.math.BigDecimal;

/**
 * Excepción lanzada cuando un descuento de inventario viola la restricción de stock no negativo
 * (SPEC-FOODCORE-v1.0 §9.1).
 */
public class StockInsuficienteException extends NegocioException {

    private final int idInsumo;
    private final BigDecimal cantidadRequerida;

    public StockInsuficienteException(int idInsumo, BigDecimal cantidadRequerida) {
        super(
            CodigoError.STOCK_INSUFICIENTE,
            String.format("Stock insuficiente para el insumo #%d (requerido: %s).", idInsumo, cantidadRequerida)
        );
        this.idInsumo = idInsumo;
        this.cantidadRequerida = cantidadRequerida;
    }

    public StockInsuficienteException(String message) {
        super(CodigoError.STOCK_INSUFICIENTE, message);
        this.idInsumo = -1;
        this.cantidadRequerida = BigDecimal.ZERO;
    }

    public int getIdInsumo() {
        return idInsumo;
    }

    public BigDecimal getCantidadRequerida() {
        return cantidadRequerida;
    }
}
