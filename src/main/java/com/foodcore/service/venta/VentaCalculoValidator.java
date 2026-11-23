package com.foodcore.service.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.VentaService;

import java.math.BigDecimal;
import java.util.List;

public interface VentaCalculoValidator {
    BigDecimal calcularDescuentoAutorizado(BigDecimal totalBruto, BigDecimal valorDescuento,
                                            boolean esPorcentaje, SesionUsuario sesion) throws NegocioException;

    void validarYPrepararMontosVenta(Venta venta, List<DetalleVenta> detalles,
                                     List<VentaService.PagoSplit> pagosDivididos) throws NegocioException;

    String normalizarMedioPagoMovimientoCaja(String medioPago);
}
