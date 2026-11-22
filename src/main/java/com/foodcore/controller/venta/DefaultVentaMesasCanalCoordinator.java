package com.foodcore.controller.venta;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.service.VentaPosConsultaService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultVentaMesasCanalCoordinator implements VentaMesasCanalCoordinator {
    private static final Logger LOGGER = Logger.getLogger(DefaultVentaMesasCanalCoordinator.class.getName());
    private final VentaPosConsultaService posConsultaService;

    public DefaultVentaMesasCanalCoordinator(VentaPosConsultaService posConsultaService) {
        this.posConsultaService = posConsultaService;
    }

    @Override
    public List<Pedido> listarPedidosActivosConDetallesSafe() {
        try {
            return posConsultaService.listarPedidosActivosConDetalles();
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Aviso consultando pedidos multicanal en POS", ex);
            return new ArrayList<>();
        }
    }

    @Override
    public List<DetallePedido> listarDetallesPorPedido(int idPedido) {
        try {
            return posConsultaService.listarDetallesPorPedido(idPedido);
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso cargando detalles de pedido #" + idPedido, ex);
            return List.of();
        }
    }

    @Override
    public String determinarCanalPedido(Pedido p) {
        if (p == null) return "MESA";
        String tipo = p.getTipoPedido() != null ? p.getTipoPedido().trim().toUpperCase(Locale.ROOT) : "";
        String mesa = p.getNumeroMesa() != null ? p.getNumeroMesa().trim().toUpperCase(Locale.ROOT) : "";
        if (tipo.contains("KIOSCO") || mesa.startsWith("T-") || mesa.contains("KIOSCO")) return "KIOSCO";
        if (tipo.contains("DELIVERY") || mesa.startsWith("DL-") || mesa.contains("DELIVERY")) return "DELIVERY";
        return "MESA";
    }

    @Override
    public String construirTextoBadgeCanal(Pedido p) {
        String canal = determinarCanalPedido(p);
        if ("KIOSCO".equals(canal)) return "[KIOSCO]";
        if ("DELIVERY".equals(canal)) return "[DELIVERY]";
        return "[MESA " + normalizarNumeroMesa(p != null ? p.getNumeroMesa() : "01") + "]";
    }

    @Override
    public String normalizarNumeroMesa(String raw) {
        if (raw == null || raw.isBlank()) return "01";
        String digits = raw.replaceAll("\\D+", "");
        if (digits.isEmpty()) return raw.trim();
        return String.format("%02d", Integer.parseInt(digits));
    }

    @Override
    public ResumenCanalesPos particionarPedidosActivos(List<Pedido> activos) {
        Map<String, Pedido> mesas = new LinkedHashMap<>();
        List<Pedido> externos = new ArrayList<>();
        if (activos != null) {
            for (Pedido pedido : activos) {
                if ("MESA".equals(determinarCanalPedido(pedido))) {
                    mesas.putIfAbsent(normalizarNumeroMesa(pedido.getNumeroMesa()), pedido);
                } else {
                    externos.add(pedido);
                }
            }
        }
        return new ResumenCanalesPos(mesas, externos);
    }

    @Override
    public CabeceraCanalPos resolverCabeceraPedido(Pedido pedido) {
        String canal = determinarCanalPedido(pedido);
        String badge = construirTextoBadgeCanal(pedido);
        if ("KIOSCO".equals(canal)) {
            String mesa = pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "T-101";
            return new CabeceraCanalPos("Kiosco · " + mesa, badge, canal, mesa);
        }
        if ("DELIVERY".equals(canal)) {
            String cliente = pedido.getClienteNombre() != null && !pedido.getClienteNombre().isBlank()
                    ? pedido.getClienteNombre() : "Pedido #" + pedido.getIdPedido();
            String mesa = pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "DL-01";
            return new CabeceraCanalPos("Delivery · " + cliente, badge, canal, mesa);
        }
        String mesa = normalizarNumeroMesa(pedido.getNumeroMesa());
        return new CabeceraCanalPos("Mesa " + mesa + " · Salón", badge, canal, mesa);
    }

    @Override
    public CabeceraCanalPos resolverCabeceraMesaLibre(String numeroMesa) {
        String mesa = normalizarNumeroMesa(numeroMesa);
        return new CabeceraCanalPos("Mesa " + mesa + " · Salón", "[MESA " + mesa + "]", "MESA", mesa);
    }

    @Override
    public List<DetalleVenta> convertirDetallesPedidoAVenta(List<DetallePedido> detalles) {
        List<DetalleVenta> ventas = new ArrayList<>();
        if (detalles == null) return ventas;
        for (DetallePedido dp : detalles) {
            BigDecimal precio = dp.getPrecioUnitario() != null ? dp.getPrecioUnitario() : BigDecimal.ZERO;
            int cantidad = Math.max(1, dp.getCantidad());
            BigDecimal subtotal = dp.getSubtotal() != null ? dp.getSubtotal() : precio.multiply(BigDecimal.valueOf(cantidad));
            DetalleVenta dv = new DetalleVenta(0, 0, dp.getIdProducto(), cantidad, precio, subtotal);
            dv.setNombreProducto(dp.getNombreProducto() != null ? dp.getNombreProducto() : "Plato #" + dp.getIdProducto());
            dv.setNotasPreparacion(dp.getNotasPreparacion());
            dv.setIdVariante(dp.getIdVariante());
            dv.setNombreVariante(dp.getNombreVariante());
            dv.setModificadoresTexto(dp.getModificadoresTexto());
            dv.setModificadoresSeleccionados(dp.getModificadoresSeleccionados());
            dv.setIdCombo(dp.getIdCombo());
            dv.setComboComponentesTexto(dp.getComboComponentesTexto());
            ventas.add(dv);
        }
        return ventas;
    }

    @Override
    public DetallePedido convertirDetalleVentaAPedido(DetalleVenta dv, int idPedido) {
        DetallePedido dp = new DetallePedido();
        dp.setIdPedido(idPedido);
        dp.setIdProducto(dv.getIdProducto());
        dp.setNombreProducto(dv.getNombreProducto());
        dp.setCantidad(dv.getCantidad());
        dp.setPrecioUnitario(dv.getPrecioUnitario());
        dp.setSubtotal(dv.getSubtotal());
        dp.setNotasPreparacion(dv.getNotasPreparacion());
        dp.setIdVariante(dv.getIdVariante());
        dp.setNombreVariante(dv.getNombreVariante());
        dp.setModificadoresTexto(dv.getModificadoresTexto());
        dp.setModificadoresSeleccionados(dv.getModificadoresSeleccionados());
        dp.setIdCombo(dv.getIdCombo());
        dp.setComboComponentesTexto(dv.getComboComponentesTexto());
        return dp;
    }

    @Override
    public void actualizarCachePedidoActivo(List<Pedido> activos, Map<String, Pedido> porMesa, int idPedido,
                                            BigDecimal totalPedido, List<DetallePedido> detalles) {
        for (Pedido pedido : activos) {
            if (pedido.getIdPedido() == idPedido) {
                pedido.setTotal(totalPedido);
                pedido.setDetalles(detalles);
            }
        }
        for (Pedido pedido : porMesa.values()) {
            if (pedido.getIdPedido() == idPedido) {
                pedido.setTotal(totalPedido);
                pedido.setDetalles(detalles);
            }
        }
    }
}
