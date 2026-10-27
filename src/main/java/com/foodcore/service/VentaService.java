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
 * Servicio transaccional de Ventas con control estricto ACID y descuento de inventario vía recetas.
 */
public class VentaService {

    private static final Logger LOGGER = Logger.getLogger(VentaService.class.getName());

    private final VentaDAO ventaDAO = new VentaDAOImpl();
    private final CajaDAO cajaDAO = new CajaDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();

    /**
     * Ejecuta el registro transaccional de una venta, emitiendo comprobante y descontando insumos.
     */
    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante) throws NegocioException, SQLException {
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("No se puede registrar una venta sin productos en el detalle.");
        }

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

                // 4. Insertar detalles y descontar insumos por receta
                for (DetalleVenta detalle : detalles) {
                    detalle.setIdVenta(idVenta);
                    ventaDAO.insertarDetalleVenta(conn, detalle);

                    // Consultar receta del producto
                    List<Receta> componentes = insumoDAO.obtenerRecetaPorProducto(conn, detalle.getIdProducto());
                    for (Receta rec : componentes) {
                        BigDecimal cantidadTotalADescontar = rec.getCantidadNecesaria().multiply(BigDecimal.valueOf(detalle.getCantidad()));

                        // Descontar stock actual
                        insumoDAO.descontarStock(conn, rec.getIdInsumo(), cantidadTotalADescontar);

                        // Registrar movimiento de inventario de tipo SALIDA
                        String motivo = "Venta #" + idVenta + " - Prod #" + detalle.getIdProducto() + " (" + detalle.getCantidad() + " und)";
                        insumoDAO.registrarMovimientoInventario(conn, rec.getIdInsumo(), "SALIDA", cantidadTotalADescontar, motivo, idVenta);
                    }
                }

                // 5. Emitir Comprobante de Pago
                if (comprobante != null) {
                    comprobante.setIdVenta(idVenta);
                    comprobante.setTotal(venta.getTotal());
                    ventaDAO.insertarComprobante(conn, comprobante);
                }

                // 6. Confirmar transacción
                conn.commit();
                LOGGER.info("Venta #" + idVenta + " registrada exitosamente con descuento de insumos.");
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
