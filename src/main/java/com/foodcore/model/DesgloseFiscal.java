package com.foodcore.model;

import com.foodcore.exception.NegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object inmutable para precisión matemática contable, desglose de IGV (18%)
 * y validación de invariantes fiscales según normativa SUNAT.
 */
public final class DesgloseFiscal {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    public static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");
    public static final int ESCALA_MONETARIA = 2;
    public static final RoundingMode MODO_REDONDEO = RoundingMode.HALF_UP;

    private final BigDecimal subtotalGravado;
    private final BigDecimal igv;
    private final BigDecimal total;

    private DesgloseFiscal(BigDecimal totalBruto) throws NegocioException {
        if (totalBruto == null) {
            throw new NegocioException("El monto total de la operación no puede ser nulo.");
        }
        BigDecimal totalNormalizado = totalBruto.setScale(ESCALA_MONETARIA, MODO_REDONDEO);
        if (totalNormalizado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El monto total de la venta debe ser estrictamente mayor a S/ 0.00.");
        }

        this.total = totalNormalizado;
        this.subtotalGravado = this.total.divide(FACTOR_IGV, ESCALA_MONETARIA, MODO_REDONDEO);
        // Invariante contable estricto: Subtotal + IGV == Total (evita pérdida de céntimos por redondeo periódico)
        this.igv = this.total.subtract(this.subtotalGravado).setScale(ESCALA_MONETARIA, MODO_REDONDEO);
    }

    public static DesgloseFiscal desdeTotalConImpuestos(BigDecimal totalConIgv) throws NegocioException {
        return new DesgloseFiscal(totalConIgv);
    }

    /**
     * Calcula el vuelto exacto validando que el monto recibido cubra la deuda en pagos en efectivo.
     */
    public BigDecimal calcularVuelto(BigDecimal montoRecibido, String metodoPago) throws NegocioException {
        if (montoRecibido == null) {
            throw new NegocioException("Debe especificar el monto recibido.");
        }
        BigDecimal recibidoNorm = montoRecibido.setScale(ESCALA_MONETARIA, MODO_REDONDEO);
        if ("EFECTIVO".equalsIgnoreCase(metodoPago) && recibidoNorm.compareTo(this.total) < 0) {
            throw new NegocioException(String.format(
                "Monto recibido insuficiente: S/ %s es menor al total exigible de S/ %s.",
                recibidoNorm, this.total
            ));
        }
        if (!"EFECTIVO".equalsIgnoreCase(metodoPago)) {
            return BigDecimal.ZERO.setScale(ESCALA_MONETARIA, MODO_REDONDEO);
        }
        return recibidoNorm.subtract(this.total).setScale(ESCALA_MONETARIA, MODO_REDONDEO);
    }

    /**
     * Valida las reglas fiscales de emisión de comprobantes (Ticket, Boleta, Factura).
     */
    public static void validarRequisitosComprobante(String tipoComprobante, String documentoCliente, String nombreCliente) throws NegocioException {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            throw new NegocioException("El tipo de comprobante es obligatorio.");
        }
        String doc = documentoCliente != null ? documentoCliente.trim() : "";
        String nombre = nombreCliente != null ? nombreCliente.trim() : "";

        if ("FACTURA".equalsIgnoreCase(tipoComprobante)) {
            if (!doc.matches("^\\d{11}$") || (!doc.startsWith("10") && !doc.startsWith("20"))) {
                throw new NegocioException("Para emitir FACTURA es obligatorio un RUC válido de 11 dígitos (iniciado en 10 o 20).");
            }
            if (nombre.isEmpty() || "CLIENTES VARIOS".equalsIgnoreCase(nombre)) {
                throw new NegocioException("Para emitir FACTURA es obligatorio consignar la Razón Social del contribuyente.");
            }
        } else if ("BOLETA".equalsIgnoreCase(tipoComprobante)) {
            if (!doc.equals("00000000") && !doc.matches("^\\d{8}$")) {
                throw new NegocioException("Para emitir BOLETA el DNI debe contener exactamente 8 dígitos numéricos.");
            }
        }
    }

    public BigDecimal getSubtotalGravado() {
        return subtotalGravado;
    }

    public BigDecimal getIgv() {
        return igv;
    }

    public BigDecimal getTotal() {
        return total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DesgloseFiscal that)) return false;
        return subtotalGravado.compareTo(that.subtotalGravado) == 0
            && igv.compareTo(that.igv) == 0
            && total.compareTo(that.total) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subtotalGravado.stripTrailingZeros(), igv.stripTrailingZeros(), total.stripTrailingZeros());
    }
}
