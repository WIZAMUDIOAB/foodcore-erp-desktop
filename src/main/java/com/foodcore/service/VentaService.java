package com.foodcore.service;

import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.model.dto.ResultadoDevolucionParcial;
import com.foodcore.model.dto.SubcuentaItems;
import com.foodcore.security.SesionUsuario;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Contrato de servicio transaccional de cobro, emisión de comprobantes, split y devoluciones de ventas.
 */
public interface VentaService {

    Venta procesarCobroVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    BigDecimal calcularDescuentoAutorizado(BigDecimal totalBruto, BigDecimal valorDescuento,
                                           boolean esPorcentaje, SesionUsuario sesion)
            throws NegocioException;

    Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    Venta registrarVentaConCreditoCorporativo(Venta venta, List<DetalleVenta> detalles,
                                               ComprobantePago comprobante, String docCliente,
                                               String nombreCliente)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    Venta registrarVentaDividida(Venta venta, List<DetalleVenta> detalles,
                                 ComprobantePago comprobante, List<PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    List<Venta> dividirCuentaPorItems(List<SubcuentaItems> subcuentas, int idUsuario)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    void anularVenta(int idVenta, boolean platoYaPreparado, SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException;

    Usuario anularVentaConSupervisor(int idVenta, boolean platoYaPreparado, SesionUsuario sesionCajero,
                                     String pinSupervisor, String motivo)
            throws NegocioException, SQLException;

    List<Venta> listarVentasHistorial(LocalDate desde, LocalDate hasta, String textoBusqueda)
            throws SQLException;

    List<DetalleVenta> listarDetallesVenta(int idVenta) throws SQLException;

    ResultadoDevolucionParcial devolverItemsParcial(int idVenta, List<Integer> idsDetalleADevolver,
                                                    SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException;

    static String normalizarMedioPagoMovimientoCaja(String medioPago) {
        if (medioPago == null || medioPago.isBlank()) {
            return "EFECTIVO";
        }
        String limpio = medioPago.trim().toUpperCase(Locale.ROOT);
        if ("CREDITO_CORPORATIVO".equals(limpio)) {
            return "CREDITO";
        }
        return limpio.length() > 15 ? limpio.substring(0, 15) : limpio;
    }
}
