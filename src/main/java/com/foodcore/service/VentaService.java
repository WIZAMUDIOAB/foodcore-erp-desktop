package com.foodcore.service;

import com.foodcore.dao.*;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.*;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio transaccional de Ventas con control estricto ACID, invariantes fiscales
 * vía Value Object (DesgloseFiscal) y descuento atómico de inventario por recetas.
 */
public class VentaService {

    private static final Logger LOGGER = Logger.getLogger(VentaService.class.getName());

    private final VentaDAO ventaDAO = new VentaDAOImpl();
    private final CajaDAO cajaDAO = new CajaDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final PedidoDAO pedidoDAO = new PedidoDAOImpl();

    /**
     * Ejecuta el registro transaccional de una venta, emitiendo comprobante,
     * descontando insumos de Kardex y liquidando el pedido de salón si aplica.
     */
    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante) throws NegocioException, SQLException {
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("No se puede registrar una venta sin productos en el detalle.");
        }

        // Recalcular total desde las líneas de detalle para garantizar integridad contable
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

        try (Connection conn = ConexionBD.getConnection()) {
            // 1. Validar que exista una caja abierta
            Optional<Caja> cajaAbierta = cajaDAO.obtenerCajaAbierta(conn);
            if (cajaAbierta.isEmpty()) {
                throw new NegocioException("Operación bloqueada: No existe un turno de caja 'ABIERTA'. Debe abrir caja antes de cobrar.");
            }
            venta.setIdCaja(cajaAbierta.get().getIdCaja());

            // 2. Iniciar transacción ACID
            conn.setAutoCommit(false);
            try {
                // 3. Insertar cabecera de venta
                int idVenta = ventaDAO.insertarVenta(conn, venta);
                venta.setIdVenta(idVenta);

                // 4. Insertar detalles y descontar insumos por receta con control de stock negativo
                for (DetalleVenta detalle : detalles) {
                    detalle.setIdVenta(idVenta);
                    ventaDAO.insertarDetalleVenta(conn, detalle);

                    // Consultar receta del producto
                    List<Receta> componentes = insumoDAO.obtenerRecetaPorProducto(conn, detalle.getIdProducto());
                    for (Receta rec : componentes) {
                        BigDecimal cantidadTotalADescontar = rec.getCantidadNecesaria()
                            .multiply(BigDecimal.valueOf(detalle.getCantidad()));

                        // Descontar stock actual (verifica stock_actual >= requerido atómicamente)
                        insumoDAO.descontarStock(conn, rec.getIdInsumo(), cantidadTotalADescontar);

                        // Registrar movimiento en el Kardex de inventario
                        String motivo = "Venta #" + idVenta + " - Prod #" + detalle.getIdProducto() + " (" + detalle.getCantidad() + " und)";
                        insumoDAO.registrarMovimientoInventario(conn, rec.getIdInsumo(), "SALIDA", cantidadTotalADescontar, motivo, idVenta);
                    }
                }

                // 5. Emitir Comprobante de Pago Fiscal
                if (comprobante != null) {
                    comprobante.setIdVenta(idVenta);
                    comprobante.setTotal(desglose.getTotal());
                    ventaDAO.insertarComprobante(conn, comprobante);
                }

                // 6. Si la venta proviene de una comanda de salón, marcar pedido como COBRADO en la misma transacción
                if (venta.getIdPedido() != null && venta.getIdPedido() > 0) {
                    pedidoDAO.actualizarEstadoPedido(conn, venta.getIdPedido(), "COBRADO");
                }

                // 7. Confirmar transacción ACID
                conn.commit();
                LOGGER.info("Venta #" + idVenta + " registrada exitosamente (Total: S/ " + desglose.getTotal() + ").");
                return venta;

            } catch (Exception ex) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Transacción de venta abortada. Se ejecutó Rollback.", ex);
                throw new SQLException("Error durante la transacción de venta: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
