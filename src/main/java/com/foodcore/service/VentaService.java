package com.foodcore.service;

import com.foodcore.dao.*;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.*;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Servicio transaccional de Ventas con control estricto ACID mediante {@link TransactionManager},
 * idempotencia por {@code idempotency_key} (§8.1, §9.2), invariantes fiscales vía
 * {@link DesgloseFiscal} y descuento atómico de inventario por recetas.
 */
public class VentaService {

    private static final Logger LOGGER = Logger.getLogger(VentaService.class.getName());
    private static final Map<String, Venta> REGISTRO_IDEMPOTENCIA = new ConcurrentHashMap<>();

    private final TransactionManager tx;
    private final VentaDAO ventaDAO;
    private final CajaDAO cajaDAO;
    private final InsumoDAO insumoDAO;
    private final PedidoDAO pedidoDAO;

    public VentaService() {
        this(new JdbcTransactionManager(), new VentaDAOImpl(), new CajaDAOImpl(), new InsumoDAOImpl(), new PedidoDAOImpl());
    }

    public VentaService(TransactionManager tx,
                        VentaDAO ventaDAO,
                        CajaDAO cajaDAO,
                        InsumoDAO insumoDAO,
                        PedidoDAO pedidoDAO) {
        this.tx = tx;
        this.ventaDAO = ventaDAO;
        this.cajaDAO = cajaDAO;
        this.insumoDAO = insumoDAO;
        this.pedidoDAO = pedidoDAO;
    }

    /**
     * Ejecuta el registro transaccional e idempotente de una venta, emitiendo comprobante,
     * descontando insumos de Kárdex y liquidando el pedido de salón si aplica.
     */
    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante) throws NegocioException, SQLException {
        if (venta == null) {
            throw new NegocioException("Los datos de la venta no pueden ser nulos.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("No se puede registrar una venta sin productos en el detalle.");
        }

        // 1. Verificar idempotencia (SPEC-FOODCORE-v1.0 §8.1, §9.2)
        String idemKey = venta.getIdempotencyKey();
        if (idemKey != null && REGISTRO_IDEMPOTENCIA.containsKey(idemKey)) {
            LOGGER.info("Idempotencia activada: devolviendo venta previamente procesada para clave " + idemKey);
            return REGISTRO_IDEMPOTENCIA.get(idemKey);
        }

        // 2. Recalcular total desde las líneas de detalle con redondeo HALF_EVEN
        BigDecimal sumaLineas = BigDecimal.ZERO;
        for (DetalleVenta det : detalles) {
            if (det.getCantidad() <= 0) {
                throw new NegocioException("La cantidad de cada ítem de venta debe ser mayor a cero.");
            }
            BigDecimal subtotalLinea = det.getPrecioUnitario()
                .multiply(BigDecimal.valueOf(det.getCantidad()))
                .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
            det.setSubtotal(subtotalLinea);
            sumaLineas = sumaLineas.add(subtotalLinea);
        }

        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(sumaLineas);
        venta.setSubtotal(desglose.getSubtotalGravado());
        venta.setIgv(desglose.getIgv());
        venta.setTotal(desglose.getTotal());

        // 3. Ejecutar unidad de trabajo atómica ACID vía TransactionManager (§7.1)
        return tx.executeChecked(conn -> {
            Optional<Caja> cajaAbierta = cajaDAO.obtenerCajaAbierta(conn);
            if (cajaAbierta.isEmpty()) {
                throw new CajaCerradaException();
            }
            venta.setIdCaja(cajaAbierta.get().getIdCaja());

            int idVenta = ventaDAO.insertarVenta(conn, venta);
            venta.setIdVenta(idVenta);

            for (DetalleVenta detalle : detalles) {
                detalle.setIdVenta(idVenta);
                ventaDAO.insertarDetalleVenta(conn, detalle);

                List<Receta> componentes = insumoDAO.obtenerRecetaPorProducto(conn, detalle.getIdProducto());
                for (Receta rec : componentes) {
                    BigDecimal cantidadTotalADescontar = DesgloseFiscal.normalizarStock(
                        rec.getCantidadNecesaria().multiply(BigDecimal.valueOf(detalle.getCantidad()))
                    );

                    try {
                        insumoDAO.descontarStock(conn, rec.getIdInsumo(), cantidadTotalADescontar);
                    } catch (SQLException se) {
                        if (se.getCause() instanceof NegocioException ne) {
                            throw ne;
                        }
                        throw se;
                    }

                    String motivo = "Venta #" + idVenta + " - Prod #" + detalle.getIdProducto() + " (" + detalle.getCantidad() + " und)";
                    insumoDAO.registrarMovimientoInventario(conn, rec.getIdInsumo(), "SALIDA", cantidadTotalADescontar, motivo, idVenta);
                }
            }

            if (comprobante != null) {
                comprobante.setIdVenta(idVenta);
                comprobante.setTotal(desglose.getTotal());
                ventaDAO.insertarComprobante(conn, comprobante);
            }

            if (venta.getIdPedido() != null && venta.getIdPedido() > 0) {
                pedidoDAO.actualizarEstadoPedido(conn, venta.getIdPedido(), "COBRADO");
            }

            if (idemKey != null) {
                REGISTRO_IDEMPOTENCIA.put(idemKey, venta);
            }
            LOGGER.info("Venta #" + idVenta + " registrada exitosamente (Total: S/ " + desglose.getTotal() + ").");
            return venta;
        });
    }
}
