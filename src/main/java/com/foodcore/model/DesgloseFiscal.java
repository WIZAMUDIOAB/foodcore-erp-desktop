package com.foodcore.model;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object inmutable para precisión matemática contable, desglose de IGV (18%)
 * e invariantes aritméticos según SPEC-FOODCORE-v1.0 (§5 y §8.1).
 * <ul>
 *   <li>Moneda: {@code setScale(2, RoundingMode.HALF_EVEN)}</li>
 *   <li>Stock de insumos: {@code setScale(4, RoundingMode.HALF_EVEN)}</li>
 *   <li>Contrato §8.1: {@code total = 55.80 -> subtotal = 47.29, igv = 8.51}</li>
 * </ul>
 */
public final class DesgloseFiscal {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    public static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");
    public static final int ESCALA_MONETARIA = 2;
    public static final int ESCALA_STOCK = 4;
    public static final RoundingMode MODO_REDONDEO = RoundingMode.HALF_EVEN;

    private final BigDecimal subtotalGravado;
    private final BigDecimal igv;
    private final BigDecimal total;

    private DesgloseFiscal(BigDecimal totalBruto) throws NegocioException {
        if (totalBruto == null) {
            throw new NegocioException(CodigoError.VALIDACION_FISCAL, "El monto total de la operación no puede ser nulo.");
        }
        BigDecimal totalNormalizado = totalBruto.setScale(ESCALA_MONETARIA, MODO_REDONDEO);
        if (totalNormalizado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException(CodigoError.VALIDACION_FISCAL, "El monto total de la venta debe ser estrictamente mayor a S/ 0.00.");
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
     * Normaliza un monto monetario a escala 2 con redondeo bancario {@link RoundingMode#HALF_EVEN} (§5).
     */
    public static BigDecimal normalizarMoneda(BigDecimal monto) throws NegocioException {
        if (monto == null) {
            throw new NegocioException(CodigoError.VALIDACION_FISCAL, "El monto monetario no puede ser nulo.");
        }
        return monto.setScale(ESCALA_MONETARIA, MODO_REDONDEO);
    }

    /**
     * Normaliza una cantidad de stock o receta a escala 4 con redondeo {@link RoundingMode#HALF_EVEN} (§5).
     */
    public static BigDecimal normalizarStock(BigDecimal cantidad) throws NegocioException {
        if (cantidad == null) {
            throw new NegocioException(CodigoError.VALIDACION_FISCAL, "La cantidad de stock no puede ser nula.");
        }
        return cantidad.setScale(ESCALA_STOCK, MODO_REDONDEO);
    }

    /**
     * Calcula el vuelto exacto validando que el monto recibido cubra la deuda en pagos en efectivo.
     */
    public BigDecimal calcularVuelto(BigDecimal montoRecibido, String metodoPago) throws NegocioException {
        if (montoRecibido == null) {
            throw new NegocioException(CodigoError.VALIDACION_FISCAL, "Debe especificar el monto recibido.");
        }
        BigDecimal recibidoNorm = montoRecibido.setScale(ESCALA_MONETARIA, MODO_REDONDEO);
        if ("EFECTIVO".equalsIgnoreCase(metodoPago) && recibidoNorm.compareTo(this.total) < 0) {
            throw new NegocioException(CodigoError.VALIDACION_FISCAL, String.format(
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
     * Valida las reglas de emisión de comprobantes (Ticket, Boleta, Factura).
     */
    public static void validarRequisitosComprobante(String tipoComprobante, String documentoCliente, String nombreCliente) throws NegocioException {
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            throw new NegocioException(CodigoError.VALIDACION_FISCAL, "El tipo de comprobante es obligatorio.");
        }
        String doc = documentoCliente != null ? documentoCliente.trim() : "";
        String nombre = nombreCliente != null ? nombreCliente.trim() : "";

        if ("FACTURA".equalsIgnoreCase(tipoComprobante)) {
            if (!doc.matches("^\\d{11}$") || (!doc.startsWith("10") && !doc.startsWith("20"))) {
                throw new NegocioException(CodigoError.VALIDACION_FISCAL,
                    "Para emitir FACTURA es obligatorio un RUC válido de 11 dígitos (iniciado en 10 o 20).");
            }
            if (nombre.isEmpty() || "CLIENTES VARIOS".equalsIgnoreCase(nombre)) {
                throw new NegocioException(CodigoError.VALIDACION_FISCAL,
                    "Para emitir FACTURA es obligatorio consignar la Razón Social del contribuyente.");
            }
        } else if ("BOLETA".equalsIgnoreCase(tipoComprobante)) {
            if (!doc.equals("00000000") && !doc.matches("^\\d{8}$")) {
                throw new NegocioException(CodigoError.VALIDACION_FISCAL,
                    "Para emitir BOLETA el DNI debe contener exactamente 8 dígitos numéricos.");
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
