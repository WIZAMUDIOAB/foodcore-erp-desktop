package com.foodcore.service.impl;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.ComboDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
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
import java.util.Objects;

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
        this(tx, ventaDAO,
                cajaDAO != null ? cajaDAO : new CajaDAOImpl(),
                insumoDAO != null ? insumoDAO : new InsumoDAOImpl(),
                pedidoDAO != null ? pedidoDAO : new PedidoDAOImpl(),
                comboDAO != null ? comboDAO : new ComboDAOImpl(),
                new ClienteDAOImpl(),
                new CuentaPorCobrarDAOImpl(),
                new AuditoriaDAOImpl(),
                new AutorizacionService(),
                new FidelizacionService(),
                new CierreContableService(),
                new SupervisorAutorizacionService());
    }

    /**
     * Constructor principal de inyección de dependencias sin instanciaciones concretas internas.
     */
    public VentaServiceImpl(TransactionManager tx,
                            VentaDAO ventaDAO,
                            CajaDAO cajaDAO,
                            InsumoDAO insumoDAO,
                            PedidoDAO pedidoDAO,
                            ComboDAO comboDAO,
                            ClienteDAO clienteDAO,
                            CuentaPorCobrarDAO cuentaPorCobrarDAO,
                            AuditoriaDAO auditoriaDAO,
                            AutorizacionService autorizacionService,
                            FidelizacionService fidelizacionService,
                            CierreContableService cierreContableService,
                            SupervisorAutorizacionService supervisorAutorizacionService) {
        TransactionManager effectiveTx = Objects.requireNonNull(tx, "tx no puede ser nulo");
        this.ventaDAO = Objects.requireNonNull(ventaDAO, "ventaDAO no puede ser nulo");
        CajaDAO effectiveCajaDAO = Objects.requireNonNull(cajaDAO, "cajaDAO no puede ser nulo");
        InsumoDAO effectiveInsumoDAO = Objects.requireNonNull(insumoDAO, "insumoDAO no puede ser nulo");
        PedidoDAO effectivePedidoDAO = Objects.requireNonNull(pedidoDAO, "pedidoDAO no puede ser nulo");
        ComboDAO effectiveComboDAO = Objects.requireNonNull(comboDAO, "comboDAO no puede ser nulo");
        ClienteDAO effectiveClienteDAO = Objects.requireNonNull(clienteDAO, "clienteDAO no puede ser nulo");
        CuentaPorCobrarDAO effectiveCxcDAO =
                Objects.requireNonNull(cuentaPorCobrarDAO, "cuentaPorCobrarDAO no puede ser nulo");
        AuditoriaDAO effectiveAuditoriaDAO = Objects.requireNonNull(auditoriaDAO, "auditoriaDAO no puede ser nulo");
        AutorizacionService effectiveAuth =
                Objects.requireNonNull(autorizacionService, "autorizacionService no puede ser nulo");
        FidelizacionService effectiveFidelizacion =
                Objects.requireNonNull(fidelizacionService, "fidelizacionService no puede ser nulo");
        CierreContableService effectiveCierre =
                Objects.requireNonNull(cierreContableService, "cierreContableService no puede ser nulo");
        SupervisorAutorizacionService effectiveSupervisor =
                Objects.requireNonNull(supervisorAutorizacionService, "supervisorAutorizacionService no puede ser nulo");

        this.calculoValidator = new DefaultVentaCalculoValidator(effectiveAuth, effectiveFidelizacion);
        VentaInventarioKardexHandler inventarioKardexHandler =
                new DefaultVentaInventarioKardexHandler(effectiveInsumoDAO, effectiveComboDAO);
        this.registrarVentaUseCase = new DefaultRegistrarVentaUseCase(
                effectiveTx, this.ventaDAO, effectiveCajaDAO, effectivePedidoDAO, effectiveClienteDAO,
                effectiveCxcDAO, effectiveCierre, effectiveFidelizacion, calculoValidator, inventarioKardexHandler);
        this.anularDevolverVentaUseCase = new DefaultAnularDevolverVentaUseCase(
                effectiveTx, this.ventaDAO, effectiveCajaDAO, effectiveAuth, effectiveSupervisor,
                effectiveCierre, effectiveFidelizacion, effectiveAuditoriaDAO,
                calculoValidator, inventarioKardexHandler);
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
