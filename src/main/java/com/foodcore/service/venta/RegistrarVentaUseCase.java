package com.foodcore.service.venta;

import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.service.VentaService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface RegistrarVentaUseCase {
    Venta registrarVentaConPagos(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante,
                                 List<VentaService.PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    Venta registrarVentaDividida(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante,
                                 List<VentaService.PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    Venta registrarVentaConCreditoCorporativo(Venta venta, List<DetalleVenta> detalles,
                                              ComprobantePago comprobante, String docCliente,
                                              String nombreCliente)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    List<Venta> dividirCuentaPorItems(List<VentaService.SubcuentaItems> subcuentas, int idUsuario)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    Venta registrarVentaEnConexion(Connection conn, Venta venta, List<DetalleVenta> detalles,
                                   ComprobantePago comprobante, List<VentaService.PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;
}