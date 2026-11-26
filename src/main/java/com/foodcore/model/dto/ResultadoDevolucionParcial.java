package com.foodcore.model.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resultado de una devolución parcial de ítems en venta existente (v5.2.0). */
public record ResultadoDevolucionParcial(
        int idVenta,
        List<Integer> idsDetalleDevueltos,
        BigDecimal montoReembolso,
        BigDecimal nuevoTotalVenta,
        String nuevoEstadoVenta,
        int cantidadInsumosRevertidos
) {
    public ResultadoDevolucionParcial {
        idsDetalleDevueltos = idsDetalleDevueltos != null ? List.copyOf(idsDetalleDevueltos) : List.of();
    }
}
