package com.foodcore.service.impl;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.ComboDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.ComboDAOImpl;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
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
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.SupervisorAutorizacionService;
import com.foodcore.service.VentaService;
import com.foodcore.service.venta.AnularDevolverVentaUseCase;
import com.foodcore.service.venta.DefaultAnularDevolverVentaUseCase;
import com.foodcore.service.venta.DefaultRegistrarVentaUseCase;
import com.foodcore.service.venta.DefaultVentaCalculoValidator;
import com.foodcore.service.venta.DefaultVentaInventarioKardexHandler;
import com.foodcore.service.venta.RegistrarVentaUseCase;
import com.foodcore.service.venta.VentaCalculoValidator;
import com.foodcore.service.venta.VentaInventarioKardexHandler;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementación canónica de {@link VentaService} en paquete {@code com.foodcore.service.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-09, R-11).
 */
public class VentaServiceImpl implements VentaService {
    private final VentaDAO ventaDAO;
    private final VentaCalculoValidator calculoValidator;
    private final RegistrarVentaUseCase registrarVentaUseCase;
    private final AnularDevolverVentaUseCase anularDevolverVentaUseCase;

    public VentaServiceImpl() {
        this(new JdbcTransactionManager(), new VentaDAOImpl(), new CajaDAOImpl(),
                new InsumoDAOImpl(), new PedidoDAOImpl(), new ComboDAOImpl());
    }

    public VentaServiceImpl(TransactionManager tx, VentaDAO ventaDAO, CajaDAO cajaDAO,
                            InsumoDAO insumoDAO, PedidoDAO pedidoDAO) {
        this(tx, ventaDAO, cajaDAO, insumoDAO, pedidoDAO, new ComboDAOImpl());
    }

    public VentaServiceImpl(TransactionManager tx, VentaDAO ventaDAO, CajaDAO cajaDAO,
                            InsumoDAO insumoDAO, PedidoDAO pedidoDAO, ComboDAO comboDAO) {
        this.ventaDAO = ventaDAO;
        CajaDAO effectiveCajaDAO = cajaDAO != null ? cajaDAO : new CajaDAOImpl();
        InsumoDAO effectiveInsumoDAO = insumoDAO != null ? insumoDAO : new InsumoDAOImpl();
        PedidoDAO effectivePedidoDAO = pedidoDAO != null ? pedidoDAO : new PedidoDAOImpl();
        ComboDAO effectiveComboDAO = comboDAO != null ? comboDAO : new ComboDAOImpl();
        AutorizacionService autorizacionService = new AutorizacionService();
        FidelizacionService fidelizacionService = new FidelizacionService();
        CierreContableService cierreContableService = new CierreContableService();
        SupervisorAutorizacionService supervisorAutorizacionService = new SupervisorAutorizacionService();
        AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();
        this.calculoValidator = new DefaultVentaCalculoValidator(autorizacionService, fidelizacionService);
        VentaInventarioKardexHandler inventarioKardexHandler =
                new DefaultVentaInventarioKardexHandler(effectiveInsumoDAO, effectiveComboDAO);
        ClienteDAOImpl clienteDAO = new ClienteDAOImpl();
        CuentaPorCobrarDAOImpl cuentaPorCobrarDAO = new CuentaPorCobrarDAOImpl();
        this.registrarVentaUseCase = new DefaultRegistrarVentaUseCase(
                tx, ventaDAO, effectiveCajaDAO, effectivePedidoDAO, clienteDAO, cuentaPorCobrarDAO,
                cierreContableService, fidelizacionService, calculoValidator, inventarioKardexHandler);
        this.anularDevolverVentaUseCase = new DefaultAnularDevolverVentaUseCase(
                tx, ventaDAO, effectiveCajaDAO, autorizacionService, supervisorAutorizacionService,
                cierreContableService, fidelizacionService, auditoriaDAO, calculoValidator, inventarioKardexHandler);
    }

    public VentaServiceImpl(VentaDAO ventaDAO, VentaCalculoValidator calculoValidator,
                            RegistrarVentaUseCase registrarVentaUseCase,
                            AnularDevolverVentaUseCase anularDevolverVentaUseCase) {
        this.ventaDAO = ventaDAO;
        this.calculoValidator = calculoValidator;
        this.registrarVentaUseCase = registrarVentaUseCase;
        this.anularDevolverVentaUseCase = anularDevolverVentaUseCase;
    }

    @Override
    public Venta procesarCobroVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVenta(venta, detalles, comprobante);
    }

    @Override
    public BigDecimal calcularDescuentoAutorizado(BigDecimal totalBruto, BigDecimal valorDescuento,
                                                  boolean esPorcentaje, SesionUsuario sesion)
            throws NegocioException {
        return calculoValidator.calcularDescuentoAutorizado(totalBruto, valorDescuento, esPorcentaje, sesion);
    }

    @Override
    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.registrarVentaConPagos(venta, detalles, comprobante, null);
    }

    @Override
    public Venta registrarVentaConCreditoCorporativo(Venta venta, List<DetalleVenta> detalles,
                                                      ComprobantePago comprobante, String docCliente,
                                                      String nombreCliente)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.registrarVentaConCreditoCorporativo(
                venta, detalles, comprobante, docCliente, nombreCliente);
    }

    @Override
    public Venta registrarVentaDividida(Venta venta, List<DetalleVenta> detalles,
                                        ComprobantePago comprobante, List<PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.registrarVentaDividida(venta, detalles, comprobante, pagosDivididos);
    }

    @Override
    public List<Venta> dividirCuentaPorItems(List<SubcuentaItems> subcuentas, int idUsuario)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.dividirCuentaPorItems(subcuentas, idUsuario);
    }

    @Override
    public void anularVenta(int idVenta, boolean platoYaPreparado, SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException {
        anularDevolverVentaUseCase.anularVenta(idVenta, platoYaPreparado, sesion, motivo);
    }

    @Override
    public Usuario anularVentaConSupervisor(int idVenta, boolean platoYaPreparado, SesionUsuario sesionCajero,
                                            String pinSupervisor, String motivo)
            throws NegocioException, SQLException {
        return anularDevolverVentaUseCase.anularVentaConSupervisor(
                idVenta, platoYaPreparado, sesionCajero, pinSupervisor, motivo);
    }

    @Override
    public List<Venta> listarVentasHistorial(LocalDate desde, LocalDate hasta, String textoBusqueda)
            throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return ventaDAO.listarVentasConFiltro(conn, desde, hasta, textoBusqueda);
        }
    }

    @Override
    public List<DetalleVenta> listarDetallesVenta(int idVenta) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return ventaDAO.listarDetallesPorVenta(conn, idVenta);
        }
    }

    @Override
    public ResultadoDevolucionParcial devolverItemsParcial(int idVenta, List<Integer> idsDetalleADevolver,
                                                           SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException {
        return anularDevolverVentaUseCase.devolverItemsParcial(idVenta, idsDetalleADevolver, sesion, motivo);
    }
}
