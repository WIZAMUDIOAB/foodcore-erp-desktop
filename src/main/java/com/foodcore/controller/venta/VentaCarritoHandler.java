package com.foodcore.controller.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.PromocionService;
import java.math.BigDecimal;
import java.util.List;

public interface VentaCarritoHandler {
    record ResumenTotalesVenta(BigDecimal totalBruto, BigDecimal descuentoActivo, BigDecimal basePreCanje,
                               BigDecimal subtotalGravado, BigDecimal igv, BigDecimal descuentoPuntosAjustado,
                               BigDecimal totalNeto, boolean hayDescuento, boolean hayDescuentoPuntos) { }

    @FunctionalInterface
    interface EvaluadorPromociones {
        PromocionService.ResultadoEvaluacion evaluar(List<DetalleVenta> items, BigDecimal totalBruto);
    }

    Producto resolverOpcionesProducto(PersonalizacionProductoService service, Producto p);
    boolean agregarItemLocal(List<DetalleVenta> items, Producto p, Integer idVariante, String nombreVariante,
                             List<ProductoModificador> mods, String modsTexto, int qty, String nota,
                             BigDecimal precioUnitario, BigDecimal subtotal);
    void incrementarItem(DetalleVenta dv);
    boolean decrementarOEliminar(List<DetalleVenta> items, DetalleVenta sel);
    BigDecimal calcularSumaBruta(List<DetalleVenta> items);
    BigDecimal obtenerBasePreCanje(List<DetalleVenta> items, BigDecimal descuentoActivo);
    BigDecimal obtenerTotalNeto(List<DetalleVenta> items, BigDecimal descuentoActivo, BigDecimal descuentoPuntosActivo);
    ResumenTotalesVenta calcularTotales(List<DetalleVenta> items, BigDecimal descuentoManual,
                                        boolean descuentoManualFijado, BigDecimal descuentoPuntosActivo)
            throws NegocioException;
    BigDecimal calcularDescuentoAutorizado(List<DetalleVenta> items, BigDecimal valor, boolean esPorcentaje,
                                           SesionUsuario sesion) throws NegocioException;
}
