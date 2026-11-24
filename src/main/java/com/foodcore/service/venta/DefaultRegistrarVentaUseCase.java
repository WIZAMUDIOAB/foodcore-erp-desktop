package com.foodcore.service.venta;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Caja;
import com.foodcore.model.Cliente;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.Venta;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.VentaService;
import com.foodcore.tx.TransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

public final class DefaultRegistrarVentaUseCase implements RegistrarVentaUseCase {
    private static final Logger LOGGER = Logger.getLogger(DefaultRegistrarVentaUseCase.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String SQL_LOCK_CLIENTE_CREDITO =
            "SELECT limite_credito, dias_credito, activo FROM cliente WHERE id_cliente = ? FOR UPDATE";
    private static final String SQL_DEUDA_PENDIENTE_CLIENTE =
            "SELECT COALESCE(SUM(monto_total - monto_cobrado), 0) AS deuda FROM cuenta_por_cobrar "
                    + "WHERE id_cliente = ? AND estado <> 'COBRADA'";

    private final TransactionManager tx;
    private final VentaDAO ventaDAO;
    private final CajaDAO cajaDAO;
    private final PedidoDAO pedidoDAO;
    private final ClienteDAO clienteDAO;
    private final CuentaPorCobrarDAO cuentaPorCobrarDAO;
    private final CierreContableService cierreContableService;
    private final FidelizacionService fidelizacionService;
    private final VentaCalculoValidator calculoValidator;
    private final VentaInventarioKardexHandler inventarioKardexHandler;

    public DefaultRegistrarVentaUseCase(TransactionManager tx, VentaDAO ventaDAO, CajaDAO cajaDAO,
                                        PedidoDAO pedidoDAO, ClienteDAO clienteDAO,
                                        CuentaPorCobrarDAO cuentaPorCobrarDAO,
                                        CierreContableService cierreContableService,
                                        FidelizacionService fidelizacionService,
                                        VentaCalculoValidator calculoValidator,
                                        VentaInventarioKardexHandler inventarioKardexHandler) {
        this.tx = tx;
        this.ventaDAO = ventaDAO;
        this.cajaDAO = cajaDAO;
        this.pedidoDAO = pedidoDAO;
        this.clienteDAO = clienteDAO;
        this.cuentaPorCobrarDAO = cuentaPorCobrarDAO;
        this.cierreContableService = cierreContableService;
        this.fidelizacionService = fidelizacionService;
        this.calculoValidator = calculoValidator;
        this.inventarioKardexHandler = inventarioKardexHandler;
    }

    @Override
    public Venta registrarVentaConPagos(Venta venta, List<DetalleVenta> detalles,
                                        ComprobantePago comprobante,
                                        List<VentaService.PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        calculoValidator.validarYPrepararMontosVenta(venta, detalles, pagosDivididos);
        return tx.executeChecked(conn ->
                registrarVentaEnConexion(conn, venta, detalles, comprobante, pagosDivididos));
    }

    @Override
    public Venta registrarVentaDividida(Venta venta, List<DetalleVenta> detalles,
                                        ComprobantePago comprobante,
                                        List<VentaService.PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (pagosDivididos == null || pagosDivididos.size() < 2) {
            throw new NegocioException("Para dividir la cuenta debe indicar al menos 2 pagos.");
        }
        return registrarVentaConPagos(venta, detalles, comprobante, pagosDivididos);
    }

    @Override
    public Venta registrarVentaConCreditoCorporativo(Venta venta, List<DetalleVenta> detalles,
                                                     ComprobantePago comprobante, String docCliente,
                                                     String nombreCliente)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (venta == null) {
            throw new NegocioException("Los datos de la venta a crédito no pueden ser nulos.");
        }
        calculoValidator.validarYPrepararMontosVenta(venta, detalles, null);

        return tx.executeChecked(conn -> {
            Cliente clienteCredito = resolverClienteParaCredito(conn, venta.getIdCliente(), docCliente);
            if (clienteCredito == null) {
                throw new NegocioException(
                        "Para ventas a CREDITO_CORPORATIVO debe seleccionar o registrar un cliente válido.");
            }
            venta.setIdCliente(clienteCredito.getIdCliente());

            BigDecimal limiteCredito = BigDecimal.ZERO;
            int diasCredito = 30;
            try (PreparedStatement psLock = conn.prepareStatement(SQL_LOCK_CLIENTE_CREDITO)) {
                psLock.setInt(1, clienteCredito.getIdCliente());
                try (ResultSet rs = psLock.executeQuery()) {
                    if (!rs.next() || !rs.getBoolean("activo")) {
                        throw new NegocioException("El cliente corporativo no existe o se encuentra inactivo.");
                    }
                    BigDecimal limite = rs.getBigDecimal("limite_credito");
                    if (limite != null) {
                        limiteCredito = limite;
                    }
                    int dias = rs.getInt("dias_credito");
                    if (dias > 0) {
                        diasCredito = dias;
                    }
                }
            }

            if (limiteCredito.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal deudaPendiente = BigDecimal.ZERO;
                try (PreparedStatement psDeuda = conn.prepareStatement(SQL_DEUDA_PENDIENTE_CLIENTE)) {
                    psDeuda.setInt(1, clienteCredito.getIdCliente());
                    try (ResultSet rsDeuda = psDeuda.executeQuery()) {
                        if (rsDeuda.next() && rsDeuda.getBigDecimal("deuda") != null) {
                            deudaPendiente = rsDeuda.getBigDecimal("deuda");
                        }
                    }
                }
                if (deudaPendiente.add(venta.getTotal()).compareTo(limiteCredito) > 0) {
                    throw new NegocioException(String.format(Locale.US,
                            "Límite de crédito corporativo excedido. Límite: S/ %.2f | Deuda actual: S/ %.2f | Venta: S/ %.2f",
                            limiteCredito, deudaPendiente, venta.getTotal()));
                }
            }

            Venta ventaRegistrada = registrarVentaEnConexion(conn, venta, detalles, comprobante, null);
            String nroComprobante = comprobante != null
                    && comprobante.getSerie() != null && comprobante.getNumero() != null
                    ? comprobante.getSerie() + "-" + comprobante.getNumero()
                    : "VTA-" + ventaRegistrada.getIdVenta();
            LocalDate hoyLima = LocalDate.now(ZONA_LIMA);
            CuentaPorCobrar cxc = new CuentaPorCobrar();
            cxc.setIdCliente(clienteCredito.getIdCliente());
            cxc.setIdVenta(ventaRegistrada.getIdVenta());
            cxc.setNumeroComprobante(nroComprobante);
            cxc.setFechaEmision(hoyLima);
            cxc.setFechaVencimiento(hoyLima.plusDays(diasCredito));
            cxc.setMontoTotal(ventaRegistrada.getTotal());
            cxc.setMontoCobrado(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            cxc.setEstado("PENDIENTE");
            String etiquetaNombre = nombreCliente != null && !nombreCliente.isBlank()
                    ? nombreCliente.trim() : clienteCredito.getRazonSocial();
            cxc.setObservaciones("Venta POS a credito corporativo. Cliente: " + etiquetaNombre);
            cuentaPorCobrarDAO.insertar(conn, cxc);
            return ventaRegistrada;
        });
    }

    private Cliente resolverClienteParaCredito(Connection conn, Integer idCliente, String docCliente)
            throws SQLException {
        if (idCliente != null && idCliente > 0) {
            Optional<Cliente> optById = clienteDAO.buscarPorId(conn, idCliente);
            if (optById.isPresent()) {
                return optById.get();
            }
        }
        if (docCliente != null && !docCliente.isBlank()) {
            Optional<Cliente> optByDoc = clienteDAO.buscarPorDocumento(conn, docCliente.trim());
            if (optByDoc.isPresent()) {
                return optByDoc.get();
            }
            return clienteDAO.buscarPorDniOTelefono(conn, docCliente.trim()).orElse(null);
        }
        return null;
    }

    @Override
    public List<Venta> dividirCuentaPorItems(List<VentaService.SubcuentaItems> subcuentas, int idUsuario)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (subcuentas == null || subcuentas.isEmpty()) {
            throw new NegocioException("Debe especificar al menos una subcuenta con ítems.");
        }
        List<VentaService.SubcuentaItems> subcuentasValidas = new ArrayList<>();
        for (VentaService.SubcuentaItems sub : subcuentas) {
            if (sub != null && sub.detalles() != null && !sub.detalles().isEmpty()) {
                subcuentasValidas.add(sub);
            }
        }
        if (subcuentasValidas.isEmpty()) {
            throw new NegocioException("Debe especificar al menos una subcuenta con ítems válidos.");
        }

        return tx.executeChecked(conn -> {
            List<Venta> ventasGeneradas = new ArrayList<>();
            int correlativo = 1;
            for (VentaService.SubcuentaItems sub : subcuentasValidas) {
                BigDecimal suma = BigDecimal.ZERO;
                for (DetalleVenta detalle : sub.detalles()) {
                    if (detalle == null || detalle.getCantidad() <= 0 || detalle.getPrecioUnitario() == null
                            || detalle.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new NegocioException(
                                "Cada ítem en la división debe tener cantidad y precio unitario mayores a cero.");
                    }
                    BigDecimal subLinea = detalle.getPrecioUnitario()
                            .multiply(BigDecimal.valueOf(detalle.getCantidad()))
                            .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
                    detalle.setSubtotal(subLinea);
                    suma = suma.add(subLinea);
                }
                DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(suma);
                String medio = sub.medioPago() != null && !sub.medioPago().isBlank()
                        ? sub.medioPago() : "EFECTIVO";
                Venta nuevaVenta = new Venta(0, null, 1, idUsuario, 1,
                        desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
                        medio, desglose.getTotal(), BigDecimal.ZERO);
                nuevaVenta.setAplicarFidelizacion(false);
                nuevaVenta.setIdempotencyKey(UUID.randomUUID().toString());
                String sufijo = UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 8).toUpperCase(Locale.ROOT);
                ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SP" + correlativo,
                        String.format(Locale.ROOT, "%04d-%s", correlativo, sufijo), desglose.getTotal());
                calculoValidator.validarYPrepararMontosVenta(nuevaVenta, sub.detalles(), null);
                ventasGeneradas.add(registrarVentaEnConexion(conn, nuevaVenta, sub.detalles(), comp, null));
                correlativo++;
            }
            return ventasGeneradas;
        });
    }

    @Override
    public Venta registrarVentaEnConexion(Connection conn, Venta venta, List<DetalleVenta> detalles,
                                          ComprobantePago comprobante,
                                          List<VentaService.PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        String idemKey = venta.getIdempotencyKey();
        if (idemKey != null && !idemKey.isBlank()) {
            Optional<Venta> existente = ventaDAO.buscarPorIdempotencyKey(conn, idemKey.trim());
            if (existente.isPresent()) {
                LOGGER.info("Idempotencia BD activada: devolviendo venta existente #"
                        + existente.get().getIdVenta());
                return existente.get();
            }
        }

        cierreContableService.validarPeriodoAbierto(conn, venta.getFechaHora());
        Optional<Caja> cajaAbierta = cajaDAO.obtenerCajaAbierta(conn);
        if (cajaAbierta.isEmpty()) {
            throw new CajaCerradaException();
        }
        int idCaja = cajaAbierta.get().getIdCaja();
        venta.setIdCaja(idCaja);
        BigDecimal totalFinal = venta.getTotal();
        BigDecimal descuentoPuntos = venta.getDescuentoPuntos() != null
                ? venta.getDescuentoPuntos() : BigDecimal.ZERO;

        VentaInventarioKardexHandler.PlanConsumoInsumos plan =
                inventarioKardexHandler.prepararYLockearInsumosVenta(conn, detalles);

        int idVenta = ventaDAO.insertarVenta(conn, venta);
        venta.setIdVenta(idVenta);
        for (DetalleVenta detalle : detalles) {
            detalle.setIdVenta(idVenta);
            ventaDAO.insertarDetalleVenta(conn, detalle);
        }
        long idUsrMovL = venta.getIdUsuario() > 0 ? venta.getIdUsuario() : 1L;
        inventarioKardexHandler.descontarStockYRegistrarKardex(conn, idVenta, idUsrMovL, plan);

        int idUsrMov = venta.getIdUsuario() > 0 ? venta.getIdUsuario() : 1;
        if (pagosDivididos != null && !pagosDivididos.isEmpty()) {
            int idxPago = 1;
            for (VentaService.PagoSplit pago : pagosDivididos) {
                String medio = calculoValidator.normalizarMedioPagoMovimientoCaja(pago.medioPago());
                MovimientoCaja movimiento = new MovimientoCaja(
                        0, idCaja, idUsrMov, "VENTA", medio,
                        pago.monto().setScale(2, RoundingMode.HALF_EVEN),
                        "Cobro Split (" + idxPago + "/" + pagosDivididos.size() + ") Venta #"
                                + idVenta + " [" + medio + "]");
                cajaDAO.registrarMovimiento(conn, movimiento);
                idxPago++;
            }
        } else {
            String medioPago = calculoValidator.normalizarMedioPagoMovimientoCaja(venta.getMetodoPago());
            MovimientoCaja movimiento = new MovimientoCaja(
                    0, idCaja, idUsrMov, "VENTA", medioPago, totalFinal,
                    "Cobro Venta #" + idVenta + " [" + medioPago + "]");
            cajaDAO.registrarMovimiento(conn, movimiento);
        }

        if (comprobante != null) {
            comprobante.setIdVenta(idVenta);
            comprobante.setTotal(totalFinal);
            ventaDAO.insertarComprobante(conn, comprobante);
        }
        if (venta.getIdPedido() != null && venta.getIdPedido() > 0) {
            pedidoDAO.actualizarEstadoPedido(conn, venta.getIdPedido(), "COBRADO");
        }
        if (venta.getIdCliente() != null && venta.getIdCliente() > 0 && venta.isAplicarFidelizacion()) {
            if (venta.getPuntosACanjear() > 0 && descuentoPuntos.compareTo(BigDecimal.ZERO) > 0) {
                fidelizacionService.canjearPuntosConfiguradosEnVenta(
                        conn, venta.getIdCliente(), idVenta, venta.getPuntosACanjear(), descuentoPuntos);
            }
            if (totalFinal.compareTo(BigDecimal.ZERO) > 0) {
                fidelizacionService.acumularPuntosPorVenta(conn, venta.getIdCliente(), idVenta, totalFinal);
            }
        }
        LOGGER.info("Venta #" + idVenta + " registrada exitosamente (Total: S/ " + totalFinal + ").");
        return venta;
    }
}