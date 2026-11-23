package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.dao.impl.CuentaPorPagarDAOImpl;
import com.foodcore.dao.impl.HistorialPrecioDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.ProveedorDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.service.finanzas.CambioPrecioUseCase;
import com.foodcore.service.finanzas.CuentaCorrienteUseCase;
import com.foodcore.service.finanzas.DefaultCambioPrecioUseCase;
import com.foodcore.service.finanzas.DefaultCuentaCorrienteUseCase;
import com.foodcore.service.finanzas.DefaultFinanzasDocumentoPdfGenerator;
import com.foodcore.service.finanzas.FinanzasDocumentoPdfGenerator;
import com.foodcore.model.Insumo;
import com.foodcore.model.Venta;
import java.io.File;
import java.sql.SQLException;
import java.util.List;

public class FinanzasService {
    private final CuentaCorrienteUseCase cuentaCorrienteUseCase;
    private final CambioPrecioUseCase cambioPrecioUseCase;
    private final FinanzasDocumentoPdfGenerator documentoPdfGenerator;
    public FinanzasService() { this(new ProveedorDAOImpl(), new CuentaPorPagarDAOImpl(), new ClienteDAOImpl(), new CuentaPorCobrarDAOImpl(), new HistorialPrecioDAOImpl(), new InsumoDAOImpl(), new ProductoDAOImpl(), new CajaDAOImpl(), new VentaDAOImpl()); }
    public FinanzasService(ProveedorDAO proveedorDAO, CuentaPorPagarDAO cuentaPorPagarDAO, ClienteDAO clienteDAO, CuentaPorCobrarDAO cuentaPorCobrarDAO, HistorialPrecioDAO historialPrecioDAO, InsumoDAO insumoDAO, ProductoDAO productoDAO, CajaDAO cajaDAO, VentaDAO ventaDAO) {
        this(new DefaultCuentaCorrienteUseCase(null, cuentaPorPagarDAO, cuentaPorCobrarDAO, clienteDAO, cajaDAO, ventaDAO, null), new DefaultCambioPrecioUseCase(null, historialPrecioDAO), new DefaultFinanzasDocumentoPdfGenerator(proveedorDAO, clienteDAO, cuentaPorCobrarDAO));
    }
    public FinanzasService(CuentaCorrienteUseCase cuentaCorrienteUseCase, CambioPrecioUseCase cambioPrecioUseCase, FinanzasDocumentoPdfGenerator documentoPdfGenerator) { this.cuentaCorrienteUseCase = cuentaCorrienteUseCase; this.cambioPrecioUseCase = cambioPrecioUseCase; this.documentoPdfGenerator = documentoPdfGenerator; }
    public boolean registrarPagoProveedor(int idCuentaPagar, double monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException { return cuentaCorrienteUseCase.registrarPagoProveedor(idCuentaPagar,monto,metodoPago,idCaja,idUsuario,nota); }
    public boolean registrarCobroCliente(int idCuentaCobrar, double monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException { return cuentaCorrienteUseCase.registrarCobroCliente(idCuentaCobrar,monto,metodoPago,idCaja,idUsuario,nota); }
    public int registrarVentaCreditoCorporativo(Venta venta,int idCliente,int diasCredito) throws SQLException { return cuentaCorrienteUseCase.registrarVentaCreditoCorporativo(venta,idCliente,diasCredito); }
    public boolean registrarCambioPrecioConMotivo(String tipoEntidad,int idEntidad,String nombreEntidad,double precioAnterior,double precioNuevo,int idUsuario,String nombreUsuario,String motivo) throws SQLException { return cambioPrecioUseCase.registrarCambioPrecioConMotivo(tipoEntidad,idEntidad,nombreEntidad,precioAnterior,precioNuevo,idUsuario,nombreUsuario,motivo); }
    public void generarOrdenCompraPdf(int idProveedor,List<Insumo> insumosRequeridos,File archivoDestino) throws Exception { documentoPdfGenerator.generarOrdenCompraPdf(idProveedor,insumosRequeridos,archivoDestino); }
    public void generarEstadoCuentaClientePdf(int idCliente,File archivoDestino) throws Exception { documentoPdfGenerator.generarEstadoCuentaClientePdf(idCliente,archivoDestino); }
}
