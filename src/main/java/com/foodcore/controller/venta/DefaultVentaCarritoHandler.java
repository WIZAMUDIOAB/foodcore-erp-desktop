package com.foodcore.controller.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.VentaService;
import com.foodcore.util.FormatoPersonalizacionUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultVentaCarritoHandler implements VentaCarritoHandler {
    private static final BigDecimal CERO = BigDecimal.ZERO;
    private final EvaluadorPromociones evaluadorPromociones;
    private final VentaService ventaService;

    public DefaultVentaCarritoHandler(EvaluadorPromociones evaluadorPromociones, VentaService ventaService) {
        this.evaluadorPromociones = Objects.requireNonNull(evaluadorPromociones);
        this.ventaService = Objects.requireNonNull(ventaService);
    }

    public DefaultVentaCarritoHandler(PromocionService promocionService, VentaService ventaService) {
        this(Objects.requireNonNull(promocionService)::evaluarPromociones, ventaService);
    }

    @Override public Producto resolverOpcionesProducto(PersonalizacionProductoService service, Producto p) {
        return service.resolverOpcionesProducto(p);
    }

    @Override
    public boolean agregarItemLocal(List<DetalleVenta> items, Producto p, Integer idVariante, String nombreVariante,
                                    List<ProductoModificador> mods, String modsTexto, int qty, String nota,
                                    BigDecimal precioUnitario, BigDecimal subtotal) {
        for (DetalleVenta dv : items) {
            String existente = FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                    dv.getModificadoresSeleccionados(), dv.getModificadoresTexto());
            if (FormatoPersonalizacionUtil.mismaPersonalizacion(dv.getIdProducto(), dv.getIdVariante(), existente,
                    dv.getNotasPreparacion(), p.getIdProducto(), idVariante, modsTexto, nota)) {
                dv.setCantidad(dv.getCantidad() + qty);
                BigDecimal unit = dv.getPrecioUnitario() != null ? dv.getPrecioUnitario() : precioUnitario;
                dv.setSubtotal(unit.multiply(BigDecimal.valueOf(dv.getCantidad())).setScale(2, RoundingMode.HALF_UP));
                return false;
            }
        }
        DetalleVenta nuevo = new DetalleVenta(0, 0, p.getIdProducto(), qty, precioUnitario, subtotal);
        nuevo.setNombreProducto(p.getNombre());
        nuevo.setIdVariante(idVariante);
        nuevo.setNombreVariante(nombreVariante);
        nuevo.setModificadoresTexto(modsTexto);
        nuevo.setModificadoresSeleccionados(mods);
        nuevo.setNotasPreparacion(nota);
        if (p.isEsCombo()) {
            nuevo.setIdCombo(p.getIdCombo());
            nuevo.setComboComponentesTexto(FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(p.getComboComponentes()));
        }
        items.add(nuevo);
        return true;
    }

    @Override public void incrementarItem(DetalleVenta dv) {
        dv.setCantidad(dv.getCantidad() + 1);
        dv.setSubtotal(dv.getPrecioUnitario().multiply(BigDecimal.valueOf(dv.getCantidad())).setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    public boolean decrementarOEliminar(List<DetalleVenta> items, DetalleVenta sel) {
        if (sel == null || !items.contains(sel)) {
            return false;
        }
        if (sel.getCantidad() > 1) {
            sel.setCantidad(sel.getCantidad() - 1);
            sel.setSubtotal(sel.getPrecioUnitario().multiply(BigDecimal.valueOf(sel.getCantidad())).setScale(2, RoundingMode.HALF_UP));
        } else {
            items.remove(sel);
        }
        return true;
    }

    @Override public BigDecimal calcularSumaBruta(List<DetalleVenta> items) {
        return items.stream().map(DetalleVenta::getSubtotal).filter(Objects::nonNull).reduce(CERO, BigDecimal::add);
    }

    @Override public BigDecimal obtenerBasePreCanje(List<DetalleVenta> items, BigDecimal descuentoActivo) {
        return calcularSumaBruta(items).subtract(ceroSiNulo(descuentoActivo)).max(CERO);
    }

    @Override public BigDecimal obtenerTotalNeto(List<DetalleVenta> items, BigDecimal descuentoActivo, BigDecimal descuentoPuntosActivo) {
        return obtenerBasePreCanje(items, descuentoActivo).subtract(ceroSiNulo(descuentoPuntosActivo)).max(CERO);
    }

    @Override
    public ResumenTotalesVenta calcularTotales(List<DetalleVenta> items, BigDecimal descuentoManual,
                                               boolean descuentoManualFijado, BigDecimal descuentoPuntosActivo)
            throws NegocioException {
        BigDecimal bruto = calcularSumaBruta(items);
        BigDecimal descuento = ceroSiNulo(descuentoManual);
        if (!descuentoManualFijado && !items.isEmpty()) {
            PromocionService.ResultadoEvaluacion resultado = evaluadorPromociones.evaluar(new ArrayList<>(items), bruto);
            descuento = resultado.tieneDescuento() ? resultado.getDescuentoTotal() : CERO;
        }
        BigDecimal basePreCanje = obtenerBasePreCanje(items, descuento);
        BigDecimal gravado = CERO.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal igv = gravado;
        if (basePreCanje.compareTo(CERO) > 0) {
            DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
            gravado = desglose.getSubtotalGravado();
            igv = desglose.getIgv();
        }
        BigDecimal puntos = ceroSiNulo(descuentoPuntosActivo).min(basePreCanje);
        BigDecimal neto = basePreCanje.subtract(puntos).setScale(2, RoundingMode.HALF_UP);
        return new ResumenTotalesVenta(bruto, descuento, basePreCanje, gravado, igv, puntos, neto,
                descuento.compareTo(CERO) > 0, puntos.compareTo(CERO) > 0);
    }

    @Override public BigDecimal calcularDescuentoAutorizado(List<DetalleVenta> items, BigDecimal valor,
                                                             boolean esPorcentaje, SesionUsuario sesion)
            throws NegocioException {
        return ventaService.calcularDescuentoAutorizado(calcularSumaBruta(items), valor, esPorcentaje, sesion);
    }

    private BigDecimal ceroSiNulo(BigDecimal valor) { return valor == null ? CERO : valor; }
}
