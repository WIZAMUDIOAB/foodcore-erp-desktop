package com.foodcore.service;

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
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
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
import java.util.Locale;

public class VentaService {
    public record PagoSplit(String medioPago, BigDecimal monto) {
    }

    public record SubcuentaItems(List<DetalleVenta> detalles, String medioPago) {
    }

    /** Resultado de una devolución parcial de ítems en venta existente (v5.2.0). */
    public record ResultadoDevolucionParcial(
            int idVenta,
            List<Integer> idsDetalleDevueltos,
            BigDecimal montoReembolso,
            BigDecimal nuevoTotalVenta,
            String nuevoEstadoVenta,
            int cantidadInsumosRevertidos
    ) {
        public ResultadoDevolucionParcial {
            idsDetalleDevueltos = idsDetalleDevueltos != null ? List.copyOf(idsDetalleDevueltos) : List.of();
        }
    }

    private final VentaDAO ventaDAO;
    private final VentaCalculoValidator calculoValidator;
    private final RegistrarVentaUseCase registrarVentaUseCase;
    private final AnularDevolverVentaUseCase anularDevolverVentaUseCase;

    public VentaService() {
        this(new JdbcTransactionManager(), new VentaDAOImpl(), new CajaDAOImpl(),
                new InsumoDAOImpl(), new PedidoDAOImpl(), new ComboDAOImpl());
    }

    public VentaService(TransactionManager tx, VentaDAO ventaDAO, CajaDAO cajaDAO,
                        InsumoDAO insumoDAO, PedidoDAO pedidoDAO) {
        this(tx, ventaDAO, cajaDAO, insumoDAO, pedidoDAO, new ComboDAOImpl());
    }

    public VentaService(TransactionManager tx, VentaDAO ventaDAO, CajaDAO cajaDAO,
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

    public VentaService(VentaDAO ventaDAO, VentaCalculoValidator calculoValidator,
                        RegistrarVentaUseCase registrarVentaUseCase,
                        AnularDevolverVentaUseCase anularDevolverVentaUseCase) {
        this.ventaDAO = ventaDAO;
        this.calculoValidator = calculoValidator;
        this.registrarVentaUseCase = registrarVentaUseCase;
        this.anularDevolverVentaUseCase = anularDevolverVentaUseCase;
    }

    public Venta procesarCobroVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVenta(venta, detalles, comprobante);
    }

    public BigDecimal calcularDescuentoAutorizado(BigDecimal totalBruto, BigDecimal valorDescuento,
                                                   boolean esPorcentaje, SesionUsuario sesion)
            throws NegocioException {
        return calculoValidator.calcularDescuentoAutorizado(totalBruto, valorDescuento, esPorcentaje, sesion);
    }

    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.registrarVentaConPagos(venta, detalles, comprobante, null);
    }

    public Venta registrarVentaConCreditoCorporativo(Venta venta, List<DetalleVenta> detalles,
                                                       ComprobantePago comprobante, String docCliente,
                                                       String nombreCliente)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.registrarVentaConCreditoCorporativo(
                venta, detalles, comprobante, docCliente, nombreCliente);
    }

    public Venta registrarVentaDividida(Venta venta, List<DetalleVenta> detalles,
                                         ComprobantePago comprobante, List<PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.registrarVentaDividida(venta, detalles, comprobante, pagosDivididos);
    }

    public List<Venta> dividirCuentaPorItems(List<SubcuentaItems> subcuentas, int idUsuario)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaUseCase.dividirCuentaPorItems(subcuentas, idUsuario);
    }

    public void anularVenta(int idVenta, boolean platoYaPreparado, SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException {
        anularDevolverVentaUseCase.anularVenta(idVenta, platoYaPreparado, sesion, motivo);
    }

    public Usuario anularVentaConSupervisor(int idVenta, boolean platoYaPreparado, SesionUsuario sesionCajero,
                                             String pinSupervisor, String motivo)
            throws NegocioException, SQLException {
        return anularDevolverVentaUseCase.anularVentaConSupervisor(
                idVenta, platoYaPreparado, sesionCajero, pinSupervisor, motivo);
    }

    public List<Venta> listarVentasHistorial(LocalDate desde, LocalDate hasta, String textoBusqueda)
            throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return ventaDAO.listarVentasConFiltro(conn, desde, hasta, textoBusqueda);
        }
    }

    public List<DetalleVenta> listarDetallesVenta(int idVenta) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return ventaDAO.listarDetallesPorVenta(conn, idVenta);
        }
    }

    public ResultadoDevolucionParcial devolverItemsParcial(int idVenta, List<Integer> idsDetalleADevolver,
                                                            SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException {
        return anularDevolverVentaUseCase.devolverItemsParcial(idVenta, idsDetalleADevolver, sesion, motivo);
    }

    public static String normalizarMedioPagoMovimientoCaja(String medioPago) {
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
