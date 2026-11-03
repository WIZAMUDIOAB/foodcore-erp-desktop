package com.foodcore.service.impl;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.service.VentaService;
import com.foodcore.tx.TransactionManager;

import java.sql.SQLException;
import java.util.List;

/**
 * Implementación canónica de {@link VentaService} en paquete {@code com.foodcore.service.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-09, R-11).
 */
public class VentaServiceImpl extends VentaService {

    public VentaServiceImpl() {
        super();
    }

    public VentaServiceImpl(TransactionManager tx,
                            VentaDAO ventaDAO,
                            CajaDAO cajaDAO,
                            InsumoDAO insumoDAO,
                            PedidoDAO pedidoDAO) {
        super(tx, ventaDAO, cajaDAO, insumoDAO, pedidoDAO);
    }

    @Override
    public Venta procesarCobroVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return super.procesarCobroVenta(venta, detalles, comprobante);
    }

    @Override
    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return super.registrarVenta(venta, detalles, comprobante);
    }
}
