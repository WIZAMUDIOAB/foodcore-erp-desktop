package com.foodcore.service;

import com.foodcore.dao.*;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.*;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Servicio transaccional de Ventas con control estricto ACID mediante {@link TransactionManager},
 * bloqueo pesimista {@code SELECT ... FOR UPDATE} en orden ascendente de {@code id_insumo} (R-09),
 * propagación directa de {@link StockInsuficienteException} sin envolver en {@link SQLException} (R-11),
 * trazabilidad en {@code kardex_movimiento} y control de idempotencia (§8.1, §9.1, §9.2).
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
     * Ejecuta el registro transaccional e idempotente de una venta, bloqueando insumos en orden ascendente,
     * descontando stock, registrando movimientos en {@code kardex_movimiento} y {@code movimiento_caja},
     * y liquidando el pedido de salón si aplica.
     *
     * @throws StockInsuficienteException directamente (sin envolver en {@link SQLException}) si algún insumo carece de stock (R-11).
     */
    public Venta procesarCobroVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVenta(venta, detalles, comprobante);
    }

    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
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

        // 3. Ejecutar unidad de trabajo atómica ACID vía TransactionManager (§7.1, §9.1, R-09, R-10, R-11)
        return tx.executeChecked(conn -> {
            Optional<Caja> cajaAbierta = cajaDAO.obtenerCajaAbierta(conn);
            if (cajaAbierta.isEmpty()) {
                throw new CajaCerradaException();
            }
            int idCaja = cajaAbierta.get().getIdCaja();
            venta.setIdCaja(idCaja);

            // 3.1 Consolidar requerimientos de insumos de todas las líneas ordenados ascendentemente por id_insumo (R-09)
            TreeMap<Long, BigDecimal> requerimientosPorInsumo = new TreeMap<>();
            for (DetalleVenta detalle : detalles) {
                List<Receta> componentes = insumoDAO.obtenerRecetaPorProducto(conn, detalle.getIdProducto());
                for (Receta rec : componentes) {
                    BigDecimal cantRequerida = DesgloseFiscal.normalizarStock(
                        rec.getCantidadNecesaria().multiply(BigDecimal.valueOf(detalle.getCantidad()))
                    );
                    requerimientosPorInsumo.merge(rec.getIdInsumo(), cantRequerida, BigDecimal::add);
                }
            }

            // 3.2 Adquirir bloqueo pesimista SELECT ... FOR UPDATE en orden ascendente de id_insumo (R-09)
            List<Long> idsOrdenados = new ArrayList<>(requerimientosPorInsumo.keySet());
            Map<Long, BigDecimal> saldosAnteriores = insumoDAO.lockearPorIds(conn, idsOrdenados);

            // 3.3 Insertar cabecera y detalle de venta
            int idVenta = ventaDAO.insertarVenta(conn, venta);
            venta.setIdVenta(idVenta);

            for (DetalleVenta detalle : detalles) {
                detalle.setIdVenta(idVenta);
                ventaDAO.insertarDetalleVenta(conn, detalle);
            }

            // 3.4 Descontar stock en orden ascendente y registrar kardex_movimiento con saldo_anterior/posterior (R-02.3, R-09, R-11)
            for (Long idInsumo : idsOrdenados) {
                if (!saldosAnteriores.containsKey(idInsumo)) {
                    throw new InsumoNoEncontradoException(idInsumo);
                }
                BigDecimal cantidadADescontar = requerimientosPorInsumo.get(idInsumo)
                    .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
                BigDecimal saldoAnterior = saldosAnteriores.get(idInsumo);

                // Lanza directamente StockInsuficienteException si stock_actual_disponible < cantidadADescontar
                insumoDAO.descontarStock(conn, idInsumo, cantidadADescontar);

                BigDecimal saldoPosterior = saldoAnterior.subtract(cantidadADescontar)
                    .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
                Insumo ins = insumoDAO.obtenerPorId(conn, idInsumo);
                BigDecimal costoUnit = (ins != null && ins.getCostoUnitario() != null)
                    ? ins.getCostoUnitario() : BigDecimal.ZERO;

                String glosa = "Venta #" + idVenta;
                insumoDAO.registrarMovimientoInventario(conn, idInsumo, "SALIDA", cantidadADescontar, glosa, idVenta);

                KardexMovimiento km = new KardexMovimiento(
                    0L,
                    idInsumo,
                    "SALIDA",
                    cantidadADescontar.negate(), // Cantidad firmada negativa para SALIDA (§8.2)
                    saldoAnterior,
                    saldoPosterior,
                    costoUnit,
                    "VENTA",
                    (long) idVenta,
                    venta.getIdUsuario() > 0 ? venta.getIdUsuario() : 1L,
                    glosa,
                    LocalDateTime.now()
                );
                insumoDAO.insertarKardex(conn, km);
            }

            // 3.5 Registrar movimiento en libro de caja (tipo VENTA)
            String medioPago = venta.getMetodoPago() != null ? venta.getMetodoPago() : "EFECTIVO";
            MovimientoCaja movVenta = new MovimientoCaja(
                0, idCaja, venta.getIdUsuario() > 0 ? venta.getIdUsuario() : 1,
                "VENTA", medioPago, desglose.getTotal(),
                "Cobro Venta #" + idVenta + " [" + medioPago + "]"
            );
            cajaDAO.registrarMovimiento(conn, movVenta);

            // 3.6 Emitir Comprobante de Pago
            if (comprobante != null) {
                comprobante.setIdVenta(idVenta);
                comprobante.setTotal(desglose.getTotal());
                ventaDAO.insertarComprobante(conn, comprobante);
            }

            // 3.7 Si la venta proviene de un pedido de salón, marcar pedido como COBRADO en la misma transacción
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
