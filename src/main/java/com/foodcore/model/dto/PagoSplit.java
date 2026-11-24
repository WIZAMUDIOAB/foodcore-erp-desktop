package com.foodcore.model.dto;

import java.math.BigDecimal;

/** Fracción de pago por medio en ventas divididas o de pago mixto. */
public record PagoSplit(String medioPago, BigDecimal monto) {
}
