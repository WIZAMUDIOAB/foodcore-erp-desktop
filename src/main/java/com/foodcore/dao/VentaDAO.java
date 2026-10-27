package com.foodcore.dao;

import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface VentaDAO {
    int insertarVenta(Connection conn, Venta venta) throws SQLException;
    void insertarDetalleVenta(Connection conn, DetalleVenta detalle) throws SQLException;
    void insertarComprobante(Connection conn, ComprobantePago comprobante) throws SQLException;
    List<Venta> listarVentasDelDia(Connection conn) throws SQLException;
}
