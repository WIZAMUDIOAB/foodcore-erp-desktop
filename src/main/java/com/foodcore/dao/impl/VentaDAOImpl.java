package com.foodcore.dao.impl;

import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Implementación canónica de {@code VentaDAO} en paquete {@code com.foodcore.dao.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01).
 */
public class VentaDAOImpl extends com.foodcore.dao.VentaDAOImpl {

    @Override
    public int insertarVenta(Connection conn, Venta venta) throws SQLException {
        return super.insertarVenta(conn, venta);
    }

    @Override
    public void insertarDetalleVenta(Connection conn, DetalleVenta detalle) throws SQLException {
        super.insertarDetalleVenta(conn, detalle);
    }

    @Override
    public void insertarComprobante(Connection conn, ComprobantePago comp) throws SQLException {
        super.insertarComprobante(conn, comp);
    }

    @Override
    public List<Venta> listarVentasDelDia(Connection conn) throws SQLException {
        return super.listarVentasDelDia(conn);
    }
}
