package com.foodcore.dao;

import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VentaDAO {
    int insertarVenta(Connection conn, Venta venta) throws SQLException;
    void insertarDetalleVenta(Connection conn, DetalleVenta detalle) throws SQLException;
    void insertarComprobante(Connection conn, ComprobantePago comprobante) throws SQLException;
    List<Venta> listarVentasDelDia(Connection conn) throws SQLException;
    List<Venta> listarVentasConFiltro(Connection conn, LocalDate desde, LocalDate hasta, String textoBusqueda) throws SQLException;
    Optional<Venta> buscarVentaPorId(Connection conn, int idVenta) throws SQLException;
    List<DetalleVenta> listarDetallesPorVenta(Connection conn, int idVenta) throws SQLException;
    int marcarDetalleDevuelto(Connection conn, int idDetalleVenta, String motivo) throws SQLException;
    void actualizarTotalesVentaPorDevolucion(Connection conn, int idVenta, BigDecimal nuevoSubtotal,
                                             BigDecimal nuevoIgv, BigDecimal nuevoTotal, String nuevoEstado) throws SQLException;
}
