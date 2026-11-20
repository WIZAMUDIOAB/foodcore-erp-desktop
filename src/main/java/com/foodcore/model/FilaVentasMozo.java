package com.foodcore.model;

/**
 * Fila de ventas agregadas por mozo / operador para el Dashboard gerencial (v3.0.3 §5).
 *
 * @param usuario       Usuario con su rol, p. ej. "jgarcia (MOZO)".
 * @param ventasTotales Total facturado formateado (S/ 0.00).
 * @param nroPedidos    Cantidad de pedidos completados.
 * @param ticketPromedio Ticket promedio formateado (S/ 0.00).
 */
public record FilaVentasMozo(
        String usuario,
        String ventasTotales,
        String nroPedidos,
        String ticketPromedio) {
}
