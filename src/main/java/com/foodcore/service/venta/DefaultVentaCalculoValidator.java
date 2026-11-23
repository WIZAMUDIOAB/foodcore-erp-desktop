package com.foodcore.service.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.VentaService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DefaultVentaCalculoValidator implements VentaCalculoValidator {
    private final AutorizacionService autorizacionService;
    private final FidelizacionService fidelizacionService;

    public DefaultVentaCalculoValidator(AutorizacionService autorizacionService,
                                        FidelizacionService fidelizacionService) {
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
        this.fidelizacionService = Objects.requireNonNull(fidelizacionService);
    }

    @Override
    public BigDecimal calcularDescuentoAutorizado(BigDecimal totalBruto,
                                                  BigDecimal valorDescuento,
                                                  boolean esPorcentaje,
                                                  SesionUsuario sesion) throws NegocioException {
        autorizacionService.exigir(sesion, Permiso.VENTA_DESCUENTO);
        if (totalBruto == null || totalBruto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El total de la venta debe ser mayor a cero para aplicar descuento.");
        }
        if (valorDescuento == null || valorDescuento.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El valor del descuento debe ser mayor a cero.");
        }
        BigDecimal montoDescuento;
        if (esPorcentaje) {
            if (valorDescuento.compareTo(new BigDecimal("100")) >= 0) {
                throw new NegocioException("El porcentaje de descuento debe ser menor al 100%.");
            }
            montoDescuento = totalBruto.multiply(valorDescuento)
                    .divide(new BigDecimal("100"), DesgloseFiscal.ESCALA_MONETARIA, RoundingMode.HALF_EVEN);
        } else {
            if (valorDescuento.compareTo(totalBruto) >= 0) {
                throw new NegocioException("El descuento fijo no puede igualar ni superar el total de la venta.");
            }
            montoDescuento = valorDescuento.setScale(DesgloseFiscal.ESCALA_MONETARIA, RoundingMode.HALF_EVEN);
        }
        return montoDescuento;
    }

    @Override
    public void validarYPrepararMontosVenta(Venta venta, List<DetalleVenta> detalles,
                                           List<VentaService.PagoSplit> pagosDivididos) throws NegocioException {
        if (venta == null) {
            throw new NegocioException("Los datos de la venta no pueden ser nulos.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("No se puede registrar una venta sin productos en el detalle.");
        }
        BigDecimal sumaLineas = BigDecimal.ZERO;
        for (DetalleVenta det : detalles) {
            if (det == null || det.getCantidad() <= 0) {
                throw new NegocioException("La cantidad de cada ítem de venta debe ser mayor a cero.");
            }
            if (det.getPrecioUnitario() == null || det.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                throw new NegocioException("El precio unitario de cada ítem de venta debe ser mayor a cero.");
            }
            BigDecimal subtotalLinea = det.getPrecioUnitario()
                    .multiply(BigDecimal.valueOf(det.getCantidad()))
                    .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
            det.setSubtotal(subtotalLinea);
            sumaLineas = sumaLineas.add(subtotalLinea);
        }

        BigDecimal descuentoAplicado = venta.getDescuento() != null
                ? venta.getDescuento().setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO)
                : BigDecimal.ZERO;
        if (descuentoAplicado.compareTo(BigDecimal.ZERO) < 0 || descuentoAplicado.compareTo(sumaLineas) >= 0) {
            throw new NegocioException("El descuento aplicado es inválido respecto al total de la venta.");
        }
        BigDecimal totalTrasDescuentoComercial = sumaLineas.subtract(descuentoAplicado);
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(totalTrasDescuentoComercial);

        int puntosSolicitados = venta.getPuntosACanjear();
        BigDecimal descuentoPuntosCaller = venta.getDescuentoPuntos() != null
                ? venta.getDescuentoPuntos().setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO)
                : BigDecimal.ZERO;
        if (puntosSolicitados < 0) {
            throw new NegocioException("Los puntos a canjear no pueden ser negativos.");
        }
        if (puntosSolicitados == 0 && descuentoPuntosCaller.compareTo(BigDecimal.ZERO) > 0) {
            throw new NegocioException(
                    "Descuento por puntos rechazado: no se puede aplicar descuento de fidelización con 0 puntos a canjear.");
        }
        BigDecimal descuentoPuntos;
        if (puntosSolicitados > 0) {
            if (venta.getIdCliente() == null || venta.getIdCliente() <= 0 || !venta.isAplicarFidelizacion()) {
                throw new NegocioException(
                        "El canje de puntos requiere un cliente válido con fidelización habilitada.");
            }
            descuentoPuntos = fidelizacionService.calcularDescuentoExactoParaCanjeVenta(
                    puntosSolicitados, totalTrasDescuentoComercial);
        } else {
            descuentoPuntos = BigDecimal.ZERO.setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
        }

        BigDecimal totalFinal = totalTrasDescuentoComercial.subtract(descuentoPuntos)
                .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
        if (totalFinal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El total final de la venta debe ser estrictamente mayor a cero.");
        }

        venta.setSubtotal(desglose.getSubtotalGravado());
        venta.setIgv(desglose.getIgv());
        venta.setDescuento(descuentoAplicado);
        venta.setDescuentoPuntos(descuentoPuntos);
        venta.setTotal(totalFinal);

        if (pagosDivididos != null && !pagosDivididos.isEmpty()) {
            BigDecimal sumaPagos = BigDecimal.ZERO;
            for (VentaService.PagoSplit p : pagosDivididos) {
                if (p == null || p.monto() == null || p.monto().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new NegocioException("Cada pago en cuenta dividida debe tener un monto estrictamente positivo.");
                }
                sumaPagos = sumaPagos.add(p.monto());
            }
            if (sumaPagos.setScale(2, RoundingMode.HALF_EVEN).compareTo(totalFinal) != 0) {
                throw new NegocioException("La suma de los pagos divididos (S/ " + sumaPagos
                        + ") no coincide con el total de la venta (S/ " + totalFinal + ").");
            }
        }
    }

    @Override
    public String normalizarMedioPagoMovimientoCaja(String medioPago) {
        if (medioPago == null || medioPago.isBlank()) {
            return "EFECTIVO";
        }
        String limpio = medioPago.trim().toUpperCase(Locale.ROOT);
        if ("CREDITO_CORPORATIVO".equals(limpio)) {
            return "CREDITO";
        }
        return limpio.length() > 15 ? limpio.substring(0, 15) : limpio;
    }
}
