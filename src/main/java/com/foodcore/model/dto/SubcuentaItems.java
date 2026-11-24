package com.foodcore.model.dto;

import com.foodcore.model.DetalleVenta;
import java.util.List;

/** Agrupación de ítems y medio de pago para dividir una cuenta en múltiples comprobantes. */
public record SubcuentaItems(List<DetalleVenta> detalles, String medioPago) {
    public SubcuentaItems {
        detalles = detalles != null ? List.copyOf(detalles) : List.of();
    }
}
