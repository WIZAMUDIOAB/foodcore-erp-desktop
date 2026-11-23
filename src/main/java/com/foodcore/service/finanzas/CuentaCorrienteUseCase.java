package com.foodcore.service.finanzas;

import com.foodcore.model.Venta;
import java.sql.SQLException;

public interface CuentaCorrienteUseCase {
    boolean registrarPagoProveedor(int idCuentaPagar, double monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException;
    boolean registrarCobroCliente(int idCuentaCobrar, double monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException;
    int registrarVentaCreditoCorporativo(Venta venta, int idCliente, int diasCredito) throws SQLException;
}
