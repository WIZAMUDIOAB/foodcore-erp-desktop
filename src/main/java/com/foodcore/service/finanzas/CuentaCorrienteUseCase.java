package com.foodcore.service.finanzas;

import com.foodcore.model.Venta;
import java.math.BigDecimal;
import java.sql.SQLException;

public interface CuentaCorrienteUseCase {
    boolean registrarPagoProveedor(int idCuentaPagar, BigDecimal monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException;
    boolean registrarCobroCliente(int idCuentaCobrar, BigDecimal monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException;
    int registrarVentaCreditoCorporativo(Venta venta, int idCliente, int diasCredito) throws SQLException;
}
